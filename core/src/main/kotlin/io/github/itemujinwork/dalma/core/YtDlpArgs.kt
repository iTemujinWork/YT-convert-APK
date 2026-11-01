package io.github.itemujinwork.dalma.core

/**
 * How hard to try. The first attempt always uses yt-dlp's own defaults (they are kept
 * up to date by the yt-dlp team); the other knobs are fallbacks chosen by [RetryPlanner].
 */
data class Strategy(
    /** YouTube player clients to ask, `null` means yt-dlp's default choice. */
    val playerClients: List<String>? = null,
    val forceIpv4: Boolean = false,
    /** Accept any audio instead of insisting on Opus 251 / AAC 140. */
    val anyFormat: Boolean = false,
    /** Skip cover art and tags, the steps most likely to trip over odd files. */
    val simpleProcessing: Boolean = false,
) {
    companion object {
        val DEFAULT = Strategy()

        /** Unknown client names are skipped by yt-dlp with a warning, so this cannot break. */
        val OTHER_CLIENTS = listOf("default", "mweb", "tv", "web_safari")
    }
}

data class EngineRequest(
    val target: Target,
    /** Private, empty directory the engine downloads into. */
    val workDir: String,
    /** yt-dlp download archive: one "youtube <id>" line per song we already have. */
    val archivePath: String? = null,
    /** Safety net for a channel's whole upload list shared by accident. */
    val maxPlaylistItems: Int = MAX_PLAYLIST_ITEMS,
) {
    companion object {
        const val MAX_PLAYLIST_ITEMS = 300
    }
}

/**
 * Builds the yt-dlp command line. Music only: the original Opus stream (YouTube's 251,
 * about 160 kbit/s) saved as .opus without re-encoding, AAC 140 as .m4a when a song has
 * no Opus. Tags and a square cover are written into the file so the stock player shows them.
 */
object YtDlpArgs {
    const val MARK_META = "[dalma-meta]"
    const val MARK_PLAN = "[dalma-plan]"
    const val MARK_PROGRESS = "[dalma-progress]"
    const val MARK_PHASE = "[dalma-phase]"
    const val MARK_FILE = "[dalma-file]"
    const val MARK_LIST = "[dalma-list]"

    /** What yt-dlp prints for a song that is already in the download archive. */
    const val ARCHIVE_SKIP = "has already been recorded in the archive"

    private const val FORMATS = "251/140/ba[acodec^=opus]/ba[ext=m4a]/ba/b"

    /** Square cover art from the 16:9 thumbnail, the way music apps show it. */
    private const val SQUARE_COVER =
        "ThumbnailsConvertor+FFmpeg_o:-c:v mjpeg -qmin 1 -qscale:v 2 " +
            "-vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\""

    /**
     * Tag values. "Artist - Topic" channels are YouTube Music's auto-generated ones; the
     * part in brackets after a title ("Official Video", "Hivatalos klip") is not the title.
     */
    private const val ARTIST_RULE = "%(artist,uploader)s:(?P<meta_artist>.+?)(?:\\s*-\\s*Topic)?\\s*$"
    private const val TITLE_RULE =
        "%(track,title)s:(?P<meta_title>.+?)(?:\\s*[(\\[](?i:official|hivatalos|lyric|lyrics|dalszöveg|audio|hd|hq|4k|" +
            "music video|videoklip|video|videó|klip|visualizer|mv)[^)\\]]*[)\\]])*\\s*$"
    private const val DATE_RULE = "%(release_year,upload_date>%Y|)s:(?P<meta_date>\\d{4})"

    fun build(request: EngineRequest, strategy: Strategy = Strategy.DEFAULT): List<String> = buildList {
        // Nothing on the device may change what we run.
        add("--ignore-config")
        add("--no-mtime")
        add("--continue")
        add("--no-overwrites")
        addAll(listOf("--color", "never"))
        addAll(listOf("--encoding", "utf-8"))
        // --print would silence yt-dlp; we want its "already in the archive" lines.
        add("--no-quiet")
        // Warnings, versions, the JS runtime, the ffmpeg command line: all on stderr, which goes
        // into the hidden last-save log when something fails. The song itself never sees it.
        add("--verbose")

        when (request.target) {
            is Target.Video -> add("--no-playlist")
            is Target.Playlist -> {
                add("--yes-playlist")
                addAll(listOf("--playlist-items", "1:${request.maxPlaylistItems}"))
                addAll(listOf("--sleep-requests", "0.75"))
            }
        }
        request.archivePath?.let { addAll(listOf("--download-archive", it)) }

        // Patience with flaky mobile networks.
        addAll(listOf("--retries", "10"))
        addAll(listOf("--fragment-retries", "10"))
        addAll(listOf("--extractor-retries", "3"))
        addAll(listOf("--file-access-retries", "5"))
        addAll(listOf("--retry-sleep", "exp=1:20"))
        addAll(listOf("--retry-sleep", "fragment:exp=1:20"))
        addAll(listOf("--socket-timeout", "30"))
        // Re-extract the stream when YouTube starts to throttle it.
        addAll(listOf("--throttled-rate", "100K"))
        if (strategy.forceIpv4) add("--force-ipv4")
        strategy.playerClients?.let {
            addAll(listOf("--extractor-args", "youtube:player_client=" + it.joinToString(",")))
        }

        // Machine readable progress.
        add("--newline")
        add("--progress")
        addAll(
            listOf(
                "--progress-template",
                "download:$MARK_PROGRESS %(progress.status)s|%(progress.downloaded_bytes)s|" +
                    "%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|" +
                    "%(progress.speed)s|%(progress.eta)s|%(info.format_id)s|%(info.id)s",
            ),
        )
        addAll(listOf("--parse-metadata", ARTIST_RULE))
        addAll(listOf("--parse-metadata", TITLE_RULE))
        addAll(listOf("--parse-metadata", DATE_RULE))
        addAll(
            listOf(
                "--print",
                "before_dl:$MARK_META %(.{id,title,meta_title,meta_artist,artist,uploader,channel,album," +
                    "duration,live_status,playlist_index,playlist_count})j",
            ),
        )
        addAll(
            listOf(
                "--print",
                "before_dl:$MARK_PLAN %(id)s|%(format_id)s|%(filesize)s|%(filesize_approx)s",
            ),
        )
        addAll(listOf("--print", "post_process:$MARK_PHASE %(id)s"))
        addAll(listOf("--print", "after_move:$MARK_FILE %(id)s|%(filepath)s"))
        if (request.target is Target.Playlist) {
            addAll(listOf("--print", "playlist:$MARK_LIST %(playlist_count,n_entries|)s"))
        }

        // Plain ASCII names inside our private folder; the real name is given when saving.
        addAll(listOf("-o", request.workDir.trimEnd('/') + "/%(id)s.%(ext)s"))

        addAll(listOf("-f", if (strategy.anyFormat) "ba/b" else FORMATS))
        add("--extract-audio")
        // "best" keeps the original stream: Opus stays Opus (.opus), AAC stays AAC (.m4a).
        addAll(listOf("--audio-format", "best"))
        if (!strategy.simpleProcessing) {
            add("--embed-metadata")
            add("--no-embed-info-json")
            add("--embed-thumbnail")
            addAll(listOf("--convert-thumbnails", "jpg"))
            addAll(listOf("--ppa", SQUARE_COVER))
        }

        // Always last, always the clean canonical address.
        add("--")
        add(request.target.canonicalUrl)
    }
}

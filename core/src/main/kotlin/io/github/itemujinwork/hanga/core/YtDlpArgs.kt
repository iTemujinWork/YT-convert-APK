package io.github.itemujinwork.hanga.core

/**
 * How hard to try. The first attempt always uses yt-dlp's own defaults (they are kept
 * up to date by the yt-dlp team); the other knobs are fallbacks chosen by [RetryPlanner].
 */
data class Strategy(
    /** YouTube player clients to ask, `null` means yt-dlp's default choice. */
    val playerClients: List<String>? = null,
    val forceIpv4: Boolean = false,
    /** Accept any format instead of the most compatible one. */
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
    val link: VideoLink,
    val kind: MediaKind,
    /** Private, empty directory the engine downloads into. */
    val workDir: String,
)

/**
 * Builds the yt-dlp command line. Every option is chosen so that the result plays
 * everywhere and the output can be followed by machines ([EngineOutput]).
 */
object YtDlpArgs {
    const val MARK_META = "[hanga-meta]"
    const val MARK_PLAN = "[hanga-plan]"
    const val MARK_PROGRESS = "[hanga-progress]"
    const val MARK_PHASE = "[hanga-phase]"
    const val MARK_FILE = "[hanga-file]"

    /** Square cover art from the 16:9 thumbnail, the way music apps show it. */
    private const val SQUARE_COVER =
        "ThumbnailsConvertor+FFmpeg_o:-c:v mjpeg -qmin 1 -qscale:v 2 " +
            "-vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\""

    fun build(request: EngineRequest, strategy: Strategy = Strategy.DEFAULT): List<String> = buildList {
        // Nothing on the device may change what we run.
        add("--ignore-config")
        add("--no-playlist")
        add("--no-mtime")
        add("--continue")
        addAll(listOf("--color", "never"))
        addAll(listOf("--encoding", "utf-8"))

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

        // Machine readable progress (printing implies --quiet, --progress brings the bar back).
        add("--newline")
        add("--progress")
        addAll(
            listOf(
                "--progress-template",
                "download:$MARK_PROGRESS %(progress.status)s|%(progress.downloaded_bytes)s|" +
                    "%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|" +
                    "%(progress.speed)s|%(progress.eta)s|%(info.format_id)s",
            ),
        )
        addAll(
            listOf(
                "--print",
                "before_dl:$MARK_META %(.{id,title,uploader,channel,artist,track,album,duration,live_status})j",
            ),
        )
        addAll(
            listOf(
                "--print",
                "before_dl:$MARK_PLAN %(format_id)s|%(requested_formats.:.format_id)j|" +
                    "%(requested_formats.:.filesize)j|%(requested_formats.:.filesize_approx)j|" +
                    "%(filesize)s|%(filesize_approx)s",
            ),
        )
        addAll(listOf("--print", "post_process:$MARK_PHASE processing"))
        addAll(listOf("--print", "after_move:$MARK_FILE %(filepath)s"))

        // Plain ASCII names inside our private folder; the real title is applied when saving.
        addAll(listOf("-o", request.workDir.trimEnd('/') + "/%(id)s.%(ext)s"))

        when (request.kind) {
            MediaKind.AUDIO -> addAudio(strategy)
            MediaKind.VIDEO -> addVideo(strategy)
        }

        // Always last, always the clean canonical address.
        add("--")
        add(request.link.canonicalUrl)
    }

    private fun MutableList<String>.addAudio(strategy: Strategy) {
        addAll(listOf("-f", if (strategy.anyFormat) "ba/b" else "bestaudio/best"))
        add("--extract-audio")
        addAll(listOf("--audio-format", "mp3"))
        addAll(listOf("--audio-quality", "0"))
        if (!strategy.simpleProcessing) {
            add("--embed-metadata")
            add("--embed-thumbnail")
            addAll(listOf("--convert-thumbnails", "jpg"))
            addAll(listOf("--ppa", SQUARE_COVER))
        }
    }

    private fun MutableList<String>.addVideo(strategy: Strategy) {
        if (strategy.anyFormat) {
            addAll(listOf("-f", "bv*+ba/b"))
        } else {
            // H.264 + AAC up to 1080p: what phones, TVs and laptops all play.
            addAll(listOf("-f", "bv*+ba/b"))
            addAll(listOf("-S", "vcodec:h264,res:1080,acodec:aac"))
        }
        addAll(listOf("--merge-output-format", "mp4"))
        addAll(listOf("--remux-video", "mp4"))
        if (!strategy.simpleProcessing) {
            add("--embed-metadata")
        }
    }
}

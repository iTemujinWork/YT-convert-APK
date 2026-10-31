package io.github.itemujinwork.hanga.core

import java.net.URLDecoder

/** A single YouTube video, identified by its 11 character id. */
data class VideoLink(val videoId: String) {
    init {
        require(VIDEO_ID.matches(videoId)) { "Not a YouTube video id: $videoId" }
    }

    /**
     * The only address the engine is ever given: no playlist, no tracking parameters,
     * no timestamps, and nothing a pasted text could smuggle in as a command line option.
     */
    val canonicalUrl: String get() = "https://www.youtube.com/watch?v=$videoId"

    val thumbnailUrl: String get() = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

    companion object {
        internal val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}

/** The answer to "what did the user give us?". */
sealed interface LinkCheck {
    data class Video(val link: VideoLink) : LinkCheck

    /** A playlist address without a selected video. */
    data object PlaylistOnly : LinkCheck

    /** A YouTube page that is not a single video: a channel, the home page, a search... */
    data object NotAVideo : LinkCheck

    /** Text without any YouTube address in it. */
    data object NotYouTube : LinkCheck

    data object Empty : LinkCheck
}

/**
 * Finds the first YouTube video in whatever people paste or share: a bare link, a link
 * with tracking parameters, "Watch this! https://youtu.be/...", Shorts, YouTube Music...
 */
object YouTubeLinks {
    private val CANDIDATES = Regex(
        """https?://[^\s<>"'`]+""" +
            """|(?<![\w.@/-])(?:[a-z0-9-]+\.)*(?:youtube\.com|youtube-nocookie\.com|youtu\.be)/[^\s<>"'`]*""",
        RegexOption.IGNORE_CASE,
    )
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}>»«\"'’”"
    private val VIDEO_PATH_PREFIXES = setOf("shorts", "live", "embed", "v", "e", "vi", "watch")

    fun check(text: String?): LinkCheck {
        if (text.isNullOrBlank()) return LinkCheck.Empty
        // Invisible formatting characters (zero width spaces, direction marks) sneak into
        // copied text and would break the id.
        val clean = text.filterNot { Character.getType(it) == Character.FORMAT.toInt() }
        var sawPlaylist = false
        var sawYouTube = false
        for (match in CANDIDATES.findAll(clean)) {
            when (val result = checkCandidate(match.value.trimEnd { it in TRAILING_PUNCTUATION })) {
                is LinkCheck.Video -> return result
                LinkCheck.PlaylistOnly -> sawPlaylist = true
                LinkCheck.NotAVideo -> sawYouTube = true
                LinkCheck.NotYouTube, LinkCheck.Empty -> Unit
            }
        }
        return when {
            sawPlaylist -> LinkCheck.PlaylistOnly
            sawYouTube -> LinkCheck.NotAVideo
            else -> LinkCheck.NotYouTube
        }
    }

    /** Convenience for callers that only care about the video. */
    fun findVideo(text: String?): VideoLink? = (check(text) as? LinkCheck.Video)?.link

    private fun checkCandidate(candidate: String): LinkCheck {
        val parts = split(candidate) ?: return LinkCheck.NotYouTube
        val host = parts.host
        return when {
            host == "youtu.be" || host == "www.youtu.be" -> {
                val id = parts.segments.firstOrNull()?.let(::asVideoId)
                if (id != null) LinkCheck.Video(VideoLink(id)) else LinkCheck.NotAVideo
            }
            isYouTubeHost(host) -> fromYouTubePath(parts.segments, parts.query, depth = 0)
            else -> LinkCheck.NotYouTube
        }
    }

    private fun isYouTubeHost(host: String): Boolean =
        host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")

    private fun fromYouTubePath(segments: List<String>, query: Map<String, String>, depth: Int): LinkCheck {
        query["v"]?.let(::asVideoId)?.let { return LinkCheck.Video(VideoLink(it)) }
        if (segments.size >= 2 && segments[0].lowercase() in VIDEO_PATH_PREFIXES) {
            asVideoId(segments[1])?.let { return LinkCheck.Video(VideoLink(it)) }
        }
        // Old style share links: /attribution_link?u=/watch%3Fv%3D<id>%26feature%3Dshare
        if (depth == 0 && segments.firstOrNull() == "attribution_link") {
            val inner = query["u"]
            if (inner != null) {
                val path = inner.substringBefore('?')
                val innerQuery = parseQuery(inner.substringAfter('?', ""))
                return fromYouTubePath(path.split('/').filter { it.isNotEmpty() }, innerQuery, depth + 1)
            }
        }
        return if (query.containsKey("list")) LinkCheck.PlaylistOnly else LinkCheck.NotAVideo
    }

    private fun asVideoId(value: String): String? = value.takeIf { VideoLink.VIDEO_ID.matches(it) }

    private class Parts(val host: String, val segments: List<String>, val query: Map<String, String>)

    /** A forgiving URL splitter: pasted text is messy and java.net.URI rejects too much. */
    private fun split(url: String): Parts? {
        var rest = url
        val schemeEnd = rest.indexOf("://")
        if (schemeEnd >= 0) {
            val scheme = rest.substring(0, schemeEnd).lowercase()
            if (scheme != "http" && scheme != "https") return null
            rest = rest.substring(schemeEnd + 3)
        }
        rest = rest.substringBefore('#')
        val beforeQuery = rest.substringBefore('?')
        val rawQuery = rest.substringAfter('?', "")
        // Anything before an '@' is user info: "youtube.com@evil.example" points at evil.example.
        val authority = beforeQuery.substringBefore('/').substringAfterLast('@')
        val host = authority.substringBefore(':').lowercase().trimEnd('.')
        if (host.isEmpty()) return null
        val path = beforeQuery.substringAfter('/', "")
        return Parts(host, path.split('/').filter { it.isNotEmpty() }, parseQuery(rawQuery))
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in raw.split('&', ';')) {
            if (pair.isEmpty()) continue
            val key = decode(pair.substringBefore('='))
            val value = decode(pair.substringAfter('=', ""))
            result.putIfAbsent(key, value)
        }
        return result
    }

    // The (String, String) overload on purpose: the Charset one needs Android 13.
    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: IllegalArgumentException) {
            value
        }
}

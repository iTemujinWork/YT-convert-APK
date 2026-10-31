package io.github.itemujinwork.dalma.core

import java.net.URLDecoder

/** What Dalma is asked to save: one video, or a whole playlist. */
sealed interface Target {
    /**
     * The only address the engine is ever given: no tracking parameters, no timestamps,
     * and nothing a pasted text could smuggle in as a command line option.
     */
    val canonicalUrl: String

    data class Video(val videoId: String) : Target {
        init {
            require(VIDEO_ID.matches(videoId)) { "Not a YouTube video id: $videoId" }
        }

        override val canonicalUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
        val thumbnailUrl: String get() = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    data class Playlist(val listId: String) : Target {
        init {
            require(LIST_ID.matches(listId)) { "Not a YouTube playlist id: $listId" }
        }

        override val canonicalUrl: String get() = "https://www.youtube.com/playlist?list=$listId"
    }

    companion object {
        internal val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        internal val LIST_ID = Regex("[A-Za-z0-9_-]{2,120}")
    }
}

/** The answer to "what did we get from the Share menu?". */
sealed interface LinkCheck {
    data class Found(val target: Target) : LinkCheck

    /** A YouTube page that is neither a video nor a playlist: a channel, the home page, a search... */
    data object NotAVideo : LinkCheck

    /** Text without any YouTube address in it. */
    data object NotYouTube : LinkCheck

    data object Empty : LinkCheck
}

/**
 * Finds the first YouTube video or playlist in whatever is shared: a bare link, a link
 * with tracking parameters, "Watch this! https://youtu.be/...", Shorts, YouTube Music...
 *
 * A song shared from a playlist (`watch?v=ID&list=LIST`) means that one song.
 * A playlist page (`playlist?list=LIST`) means the whole list.
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
        var sawYouTube = false
        for (match in CANDIDATES.findAll(clean)) {
            when (val result = checkCandidate(match.value.trimEnd { it in TRAILING_PUNCTUATION })) {
                is LinkCheck.Found -> return result
                LinkCheck.NotAVideo -> sawYouTube = true
                LinkCheck.NotYouTube, LinkCheck.Empty -> Unit
            }
        }
        return if (sawYouTube) LinkCheck.NotAVideo else LinkCheck.NotYouTube
    }

    fun find(text: String?): Target? = (check(text) as? LinkCheck.Found)?.target

    private fun checkCandidate(candidate: String): LinkCheck {
        val parts = split(candidate) ?: return LinkCheck.NotYouTube
        val host = parts.host
        return when {
            host == "youtu.be" || host == "www.youtu.be" -> {
                val id = parts.segments.firstOrNull()?.let(::asVideoId)
                if (id != null) LinkCheck.Found(Target.Video(id)) else LinkCheck.NotAVideo
            }
            isYouTubeHost(host) -> fromYouTubePath(parts.segments, parts.query, depth = 0)
            else -> LinkCheck.NotYouTube
        }
    }

    private fun isYouTubeHost(host: String): Boolean =
        host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")

    private fun fromYouTubePath(segments: List<String>, query: Map<String, String>, depth: Int): LinkCheck {
        query["v"]?.let(::asVideoId)?.let { return LinkCheck.Found(Target.Video(it)) }
        if (segments.size >= 2 && segments[0].lowercase() in VIDEO_PATH_PREFIXES) {
            asVideoId(segments[1])?.let { return LinkCheck.Found(Target.Video(it)) }
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
        query["list"]?.takeIf { Target.LIST_ID.matches(it) }?.let { return LinkCheck.Found(Target.Playlist(it)) }
        return LinkCheck.NotAVideo
    }

    private fun asVideoId(value: String): String? = value.takeIf { Target.VIDEO_ID.matches(it) }

    private class Parts(val host: String, val segments: List<String>, val query: Map<String, String>)

    /** A forgiving URL splitter: shared text is messy and java.net.URI rejects too much. */
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

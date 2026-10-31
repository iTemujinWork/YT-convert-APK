package io.github.itemujinwork.dalma.core

/** Friendly, safe file names: "Előadó – Cím.opus". */
object FileNames {
    private const val MAX_CHARS = 120
    private const val MAX_UTF8_BYTES = 200
    private const val DASH = " – "

    private val BRACKETED = Regex("""\s*[(\[]([^()\[\]]{1,48})[)\]]""")
    private val WORD_SPLIT = Regex("""[\s/|&,+.-]+""")

    /** Words that only describe the upload, never the song ("Official Video", "Hivatalos klip"...). */
    private val NOISE_WORDS = setOf(
        "official", "hivatalos", "music", "zenei", "lyric", "lyrics", "video", "videó", "videoklip",
        "videoclip", "klip", "clip", "audio", "hd", "hq", "4k", "uhd", "visualizer", "dalszöveg",
        "szöveg", "szöveges", "with", "full", "mv",
    )

    /**
     * @param artist may be blank or `null`
     * @param title the song or video title, may be anything
     * @param fallback used when nothing usable is left, e.g. the video id
     * @param extension without the dot: opus, m4a...
     */
    fun forTrack(artist: String?, title: String?, fallback: String, extension: String): String {
        val cleanTitle = sanitize(stripNoise(title.orEmpty()))
        val cleanArtist = sanitize(artist.orEmpty())
        var name = when {
            cleanTitle.isEmpty() -> cleanArtist
            cleanArtist.isEmpty() -> cleanTitle
            // "Artist - Song" titles already carry the artist.
            cleanTitle.startsWith(cleanArtist, ignoreCase = true) && cleanTitle.length > cleanArtist.length + 2 -> cleanTitle
            else -> cleanArtist + DASH + cleanTitle
        }
        name = truncate(name).trimEnd { it == ' ' || it == '.' || it == '-' || it == '–' }
        if (name.isEmpty()) name = sanitize(fallback).ifEmpty { "dalma" }
        return "$name.$extension"
    }

    /** "Artist - Song (Official Music Video) [4K]" → "Artist - Song". */
    fun stripNoise(title: String): String {
        val stripped = BRACKETED.replace(title) { match ->
            val words = match.groupValues[1].lowercase().split(WORD_SPLIT).filter { it.isNotBlank() }
            if (words.isNotEmpty() && words.all { it in NOISE_WORDS }) "" else match.value
        }
        return stripped.trim().ifEmpty { title }
    }

    fun sanitize(raw: String): String {
        val builder = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch == '/' || ch == '\\' || ch == '|' -> builder.append('-')
                ch == ':' -> builder.append(" -")
                ch == '"' -> builder.append('\'')
                ch == '*' || ch == '?' || ch == '<' || ch == '>' -> Unit
                ch.isISOControl() || Character.getType(ch) == Character.FORMAT.toInt() -> Unit
                ch.isWhitespace() -> builder.append(' ')
                else -> builder.append(ch)
            }
        }
        return builder.toString()
            .replace(Regex(" {2,}"), " ")
            .replace(Regex("-{2,}"), "-")
            .trim { it == ' ' || it == '.' || it == '-' || it == '_' }
    }

    private fun truncate(name: String): String {
        var result = name
        if (result.codePointCount(0, result.length) > MAX_CHARS) {
            result = result.substring(0, result.offsetByCodePoints(0, MAX_CHARS))
        }
        while (result.toByteArray(Charsets.UTF_8).size > MAX_UTF8_BYTES) {
            result = result.substring(0, result.offsetByCodePoints(0, result.codePointCount(0, result.length) - 1))
        }
        return result
    }
}

package io.github.itemujinwork.dalma.core

/**
 * The lists: plain .m3u files in the same folder as the songs, readable by any player.
 * One file name per line, relative to the folder; an #EXTINF line with the length and the
 * title before each; the list's name is the file's name.
 */
object M3u {
    const val EXTENSION = "m3u"

    data class Entry(val fileName: String, val title: String?, val seconds: Long?)

    /** Reads a list; lines that are not file names (comments, blanks, absolute paths) are skipped. */
    fun parse(text: String): List<Entry> {
        val entries = ArrayList<Entry>()
        var pendingTitle: String? = null
        var pendingSeconds: Long? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val body = line.substringAfter(':')
                    pendingSeconds = body.substringBefore(',').trim().toDoubleOrNull()?.toLong()
                    pendingTitle = body.substringAfter(',', "").trim().ifEmpty { null }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val name = line.replace('\\', '/').substringAfterLast('/')
                    if (name.isNotEmpty() && !line.contains("://")) entries += Entry(name, pendingTitle, pendingSeconds)
                    pendingTitle = null
                    pendingSeconds = null
                }
            }
        }
        return entries
    }

    /** Writes a list the way other players expect it. */
    fun write(entries: List<Entry>): String = buildString {
        append("#EXTM3U\n")
        for (entry in entries) {
            append("#EXTINF:").append(entry.seconds ?: -1).append(',').append(entry.title ?: entry.fileName.substringBeforeLast('.')).append('\n')
            append(entry.fileName).append('\n')
        }
    }

    /** "Nyári mix.m3u" → "Nyári mix". */
    fun nameOf(fileName: String): String = fileName.substringBeforeLast('.')

    /** A safe file name for a list titled [title]. */
    fun fileNameFor(title: String): String = FileNames.forTrack(null, title, "lista", EXTENSION)
}

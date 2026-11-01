package io.github.itemujinwork.dalma.core

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * The yt-dlp release file is a Python zipapp: a `#!/usr/bin/env python3` line, then a zip
 * whose offsets count from the zip, not the file. Android's `java.util.zip.ZipFile` refuses
 * anything before the first local header, so this reads the archive as a stream from there.
 */
object ZipApp {
    private const val VERSION_FILE = "yt_dlp/version.py"
    private const val PREAMBLE_LIMIT = 4096
    private val VERSION = Regex("""__version__\s*=\s*['"]([^'"]+)['"]""")
    private val LOCAL_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /** The version inside yt_dlp/version.py, or null when this is not a yt-dlp zipapp. */
    fun version(file: File): String? = try {
        file.inputStream().buffered().use { version(it) }
    } catch (_: Exception) {
        null
    }

    fun version(input: InputStream): String? {
        val buffered = input as? BufferedInputStream ?: BufferedInputStream(input)
        skipPreamble(buffered)
        val zip = ZipInputStream(buffered)
        while (true) {
            val entry = zip.nextEntry ?: return null
            if (entry.name == VERSION_FILE) {
                val text = zip.readBytes().toString(Charsets.UTF_8)
                return VERSION.find(text)?.groupValues?.get(1)
            }
            zip.closeEntry()
        }
    }

    /** Leaves the stream at the first local file header, if one starts within the first few KB. */
    private fun skipPreamble(input: BufferedInputStream) {
        input.mark(PREAMBLE_LIMIT)
        val head = ByteArray(PREAMBLE_LIMIT)
        var filled = 0
        while (filled < head.size) {
            val read = input.read(head, filled, head.size - filled)
            if (read < 0) break
            filled += read
        }
        input.reset()
        val at = indexOf(head, filled, LOCAL_HEADER)
        var left = at.toLong()
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped <= 0) break
            left -= skipped
        }
    }

    private fun indexOf(haystack: ByteArray, length: Int, needle: ByteArray): Int {
        for (i in 0..length - needle.size) {
            if (needle.indices.all { haystack[i + it] == needle[it] }) return i
        }
        return 0
    }
}

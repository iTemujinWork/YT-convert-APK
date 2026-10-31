package io.github.itemujinwork.dalma.engine

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Small, careful HTTPS fetches for the two updaters. No third party client needed. */
internal object Http {
    private const val USER_AGENT = "Dalma (https://github.com/iTemujinWork/YT-convert-APK)"

    fun get(url: String, maxBytes: Long, headers: Map<String, String> = emptyMap()): ByteArray {
        require(url.startsWith("https://")) { "only https: $url" }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept-Encoding", "identity")
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            val declared = connection.contentLengthLong
            if (declared > maxBytes) throw IOException("too big: $declared bytes")
            val out = ByteArrayOutputStream(if (declared > 0) declared.toInt() else 64 * 1024)
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw IOException("too big: more than $maxBytes bytes")
                    out.write(buffer, 0, read)
                }
            }
            return out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    fun getText(url: String, maxBytes: Long = 1L shl 20, headers: Map<String, String> = emptyMap()): String =
        String(get(url, maxBytes, headers), Charsets.UTF_8)
}

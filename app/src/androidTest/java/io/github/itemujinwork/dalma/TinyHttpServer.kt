package io.github.itemujinwork.dalma

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** Serves the files of one folder over plain HTTP on localhost, with Range support, for yt-dlp to fetch. */
class TinyHttpServer(private val root: File) : AutoCloseable {
    private val socket = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
    val port: Int get() = socket.localPort

    init {
        thread(isDaemon = true, name = "tiny-http") {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: IOException) {
                    break
                }
                thread(isDaemon = true) { serve(client) }
            }
        }
    }

    private fun serve(client: Socket) = client.use {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.ISO_8859_1))
        val request = reader.readLine() ?: return
        var range: Pair<Long, Long?>? = null
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", ignoreCase = true)) {
                val spec = line.substringAfter("bytes=").trim()
                val start = spec.substringBefore('-').toLongOrNull() ?: 0L
                val end = spec.substringAfter('-', "").toLongOrNull()
                range = start to end
            }
        }
        val parts = request.split(' ')
        val path = parts.getOrNull(1)?.substringBefore('?')?.trimStart('/') ?: ""
        val file = File(root, path)
        val out = client.getOutputStream()
        if (!file.isFile || file.parentFile != root) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        val length = file.length()
        val start = range?.first ?: 0L
        val end = range?.second ?: (length - 1)
        val count = end - start + 1
        val head = if (range != null) {
            "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $start-$end/$length\r\n"
        } else {
            "HTTP/1.1 200 OK\r\n"
        }
        out.write((head + "Content-Type: application/octet-stream\r\nContent-Length: $count\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n").toByteArray())
        if (parts.firstOrNull() != "HEAD") {
            file.inputStream().use { input ->
                input.skip(start)
                val buffer = ByteArray(64 * 1024)
                var remaining = count
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
        out.flush()
    }

    override fun close() {
        socket.close()
    }
}

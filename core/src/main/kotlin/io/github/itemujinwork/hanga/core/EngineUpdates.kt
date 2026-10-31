package io.github.itemujinwork.hanga.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.InputStream
import java.security.MessageDigest

/** yt-dlp versions are dates: 2026.10.26, sometimes with a fourth part (2024.08.06.1). */
object EngineVersion {
    fun compare(a: String?, b: String?): Int {
        val left = parts(a)
        val right = parts(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    fun isNewer(candidate: String?, current: String?): Boolean = parts(candidate).isNotEmpty() && compare(candidate, current) > 0

    private fun parts(version: String?): List<Int> =
        version.orEmpty().trim().removePrefix("v").split('.')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
}

/** A GitHub release, reduced to what the updater needs. */
data class ReleaseInfo(val tag: String, val assets: Map<String, String>) {
    val version: String get() = tag.removePrefix("v")
}

object Releases {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Parses https://api.github.com/repos/OWNER/REPO/releases/latest. */
    fun parseLatest(text: String): ReleaseInfo? {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val tag = (root["tag_name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { asset ->
            val obj = asset as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val url = (obj["browser_download_url"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (!url.startsWith("https://")) return@mapNotNull null
            name to url
        }.toMap()
        return ReleaseInfo(tag, assets)
    }
}

object Checksums {
    private val LINE = Regex("""^([0-9a-fA-F]{64})\s+\*?(.+?)\s*$""")

    /** Parses yt-dlp's SHA2-256SUMS: "<hex>  <file name>" per line. */
    fun parseSha256Sums(text: String): Map<String, String> =
        text.lineSequence().mapNotNull { LINE.matchEntire(it.trim()) }
            .associate { it.groupValues[2] to it.groupValues[1].lowercase() }

    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(bytes: ByteArray): String = sha256Hex(bytes.inputStream())
}

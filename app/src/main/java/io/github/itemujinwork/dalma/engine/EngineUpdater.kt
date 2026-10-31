package io.github.itemujinwork.dalma.engine

import android.util.Log
import io.github.itemujinwork.dalma.core.Checksums
import io.github.itemujinwork.dalma.core.Releases
import io.github.itemujinwork.dalma.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * YouTube changes, yt-dlp follows within days: once a day, and after a failure, Dalma
 * fetches the newest yt-dlp from its GitHub releases. The file is only installed when
 * its SHA-256 matches the published checksum and it really is the version it claims.
 */
class EngineUpdater(private val engine: Engine, private val prefs: Prefs) {
    sealed interface Result {
        data class Updated(val version: String) : Result
        data object UpToDate : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun updateIfStale(force: Boolean = false): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.lastEngineCheck < DAY_MILLIS) return@withContext Result.UpToDate
        try {
            val release = Releases.parseLatest(Http.getText(LATEST_URL, headers = API_HEADERS))
                ?: return@withContext Result.Failed("cannot read the release list")
            prefs.lastEngineCheck = now
            if (!engine.isNewerThanInstalled(release.version)) return@withContext Result.UpToDate
            val binaryUrl = release.assets[BINARY] ?: return@withContext Result.Failed("no $BINARY in release ${release.tag}")
            val sumsUrl = release.assets[SUMS] ?: return@withContext Result.Failed("no $SUMS in release ${release.tag}")
            val expected = Checksums.parseSha256Sums(Http.getText(sumsUrl))[BINARY]
                ?: return@withContext Result.Failed("no checksum for $BINARY")
            val bytes = Http.get(binaryUrl, MAX_BINARY_BYTES)
            val actual = Checksums.sha256Hex(bytes)
            if (actual != expected) return@withContext Result.Failed("checksum mismatch")
            val tmp = File(engine.workDir.parentFile, "yt-dlp.download")
            tmp.writeBytes(bytes)
            val inside = engine.versionInZip(tmp)
            if (inside != release.version) {
                tmp.delete()
                return@withContext Result.Failed("file says $inside, release says ${release.version}")
            }
            engine.installUpdate(tmp, release.version)
            prefs.engineUpdatedAt = now
            Log.i(TAG, "yt-dlp updated to ${release.version}")
            Result.Updated(release.version)
        } catch (e: Exception) {
            if (e.isCancellation()) throw e
            Log.w(TAG, "yt-dlp update failed", e)
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Back to the shipped copy; counts as "recently updated" so we do not loop. */
    fun reset() {
        engine.resetToShipped()
        prefs.engineUpdatedAt = System.currentTimeMillis()
    }

    val recentlyUpdated: Boolean
        get() = System.currentTimeMillis() - prefs.engineUpdatedAt < HOUR_MILLIS

    companion object {
        private const val TAG = "DalmaUpdater"
        private const val LATEST_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
        private const val BINARY = "yt-dlp"
        private const val SUMS = "SHA2-256SUMS"
        private const val MAX_BINARY_BYTES = 30L * 1024 * 1024
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val HOUR_MILLIS = 60L * 60 * 1000
        private val API_HEADERS = mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2022-11-28")
    }
}

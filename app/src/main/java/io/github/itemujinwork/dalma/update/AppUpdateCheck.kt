package io.github.itemujinwork.dalma.update

import android.content.Context
import android.util.Log
import io.github.itemujinwork.dalma.BuildConfig
import io.github.itemujinwork.dalma.core.AppVersion
import io.github.itemujinwork.dalma.core.Releases
import io.github.itemujinwork.dalma.data.Prefs
import io.github.itemujinwork.dalma.engine.Http
import io.github.itemujinwork.dalma.engine.isCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Once a day Dalma looks whether a newer APK is on the Releases page, and says so in one line. */
object AppUpdateCheck {
    private const val TAG = "DalmaUpdateCheck"
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    class Available(val version: String, val url: String)

    suspend fun check(context: Context): Available? = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        if (now - prefs.lastAppCheck > DAY_MILLIS) {
            try {
                val headers = HashMap<String, String>()
                headers["Accept"] = "application/vnd.github+json"
                headers["X-GitHub-Api-Version"] = "2022-11-28"
                if (BuildConfig.UPDATE_TOKEN.isNotBlank()) headers["Authorization"] = "Bearer " + BuildConfig.UPDATE_TOKEN
                val url = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"
                val release = Releases.parseLatest(Http.getText(url, headers = headers))
                prefs.lastAppCheck = now
                if (release != null) {
                    prefs.latestAppVersion = release.version
                    prefs.latestAppUrl = release.htmlUrl ?: "https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest"
                }
            } catch (e: Exception) {
                if (e.isCancellation()) throw e
                Log.i(TAG, "update check skipped: ${e.message}")
            }
        }
        val latest = prefs.latestAppVersion ?: return@withContext null
        if (AppVersion.isNewer(latest, BuildConfig.VERSION_NAME)) Available(latest, prefs.latestAppUrl ?: "https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest") else null
    }
}

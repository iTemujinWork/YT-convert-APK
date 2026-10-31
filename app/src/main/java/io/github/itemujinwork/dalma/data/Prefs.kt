package io.github.itemujinwork.dalma.data

import android.content.Context
import androidx.core.content.edit

/** The few things Dalma remembers between runs. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("dalma", Context.MODE_PRIVATE)

    var lastEngineCheck: Long
        get() = sp.getLong("engine_check", 0L)
        set(value) = sp.edit { putLong("engine_check", value) }

    var engineUpdatedAt: Long
        get() = sp.getLong("engine_updated", 0L)
        set(value) = sp.edit { putLong("engine_updated", value) }

    var lastAppCheck: Long
        get() = sp.getLong("app_check", 0L)
        set(value) = sp.edit { putLong("app_check", value) }

    var latestAppVersion: String?
        get() = sp.getString("app_latest", null)
        set(value) = sp.edit { putString("app_latest", value) }

    var latestAppUrl: String?
        get() = sp.getString("app_latest_url", null)
        set(value) = sp.edit { putString("app_latest_url", value) }

    var notificationAsked: Boolean
        get() = sp.getBoolean("notification_asked", false)
        set(value) = sp.edit { putBoolean("notification_asked", value) }
}

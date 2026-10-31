package io.github.itemujinwork.dalma

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import io.github.itemujinwork.dalma.ui.mascot.MascotAssets
import io.github.itemujinwork.dalma.work.Notifications

class DalmaApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.WARN)
            .build()

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        // Decode Dalma's pictures (if the app has them) before the first sheet needs them.
        MascotAssets.warmUp(this)
    }
}

package io.github.itemujinwork.dalma.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.itemujinwork.dalma.data.Prefs
import io.github.itemujinwork.dalma.engine.Engine
import io.github.itemujinwork.dalma.engine.EngineUpdater
import io.github.itemujinwork.dalma.engine.isCancellation
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Once a day, quietly, and never on the song's critical path: a newer yt-dlp. Scheduled
 * when the app opens and after every save; [EngineUpdater] itself decides whether a day
 * has passed.
 */
class EngineUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        // Never swap the yt-dlp file under a running download.
        val saving = WorkManager.getInstance(app).getWorkInfosByTagFlow(Saves.TAG).first().any { it.state == WorkInfo.State.RUNNING }
        if (saving) return Result.retry()
        val engine = Engine(app)
        try {
            engine.prepare()
            EngineUpdater(engine, Prefs(app)).updateIfStale()
        } catch (e: Exception) {
            if (e.isCancellation()) throw e
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "engine-update"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<EngineUpdateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 2, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}

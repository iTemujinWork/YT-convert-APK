package io.github.itemujinwork.dalma.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.itemujinwork.dalma.core.Target
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Enqueues and observes one save per target; sharing the same song twice does not start it twice. */
object Saves {
    const val TAG = "save"

    fun uniqueName(target: Target): String = "save:" + target.canonicalUrl

    fun enqueue(context: Context, target: Target): UUID {
        val request = OneTimeWorkRequestBuilder<SaveWorker>()
            .setInputData(Protocol.targetData(target))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(target), ExistingWorkPolicy.KEEP, request)
        return request.id
    }

    /** The work for this target: ours if it exists, otherwise a running duplicate, never a stale finished one. */
    fun observe(context: Context, target: Target, ownId: UUID): Flow<WorkInfo?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(uniqueName(target)).map { infos ->
            infos.firstOrNull { it.id == ownId } ?: infos.firstOrNull { !it.state.isFinished }
        }

    fun cancel(context: Context, target: Target) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(target))
    }
}

package io.github.itemujinwork.dalma.share

import android.content.Context
import androidx.work.WorkInfo
import io.github.itemujinwork.dalma.core.FailureKind
import io.github.itemujinwork.dalma.core.Outcome
import io.github.itemujinwork.dalma.core.Target
import io.github.itemujinwork.dalma.data.Library
import io.github.itemujinwork.dalma.work.Connectivity
import io.github.itemujinwork.dalma.work.Protocol
import io.github.itemujinwork.dalma.work.SavePhase
import io.github.itemujinwork.dalma.work.SaveProgress
import io.github.itemujinwork.dalma.work.Saves
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the sheet needs to know, distilled from WorkManager's state. */
sealed interface ShareStatus {
    data object Starting : ShareStatus

    data class Working(val fraction: Float?, val phase: SavePhase, val index: Int, val count: Int) : ShareStatus

    /** Queued until the phone is online again; the download then runs by itself. */
    data object WaitingForNetwork : ShareStatus

    data class Finished(val outcome: Outcome) : ShareStatus
}

class ShareController(private val context: Context) {
    /** "Ez már megvan.": answered before anything starts. */
    suspend fun alreadyHave(target: Target): Boolean =
        target is Target.Video && Library(context).findExisting(target.videoId) != null

    fun start(target: Target): Flow<ShareStatus> {
        val id = Saves.enqueue(context, target)
        val offline = !Connectivity.isOnline(context)
        return Saves.observe(context, target, id).map { info -> toStatus(info, offline) }
    }

    private fun toStatus(info: WorkInfo?, offline: Boolean): ShareStatus {
        if (info == null) return if (offline) ShareStatus.WaitingForNetwork else ShareStatus.Starting
        return when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                if (offline || info.runAttemptCount > 0) ShareStatus.WaitingForNetwork else ShareStatus.Starting
            WorkInfo.State.RUNNING -> {
                val progress = SaveProgress.from(info.progress)
                if (progress?.phase == SavePhase.WAITING_NETWORK) {
                    ShareStatus.WaitingForNetwork
                } else {
                    ShareStatus.Working(progress?.fraction, progress?.phase ?: SavePhase.PREPARING, progress?.index ?: 0, progress?.count ?: 0)
                }
            }
            WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED ->
                ShareStatus.Finished(Protocol.outcome(info.outputData) ?: Outcome.Failed(FailureKind.UNKNOWN, null))
            WorkInfo.State.CANCELLED -> ShareStatus.Finished(Outcome.Failed(FailureKind.UNKNOWN, "cancelled"))
        }
    }
}

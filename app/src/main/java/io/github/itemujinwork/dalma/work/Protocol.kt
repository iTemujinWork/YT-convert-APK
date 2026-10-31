package io.github.itemujinwork.dalma.work

import androidx.work.Data
import androidx.work.workDataOf
import io.github.itemujinwork.dalma.core.FailureKind
import io.github.itemujinwork.dalma.core.Outcome
import io.github.itemujinwork.dalma.core.SavedSong
import io.github.itemujinwork.dalma.core.Target

/** What the worker tells the sheet while it runs. */
enum class SavePhase { PREPARING, UNPACKING, DOWNLOADING, PROCESSING, SAVING, WAITING_NETWORK }

class SaveProgress(val phase: SavePhase, val fraction: Float?, val index: Int, val count: Int) {
    fun toData(): Data = workDataOf(
        KEY_PHASE to phase.name,
        KEY_FRACTION to (fraction ?: -1f),
        KEY_INDEX to index,
        KEY_COUNT to count,
    )

    companion object {
        const val KEY_PHASE = "phase"
        const val KEY_FRACTION = "fraction"
        const val KEY_INDEX = "index"
        const val KEY_COUNT = "count"

        fun from(data: Data): SaveProgress? {
            val phase = data.getString(KEY_PHASE)?.let { name -> SavePhase.entries.firstOrNull { it.name == name } } ?: return null
            val fraction = data.getFloat(KEY_FRACTION, -1f).takeIf { it >= 0f }
            return SaveProgress(phase, fraction, data.getInt(KEY_INDEX, 0), data.getInt(KEY_COUNT, 0))
        }
    }
}

/** Target and outcome travel through WorkManager's small key-value Data. */
object Protocol {
    private const val KEY_KIND = "target_kind"
    private const val KEY_ID = "target_id"
    private const val KEY_OUTCOME = "outcome"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_SAVED = "saved"
    private const val KEY_HAD = "had"
    private const val KEY_FAILED = "failed"
    private const val KEY_CAPPED = "capped"
    private const val KEY_FAILURE = "failure"
    private const val KEY_DETAIL = "detail"

    fun targetData(target: Target): Data = when (target) {
        is Target.Video -> workDataOf(KEY_KIND to "video", KEY_ID to target.videoId)
        is Target.Playlist -> workDataOf(KEY_KIND to "playlist", KEY_ID to target.listId)
    }

    fun target(data: Data): Target? {
        val id = data.getString(KEY_ID) ?: return null
        return runCatching {
            when (data.getString(KEY_KIND)) {
                "video" -> Target.Video(id)
                "playlist" -> Target.Playlist(id)
                else -> null
            }
        }.getOrNull()
    }

    fun outcomeData(outcome: Outcome): Data = when (outcome) {
        is Outcome.Saved -> workDataOf(KEY_OUTCOME to "saved", KEY_TITLE to outcome.song.title, KEY_ARTIST to outcome.song.artist)
        is Outcome.SavedMany -> workDataOf(
            KEY_OUTCOME to "many", KEY_SAVED to outcome.saved, KEY_HAD to outcome.alreadyHad,
            KEY_FAILED to outcome.failed, KEY_CAPPED to outcome.capped,
        )
        Outcome.AlreadyHave -> workDataOf(KEY_OUTCOME to "have")
        is Outcome.Failed -> workDataOf(KEY_OUTCOME to "failed", KEY_FAILURE to outcome.kind.name, KEY_DETAIL to outcome.detail)
    }

    fun outcome(data: Data): Outcome? = when (data.getString(KEY_OUTCOME)) {
        "saved" -> Outcome.Saved(
            SavedSong(videoId = "", uri = "", fileName = "", title = data.getString(KEY_TITLE), artist = data.getString(KEY_ARTIST), savedAtMillis = 0L),
        )
        "many" -> Outcome.SavedMany(data.getInt(KEY_SAVED, 0), data.getInt(KEY_HAD, 0), data.getInt(KEY_FAILED, 0), data.getBoolean(KEY_CAPPED, false))
        "have" -> Outcome.AlreadyHave
        "failed" -> Outcome.Failed(
            data.getString(KEY_FAILURE)?.let { name -> FailureKind.entries.firstOrNull { it.name == name } } ?: FailureKind.UNKNOWN,
            data.getString(KEY_DETAIL),
        )
        else -> null
    }
}

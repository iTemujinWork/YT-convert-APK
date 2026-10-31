package io.github.itemujinwork.dalma.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.StatFs
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.itemujinwork.dalma.R
import io.github.itemujinwork.dalma.core.Decision
import io.github.itemujinwork.dalma.core.EngineEvent
import io.github.itemujinwork.dalma.core.EngineOutput
import io.github.itemujinwork.dalma.core.EngineRequest
import io.github.itemujinwork.dalma.core.ErrorClassifier
import io.github.itemujinwork.dalma.core.Failure
import io.github.itemujinwork.dalma.core.FailureKind
import io.github.itemujinwork.dalma.core.FileNames
import io.github.itemujinwork.dalma.core.Outcome
import io.github.itemujinwork.dalma.core.ProgressTracker
import io.github.itemujinwork.dalma.core.Remedy
import io.github.itemujinwork.dalma.core.RetryPlanner
import io.github.itemujinwork.dalma.core.SavedSong
import io.github.itemujinwork.dalma.core.SpaceCheck
import io.github.itemujinwork.dalma.core.Strategy
import io.github.itemujinwork.dalma.core.Target
import io.github.itemujinwork.dalma.core.YtDlpArgs
import io.github.itemujinwork.dalma.data.Library
import io.github.itemujinwork.dalma.data.MusicStore
import io.github.itemujinwork.dalma.data.Prefs
import io.github.itemujinwork.dalma.engine.Engine
import io.github.itemujinwork.dalma.engine.EngineUpdater
import io.github.itemujinwork.dalma.engine.isCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * What happens after a share, step by step: engine ready → (daily yt-dlp update) →
 * download → tags and cover → Music folder → done. Failures go through [RetryPlanner]:
 * update the engine, ask other player clients, take any format, skip the extras...
 * and only then does Dalma apologise.
 */
class SaveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app = applicationContext
    private val progress = MutableStateFlow(SaveProgress(SavePhase.PREPARING, null, 0, 0))

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(app.getString(R.string.notification_preparing), null)

    private fun foregroundInfo(title: String, fraction: Float?): ForegroundInfo {
        val cancel = WorkManager.getInstance(app).createCancelPendingIntent(id)
        val notification = Notifications.saving(app, title, fraction, cancel)
        return ForegroundInfo(Notifications.SAVE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override suspend fun doWork(): Result {
        val target = Protocol.target(inputData) ?: return fail(Outcome.Failed(FailureKind.NOT_YOUTUBE, null))
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            // Started while the app is in the background on Android 14: keep going without the notification.
            Log.w(TAG, "no foreground service: ${e.message}")
        }
        val publisher = CoroutineScope(Dispatchers.Default + Job()).launch { publishLoop() }
        return try {
            save(target)
        } finally {
            publisher.cancel()
            // Once a day, quietly, after the song: a newer yt-dlp.
            EngineUpdateWorker.enqueue(app)
        }
    }

    /** Progress and the notification are updated calmly, not on every line. */
    private suspend fun publishLoop() {
        var lastNotified = 0L
        progress.collectLatest { p ->
            runCatching { setProgress(p.toData()) }
            val now = System.currentTimeMillis()
            if (now - lastNotified > 900 || p.phase == SavePhase.SAVING) {
                lastNotified = now
                val title = if (p.count > 1) app.getString(R.string.notification_title_many, p.index, p.count) else app.getString(R.string.notification_title)
                runCatching { setForeground(foregroundInfo(title, p.fraction)) }
            }
            delay(120)
        }
    }

    private suspend fun save(target: Target): Result {
        val engine = Engine(app)
        val prefs = Prefs(app)
        val library = Library(app)
        val store = MusicStore(app)
        val updater = EngineUpdater(engine, prefs)

        // 1. The engine. On the first run this unpacks for a few seconds and the clip shows it.
        val unpacking = !engine.isUnpacked
        progress.value = SaveProgress(SavePhase.PREPARING, null, 0, 0)
        engine.prepare { fraction ->
            progress.value = SaveProgress(SavePhase.UNPACKING, fraction * UNPACK_SHARE, 0, 0)
        }
        engine.cleanWorkDir(keepPartials = true)

        // 2. Attempts, each one a little different from the last. (The daily yt-dlp update
        //    runs after the save, off the song's critical path: see EngineUpdateWorker.)
        var strategy = Strategy.DEFAULT
        val applied = mutableSetOf<Remedy>()
        val timesApplied = mutableMapOf<Remedy, Int>()
        var attempts = 0
        val counts = Counts()
        var lastSaved: SavedSong? = null
        var listTotal: Int? = null
        val base = if (unpacking) UNPACK_SHARE else 0f

        while (true) {
            attempts++
            val archive = File(engine.workDir, "archive.txt")
            library.writeArchive(archive)
            val request = EngineRequest(target, engine.workDir.absolutePath, archive.absolutePath)
            val tracker = ProgressTracker()
            val titles = HashMap<String, EngineEvent.Meta>()
            var index = 0

            val result = try {
                engine.run(YtDlpArgs.build(request, strategy)) { line ->
                    val event = EngineOutput.parse(line) ?: return@run
                    when (event) {
                        is EngineEvent.Meta -> {
                            if (event.isLive) throw Engine.Abort(Failure(FailureKind.LIVE, "live"))
                            event.id?.let { titles[it] = event }
                            index = event.playlistIndex ?: (index + 1)
                            event.playlistCount?.let { listTotal = it }
                        }
                        is EngineEvent.Plan -> {
                            if (!SpaceCheck.fits(event.sizeBytes, freeBytes(engine.workDir))) {
                                throw Engine.Abort(Failure(FailureKind.DISK_FULL, "space"))
                            }
                        }
                        is EngineEvent.Processing -> progress.value = SaveProgress(SavePhase.PROCESSING, progress.value.fraction, index, listTotal ?: counts.expected())
                        is EngineEvent.Output -> {
                            progress.value = SaveProgress(SavePhase.SAVING, progress.value.fraction, index, listTotal ?: counts.expected())
                            val meta = event.id?.let { titles[it] }
                            val file = File(event.path)
                            try {
                                val song = runBlocking { keep(file, event.id ?: meta?.id ?: file.nameWithoutExtension, meta, store, library) }
                                lastSaved = song
                                counts.saved++
                            } catch (e: Exception) {
                                if (e.isCancellation()) throw e
                                Log.w(TAG, "cannot keep ${file.name}", e)
                                counts.failed++
                            } finally {
                                file.delete()
                            }
                        }
                        is EngineEvent.AlreadyHave -> counts.alreadyHad++
                        is EngineEvent.PlaylistDone -> event.count?.let { listTotal = it }
                        is EngineEvent.Progress -> Unit
                    }
                    tracker.onEvent(event)?.let { fraction ->
                        val phase = if (event is EngineEvent.Output || event is EngineEvent.Processing) progress.value.phase else SavePhase.DOWNLOADING
                        progress.value = SaveProgress(phase, base + (1f - base) * fraction, index, listTotal ?: counts.expected())
                    }
                }
            } catch (abort: Engine.Abort) {
                val failure = abort.reason as Failure
                engine.cleanWorkDir(keepPartials = failure.kind != FailureKind.DISK_FULL)
                return finish(target, counts, lastSaved, listTotal, failure)
            }

            if (result.exitCode == 0) {
                engine.cleanWorkDir(keepPartials = false)
                return finish(target, counts, lastSaved, listTotal, null)
            }

            val failure = ErrorClassifier.classify(result.stderr)
            Log.w(TAG, "attempt $attempts failed: ${failure.kind} (${failure.detail})")
            // A playlist where most songs came through is a result, not a failure.
            if (target is Target.Playlist && counts.saved + counts.alreadyHad > 0) {
                counts.failed += result.stderr.lineSequence().count { it.trimStart().startsWith("ERROR") }.coerceAtLeast(1)
                engine.cleanWorkDir(keepPartials = false)
                return finish(target, counts, lastSaved, listTotal, null)
            }

            when (val decision = RetryPlanner.decide(failure.kind, attempts, applied, updater.recentlyUpdated)) {
                Decision.GiveUp -> {
                    engine.cleanWorkDir(keepPartials = !failure.kind.isFinal)
                    return finish(target, counts, lastSaved, listTotal, failure)
                }
                Decision.WaitForNetwork -> {
                    progress.value = SaveProgress(SavePhase.WAITING_NETWORK, null, index, listTotal ?: 0)
                    delay(400)
                    return Result.retry()
                }
                is Decision.Apply -> {
                    val remedy = decision.remedy
                    applied += remedy
                    val times = timesApplied.getOrDefault(remedy, 0)
                    timesApplied[remedy] = times + 1
                    when (remedy) {
                        Remedy.UPDATE_ENGINE -> runCatching { updater.updateIfStale(force = true) }.onFailure { if (it.isCancellation()) throw it }
                        Remedy.RESET_ENGINE -> runCatching { updater.reset() }
                        Remedy.RETRY_SOON, Remedy.WAIT_A_BIT -> delay(RetryPlanner.delayMillis(remedy, times))
                        Remedy.OTHER_CLIENTS, Remedy.ANY_FORMAT, Remedy.SIMPLE_PROCESSING -> strategy = RetryPlanner.adjust(strategy, remedy)
                    }
                }
            }
        }
    }

    /** Moves a finished file into the Music folder and remembers it. */
    private suspend fun keep(file: File, videoId: String, meta: EngineEvent.Meta?, store: MusicStore, library: Library): SavedSong {
        val name = FileNames.forTrack(meta?.artist, meta?.title, videoId, file.extension.ifEmpty { "opus" })
        val uri = store.save(file, name)
        val song = SavedSong(
            videoId = videoId,
            uri = uri.toString(),
            fileName = name,
            title = meta?.title,
            artist = meta?.artist,
            sizeBytes = file.length(),
            savedAtMillis = System.currentTimeMillis(),
        )
        library.add(song)
        return song
    }

    private fun finish(target: Target, counts: Counts, lastSaved: SavedSong?, listTotal: Int?, failure: Failure?): Result {
        val outcome: Outcome = when {
            target is Target.Playlist && (counts.saved > 0 || counts.alreadyHad > 0) -> {
                if (counts.saved == 0 && counts.failed == 0) Outcome.AlreadyHave
                else Outcome.SavedMany(counts.saved, counts.alreadyHad, counts.failed, (listTotal ?: 0) >= EngineRequest.MAX_PLAYLIST_ITEMS)
            }
            lastSaved != null -> Outcome.Saved(lastSaved)
            counts.alreadyHad > 0 -> Outcome.AlreadyHave
            failure != null -> Outcome.Failed(failure.kind, failure.detail)
            else -> Outcome.Failed(FailureKind.UNKNOWN, null)
        }
        return if (outcome is Outcome.Failed) fail(outcome) else Result.success(Protocol.outcomeData(outcome))
    }

    private fun fail(outcome: Outcome.Failed): Result = Result.failure(Protocol.outcomeData(outcome))

    private fun freeBytes(dir: File): Long = try {
        StatFs(dir.absolutePath).availableBytes
    } catch (_: Exception) {
        Long.MAX_VALUE
    }

    private class Counts(var saved: Int = 0, var alreadyHad: Int = 0, var failed: Int = 0) {
        fun expected(): Int = saved + alreadyHad + failed
    }

    companion object {
        private const val TAG = "DalmaSave"

        /** On a first run, unpacking the engine is the first quarter of the hair clip. */
        private const val UNPACK_SHARE = 0.25f
    }
}

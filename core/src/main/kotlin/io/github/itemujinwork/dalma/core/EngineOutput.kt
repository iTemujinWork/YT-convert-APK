package io.github.itemujinwork.dalma.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Something the engine told us, decoded from the marker lines [YtDlpArgs] asks for. */
sealed interface EngineEvent {
    /** A song is about to be downloaded. */
    data class Meta(
        val id: String?,
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationSeconds: Double?,
        val liveStatus: String?,
        /** 1-based position inside a playlist, `null` for a single song. */
        val playlistIndex: Int?,
        val playlistCount: Int?,
    ) : EngineEvent {
        val isLive: Boolean get() = liveStatus == "is_live" || liveStatus == "is_upcoming"
    }

    /** Which stream will be downloaded and how big it is, when known. */
    data class Plan(val id: String?, val formatId: String?, val sizeBytes: Long?) : EngineEvent

    data class Progress(
        val status: String,
        val downloadedBytes: Long?,
        val totalBytes: Long?,
        val speedBytesPerSecond: Double?,
        val etaSeconds: Long?,
        val formatId: String?,
        val id: String?,
    ) : EngineEvent

    /** Downloading is over for this song, tagging and cover art start. */
    data class Processing(val id: String?) : EngineEvent

    /** A finished file in the work directory. */
    data class Output(val id: String?, val path: String) : EngineEvent

    /** yt-dlp skipped a song because the archive says we have it. */
    data class AlreadyHave(val id: String?) : EngineEvent

    /** The whole playlist has been walked. */
    data class PlaylistDone(val count: Int?) : EngineEvent
}

object EngineOutput {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val ARCHIVE_LINE = Regex("""\[download]\s+(\S+?):\s+${Regex.escape(YtDlpArgs.ARCHIVE_SKIP)}""")

    fun parse(rawLine: String): EngineEvent? {
        val line = rawLine.trim()
        return when {
            line.startsWith(YtDlpArgs.MARK_PROGRESS) -> parseProgress(payload(line, YtDlpArgs.MARK_PROGRESS))
            line.startsWith(YtDlpArgs.MARK_META) -> parseMeta(payload(line, YtDlpArgs.MARK_META))
            line.startsWith(YtDlpArgs.MARK_PLAN) -> parsePlan(payload(line, YtDlpArgs.MARK_PLAN))
            line.startsWith(YtDlpArgs.MARK_PHASE) -> EngineEvent.Processing(id(payload(line, YtDlpArgs.MARK_PHASE)))
            line.startsWith(YtDlpArgs.MARK_FILE) -> parseFile(payload(line, YtDlpArgs.MARK_FILE))
            line.startsWith(YtDlpArgs.MARK_LIST) -> EngineEvent.PlaylistDone(payload(line, YtDlpArgs.MARK_LIST).toIntOrNull())
            line.contains(YtDlpArgs.ARCHIVE_SKIP) -> EngineEvent.AlreadyHave(ARCHIVE_LINE.find(line)?.groupValues?.get(1))
            else -> null
        }
    }

    private fun payload(line: String, mark: String) = line.removePrefix(mark).trim()

    private fun id(text: String): String? = text.takeUnless { it.isBlank() || it == "NA" }

    private fun parseProgress(payload: String): EngineEvent.Progress? {
        val parts = payload.split('|')
        if (parts.size < 8) return null
        return EngineEvent.Progress(
            status = parts[0],
            downloadedBytes = number(parts[1])?.toLong(),
            totalBytes = number(parts[2])?.toLong() ?: number(parts[3])?.toLong(),
            speedBytesPerSecond = number(parts[4]),
            etaSeconds = number(parts[5])?.toLong(),
            formatId = id(parts[6]),
            id = id(parts[7]),
        )
    }

    private fun parseMeta(payload: String): EngineEvent.Meta? {
        val obj = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        fun text(key: String) = (obj[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
        val artist = text("meta_artist")
            ?: text("artist")
            ?: (text("uploader") ?: text("channel"))?.replace(Regex("""\s*-\s*Topic\s*$"""), "")?.takeIf { it.isNotBlank() }
        return EngineEvent.Meta(
            id = text("id"),
            title = text("meta_title") ?: text("title"),
            artist = artist,
            album = text("album"),
            durationSeconds = (obj["duration"] as? JsonPrimitive)?.doubleOrNull,
            liveStatus = text("live_status"),
            playlistIndex = (obj["playlist_index"] as? JsonPrimitive)?.intOrNull,
            playlistCount = (obj["playlist_count"] as? JsonPrimitive)?.intOrNull,
        )
    }

    private fun parsePlan(payload: String): EngineEvent.Plan? {
        val parts = payload.split('|')
        if (parts.size < 4) return null
        return EngineEvent.Plan(
            id = id(parts[0]),
            formatId = id(parts[1]),
            sizeBytes = number(parts[2])?.toLong() ?: number(parts[3])?.toLong(),
        )
    }

    private fun parseFile(payload: String): EngineEvent.Output? {
        val separator = payload.indexOf('|')
        if (separator < 0) return null
        val path = payload.substring(separator + 1).trim()
        if (path.isEmpty() || path == "NA") return null
        return EngineEvent.Output(id(payload.substring(0, separator)), path)
    }

    private fun number(text: String): Double? =
        text.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}

/**
 * Turns the engine's chatter into one calm number for the hair clip: 0..1 for the whole
 * job, never going backwards. A playlist of N songs fills in N equal steps.
 */
class ProgressTracker {
    private var songsExpected: Int? = null
    private var songsDone = 0
    private var currentId: String? = null
    private var currentDone = 0L
    private var currentTotal: Long? = null
    private var best = 0f

    /** 0..1 once anything is known, `null` while we only wait. */
    var fraction: Float? = null
        private set

    var speedBytesPerSecond: Double? = null
        private set

    fun onEvent(event: EngineEvent): Float? {
        when (event) {
            is EngineEvent.Meta -> {
                event.playlistCount?.let { songsExpected = it }
                startSong(event.id)
            }
            is EngineEvent.Plan -> {
                startSong(event.id)
                event.sizeBytes?.let { currentTotal = it }
            }
            is EngineEvent.Progress -> {
                startSong(event.id)
                event.totalBytes?.let { currentTotal = it }
                event.downloadedBytes?.let { currentDone = it }
                speedBytesPerSecond = event.speedBytesPerSecond
                if (event.status == "finished") currentDone = currentTotal ?: currentDone
            }
            is EngineEvent.Processing -> {
                currentTotal?.let { currentDone = it }
            }
            is EngineEvent.Output -> {
                songsDone++
                currentId = null
                currentDone = 0
                currentTotal = null
            }
            is EngineEvent.AlreadyHave, is EngineEvent.PlaylistDone -> Unit
        }
        val value = compute()?.coerceIn(0f, 1f) ?: return fraction
        if (value > best) best = value
        fraction = best
        return fraction
    }

    private fun startSong(id: String?) {
        if (id != null && id != currentId) {
            currentId = id
            currentDone = 0
            currentTotal = null
        }
    }

    private fun compute(): Float? {
        val expected = (songsExpected ?: 1).coerceAtLeast(1)
        val songFraction = currentTotal?.takeIf { it > 0 }?.let { currentDone.toFloat() / it }
        if (songFraction == null && songsDone == 0) return null
        return (songsDone + (songFraction ?: 0f)) / expected
    }
}

package io.github.itemujinwork.hanga.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Something the engine told us, decoded from the marker lines [YtDlpArgs] asks for. */
sealed interface EngineEvent {
    data class Meta(
        val id: String?,
        val title: String?,
        val uploader: String?,
        val artist: String?,
        val track: String?,
        val album: String?,
        val durationSeconds: Double?,
        val liveStatus: String?,
    ) : EngineEvent {
        val isLive: Boolean get() = liveStatus == "is_live" || liveStatus == "is_upcoming"

        /** "Artist - Topic" channels are YouTube Music auto-generated ones. */
        val author: String?
            get() = artist?.takeIf { it.isNotBlank() }
                ?: uploader?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() }
    }

    /** Which streams will be downloaded, in order, with their sizes when known. */
    data class Plan(val formatIds: List<String>, val sizes: List<Long?>) : EngineEvent

    data class Progress(
        val status: String,
        val downloadedBytes: Long?,
        val totalBytes: Long?,
        val speedBytesPerSecond: Double?,
        val etaSeconds: Long?,
        val formatId: String?,
    ) : EngineEvent

    /** Downloading is over, converting / merging / tagging starts. */
    data object Processing : EngineEvent

    data class Output(val path: String) : EngineEvent
}

object EngineOutput {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawLine: String): EngineEvent? {
        val line = rawLine.trim()
        return when {
            line.startsWith(YtDlpArgs.MARK_PROGRESS) -> parseProgress(payload(line, YtDlpArgs.MARK_PROGRESS))
            line.startsWith(YtDlpArgs.MARK_META) -> parseMeta(payload(line, YtDlpArgs.MARK_META))
            line.startsWith(YtDlpArgs.MARK_PLAN) -> parsePlan(payload(line, YtDlpArgs.MARK_PLAN))
            line.startsWith(YtDlpArgs.MARK_PHASE) -> EngineEvent.Processing
            line.startsWith(YtDlpArgs.MARK_FILE) -> payload(line, YtDlpArgs.MARK_FILE)
                .takeIf { it.isNotEmpty() && it != "NA" }
                ?.let { EngineEvent.Output(it) }
            else -> null
        }
    }

    private fun payload(line: String, mark: String) = line.removePrefix(mark).trim()

    private fun parseProgress(payload: String): EngineEvent.Progress? {
        val parts = payload.split('|')
        if (parts.size < 7) return null
        return EngineEvent.Progress(
            status = parts[0],
            downloadedBytes = number(parts[1])?.toLong(),
            totalBytes = number(parts[2])?.toLong() ?: number(parts[3])?.toLong(),
            speedBytesPerSecond = number(parts[4]),
            etaSeconds = number(parts[5])?.toLong(),
            formatId = parts[6].takeUnless { it.isBlank() || it == "NA" },
        )
    }

    private fun parseMeta(payload: String): EngineEvent.Meta? {
        val obj = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        fun text(key: String) = (obj[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
        return EngineEvent.Meta(
            id = text("id"),
            title = text("title"),
            uploader = text("uploader") ?: text("channel"),
            artist = text("artist"),
            track = text("track"),
            album = text("album"),
            durationSeconds = (obj["duration"] as? JsonPrimitive)?.doubleOrNull,
            liveStatus = text("live_status"),
        )
    }

    private fun parsePlan(payload: String): EngineEvent.Plan? {
        val parts = payload.split('|')
        if (parts.size < 6) return null
        val ids = jsonList(parts[1])?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
        if (ids != null && ids.isNotEmpty()) {
            val exact = jsonList(parts[2]).orEmpty()
            val approx = jsonList(parts[3]).orEmpty()
            val sizes = ids.indices.map { i -> size(exact.getOrNull(i)) ?: size(approx.getOrNull(i)) }
            return EngineEvent.Plan(ids, sizes)
        }
        val single = parts[0].takeUnless { it.isBlank() || it == "NA" } ?: return null
        return EngineEvent.Plan(listOf(single), listOf(number(parts[4])?.toLong() ?: number(parts[5])?.toLong()))
    }

    private fun jsonList(text: String): JsonArray? =
        if (text.isBlank() || text == "NA") null
        else runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()

    private fun size(element: JsonElement?): Long? =
        (element as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }?.toLong()

    private fun number(text: String): Double? =
        text.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}

/**
 * Turns per-stream progress into one calm number. A video is two downloads (picture,
 * then sound) and the bar must not jump back to zero in between, nor ever go backwards.
 */
class ProgressTracker {
    private var plan: EngineEvent.Plan? = null
    private val finished = LinkedHashMap<String, Long>()
    private var currentId: String? = null
    private var currentDone = 0L
    private var currentTotal: Long? = null
    private val seenTotals = HashMap<String, Long>()
    private var best = 0f

    /** 0..1 once anything is known, `null` while we only wait. */
    var fraction: Float? = null
        private set

    var speedBytesPerSecond: Double? = null
        private set

    var etaSeconds: Long? = null
        private set

    fun onPlan(newPlan: EngineEvent.Plan) {
        plan = newPlan
    }

    fun onProgress(event: EngineEvent.Progress): Float? {
        val id = event.formatId ?: currentId ?: plan?.formatIds?.firstOrNull() ?: "?"
        if (id != currentId) {
            currentId = id
            currentDone = 0
            currentTotal = null
        }
        event.totalBytes?.let {
            currentTotal = it
            seenTotals[id] = it
        }
        event.downloadedBytes?.let { currentDone = it }
        if (event.status == "finished") {
            finished[id] = currentTotal ?: currentDone
            currentDone = currentTotal ?: currentDone
        }
        speedBytesPerSecond = event.speedBytesPerSecond
        etaSeconds = event.etaSeconds
        val value = compute(id)?.coerceIn(0f, 1f) ?: return fraction
        if (value > best) best = value
        fraction = best
        return fraction
    }

    private fun compute(currentId: String): Float? {
        val ids = plan?.formatIds?.takeIf { it.isNotEmpty() } ?: listOf(currentId)
        if (ids.size == 1) {
            val total = currentTotal ?: return null
            if (total <= 0) return null
            return currentDone.toFloat() / total
        }
        val sizes = ids.mapIndexed { i, id -> plan?.sizes?.getOrNull(i) ?: seenTotals[id] }
        val index = ids.indexOf(currentId)
        if (sizes.all { it != null && it > 0 }) {
            val all = sizes.sumOf { it!! }.toFloat()
            var done = 0f
            ids.forEachIndexed { i, id ->
                done += when {
                    id in finished -> sizes[i]!!.toFloat()
                    id == currentId -> currentDone.toFloat().coerceAtMost(sizes[i]!!.toFloat())
                    else -> 0f
                }
            }
            return done / all
        }
        // Sizes unknown: picture is the big part of a video.
        val weights = if (ids.size == 2) listOf(0.85f, 0.15f) else List(ids.size) { 1f / ids.size }
        var done = 0f
        ids.forEachIndexed { i, id ->
            done += when {
                id in finished -> weights[i]
                id == currentId -> {
                    val total = currentTotal
                    if (total != null && total > 0) weights[i] * (currentDone.toFloat() / total) else 0f
                }
                i < index -> weights[i]
                else -> 0f
            }
        }
        return done
    }
}

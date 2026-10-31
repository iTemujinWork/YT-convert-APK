package io.github.itemujinwork.hanga.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/** One line in "Recent": a finished download or one that could not be done. */
@Serializable
data class HistoryEntry(
    val id: String,
    val videoId: String,
    val kind: MediaKind,
    val title: String,
    val author: String? = null,
    val status: Status = Status.DONE,
    val failure: FailureKind? = null,
    val failureDetail: String? = null,
    /** content:// address of the saved file. */
    val uri: String? = null,
    val sizeBytes: Long? = null,
    val durationSeconds: Long? = null,
    val createdAtMillis: Long,
) {
    @Serializable
    enum class Status { DONE, FAILED }
}

object HistoryCodec {
    const val MAX_ENTRIES = 100

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = false
    }

    fun encode(entries: List<HistoryEntry>): String =
        json.encodeToString(ListSerializer(HistoryEntry.serializer()), entries)

    /** Never throws: a damaged file costs the damaged entries, not the whole list. */
    fun decode(text: String?): List<HistoryEntry> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(HistoryEntry.serializer(), element) }.getOrNull()
        }
    }

    /** Newest first; a finished entry replaces an older one of the same video and kind. */
    fun add(entries: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> =
        (listOf(entry) + entries.filterNot { it.id == entry.id || (it.videoId == entry.videoId && it.kind == entry.kind) })
            .take(MAX_ENTRIES)
}

package io.github.itemujinwork.dalma.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/** One saved song: this is how Dalma knows "Ez már megvan." */
@Serializable
data class SavedSong(
    val videoId: String,
    /** content:// address of the file in the Music folder. */
    val uri: String,
    val fileName: String,
    val title: String? = null,
    val artist: String? = null,
    val sizeBytes: Long? = null,
    val savedAtMillis: Long,
)

object LibraryCodec {
    const val MAX_ENTRIES = 5000

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = false
    }

    fun encode(songs: List<SavedSong>): String =
        json.encodeToString(ListSerializer(SavedSong.serializer()), songs)

    /** Never throws: a damaged file costs the damaged entries, not the whole list. */
    fun decode(text: String?): List<SavedSong> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(SavedSong.serializer(), element) }.getOrNull()
        }
    }

    /** Newest first, one entry per video. */
    fun add(songs: List<SavedSong>, song: SavedSong): List<SavedSong> =
        (listOf(song) + songs.filterNot { it.videoId == song.videoId }).take(MAX_ENTRIES)

    fun remove(songs: List<SavedSong>, videoId: String): List<SavedSong> = songs.filterNot { it.videoId == videoId }

    /** The yt-dlp download archive: it makes a playlist run skip what we already have. */
    fun archive(songs: List<SavedSong>): String =
        songs.asSequence().map { it.videoId }.filter { Target.VIDEO_ID.matches(it) }.distinct()
            .joinToString("") { "youtube $it\n" }
}

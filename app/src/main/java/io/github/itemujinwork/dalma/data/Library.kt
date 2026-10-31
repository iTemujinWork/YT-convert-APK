package io.github.itemujinwork.dalma.data

import android.content.Context
import android.net.Uri
import io.github.itemujinwork.dalma.core.LibraryCodec
import io.github.itemujinwork.dalma.core.SavedSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** The small local list of saved songs: video id → file. This is how "Ez már megvan." works. */
class Library(context: Context, private val store: MusicStore = MusicStore(context)) {
    private val file = File(context.applicationContext.filesDir, "library.json")
    private val mutex = Mutex()

    suspend fun all(): List<SavedSong> = mutex.withLock { read() }

    /** The song if its file is still there; a vanished file is forgotten so it gets saved again. */
    suspend fun findExisting(videoId: String): SavedSong? {
        val song = all().firstOrNull { it.videoId == videoId } ?: return null
        if (store.exists(Uri.parse(song.uri))) return song
        remove(videoId)
        return null
    }

    suspend fun add(song: SavedSong) = mutex.withLock { write(LibraryCodec.add(read(), song)) }

    suspend fun remove(videoId: String) = mutex.withLock { write(LibraryCodec.remove(read(), videoId)) }

    /** Drops entries whose files are gone, then writes the yt-dlp archive for a run. */
    suspend fun writeArchive(target: File) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val songs = read()
            val ids = songs.mapNotNull { song -> store.idOf(Uri.parse(song.uri))?.let { it to song } }
            val existing = store.existingIds(ids.map { it.first })
            val kept = songs.filter { song ->
                val id = store.idOf(Uri.parse(song.uri))
                id == null || id in existing
            }
            if (kept.size != songs.size) write(kept)
            target.writeText(LibraryCodec.archive(kept))
        }
    }

    private fun read(): List<SavedSong> = LibraryCodec.decode(file.takeIf { it.isFile }?.readText())

    private fun write(songs: List<SavedSong>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(LibraryCodec.encode(songs))
        if (!tmp.renameTo(file)) {
            file.writeText(LibraryCodec.encode(songs))
            tmp.delete()
        }
    }
}

package io.github.itemujinwork.dalma.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.graphics.scale
import io.github.itemujinwork.dalma.core.TagScript
import io.github.itemujinwork.dalma.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes what was typed into the song's own file, the way the brief has it: an Opus gets Vorbis
 * comments and a METADATA_BLOCK_PICTURE, an AAC its iTunes tags; no database. The file comes into
 * the cache, the engine's Python writes it with mutagen, and the bytes go back through MediaStore:
 * Dalma's own files at once, others with Android's consent.
 */
class SongTags(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val store = MusicStore(app)
    private val engine = Engine(app)

    suspend fun write(song: Song, title: String, artist: String?, cover: File?): MusicStore.Edit = withContext(Dispatchers.IO) {
        val work = File(app.cacheDir, "tags").apply { mkdirs() }
        val copy = File(work, song.fileName)
        try {
            if (!engine.isUnpacked) engine.prepare()
            resolver.openInputStream(song.uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } } ?: return@withContext MusicStore.Edit.Failed
            val run = engine.runPython(TagScript.SOURCE, TagScript.arguments(copy.absolutePath, title, artist, cover?.absolutePath))
            if (run.exitCode != 0) {
                Log.w(TAG, "tags not written (${run.exitCode}): ${run.stderr}")
                return@withContext MusicStore.Edit.Failed
            }
            val edit = store.writeFile(song.uri, copy)
            if (edit == MusicStore.Edit.Done) rescan(song)
            edit
        } catch (e: Exception) {
            Log.w(TAG, "tags not written", e)
            MusicStore.Edit.Failed
        } finally {
            copy.delete()
        }
    }

    /** MediaStore reads the title and the artist again from the file. */
    private fun rescan(song: Song) {
        val path = runCatching {
            @Suppress("DEPRECATION")
            resolver.query(song.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: return
        MediaScannerConnection.scanFile(app, arrayOf(path), null, null)
    }

    /**
     * The picked picture as the brief wants a cover: cut to a square from the middle, at most
     * 1000 × 1000, JPEG 85, in the cache; turned the right way up as its photo says.
     */
    fun squareJpeg(picture: Uri): File? = try {
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, picture)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val side = minOf(info.size.width, info.size.height)
            var sample = 1
            while (side / (sample * 2) >= COVER_SIDE) sample *= 2
            decoder.setTargetSampleSize(sample)
        }
        val cut = minOf(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - cut) / 2, (decoded.height - cut) / 2, cut, cut)
        val sized = if (cut > COVER_SIDE) square.scale(COVER_SIDE, COVER_SIDE) else square
        val file = File(app.cacheDir, "cover-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, it) }
        file
    } catch (e: Exception) {
        Log.w(TAG, "cannot read the picture", e)
        null
    }

    companion object {
        private const val TAG = "DalmaTags"
        private const val COVER_SIDE = 1000
        private const val COVER_QUALITY = 85
    }
}

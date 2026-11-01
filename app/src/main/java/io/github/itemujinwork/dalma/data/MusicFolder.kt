package io.github.itemujinwork.dalma.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Size

/** One song in the Music/Dalma folder, the way MediaStore knows it. */
data class Song(
    val id: Long,
    /** content:// address, good for playing and for deleting. */
    val uri: Uri,
    val fileName: String,
    /** The tag, or the file name without its extension when there is no tag. */
    val title: String,
    /** `null` when the file carries no artist. */
    val artist: String?,
    val durationMillis: Long,
    val addedAtSeconds: Long,
)

/**
 * Everything in Music/Dalma is Dalma's. This reads the folder from MediaStore and keeps
 * nothing anywhere else: a file copied in by hand shows up, one deleted elsewhere is let go.
 */
class MusicFolder(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val collection: Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** The songs, newest first. Never throws: a bad row is skipped, a failed query is an empty list. */
    fun read(): List<Song> = try {
        resolver.query(collection, PROJECTION, SELECTION, arrayOf(MusicStore.FOLDER_PATH), SORT)?.use { cursor ->
            buildList { while (cursor.moveToNext()) songFrom(cursor)?.let(::add) }
        } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** What happened to a song Dalma was asked to let go of. */
    sealed interface Removal {
        data object Removed : Removal

        /** A file someone else put there: Android asks the person itself before it goes. */
        class NeedsConsent(val intentSender: IntentSender) : Removal

        data object Failed : Removal
    }

    /** Deletes the file through MediaStore; Dalma's own files go at once, others only with Android's consent. */
    fun remove(song: Song): Removal = try {
        if (resolver.delete(song.uri, null, null) > 0) Removal.Removed else Removal.Failed
    } catch (e: SecurityException) {
        val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(resolver, listOf(song.uri)).intentSender
        } else {
            (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
        }
        if (sender != null) Removal.NeedsConsent(sender) else Removal.Failed
    } catch (_: Exception) {
        Removal.Failed
    }

    /** The cover inside the file, about [sizePx] square, or `null` when there is none. */
    fun cover(song: Song, sizePx: Int): Bitmap? =
        runCatching { resolver.loadThumbnail(song.uri, Size(sizePx, sizePx), null) }.getOrNull()
            ?: embeddedPicture(song, sizePx)

    /** Opus covers are not always thumbnailed by the system; the retriever reads the tag itself. */
    private fun embeddedPicture(song: Song, sizePx: Int): Bitmap? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(resolver.openFileDescriptor(song.uri, "r")?.fileDescriptor ?: return null)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / (inSampleSize * 2) >= sizePx) inSampleSize *= 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }.getOrNull()

    private fun songFrom(cursor: Cursor): Song? {
        val id = cursor.getLong(0)
        val fileName = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: return null
        if (id <= 0) return null
        val title = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
        val artist = cursor.getString(3)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
        return Song(
            id = id,
            uri = ContentUris.withAppendedId(collection, id),
            fileName = fileName,
            title = title,
            artist = artist,
            durationMillis = cursor.getLong(4).coerceAtLeast(0L),
            addedAtSeconds = cursor.getLong(5),
        )
    }

    private companion object {
        val PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        const val SELECTION = "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.IS_PENDING} = 0"
        const val SORT = "${MediaStore.Audio.Media.DATE_ADDED} DESC, ${MediaStore.Audio.Media._ID} DESC"
    }
}

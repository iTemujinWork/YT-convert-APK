package io.github.itemujinwork.dalma.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

/**
 * Writes finished songs into the Music folder through MediaStore: no storage permission,
 * the stock player sees them at once, and a name clash gets "(1)" instead of overwriting.
 */
class MusicStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val collection: Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun save(source: File, fileName: String): Uri {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeTypeFor(extension))
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused $fileName")
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw IOException("cannot open $uri")
            out.use { stream -> source.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    /** True while the file is still there (the user may delete it with a file manager). */
    fun exists(uri: Uri): Boolean = try {
        resolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use { it.moveToFirst() } ?: false
    } catch (_: Exception) {
        false
    }

    /** Which of these MediaStore ids still exist, in one query per few hundred ids. */
    fun existingIds(ids: Collection<Long>): Set<Long> {
        val found = HashSet<Long>()
        ids.chunked(400).forEach { chunk ->
            val selection = MediaStore.Audio.Media._ID + " IN (" + chunk.joinToString(",") { "?" } + ")"
            val args = chunk.map { it.toString() }.toTypedArray()
            try {
                resolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), selection, args, null)?.use { cursor ->
                    while (cursor.moveToNext()) found += cursor.getLong(0)
                }
            } catch (_: Exception) {
            }
        }
        return found
    }

    fun idOf(uri: Uri): Long? = try {
        ContentUris.parseId(uri)
    } catch (_: Exception) {
        null
    }

    companion object {
        /** The MIME type Android itself pairs with the extension, so MediaStore keeps the name. */
        fun mimeTypeFor(extension: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: when (extension) {
                    "opus", "ogg", "oga" -> "audio/ogg"
                    "m4a" -> "audio/mp4"
                    "mp3" -> "audio/mpeg"
                    "webm" -> "audio/webm"
                    else -> "audio/*"
                }
    }
}

package io.github.itemujinwork.dalma.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

/**
 * Writes finished songs into the Music/Dalma folder through MediaStore: no storage permission,
 * other players see them at once, and a name clash gets "(1)" instead of overwriting.
 * Everything in that folder is Dalma's; [MusicFolder] reads it back.
 */
class MusicStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val collection: Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun save(source: File, fileName: String): Uri {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeTypeFor(extension))
            put(MediaStore.Audio.Media.RELATIVE_PATH, FOLDER)
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

    /** Writes a list (.m3u) next to the songs; an older list of the same name, written by Dalma, is replaced. */
    fun saveList(fileName: String, text: String): Uri? = try {
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(files, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(FOLDER_PATH, fileName), null)?.use { cursor ->
            while (cursor.moveToNext()) runCatching { resolver.delete(ContentUris.withAppendedId(files, cursor.getLong(0)), null, null) }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, LIST_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
        }
        val uri = resolver.insert(files, values) ?: throw IOException("MediaStore refused $fileName")
        resolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) } ?: throw IOException("cannot open $uri")
        uri
    } catch (_: Exception) {
        null
    }

    /** A list born on the surface, from one song; MediaStore gives a clashing name a "(1)". */
    fun createList(fileName: String, text: String): Uri? = try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, LIST_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: throw IOException("MediaStore refused $fileName")
        resolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) } ?: throw IOException("cannot open $uri")
        uri
    } catch (_: Exception) {
        null
    }

    /** What came of changing a list file on the surface. */
    sealed interface ListEdit {
        data object Done : ListEdit

        /** A file someone else put there: Android asks the person itself before it changes. */
        class NeedsConsent(val intentSender: IntentSender) : ListEdit

        data object Failed : ListEdit
    }

    /** Overwrites a list in place: Dalma's own files at once, others only with Android's consent. */
    fun writeList(uri: Uri, text: String): ListEdit = edit(uri, delete = false) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: throw IOException("cannot open $uri")
    }

    /** Renames a list file; its name is the list's name. */
    fun renameList(uri: Uri, fileName: String): ListEdit = edit(uri, delete = false) {
        if (resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, fileName) }, null, null) == 0) throw IOException("no such list")
    }

    /** Deletes a list file; the songs stay. */
    fun deleteList(uri: Uri): ListEdit = edit(uri, delete = true) {
        if (resolver.delete(uri, null, null) == 0) throw IOException("no such list")
    }

    private inline fun edit(uri: Uri, delete: Boolean, action: () -> Unit): ListEdit = try {
        action()
        ListEdit.Done
    } catch (e: SecurityException) {
        val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            (if (delete) MediaStore.createDeleteRequest(resolver, listOf(uri)) else MediaStore.createWriteRequest(resolver, listOf(uri))).intentSender
        } else {
            (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
        }
        if (sender != null) ListEdit.NeedsConsent(sender) else ListEdit.Failed
    } catch (_: Exception) {
        ListEdit.Failed
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
        /** Dalma's folder, as MediaStore names it: "Music/Dalma". */
        val FOLDER: String = Environment.DIRECTORY_MUSIC + "/Dalma"

        /** What a .m3u is to MediaStore. */
        const val LIST_MIME = "audio/x-mpegurl"

        /** The same folder the way MediaStore stores and matches it: with a trailing slash. */
        val FOLDER_PATH: String = "$FOLDER/"

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

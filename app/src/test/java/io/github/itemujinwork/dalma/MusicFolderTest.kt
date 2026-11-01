package io.github.itemujinwork.dalma

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.itemujinwork.dalma.data.MusicFolder
import io.github.itemujinwork.dalma.data.MusicStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * The folder is read from MediaStore and from nowhere else. A tiny stand-in provider plays
 * MediaStore here; the real one is exercised by MusicFolderOnDeviceTest.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MusicFolderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private lateinit var media: FakeMediaProvider

    @Before
    fun fakeMediaStore() {
        media = Robolectric.setupContentProvider(FakeMediaProvider::class.java, MediaStore.AUTHORITY)
    }

    private fun copyIn(name: String, title: String? = name.substringBeforeLast('.'), artist: String? = "Kóbor Lámpás", folder: String = MusicStore.FOLDER_PATH, addedAt: Long = 100, pending: Int = 0): Uri =
        context.contentResolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.DURATION, 225_000L)
                put(MediaStore.Audio.Media.DATE_ADDED, addedAt)
                put(MediaStore.Audio.Media.RELATIVE_PATH, folder)
                put(MediaStore.Audio.Media.IS_PENDING, pending)
            },
        )!!

    @Test
    fun aFileCopiedInByHandShowsUp() {
        val uri = copyIn("Kóbor Lámpás – Hajnali vonat.opus", title = "Hajnali vonat")
        val songs = MusicFolder(context).read()
        assertEquals(1, songs.size)
        val song = songs.single()
        assertEquals(uri, song.uri)
        assertEquals("Hajnali vonat", song.title)
        assertEquals("Kóbor Lámpás", song.artist)
        assertEquals(225_000L, song.durationMillis)
        assertEquals("Kóbor Lámpás – Hajnali vonat.opus", song.fileName)
    }

    @Test
    fun aFileDeletedElsewhereIsLetGo() {
        val uri = copyIn("Hajnali vonat.opus")
        assertEquals(1, MusicFolder(context).read().size)
        context.contentResolver.delete(uri, null, null)
        assertEquals(emptyList<Any>(), MusicFolder(context).read())
    }

    @Test
    fun onlyHerFolderAndOnlyFinishedFiles() {
        copyIn("Máshol.opus", folder = "Music/")
        copyIn("Még töltődik.opus", pending = 1)
        copyIn("Az övé.opus")
        assertEquals(listOf("Az övé.opus"), MusicFolder(context).read().map { it.fileName })
    }

    @Test
    fun newestFirstAndMissingTagsAreNotAProblem() {
        copyIn("Régi.opus", addedAt = 10)
        copyIn("Új.opus", addedAt = 20, title = "", artist = MediaStore.UNKNOWN_STRING)
        copyIn("", addedAt = 30) // a row without a name is not a song
        val songs = MusicFolder(context).read()
        assertEquals(listOf("Új.opus", "Régi.opus"), songs.map { it.fileName })
        assertEquals("Új", songs.first().title)
        assertNull(songs.first().artist)
    }

    @Test
    fun aBrokenMediaStoreIsAnEmptyFolderNotACrash() {
        media.broken = true
        assertEquals(emptyList<Any>(), MusicFolder(context).read())
    }

    /** Just enough of MediaStore for these tests: rows in memory, the folder filter, newest first. */
    class FakeMediaProvider : ContentProvider() {
        private val rows = ArrayList<ContentValues>()
        private var nextId = 1L
        var broken = false

        override fun onCreate(): Boolean = true

        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val row = ContentValues(values).apply { put(MediaStore.Audio.Media._ID, nextId) }
            rows += row
            return ContentUris.withAppendedId(uri, nextId++)
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            val id = ContentUris.parseId(uri)
            return if (rows.removeIf { it.getAsLong(MediaStore.Audio.Media._ID) == id }) 1 else 0
        }

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            if (broken) throw IllegalStateException("media store down")
            val columns = projection ?: arrayOf(MediaStore.Audio.Media._ID)
            val folder = selectionArgs?.firstOrNull()
            val cursor = MatrixCursor(columns)
            rows.filter { row ->
                (folder == null || row.getAsString(MediaStore.Audio.Media.RELATIVE_PATH) == folder) &&
                    (row.getAsInteger(MediaStore.Audio.Media.IS_PENDING) ?: 0) == 0
            }.sortedWith(compareByDescending<ContentValues> { it.getAsLong(MediaStore.Audio.Media.DATE_ADDED) ?: 0L }.thenByDescending { it.getAsLong(MediaStore.Audio.Media._ID) })
                .forEach { row -> cursor.addRow(columns.map { row.get(it) }) }
            return cursor
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun getType(uri: Uri): String? = null
    }
}

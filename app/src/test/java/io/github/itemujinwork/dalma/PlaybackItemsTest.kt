package io.github.itemujinwork.dalma

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.itemujinwork.dalma.data.Song
import io.github.itemujinwork.dalma.play.Playback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A song from the folder becomes a queue entry the player and the notification understand. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PlaybackItemsTest {
    private val uri = "content://media/external_primary/audio/media/42".toUri()

    @Test
    fun theSongIsTheQueueEntry() {
        val song = Song(id = 42, uri = uri, fileName = "Kóbor Lámpás – Hajnali vonat.opus", title = "Hajnali vonat", artist = "Kóbor Lámpás", durationMillis = 225_000, addedAtSeconds = 1)
        val item = Playback.item(song)
        assertEquals("42", item.mediaId)
        assertEquals(uri, item.localConfiguration?.uri)
        assertEquals("Hajnali vonat", item.mediaMetadata.title)
        assertEquals("Kóbor Lámpás", item.mediaMetadata.artist)
    }

    @Test
    fun aSongWithoutAnArtistStaysWithoutOne() {
        val song = Song(id = 7, uri = uri, fileName = "Esti villamos.opus", title = "Esti villamos", artist = null, durationMillis = 0, addedAtSeconds = 1)
        assertNull(Playback.item(song).mediaMetadata.artist)
    }
}

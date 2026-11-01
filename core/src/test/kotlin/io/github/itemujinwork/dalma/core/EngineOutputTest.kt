package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineOutputTest {
    @Test
    fun `parses meta json and prefers the cleaned tag values`() {
        val event = EngineOutput.parse(
            """[dalma-meta] {"id": "dQw4w9WgXcQ", "title": "Rick Astley - Never Gonna Give You Up (Official Video)", """ +
                """"meta_title": "Rick Astley - Never Gonna Give You Up", "meta_artist": "Rick Astley", "artist": null, """ +
                """"uploader": "Rick Astley - Topic", "channel": "Rick Astley", "album": "Whenever You Need Somebody", """ +
                """"duration": 213.0, "live_status": "not_live", "playlist_index": 3, "playlist_count": 12}""",
        ) as EngineEvent.Meta
        assertEquals("Rick Astley - Never Gonna Give You Up", event.title)
        assertEquals("Rick Astley", event.artist)
        assertEquals("Whenever You Need Somebody", event.album)
        assertEquals(213.0, event.durationSeconds!!, 0.001)
        assertEquals(3, event.playlistIndex)
        assertEquals(12, event.playlistCount)
        assertEquals(false, event.isLive)
    }

    @Test
    fun `meta falls back to raw fields and strips Topic`() {
        val event = EngineOutput.parse("""[dalma-meta] {"id":"x","title":"Dal","uploader":"Valaki - Topic","live_status":"is_live"}""") as EngineEvent.Meta
        assertEquals("Dal", event.title)
        assertEquals("Valaki", event.artist)
        assertNull(event.playlistIndex)
        assertTrue(event.isLive)
    }

    @Test
    fun `parses plan, progress, phase, file, archive and list lines`() {
        assertEquals(EngineEvent.Plan("dQw4w9WgXcQ", "251", 4012345L), EngineOutput.parse("[dalma-plan] dQw4w9WgXcQ|251|NA|4012345.0"))
        val p = EngineOutput.parse("[dalma-progress] downloading|1024|NA|4096.0|512.5|NA|251|dQw4w9WgXcQ") as EngineEvent.Progress
        assertEquals(1024L, p.downloadedBytes)
        assertEquals(4096L, p.totalBytes)
        assertEquals(512.5, p.speedBytesPerSecond!!, 0.001)
        assertNull(p.etaSeconds)
        assertEquals("251", p.formatId)
        assertEquals("dQw4w9WgXcQ", p.id)
        assertEquals(EngineEvent.Processing("dQw4w9WgXcQ"), EngineOutput.parse("[dalma-phase] dQw4w9WgXcQ"))
        assertEquals(EngineEvent.Output("dQw4w9WgXcQ", "/data/work/dQw4w9WgXcQ.opus"), EngineOutput.parse("[dalma-file] dQw4w9WgXcQ|/data/work/dQw4w9WgXcQ.opus\r"))
        assertEquals(EngineEvent.AlreadyHave("dQw4w9WgXcQ"), EngineOutput.parse("[download] dQw4w9WgXcQ: Rick Astley - Never Gonna Give You Up has already been recorded in the archive"))
        assertEquals(EngineEvent.AlreadyHave("dQw4w9WgXcQ"), EngineOutput.parse("[download] dQw4w9WgXcQ: has already been recorded in the archive"))
        assertEquals(EngineEvent.PlaylistDone(7, "Nyári mix"), EngineOutput.parse("[dalma-list] 7\tNyári mix"))
        assertEquals(EngineEvent.PlaylistDone(12), EngineOutput.parse("[dalma-list] 12"))
        assertEquals(EngineEvent.PlaylistDone(null), EngineOutput.parse("[dalma-list] "))
    }

    @Test
    fun `ignores everything else`() {
        assertNull(EngineOutput.parse("[download] Destination: x.webm"))
        assertNull(EngineOutput.parse("[dalma-meta] {broken"))
        assertNull(EngineOutput.parse("[dalma-progress] too|few"))
        assertNull(EngineOutput.parse("[dalma-file] dQw4w9WgXcQ|NA"))
        assertNull(EngineOutput.parse(""))
    }

    @Test
    fun `tracker fills once per song and never goes back`() {
        val tracker = ProgressTracker()
        fun progress(status: String, done: Long, total: Long?, id: String) =
            tracker.onEvent(EngineEvent.Progress(status, done, total, null, null, "251", id))

        assertNull(progress("downloading", 5, null, "a"))
        assertEquals(0.5f, progress("downloading", 50, 100, "a")!!, 0.001f)
        assertEquals(0.5f, progress("downloading", 40, 100, "a")!!, 0.001f)
        assertEquals(1f, progress("finished", 100, 100, "a")!!, 0.001f)
        assertEquals(1f, tracker.onEvent(EngineEvent.Processing("a"))!!, 0.001f)
        assertEquals(1f, tracker.onEvent(EngineEvent.Output("a", "/x/a.opus"))!!, 0.001f)
    }

    @Test
    fun `tracker shares the bar between the songs of a playlist`() {
        val tracker = ProgressTracker()
        tracker.onEvent(EngineEvent.Meta("a", "A", null, null, null, null, 1, 4))
        tracker.onEvent(EngineEvent.Plan("a", "251", 100))
        assertEquals(0.125f, tracker.onEvent(EngineEvent.Progress("downloading", 50, 100, null, null, "251", "a"))!!, 0.001f)
        assertEquals(0.25f, tracker.onEvent(EngineEvent.Output("a", "/x/a.opus"))!!, 0.001f)
        tracker.onEvent(EngineEvent.Meta("b", "B", null, null, null, null, 2, 4))
        // The next song starts at zero; the bar must not.
        assertEquals(0.25f, tracker.onEvent(EngineEvent.Progress("downloading", 0, 100, null, null, "251", "b"))!!, 0.001f)
        assertEquals(0.375f, tracker.onEvent(EngineEvent.Progress("downloading", 50, 100, null, null, "251", "b"))!!, 0.001f)
    }
}

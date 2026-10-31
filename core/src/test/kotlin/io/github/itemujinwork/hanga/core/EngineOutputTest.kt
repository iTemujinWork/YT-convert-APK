package io.github.itemujinwork.hanga.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineOutputTest {
    @Test
    fun `parses meta json`() {
        val event = EngineOutput.parse(
            """[hanga-meta] {"id": "dQw4w9WgXcQ", "title": "Árvíztűrő tükörfúrógép", "uploader": "Rick Astley - Topic", """ +
                """"channel": "Rick Astley", "artist": null, "track": null, "album": null, "duration": 213.0, "live_status": "not_live"}""",
        ) as EngineEvent.Meta
        assertEquals("Árvíztűrő tükörfúrógép", event.title)
        assertEquals("Rick Astley", event.author)
        assertEquals(213.0, event.durationSeconds!!, 0.001)
        assertEquals(false, event.isLive)
    }

    @Test
    fun `meta knows live streams`() {
        val event = EngineOutput.parse("""[hanga-meta] {"id":"x","title":"t","live_status":"is_live"}""") as EngineEvent.Meta
        assertTrue(event.isLive)
    }

    @Test
    fun `parses a two stream plan`() {
        val plan = EngineOutput.parse("""[hanga-plan] 137+140|["137", "140"]|[52428800, null]|[null, 3355443]|NA|NA""") as EngineEvent.Plan
        assertEquals(listOf("137", "140"), plan.formatIds)
        assertEquals(listOf(52428800L, 3355443L), plan.sizes)
    }

    @Test
    fun `parses a single stream plan`() {
        val plan = EngineOutput.parse("[hanga-plan] 251|NA|NA|NA|4012345|NA") as EngineEvent.Plan
        assertEquals(listOf("251"), plan.formatIds)
        assertEquals(listOf(4012345L), plan.sizes)
    }

    @Test
    fun `parses progress with missing values`() {
        val p = EngineOutput.parse("[hanga-progress] downloading|1024|NA|4096.0|512.5|NA|251") as EngineEvent.Progress
        assertEquals(1024L, p.downloadedBytes)
        assertEquals(4096L, p.totalBytes)
        assertEquals(512.5, p.speedBytesPerSecond!!, 0.001)
        assertNull(p.etaSeconds)
        assertEquals("251", p.formatId)
    }

    @Test
    fun `parses file and phase, ignores the rest`() {
        assertEquals(EngineEvent.Output("/data/work/dQw4w9WgXcQ.mp3"), EngineOutput.parse("[hanga-file] /data/work/dQw4w9WgXcQ.mp3\r"))
        assertEquals(EngineEvent.Processing, EngineOutput.parse("[hanga-phase] processing"))
        assertNull(EngineOutput.parse("[download] Destination: x.webm"))
        assertNull(EngineOutput.parse("[hanga-meta] {broken"))
        assertNull(EngineOutput.parse("[hanga-progress] too|few"))
        assertNull(EngineOutput.parse(""))
    }

    @Test
    fun `tracker blends picture and sound into one bar that never goes back`() {
        val tracker = ProgressTracker()
        tracker.onPlan(EngineEvent.Plan(listOf("137", "140"), listOf(900L, 100L)))
        fun progress(status: String, done: Long, total: Long, id: String) =
            tracker.onProgress(EngineEvent.Progress(status, done, total, null, null, id))

        assertEquals(0.45f, progress("downloading", 450, 900, "137")!!, 0.001f)
        assertEquals(0.9f, progress("finished", 900, 900, "137")!!, 0.001f)
        // The sound stream starts at zero; the bar must not.
        assertEquals(0.9f, progress("downloading", 0, 100, "140")!!, 0.001f)
        assertEquals(0.95f, progress("downloading", 50, 100, "140")!!, 0.001f)
        assertEquals(1f, progress("finished", 100, 100, "140")!!, 0.001f)
    }

    @Test
    fun `tracker guesses weights while sizes are unknown, then uses the real ones`() {
        val tracker = ProgressTracker()
        tracker.onPlan(EngineEvent.Plan(listOf("137", "140"), listOf(null, null)))
        // Only the picture's size is known: the picture counts as 85 %.
        val half = tracker.onProgress(EngineEvent.Progress("downloading", 450, 900, null, null, "137"))
        assertEquals(0.425f, half!!, 0.001f)
        assertEquals(0.85f, tracker.onProgress(EngineEvent.Progress("finished", 900, 900, null, null, "137"))!!, 0.001f)
        // Once the sound's size shows up, the real proportions take over.
        val value = tracker.onProgress(EngineEvent.Progress("downloading", 50, 100, null, null, "140"))
        assertNotNull(value)
        assertEquals(0.95f, value!!, 0.001f)
    }

    @Test
    fun `tracker waits for a total before showing a number`() {
        val tracker = ProgressTracker()
        assertNull(tracker.onProgress(EngineEvent.Progress("downloading", 5, null, null, null, "251")))
        assertEquals(0.5f, tracker.onProgress(EngineEvent.Progress("downloading", 5, 10, null, null, "251"))!!, 0.001f)
    }
}

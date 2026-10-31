package io.github.itemujinwork.hanga.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpArgsTest {
    private val link = VideoLink("dQw4w9WgXcQ")

    private fun args(kind: MediaKind, strategy: Strategy = Strategy.DEFAULT) =
        YtDlpArgs.build(EngineRequest(link, kind, "/data/work/"), strategy)

    private fun List<String>.valueOf(option: String): String? = indexOf(option).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

    @Test
    fun `the url is last, behind an end-of-options marker`() {
        for (kind in MediaKind.entries) {
            val a = args(kind)
            assertEquals(link.canonicalUrl, a.last())
            assertEquals("--", a[a.size - 2])
            assertEquals(1, a.count { it.startsWith("https://") })
        }
    }

    @Test
    fun `local configuration files are ignored and playlists never expand`() {
        val a = args(MediaKind.AUDIO)
        assertTrue("--ignore-config" in a)
        assertTrue("--no-playlist" in a)
    }

    @Test
    fun `output stays inside the private work folder`() {
        assertEquals("/data/work/%(id)s.%(ext)s", args(MediaKind.VIDEO).valueOf("-o"))
    }

    @Test
    fun `audio becomes an mp3 with a square cover`() {
        val a = args(MediaKind.AUDIO)
        assertTrue("--extract-audio" in a)
        assertEquals("mp3", a.valueOf("--audio-format"))
        assertEquals("0", a.valueOf("--audio-quality"))
        assertTrue("--embed-thumbnail" in a)
        assertTrue(a.valueOf("--ppa")!!.startsWith("ThumbnailsConvertor+FFmpeg_o:"))
    }

    @Test
    fun `video prefers h264 and aac up to 1080p in an mp4`() {
        val a = args(MediaKind.VIDEO)
        assertEquals("vcodec:h264,res:1080,acodec:aac", a.valueOf("-S"))
        assertEquals("mp4", a.valueOf("--merge-output-format"))
        assertFalse("--extract-audio" in a)
    }

    @Test
    fun `fallback strategies change only what they should`() {
        val other = args(MediaKind.AUDIO, RetryPlanner.adjust(Strategy.DEFAULT, Remedy.OTHER_CLIENTS))
        assertTrue("--force-ipv4" in other)
        assertEquals("youtube:player_client=default,mweb,tv,web_safari", other.valueOf("--extractor-args"))

        val simple = args(MediaKind.AUDIO, Strategy(simpleProcessing = true))
        assertFalse("--embed-thumbnail" in simple)
        assertFalse("--embed-metadata" in simple)
        assertTrue("--extract-audio" in simple)

        val any = args(MediaKind.VIDEO, Strategy(anyFormat = true))
        assertFalse("-S" in any)
        assertEquals("bv*+ba/b", any.valueOf("-f"))
    }

    @Test
    fun `machine readable markers are requested`() {
        val printed = args(MediaKind.AUDIO).let { a -> a.indices.filter { a[it] == "--print" }.map { a[it + 1] } }
        assertTrue(printed.any { it.contains(YtDlpArgs.MARK_META) })
        assertTrue(printed.any { it.contains(YtDlpArgs.MARK_PLAN) })
        assertTrue(printed.any { it.startsWith("after_move:") && it.contains(YtDlpArgs.MARK_FILE) })
    }
}

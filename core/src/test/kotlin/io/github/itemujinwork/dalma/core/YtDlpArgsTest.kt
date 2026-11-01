package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpArgsTest {
    private val video = Target.Video("dQw4w9WgXcQ")
    private val playlist = Target.Playlist("PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG")

    private fun args(target: Target = video, strategy: Strategy = Strategy.DEFAULT, archive: String? = "/data/archive.txt") =
        YtDlpArgs.build(EngineRequest(target, "/data/work/", archive), strategy)

    private fun List<String>.valueOf(option: String): String? = indexOf(option).takeIf { it >= 0 }?.let { getOrNull(it + 1) }
    private fun List<String>.valuesOf(option: String): List<String> = indices.filter { this[it] == option }.map { this[it + 1] }

    @Test
    fun `the url is last, behind an end-of-options marker`() {
        for (target in listOf(video, playlist)) {
            val a = args(target)
            assertEquals(target.canonicalUrl, a.last())
            assertEquals("--", a[a.size - 2])
            assertEquals(1, a.count { it.startsWith("https://") })
        }
    }

    @Test
    fun `local configuration files are ignored`() {
        assertTrue("--ignore-config" in args())
    }

    @Test
    fun `stderr carries the whole story for the hidden log`() {
        val a = args()
        assertTrue("--verbose" in a)
        assertFalse("--no-warnings" in a)
        assertFalse("--quiet" in a)
        assertTrue("--no-quiet" in a)
    }

    @Test
    fun `one song never expands into its playlist, a playlist is taken whole but capped`() {
        val one = args(video)
        assertTrue("--no-playlist" in one)
        assertFalse("--yes-playlist" in one)

        val whole = args(playlist)
        assertTrue("--yes-playlist" in whole)
        assertEquals("1:${EngineRequest.MAX_PLAYLIST_ITEMS}", whole.valueOf("--playlist-items"))
        assertTrue(whole.valuesOf("--print").any { it.startsWith("playlist:") && it.contains(YtDlpArgs.MARK_LIST) })
    }

    @Test
    fun `the archive tells yt-dlp what we already have`() {
        assertEquals("/data/archive.txt", args().valueOf("--download-archive"))
        assertFalse("--download-archive" in args(archive = null))
    }

    @Test
    fun `output stays inside the private work folder`() {
        assertEquals("/data/work/%(id)s.%(ext)s", args().valueOf("-o"))
    }

    @Test
    fun `opus 251 first, aac 140 second, kept as they are`() {
        val a = args()
        assertEquals("251/140/ba[acodec^=opus]/ba[ext=m4a]/ba/b", a.valueOf("-f"))
        assertTrue("--extract-audio" in a)
        assertEquals("best", a.valueOf("--audio-format"))
        assertFalse(a.any { it == "mp3" })
    }

    @Test
    fun `tags and a square cover are written`() {
        val a = args()
        assertTrue("--embed-metadata" in a)
        assertTrue("--embed-thumbnail" in a)
        assertEquals("jpg", a.valueOf("--convert-thumbnails"))
        assertTrue(a.valueOf("--ppa")!!.startsWith("ThumbnailsConvertor+FFmpeg_o:"))
        val rules = a.valuesOf("--parse-metadata")
        assertEquals(3, rules.size)
        assertTrue(rules.any { it.contains("meta_artist") && it.contains("Topic") })
        assertTrue(rules.any { it.contains("meta_title") })
        assertTrue(rules.any { it.contains("meta_date") })
    }

    @Test
    fun `fallback strategies change only what they should`() {
        val other = args(strategy = RetryPlanner.adjust(Strategy.DEFAULT, Remedy.OTHER_CLIENTS))
        assertTrue("--force-ipv4" in other)
        assertEquals("youtube:player_client=default,mweb,tv,web_safari", other.valueOf("--extractor-args"))

        val simple = args(strategy = Strategy(simpleProcessing = true))
        assertFalse("--embed-thumbnail" in simple)
        assertFalse("--embed-metadata" in simple)
        assertTrue("--extract-audio" in simple)

        val any = args(strategy = Strategy(anyFormat = true))
        assertEquals("ba/b", any.valueOf("-f"))
    }

    @Test
    fun `machine readable markers are requested`() {
        val printed = args().valuesOf("--print")
        assertTrue(printed.any { it.startsWith("before_dl:") && it.contains(YtDlpArgs.MARK_META) })
        assertTrue(printed.any { it.startsWith("before_dl:") && it.contains(YtDlpArgs.MARK_PLAN) })
        assertTrue(printed.any { it.startsWith("after_move:") && it.contains(YtDlpArgs.MARK_FILE) })
        assertTrue(args().valueOf("--progress-template")!!.startsWith("download:" + YtDlpArgs.MARK_PROGRESS))
    }
}

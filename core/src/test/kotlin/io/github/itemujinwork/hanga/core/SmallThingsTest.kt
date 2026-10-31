package io.github.itemujinwork.hanga.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {
    @Test
    fun `music titles lose the upload noise`() {
        assertEquals("Rick Astley - Never Gonna Give You Up.mp3",
            FileNames.forMedia("Rick Astley - Never Gonna Give You Up (Official Music Video) [4K]", "x", MediaKind.AUDIO))
        assertEquals("Wellhello - Rakpart.mp3", FileNames.forMedia("Wellhello - Rakpart (Hivatalos videó)", "x", MediaKind.AUDIO))
        assertEquals("Song (feat. Someone).mp3", FileNames.forMedia("Song (feat. Someone)", "x", MediaKind.AUDIO))
        assertEquals("Song (Remastered 2011).mp3", FileNames.forMedia("Song (Remastered 2011) [Official Audio]", "x", MediaKind.AUDIO))
    }

    @Test
    fun `video titles stay as they are, only made safe`() {
        assertEquals("Cats (Official Video).mp4", FileNames.forMedia("Cats (Official Video)", "x", MediaKind.VIDEO))
        assertEquals("AC-DC - Back In Black.mp4", FileNames.forMedia("AC/DC - Back In Black", "x", MediaKind.VIDEO))
        assertEquals("Why - because.mp4", FileNames.forMedia("Why: because?", "x", MediaKind.VIDEO))
    }

    @Test
    fun `hungarian letters and emoji survive`() {
        assertEquals("Árvíztűrő tükörfúrógép 🎵.mp3", FileNames.forMedia("Árvíztűrő tükörfúrógép 🎵", "x", MediaKind.AUDIO))
    }

    @Test
    fun `dangerous or empty names fall back`() {
        assertEquals("dQw4w9WgXcQ.mp3", FileNames.forMedia("..", "dQw4w9WgXcQ", MediaKind.AUDIO))
        assertEquals("dQw4w9WgXcQ.mp4", FileNames.forMedia("   ", "dQw4w9WgXcQ", MediaKind.VIDEO))
        assertEquals("dQw4w9WgXcQ.mp4", FileNames.forMedia(null, "dQw4w9WgXcQ", MediaKind.VIDEO))
        assertFalse(FileNames.forMedia("a\u0000b\nc", "x", MediaKind.VIDEO).contains('\u0000'))
        assertFalse(FileNames.forMedia("../../etc/passwd", "x", MediaKind.VIDEO).contains('/'))
    }

    @Test
    fun `long names are cut on character boundaries`() {
        val name = FileNames.forMedia("ű".repeat(300), "x", MediaKind.AUDIO)
        assertTrue(name.toByteArray().size <= 205)
        assertTrue(name.endsWith(".mp3"))
        assertTrue(name.removeSuffix(".mp3").all { it == 'ű' })
        val emoji = FileNames.forMedia("🎵".repeat(200), "x", MediaKind.AUDIO)
        assertTrue(emoji.removeSuffix(".mp3").codePoints().allMatch { it == "🎵".codePointAt(0) })
    }
}

class HistoryCodecTest {
    private fun entry(id: String, videoId: String = "dQw4w9WgXcQ", kind: MediaKind = MediaKind.AUDIO) =
        HistoryEntry(id = id, videoId = videoId, kind = kind, title = "T $id", createdAtMillis = 1_700_000_000_000)

    @Test
    fun `round trips`() {
        val list = listOf(
            entry("1"),
            entry("2", kind = MediaKind.VIDEO).copy(status = HistoryEntry.Status.FAILED, failure = FailureKind.PRIVATE),
        )
        assertEquals(list, HistoryCodec.decode(HistoryCodec.encode(list)))
    }

    @Test
    fun `damaged data costs only the damaged entries`() {
        val text = """[{"id":"1","videoId":"dQw4w9WgXcQ","kind":"AUDIO","title":"ok","createdAtMillis":1},""" +
            """{"id":"2","kind":"HOLOGRAM"},{"id":"3","videoId":"dQw4w9WgXcQ","kind":"VIDEO","title":"ok too",""" +
            """"createdAtMillis":2,"failure":"FROM_THE_FUTURE","somethingNew":true}]"""
        val decoded = HistoryCodec.decode(text)
        assertEquals(listOf("1", "3"), decoded.map { it.id })
        assertNull(decoded[1].failure)
        assertEquals(emptyList<HistoryEntry>(), HistoryCodec.decode("not json at all"))
        assertEquals(emptyList<HistoryEntry>(), HistoryCodec.decode(null))
    }

    @Test
    fun `newest first, one line per video and kind, capped`() {
        var list = emptyList<HistoryEntry>()
        list = HistoryCodec.add(list, entry("1"))
        list = HistoryCodec.add(list, entry("2", kind = MediaKind.VIDEO))
        list = HistoryCodec.add(list, entry("3"))
        assertEquals(listOf("3", "2"), list.map { it.id })
        repeat(150) { list = HistoryCodec.add(list, entry("n$it", videoId = "a" + it.toString().padStart(10, '0'))) }
        assertEquals(HistoryCodec.MAX_ENTRIES, list.size)
    }
}

class EngineUpdatesTest {
    @Test
    fun `versions compare like dates`() {
        assertTrue(EngineVersion.isNewer("2026.10.26", "2025.11.12"))
        assertTrue(EngineVersion.isNewer("2024.08.06.1", "2024.08.06"))
        assertFalse(EngineVersion.isNewer("2025.11.12", "2025.11.12"))
        assertFalse(EngineVersion.isNewer("2025.01.01", "2025.11.12"))
        assertTrue(EngineVersion.isNewer("2025.11.12", null))
        assertFalse(EngineVersion.isNewer("garbage", "2025.11.12"))
        assertEquals(0, EngineVersion.compare("v2026.1.2", "2026.01.02"))
    }

    @Test
    fun `parses the latest release`() {
        val json = """{"tag_name":"2026.10.26","name":"yt-dlp 2026.10.26","assets":[
            {"name":"yt-dlp","browser_download_url":"https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.26/yt-dlp"},
            {"name":"SHA2-256SUMS","browser_download_url":"https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.26/SHA2-256SUMS"},
            {"name":"evil","browser_download_url":"http://example.com/evil"}]}"""
        val release = Releases.parseLatest(json)!!
        assertEquals("2026.10.26", release.version)
        assertEquals(setOf("yt-dlp", "SHA2-256SUMS"), release.assets.keys)
        assertNull(Releases.parseLatest("{}"))
        assertNull(Releases.parseLatest("<html>rate limited</html>"))
    }

    @Test
    fun `parses checksum files and hashes`() {
        val sums = Checksums.parseSha256Sums(
            "ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef0123456789  yt-dlp\n" +
                "0000000000000000000000000000000000000000000000000000000000000000 *yt-dlp.exe\n" +
                "garbage line\n",
        )
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", sums["yt-dlp"])
        assertTrue("yt-dlp.exe" in sums)
        assertEquals(2, sums.size)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Checksums.sha256Hex("abc".toByteArray()))
    }
}

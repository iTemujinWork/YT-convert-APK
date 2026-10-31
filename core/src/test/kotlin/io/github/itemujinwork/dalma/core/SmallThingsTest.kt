package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {
    private fun name(artist: String?, title: String?, ext: String = "opus") = FileNames.forTrack(artist, title, "dQw4w9WgXcQ", ext)

    @Test
    fun `artist and title with an en dash`() {
        assertEquals("Rick Astley – Never Gonna Give You Up.opus", name("Rick Astley", "Never Gonna Give You Up"))
        assertEquals("Wellhello – Rakpart.m4a", name("Wellhello", "Rakpart (Hivatalos videó)", "m4a"))
    }

    @Test
    fun `titles that already carry the artist are not doubled`() {
        assertEquals("Rick Astley - Never Gonna Give You Up.opus", name("Rick Astley", "Rick Astley - Never Gonna Give You Up (Official Music Video) [4K]"))
    }

    @Test
    fun `missing pieces`() {
        assertEquals("Never Gonna Give You Up.opus", name(null, "Never Gonna Give You Up"))
        assertEquals("Never Gonna Give You Up.opus", name("  ", "Never Gonna Give You Up"))
        assertEquals("Rick Astley.opus", name("Rick Astley", null))
        assertEquals("dQw4w9WgXcQ.opus", name(null, null))
        assertEquals("dQw4w9WgXcQ.opus", name("..", "   "))
    }

    @Test
    fun `noise in brackets goes, real brackets stay`() {
        assertEquals("Song (feat. Someone).opus", name(null, "Song (feat. Someone)"))
        assertEquals("Song (Remastered 2011).opus", name(null, "Song (Remastered 2011) [Official Audio]"))
    }

    @Test
    fun `hungarian letters and emoji survive, dangerous characters do not`() {
        assertEquals("Árvíztűrő – tükörfúrógép 🎵.opus", name("Árvíztűrő", "tükörfúrógép 🎵"))
        assertEquals("AC-DC – Back In Black.opus", name("AC/DC", "Back In Black"))
        assertEquals("Why - because.opus", name(null, "Why: because?"))
        assertFalse(name(null, "a\u0000b\nc").contains('\u0000'))
        assertFalse(name(null, "../../etc/passwd").contains('/'))
    }

    @Test
    fun `long names are cut on character boundaries`() {
        val long = name("ű".repeat(100), "ű".repeat(300))
        assertTrue(long.toByteArray().size <= 206)
        assertTrue(long.endsWith(".opus"))
        val emoji = name(null, "🎵".repeat(200))
        assertTrue(emoji.removeSuffix(".opus").codePoints().allMatch { it == "🎵".codePointAt(0) })
    }
}

class LibraryCodecTest {
    private fun song(id: String) = SavedSong(videoId = id, uri = "content://media/1", fileName = "$id.opus", savedAtMillis = 1_700_000_000_000)

    @Test
    fun `round trips`() {
        val list = listOf(song("dQw4w9WgXcQ"), song("abcdefghijk").copy(title = "T", artist = "A", sizeBytes = 5))
        assertEquals(list, LibraryCodec.decode(LibraryCodec.encode(list)))
    }

    @Test
    fun `damaged data costs only the damaged entries`() {
        val text = """[{"videoId":"dQw4w9WgXcQ","uri":"content://x","fileName":"a.opus","savedAtMillis":1},""" +
            """{"videoId":"broken"},{"videoId":"abcdefghijk","uri":"content://y","fileName":"b.opus","savedAtMillis":2,"somethingNew":true}]"""
        assertEquals(listOf("dQw4w9WgXcQ", "abcdefghijk"), LibraryCodec.decode(text).map { it.videoId })
        assertEquals(emptyList<SavedSong>(), LibraryCodec.decode("not json at all"))
        assertEquals(emptyList<SavedSong>(), LibraryCodec.decode(null))
    }

    @Test
    fun `newest first, one entry per video, and an archive for yt-dlp`() {
        var list = emptyList<SavedSong>()
        list = LibraryCodec.add(list, song("dQw4w9WgXcQ"))
        list = LibraryCodec.add(list, song("abcdefghijk"))
        list = LibraryCodec.add(list, song("dQw4w9WgXcQ").copy(uri = "content://new"))
        assertEquals(listOf("dQw4w9WgXcQ", "abcdefghijk"), list.map { it.videoId })
        assertEquals("content://new", list[0].uri)
        assertEquals("youtube dQw4w9WgXcQ\nyoutube abcdefghijk\n", LibraryCodec.archive(list))
        assertEquals(listOf("abcdefghijk"), LibraryCodec.remove(list, "dQw4w9WgXcQ").map { it.videoId })
    }
}

class VersionsTest {
    @Test
    fun `engine versions compare like dates`() {
        assertTrue(EngineVersion.isNewer("2026.10.26", "2025.11.12"))
        assertTrue(EngineVersion.isNewer("2024.08.06.1", "2024.08.06"))
        assertFalse(EngineVersion.isNewer("2025.11.12", "2025.11.12"))
        assertFalse(EngineVersion.isNewer("2025.01.01", "2025.11.12"))
        assertTrue(EngineVersion.isNewer("2025.11.12", null))
        assertFalse(EngineVersion.isNewer("garbage", "2025.11.12"))
        assertEquals(0, EngineVersion.compare("v2026.1.2", "2026.01.02"))
    }

    @Test
    fun `app versions`() {
        assertTrue(AppVersion.isNewer("v1.1.0", "1.0.0"))
        assertTrue(AppVersion.isNewer("1.0.1", "1.0"))
        assertFalse(AppVersion.isNewer("1.0.0", "1.0.0"))
        assertFalse(AppVersion.isNewer("0.9.9", "1.0.0"))
        assertFalse(AppVersion.isNewer("preview", "1.0.0"))
        assertTrue(AppVersion.isNewer("1.2.0-rc1", "1.1.0"))
    }

    @Test
    fun `parses the latest release`() {
        val json = """{"tag_name":"2026.10.26","name":"yt-dlp 2026.10.26","html_url":"https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26","assets":[
            {"name":"yt-dlp","browser_download_url":"https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.26/yt-dlp"},
            {"name":"SHA2-256SUMS","browser_download_url":"https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.26/SHA2-256SUMS"},
            {"name":"evil","browser_download_url":"http://example.com/evil"}]}"""
        val release = Releases.parseLatest(json)!!
        assertEquals("2026.10.26", release.version)
        assertEquals(setOf("yt-dlp", "SHA2-256SUMS"), release.assets.keys)
        assertEquals("https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26", release.htmlUrl)
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

class SpaceCheckTest {
    @Test
    fun `needs room for three copies and a margin`() {
        val mb = 1024L * 1024
        assertTrue(SpaceCheck.fits(5 * mb, 100 * mb))
        assertFalse(SpaceCheck.fits(20 * mb, 100 * mb))
        assertTrue(SpaceCheck.fits(null, 60 * mb))
        assertFalse(SpaceCheck.fits(null, 10 * mb))
    }
}

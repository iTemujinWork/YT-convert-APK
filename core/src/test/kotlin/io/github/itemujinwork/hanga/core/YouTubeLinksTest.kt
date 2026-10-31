package io.github.itemujinwork.hanga.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLinksTest {
    private val id = "dQw4w9WgXcQ"

    private fun videoId(text: String?): String? = YouTubeLinks.findVideo(text)?.videoId

    @Test
    fun `recognises every common link shape`() {
        val shapes = listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://youtube.com/watch?v=$id",
            "http://m.youtube.com/watch?v=$id&feature=share",
            "https://music.youtube.com/watch?v=$id&list=RDAMVM$id",
            "https://youtu.be/$id",
            "https://youtu.be/$id?si=AbCdEfGhIjKlMnOp&t=42",
            "https://www.youtube.com/shorts/$id",
            "https://youtube.com/shorts/$id?feature=share",
            "https://www.youtube.com/live/$id?si=x",
            "https://www.youtube.com/embed/$id?start=10",
            "https://www.youtube-nocookie.com/embed/$id",
            "https://www.youtube.com/v/$id",
            "https://www.youtube.com/watch?app=desktop&v=$id",
            "https://www.youtube.com/watch?v=$id#t=1m",
            "https://WWW.YOUTUBE.COM/watch?v=$id",
            "youtube.com/watch?v=$id",
            "www.youtube.com/watch?v=$id",
            "youtu.be/$id",
            "https://www.youtube.com/attribution_link?a=x&u=%2Fwatch%3Fv%3D$id%26feature%3Dshare",
        )
        for (shape in shapes) assertEquals(shape, id, videoId(shape))
    }

    @Test
    fun `finds the link inside shared text`() {
        assertEquals(id, videoId("Nézd meg ezt! https://youtu.be/$id?si=abc ♥"))
        assertEquals(id, videoId("Watch \"Never Gonna Give You Up\" on YouTube\nhttps://youtu.be/$id"))
        assertEquals(id, videoId("(https://www.youtube.com/watch?v=$id)."))
        assertEquals(id, videoId("[klip](https://youtu.be/$id)"))
        assertEquals(id, videoId("https://youtu.be/$id,"))
    }

    @Test
    fun `ignores invisible characters from copied text`() {
        assertEquals(id, videoId("​https://youtu.be/$id​"))
        assertEquals(id, videoId("https://youtu.be/⁠$id"))
    }

    @Test
    fun `takes the first video when there are several`() {
        assertEquals(id, videoId("https://youtu.be/$id and https://youtu.be/abcdefghijk"))
    }

    @Test
    fun `canonical url drops everything but the id`() {
        val link = YouTubeLinks.findVideo("https://www.youtube.com/watch?v=$id&list=PL123&index=4&t=30s")!!
        assertEquals("https://www.youtube.com/watch?v=$id", link.canonicalUrl)
    }

    @Test
    fun `playlists, channels and other sites are told apart`() {
        assertEquals(LinkCheck.PlaylistOnly, YouTubeLinks.check("https://www.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"))
        assertEquals(LinkCheck.NotAVideo, YouTubeLinks.check("https://www.youtube.com/@SomeChannel"))
        assertEquals(LinkCheck.NotAVideo, YouTubeLinks.check("https://www.youtube.com/"))
        assertEquals(LinkCheck.NotAVideo, YouTubeLinks.check("https://www.youtube.com/watch?v=tooShort"))
        assertEquals(LinkCheck.NotYouTube, YouTubeLinks.check("https://vimeo.com/123456"))
        assertEquals(LinkCheck.NotYouTube, YouTubeLinks.check("hello there"))
        assertEquals(LinkCheck.Empty, YouTubeLinks.check("   "))
        assertEquals(LinkCheck.Empty, YouTubeLinks.check(null))
    }

    @Test
    fun `look-alike hosts are not YouTube`() {
        assertNull(videoId("https://youtube.com.evil.example/watch?v=$id"))
        assertNull(videoId("https://notyoutube.com/watch?v=$id"))
        assertNull(videoId("https://evil-youtube.com/watch?v=$id"))
        assertNull(videoId("https://www.youtube.com@evil.example/watch?v=$id"))
        assertNull(videoId("ftp://www.youtube.com/watch?v=$id"))
        assertNull(videoId("javascript:alert(1)//youtube.com/watch?v=$id"))
    }

    @Test
    fun `ids must be exactly eleven safe characters`() {
        assertNull(videoId("https://www.youtube.com/watch?v=$id" + "x"))
        assertNull(videoId("https://www.youtube.com/watch?v=--exec_rm_"))
        assertNull(videoId("https://youtu.be/../../etc"))
    }

    @Test
    fun `an id that starts with a dash stays a plain url argument`() {
        // Valid YouTube ids can start with "-"; the canonical URL keeps it away from option parsing.
        val link = YouTubeLinks.findVideo("https://youtu.be/-abcdefghij")!!
        assertEquals("https://www.youtube.com/watch?v=-abcdefghij", link.canonicalUrl)
    }
}

package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLinksTest {
    private val id = "dQw4w9WgXcQ"

    private fun videoId(text: String?): String? = (YouTubeLinks.find(text) as? Target.Video)?.videoId
    private fun listId(text: String?): String? = (YouTubeLinks.find(text) as? Target.Playlist)?.listId

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
    fun `a song shared from a playlist means that one song`() {
        val target = YouTubeLinks.find("https://www.youtube.com/watch?v=$id&list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG&index=4&t=30s")
        assertEquals(Target.Video(id), target)
        assertEquals("https://www.youtube.com/watch?v=$id", target!!.canonicalUrl)
    }

    @Test
    fun `a playlist page means the whole list`() {
        val list = "PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"
        assertEquals(list, listId("https://www.youtube.com/playlist?list=$list&si=abc"))
        assertEquals(list, listId("https://music.youtube.com/playlist?list=$list"))
        assertEquals("https://www.youtube.com/playlist?list=$list", YouTubeLinks.find("https://youtube.com/playlist?list=$list")!!.canonicalUrl)
        assertEquals("OLAK5uy_abc", listId("https://music.youtube.com/browse/VLOLAK5uy_abc?list=OLAK5uy_abc"))
    }

    @Test
    fun `channels and other sites are told apart`() {
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
        assertNull(listId("https://www.youtube.com/playlist?list=PL%20rm%20-rf"))
    }

    @Test
    fun `an id that starts with a dash stays a plain url argument`() {
        // Valid YouTube ids can start with "-"; the canonical URL keeps it away from option parsing.
        assertEquals("https://www.youtube.com/watch?v=-abcdefghij", YouTubeLinks.find("https://youtu.be/-abcdefghij")!!.canonicalUrl)
    }
}

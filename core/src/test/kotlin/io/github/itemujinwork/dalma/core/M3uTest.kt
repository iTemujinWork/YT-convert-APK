package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uTest {
    private val entries = listOf(
        M3u.Entry("Kóbor Lámpás – Hajnali vonat.opus", "Hajnali vonat", 235),
        M3u.Entry("Ibolya – Negyedik emelet.opus", "Negyedik emelet", null),
    )

    @Test
    fun writtenListsReadBack() {
        val text = M3u.write(entries)
        assertEquals("#EXTM3U\n#EXTINF:235,Hajnali vonat\nKóbor Lámpás – Hajnali vonat.opus\n#EXTINF:-1,Negyedik emelet\nIbolya – Negyedik emelet.opus\n", text)
        assertEquals(entries.map { it.fileName }, M3u.parse(text).map { it.fileName })
        assertEquals("Hajnali vonat", M3u.parse(text).first().title)
    }

    @Test
    fun otherPlayersListsAreReadToo() {
        val text = "﻿#EXTM3U\r\n# a comment\r\n#EXTINF:200.5,Tengerszem\r\nMusic\\Dalma\\Tengerszem.opus\r\n\r\nhttp://example.com/stream\r\nEsti villamos.m4a\r\n"
        val parsed = M3u.parse(text)
        assertEquals(listOf("Tengerszem.opus", "Esti villamos.m4a"), parsed.map { it.fileName })
        assertEquals(200L, parsed.first().seconds)
        assertEquals(null, parsed.last().title)
    }

    @Test
    fun namesComeFromFileNames() {
        assertEquals("Nyári mix", M3u.nameOf("Nyári mix.m3u"))
        assertEquals("Nyári mix.m3u", M3u.fileNameFor("Nyári mix"))
        assertEquals("lista.m3u", M3u.fileNameFor("   "))
    }
}

package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListsTest {
    private val train = M3u.Entry("Kóbor Lámpás – Hajnali vonat.opus", "Hajnali vonat", 235)
    private val floor = M3u.Entry("Ibolya – Negyedik emelet.opus", "Negyedik emelet", 200)
    private val lake = M3u.Entry("Fenyő & Szél – Tengerszem.opus", "Tengerszem", 180)

    @Test
    fun aTapAddsAtTheEndAndATapAgainTakesOut() {
        val added = Lists.toggled(listOf(train, floor), lake)
        assertEquals(listOf(train, floor, lake), added)
        assertTrue(Lists.contains(added, lake.fileName))
        val removed = Lists.toggled(added, floor)
        assertEquals("the others keep their order", listOf(train, lake), removed)
        assertFalse(Lists.contains(removed, floor.fileName))
        assertTrue("the last one out leaves nothing: the caller deletes the list", Lists.toggled(listOf(train), train).isEmpty())
    }

    @Test
    fun aNameTypedBecomesAFileAndNothingBecomesNothing() {
        assertEquals("Futás reggel.m3u", Lists.fileNameFor("  Futás reggel  "))
        assertNull(Lists.fileNameFor(""))
        assertNull(Lists.fileNameFor("   "))
        assertEquals("Nyár (2024).m3u", Lists.fileNameFor("Nyár (2024)"))
    }

    @Test
    fun theChipsReadTheAlphabetTheHungarianWay() {
        val names = listOf("YouTube Mix.m3u", "este.m3u", "Futás.m3u", "Árvíz.m3u", "Este 2.m3u")
        assertEquals(listOf("Árvíz.m3u", "este.m3u", "Este 2.m3u", "Futás.m3u", "YouTube Mix.m3u"), Lists.sorted(names) { it })
    }
}

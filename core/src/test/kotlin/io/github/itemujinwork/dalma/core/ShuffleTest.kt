package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class ShuffleTest {
    private val ids = (1L..12L).toList()

    @Test
    fun thePlayingSongStaysAtTheFrontAndTheRestIsAPermutation() {
        val order = Shuffle.order(ids, first = 7L, random = Random(3))
        assertEquals(7L, order.first())
        assertEquals(ids.toSet(), order.toSet())
        assertEquals(ids.size, order.size)
        assertNotEquals(ids, order)
    }

    @Test
    fun withoutAPlayingSongEverythingIsShuffled() {
        val order = Shuffle.order(ids, first = null, random = Random(5))
        assertEquals(ids.toSet(), order.toSet())
        assertNotEquals(ids, order)
        // A song that is not in the list cannot be first either.
        assertEquals(ids.toSet(), Shuffle.order(ids, first = 99L, random = Random(5)).toSet())
    }

    @Test
    fun anOrderSurvivesSongsComingAndGoing() {
        val order = listOf(3L, 1L, 2L)
        val now = listOf(1L, 2L, 4L) // 3 is gone, 4 is new
        assertEquals(listOf(1L, 2L, 4L), Shuffle.apply(now, order) { it })
    }
}

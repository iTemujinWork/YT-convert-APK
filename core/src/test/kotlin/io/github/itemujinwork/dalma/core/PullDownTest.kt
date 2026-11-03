package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PullDownTest {
    private val mark = PullDown.MARK_DP
    private val dead = PullDown.DEAD_ZONE_DP

    @Test
    fun theContentFollowsOneToOneThenWithResistance() {
        assertEquals(0f, PullDown.content(-20f, mark), 0.01f)
        assertEquals(40f, PullDown.content(40f, mark), 0.01f)
        assertEquals(96f, PullDown.content(96f, mark), 0.01f)
        assertEquals(117.6f, PullDown.content(150f, mark), 0.01f) // the brief: 150 dp of finger = 118 dp of content
    }

    @Test
    fun lettingGoInTheList() {
        assertEquals(PullDown.Outcome.Nothing, PullDown.outcome(10f, mark, dead, canClose = true))
        assertEquals(PullDown.Outcome.Close, PullDown.outcome(16f, mark, dead, canClose = true))
        assertEquals(PullDown.Outcome.Close, PullDown.outcome(95f, mark, dead, canClose = true))
        assertEquals(PullDown.Outcome.Search, PullDown.outcome(96f, mark, dead, canClose = true))
        assertEquals(PullDown.Outcome.Search, PullDown.outcome(150f, mark, dead, canClose = true))
    }

    @Test
    fun lettingGoOnMostSzolSpringsBackBelowTheMark() {
        assertEquals(PullDown.Outcome.Nothing, PullDown.outcome(60f, mark, dead, canClose = false))
        assertEquals(PullDown.Outcome.Search, PullDown.outcome(96f, mark, dead, canClose = false))
    }
}

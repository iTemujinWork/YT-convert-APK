package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The numbers are the pose files' SMIL animations; this keeps them from drifting. */
class IdleMotionTest {
    @Test
    fun thePoseFilesNumbers() {
        with(IdleMotion.LISTEN_DAY) {
            assertEquals(-3f, rise, 0f); assertEquals(2400, periodMillis)
            assertEquals(2f, head, 0f); assertEquals(2.8f, hair, 0f); assertEquals(300, hairLagMillis)
            assertEquals(2.4f, fringe, 0f); assertEquals(420, fringeLagMillis)
        }
        with(IdleMotion.LISTEN_NIGHT) {
            assertEquals(-1.2f, rise, 0f); assertEquals(4200, periodMillis)
            assertEquals(1.2f, head, 0f); assertEquals(1.7f, hair, 0f); assertEquals(500, hairLagMillis)
            assertEquals(1.4f, fringe, 0f); assertEquals(700, fringeLagMillis)
        }
        with(IdleMotion.ASLEEP) {
            assertEquals("against the wall she sinks", 1.8f, rise, 0f); assertEquals(5500, periodMillis); assertEquals(0f, head, 0f)
        }
        with(IdleMotion.BREATHE) {
            assertEquals(-1.2f, rise, 0f); assertTrue("4–5 s", periodMillis in 4000..5000); assertEquals(0f, head, 0f)
        }
    }

    @Test
    fun aWaveGoesThereAndBackWithTheFilesEasing() {
        val day = IdleMotion.LISTEN_DAY
        assertEquals(0f, day.wave(0), 1e-4f)
        assertEquals(1f, day.wave(1200), 1e-4f)
        assertEquals(0f, day.wave(2400), 1e-4f)
        assertEquals("halfway up at a quarter: the easing is symmetric", 0.5f, day.wave(600), 1e-3f)
        assertTrue("slow off the mark", day.wave(300) < 0.25f)
        assertEquals("the same way back", day.wave(300), day.wave(2100), 1e-4f)
        assertEquals("the figure is 3 points up at the top of the wave", -3f, day.rise * day.wave(1200), 1e-4f)
    }

    @Test
    fun theHeadSwingsAndTheHairRunsBehindIt() {
        val day = IdleMotion.LISTEN_DAY
        assertEquals(-2f, day.swing(day.head, 0), 1e-4f)
        assertEquals(2f, day.swing(day.head, 1200), 1e-4f)
        assertEquals(0f, day.swing(day.head, 600), 1e-3f)
        assertEquals("the back hair reaches its start 0.3 s after the head", -2.8f, day.swing(day.hair, 300, day.hairLagMillis), 1e-4f)
        assertEquals("the fringe 0.42 s after", -2.4f, day.swing(day.fringe, 420, day.fringeLagMillis), 1e-4f)
        assertEquals("a lag is a phase: continuous, never a jump", day.swing(day.hair, 2500, 300), day.swing(day.hair, 100, 300), 1e-4f)
        assertEquals("already on its way before its lag is up", -2.8f * (2f * IdleMotion.ease(0.75f) - 1f), day.swing(day.hair, 0, 300), 1e-3f)
    }

    @Test
    fun theEasingIsTheFilesSpline() {
        assertEquals(0f, IdleMotion.ease(0f), 0f)
        assertEquals(1f, IdleMotion.ease(1f), 0f)
        assertEquals(0.5f, IdleMotion.ease(0.5f), 1e-4f)
        assertTrue(IdleMotion.ease(0.25f) < 0.25f)
        assertTrue(IdleMotion.ease(0.75f) > 0.75f)
        var last = 0f
        for (i in 1..100) {
            val y = IdleMotion.ease(i / 100f)
            assertTrue("climbs all the way", y >= last)
            last = y
        }
    }
}

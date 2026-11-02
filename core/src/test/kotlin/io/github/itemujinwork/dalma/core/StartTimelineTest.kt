package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The numbers are the brief's; this keeps them from drifting. */
class StartTimelineTest {
    @Test
    fun theBriefsTable() {
        assertEquals(StartTimeline.Span(0, 600), StartTimeline.appear)
        assertEquals(StartTimeline.Span(800, 2000), StartTimeline.greet)
        assertEquals(StartTimeline.Span(2000, 3000), StartTimeline.air)
        assertEquals(StartTimeline.Span(3000, 3700), StartTimeline.corner)
        assertEquals(StartTimeline.Span(3300, 3900), StartTimeline.cover)
        assertEquals(StartTimeline.Span(3600, 4100), StartTimeline.texts)
        assertEquals(StartTimeline.Span(3900, 4500), StartTimeline.line)
        assertEquals(StartTimeline.Span(4200, 4800), StartTimeline.listen)
        assertEquals(2000, StartTimeline.WITHOUT_MUSIC_MILLIS)
    }

    @Test
    fun theGreetingLastsATwelfthOfAMinuteAndTheAirAFullSecond() {
        assertEquals(1200, StartTimeline.greet.durationMillis)
        assertEquals(1000, StartTimeline.air.durationMillis)
        assertTrue("a full second between the greeting and the step into the corner", StartTimeline.corner.startMillis - StartTimeline.greet.endMillis >= 1000)
    }
}

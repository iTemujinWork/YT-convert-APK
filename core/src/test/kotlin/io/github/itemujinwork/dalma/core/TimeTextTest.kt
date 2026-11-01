package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeTextTest {
    @Test
    fun minutesAndSeconds() {
        assertEquals("0:00", TimeText.clock(0))
        assertEquals("0:09", TimeText.clock(9_999))
        assertEquals("1:48", TimeText.clock(108_400))
        assertEquals("59:59", TimeText.clock(3_599_000))
    }

    @Test
    fun hoursOnlyWhenNeeded() {
        assertEquals("1:00:00", TimeText.clock(3_600_000))
        assertEquals("1:01:48", TimeText.clock(3_708_000))
    }

    @Test
    fun nothingOddFromBadInput() {
        assertEquals("0:00", TimeText.clock(-5_000))
        assertEquals("-0:00", TimeText.remaining(10_000, 5_000))
        assertEquals("-2:07", TimeText.remaining(108_000, 235_000))
    }
}

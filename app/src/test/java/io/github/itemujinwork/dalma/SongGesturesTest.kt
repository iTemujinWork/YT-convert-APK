package io.github.itemujinwork.dalma

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.itemujinwork.dalma.play.songGestures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The player's moves never fight: a drag is never a shuffle, a hold is never a swipe. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SongGesturesTest {
    @get:Rule
    val compose = createComposeRule()

    private class Calls {
        var taps = 0
        val swipes = mutableListOf<Int>()
        var ups = 0
        var longPresses = 0
        var pullStarts = 0
        var pulled = 0f
        var pullEnds = 0
    }

    private fun screen(): Calls {
        val calls = Calls()
        compose.setContent {
            Box(
                Modifier.size(300.dp).songGestures(
                    onTap = { calls.taps++ },
                    onSwipe = { calls.swipes += it },
                    onSwipeUp = { calls.ups++ },
                    onLongPress = { calls.longPresses++ },
                    onPullStart = { calls.pullStarts++ },
                    onPull = { calls.pulled += it },
                    onPullEnd = { calls.pullEnds++ },
                ),
            )
        }
        return calls
    }

    @Test
    fun aPullDownIsReportedLiveAndLetGo() {
        val calls = screen()
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f), delayMillis = 100)
            moveBy(Offset(0f, 100f), delayMillis = 100)
            up()
        }
        compose.waitForIdle()
        assertEquals(1, calls.pullStarts)
        assertEquals(300f, calls.pulled, 1f)
        assertEquals(1, calls.pullEnds)
        assertEquals(0, calls.taps)
        assertEquals(emptyList<Int>(), calls.swipes)
        assertEquals(0, calls.longPresses)
    }

    /** The handler lives as long as the screen, but the screen's state changes: every tap must see the latest. */
    @Test
    fun aTapRunsTheLatestHandlerNotTheFirst() {
        var playing by mutableStateOf(false)
        val seen = mutableListOf<Boolean>()
        compose.setContent {
            Box(Modifier.size(300.dp).songGestures(onTap = { seen += playing }, onSwipe = {}, onSwipeUp = {}, onLongPress = {}))
        }
        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        playing = true
        compose.waitForIdle()
        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(listOf(false, true), seen)
    }

    @Test
    fun aTapPlaysOrPauses() {
        val calls = screen()
        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(1, calls.taps)
        assertEquals(0, calls.longPresses)
        assertEquals(emptyList<Int>(), calls.swipes)
    }

    @Test
    fun aSwipeToTheLeftIsNext() {
        val calls = screen()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf(-1), calls.swipes)
        assertEquals(0, calls.taps)
        assertEquals(0, calls.longPresses)
    }

    @Test
    fun aDragThatThenHoldsStillIsNoShuffle() {
        val calls = screen()
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(150f, 0f), delayMillis = 100)
            moveBy(Offset(0f, 0f), delayMillis = 1500)
            up()
        }
        compose.waitForIdle()
        assertEquals(0, calls.longPresses)
        assertEquals(listOf(1), calls.swipes)
    }

    @Test
    fun holdingStillIsAShuffle() {
        val calls = screen()
        compose.onRoot().performTouchInput { longClick(center) }
        compose.waitForIdle()
        assertEquals(1, calls.longPresses)
        assertEquals(0, calls.taps)
        assertEquals(emptyList<Int>(), calls.swipes)
    }
}

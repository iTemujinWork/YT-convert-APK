package io.github.itemujinwork.dalma.play

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private enum class Early { Up, Moved, Gone }

/**
 * The player's moves in one pointer handler, so they never fight: a lift before the long-press
 * time is a tap, movement past the touch slop before that time is a swipe (left/right for
 * next/previous, up for the list) or, downwards, a live pull that reports every move, and
 * holding still past it is a long press. The handlers are read fresh on every event: the pointer
 * handler lives as long as the screen, the handlers change with its state.
 */
@Composable
fun Modifier.songGestures(
    onTap: () -> Unit,
    /** -1 for a swipe to the left (next), +1 for a swipe to the right (previous). */
    onSwipe: (direction: Int) -> Unit,
    onSwipeUp: () -> Unit,
    onLongPress: () -> Unit,
    onPullStart: () -> Unit = {},
    /** The finger's vertical move since the last report, px; the pull follows it live. */
    onPull: (dy: Float) -> Unit = {},
    onPullEnd: () -> Unit = {},
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val swipe by rememberUpdatedState(onSwipe)
    val swipeUp by rememberUpdatedState(onSwipeUp)
    val longPress by rememberUpdatedState(onLongPress)
    val pullStart by rememberUpdatedState(onPullStart)
    val pull by rememberUpdatedState(onPull)
    val pullEnd by rememberUpdatedState(onPullEnd)
    return pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    val longPressMillis = viewConfiguration.longPressTimeoutMillis
    val swipeDistance = 56.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        var dx = 0f
        var dy = 0f
        val early = withTimeoutOrNull(longPressMillis) {
            var result: Early? = null
            while (result == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                when {
                    change == null -> result = Early.Gone
                    !change.pressed -> result = Early.Up
                    else -> {
                        dx += change.positionChange().x
                        dy += change.positionChange().y
                        if (abs(dx) > slop || abs(dy) > slop) {
                            change.consume()
                            result = Early.Moved
                        }
                    }
                }
            }
            result
        }
        when (early) {
            Early.Up -> tap()
            Early.Gone -> Unit
            Early.Moved -> {
                if (dy > 0f && dy >= abs(dx)) {
                    // A pull: the content goes with the finger from here on.
                    pullStart()
                    pull(dy)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        pull(change.positionChange().y)
                        change.consume()
                    }
                    pullEnd()
                    return@awaitEachGesture
                }
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    dx += change.positionChange().x
                    dy += change.positionChange().y
                    change.consume()
                }
                when {
                    abs(dx) >= abs(dy) && abs(dx) > swipeDistance -> swipe(if (dx < 0f) -1 else 1)
                    dy < -swipeDistance -> swipeUp()
                }
            }
            null -> {
                longPress()
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                }
            }
        }
    }
}
}

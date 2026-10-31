package io.github.itemujinwork.dalma.ui.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.itemujinwork.dalma.ui.LocalReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The faces Dalma can make. */
enum class Expression { NEUTRAL, HAPPY, SORRY, SLEEPY }

/**
 * Everything that moves on Dalma, plus the little choreographies (greeting, turning,
 * being happy...). The composable [Mascot] draws whatever this state says.
 */
@Stable
class MascotState(val reducedMotion: Boolean) {
    /** 0 = looking at you, 1 = head turned three-quarters, hair clip towards you. */
    val turn = Animatable(0f)

    /** Scale of the whole figure: she "comes closer" from 0.94 to 1. */
    val closeness = Animatable(0.94f)

    /** Head nod in degrees, positive = chin down. */
    val nod = Animatable(0f)

    /** Sideways head tilt in degrees. */
    val tilt = Animatable(0f)

    /** Vertical offset of the whole figure in art units, negative = up. */
    val hop = Animatable(0f)

    /** Where she looks, -1..1 on both axes. */
    val gazeX = Animatable(0f)
    val gazeY = Animatable(0f)

    val sparkles = Animatable(0f)
    val drop = Animatable(0f)
    val zz = Animatable(0f)

    var expression by mutableStateOf(Expression.NEUTRAL)
        private set
    var previousExpression by mutableStateOf(Expression.NEUTRAL)
        private set

    /** 0..1 cross-fade from [previousExpression] to [expression]. */
    val expressionBlend = Animatable(1f)

    /** 0 open, 1 half closed, 2 closed. */
    var blinkFrame by mutableIntStateOf(0)
        private set

    var wink by mutableStateOf(false)
        private set

    /** Download progress painted into the hair clip, `null` when idle. */
    var clipFill by mutableStateOf<Float?>(null)

    private fun ms(millis: Int) = if (reducedMotion) 0 else millis

    suspend fun setExpression(next: Expression) {
        if (next == expression) return
        previousExpression = expression
        expression = next
        expressionBlend.snapTo(0f)
        coroutineScope {
            launch { expressionBlend.animateTo(1f, tween(ms(200))) }
            launch { sparkles.animateTo(if (next == Expression.HAPPY) 1f else 0f, tween(ms(350))) }
            launch { drop.animateTo(if (next == Expression.SORRY) 1f else 0f, tween(ms(350))) }
            launch { zz.animateTo(if (next == Expression.SLEEPY) 1f else 0f, tween(ms(600))) }
        }
    }

    /** She arrives: a touch closer, as if stepping up to the screen. */
    suspend fun arrive() {
        closeness.animateTo(1f, tween(ms(650), easing = FastOutSlowInEasing))
    }

    /** A small nod and a wink. */
    suspend fun greet() {
        if (reducedMotion) return
        wink = true
        nod.animateTo(7f, tween(220, easing = FastOutSlowInEasing))
        delay(240)
        nod.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        wink = false
    }

    suspend fun blink() {
        if (reducedMotion) return
        blinkFrame = 1
        delay(40)
        blinkFrame = 2
        delay(80)
        blinkFrame = 1
        delay(45)
        blinkFrame = 0
    }

    /** Frozen poses for screenshots: a wink, and a blink frame (0 open, 1 half, 2 closed). */
    fun greetStill() {
        wink = true
    }

    fun blinkStill(frame: Int) {
        blinkFrame = frame.coerceIn(0, 2)
    }

    suspend fun lookAround() {
        if (reducedMotion) return
        val first = if (Random.nextBoolean()) -1f else 1f
        gazeX.animateTo(first, tween(380, easing = FastOutSlowInEasing))
        delay(650)
        gazeX.animateTo(-first, tween(520, easing = FastOutSlowInEasing))
        delay(650)
        gazeX.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
    }

    /** Breathing, blinking, the occasional glance: runs until cancelled. */
    suspend fun live(sleepy: Boolean) {
        if (reducedMotion) return
        coroutineScope {
            launch {
                while (true) {
                    delay(Random.nextLong(if (sleepy) 3_500 else 2_200, if (sleepy) 9_000 else 6_500))
                    blink()
                    if (!sleepy && Random.nextFloat() < 0.15f) {
                        delay(170)
                        blink()
                    }
                }
            }
            if (!sleepy) {
                launch {
                    while (true) {
                        delay(Random.nextLong(7_000, 19_000))
                        lookAround()
                    }
                }
            }
        }
    }

    suspend fun turnAway() {
        turn.animateTo(1f, tween(ms(700), easing = FastOutSlowInEasing))
    }

    suspend fun turnBack() {
        turn.animateTo(0f, tween(ms(600), easing = FastOutSlowInEasing))
    }

    /** Eyes closed in a big smile, a tiny hop, sparkles. */
    suspend fun happy() {
        coroutineScope {
            launch { setExpression(Expression.HAPPY) }
            if (!reducedMotion) {
                launch {
                    hop.animateTo(-9f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
                    hop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
    }

    /** A kind, apologetic face with a slight tilt. */
    suspend fun sorry() {
        coroutineScope {
            launch { setExpression(Expression.SORRY) }
            launch { tilt.animateTo(5f, tween(ms(450), easing = FastOutSlowInEasing)) }
        }
    }

    suspend fun neutral() {
        coroutineScope {
            launch { setExpression(Expression.NEUTRAL) }
            launch { tilt.animateTo(0f, tween(ms(400))) }
        }
    }

    suspend fun sleepy() {
        setExpression(Expression.SLEEPY)
    }
}

@Composable
fun rememberMascotState(): MascotState {
    val reduced = LocalReducedMotion.current
    return remember(reduced) { MascotState(reduced) }
}

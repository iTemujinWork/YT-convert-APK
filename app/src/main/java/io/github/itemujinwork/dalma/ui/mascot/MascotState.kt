package io.github.itemujinwork.dalma.ui.mascot

import android.graphics.drawable.AnimatedImageDrawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.platform.LocalContext
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
 *
 * Two bodies, one mind: with the vector layers the moves are animated here; when the
 * AI-made pictures exist ([images]), the same calls play her clips instead.
 */
@Stable
class MascotState(val reducedMotion: Boolean, internal val images: MascotImages? = null) {
    /** 0 = looking at you, 1 = head turned three-quarters, hair clip towards you. */
    val turn = Animatable(0f)

    /** The hair follows the turn a beat later (secondary motion). */
    val hairLag = Animatable(0f)

    /** Scale of the whole figure: she "comes closer" from 0.94 to 1. */
    val closeness = Animatable(0.94f)

    /** Head nod in degrees, positive = chin down. */
    val nod = Animatable(0f)

    /** Sideways head tilt in degrees. */
    val tilt = Animatable(0f)

    /** Slow idle sway of the head in degrees, like someone listening to music. */
    val sway = Animatable(0f)

    /** Vertical offset of the whole figure in art units, negative = up. */
    val hop = Animatable(0f)

    /** Squash and stretch for the hop: 0 = none, negative = squashed, positive = stretched. */
    val squash = Animatable(0f)

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

    // --- The picture body ---

    /** The still picture to show when no clip plays. */
    internal var still by mutableStateOf("alap")
        private set
    internal var previousStill by mutableStateOf<String?>(null)
        private set

    /** 0..1 cross-fade from [previousStill] to [still]. */
    internal val stillBlend = Animatable(1f)

    /** The clip playing right now, if any. */
    internal var playing by mutableStateOf<ClipAsset?>(null)
        private set

    val usesPictures: Boolean get() = images != null

    private fun ms(millis: Int) = if (reducedMotion) 0 else millis

    /** Animates, or jumps when motion is off (no frame clock needed then: good for screenshots too). */
    private suspend fun Animatable<Float, AnimationVector1D>.go(target: Float, spec: AnimationSpec<Float>) {
        if (reducedMotion) snapTo(target) else animateTo(target, spec)
    }

    private suspend fun show(name: String) {
        if (still == name) return
        previousStill = still
        still = name
        stillBlend.snapTo(0f)
        stillBlend.go(1f, tween(240))
    }

    /** Plays a clip once (or loops it) and returns false when there is no such clip. */
    private suspend fun play(name: String, loop: Boolean = false): Boolean {
        val clip = images?.clip(name) ?: return false
        if (reducedMotion) return false
        stopClip()
        clip.drawable.repeatCount = if (loop) AnimatedImageDrawable.REPEAT_INFINITE else 0
        playing = clip
        clip.drawable.start()
        if (loop) return true
        try {
            delay(clip.durationMillis)
        } finally {
            if (playing === clip) {
                clip.drawable.stop()
                playing = null
            }
        }
        return true
    }

    private fun stopClip() {
        playing?.drawable?.stop()
        playing = null
    }

    suspend fun setExpression(next: Expression) {
        if (next == expression) return
        previousExpression = expression
        expression = next
        expressionBlend.snapTo(0f)
        coroutineScope {
            launch { expressionBlend.go(1f, tween(200)) }
            launch { sparkles.go(if (next == Expression.HAPPY) 1f else 0f, tween(350)) }
            launch { drop.go(if (next == Expression.SORRY) 1f else 0f, tween(450, easing = LinearOutSlowInEasing)) }
            launch { zz.go(if (next == Expression.SLEEPY) 1f else 0f, tween(700)) }
            // A sorry face looks down a little; the others look at you.
            launch { gazeY.go(if (next == Expression.SORRY) 0.45f else 0f, tween(400)) }
            if (images != null) {
                launch {
                    show(
                        when (next) {
                            Expression.NEUTRAL -> if (turn.value > 0.5f) "fordul" else "alap"
                            Expression.HAPPY -> "orul"
                            Expression.SORRY -> "bocsanat"
                            Expression.SLEEPY -> "almos"
                        },
                    )
                }
            }
        }
    }

    /** She arrives: a touch closer, as if stepping up to the screen, settling softly. */
    suspend fun arrive() {
        closeness.go(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow))
    }

    /** A small nod and a wink: a tiny lift first, the nod, a soft settle. */
    suspend fun greet() {
        if (reducedMotion) return
        if (play("koszon")) {
            show("alap")
            return
        }
        nod.animateTo(-2f, tween(110, easing = FastOutSlowInEasing))
        wink = true
        nod.animateTo(7f, tween(190, easing = FastOutSlowInEasing))
        delay(300)
        nod.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
        wink = false
    }

    /** Eyes close fast and open a little slower, like real ones. */
    suspend fun blink(deep: Boolean = false) {
        if (reducedMotion || images != null) return
        blinkFrame = 1
        delay(30)
        blinkFrame = 2
        delay(if (deep) 220 else 70)
        blinkFrame = 1
        delay(60)
        blinkFrame = 0
    }

    /** Frozen poses for screenshots: a wink, and a blink frame (0 open, 1 half, 2 closed). */
    fun greetStill() {
        wink = true
    }

    fun blinkStill(frame: Int) {
        blinkFrame = frame.coerceIn(0, 2)
    }

    /** The eyes lead, the head follows a little: that is what makes a glance look real. */
    suspend fun lookAround() {
        if (reducedMotion) return
        if (play("korbenez")) {
            show("alap")
            return
        }
        val first = if (Random.nextBoolean()) -1f else 1f
        coroutineScope {
            launch { gazeX.animateTo(first, tween(260, easing = FastOutSlowInEasing)) }
            launch { delay(90); tilt.animateTo(first * 1.6f, tween(420, easing = FastOutSlowInEasing)) }
        }
        delay(Random.nextLong(500, 900))
        if (Random.nextFloat() < 0.4f) blink()
        coroutineScope {
            launch { gazeX.animateTo(-first, tween(380, easing = FastOutSlowInEasing)) }
            launch { delay(90); tilt.animateTo(-first * 1.4f, tween(520, easing = FastOutSlowInEasing)) }
        }
        delay(Random.nextLong(500, 900))
        coroutineScope {
            launch { gazeX.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
            launch { delay(60); tilt.animateTo(0f, tween(480, easing = FastOutSlowInEasing)) }
        }
    }

    /** Breathing, blinking, a slow sway, the occasional glance: runs until cancelled. */
    suspend fun live(sleepy: Boolean) {
        if (reducedMotion) return
        if (images != null) {
            try {
                while (true) {
                    val loop = play(if (sleepy) "almos" else "pislog", loop = true)
                    if (!loop || sleepy) {
                        delay(Long.MAX_VALUE)
                    }
                    delay(Random.nextLong(9_000, 22_000))
                    stopClip()
                    lookAround()
                }
            } finally {
                stopClip()
            }
        }
        coroutineScope {
            launch {
                while (true) {
                    delay(Random.nextLong(if (sleepy) 3_500 else 2_400, if (sleepy) 9_000 else 6_800))
                    blink(deep = sleepy && Random.nextFloat() < 0.5f)
                    if (!sleepy && Random.nextFloat() < 0.18f) {
                        delay(160)
                        blink()
                    }
                }
            }
            launch {
                // Listening to music: the head drifts gently from side to side, never the same twice.
                while (true) {
                    val target = (Random.nextFloat() * 2f - 1f) * (if (sleepy) 2.2f else 1.3f)
                    sway.animateTo(target, tween(Random.nextInt(2_800, 4_600), easing = FastOutSlowInEasing))
                    delay(Random.nextLong(300, 1_400))
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

    /** Turns so the hair clip faces you: a tiny anticipation, then the turn, the hair a beat behind. */
    suspend fun turnAway() {
        if (!reducedMotion && images == null) {
            tilt.animateTo(-2f, tween(120, easing = FastOutSlowInEasing))
        }
        coroutineScope {
            if (images != null) {
                launch {
                    if (!play("fordul")) delay(ms(400).toLong())
                    show("fordul")
                }
            }
            launch { turn.go(1f, tween(700, easing = FastOutSlowInEasing)) }
            launch { hairLag.go(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)) }
            launch { tilt.go(0f, tween(500, easing = FastOutSlowInEasing)) }
        }
    }

    suspend fun turnBack() {
        if (images != null) show("alap")
        coroutineScope {
            launch { turn.go(0f, tween(600, easing = FastOutSlowInEasing)) }
            launch { hairLag.go(0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)) }
        }
    }

    /** Eyes closed in a big smile, a tiny hop with squash and stretch, sparkles. */
    suspend fun happy() {
        coroutineScope {
            launch { setExpression(Expression.HAPPY) }
            if (!reducedMotion && images == null) {
                launch {
                    squash.animateTo(-1f, tween(90, easing = FastOutSlowInEasing))
                    coroutineScope {
                        launch { hop.animateTo(-10f, tween(170, easing = LinearOutSlowInEasing)) }
                        launch { squash.animateTo(1f, tween(170, easing = LinearOutSlowInEasing)) }
                    }
                    coroutineScope {
                        launch { hop.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
                        launch { squash.animateTo(-0.7f, tween(200, easing = FastOutSlowInEasing)) }
                    }
                    squash.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
    }

    /** The download is done: she turns back and is happy (one clip when she has one). */
    suspend fun celebrate() {
        if (images != null && play("vissza_orul")) {
            turn.snapTo(0f)
            hairLag.snapTo(0f)
            setExpression(Expression.HAPPY)
            return
        }
        turnBack()
        happy()
    }

    /** Something went wrong: she turns back with a kind, apologetic face. */
    suspend fun apologize() {
        turnBack()
        sorry()
    }

    /** A kind, apologetic face with a slight tilt and a small sinking of the head. */
    suspend fun sorry() {
        coroutineScope {
            launch { setExpression(Expression.SORRY) }
            launch { tilt.go(if (images == null) 5f else 0f, tween(450, easing = FastOutSlowInEasing)) }
            launch { if (images == null) nod.go(3f, tween(500, easing = FastOutSlowInEasing)) }
        }
    }

    suspend fun neutral() {
        coroutineScope {
            launch { setExpression(Expression.NEUTRAL) }
            launch { tilt.go(0f, tween(400)) }
            launch { nod.go(0f, tween(400)) }
        }
    }

    suspend fun sleepy() {
        coroutineScope {
            launch { setExpression(Expression.SLEEPY) }
            launch { if (images == null) nod.go(2f, tween(900, easing = FastOutSlowInEasing)) }
        }
    }
}

@Composable
fun rememberMascotState(): MascotState {
    val reduced = LocalReducedMotion.current
    val context = LocalContext.current
    return remember(reduced) { MascotState(reduced, MascotAssets.load(context)) }
}

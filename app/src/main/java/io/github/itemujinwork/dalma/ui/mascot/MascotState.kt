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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.itemujinwork.dalma.core.IdleMotion
import io.github.itemujinwork.dalma.ui.LocalReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The faces Dalma can make: the last two with closed eyes, listening at night and asleep against the wall. */
enum class Expression { NEUTRAL, HAPPY, SORRY, SLEEPY, NIGHT, ASLEEP }

/** What an idle motion says at one instant, in the 240 box: the figure's rise, the swings of the head, the back hair and the fringe, and how much of it is on. */
internal class IdlePose(val rise: Float, val head: Float, val hair: Float, val fringe: Float, val weight: Float) {
    fun mix(to: IdlePose, t: Float) = IdlePose(rise + (to.rise - rise) * t, head + (to.head - head) * t, hair + (to.hair - hair) * t, fringe + (to.fringe - fringe) * t, weight + (to.weight - weight) * t)
    fun minus(other: IdlePose) = IdlePose(rise - other.rise, head - other.head, hair - other.hair, fringe - other.fringe, 0f)
    fun plus(other: IdlePose, times: Float) = IdlePose(rise + other.rise * times, head + other.head * times, hair + other.hair * times, fringe + other.fringe * times, weight)

    companion object {
        val STILL = IdlePose(0f, 0f, 0f, 0f, 0f)

        /** The pose files are 200 points across, Dalma's box 240. */
        private const val POINT = 240f / 200f

        fun of(motion: IdleMotion?, millis: Long): IdlePose = if (motion == null) STILL else IdlePose(
            rise = motion.rise * motion.wave(millis) * POINT,
            head = motion.swing(motion.head, millis),
            hair = motion.swing(motion.hair, millis, motion.hairLagMillis),
            fringe = motion.swing(motion.fringe, millis, motion.fringeLagMillis),
            weight = 1f,
        )
    }
}

/**
 * Everything that moves on Dalma, plus the little choreographies (greeting, turning,
 * being happy...). The composable [Mascot] draws whatever this state says.
 *
 * Two bodies, one mind: with the vector layers the moves are animated here; when the
 * AI-made pictures exist ([images]), the same calls play her clips instead.
 */
@Stable
class MascotState internal constructor(val reducedMotion: Boolean, internal val images: MascotImages? = null, startHidden: Boolean = false) {
    /** Scale of the whole figure: she "comes closer" from 0.94 to 1. */
    val closeness = Animatable(0.94f)

    /** 0..1 opacity of the whole figure: she appears with it (the sheet's 0 → 1 over 600 ms); hidden first where she arrives. */
    val presence = Animatable(if (startHidden) 0f else 1f)

    /** The whole figure leaning against the wall, in degrees about her bottom-left; negative leans to the viewer's left. */
    val lean = Animatable(0f)

    /** The greeting's head tilt in degrees (positive towards the viewer's right), and the hair following it late. */
    val greetTilt = Animatable(0f)
    val greetTiltBack = Animatable(0f)
    val greetTiltFront = Animatable(0f)

    /** The left eye closing to a line for the wink: 0 open, 1 a line; the lid's arc shows as it closes. */
    val winkSquash = Animatable(0f)

    /** 0 the usual brows, 1 the wink's: they morph, they don't switch. */
    val browMorph = Animatable(0f)

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

    /** The hair clip standing straight: 0 as drawn (tilted 7°), 1 upright, for "Megjegyeztem.". */
    val clipStraight = Animatable(0f)

    /** The designer's idle motion she follows now (`null`: the sheet's old breathing), cross-fading from the one before. */
    var idle by mutableStateOf<IdleMotion?>(null)
        private set
    var previousIdle by mutableStateOf<IdleMotion?>(null)
        private set

    /** 0..1 cross-fade from [previousIdle] to [idle]. */
    val idleBlend = Animatable(1f)

    /** The idle motions' clock, run by the composable while one is on. */
    var idleMillis by mutableLongStateOf(0L)
        internal set

    /** Where she was when the motion last changed, less where the one before would have her: it fades out with the blend, so no switch jumps. */
    private var idleJump by mutableStateOf(IdlePose.STILL)

    /** Her idle pose this instant: the motion she follows, cross-faded from the one before; still when motion is off. */
    internal fun idlePose(): IdlePose {
        if (reducedMotion) return IdlePose.STILL
        val t = idleBlend.value
        return IdlePose.of(previousIdle, idleMillis).mix(IdlePose.of(idle, idleMillis), t).plus(idleJump, 1f - t)
    }

    var expression by mutableStateOf(Expression.NEUTRAL)
        private set
    var previousExpression by mutableStateOf(Expression.NEUTRAL)
        private set

    /** 0..1 cross-fade from [previousExpression] to [expression]. */
    val expressionBlend = Animatable(1f)

    /** 0 open, 1 half closed, 2 closed. */
    var blinkFrame by mutableIntStateOf(0)
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

    suspend fun setExpression(next: Expression, millis: Int = 200) {
        if (next == expression) return
        previousExpression = expression
        expression = next
        expressionBlend.snapTo(0f)
        coroutineScope {
            launch { expressionBlend.go(1f, tween(millis)) }
            launch { sparkles.go(if (next == Expression.HAPPY) 1f else 0f, tween(350)) }
            launch { drop.go(if (next == Expression.SORRY) 1f else 0f, tween(450, easing = LinearOutSlowInEasing)) }
            launch { zz.go(if (next == Expression.SLEEPY || next == Expression.ASLEEP) 1f else 0f, tween(700)) }
            if (images != null) {
                launch {
                    show(
                        when (next) {
                            Expression.NEUTRAL -> "alap"
                            Expression.HAPPY -> "orul"
                            Expression.SORRY -> "bocsanat"
                            Expression.SLEEPY, Expression.NIGHT, Expression.ASLEEP -> "almos"
                        },
                    )
                }
            }
        }
    }

    suspend fun setIdle(next: IdleMotion?, millis: Int = 600) {
        if (next === idle) return
        val here = idlePose()
        previousIdle = idle
        idle = next
        idleJump = here.minus(IdlePose.of(previousIdle, idleMillis))
        idleBlend.snapTo(0f)
        idleBlend.go(1f, tween(millis, easing = FastOutSlowInEasing))
    }

    /** She appears: 0 → 1 opacity and 0.96 → 1 scale over 600 ms, as the sheet's opening has it; on the download's sheet without the scale. */
    suspend fun arrive(scale: Boolean = true) {
        if (reducedMotion) {
            presence.snapTo(1f)
            closeness.snapTo(1f)
            return
        }
        presence.snapTo(0f)
        closeness.snapTo(if (scale) 0.96f else 1f)
        coroutineScope {
            launch { presence.animateTo(1f, tween(600, easing = LinearOutSlowInEasing)) }
            if (scale) launch { closeness.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        }
    }

    /**
     * The greeting as motion, not two pictures: a breath of anticipation the other way, the head
     * tilts 4°, the back hair follows 100 ms later and swings a little further (1.2×, 4.8°), the
     * fringe 150 ms later and more held back (0.6×, 2.4°); the left eye squashes to a line while
     * the brows morph into the wink's; then everything comes back. 1.2 s in all.
     */
    suspend fun greet() {
        if (reducedMotion) return
        if (play("koszon")) {
            show("alap")
            return
        }
        suspend fun Animatable<Float, AnimationVector1D>.tiltAndBack(times: Float) {
            animateTo(-1f * times, tween(100, easing = FastOutSlowInEasing))
            animateTo(GREET_TILT * times, tween(260, easing = FastOutSlowInEasing))
            delay(420)
            animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        }
        coroutineScope {
            launch { greetTilt.tiltAndBack(1f) }
            launch { delay(100); greetTiltBack.tiltAndBack(GREET_HAIR_TIMES) }
            launch { delay(150); greetTiltFront.tiltAndBack(GREET_FRINGE_TIMES) }
            launch {
                delay(160)
                coroutineScope {
                    launch { winkSquash.animateTo(1f, tween(140, easing = FastOutSlowInEasing)) }
                    launch { browMorph.animateTo(1f, tween(140, easing = FastOutSlowInEasing)) }
                }
                delay(500)
                coroutineScope {
                    launch { winkSquash.animateTo(0f, tween(180, easing = FastOutSlowInEasing)) }
                    launch { browMorph.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) }
                }
            }
        }
    }

    /** How far she is against the wall, 0..1: the screens stand her further left by it, so there is a wall to lean on. */
    val wallness: Float get() = (lean.value / WALL_LEAN).coerceIn(0f, 1f)

    /** Late at night, with nothing to hear, she leans on the wall and sleeps: alszik-fal.svg, sinking and rising 1.8 points. */
    suspend fun sleepAtWall() {
        coroutineScope {
            launch { setExpression(Expression.ASLEEP, 600) }
            launch { lean.go(WALL_LEAN, tween(900, easing = FastOutSlowInEasing)) }
            launch { tilt.go(-10f, tween(900, easing = FastOutSlowInEasing)) }
            launch { setIdle(IdleMotion.ASLEEP, 900) }
        }
    }

    /** The same, at once: where the night's opening starts, and for screenshots. */
    suspend fun sleepAtWallStill() {
        lean.snapTo(WALL_LEAN)
        tilt.snapTo(-10f)
        previousExpression = Expression.ASLEEP
        expression = Expression.ASLEEP
        expressionBlend.snapTo(1f)
        zz.snapTo(1f)
        previousIdle = null
        idle = IdleMotion.ASLEEP
        idleJump = IdlePose.STILL
        idleBlend.snapTo(1f)
    }

    /** The night's greeting instead of the wink: she straightens up from the wall, eyes half open, a soft smile, then awake and breathing. */
    suspend fun wakeUp() {
        if (reducedMotion) {
            lean.snapTo(0f)
            tilt.snapTo(0f)
            setExpression(Expression.NEUTRAL)
            setIdle(IdleMotion.BREATHE)
            return
        }
        coroutineScope {
            launch { lean.animateTo(0f, tween(800, easing = FastOutSlowInEasing)) }
            launch { tilt.animateTo(0f, tween(800, easing = FastOutSlowInEasing)) }
            launch { setIdle(IdleMotion.BREATHE, 800) }
            launch {
                delay(300)
                setExpression(Expression.SLEEPY, 250)
                delay(450)
                setExpression(Expression.NEUTRAL, 200)
            }
        }
    }

    /** Standing, as alap.svg draws her: eyes open, breathing, and straight (off the wall, if she leaned on it). */
    suspend fun stand() {
        coroutineScope {
            launch { setExpression(Expression.NEUTRAL, 400) }
            launch { straighten() }
            launch { setIdle(IdleMotion.BREATHE) }
        }
    }

    /**
     * Listening in the player's corner: by day with open eyes and the livelier sway of hallgat.svg,
     * at night with closed eyes and the gentle one of hallgat-este.svg.
     */
    suspend fun listen(night: Boolean) {
        coroutineScope {
            launch { setExpression(if (night) Expression.NIGHT else Expression.NEUTRAL, 600) }
            launch { straighten() }
            launch { setIdle(if (night) IdleMotion.LISTEN_NIGHT else IdleMotion.LISTEN_DAY) }
        }
    }

    private suspend fun straighten() {
        if (lean.value == 0f && tilt.value == 0f) return
        coroutineScope {
            launch { lean.go(0f, tween(800, easing = FastOutSlowInEasing)) }
            launch { tilt.go(0f, tween(800, easing = FastOutSlowInEasing)) }
        }
    }

    /** "Megjegyeztem.": the hair clip stands straight (300 ms), stays while the word shows (1.2 s), then slides back. */
    suspend fun noteTaken() {
        clipStraight.go(1f, tween(300, easing = FastOutSlowInEasing))
        delay(1_200)
        clipStraight.go(0f, tween(300, easing = FastOutSlowInEasing))
    }

    /** The same, held: for screenshots. */
    suspend fun noteTakenStill() = clipStraight.snapTo(1f)

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

    /** Frozen poses for screenshots: the wink, and a blink frame (0 open, 1 half, 2 closed). */
    suspend fun greetStill() {
        greetTilt.snapTo(GREET_TILT)
        greetTiltBack.snapTo(GREET_TILT * GREET_HAIR_TIMES)
        greetTiltFront.snapTo(GREET_TILT * GREET_FRINGE_TIMES)
        winkSquash.snapTo(1f)
        browMorph.snapTo(1f)
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

    /** Blinks at her own pace, now and then twice; runs until cancelled. */
    suspend fun blinking() {
        if (reducedMotion || images != null) return
        while (true) {
            delay(Random.nextLong(2_400, 6_800))
            blink()
            if (Random.nextFloat() < 0.18f) {
                delay(160)
                blink()
            }
        }
    }

    /** The empty screen's day: blinking, a slow drift of the head, the occasional glance; runs until cancelled. */
    suspend fun live() {
        if (reducedMotion) return
        if (images != null) {
            try {
                while (true) {
                    if (!play("pislog", loop = true)) delay(Long.MAX_VALUE)
                    delay(Random.nextLong(9_000, 22_000))
                    stopClip()
                    lookAround()
                }
            } finally {
                stopClip()
            }
        }
        coroutineScope {
            launch { blinking() }
            launch {
                // The head drifts gently from side to side, never the same twice.
                while (true) {
                    val target = (Random.nextFloat() * 2f - 1f) * 1.3f
                    sway.animateTo(target, tween(Random.nextInt(2_800, 4_600), easing = FastOutSlowInEasing))
                    delay(Random.nextLong(300, 1_400))
                }
            }
            launch {
                while (true) {
                    delay(Random.nextLong(7_000, 19_000))
                    lookAround()
                }
            }
        }
    }

    /**
     * A download fills the clip: her head tilts 2.5° towards it and her eyes look up at it; meanwhile
     * she looks back at you once and blinks once (and now and then after that, should it take long).
     * Runs until cancelled.
     */
    suspend fun watchTheClip() {
        coroutineScope {
            launch { tilt.go(2.5f, tween(400, easing = FastOutSlowInEasing)) }
            launch { gazeX.go(1f, tween(320, easing = FastOutSlowInEasing)) }
            launch { gazeY.go(-1f, tween(320, easing = FastOutSlowInEasing)) }
        }
        if (reducedMotion || images != null) return
        delay(2_000)
        coroutineScope {
            launch { gazeX.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
            launch { gazeY.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
        }
        delay(700)
        coroutineScope {
            launch { gazeX.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
            launch { gazeY.animateTo(-1f, tween(320, easing = FastOutSlowInEasing)) }
        }
        delay(1_500)
        blink()
        while (true) {
            delay(Random.nextLong(7_000, 11_000))
            blink()
        }
    }

    /** The pose held, for screenshots: head to the clip, eyes up at it. */
    suspend fun watchTheClipStill() {
        tilt.snapTo(2.5f)
        gazeX.snapTo(1f)
        gazeY.snapTo(-1f)
    }

    /** The clip is full: eyes and head back to you (300 ms). */
    suspend fun lookBack() {
        coroutineScope {
            launch { tilt.go(0f, tween(300, easing = FastOutSlowInEasing)) }
            launch { gazeX.go(0f, tween(300, easing = FastOutSlowInEasing)) }
            launch { gazeY.go(0f, tween(300, easing = FastOutSlowInEasing)) }
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

    /** A kind, apologetic face, the head tilted 5° the other way about the greeting's pivot, as bocsanat.svg has it. */
    suspend fun sorry() {
        coroutineScope {
            launch { setExpression(Expression.SORRY) }
            if (images == null) {
                launch { greetTilt.go(SORRY_TILT, tween(450, easing = FastOutSlowInEasing)) }
                launch { greetTiltBack.go(SORRY_TILT, tween(450, easing = FastOutSlowInEasing)) }
                launch { greetTiltFront.go(SORRY_TILT, tween(450, easing = FastOutSlowInEasing)) }
            }
        }
    }

    /** The same, held: for screenshots. */
    suspend fun sorryStill() {
        setExpression(Expression.SORRY)
        drop.snapTo(1f)
        greetTilt.snapTo(SORRY_TILT)
        greetTiltBack.snapTo(SORRY_TILT)
        greetTiltFront.snapTo(SORRY_TILT)
    }

    suspend fun neutral() {
        coroutineScope {
            launch { setExpression(Expression.NEUTRAL) }
            launch { tilt.go(0f, tween(400)) }
            launch { nod.go(0f, tween(400)) }
            launch { greetTilt.go(0f, tween(400)) }
            launch { greetTiltBack.go(0f, tween(400)) }
            launch { greetTiltFront.go(0f, tween(400)) }
        }
    }

    suspend fun sleepy() {
        coroutineScope {
            launch { setExpression(Expression.SLEEPY) }
            launch { if (images == null) nod.go(2f, tween(900, easing = FastOutSlowInEasing)) }
        }
    }
}

/** The designer's numbers: the greeting's head tilt and how the hair and the fringe follow it; the lean against the wall (alszik-fal.svg). */
private const val GREET_TILT = 4f
private const val GREET_HAIR_TIMES = 1.2f
private const val GREET_FRINGE_TIMES = 0.6f
private const val SORRY_TILT = -5f
private const val WALL_LEAN = -9f

/** [arriving]: she is not there until [MascotState.arrive] fades her in, so the first frame shows no one. */
@Composable
fun rememberMascotState(arriving: Boolean = false): MascotState {
    val reduced = LocalReducedMotion.current
    val context = LocalContext.current
    return remember(reduced) { MascotState(reduced, MascotAssets.load(context), startHidden = arriving) }
}

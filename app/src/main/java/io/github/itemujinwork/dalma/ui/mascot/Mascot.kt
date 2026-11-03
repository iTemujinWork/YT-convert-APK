package io.github.itemujinwork.dalma.ui.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.itemujinwork.dalma.ui.LocalReducedMotion
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import kotlin.math.roundToInt
import kotlin.math.sin

private class Variants(val eyeL: String, val eyeR: String, val mouth: String, val browL: String, val browR: String)

private fun variants(expression: Expression): Variants = when (expression) {
    Expression.NEUTRAL -> Variants("eye_l_open", "eye_r_open", "mouth_smile", "brow_l", "brow_r")
    Expression.HAPPY -> Variants("eye_l_happy", "eye_r_happy", "mouth_happy", "brow_l", "brow_r")
    Expression.SORRY -> Variants("eye_l_open", "eye_r_open", "mouth_sorry", "brow_l_sorry", "brow_r_sorry")
    Expression.SLEEPY -> Variants("eye_l_sleepy", "eye_r_sleepy", "mouth_sleepy", "brow_l", "brow_r")
    // hallgat-este.svg and alszik-fal.svg: closed eyes, and each its own brows and smile.
    Expression.NIGHT -> Variants("eye_l_closed", "eye_r_closed", "mouth_night", "brow_l_wink", "brow_r_wink")
    Expression.ASLEEP -> Variants("eye_l_closed", "eye_r_closed", "mouth_asleep", "brow_l_asleep", "brow_r_asleep")
}

/**
 * The designer's pivots, in the 240 box: the greeting tilts the whole face about kacsint.svg's (100,100); the head
 * turns about the chin (100,136), the back hair swings about the nape (100,84), the fringe about the top of the
 * head (100,40), and the lean is about the wall's foot (60,200).
 */
private val GREET_PIVOT = Offset(120f, 120f)
private val HEAD_PIVOT = Offset(120f, 163.2f)
private val HAIR_PIVOT = Offset(120f, 100.8f)
private val FRINGE_PIVOT = Offset(120f, 48f)
private val LEAN_PIVOT = Offset(72f, 240f)

private const val TWO_PI = (Math.PI * 2).toFloat()

/** Time-driven values that are not part of the state: the sheet's breathing, a slow drift for twinkles, and the designer's idle motion this frame. */
private class Clock(val breath: Float, val drift: Float, val shine: Float, val idle: IdlePose)

/**
 * Dalma. The square art box is scaled to the width of the composable and aligned to its
 * bottom edge, so her shoulders run off the screen the way the mock-ups show. Drawn from
 * the vector layers, or from the AI-made pictures when the app has them.
 */
@Composable
fun Mascot(
    state: MascotState,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val reducedMotion = LocalReducedMotion.current
    val sleepy = state.expression == Expression.SLEEPY
    val breath: Float = if (reducedMotion) {
        0f
    } else {
        rememberInfiniteTransition(label = "breath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (sleepy) 5_200 else 3_400, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "breathing",
        ).value
    }
    val drift: Float = if (reducedMotion) {
        0f
    } else {
        rememberInfiniteTransition(label = "drift").animateFloat(
            initialValue = 0f,
            targetValue = TWO_PI,
            animationSpec = infiniteRepeatable(tween(6_000)),
            label = "drift",
        ).value
    }

    // A short gleam on the hair clip the moment it is full.
    val shine = remember { Animatable(0f) }
    val full = state.clipFill == 1f
    LaunchedEffect(full) {
        if (full && !reducedMotion) {
            shine.snapTo(1f)
            shine.animateTo(0f, tween(520))
        }
    }

    // While a clip plays, redraw every frame so Android can show the next picture.
    val playing = state.playing
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        if (playing != null) {
            while (true) withFrameNanos { frame = it }
        }
    }

    // The designer's idle motions (listening, breathing, sleeping) run on a clock of their own; while one is on, every frame redraws.
    val idleOn = !reducedMotion && state.images == null && (state.idle != null || state.previousIdle != null)
    LaunchedEffect(idleOn) {
        if (!idleOn) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { state.idleMillis = (it - start) / 1_000_000 }
    }

    val semanticsModifier = if (contentDescription != null) modifier.semantics { this.contentDescription = contentDescription } else modifier
    Canvas(semanticsModifier.graphicsLayer { alpha = state.presence.value }) {
        @Suppress("UNUSED_VARIABLE") val tick = frame
        val idle = state.idlePose()
        // The sheet's own breathing gives way to the designer's motion as far as one is on.
        val clock = Clock(breath * (1f - idle.weight), drift, shine.value, idle)
        val images = state.images
        if (images != null) {
            drawPictures(state, images, clock)
        } else {
            val art = MascotArt.SIZE
            val s = size.width / art
            val top = size.height - art * s
            withTransform({
                translate(0f, top)
                scale(s, s, Offset.Zero)
            }) {
                drawMascot(state, clock)
            }
        }
    }
}

// ---------------------------------------------------------------- pictures

private fun DrawScope.drawPictures(state: MascotState, images: MascotImages, clock: Clock) {
    val side = size.width
    val top = size.height - side
    val closeness = state.closeness.value
    val dst = IntSize(side.roundToInt(), side.roundToInt())
    scale(closeness, closeness, pivot = Offset(size.width / 2f, size.height)) {
        translate(0f, top + state.hop.value * side / MascotArt.SIZE - 0.004f * side * clock.breath) {
            val clip = state.playing
            if (clip != null) {
                drawIntoCanvas { canvas ->
                    clip.drawable.setBounds(0, 0, dst.width, dst.height)
                    clip.drawable.draw(canvas.nativeCanvas)
                }
            } else {
                val blend = state.stillBlend.value
                val previous = state.previousStill
                if (blend < 1f && previous != null) {
                    images.still(previous)?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = dst, alpha = 1f - blend) }
                }
                images.still(state.still)?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = dst, alpha = blend) }
                if (state.still == "fordul") state.clipFill?.let { drawPictureClipFill(images, it, dst, clock.shine) }
            }
        }
    }
}

/** The empty part of the hair clip is painted deep teal through the clip's mask. */
private fun DrawScope.drawPictureClipFill(images: MascotImages, fill: Float, dst: IntSize, shine: Float) {
    val mask = images.clipMask ?: return
    val box = images.clipBox ?: return
    val scale = dst.width.toFloat() / images.size
    val top = box[1] * scale
    val bottom = box[3] * scale
    val emptyBottom = top + (bottom - top) * (1f - fill.coerceIn(0f, 1f))
    if (emptyBottom > top) {
        clipRect(box[0] * scale - 2f, top - 2f, box[2] * scale + 2f, emptyBottom) {
            drawImage(mask, dstOffset = IntOffset.Zero, dstSize = dst, colorFilter = ColorFilter.tint(DalmaColors.DeepTeal, BlendMode.SrcIn))
        }
    }
    if (shine > 0f) {
        drawImage(mask, dstOffset = IntOffset.Zero, dstSize = dst, alpha = shine * 0.75f, colorFilter = ColorFilter.tint(DalmaColors.Milk, BlendMode.SrcIn))
    }
}

// ---------------------------------------------------------------- vector layers

private fun DrawScope.drawMascot(state: MascotState, clock: Clock) {
    val closeness = state.closeness.value
    val hop = state.hop.value
    val squash = state.squash.value
    val breath = clock.breath
    val idle = clock.idle

    scale(closeness, closeness, pivot = Offset(120f, 240f)) {
        // The hop stretches her a little going up and squashes her a little landing; asleep, she leans on the
        // wall; the designer's idle motion lifts (or sinks) the whole figure, z's and all.
        withTransform({
            rotate(state.lean.value, pivot = LEAN_PIVOT)
            translate(0f, hop + idle.rise)
            scale(1f - 0.03f * squash, 1f + 0.045f * squash, pivot = Offset(120f, 240f))
        }) {
            // The body breathes the sheet's way while no designer's motion is on: the shoulders rise a little.
            scale(1f + 0.008f * breath, 1f + 0.014f * breath, pivot = Offset(120f, 240f)) {
                drawLayer("body")
            }
            // The head turns about the chin, as the pose files have it: it tilts, swings with the music and follows a glance a little.
            val headAngle = state.tilt.value + state.sway.value + state.gazeX.value * 0.9f + idle.head
            withTransform({
                translate(0f, -1.6f * breath)
                rotate(headAngle, pivot = HEAD_PIVOT)
            }) {
                drawHead(state, clock)
            }
            drawExtras(state, clock)
        }
    }
}

private fun DrawScope.drawHead(state: MascotState, clock: Clock) {
    val turn = state.turn.value
    val lag = state.hairLag.value
    val nodDip = state.nod.value * 0.9f
    val nodSquash = 1f - state.nod.value * 0.004f
    val headPivot = Offset(120f, 104f)
    val idle = clock.idle
    withTransform({
        translate(0f, nodDip)
        scale(1f, nodSquash, pivot = headPivot)
    }) {
        // Turning three-quarters: the head narrows, the back hair shows a little more, a beat later.
        // The greeting tilts the head; the back hair follows it 100 ms late, the fringe 150 ms late.
        // Listening, the back hair swings about the nape and the fringe about the top of the head, each behind the head.
        rotate(state.greetTiltBack.value, pivot = GREET_PIVOT) {
            rotate(idle.hair, pivot = HAIR_PIVOT) {
                scale(1f - 0.07f * turn, 1f, pivot = headPivot) {
                    translate(5f * lag, -0.4f * clock.breath) { drawLayer("hair_back") }
                }
            }
        }
        rotate(state.greetTilt.value, pivot = GREET_PIVOT) {
            scale(1f - 0.07f * turn, 1f, pivot = headPivot) { drawLayer("head") }
        }
        // The fringe's shadow lies on the face but belongs to the fringe: it moves with it, cut to the face's outline as in the files.
        rotate(state.greetTiltFront.value, pivot = GREET_PIVOT) {
            rotate(idle.fringe, pivot = FRINGE_PIVOT) {
                scale(1f - 0.07f * turn, 1f, pivot = headPivot) {
                    translate(-3f * lag, -0.6f * clock.breath) {
                        clipPath(MascotPaths.faceClip) { drawLayer("fringe_shadow") }
                    }
                }
            }
        }
        rotate(state.greetTilt.value, pivot = GREET_PIVOT) {
            scale(1f - 0.07f * turn, 1f, pivot = headPivot) { drawLayer("blush") }
            // The face slides towards the turn and narrows; a glance moves it a touch too.
            withTransform({
                translate(-14f * turn + state.gazeX.value * 1.2f, 1.5f * turn + state.gazeY.value * 0.8f)
                scale(1f - 0.12f * turn, 1f, pivot = headPivot)
            }) {
                drawLayer("nose")
                drawFace(state)
            }
            // The cap of the hair sits on the skull and goes with the head; only the fringe under it swings.
            scale(1f - 0.07f * turn, 1f, pivot = headPivot) {
                translate(-3f * lag, -0.6f * clock.breath) { drawLayer("hair_cap") }
            }
        }
        rotate(state.greetTiltFront.value, pivot = GREET_PIVOT) {
            rotate(idle.fringe, pivot = FRINGE_PIVOT) {
                scale(1f - 0.07f * turn, 1f, pivot = headPivot) {
                    translate(-3f * lag, -0.6f * clock.breath) { drawLayer("hair_front") }
                }
                // The hair clip comes forward and grows as she turns.
                val clipCenter = MascotPaths.clipBounds.center
                withTransform({
                    translate(-9f * turn - 3f * (lag - turn), 2.5f * turn - 0.6f * clock.breath)
                    scale(1f + 0.28f * turn, 1f + 0.28f * turn, pivot = clipCenter)
                }) {
                    drawLayer("clip")
                    state.clipFill?.let { drawClipFill(it, clock.shine) }
                }
            }
        }
    }
}

/** Eyes, brows and mouth, cross-fading between two expressions. */
private fun DrawScope.drawFace(state: MascotState) {
    val blend = state.expressionBlend.value
    if (blend < 1f) drawExpression(state, state.previousExpression, 1f - blend)
    drawExpression(state, state.expression, blend)
}

private fun DrawScope.drawExpression(state: MascotState, expression: Expression, alpha: Float) {
    if (alpha <= 0f) return
    val v = variants(expression)
    val blink = state.blinkFrame
    val openEyes = expression == Expression.NEUTRAL || expression == Expression.SORRY
    // The designer's wink (kacsint.svg), as motion: the viewer's left eye squashes to a line and its lid's arc
    // comes out, the other eye narrows a touch under its lid line, and the brows morph into the wink's.
    val wink = if (expression == Expression.NEUTRAL && blink == 0) state.winkSquash.value else 0f
    val brows = if (expression == Expression.NEUTRAL) state.browMorph.value else 0f
    drawLayer(v.mouth, alpha)
    drawMorphedLayer(v.browL, "brow_l_wink", brows, alpha)
    drawMorphedLayer(v.browR, "brow_r_wink", brows, alpha)

    var eyeL = v.eyeL
    var eyeR = v.eyeR
    if (openEyes) {
        when (blink) {
            1 -> { eyeL = "eye_l_sleepy"; eyeR = "eye_r_sleepy" }
            2 -> { eyeL = "eye_l_closed"; eyeR = "eye_r_closed" }
        }
    } else if (expression == Expression.SLEEPY && blink == 2) {
        eyeL = "eye_l_closed"
        eyeR = "eye_r_closed"
    }
    drawEye(eyeL, state, alpha, squash = wink)
    drawEye(eyeR, state, alpha, squash = 0.08f * wink)
    if (wink > 0f) {
        drawEye("eye_l_wink", state, alpha * wink)
        drawEye("eye_r_wink", state, alpha * wink)
    }
}

/**
 * One eye, moved whole by the glance (2.2 right, 2.4 up at most, the upper lid's line with it,
 * so the eye never slips out from under the line), and squashed vertically for the wink.
 */
private fun DrawScope.drawEye(layer: String, state: MascotState, alpha: Float, squash: Float = 0f) {
    val shapes = MascotPaths.layer(layer)
    if (shapes.isEmpty()) return
    val gaze = Offset(state.gazeX.value * 2.2f, state.gazeY.value * 2.4f)
    val centre = shapes.map { it.bounds }.reduce { acc, rect -> Rect(minOf(acc.left, rect.left), minOf(acc.top, rect.top), maxOf(acc.right, rect.right), maxOf(acc.bottom, rect.bottom)) }.center
    withTransform({
        translate(gaze.x, gaze.y)
        if (squash > 0f) scale(1f, (1f - squash).coerceAtLeast(0.04f), pivot = centre)
    }) {
        for (shape in shapes) drawShape(shape, alpha)
    }
}

/** The download fills the clip from the bottom; the empty part is deep teal; full, it gleams once. */
private fun DrawScope.drawClipFill(fill: Float, shine: Float) {
    val bounds = MascotPaths.clipBounds
    val emptyHeight = bounds.height * (1f - fill.coerceIn(0f, 1f))
    if (emptyHeight > 0f) {
        clipRect(bounds.left - 2f, bounds.top - 2f, bounds.right + 2f, bounds.top + emptyHeight) {
            drawPath(MascotPaths.clip, DalmaColors.DeepTeal)
        }
    }
    drawPath(MascotPaths.clip, DalmaColors.Mint, style = Stroke(width = 1.6f))
    if (shine > 0f) drawPath(MascotPaths.clip, DalmaColors.Milk, alpha = shine * 0.8f)
}

private fun DrawScope.drawExtras(state: MascotState, clock: Clock) {
    val drift = clock.drift
    val sparkles = state.sparkles.value
    if (sparkles > 0f) {
        // Each sparkle twinkles on its own beat.
        MascotPaths.layer("sparkles").forEachIndexed { index, shape ->
            val twinkle = 0.55f + 0.45f * sin(drift * 2.3f + index * 2.1f)
            val pulse = 0.85f + 0.15f * sin(drift * 2.3f + index * 2.1f + 1f)
            scale(pulse, pulse, pivot = shape.bounds.center) { drawShape(shape, sparkles * twinkle) }
        }
    }
    val drop = state.drop.value
    if (drop > 0f) translate(0f, (1f - drop) * -4f + 0.8f * sin(drift * 0.7f)) { drawLayer("drop", drop) }
    val zz = state.zz.value
    if (zz > 0f) {
        // The z's float up one after the other.
        MascotPaths.layer("zz").forEachIndexed { index, shape ->
            val phase = drift * 0.9f + index * 1.4f
            val rise = -2.5f - 2.5f * sin(phase)
            val fade = 0.6f + 0.4f * sin(phase + 0.6f)
            translate(0.6f * sin(phase * 0.5f), rise) { drawShape(shape, zz * fade) }
        }
    }
}

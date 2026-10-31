package io.github.itemujinwork.dalma.ui.mascot

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.itemujinwork.dalma.ui.LocalReducedMotion
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import kotlin.math.sin

private class Variants(val eyeL: String, val eyeR: String, val mouth: String, val browL: String, val browR: String)

private fun variants(expression: Expression): Variants = when (expression) {
    Expression.NEUTRAL -> Variants("eye_l_open", "eye_r_open", "mouth_smile", "brow_l", "brow_r")
    Expression.HAPPY -> Variants("eye_l_happy", "eye_r_happy", "mouth_happy", "brow_l", "brow_r")
    Expression.SORRY -> Variants("eye_l_open", "eye_r_open", "mouth_sorry", "brow_l_sorry", "brow_r_sorry")
    Expression.SLEEPY -> Variants("eye_l_sleepy", "eye_r_sleepy", "mouth_sleepy", "brow_l", "brow_r")
}

/**
 * Dalma, drawn from the vector layers. The 240 x 240 art box is scaled to the width of
 * the composable and aligned to its bottom edge, so her shoulders run off the screen
 * the way the mock-ups show.
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
            targetValue = (Math.PI * 2).toFloat(),
            animationSpec = infiniteRepeatable(tween(6_000)),
            label = "zz",
        ).value
    }

    val semanticsModifier = if (contentDescription != null) modifier.semantics { this.contentDescription = contentDescription } else modifier
    Canvas(semanticsModifier) {
        val art = MascotArt.SIZE
        val s = size.width / art
        val top = size.height - art * s
        withTransform({
            translate(0f, top)
            scale(s, s, Offset.Zero)
        }) {
            drawMascot(state, breath, drift)
        }
    }
}

private fun DrawScope.drawMascot(state: MascotState, breath: Float, drift: Float) {
    val turn = state.turn.value
    val closeness = state.closeness.value
    val hop = state.hop.value
    val neck = Offset(120f, 168f)

    scale(closeness, closeness, pivot = Offset(120f, 240f)) {
        translate(0f, hop) {
            // The body breathes: the shoulders rise a little.
            scale(1f + 0.008f * breath, 1f + 0.014f * breath, pivot = Offset(120f, 240f)) {
                drawLayer("body")
            }
            // The head sits on the neck, nods and tilts around it, and rises with each breath.
            withTransform({
                translate(0f, -1.6f * breath)
                rotate(state.tilt.value, pivot = neck)
            }) {
                drawHead(state, turn)
            }
            drawExtras(state, drift)
        }
    }
}

private fun DrawScope.drawHead(state: MascotState, turn: Float) {
    val nodDip = state.nod.value * 0.9f
    val nodSquash = 1f - state.nod.value * 0.004f
    val headPivot = Offset(120f, 104f)
    withTransform({
        translate(0f, nodDip)
        scale(1f, nodSquash, pivot = headPivot)
    }) {
        // Turning three-quarters: the head narrows, the back hair shows a little more.
        scale(1f - 0.07f * turn, 1f, pivot = headPivot) {
            translate(5f * turn, 0f) { drawLayer("hair_back") }
            drawLayer("head")
            drawLayer("blush")
        }
        // The face slides towards the turn and narrows.
        withTransform({
            translate(-14f * turn, 1.5f * turn)
            scale(1f - 0.12f * turn, 1f, pivot = headPivot)
        }) {
            drawLayer("nose")
            drawFace(state)
        }
        scale(1f - 0.07f * turn, 1f, pivot = headPivot) {
            translate(-3f * turn, 0f) { drawLayer("hair_front") }
        }
        // The hair clip comes forward and grows as she turns.
        val clipCenter = MascotPaths.clipBounds.center
        withTransform({
            translate(-9f * turn, 2.5f * turn)
            scale(1f + 0.28f * turn, 1f + 0.28f * turn, pivot = clipCenter)
        }) {
            drawLayer("clip")
            state.clipFill?.let { drawClipFill(it) }
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
    drawLayer(v.mouth, alpha)
    drawLayer(v.browL, alpha)
    drawLayer(v.browR, alpha)

    val blink = state.blinkFrame
    val openEyes = expression == Expression.NEUTRAL || expression == Expression.SORRY
    var eyeL = v.eyeL
    var eyeR = v.eyeR
    if (openEyes) {
        when (blink) {
            1 -> { eyeL = "eye_l_sleepy"; eyeR = "eye_r_sleepy" }
            2 -> { eyeL = "eye_l_closed"; eyeR = "eye_r_closed" }
        }
        if (state.wink && blink == 0) eyeR = "eye_r_closed"
    } else if (expression == Expression.SLEEPY && blink == 2) {
        eyeL = "eye_l_closed"
        eyeR = "eye_r_closed"
    }
    drawEye(eyeL, state, alpha)
    drawEye(eyeR, state, alpha)
}

private fun DrawScope.drawEye(layer: String, state: MascotState, alpha: Float) {
    val gaze = Offset(state.gazeX.value * 2.6f, state.gazeY.value * 1.4f)
    for (shape in MascotPaths.layer(layer)) {
        if (shape.isPupil) {
            translate(gaze.x, gaze.y) { drawShape(shape, alpha) }
        } else {
            drawShape(shape, alpha)
        }
    }
}

/** The download fills the clip from the bottom; the empty part is deep teal. */
private fun DrawScope.drawClipFill(fill: Float) {
    val bounds = MascotPaths.clipBounds
    val emptyHeight = bounds.height * (1f - fill.coerceIn(0f, 1f))
    if (emptyHeight > 0f) {
        clipRect(bounds.left - 2f, bounds.top - 2f, bounds.right + 2f, bounds.top + emptyHeight) {
            drawPath(MascotPaths.clip, DalmaColors.DeepTeal)
        }
    }
    drawPath(MascotPaths.clip, DalmaColors.Mint, style = Stroke(width = 1.6f))
}

private fun DrawScope.drawExtras(state: MascotState, drift: Float) {
    val sparkles = state.sparkles.value
    if (sparkles > 0f) {
        val pulse = 0.9f + 0.1f * sin(drift * 2f)
        scale(pulse, pulse, pivot = Offset(120f, 90f)) { drawLayer("sparkles", sparkles) }
    }
    val drop = state.drop.value
    if (drop > 0f) translate(0f, (1f - drop) * -4f) { drawLayer("drop", drop) }
    val zz = state.zz.value
    if (zz > 0f) translate(0f, -3f * sin(drift)) { drawLayer("zz", zz) }
}

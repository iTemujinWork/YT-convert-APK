package io.github.itemujinwork.dalma.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val TWO_PI = (Math.PI * 2).toFloat()

/** Asks the sky for a shooting star. */
@Stable
class SkyState {
    var shots by mutableIntStateOf(0)
        private set

    fun shoot() {
        shots++
    }
}

@Composable
fun rememberSkyState(): SkyState = remember { SkyState() }

private class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val phase: Float,
    val speed: Float,
    val sparkle: Boolean,
)

private fun makeStars(seed: Int, count: Int): List<Star> {
    val random = Random(seed)
    return List(count) {
        Star(
            x = random.nextFloat(),
            y = random.nextFloat(),
            radius = 0.6f + random.nextFloat() * 1.1f,
            alpha = 0.2f + random.nextFloat() * 0.6f,
            phase = random.nextFloat() * TWO_PI,
            speed = 0.5f + random.nextFloat() * 1.5f,
            sparkle = random.nextFloat() < 0.07f,
        )
    }
}

/**
 * A quiet starry night. No constellations, no lines, no moon.
 *
 * @param glow 0..1, while a download runs the stars brighten a little
 * @param ambientShootingStars on the home screen a shooting star crosses now and then
 */
@Composable
fun Sky(
    modifier: Modifier = Modifier,
    state: SkyState = rememberSkyState(),
    glow: Float = 0f,
    ambientShootingStars: Boolean = false,
    seed: Int = 7,
) {
    val reducedMotion = LocalReducedMotion.current
    val stars = remember(seed) { makeStars(seed, 120) }

    val time: Float = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "sky")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = TWO_PI,
            animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
            label = "twinkle",
        ).value
    }

    val shooting = remember { Animatable(0f) }
    var shotSeed by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.shots) {
        if (state.shots > 0 && !reducedMotion) {
            shotSeed = state.shots * 31 + seed
            shooting.snapTo(0f)
            shooting.animateTo(1f, tween(1_100, easing = LinearOutSlowInEasing))
            shooting.snapTo(0f)
        }
    }
    if (ambientShootingStars && !reducedMotion) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(18_000, 55_000))
                state.shoot()
            }
        }
    }

    Canvas(modifier) {
        val brightness = 1f + 0.8f * glow.coerceIn(0f, 1f)
        for (star in stars) {
            val twinkle = if (reducedMotion) 1f else 0.65f + 0.35f * sin(time * star.speed + star.phase)
            val alpha = (star.alpha * twinkle * brightness).coerceIn(0f, 1f)
            val center = Offset(star.x * size.width, star.y * size.height)
            if (star.sparkle) {
                drawSparkle(center, (2.2f + star.radius).dp.toPx(), alpha)
            } else {
                drawCircle(DalmaColors.Milk, radius = star.radius.dp.toPx(), center = center, alpha = alpha)
            }
        }
        val progress = shooting.value
        if (progress > 0f) drawShootingStar(progress, shotSeed)
    }
}

/** A tiny four-pointed star, like the ones in the mock-ups. */
private fun DrawScope.drawSparkle(center: Offset, arm: Float, alpha: Float) {
    val width = Stroke(width = 1.2f.dp.toPx(), cap = StrokeCap.Round)
    drawLine(DalmaColors.Mint, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), width.width, width.cap, alpha = alpha)
    drawLine(DalmaColors.Mint, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), width.width, width.cap, alpha = alpha)
}

private fun DrawScope.drawShootingStar(progress: Float, seed: Int) {
    val random = Random(seed)
    val start = Offset((0.08f + random.nextFloat() * 0.6f) * size.width, (0.06f + random.nextFloat() * 0.4f) * size.height)
    val angle = (10f + random.nextFloat() * 18f) * (Math.PI / 180).toFloat()
    val direction = Offset(cos(angle), sin(angle))
    val travel = size.width * 0.32f
    val head = start + direction * (travel * progress)
    val tailLength = size.width * 0.14f * (1f - 0.4f * progress)
    val tail = head - direction * tailLength
    val fade = if (progress > 0.75f) (1f - progress) / 0.25f else 1f
    drawLine(
        brush = Brush.linearGradient(listOf(DalmaColors.Milk.copy(alpha = 0f), DalmaColors.Milk.copy(alpha = 0.9f * fade)), start = tail, end = head),
        start = tail,
        end = head,
        strokeWidth = 1.4f.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(DalmaColors.Milk, radius = 1.9f.dp.toPx(), center = head, alpha = fade)
}

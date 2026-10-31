package io.github.itemujinwork.dalma.ui.mascot

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser

/** One drawable shape of the mascot, parsed once from [MascotArt]. */
internal class MascotShape(
    val path: Path,
    val bounds: Rect,
    val fill: Color?,
    val fillAlpha: Float,
    val stroke: Color?,
    val strokeAlpha: Float,
    val strokeWidth: Float,
    val cap: StrokeCap,
    val join: StrokeJoin,
    /** Iris, pupil and highlight: the parts that move when she looks around. */
    val isPupil: Boolean,
)

internal object MascotPaths {
    val layers: Map<String, List<MascotShape>> by lazy {
        MascotArt.layers.mapValues { (name, shapes) -> shapes.map { it.toShape(name) } }
    }

    val clip: Path by lazy { PathParser().parsePathString(MascotArt.CLIP_SHAPE).toPath() }
    val clipBounds: Rect by lazy { clip.getBounds() }

    fun layer(name: String): List<MascotShape> = layers[name].orEmpty()

    private fun MascotArt.Shape.toShape(layerName: String): MascotShape {
        val path = PathParser().parsePathString(d).toPath()
        val bounds = path.getBounds()
        val fillColor = if (fill != 0L) Color(fill) else null
        val openEye = layerName.startsWith("eye_") && layerName.endsWith("_open")
        val isPupil = openEye && fillColor != null &&
            (fillColor.luminance() < 0.35f || (fillColor.luminance() > 0.85f && bounds.width < 7f && bounds.height < 7f))
        return MascotShape(
            path = path,
            bounds = bounds,
            fill = fillColor,
            fillAlpha = fillAlpha,
            stroke = if (stroke != 0L) Color(stroke) else null,
            strokeAlpha = strokeAlpha,
            strokeWidth = strokeWidth,
            cap = when (cap) {
                1 -> StrokeCap.Round
                2 -> StrokeCap.Square
                else -> StrokeCap.Butt
            },
            join = when (join) {
                1 -> StrokeJoin.Round
                2 -> StrokeJoin.Bevel
                else -> StrokeJoin.Miter
            },
            isPupil = isPupil,
        )
    }
}

internal fun DrawScope.drawShape(shape: MascotShape, alpha: Float = 1f) {
    if (alpha <= 0f) return
    shape.fill?.let { drawPath(shape.path, it, alpha = (shape.fillAlpha * alpha).coerceIn(0f, 1f)) }
    shape.stroke?.let {
        drawPath(
            shape.path,
            it,
            alpha = (shape.strokeAlpha * alpha).coerceIn(0f, 1f),
            style = Stroke(width = shape.strokeWidth, cap = shape.cap, join = shape.join),
        )
    }
}

internal fun DrawScope.drawLayer(name: String, alpha: Float = 1f) {
    for (shape in MascotPaths.layer(name)) drawShape(shape, alpha)
}

package io.github.itemujinwork.dalma.ui.mascot

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.util.lerp

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
    /** The path's commands, for morphing into another shape of the same build. */
    val nodes: List<PathNode>,
)

internal object MascotPaths {
    val layers: Map<String, List<MascotShape>> by lazy {
        MascotArt.layers.mapValues { (name, shapes) -> shapes.map { it.toShape(name) } }
    }

    val clip: Path by lazy { PathParser().parsePathString(MascotArt.CLIP_SHAPE).toPath() }
    val clipBounds: Rect by lazy { clip.getBounds() }

    /** The face's inner outline from the pose files: the fringe's shadow is cut to it, as their clip-path does. */
    val faceClip: Path by lazy { layer("face_clip").firstOrNull()?.path ?: Path() }

    fun layer(name: String): List<MascotShape> = layers[name].orEmpty()

    private fun MascotArt.Shape.toShape(layerName: String): MascotShape {
        val parser = PathParser().parsePathString(d)
        val path = parser.toPath()
        val nodes = parser.toNodes()
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
            nodes = nodes,
        )
    }

    /**
     * A shape on the way from [a] to [b] (the brows morphing into the wink's): the same commands,
     * every number blended by [t]. Shapes built differently stay [a].
     */
    fun morph(a: MascotShape, b: MascotShape, t: Float): Path {
        if (a.nodes.size != b.nodes.size) return a.path
        val path = Path()
        for ((from, to) in a.nodes.zip(b.nodes)) {
            when {
                from is PathNode.MoveTo && to is PathNode.MoveTo -> path.moveTo(lerp(from.x, to.x, t), lerp(from.y, to.y, t))
                from is PathNode.LineTo && to is PathNode.LineTo -> path.lineTo(lerp(from.x, to.x, t), lerp(from.y, to.y, t))
                from is PathNode.QuadTo && to is PathNode.QuadTo -> path.quadraticTo(lerp(from.x1, to.x1, t), lerp(from.y1, to.y1, t), lerp(from.x2, to.x2, t), lerp(from.y2, to.y2, t))
                from is PathNode.CurveTo && to is PathNode.CurveTo -> path.cubicTo(lerp(from.x1, to.x1, t), lerp(from.y1, to.y1, t), lerp(from.x2, to.x2, t), lerp(from.y2, to.y2, t), lerp(from.x3, to.x3, t), lerp(from.y3, to.y3, t))
                from is PathNode.Close && to is PathNode.Close -> path.close()
                else -> return a.path
            }
        }
        return path
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

/** Layer [a] morphing into layer [b] by [t], shape by shape, painted like [a]. */
internal fun DrawScope.drawMorphedLayer(a: String, b: String, t: Float, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val from = MascotPaths.layer(a)
    val to = MascotPaths.layer(b)
    if (t <= 0f || from.size != to.size) {
        drawLayer(a, alpha)
        return
    }
    for ((shapeA, shapeB) in from.zip(to)) {
        val path = MascotPaths.morph(shapeA, shapeB, t)
        shapeA.fill?.let { drawPath(path, it, alpha = (shapeA.fillAlpha * alpha).coerceIn(0f, 1f)) }
        shapeA.stroke?.let {
            drawPath(path, it, alpha = (shapeA.strokeAlpha * alpha).coerceIn(0f, 1f), style = Stroke(width = shapeA.strokeWidth, cap = shapeA.cap, join = shapeA.join))
        }
    }
}

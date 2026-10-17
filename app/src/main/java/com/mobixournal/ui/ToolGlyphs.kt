package com.mobixournal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Outline figure glyphs drawn on Material's 24-unit icon grid with a 1.5-unit stroke width and
 * no fill, ensuring figures in the Shapes submenu appear consistently as monoline outlines.
 */

private fun buildOutlineIcon(name: String, outline: List<Pair<Float, Float>>): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            val (firstX, firstY) = outline.first()
            moveTo(firstX, firstY)
            outline.drop(1).forEach { (x, y) ->
                lineTo(x, y)
            }
            close()
        }
    }.build()

/** The **Rhombus** figure's outline vertices. */
val RHOMBUS_OUTLINE: List<Pair<Float, Float>> = listOf(
    12f to 3f,
    21f to 12f,
    12f to 21f,
    3f to 12f,
)

/**
 * The **Trapezoid** figure's outline vertices — the isosceles trapezoid the tool ships as its
 * default, so the glyph reads as the shape the tool draws.
 */
val TRAPEZOID_OUTLINE: List<Pair<Float, Float>> = listOf(
    8f to 8f,
    16f to 8f,
    21f to 19f,
    3f to 19f,
)

/** The **Rectangle** figure's outline vertices. */
val RECTANGLE_OUTLINE: List<Pair<Float, Float>> = listOf(
    3f to 6f,
    21f to 6f,
    21f to 18f,
    3f to 18f,
)

/** The **Square** figure's outline vertices. */
val SQUARE_OUTLINE: List<Pair<Float, Float>> = listOf(
    3f to 3f,
    21f to 3f,
    21f to 21f,
    3f to 21f,
)

/** Computes vertices for a regular [sides]-gon inscribed in a circle of radius [r]. */
internal fun regularPolygonOutline(
    sides: Int,
    cx: Float = 12f,
    cy: Float = 12f,
    r: Float = 9f,
): List<Pair<Float, Float>> =
    (0 until sides).map { i ->
        val a = -PI / 2.0 + 2.0 * PI * i / sides
        (cx + (r * cos(a)).toFloat()) to (cy + (r * sin(a)).toFloat())
    }

/** The **Pentagon** figure's outline vertices. */
val PENTAGON_OUTLINE: List<Pair<Float, Float>> = regularPolygonOutline(5)

/** The **Hexagon** figure's outline vertices. */
val HEXAGON_OUTLINE: List<Pair<Float, Float>> = regularPolygonOutline(6)

/** Hollow rhombus outline glyph. */
val RhombusIcon: ImageVector by lazy { buildOutlineIcon("Rhombus", RHOMBUS_OUTLINE) }

/** Hollow trapezoid outline glyph. */
val TrapezoidIcon: ImageVector by lazy { buildOutlineIcon("Trapezoid", TRAPEZOID_OUTLINE) }

/** Hollow rectangle outline glyph. */
val RectangleIcon: ImageVector by lazy { buildOutlineIcon("Rectangle", RECTANGLE_OUTLINE) }

/** Hollow square outline glyph. */
val SquareIcon: ImageVector by lazy { buildOutlineIcon("Square", SQUARE_OUTLINE) }

/** Hollow pentagon outline glyph. */
val PentagonIcon: ImageVector by lazy { buildOutlineIcon("Pentagon", PENTAGON_OUTLINE) }

/** Hollow hexagon outline glyph. */
val HexagonIcon: ImageVector by lazy { buildOutlineIcon("Hexagon", HEXAGON_OUTLINE) }

/**
 * One monoline outline per **triangle variant**, drawn on the same 24-unit grid as the shape icons.
 *
 * The proportions are what tell the kinds apart at 18dp: the equilateral is wide and short, the
 * isosceles is the tall narrow one with its apex centred, the right-angled one stands on a vertical
 * leg and the scalene's apex leans off-centre.
 */
private val TRIANGLE_KIND_OUTLINES: Map<TriangleKind, List<Pair<Float, Float>>> = mapOf(
    TriangleKind.EQUILATERAL to listOf(12f to 4f, 20f to 17f, 4f to 17f),
    TriangleKind.RIGHT to listOf(4f to 4f, 4f to 19f, 20f to 19f),
    TriangleKind.ISOSCELES to listOf(12f to 2.5f, 18f to 19f, 6f to 19f),
    TriangleKind.SCALENE to listOf(8.5f to 3f, 20f to 19f, 4f to 19f),
)

/** One monoline outline per **trapezoid variant**: centred, right-angled, then the uneven one. */
private val TRAPEZOID_KIND_OUTLINES: Map<TrapezoidKind, List<Pair<Float, Float>>> = mapOf(
    TrapezoidKind.ISOSCELES to listOf(8f to 7f, 16f to 7f, 20f to 19f, 4f to 19f),
    TrapezoidKind.RIGHT to listOf(5f to 7f, 12f to 7f, 20f to 19f, 5f to 19f),
    TrapezoidKind.SCALENE to listOf(9f to 7f, 17f to 7f, 20f to 19f, 4f to 19f),
)

private val triangleKindIcons: Map<TriangleKind, ImageVector> by lazy {
    TRIANGLE_KIND_OUTLINES.mapValues { (kind, outline) -> buildOutlineIcon("Triangle ${kind.name}", outline) }
}

private val trapezoidKindIcons: Map<TrapezoidKind, ImageVector> by lazy {
    TRAPEZOID_KIND_OUTLINES.mapValues { (kind, outline) -> buildOutlineIcon("Trapezoid ${kind.name}", outline) }
}

/** The outline that represents [kind] beside its name in the picker and in Settings. */
internal fun triangleKindIcon(kind: TriangleKind): ImageVector = triangleKindIcons.getValue(kind)

/** The outline that represents [kind] beside its name in the picker and in Settings. */
internal fun trapezoidKindIcon(kind: TrapezoidKind): ImageVector = trapezoidKindIcons.getValue(kind)

/**
 * The **Cartesian axis** glyph: a first-quadrant coordinate diagram — two arrowed axes meeting at an
 * origin, with a plotted curve between them.
 *
 * It replaces Material's `ShowChart`, whose bar chart read as a statistics icon rather than the
 * oriented X/Y plane this tool draws. The axes carry arrowheads (the tool's own axes do too) and the
 * curve says "something is plotted here", so the button shows the figure it makes.
 */
val CoordinateAxisIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CoordinateAxis",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // Y axis, up from the origin, with its head.
            moveTo(5f, 20f)
            lineTo(5f, 3f)
            moveTo(2.4f, 7f)
            lineTo(5f, 3f)
            lineTo(7.6f, 7f)
            // X axis, right from the origin, with its head.
            moveTo(5f, 20f)
            lineTo(21f, 20f)
            moveTo(17f, 17.4f)
            lineTo(21f, 20f)
            lineTo(17f, 22.6f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // A plotted curve in the first quadrant.
            moveTo(8f, 17f)
            quadTo(11.5f, 17f, 17f, 7f)
        }
    }.build()
}

/** Distinct data table glyph with prominent header row and columns, contrasting with paper grid. */
val TableIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Table",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Outline header bar across top
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 3f)
            lineTo(21f, 3f)
            lineTo(21f, 8f)
            lineTo(3f, 8f)
            close()
        }
        // Outer border and interior cell lines
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 8f)
            lineTo(21f, 8f)
            lineTo(21f, 21f)
            lineTo(3f, 21f)
            close()
            moveTo(3f, 14.5f)
            lineTo(21f, 14.5f)
            moveTo(12f, 8f)
            lineTo(12f, 21f)
        }
    }.build()
}

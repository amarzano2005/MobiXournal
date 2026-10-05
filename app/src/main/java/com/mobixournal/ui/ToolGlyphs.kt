package com.mobixournal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Outline figure glyphs drawn on Material's 24-unit icon grid with a 2-unit stroke width and
 * no fill, ensuring figures in the Shapes submenu appear consistently as outlines rather than solid shapes.
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
            strokeLineWidth = 2f,
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

/** Hollow square outline glyph. */
val SquareIcon: ImageVector by lazy { buildOutlineIcon("Square", SQUARE_OUTLINE) }

/** Hollow pentagon outline glyph. */
val PentagonIcon: ImageVector by lazy { buildOutlineIcon("Pentagon", PENTAGON_OUTLINE) }

/** Hollow hexagon outline glyph. */
val HexagonIcon: ImageVector by lazy { buildOutlineIcon("Hexagon", HEXAGON_OUTLINE) }

/** Distinct data table glyph with prominent header row and columns, contrasting with paper grid. */
val TableIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Table",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Filled header bar across top (distinct from plain wire grid)
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
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
            strokeLineWidth = 2f,
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

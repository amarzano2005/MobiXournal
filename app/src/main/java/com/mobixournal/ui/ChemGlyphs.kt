package com.mobixournal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Rail and menu glyphs for the chemistry tools: the three bond orders and the aromatic ring. Drawn
 * on Material's 24-unit icon grid with a 2-unit round stroke, matching [CircuitGlyphs].
 */

/** A single bond: one line, tilted 60°. */
val BondSingleIcon: ImageVector by lazy {
    ImageVector.Builder("BondSingle", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4.5f, 19.5f)
            lineTo(19.5f, 10.84f)
        }
    }.build()
}

/** A double bond: two parallel lines, tilted 60°. */
val BondDoubleIcon: ImageVector by lazy {
    ImageVector.Builder("BondDouble", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3.0f, 17.0f); lineTo(18.0f, 8.34f)
            moveTo(6.0f, 22.0f); lineTo(21.0f, 13.34f)
        }
    }.build()
}

/** A triple bond: three parallel lines, tilted 60°. */
val BondTripleIcon: ImageVector by lazy {
    ImageVector.Builder("BondTriple", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2.0f, 14.0f); lineTo(15.0f, 6.49f)
            moveTo(5.0f, 19.0f); lineTo(19.0f, 10.92f)
            moveTo(9.0f, 23.0f); lineTo(22.0f, 15.49f)
        }
    }.build()
}

/** The aromatic ring: a hexagon with an inner circle. */
val BenzeneIcon: ImageVector by lazy {
    ImageVector.Builder("Benzene", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // Hexagon, vertex at the top (radius 8 about (12, 13)).
            moveTo(12f, 5f)
            lineTo(18.93f, 9f)
            lineTo(18.93f, 17f)
            lineTo(12f, 21f)
            lineTo(5.07f, 17f)
            lineTo(5.07f, 9f)
            close()
            // Inner aromatic circle (radius 4.6), as two half arcs.
            moveTo(16.6f, 13f)
            arcTo(4.6f, 4.6f, 0f, true, true, 7.4f, 13f)
            arcTo(4.6f, 4.6f, 0f, true, true, 16.6f, 13f)
            close()
        }
    }.build()
}

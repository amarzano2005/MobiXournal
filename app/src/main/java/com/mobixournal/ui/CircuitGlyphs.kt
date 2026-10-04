package com.mobixournal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Rail and menu glyphs for electronic circuit components and logic gates.
 * All glyphs fit Material's 24x24 icon viewport with an outline stroke.
 */

val ResistorIcon: ImageVector by lazy {
    ImageVector.Builder("Resistor", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f)
            lineTo(6f, 12f)
            lineTo(8f, 7f)
            lineTo(11f, 17f)
            lineTo(13f, 7f)
            lineTo(16f, 17f)
            lineTo(18f, 12f)
            lineTo(22f, 12f)
        }
    }.build()
}

val CapacitorIcon: ImageVector by lazy {
    ImageVector.Builder("Capacitor", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f)
            lineTo(9f, 12f)
            moveTo(9f, 5f)
            lineTo(9f, 19f)
            moveTo(15f, 5f)
            lineTo(15f, 19f)
            moveTo(15f, 12f)
            lineTo(22f, 12f)
        }
    }.build()
}

val InductorIcon: ImageVector by lazy {
    ImageVector.Builder("Inductor", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 14f)
            lineTo(5f, 14f)
            // 3 arches
            curveTo(5f, 8f, 9f, 8f, 9f, 14f)
            curveTo(9f, 8f, 14f, 8f, 14f, 14f)
            curveTo(14f, 8f, 19f, 8f, 19f, 14f)
            lineTo(22f, 14f)
        }
    }.build()
}

val GroundIcon: ImageVector by lazy {
    ImageVector.Builder("Ground", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 3f)
            lineTo(12f, 11f)
            moveTo(5f, 11f)
            lineTo(19f, 11f)
            moveTo(8f, 15f)
            lineTo(16f, 15f)
            moveTo(10f, 19f)
            lineTo(14f, 19f)
        }
    }.build()
}

val AndGateIcon: ImageVector by lazy {
    ImageVector.Builder("AndGate", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 8f)
            lineTo(7f, 8f)
            moveTo(2f, 16f)
            lineTo(7f, 16f)
            moveTo(7f, 5f)
            lineTo(12f, 5f)
            curveTo(17f, 5f, 17f, 19f, 12f, 19f)
            lineTo(7f, 19f)
            close()
            moveTo(16f, 12f)
            lineTo(22f, 12f)
        }
    }.build()
}

val OrGateIcon: ImageVector by lazy {
    ImageVector.Builder("OrGate", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 8f)
            lineTo(8f, 8f)
            moveTo(2f, 16f)
            lineTo(8f, 16f)
            moveTo(6f, 5f)
            curveTo(9f, 10f, 9f, 14f, 6f, 19f)
            curveTo(13f, 19f, 17f, 15f, 18f, 12f)
            curveTo(17f, 9f, 13f, 5f, 6f, 5f)
            moveTo(18f, 12f)
            lineTo(22f, 12f)
        }
    }.build()
}

val NotGateIcon: ImageVector by lazy {
    ImageVector.Builder("NotGate", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f)
            lineTo(6f, 12f)
            moveTo(6f, 6f)
            lineTo(14f, 12f)
            lineTo(6f, 18f)
            close()
            moveTo(18f, 12f)
            lineTo(22f, 12f)
            moveTo(14f, 12f)
            // bubble: draw small circle
            curveTo(14f, 10.8f, 15f, 9.8f, 16f, 10f)
            curveTo(17.2f, 10.2f, 17.8f, 11.2f, 17.6f, 12.4f)
            curveTo(17.4f, 13.6f, 16.2f, 14.2f, 15f, 13.8f)
            curveTo(14.2f, 13.5f, 14f, 12.8f, 14f, 12f)
        }
    }.build()
}

val NandGateIcon: ImageVector by lazy {
    ImageVector.Builder("NandGate", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 8f); lineTo(6f, 8f)
            moveTo(2f, 16f); lineTo(6f, 16f)
            moveTo(6f, 5f); lineTo(10f, 5f)
            curveTo(14.5f, 5f, 14.5f, 19f, 10f, 19f)
            lineTo(6f, 19f); close()
            moveTo(14f, 12f)
            curveTo(14f, 10.8f, 15f, 9.8f, 16f, 10f)
            curveTo(17.2f, 10.2f, 17.8f, 11.2f, 17.6f, 12.4f)
            curveTo(17.4f, 13.6f, 16.2f, 14.2f, 15f, 13.8f)
            curveTo(14.2f, 13.5f, 14f, 12.8f, 14f, 12f)
            moveTo(18f, 12f); lineTo(22f, 12f)
        }
    }.build()
}

val NorGateIcon: ImageVector by lazy {
    ImageVector.Builder("NorGate", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 8f); lineTo(7f, 8f)
            moveTo(2f, 16f); lineTo(7f, 16f)
            moveTo(5f, 5f)
            curveTo(8f, 10f, 8f, 14f, 5f, 19f)
            curveTo(11f, 19f, 14f, 15f, 15f, 12f)
            curveTo(14f, 9f, 11f, 5f, 5f, 5f)
            moveTo(15f, 12f)
            curveTo(15f, 10.8f, 16f, 9.8f, 17f, 10f)
            curveTo(18.2f, 10.2f, 18.8f, 11.2f, 18.6f, 12.4f)
            curveTo(18.4f, 13.6f, 17.2f, 14.2f, 16f, 13.8f)
            curveTo(15.2f, 13.5f, 15f, 12.8f, 15f, 12f)
            moveTo(19f, 12f); lineTo(22f, 12f)
        }
    }.build()
}

val XorGateIcon: ImageVector by lazy {
    ImageVector.Builder("XorGate", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 8f); lineTo(7f, 8f)
            moveTo(2f, 16f); lineTo(7f, 16f)
            moveTo(3f, 5f)
            curveTo(6f, 10f, 6f, 14f, 3f, 19f)
            moveTo(6f, 5f)
            curveTo(9f, 10f, 9f, 14f, 6f, 19f)
            curveTo(13f, 19f, 17f, 15f, 18f, 12f)
            curveTo(17f, 9f, 13f, 5f, 6f, 5f)
            moveTo(18f, 12f); lineTo(22f, 12f)
        }
    }.build()
}

val XnorGateIcon: ImageVector by lazy {
    ImageVector.Builder("XnorGate", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 8f); lineTo(7f, 8f)
            moveTo(2f, 16f); lineTo(7f, 16f)
            moveTo(3f, 5f)
            curveTo(6f, 10f, 6f, 14f, 3f, 19f)
            moveTo(6f, 5f)
            curveTo(9f, 10f, 9f, 14f, 6f, 19f)
            curveTo(11f, 19f, 14f, 15f, 15f, 12f)
            curveTo(14f, 9f, 11f, 5f, 6f, 5f)
            moveTo(15f, 12f)
            curveTo(15f, 10.8f, 16f, 9.8f, 17f, 10f)
            curveTo(18.2f, 10.2f, 18.8f, 11.2f, 18.6f, 12.4f)
            curveTo(18.4f, 13.6f, 17.2f, 14.2f, 16f, 13.8f)
            curveTo(15.2f, 13.5f, 15f, 12.8f, 15f, 12f)
            moveTo(19f, 12f); lineTo(22f, 12f)
        }
    }.build()
}

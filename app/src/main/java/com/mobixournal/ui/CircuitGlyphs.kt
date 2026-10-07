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

val DiodeIcon: ImageVector by lazy {
    ImageVector.Builder("Diode", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f); lineTo(8f, 12f)
            moveTo(8f, 6f); lineTo(16f, 12f); lineTo(8f, 18f); close()
            moveTo(16f, 6f); lineTo(16f, 18f)
            moveTo(16f, 12f); lineTo(22f, 12f)
        }
    }.build()
}

val LedIcon: ImageVector by lazy {
    ImageVector.Builder("LED", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(1f, 14f); lineTo(6f, 14f)
            moveTo(6f, 9f); lineTo(13f, 14f); lineTo(6f, 19f); close()
            moveTo(13f, 9f); lineTo(13f, 19f)
            moveTo(13f, 14f); lineTo(18f, 14f)
            // Light emission rays
            moveTo(10f, 7f); lineTo(15f, 2f)
            moveTo(12f, 2f); lineTo(15f, 2f); lineTo(15f, 5f)
            moveTo(14f, 9f); lineTo(19f, 4f)
            moveTo(16f, 4f); lineTo(19f, 4f); lineTo(19f, 7f)
        }
    }.build()
}

val ZenerDiodeIcon: ImageVector by lazy {
    ImageVector.Builder("ZenerDiode", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f); lineTo(8f, 12f)
            moveTo(8f, 6f); lineTo(16f, 12f); lineTo(8f, 18f); close()
            moveTo(18f, 6f); lineTo(16f, 6f); lineTo(16f, 18f); lineTo(14f, 18f)
            moveTo(16f, 12f); lineTo(22f, 12f)
        }
    }.build()
}

val OpAmpIcon: ImageVector by lazy {
    ImageVector.Builder("OpAmp", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 3f); lineTo(18f, 12f); lineTo(6f, 21f); close()
            moveTo(2f, 7f); lineTo(6f, 7f)
            moveTo(2f, 17f); lineTo(6f, 17f)
            moveTo(18f, 12f); lineTo(22f, 12f)
            // Minus sign
            moveTo(8f, 7f); lineTo(10.5f, 7f)
            // Plus sign
            moveTo(8f, 17f); lineTo(10.5f, 17f)
            moveTo(9.25f, 15.75f); lineTo(9.25f, 18.25f)
        }
    }.build()
}

val BjtNpnIcon: ImageVector by lazy {
    ImageVector.Builder("BjtNpn", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f); lineTo(8f, 12f)
            moveTo(8f, 5f); lineTo(8f, 19f)
            moveTo(8f, 8f); lineTo(17f, 4f); lineTo(22f, 4f)
            moveTo(8f, 16f); lineTo(17f, 20f); lineTo(22f, 20f)
            // Arrow pointing outward
            moveTo(13f, 18f); lineTo(16f, 19.5f); lineTo(14f, 16.5f)
        }
    }.build()
}

val BjtPnpIcon: ImageVector by lazy {
    ImageVector.Builder("BjtPnp", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f); lineTo(8f, 12f)
            moveTo(8f, 5f); lineTo(8f, 19f)
            moveTo(8f, 8f); lineTo(17f, 4f); lineTo(22f, 4f)
            moveTo(8f, 16f); lineTo(17f, 20f); lineTo(22f, 20f)
            // Arrow pointing inward
            moveTo(12.5f, 17.5f); lineTo(9.5f, 16.5f); lineTo(11f, 14.5f)
        }
    }.build()
}

val DcSourceIcon: ImageVector by lazy {
    ImageVector.Builder("DcSource", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f); lineTo(9f, 12f)
            moveTo(9f, 4f); lineTo(9f, 20f)
            moveTo(15f, 8f); lineTo(15f, 16f)
            moveTo(15f, 12f); lineTo(22f, 12f)
            // Optional plus sign
            moveTo(5.5f, 7f); lineTo(7.5f, 7f)
            moveTo(6.5f, 6f); lineTo(6.5f, 8f)
        }
    }.build()
}

val CurrentSourceIcon: ImageVector by lazy {
    ImageVector.Builder("CurrentSource", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f); lineTo(5f, 12f)
            moveTo(5f, 12f)
            curveTo(5f, 8.13f, 8.13f, 5f, 12f, 5f)
            curveTo(15.87f, 5f, 19f, 8.13f, 19f, 12f)
            curveTo(19f, 15.87f, 15.87f, 19f, 12f, 19f)
            curveTo(8.13f, 19f, 5f, 15.87f, 5f, 12f)
            moveTo(19f, 12f); lineTo(22f, 12f)
            // Current direction arrow
            moveTo(8f, 12f); lineTo(16f, 12f)
            moveTo(13f, 9.5f); lineTo(16f, 12f); lineTo(13f, 14.5f)
        }
    }.build()
}


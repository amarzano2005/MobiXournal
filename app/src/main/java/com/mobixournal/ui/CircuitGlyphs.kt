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
            lineTo(5f, 12f)
            lineTo(7.5f, 6.5f)
            lineTo(10.5f, 17.5f)
            lineTo(13.5f, 6.5f)
            lineTo(16.5f, 17.5f)
            lineTo(19f, 12f)
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
            moveTo(2f, 12f); lineTo(9.5f, 12f)
            moveTo(9.5f, 5f); lineTo(9.5f, 19f)
            moveTo(14.5f, 5f); lineTo(14.5f, 19f)
            moveTo(14.5f, 12f); lineTo(22f, 12f)
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
            moveTo(2f, 14f); lineTo(5f, 14f)
            curveTo(5f, 8f, 9.5f, 8f, 9.5f, 14f)
            curveTo(9.5f, 8f, 14.5f, 8f, 14.5f, 14f)
            curveTo(14.5f, 8f, 19f, 8f, 19f, 14f)
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
            moveTo(12f, 3f); lineTo(12f, 11f)
            moveTo(4f, 11f); lineTo(20f, 11f)
            moveTo(7.5f, 15f); lineTo(16.5f, 15f)
            moveTo(10.5f, 19f); lineTo(13.5f, 19f)
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
            moveTo(15.5f, 5.5f); lineTo(15.5f, 18.5f)
            moveTo(15.5f, 12f); lineTo(22f, 12f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 6f)
            lineTo(15.5f, 12f)
            lineTo(8f, 18f)
            close()
        }
    }.build()
}

val LedIcon: ImageVector by lazy {
    ImageVector.Builder("LED", 24.dp, 24.dp, 24f, 24f).apply {
        // Hollow diode body + cathode bar + lead wires
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 13f); lineTo(7.5f, 13f)
            moveTo(7.5f, 7.5f); lineTo(14.5f, 13f); lineTo(7.5f, 18.5f); close()
            moveTo(14.5f, 6.5f); lineTo(14.5f, 19.5f)
            moveTo(14.5f, 13f); lineTo(22f, 13f)
        }
        // Optical emission rays with open V-barb arrowheads
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // Ray 1
            moveTo(10.5f, 6f); lineTo(14.5f, 2f)
            moveTo(12.2f, 2f); lineTo(14.5f, 2f); lineTo(14.5f, 4.3f)
            // Ray 2
            moveTo(15f, 7.5f); lineTo(19f, 3.5f)
            moveTo(16.7f, 3.5f); lineTo(19f, 3.5f); lineTo(19f, 5.8f)
        }
    }.build()
}

val ZenerDiodeIcon: ImageVector by lazy {
    ImageVector.Builder("ZenerDiode", 24.dp, 24.dp, 24f, 24f).apply {
        // Hollow diode body + 90-degree Z-bend cathode bar + lead wires
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f); lineTo(8f, 12f)
            moveTo(8f, 6.5f); lineTo(15.5f, 12f); lineTo(8f, 17.5f); close()
            // Z-shaped cathode bar: top bend right (towards K), bottom bend left (towards A)
            moveTo(18f, 6.5f); lineTo(15.5f, 6.5f); lineTo(15.5f, 17.5f); lineTo(13f, 17.5f)
            moveTo(15.5f, 12f); lineTo(22f, 12f)
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
            moveTo(6.5f, 4.5f); lineTo(18f, 12f); lineTo(6.5f, 19.5f); close()
            moveTo(2f, 8f); lineTo(6.5f, 8f)
            moveTo(2f, 16f); lineTo(6.5f, 16f)
            moveTo(18f, 12f); lineTo(22f, 12f)
            moveTo(8.5f, 8f); lineTo(11f, 8f)
            moveTo(8.5f, 16f); lineTo(11f, 16f)
            moveTo(9.75f, 14.75f); lineTo(9.75f, 17.25f)
        }
    }.build()
}

val BjtNpnIcon: ImageVector by lazy {
    ImageVector.Builder("BjtNpn", 24.dp, 24.dp, 24f, 24f).apply {
        // Enclosing circular body
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6.5f, 12f)
            curveTo(6.5f, 8.41f, 9.41f, 5.5f, 13f, 5.5f)
            curveTo(16.59f, 5.5f, 19.5f, 8.41f, 19.5f, 12f)
            curveTo(19.5f, 15.59f, 16.59f, 18.5f, 13f, 18.5f)
            curveTo(9.41f, 18.5f, 6.5f, 15.59f, 6.5f, 12f)
        }
        // Base lead, collector lead (top) and emitter lead (bottom)
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f); lineTo(9.5f, 12f)
            // Collector branch: slanted then straight UP
            moveTo(9.5f, 10f); lineTo(15.5f, 6f); lineTo(15.5f, 2f)
            // Emitter branch: slanted then straight DOWN
            moveTo(9.5f, 14f); lineTo(15.5f, 18f); lineTo(15.5f, 22f)
        }
        // Semiconductor base bar
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(9.5f, 7.5f); lineTo(9.5f, 16.5f)
        }
        // Emitter arrow pointing OUTWARD (down-right)
        path(fill = SolidColor(Color.Black)) {
            moveTo(15.5f, 18f)
            lineTo(12.2f, 17.8f)
            lineTo(13.6f, 15f)
            close()
        }
    }.build()
}

val BjtPnpIcon: ImageVector by lazy {
    ImageVector.Builder("BjtPnp", 24.dp, 24.dp, 24f, 24f).apply {
        // Enclosing circular body
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6.5f, 12f)
            curveTo(6.5f, 8.41f, 9.41f, 5.5f, 13f, 5.5f)
            curveTo(16.59f, 5.5f, 19.5f, 8.41f, 19.5f, 12f)
            curveTo(19.5f, 15.59f, 16.59f, 18.5f, 13f, 18.5f)
            curveTo(9.41f, 18.5f, 6.5f, 15.59f, 6.5f, 12f)
        }
        // Base lead, collector lead (top) and emitter lead (bottom)
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f); lineTo(9.5f, 12f)
            // Collector branch: slanted then straight UP
            moveTo(9.5f, 10f); lineTo(15.5f, 6f); lineTo(15.5f, 2f)
            // Emitter branch: slanted then straight DOWN
            moveTo(9.5f, 14f); lineTo(15.5f, 18f); lineTo(15.5f, 22f)
        }
        // Semiconductor base bar
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(9.5f, 7.5f); lineTo(9.5f, 16.5f)
        }
        // Emitter arrow pointing INWARD (up-left towards base)
        path(fill = SolidColor(Color.Black)) {
            moveTo(10.2f, 14.4f)
            lineTo(13.6f, 14.2f)
            lineTo(12.2f, 17f)
            close()
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
            moveTo(15f, 12f); lineTo(22f, 12f)
            moveTo(4.5f, 7f); lineTo(7.5f, 7f)
            moveTo(6f, 5.5f); lineTo(6f, 8.5f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(13.5f, 8f)
            lineTo(15.5f, 8f)
            lineTo(15.5f, 16f)
            lineTo(13.5f, 16f)
            close()
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
            moveTo(2f, 12f); lineTo(5.5f, 12f)
            moveTo(18.5f, 12f); lineTo(22f, 12f)
            moveTo(5.5f, 12f)
            curveTo(5.5f, 8.41f, 8.41f, 5.5f, 12f, 5.5f)
            curveTo(15.59f, 5.5f, 18.5f, 8.41f, 18.5f, 12f)
            curveTo(18.5f, 15.59f, 15.59f, 18.5f, 12f, 18.5f)
            curveTo(8.41f, 18.5f, 5.5f, 15.59f, 5.5f, 12f)
            moveTo(8.5f, 12f); lineTo(15.5f, 12f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(16.5f, 12f)
            lineTo(12.5f, 9.5f)
            lineTo(12.5f, 14.5f)
            close()
        }
    }.build()
}

val BufferGateIcon: ImageVector by lazy {
    ImageVector.Builder("BufferGate", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f); lineTo(6f, 12f)
            moveTo(6f, 5f); lineTo(18f, 12f); lineTo(6f, 19f); close()
            moveTo(18f, 12f); lineTo(22f, 12f)
        }
    }.build()
}

val SwitchOpenIcon: ImageVector by lazy {
    ImageVector.Builder("SwitchOpen", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 15f); lineTo(7f, 15f); lineTo(17f, 6f)
            moveTo(17f, 15f); lineTo(22f, 15f)
            moveTo(17f, 12f); lineTo(17f, 18f)
        }
    }.build()
}

val SwitchClosedIcon: ImageVector by lazy {
    ImageVector.Builder("SwitchClosed", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f); lineTo(22f, 12f)
            moveTo(7f, 9f); lineTo(7f, 15f)
            moveTo(17f, 9f); lineTo(17f, 15f)
        }
    }.build()
}

val TransformerIcon: ImageVector by lazy {
    ImageVector.Builder("Transformer", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 8f); lineTo(5f, 8f)
            curveTo(5f, 4f, 9f, 4f, 9f, 8f)
            curveTo(9f, 4f, 13f, 4f, 13f, 8f)
            curveTo(13f, 4f, 17f, 4f, 17f, 8f)
            lineTo(22f, 8f)
            moveTo(2f, 16f); lineTo(5f, 16f)
            curveTo(5f, 20f, 9f, 20f, 9f, 16f)
            curveTo(9f, 20f, 13f, 20f, 13f, 16f)
            curveTo(13f, 20f, 17f, 20f, 17f, 16f)
            lineTo(22f, 16f)
            moveTo(11f, 11f); lineTo(11f, 13f)
            moveTo(13f, 11f); lineTo(13f, 13f)
        }
    }.build()
}

val JunctionDotIcon: ImageVector by lazy {
    ImageVector.Builder("JunctionDot", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 12f)
            curveTo(9f, 10.34f, 10.34f, 9f, 12f, 9f)
            curveTo(13.66f, 9f, 15f, 10.34f, 15f, 12f)
            curveTo(15f, 13.66f, 13.66f, 15f, 12f, 15f)
            curveTo(10.34f, 15f, 9f, 13.66f, 9f, 12f)
            close()
        }
    }.build()
}

val DimensionIcon: ImageVector by lazy {
    ImageVector.Builder("Dimension", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f); lineTo(5f, 9f)
            moveTo(2f, 12f); lineTo(5f, 15f)
            moveTo(22f, 12f); lineTo(19f, 9f)
            moveTo(22f, 12f); lineTo(19f, 15f)
            moveTo(2f, 12f); lineTo(10f, 12f)
            moveTo(14f, 12f); lineTo(22f, 12f)
        }
    }.build()
}


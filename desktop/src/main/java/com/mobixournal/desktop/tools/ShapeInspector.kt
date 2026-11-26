package com.mobixournal.desktop.tools

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.format.XoppColor.withAlpha
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.render.ParametricShapes

/** Translucent fill alpha applied when picking a fill colour so grid/ruling lines remain legible. */
private const val SHAPE_FILL_ALPHA = 0x40

/**
 * The parametric shape inspector body: editable side lengths and interior angles, on-page
 * readout toggles, outline style, and optional fill colour.
 */
@Composable
internal fun ShapeBody(shape: ParametricShapeElement, commit: (Element) -> Unit) {
    if (ParametricShapes.isConic(shape)) {
        ConicGeometrySection(shape, commit)
    } else {
        PolygonGeometrySection(shape, commit)
    }
    SectionCard("Outline") {
        NumberField("width pt", shape.strokeWidth) { value ->
            if (value in 0.5..30.0 && value != shape.strokeWidth) {
                commit(shape.copy(strokeWidth = value))
            }
        }
        ColourRow(current = shape.strokeColor) { colour ->
            commit(shape.copy(strokeColor = colour))
        }
    }
    SectionCard("Fill") {
        ColourRow(current = shape.fillColor?.let { it or 0xFF000000.toInt() }) { colour ->
            commit(shape.copy(fillColor = colour.withAlpha(SHAPE_FILL_ALPHA)))
        }
        if (shape.fillColor != null) {
            Spacer(Modifier.height(4.dp))
            InspectorButton(
                label = "None (clear fill)",
                primary = false,
                enabled = true,
                onClick = { commit(shape.copy(fillColor = null)) },
            )
        }
    }
}

@Composable
private fun ConicGeometrySection(shape: ParametricShapeElement, commit: (Element) -> Unit) {
    val frame = ParametricShapes.conicFrame(shape)
    if (frame != null) {
        SectionCard("Geometry") {
            NumberRow(
                left = {
                    NumberField("radius x pt", frame.rx) { rx ->
                        if (rx >= 4.0 && rx != frame.rx) commit(ParametricShapes.setConicRadii(shape, rxPt = rx))
                    }
                },
                right = {
                    NumberField("radius y pt", frame.ry) { ry ->
                        if (ry >= 4.0 && ry != frame.ry) commit(ParametricShapes.setConicRadii(shape, ryPt = ry))
                    }
                },
            )
            if (shape.shape == ParametricShapes.ELLIPSE) {
                Spacer(Modifier.height(4.dp))
                val avgR = (frame.rx + frame.ry) / 2.0
                InspectorButton(
                    label = "Make circle",
                    primary = false,
                    enabled = true,
                    onClick = { commit(ParametricShapes.setConicRadii(shape, rxPt = avgR, ryPt = avgR)) },
                )
            }
        }
    }
    SectionCard("Labels") {
        SwitchRow("Show radii", shape.showSideLengths) { on ->
            commit(shape.copy(showSideLengths = on))
        }
    }
}

@Composable
private fun PolygonGeometrySection(shape: ParametricShapeElement, commit: (Element) -> Unit) {
    SectionCard("Geometry") {
        for (index in shape.vertices.indices) {
            val side = ParametricShapes.sideLengthPt(shape, index)
            val angle = ParametricShapes.interiorAngleDeg(shape, index)
            NumberRow(
                left = {
                    NumberField("side ${index + 1} pt", side) { length ->
                        if (length > 0.5 && length != side) {
                            commit(ParametricShapes.setSideLength(shape, index, length))
                        }
                    }
                },
                right = {
                    NumberField("angle ${index + 1} °", angle) { deg ->
                        if (deg in 1.0..179.0 && deg != angle) {
                            commit(ParametricShapes.setInteriorAngle(shape, index, deg))
                        }
                    }
                },
            )
        }
    }
    SectionCard("Labels") {
        SwitchRow("Show side lengths", shape.showSideLengths) { on ->
            commit(shape.copy(showSideLengths = on))
        }
        SwitchRow("Show angles", shape.showAngles) { on ->
            commit(shape.copy(showAngles = on))
        }
    }
}

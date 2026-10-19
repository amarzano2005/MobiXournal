package com.mobixournal.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.render.replaceCircuitElement
import com.mobixournal.ui.theme.FloatingSurface
import com.mobixournal.ui.theme.liquidGlassSpringAnimation
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Compact anchored inspector for editing a living [CircuitElement].
 *
 * Sits beside the selected component on the canvas, is freely draggable,
 * and allows quick editing of component value/designation and 90° rotation.
 */
@Composable
fun CircuitInspectorAnchor(
    pane: PaneState,
    ui: EditorUiState,
    canvasSizePx: IntSize,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = { ui.editingCircuit = null },
) {
    val circuit = ui.editingCircuit ?: return
    val surface = pane.surface
    val rect = pane.selectionRect

    var inspectorSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    val initialAnchorRect = remember(circuit.id ?: circuit.hashCode()) { rect }
    val effectiveRect = initialAnchorRect ?: rect

    var dragDelta by remember(circuit.id ?: circuit.hashCode()) { mutableStateOf(Offset.Zero) }

    val baseOffset = plotInspectorOffset(
        selectionLeft = effectiveRect?.left,
        selectionTop = effectiveRect?.top,
        selectionRight = effectiveRect?.right,
        selectionBottom = effectiveRect?.bottom,
        inspectorSize = inspectorSize,
        canvasSize = canvasSizePx,
        gapPx = gapPx,
        marginPx = marginPx,
    )

    val finalOffset = IntOffset(
        x = (baseOffset.x + dragDelta.x.roundToInt()).coerceIn(
            marginPx,
            maxOf(marginPx, canvasSizePx.width - inspectorSize.width - marginPx),
        ),
        y = (baseOffset.y + dragDelta.y.roundToInt()).coerceIn(
            marginPx,
            maxOf(marginPx, canvasSizePx.height - inspectorSize.height - marginPx),
        ),
    )

    var valueText by remember(circuit.id ?: circuit.hashCode()) { mutableStateOf(circuit.value ?: "") }
    var rotation by remember(circuit.id ?: circuit.hashCode()) { mutableDoubleStateOf(circuit.rotation) }

    FloatingSurface(
        modifier = modifier
            .offset { finalOffset }
            .widthIn(min = 240.dp, max = 290.dp)
            .padding(4.dp)
            .liquidGlassSpringAnimation(origin = TransformOrigin(0.5f, 0.5f))
            .onSizeChanged { inspectorSize = it },
    ) {
        CircuitInspectorContent(
            circuit = circuit,
            valueText = valueText,
            onValueTextChange = { valueText = it },
            rotation = rotation,
            onRotate = {
                val nextRot = (rotation + 90.0) % 360.0
                rotation = nextRot
                val updated = circuit.copy(value = valueText.ifBlank { null }, rotation = nextRot)
                surface?.replaceCircuitElement(circuit, updated)
                ui.editingCircuit = updated
            },
            onStraighten = {
                val straight = (round(rotation / 90.0) * 90.0) % 360.0
                val nextRot = if (straight < 0.0) straight + 360.0 else straight
                rotation = nextRot
                val updated = circuit.copy(value = valueText.ifBlank { null }, rotation = nextRot)
                surface?.replaceCircuitElement(circuit, updated)
                ui.editingCircuit = updated
            },
            onDismiss = onDismiss,
            onSave = {
                val updated = circuit.copy(value = valueText.ifBlank { null }, rotation = rotation)
                surface?.replaceCircuitElement(circuit, updated)
                onDismiss()
            },
            onDrag = { dragDelta += it },
        )
    }
}

@Composable
private fun CircuitInspectorContent(
    circuit: CircuitElement,
    valueText: String,
    onValueTextChange: (String) -> Unit,
    rotation: Double,
    onRotate: () -> Unit,
    onStraighten: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDrag: (Offset) -> Unit,
) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircuitInspectorHeader(
            symbol = circuit.symbol,
            onDismiss = onDismiss,
            onDrag = onDrag,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        OutlinedTextField(
            value = valueText,
            onValueChange = onValueTextChange,
            label = { Text("Valore / Sigla") },
            placeholder = { Text("es. 10 kΩ, Vcc, R1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        CircuitRotationControl(
            rotation = rotation,
            onRotate = onRotate,
            onStraighten = onStraighten,
        )
        CircuitInspectorActions(
            onDismiss = onDismiss,
            onSave = onSave,
        )
    }
}

@Composable
private fun CircuitInspectorHeader(
    symbol: String,
    onDismiss: () -> Unit,
    onDrag: (Offset) -> Unit,
) {
    val prettySymbol = symbol.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                },
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Trascina",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = prettySymbol,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Chiudi",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CircuitRotationControl(
    rotation: Double,
    onRotate: () -> Unit,
    onStraighten: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Rotazione: ${rotation.roundToInt()}°",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onStraighten, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Straighten,
                    contentDescription = "Raddrizza (90° multiplo)",
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onRotate, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.RotateRight,
                    contentDescription = "Ruota 90°",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CircuitInspectorActions(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) {
            Text("Annulla")
        }
        Spacer(Modifier.width(6.dp))
        Button(onClick = onSave) {
            Text("Salva")
        }
    }
}

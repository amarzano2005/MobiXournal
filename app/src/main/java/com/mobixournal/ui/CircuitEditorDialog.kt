package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.render.CircuitElementFactory
import com.mobixournal.render.replaceCircuitElement
import com.mobixournal.ui.theme.FloatingSurface
import com.mobixournal.ui.theme.liquidGlassSpringAnimation
import kotlin.math.roundToInt

private val CIRCUIT_PALETTE_COLORS = listOf(
    0xFF000000.toInt(),
    0xFF1E88E5.toInt(),
    0xFFD32F2F.toInt(),
    0xFF388E3C.toInt(),
    0xFFF57C00.toInt(),
    0xFF7B1FA2.toInt(),
)

/**
 * In-place editor for a selected living [CircuitElement].
 */
@Composable
fun CircuitEditorDialog(
    ui: EditorUiState,
    pane: PaneState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = { ui.editingCircuit = null },
) {
    val circuit = ui.editingCircuit ?: return
    var selectedSymbol by remember { mutableStateOf(circuit.symbol) }
    var valueText by remember { mutableStateOf(circuit.value ?: "") }
    var strokeColor by remember { mutableIntStateOf(circuit.strokeColor) }
    var strokeWidth by remember { mutableDoubleStateOf(circuit.strokeWidth) }
    var rotation by remember { mutableDoubleStateOf(circuit.rotation) }
    var pins by remember { mutableStateOf(circuit.pins) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FloatingSurface(
            modifier = modifier
                .offset { IntOffset(dragDelta.x.roundToInt(), dragDelta.y.roundToInt()) }
                .widthIn(min = 340.dp, max = 500.dp)
                .fillMaxWidth(0.92f)
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .liquidGlassSpringAnimation(origin = TransformOrigin(0.5f, 0.5f)),
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircuitDialogHeader(
                    onDismiss = onDismiss,
                    modifier = Modifier.pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            dragDelta += dragAmount
                        }
                    },
                )
                CircuitSymbolPicker(
                    selectedSymbol = selectedSymbol,
                    onSelectSymbol = { sym ->
                        selectedSymbol = sym
                        pins = CircuitElementFactory.defaultPinsFor(sym, CircuitElementFactory.DEFAULT_LENGTH_PT)
                    },
                )
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Value / Designation (e.g. 10 kΩ, Vcc)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CircuitStrokeWidthSlider(
                    strokeWidth = strokeWidth,
                    onStrokeWidthChange = { strokeWidth = it },
                )
                CircuitRotationRow(
                    rotation = rotation,
                    onRotate = { rotation = (rotation + 90.0) % 360.0 },
                )
                CircuitStrokeColorPicker(
                    selectedColor = strokeColor,
                    onSelectColor = { strokeColor = it },
                )
                CircuitPinsView(
                    pins = pins,
                    onDisconnectPin = { idx ->
                        val newPins = pins.toMutableList()
                        newPins[idx] = newPins[idx].copy(connectedTo = null)
                        pins = newPins
                    },
                )
                Spacer(Modifier.height(4.dp))
                CircuitActionButtons(
                    onDismiss = onDismiss,
                    onSave = {
                        val updated = circuit.copy(
                            symbol = selectedSymbol,
                            value = valueText.ifBlank { null },
                            strokeColor = strokeColor,
                            strokeWidth = strokeWidth,
                            rotation = rotation,
                            pins = pins,
                        )
                        if (updated != circuit) {
                            pane.surface?.replaceCircuitElement(circuit, updated)
                        }
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun CircuitDialogHeader(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Edit Circuit Component",
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CircuitSymbolPicker(
    selectedSymbol: String,
    onSelectSymbol: (String) -> Unit,
) {
    Text("Symbol", style = MaterialTheme.typography.labelMedium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CircuitElementFactory.CIRCUIT_KINDS.forEach { kind ->
            val isSelected = kind.name == selectedSymbol
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clickable {
                        if (selectedSymbol != kind.name) {
                            onSelectSymbol(kind.name)
                        }
                    }
                    .padding(horizontal = 2.dp),
            ) {
                Text(
                    text = kind.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun CircuitStrokeWidthSlider(
    strokeWidth: Double,
    onStrokeWidthChange: (Double) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Width: ${"%.1f".format(strokeWidth)} pt", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(12.dp))
        Slider(
            value = strokeWidth.toFloat(),
            onValueChange = { onStrokeWidthChange(it.toDouble()) },
            valueRange = 0.5f..5.0f,
            steps = 8,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CircuitRotationRow(
    rotation: Double,
    onRotate: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Rotation: ${rotation.roundToInt()}°", style = MaterialTheme.typography.labelMedium)
        IconButton(
            onClick = onRotate,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = "Rotate 90°")
        }
    }
}

@Composable
private fun CircuitStrokeColorPicker(
    selectedColor: Int,
    onSelectColor: (Int) -> Unit,
) {
    Text("Stroke Color", style = MaterialTheme.typography.labelMedium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CIRCUIT_PALETTE_COLORS.forEach { c ->
            val isSelected = c == selectedColor
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .clickable { onSelectColor(c) }
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier,
                    ),
            )
        }
    }
}

@Composable
private fun CircuitPinsView(
    pins: List<CircuitPin>,
    onDisconnectPin: (Int) -> Unit,
) {
    if (pins.isEmpty()) return
    Text("Terminals & Nets", style = MaterialTheme.typography.labelMedium)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        pins.forEachIndexed { idx, pin ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pin: ${pin.id} (${"%.1f".format(pin.relX)}, ${"%.1f".format(pin.relY)})",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (pin.connectedTo != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pin.connectedTo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "✕",
                            modifier = Modifier
                                .clickable { onDisconnectPin(idx) }
                                .padding(2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else {
                    Text(
                        text = "Floating",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun CircuitActionButtons(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(onClick = onDismiss) {
            Text("Cancel")
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onSave) {
            Text("Save")
        }
    }
}

package com.mobixournal.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitLabelSide
import com.mobixournal.render.replaceCircuitElement
import com.mobixournal.ui.theme.FloatingSurface
import com.mobixournal.ui.theme.LocalFloatingSurfaceClosing
import com.mobixournal.ui.theme.liquidGlassSpringAnimation
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Compact anchored inspector for editing a living [CircuitElement].
 *
 * Sits beside the selected component on the canvas, is freely draggable, and allows quick editing of
 * the component's value/designation — typed, or assembled from the circuit symbol keyboard below the
 * field — which side of the symbol that label is written on, and its 90° rotation.
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

    val finalOffset = clampInspectorOffset(baseOffset, dragDelta, canvasSizePx, inspectorSize, marginPx)

    var field by remember(circuit.id ?: circuit.hashCode()) {
        mutableStateOf(TextFieldValue(circuit.value ?: "", TextRange((circuit.value ?: "").length)))
    }
    var rotation by remember(circuit.id ?: circuit.hashCode()) { mutableDoubleStateOf(circuit.rotation) }
    var labelSide by remember(circuit.id ?: circuit.hashCode()) { mutableStateOf(circuit.labelSide) }

    /** The element as the inspector currently reads it — what every control below applies. */
    fun edited() = circuit.copy(
        value = field.text.ifBlank { null },
        labelSide = labelSide,
        rotation = rotation,
    )

    /** Push the inspector's state to the page. A label's side is a placement, so it applies live. */
    fun apply(updated: CircuitElement = edited()) {
        surface?.replaceCircuitElement(circuit, updated)
        ui.editingCircuit = updated
    }

    FloatingSurface(
        modifier = modifier
            .offset { finalOffset }
            .widthIn(min = 240.dp, max = 290.dp)
            .padding(4.dp)
            .liquidGlassSpringAnimation(
                    expanded = !LocalFloatingSurfaceClosing.current,
                    origin = TransformOrigin(0.5f, 0.5f),
                )
            .onSizeChanged { inspectorSize = it },
    ) {
        CircuitInspectorContent(
            circuit = circuit,
            field = field,
            onFieldChange = { field = it },
            labelSide = labelSide,
            onLabelSideChange = { side ->
                labelSide = side
                apply()
            },
            rotation = rotation,
            onRotate = {
                rotation = (rotation + 90.0) % 360.0
                apply()
            },
            onStraighten = {
                val straight = (round(rotation / 90.0) * 90.0) % 360.0
                rotation = if (straight < 0.0) straight + 360.0 else straight
                apply()
            },
            onDismiss = onDismiss,
            onSave = {
                apply()
                onDismiss()
            },
            onDrag = { dragDelta += it },
        )
    }
}

/** The inspector's place: its anchored offset plus wherever the user dragged it, kept in the canvas. */
private fun clampInspectorOffset(
    base: IntOffset,
    drag: Offset,
    canvasSize: IntSize,
    inspectorSize: IntSize,
    marginPx: Int,
): IntOffset = IntOffset(
    x = (base.x + drag.x.roundToInt()).coerceIn(
        marginPx,
        maxOf(marginPx, canvasSize.width - inspectorSize.width - marginPx),
    ),
    y = (base.y + drag.y.roundToInt()).coerceIn(
        marginPx,
        maxOf(marginPx, canvasSize.height - inspectorSize.height - marginPx),
    ),
)

@Composable
private fun CircuitInspectorContent(
    circuit: CircuitElement,
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
    labelSide: CircuitLabelSide,
    onLabelSideChange: (CircuitLabelSide) -> Unit,
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
            value = field,
            onValueChange = onFieldChange,
            label = { Text("Value / Label") },
            placeholder = { Text("e.g. 10 kΩ, Vcc, R1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        CircuitSymbolKeyboard(onInsert = { symbol -> onFieldChange(insertSnippetAtCursor(field, symbol)) })
        CircuitLabelPlacementControl(
            labelSide = labelSide,
            onSelect = onLabelSideChange,
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
                contentDescription = "Drag",
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
                contentDescription = "Close",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Where the value label is written: above, below, left or right of the symbol.
 *
 * The label is laid out around the drawn symbol whichever side is chosen, so this picks a **side**,
 * not a position — above is where a dimension is written, below keeps a crowded wire row free, and
 * left/right serve a component whose leads already own the space above and below. The choice is
 * applied at once, so the user sees the value move while the inspector is still open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CircuitLabelPlacementControl(
    labelSide: CircuitLabelSide,
    onSelect: (CircuitLabelSide) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Label",
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (side in CircuitLabelSide.entries) {
                FilterChip(
                    selected = side == labelSide,
                    onClick = { onSelect(side) },
                    label = { Text(side.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

/** The words the four sides are offered under — the user's own names, not the enum's. */
private val CircuitLabelSide.label: String
    get() = when (this) {
        CircuitLabelSide.ABOVE -> "Above"
        CircuitLabelSide.BELOW -> "Below"
        CircuitLabelSide.LEFT -> "Left"
        CircuitLabelSide.RIGHT -> "Right"
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
            text = "Rotation: ${rotation.roundToInt()}°",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onStraighten, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Straighten,
                    contentDescription = "Straighten (multiple of 90°)",
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onRotate, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.RotateRight,
                    contentDescription = "Rotate 90°",
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
            Text("Cancel")
        }
        Spacer(Modifier.width(6.dp))
        Button(onClick = onSave) {
            Text("Save")
        }
    }
}

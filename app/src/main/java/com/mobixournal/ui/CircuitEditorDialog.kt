package com.mobixournal.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
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
 * Compact anchored inspector for editing a living [CircuitElement], plus its **own keyboard**.
 *
 * Sits beside the selected component on the canvas, is freely draggable, and allows quick editing of
 * the component's value/designation, which side of the symbol that label is written on, and its 90°
 * rotation. The value is typed on [CircuitValueKeyboard] — docked at the foot of the canvas — rather
 * than on the system IME, so the panel that edits the component neither grows a symbol strip nor
 * disappears under a keyboard it did not ask for; while the keyboard is up, the inspector is laid out
 * against the space above it.
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

    var field by remember(circuit.id ?: circuit.hashCode()) {
        mutableStateOf(TextFieldValue(circuit.value ?: "", TextRange((circuit.value ?: "").length)))
    }
    var rotation by remember(circuit.id ?: circuit.hashCode()) { mutableDoubleStateOf(circuit.rotation) }
    var labelSide by remember(circuit.id ?: circuit.hashCode()) { mutableStateOf(circuit.labelSide) }
    var keyboardOpen by remember(circuit.id ?: circuit.hashCode()) { mutableStateOf(false) }
    var keyboardHeight by remember { mutableStateOf(0) }

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

    Box(modifier = modifier.fillMaxSize()) {
        CircuitInspectorSurface(
            circuit = circuit,
            selectionRect = pane.selectionRect,
            canvasSize = IntSize(
                canvasSizePx.width,
                (canvasSizePx.height - if (keyboardOpen) keyboardHeight else 0).coerceAtLeast(0),
            ),
            field = field,
            keyboardOpen = keyboardOpen,
            onOpenKeyboard = { keyboardOpen = true },
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
        )
        if (keyboardOpen) {
            CircuitValueKeyboard(
                field = field,
                onFieldChange = { field = it },
                onDone = { keyboardOpen = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Measured first, so the height reserved for it is the whole keyboard — the insets
                    // and the air around it included — and the panel above clears every key.
                    .onSizeChanged { keyboardHeight = it.height }
                    // A keyboard, not a grid across the whole tablet: the keys keep a thumb's width
                    // instead of stretching to a quarter of the canvas each.
                    .widthIn(max = KEYBOARD_MAX_WIDTH)
                    .fillMaxWidth()
                    // The window is edge-to-edge, so without this the last row (the pages, the arrows,
                    // Done) would sit under the gesture bar and be unreachable.
                    .navigationBarsPadding()
                    .padding(8.dp),
            )
        }
    }
}

/**
 * The inspector itself: a floating panel anchored beside [circuit]'s selection box and kept inside
 * [canvasSize] as the user drags it.
 */
@Composable
private fun CircuitInspectorSurface(
    circuit: CircuitElement,
    selectionRect: android.graphics.RectF?,
    canvasSize: IntSize,
    field: TextFieldValue,
    keyboardOpen: Boolean,
    onOpenKeyboard: () -> Unit,
    labelSide: CircuitLabelSide,
    onLabelSideChange: (CircuitLabelSide) -> Unit,
    rotation: Double,
    onRotate: () -> Unit,
    onStraighten: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var inspectorSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    val initialAnchorRect = remember(circuit.id ?: circuit.hashCode()) { selectionRect }
    val effectiveRect = initialAnchorRect ?: selectionRect

    var dragDelta by remember(circuit.id ?: circuit.hashCode()) { mutableStateOf(Offset.Zero) }

    val baseOffset = plotInspectorOffset(
        selectionLeft = effectiveRect?.left,
        selectionTop = effectiveRect?.top,
        selectionRight = effectiveRect?.right,
        selectionBottom = effectiveRect?.bottom,
        inspectorSize = inspectorSize,
        canvasSize = canvasSize,
        gapPx = gapPx,
        marginPx = marginPx,
    )
    val finalOffset = clampInspectorOffset(baseOffset, dragDelta, canvasSize, inspectorSize, marginPx)

    FloatingSurface(
        modifier = Modifier
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
            keyboardOpen = keyboardOpen,
            onOpenKeyboard = onOpenKeyboard,
            labelSide = labelSide,
            onLabelSideChange = onLabelSideChange,
            rotation = rotation,
            onRotate = onRotate,
            onStraighten = onStraighten,
            onDismiss = onDismiss,
            onSave = onSave,
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
    keyboardOpen: Boolean,
    onOpenKeyboard: () -> Unit,
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
        CircuitValueDisplay(
            value = field.text,
            caretAt = if (keyboardOpen) field.selection.min else null,
            onClick = onOpenKeyboard,
        )
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

/**
 * The value line: a **display**, not a text field.
 *
 * It used to be a read-only `OutlinedTextField`, which was the wrong object for the job: a text field
 * takes the tap for itself (it asks for focus, and the IME answers), so the tap never reached the
 * panel's own handler — the keyboard underneath never opened, and the system keyboard was asked for a
 * panel this inspector does not want. Nothing here is focusable: the whole line is one click target
 * that opens [CircuitValueKeyboard], and the value is painted as text with the caret drawn in place
 * while that keyboard is up, so the arrows still show where the next key lands.
 */
@Composable
private fun CircuitValueDisplay(
    value: String,
    caretAt: Int?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(4.dp)
    Column {
        Text(
            text = "Value / Label",
            style = MaterialTheme.typography.bodySmall,
            color = if (caretAt != null) scheme.primary else scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .border(1.dp, if (caretAt != null) scheme.primary else scheme.outline, shape)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Open the circuit keyboard",
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (value.isEmpty() && caretAt == null) PLACEHOLDER else valueWithCaret(value, caretAt),
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.Keyboard,
                contentDescription = "Open the circuit keyboard",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The placeholder the value line shows while it is empty. */
private const val PLACEHOLDER = "e.g. 10 kΩ, Vcc, R1"

/**
 * How wide the circuit keyboard may get, whatever the screen: past this the keys stop being keys and
 * become a grid of tiles — a 7-column letter row on a 1280 dp canvas would give each key 170 dp.
 */
private val KEYBOARD_MAX_WIDTH = 480.dp

/** The caret a value is edited with — a thin bar between the characters it sits between. */
internal const val VALUE_CARET = "|"

/**
 * [value] with [VALUE_CARET] drawn at [caretAt] (clamped to the text), or [value] unchanged when the
 * keyboard is closed ([caretAt] null). Pure so the drawn line can be checked on the JVM.
 */
internal fun valueWithCaret(value: String, caretAt: Int?): String {
    if (caretAt == null) return value
    val at = caretAt.coerceIn(0, value.length)
    return value.substring(0, at) + VALUE_CARET + value.substring(at)
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

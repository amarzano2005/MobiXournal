package com.mobixournal.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mobixournal.design.PenPalette
import com.mobixournal.format.XoppColor.withAlpha
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.ParametricShapes
import com.mobixournal.render.replaceShapeElement
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.FloatingDialog
import com.mobixournal.ui.theme.rememberClosingArgument
import kotlin.math.roundToInt

/** Translucent fill alpha applied when picking a fill swatch (keeps grid lines readable). */
private const val FILL_ALPHA = 0x40

/**
 * Hosts [ShapeEditorDialog] for whichever figure [EditorUiState.editingShape] names, and applies what
 * it returns.
 */
@Composable
fun ShapeEditorHost(ui: EditorUiState, surface: DrawingSurfaceView?) {
    DialogPresence(ui.editingShape != null) {
        val shape = rememberClosingArgument(ui.editingShape) ?: return@DialogPresence
        ShapeEditorDialog(
            shape = shape,
            onConfirm = { updated ->
                ui.editingShape = null
                surface?.replaceShapeElement(shape, updated)
            },
            onDismiss = { ui.editingShape = null },
        )
    }
}

/**
 * **Edit shape**: parametric editor for a living polygon's side lengths, interior angles, outline,
 * optional fill, and on-page measurement badges.
 */
@Composable
fun ShapeEditorDialog(
    shape: ParametricShapeElement,
    onConfirm: (ParametricShapeElement) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember(shape) { mutableStateOf(shape) }
    var editVersion by remember(shape) { mutableStateOf(0) }
    var activeField by remember(shape) { mutableStateOf<String?>(null) }

    FloatingDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 380.dp),
        title = { Text("Edit shape", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Drag a corner handle on the page to reshape. Enable Page ruling or Snap settings " +
                        "to lock sides to 0, 30, 45 and 90 degrees.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ShapeGeometrySection(
                    shape = working,
                    editVersion = editVersion,
                    activeField = activeField,
                    onFocusField = { activeField = it },
                    onUpdate = { next ->
                        working = next
                        editVersion++
                    },
                )
                HorizontalDivider()
                ShapeStyleSection(
                    shape = working,
                    editVersion = editVersion,
                    activeField = activeField,
                    onFocusField = { activeField = it },
                    onUpdate = { working = it },
                )
                HorizontalDivider()
                SwitchRow(
                    title = "Show side lengths",
                    subtitle = "Label every side with its length.",
                    checked = working.showSideLengths,
                    onCheckedChange = { working = working.copy(showSideLengths = it) },
                )
                SwitchRow(
                    title = "Show angles",
                    subtitle = "Label every corner with its interior angle.",
                    checked = working.showAngles,
                    onCheckedChange = { working = working.copy(showAngles = it) },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = { onConfirm(working) }) { Text("Done") } },
    )
}

@Composable
private fun ShapeGeometrySection(
    shape: ParametricShapeElement,
    editVersion: Int,
    activeField: String?,
    onFocusField: (String?) -> Unit,
    onUpdate: (ParametricShapeElement) -> Unit,
) {
    Text("Sides & angles", style = MaterialTheme.typography.labelLarge)
    for (index in shape.vertices.indices) {
        val sidePt = ParametricShapes.sideLengthPt(shape, index)
        val angleDeg = ParametricShapes.interiorAngleDeg(shape, index)
        val sideKey = "side_$index"
        val angleKey = "angle_$index"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapeNumberInput(
                label = "Side ${index + 1} (pt)",
                value = sidePt,
                syncKey = editVersion,
                isEditing = activeField == sideKey,
                onActivate = { onFocusField(sideKey) },
                onCommit = { length ->
                    if (length > 0.5) onUpdate(ParametricShapes.setSideLength(shape, index, length))
                },
                modifier = Modifier.weight(1f),
            )
            ShapeNumberInput(
                label = "Corner ${index + 1} (°)",
                value = angleDeg,
                syncKey = editVersion,
                isEditing = activeField == angleKey,
                onActivate = { onFocusField(angleKey) },
                onCommit = { deg ->
                    if (deg in 1.0..179.0) onUpdate(ParametricShapes.setInteriorAngle(shape, index, deg))
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ShapeStyleSection(
    shape: ParametricShapeElement,
    editVersion: Int,
    activeField: String?,
    onFocusField: (String?) -> Unit,
    onUpdate: (ParametricShapeElement) -> Unit,
) {
    Text("Outline & fill", style = MaterialTheme.typography.labelLarge)
    ShapeNumberInput(
        label = "Outline width (pt)",
        value = shape.strokeWidth,
        syncKey = editVersion,
        isEditing = activeField == "width",
        onActivate = { onFocusField("width") },
        onCommit = { w ->
            if (w in 0.5..30.0) onUpdate(shape.copy(strokeWidth = w))
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Outline colour", style = MaterialTheme.typography.labelMedium)
    ShapeColorStrip(
        selectedOpaqueRgb = shape.strokeColor and 0x00FFFFFF,
        hasSelection = true,
        onPick = { color -> onUpdate(shape.copy(strokeColor = color)) },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Fill colour", style = MaterialTheme.typography.labelMedium)
        FilterChip(
            selected = shape.fillColor == null,
            onClick = { onUpdate(shape.copy(fillColor = null)) },
            label = { Text("None") },
        )
    }
    ShapeColorStrip(
        selectedOpaqueRgb = (shape.fillColor ?: 0) and 0x00FFFFFF,
        hasSelection = shape.fillColor != null,
        onPick = { color -> onUpdate(shape.copy(fillColor = color.withAlpha(FILL_ALPHA))) },
    )
}

@Composable
private fun ShapeColorStrip(
    selectedOpaqueRgb: Int,
    hasSelection: Boolean,
    onPick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (swatch in PenPalette.COLORS) {
            val selected = hasSelection && ((swatch and 0x00FFFFFF) == selectedOpaqueRgb)
            ColorSwatch(
                color = swatch,
                selected = selected,
                onClick = { onPick(swatch) },
                compact = true,
            )
        }
        Spacer(Modifier.width(2.dp))
    }
}

@Composable
private fun ShapeNumberInput(
    label: String,
    value: Double,
    syncKey: Int,
    isEditing: Boolean,
    onActivate: () -> Unit,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(formatMeasure(value)) }
    LaunchedEffect(syncKey, value, isEditing) {
        if (!isEditing) text = formatMeasure(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            onActivate()
            val cleaned = typed.filter { it.isDigit() || it == '.' }.take(7)
            text = cleaned
            cleaned.toDoubleOrNull()?.let(onCommit)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

private fun formatMeasure(value: Double): String {
    val tenth = (value * 10.0).roundToInt() / 10.0
    return if (tenth == tenth.toLong().toDouble()) tenth.toLong().toString() else tenth.toString()
}

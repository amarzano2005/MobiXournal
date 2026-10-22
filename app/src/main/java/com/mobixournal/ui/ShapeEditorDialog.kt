package com.mobixournal.ui

import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.replaceShapeElement
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.rememberClosingArgument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.ui.theme.FloatingDialog

/**
 * Hosts [ShapeEditorDialog] for whichever figure [EditorUiState.editingShape] names, and applies what
 * it returns.
 *
 * It lives beside the dialog rather than in the editor's overlay list so that adding a living
 * entity's panel costs that list one line — the canvas file is already at its size ceiling.
 */
@Composable
fun ShapeEditorHost(ui: EditorUiState, surface: DrawingSurfaceView?) {
    DialogPresence(ui.editingShape != null) {
        // Held while it closes, so the panel keeps describing the shape it was opened on rather than
        // going blank on the way out.
        val shape = rememberClosingArgument(ui.editingShape) ?: return@DialogPresence
        ShapeEditorDialog(
            shape = shape,
            onConfirm = { sideLengths, angles ->
                ui.editingShape = null
                surface?.replaceShapeElement(
                    shape,
                    shape.copy(showSideLengths = sideLengths, showAngles = angles),
                )
            },
            onDismiss = { ui.editingShape = null },
        )
    }
}

/**
 * **Edit shape**: the two readout badges of a living figure, and a reminder of where its geometry is
 * edited.
 *
 * The panel deliberately carries no numbers for the corners: a shape is deformed on the page, by
 * dragging the handles the selection draws, so that the user sees the figure change under the pen
 * (`docs/drawing-tools.md` → *Living figures*). What is *not* on the page is whether the side lengths
 * and interior angles are labelled — that is what this panel switches, and the labels themselves are
 * drawn by the same compiler the export writes (`ShapeInk`), so what the sheet shows is what the file
 * keeps.
 *
 * @param shape The element the panel was opened on — its flags seed the switches.
 * @param onConfirm Called with the chosen flags when the user accepts.
 * @param onDismiss Called when the panel is dismissed without accepting.
 */
@Composable
fun ShapeEditorDialog(
    shape: ParametricShapeElement,
    onConfirm: (showSideLengths: Boolean, showAngles: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var sideLengths by remember(shape) { mutableStateOf(shape.showSideLengths) }
    var angles by remember(shape) { mutableStateOf(shape.showAngles) }
    FloatingDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 340.dp),
        title = {
            Text("Edit shape", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Drag a corner handle on the page to change the figure. Sides snap to the ruling " +
                        "at 0, 30, 45 and 90 degrees.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    title = "Show side lengths",
                    subtitle = "Label every side with its length.",
                    checked = sideLengths,
                    onCheckedChange = { sideLengths = it },
                )
                SwitchRow(
                    title = "Show angles",
                    subtitle = "Label every corner with its interior angle.",
                    checked = angles,
                    onCheckedChange = { angles = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sideLengths, angles) }) { Text("Done") }
        },
    )
}

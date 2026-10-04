package com.mobixournal.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The pen palette, edited: every swatch the colour pickers offer, addable, editable and deletable.
 *
 * This is the list behind [ColorPaletteState.colors] — the same swatches the toolbar's Colour & size
 * pop-up, the text-box dialog and the selection recolour menu all show, so editing one here edits
 * the app's colours everywhere at once. The factory set is the eight shipping swatches
 * ([PEN_COLORS]), which **Restore default palette** puts back.
 *
 * One colour always survives: an empty palette would leave the pickers with nothing to pick, so the
 * last row's delete is disabled rather than refused after the fact.
 */
@Composable
fun ColorsSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    // Which row's picker is open: the index to redefine, or -1 for a brand-new colour.
    var editing by remember { mutableStateOf(-1) }

    Text("Pen palette", style = MaterialTheme.typography.bodyLarge)
    Text(
        "The swatches every colour picker offers — Colour & size on the toolbar, the text dialog and " +
            "the selection recolour menu. Tap a swatch to redefine it, or remove it; add one with " +
            "the button below. The default set is Black, Red, Green, Blue, Orange, Magenta, Yellow and White.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    settings.penColors.forEachIndexed { i, color ->
        PaletteRow(
            color = color,
            canDelete = settings.penColors.size > 1,
            onEdit = { editing = i },
            onDelete = { deletePenColor(settings, color, onChange) },
        )
    }

    OutlinedButton(
        onClick = { editing = settings.penColors.size },
        enabled = settings.penColors.size < AppSettings.MAX_PEN_COLORS,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Add colour")
    }
    if (settings.penColors != PEN_COLORS) {
        TextButton(
            onClick = { onChange(settings.copy(penColors = PEN_COLORS)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore default palette")
        }
    }

    // Index == penColors.size means "a new colour"; anything inside the list means "this one".
    if (editing in 0..settings.penColors.size) {
        val isNew = editing == settings.penColors.size
        val initial =
            if (isNew) settings.customColor else settings.penColors[editing]
        CustomColorPickerDialog(
            initial = initial,
            onConfirm = { newColor ->
                onChange(
                    if (isNew) {
                        // Appended, de-duplicated: re-adding an existing swatch must not leave two.
                        settings.copy(penColors = (settings.penColors + newColor).distinct())
                    } else {
                        // Replaced in place, then de-duplicated across the list: a palette with the
                        // same colour twice is a palette with one dead swatch.
                        val replaced = settings.penColors.toMutableList()
                        replaced[editing] = newColor
                        settings.copy(penColors = replaced.distinct())
                    },
                )
                editing = -1
            },
            onDismiss = { editing = -1 },
        )
    }
}

/** One palette colour: its swatch, its hex, an edit button and a delete button. */
@Composable
private fun PaletteRow(
    color: Int,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val label = colorDisplayName(color)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorSwatch(color = color, selected = false, onClick = onEdit)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit $label")
        }
        IconButton(onClick = onDelete, enabled = canDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete $label")
        }
    }
}

/**
 * Drop [color] from the palette. The colour's keyboard shortcut goes with it — a key bound to a swatch
 * that no longer exists would otherwise select a colour no picker can show.
 */
private fun deletePenColor(
    settings: AppSettings,
    color: Int,
    onChange: (AppSettings) -> Unit,
) {
    if (settings.penColors.size <= 1) return
    onChange(
        settings.copy(
            penColors = settings.penColors - color,
            colorShortcutKeys = settings.colorShortcutKeys - color,
        ),
    )
}

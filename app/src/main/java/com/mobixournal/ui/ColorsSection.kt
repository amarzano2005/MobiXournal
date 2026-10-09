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
 * The pen palette and favourite colours:
 * - **Pen palette**: the swatches offered by every colour picker (Colour & size on the toolbar, text,
 *   recolour selection).
 * - **Favourite colours**: the three quick-pick colours shown in the rail's Colour & size slot for pen
 *   and highlighter.
 */
@Composable
fun ColorsSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var editing by remember { mutableStateOf(-1) }
    var favoriteEditing by remember { mutableStateOf<Pair<EditorTool, Int>?>(null) }

    SettingsGroup("Favourite colours") {
        SettingsNote(
            "The three colours that sit down the left of the rail's Colour & size slot, one tap away. " +
                "The pen and the highlighter keep their own three; the slot shows the ones belonging to " +
                "the tool in use. Tap a colour to redefine it.",
        )
        FavoritesEditor(settings, onEdit = { tool, index -> favoriteEditing = tool to index })
    }

    SettingsGroup("Pen palette") {
        SettingsNote(
            "The swatches every colour picker offers — Colour & size on the toolbar, the text dialog and " +
                "the selection recolour menu. Tap a swatch to redefine it, or remove it; add one with " +
                "the button below. The default set is Black, Red, Green, Blue, Orange, Magenta, Yellow and White.",
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
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
    }

    favoriteEditing?.let { (tool, index) ->
        val terna = if (tool == EditorTool.HIGHLIGHTER) settings.highlighterFavorites else settings.penFavorites
        CustomColorPickerDialog(
            initial = terna.getOrElse(index) { settings.customColor ?: AppSettings.DEFAULT_CUSTOM_COLOR },
            palette = settings.penColors,
            onConfirm = { newColor ->
                onChange(assignFavorite(settings, tool, index, newColor))
                favoriteEditing = null
            },
            onDismiss = { favoriteEditing = null },
        )
    }

    if (editing in 0..settings.penColors.size) {
        val isNew = editing == settings.penColors.size
        val initial = if (isNew) settings.customColor ?: AppSettings.DEFAULT_CUSTOM_COLOR
        else settings.penColors[editing]
        CustomColorPickerDialog(
            initial = initial,
            palette = settings.penColors,
            onConfirm = { newColor ->
                onChange(
                    if (isNew) {
                        settings.copy(penColors = (settings.penColors + newColor).distinct())
                    } else {
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

@Composable
private fun FavoritesEditor(
    settings: AppSettings,
    onEdit: (EditorTool, Int) -> Unit,
) {
    FavoriteRow("Pen", settings.penFavorites) { index -> onEdit(EditorTool.PEN, index) }
    FavoriteRow("Highlighter", settings.highlighterFavorites) { index ->
        onEdit(EditorTool.HIGHLIGHTER, index)
    }
}

@Composable
private fun FavoriteRow(label: String, colors: List<Int>, onEdit: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(96.dp),
        )
        colors.forEachIndexed { index, color ->
            ColorSwatch(color = color, selected = false, onClick = { onEdit(index) })
            Spacer(Modifier.width(8.dp))
        }
    }
}

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

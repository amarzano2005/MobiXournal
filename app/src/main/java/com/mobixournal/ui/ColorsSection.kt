package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.DragHandle
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.rememberClosingArgument

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
        SettingsNote("Quick-pick colors displayed in the toolbar color capsule. Tap any swatch to customize.")
        FavoritesEditor(settings, onEdit = { tool, index -> favoriteEditing = tool to index })
    }

    SettingsGroup("Pen palette") {
        SettingsNote(
            "Color swatches available across all color pickers. Drag the handle to reorder them; " +
                "tap a swatch to edit or delete.",
        )
        PenPaletteRows(settings, onChange, onEdit = { editing = it })

        OutlinedButton(
            onClick = { editing = settings.penColors.size },
            enabled = settings.penColors.size < AppSettings.MAX_PEN_COLORS,
            modifier = Modifier.settingsActionPill(top = 8.dp),
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

    DialogPresence(favoriteEditing != null) {
        // Held past the flag so the picker still knows which dot it is editing while it closes.
        val (tool, index) = rememberClosingArgument(favoriteEditing) ?: return@DialogPresence
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

    DialogPresence(editing in 0..settings.penColors.size) {
        // Held past the flag, like the favourites' subject: the picker is still composed for its
        // exit after `editing` has gone back to -1, and reading that live indexed the palette at -1.
        val index = rememberClosingArgument(editing.takeIf { it in 0..settings.penColors.size })
            ?: return@DialogPresence
        val isNew = index == settings.penColors.size
        val initial = if (isNew) settings.customColor ?: AppSettings.DEFAULT_CUSTOM_COLOR
        else settings.penColors[index]
        CustomColorPickerDialog(
            initial = initial,
            palette = settings.penColors,
            onConfirm = { newColor ->
                onChange(
                    if (isNew) {
                        settings.copy(penColors = (settings.penColors + newColor).distinct())
                    } else {
                        val replaced = settings.penColors.toMutableList()
                        val previous = replaced[index]
                        replaced[index] = newColor
                        settings.copy(
                            penColors = replaced.distinct(),
                            // The row's shortcut belongs to the slot, not to the old colour: redefine
                            // the swatch and its key follows, instead of pointing at a colour that is
                            // no longer in the palette.
                            colorShortcutKeys = rekeyColorShortcut(settings.colorShortcutKeys, previous, newColor),
                        )
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

/**
 * The pen palette's rows, with the drag that arranges them.
 *
 * It is the page list's drag in miniature ([PageListDrag]): a row is carried by its **handle**, the
 * carried row is lifted over the list on its shadow and follows the finger, and the row it would land
 * on wears the accent along its top edge. One measured row is the pitch, so a drop lands on the row the
 * eye saw the swatch over. Releasing writes the whole palette through [movePaletteColor], and every
 * picker, the pen-tip ring and the colour-shortcut list read the user's new order at once — the
 * palette *is* that list.
 *
 * A cancel clears the drag without writing anything: a gesture taken away from us (a system swipe, the
 * page leaving composition) must never leave a colour somewhere the finger never put it.
 */
@Composable
private fun PenPaletteRows(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onEdit: (Int) -> Unit,
) {
    val colors = settings.penColors
    var dragFrom by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeightPx by remember { mutableStateOf(0) }
    val landing = paletteDropTarget(dragFrom, dragOffset, rowHeightPx, colors.size)

    fun clearDrag() {
        dragFrom = -1
        dragOffset = 0f
    }

    fun dropDrag() {
        val to = paletteDropTarget(dragFrom, dragOffset, rowHeightPx, colors.size)
        if (dragFrom in colors.indices && to != dragFrom) {
            onChange(settings.copy(penColors = movePaletteColor(colors, dragFrom, to)))
        }
        clearDrag()
    }

    colors.forEachIndexed { index, color ->
        PaletteRow(
            color = color,
            canDelete = colors.size > 1,
            dragging = index == dragFrom,
            landing = dragFrom >= 0 && landing == index && landing != dragFrom,
            dragOffset = if (index == dragFrom) dragOffset else 0f,
            onHeight = { height -> if (rowHeightPx == 0) rowHeightPx = height },
            onDragStart = { dragFrom = index; dragOffset = 0f },
            onDrag = { dragOffset += it },
            onDragEnd = { dropDrag() },
            onDragCancel = { clearDrag() },
            onEdit = { onEdit(index) },
            onDelete = { deletePenColor(settings, color, onChange) },
        )
    }
}

@Composable
private fun PaletteRow(
    color: Int,
    canDelete: Boolean,
    dragging: Boolean,
    landing: Boolean,
    dragOffset: Float,
    onHeight: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val label = colorDisplayName(color)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset
                shadowElevation = if (dragging) PALETTE_DRAG_LIFT.toPx() else 0f
            }
            .background(
                if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
            )
            // The row the swatch would land on wears the accent along its **top edge**, as in the page
            // list: one line in the chrome's accent, so where the colour will go is readable in flight.
            .drawBehind {
                if (landing) drawRect(accent, size = Size(size.width, PALETTE_LANDING_LINE.toPx()))
            }
            .onSizeChanged { onHeight(it.height) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaletteDragHandle(
            color = color,
            label = label,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
        )
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

/** How far a carried swatch sits off the rows, matching the page list's ghost. */
private val PALETTE_DRAG_LIFT = 12.dp

/** The landing line's thickness: the accent that says where a dragged swatch will go. */
private val PALETTE_LANDING_LINE = 2.dp

/**
 * The grabbable glyph at a palette row's start, and the only place a reorder starts.
 *
 * A dedicated target rather than a drag on the row itself: the swatch is a tap (it opens the editor), and
 * a row that also started a drag on any press could not be tapped without guessing. The gesture is read
 * here, on the row that owns it; the list's arithmetic does not depend on where the finger is.
 */
@Composable
private fun PaletteDragHandle(
    color: Int,
    label: String,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val start = rememberUpdatedState(onDragStart)
    val move = rememberUpdatedState(onDrag)
    val end = rememberUpdatedState(onDragEnd)
    val cancel = rememberUpdatedState(onDragCancel)
    Box(
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = "Drag to reorder $label" }
            .pointerInput(color) {
                detectDragGestures(
                    onDragStart = { start.value() },
                    onDrag = { change, amount ->
                        change.consume()
                        move.value(amount.y)
                    },
                    onDragEnd = { end.value() },
                    onDragCancel = { cancel.value() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
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

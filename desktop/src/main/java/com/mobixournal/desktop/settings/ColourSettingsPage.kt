package com.mobixournal.desktop.settings
import com.mobixournal.desktop.tools.ColourPicker
import com.mobixournal.desktop.tools.ColourPickerPanel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobixournal.design.PenPalette

/**
 * **Colors**, as a page: the palette itself — the list every picker in the window draws — editable the way
 * the tablet's *Settings → Drawing → Colors* edits it. **Edit** redefines a swatch in its own place,
 * **Delete** removes one, **Add colour** appends one, and **Restore default palette** puts the factory
 * eight back.
 *
 * It used to say only that the colour lived in the strip, which was true of *where to pick it* and false
 * of *where to change it*: the phone has had an editable palette for as long as it has had a palette, and
 * a window that could only pick from it was the one place a reader could not define their own. The rules
 * are [PenPalette]'s — opaque, de-duplicated, capped at [PenPalette.MAX_COLORS], never empty — so what
 * this page writes is exactly what the tablet would write, and **Restore** writes the same eight hexes in
 * the same order.
 *
 * A row keeps the page's grammar: the swatch's name on the left, the row itself picking it, and the two
 * words that act on the entry at its end. The picker opens **in the page**, under the row it edits, rather
 * than as a floating panel: the settings surface already covers the sheet, and a second surface over it
 * would be a question about which one a key belongs to.
 *
 * The order is the reader's too, exactly as the toolbar's slots are: each row's **handle** carries it
 * through the list, the rows between glide open on a short tween, and a drop writes the whole palette — so
 * every picker, the pen-tip ring and the colour-shortcut rows follow the arrangement at once.
 */
@Composable
internal fun ColorsSettings(palette: PaletteActions) {
    val colours = palette.palette()
    // Which entry the picker is editing: an index, or `colours.size` for a colour not yet in the list.
    var editing by remember { mutableStateOf<Int?>(null) }
    val drag = remember { ReorderState() }
    SettingsGroup("Pen palette") {
        colours.forEachIndexed { index, colour ->
            ReorderRow(drag, index, colours.size) {
                PaletteRow(
                    colour = colour,
                    canDelete = colours.size > 1,
                    drag = drag,
                    index = index,
                    count = colours.size,
                    onMove = palette.move,
                    onPick = { palette.pick(colour) },
                    onEdit = { editing = index },
                    onDelete = {
                        palette.remove(index)
                        editing = null
                    },
                )
            }
            // The panel opens **under the row it edits**. Appended to the foot of the group it opened
            // below every other swatch, so a reader who clicked Edit on Black went looking for it past
            // White — and on a short page its Set button was under the panel's own edge.
            if (editing == index) {
                InlinePicker(palette, colours, index) { editing = null }
            }
        }
        SettingsCommand(
            label = "Add colour",
            note = "One more swatch in every picker, up to ${PenPalette.MAX_COLORS}.",
            onClick = { editing = colours.size },
        )
        if (colours != PenPalette.COLORS) {
            SettingsCommand(
                label = "Restore default palette",
                note = "The tablet's factory eight, in its published order.",
                onClick = {
                    palette.restore()
                    editing = null
                },
            )
        }
        if (editing == colours.size) {
            InlinePicker(palette, colours, colours.size) { editing = null }
        }
    }
    SettingsNote(
        "Every picker draws this list: the stroke panel's swatches, the pen-tip ring and the text dialog.",
    )
}

/**
 * The colour panel, opened **inside the page under the row it edits** — and scrolled into view.
 *
 * It is not a second surface over the settings surface: the page already floats over the sheet, and two
 * layers would be a question about which one a key belongs to. What the page has to do instead is make
 * room for it, which is the [BringIntoViewRequester]'s job: the reader clicked a row near the bottom of a
 * scrolling column, and a panel that opened below the fold would be the same bug in another place.
 *
 * [target] is `colours.size` for a colour not yet in the list — the add command's own case.
 */
// `BringIntoViewRequester` is still the foundation's experimental surface in this Compose line.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InlinePicker(palette: PaletteActions, colours: List<Int>, target: Int, onClose: () -> Unit) {
    val isNew = target == colours.size
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(target) { requester.bringIntoView() }
    ColourPickerPanel(
        title = if (isNew) "New colour" else "Edit ${paletteColourName(colours[target])}",
        initial = if (isNew) colours.lastOrNull() ?: PenPalette.BLACK else colours[target],
        onSet = { colour ->
            if (isNew) palette.add(colour) else palette.define(target, colour)
            onClose()
        },
        onCancel = onClose,
        modifier = Modifier
            .bringIntoViewRequester(requester)
            .padding(start = ROW_INSET, top = 6.dp, bottom = 6.dp),
    )
}

/** One swatch, as a row: its drag handle, its name, the row picking it, and the two words that act on it. */
@Composable
private fun PaletteRow(
    colour: Int,
    canDelete: Boolean,
    drag: ReorderState,
    index: Int,
    count: Int,
    onMove: (from: Int, to: Int) -> Unit,
    onPick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SettingsRow(
        label = paletteColourName(colour),
        note = "Drag the handle to move it; a click picks it, Edit redefines the swatch in its place.",
        onClick = onPick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DragGrip(
                label = paletteColourName(colour),
                onDragStart = { drag.start(index) },
                onDrag = drag::by,
                onDragEnd = { drag.drop(count)?.let { to -> onMove(index, to) } },
                onDragCancel = drag::clear,
                onStep = { direction -> onMove(index, index + direction) },
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(18.dp)
                    .background(Color(colour), DesktopTheme.capsuleShape)
                    .border(1.dp, DesktopTheme.inkMuted, DesktopTheme.capsuleShape),
            )
            Spacer(Modifier.width(10.dp))
            SettingsTextAction("Edit", onClick = onEdit)
            Spacer(Modifier.width(4.dp))
            // The last swatch keeps its × drawn and out of reach: a picker with nothing in it cannot pick.
            SettingsTextAction(
                text = "Delete",
                enabled = canDelete,
                tone = DesktopTheme.danger,
                onClick = onDelete,
            )
        }
    }
}

/** The word a swatch goes by: the factory eight by name, anything the reader added by its `#RRGGBB`. */
internal fun paletteColourName(colour: Int): String = when (PenPalette.opaque(colour)) {
    PenPalette.BLACK -> "Black"
    PenPalette.RED -> "Red"
    PenPalette.GREEN -> "Green"
    PenPalette.BLUE -> "Blue"
    PenPalette.ORANGE -> "Orange"
    PenPalette.YELLOW -> "Yellow"
    PenPalette.MAGENTA -> "Magenta"
    PenPalette.WHITE -> "White"
    else -> ColourPicker.hex(colour)
}

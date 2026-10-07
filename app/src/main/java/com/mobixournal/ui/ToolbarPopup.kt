package com.mobixournal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The touch-target square every rail slot occupies, tool buttons and popup buttons alike. Icons are
 * 24.dp, so the extra room is what keeps the rail from reading as a squished run of glyphs.
 */
internal val ToolbarButtonSize = 48.dp

/**
 * A tiny chevron in the bottom-right corner of a **tool-group slot** whose picker offers a choice —
 * a tap-again opens a menu over several tools. The *panel* pop-ups (Colour, Zoom, Layers, …) do
 * **not** wear it: they are a button that opens its own panel, not a submenu of alternatives, so the
 * chevron is reserved for "this slot can be re-faced".
 *
 * Call it inside the button's own [Box] (it aligns itself to the corner).
 */
@Composable
internal fun BoxScope.MenuChevron(tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 6.dp, bottom = 4.dp)
            .size(12.dp),
    )
}

/**
 * A small non-clickable section heading inside a dropdown menu.
 */
@Composable
internal fun MenuHeading(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Cell width when a row holds three tools, and when it holds only one or two. */
private val ToolPickerCellWidth = 76.dp
private val ToolPickerWideCellWidth = 104.dp

/**
 * The body of a **tool group's picker**: the group's members as rows of icon-and-label buttons, with
 * the members that belong together sharing a row ([pickerRows]) — the diode family across one row,
 * the two transistors side by side on the next — instead of every member taking a full-width row. The
 * slot's live tool is tinted in the primary container colour, the same "this one is on" language as
 * the rail, and each button is a radio item, so picking one face of the slot reads as one setting
 * with several choices.
 *
 * Shared by the rail's slots ([ToolGroupButton] in SideToolbar.kt) and the modern top bar's compact
 * ones (`CompactTopBarToolButton` in EditorRegions.kt), so the two pickers cannot drift apart.
 *
 * @param members the tools to offer, in order — the group's own list, or the Shapes slot's
 *   user-ordered one.
 */
@Composable
internal fun ToolGroupPicker(
    group: ToolGroup,
    members: List<EditorTool>,
    selected: EditorTool,
    onPick: (EditorTool) -> Unit,
) {
    MenuHeading(group.label)
    val rows = pickerRows(group.pickerLayout, members)
    // A group that pairs its members up has room for the longer names ("DC voltage source") on one
    // line; the three-to-a-row grid is narrower and lets those wrap onto two.
    val cellWidth = if (rows.any { it.size > 2 }) ToolPickerCellWidth else ToolPickerWideCellWidth
    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        for (row in rows) {
            Row(modifier = Modifier.selectableGroup()) {
                for (tool in row) {
                    ToolPickerCell(
                        tool = tool,
                        selected = tool == selected,
                        width = cellWidth,
                        onClick = { onPick(tool) },
                    )
                }
            }
        }
    }
}

/**
 * One button in a picker row: the tool's glyph over its name, tinted while it is the slot's live
 * tool. The name wraps to two lines so a narrow cell stays readable and the longest labels ellipsise
 * rather than pushing their neighbours out of the menu.
 */
@Composable
private fun ToolPickerCell(tool: EditorTool, selected: Boolean, width: Dp, onClick: () -> Unit) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .width(width)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tool.icon, contentDescription = null, tint = tint)
        Text(
            text = tool.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * A standard toolbar popup button with an icon face. A click toggles its [DropdownMenu]: the first
 * click opens it, and clicking again closes it (an open menu also dismisses on any outside touch).
 *
 * @param icon The icon to show on the button face.
 * @param contentDescription Content description for the icon.
 * @param heading Optional heading text shown at the top of the menu.
 * @param active Whether the button is in an active state (tints the icon primary and shows background).
 * @param tint Optional explicit tint for the icon; defaults to [LocalContentColor.current].
 * @param onLongClick Optional long-click handler.
 * @param content The menu content, called with a `dismiss` lambda to close the menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ToolbarPopupButton(
    icon: ImageVector,
    contentDescription: String,
    heading: String? = null,
    active: Boolean = false,
    tint: androidx.compose.ui.graphics.Color? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val iconTint = tint ?: if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current
    Box {
        Box(
            modifier = Modifier
                .size(ToolbarButtonSize)
                .clip(CircleShape)
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                .combinedClickable(
                    onClick = { open = !open },
                    onLongClick = onLongClick,
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = iconTint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (heading != null) MenuHeading(heading)
            content { open = false }
        }
    }
}

/**
 * A toolbar popup button with a custom composable face (e.g. TextButton for Zoom, TipDot for Size).
 *
 * @param face The custom composable to use as the button face, called with a `toggle` lambda it must
 *   wire to its own click handler — that lambda is what opens or closes the menu.
 * @param heading Optional heading text shown at the top of the menu.
 * @param content The menu content, called with a `dismiss` lambda to close the menu.
 */
@Composable
internal fun ToolbarPopupButton(
    face: @Composable (open: () -> Unit) -> Unit,
    heading: String? = null,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        face { open = !open }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (heading != null) MenuHeading(heading)
            content { open = false }
        }
    }
}

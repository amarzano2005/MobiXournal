package com.mobixournal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
 *
 * This is only the fallback for a slot drawn outside the rail; inside it, a slot reads the size it is
 * actually drawn at from [LocalRailSlotSize], which the rail sets from its adaptive pitch
 * ([railContentScale]). A slot that hard-codes [ToolbarButtonSize] would keep the rail overflowing.
 */
internal val ToolbarButtonSize = 48.dp

/**
 * The height/width one rail slot is drawn at **here**, as decided by the rail's adaptive pitch: the
 * rail's own 44dp when every visible slot fits, a few percent larger when that is what swallows the
 * leftover strip (see [railContentScale]).
 *
 * It is a composition local rather than a parameter so that every slot — tool buttons, pop-up
 * buttons, the one-tap slots — shrinks together without each of them growing a size argument.
 */
internal val LocalRailSlotSize = compositionLocalOf { ToolbarButtonSize }

/**
 * The rail's current scale (1 at full size), for the few slots whose **content** is not a dp size —
 * the zoom slot's percentage label, which has to be typeset smaller as its button shrinks.
 */
internal val LocalRailSlotScale = compositionLocalOf { 1f }

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

/**
 * A one-line explanation under a pop-up heading, for a section that has nothing to offer on this
 * page — shorter and quieter than a disabled menu item, which would otherwise claim a full row of the
 * menu to say only that.
 */
@Composable
internal fun MenuHint(text: String) {
    Text(
        text,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .widthIn(max = 260.dp),
        style = MaterialTheme.typography.bodySmall,
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
 * The body of a **variant picker**: a figure tool that ships in several geometric kinds (the triangle, the trapezoid)
 * offers those kinds as rows of compact label buttons ([variantRows]) rather than one full-width menu row each, so its
 * submenu reads the same as the tool pickers ([ToolGroupPicker]). The live kind is tinted in the primary container
 * colour and each button is a radio item — one setting with several choices, not a list of commands.
 *
 * The one kind that carries something to configure (the scalene kind's angles) wears a small pencil; tapping the pencil
 * picks that kind **and** opens its dialog in one gesture, while tapping the rest of the cell just picks it.
 *
 * Shared by both figures rather than copied, so the two submenus cannot drift apart.
 *
 * @param labels the kinds in menu order.
 * @param selectedIndex the live kind, or an out-of-range index when none is picked.
 * @param editableIndex the kind that opens a dialog, or null when no kind does.
 */
@Composable
internal fun ToolVariantPicker(
    heading: String,
    labels: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    editableIndex: Int? = null,
    editHint: String = "",
    onEdit: (() -> Unit)? = null,
) {
    MenuHeading(heading)
    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        for (row in variantRows(labels.size)) {
            Row(modifier = Modifier.selectableGroup()) {
                for (index in row) {
                    VariantCell(
                        label = labels[index],
                        selected = index == selectedIndex,
                        width = ToolPickerWideCellWidth,
                        onPick = { onPick(index) },
                        editHint = editHint,
                        onEdit = if (index == editableIndex) onEdit else null,
                    )
                }
            }
        }
    }
}

/**
 * One button in a variant picker row: the kind's name, tinted while it is the live one, with the pencil affordance in
 * its top corner when this is the kind that has a dialog behind it. The name wraps to two lines so the longest kind
 * stays readable in a narrow cell.
 */
@Composable
private fun VariantCell(
    label: String,
    selected: Boolean,
    width: Dp,
    onPick: () -> Unit,
    editHint: String,
    onEdit: (() -> Unit)?,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .width(width)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onPick)
            .heightIn(min = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                start = 6.dp,
                end = if (onEdit != null) 22.dp else 6.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        )
        if (onEdit != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = editHint,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
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
        val slotSize = LocalRailSlotSize.current
        Box(
            modifier = Modifier
                .size(slotSize)
                .clip(CircleShape)
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                .combinedClickable(
                    onClick = { open = !open },
                    onLongClick = onLongClick,
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(slotSize / 2),
            )
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

package com.mobixournal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.mobixournal.ui.theme.LocalLiquidGlassAnimations
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.liquidGlassSpringAnimation
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor
import com.mobixournal.ui.theme.rememberGlassSurfaceColor

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
internal fun BoxScope.MenuChevron(tint: Color = Color.White) {
    Icon(
        Icons.Outlined.KeyboardArrowDown,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 6.dp, bottom = 4.dp)
            .size(12.dp),
    )
}

/**
 * The toolbar's own [DropdownMenu] — every pop-up the toolbars open is cut from the **same material
 * as the bars themselves**: [rememberFloatingSurfaceColor], the one fill every floating surface in the
 * app wears (the bars' glass with the see-through turned down), and their own [TOP_BAR_DOCK_CORNER]
 * for its corners,
 * so a menu reads as part of the bar it hangs from instead of a paler, squarer panel floating over it.
 *
 * That fill used to be [rememberToolbarColor] (`surfaceContainer`) while the bars painted the glass,
 * which is what left every light-theme pop-up the *baseline lavender* `#F3EDF7` — a violet-tinted
 * panel beside a near-white bar, and the one place a colour outside the black/grey/blue palette could
 * still reach the screen. One function for both is what makes "the menu is the bar's material" a
 * property of the code instead of a claim in a comment.
 *
 * Neither the fill nor the corner is a parameter this Material3 version exposes — both overloads of
 * [DropdownMenu] in 1.2.1 stop at the offset, the scroll state and `PopupProperties` — so this takes
 * the two levers the ambient [MaterialTheme] does leave, and only inside this scope:
 *
 * - `surface` is what `MenuTokens.ContainerColor` resolves to, sitting deliberately one step **paler**
 *   than the rail and the dock. `surfaceTint` is the second half of that: a menu's container carries
 *   `MenuTokens.ContainerElevation` (3dp), painted as an ~8% wash of `surfaceTint` over the fill, so
 *   setting the fill alone still left the pop-up a tint step off the toolbar. Pinning the tint to the
 *   same colour makes the wash a no-op and the fill exact.
 * - `MenuTokens.ContainerShape` resolves to `Shapes.extraSmall` — M3's 4dp menu corner, far squarer
 *   than the 20dp dock the menu hangs under. Re-shaping that one role rounds the menu to the chrome's
 *   own corner; nothing outside this menu sees the override.
 *
 * Routing every toolbar menu through here is what keeps the whole chrome on one colour and one
 * corner: a stray raw [DropdownMenu] brings the paler, squarer panel back (see
 * `ToolbarMenuGuardTest`, which fails the build on one).
 *
 * On top of that material the menu carries an **edge of its own**, because the fill that ties a
 * pop-up to its bar is also what lets it dissolve into the surface it hangs over: the toolbars sit
 * in the same grey as the canvas desk, and a menu opened from the bar lands on that same grey with
 * nothing between them. The cue is a hairline drawn around the panel in [rememberMenuRimColor] — the
 * ambient surface's own ink, so it is an edge in the light theme and in the dark one — painted
 * *after* the panel so it reads as the panel's own boundary rather than as something behind it, and
 * shaded along its length (bright at the top edge, faint at the bottom) so the panel reads as lit
 * from above instead of as an outlined box.
 *
 * A raised shadow is deliberately **not** the cue here. The pop-up's window is only as large as the
 * panel, so a shadow has nowhere to fall: built and measured on the emulator, an elevated menu
 * (14dp, both `clip` settings) painted a grey wash *inside* the panel's top 50px — the blur has no
 * room outside it — and clipped away entirely at the sides; a rim reads the same on all four edges.
 */
@Composable
internal fun ToolbarMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val useLiquid = LocalLiquidGlassAnimations.current
    if (useLiquid) {
        if (expanded) {
            var transformOrigin by remember { mutableStateOf(TransformOrigin(0f, 0f)) }
            val density = LocalDensity.current
            val positionProvider = remember(density) {
                ToolbarPopupPositionProvider(density = density) { origin ->
                    transformOrigin = origin
                }
            }
            Popup(
                onDismissRequest = onDismissRequest,
                popupPositionProvider = positionProvider,
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = RoundedCornerShape(TOP_BAR_DOCK_CORNER),
                    color = rememberFloatingSurfaceColor(),
                    shadowElevation = 8.dp,
                    modifier = modifier
                        .floatingRim()
                        .liquidGlassSpringAnimation(expanded = true, origin = transformOrigin),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .width(IntrinsicSize.Max)
                            .verticalScroll(rememberScrollState()),
                        content = content,
                    )
                }
            }
        }
    } else {
        val toolbar = rememberFloatingSurfaceColor()
        val scheme = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        MaterialTheme(
            colorScheme = scheme.copy(
                surface = toolbar,
                surfaceTint = Color.Transparent,
                surfaceContainer = toolbar,
                surfaceContainerHigh = toolbar,
                surfaceContainerHighest = toolbar,
            ),
            shapes = shapes.copy(extraSmall = RoundedCornerShape(TOP_BAR_DOCK_CORNER)),
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismissRequest,
                modifier = modifier.floatingRim(),
                content = content,
            )
        }
    }
}

internal class ToolbarPopupPositionProvider(
    private val density: Density,
    private val onPositionCalculated: (TransformOrigin) -> Unit,
) : PopupPositionProvider {
    private val verticalMargin = with(density) { 48.dp.roundToPx() }

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val startToAnchorStart = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val endToAnchorEnd = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }
        val windowBoundaryX = if (anchorBounds.center.x < windowSize.width / 2) {
            0
        } else {
            windowSize.width - popupContentSize.width
        }

        val xCandidates = listOf(startToAnchorStart, endToAnchorEnd, windowBoundaryX)
        val x = xCandidates.firstOrNull { candidate ->
            candidate in 0..(windowSize.width - popupContentSize.width)
        } ?: windowBoundaryX

        val topToAnchorBottom = anchorBounds.bottom
        val bottomToAnchorTop = anchorBounds.top - popupContentSize.height
        val centerToAnchorTop = anchorBounds.top - popupContentSize.height / 2
        val windowBoundaryY = if (anchorBounds.center.y < windowSize.height / 2) {
            verticalMargin
        } else {
            windowSize.height - verticalMargin - popupContentSize.height
        }

        val yCandidates = listOf(topToAnchorBottom, bottomToAnchorTop, centerToAnchorTop, windowBoundaryY)
        val y = yCandidates.firstOrNull { candidate ->
            candidate in verticalMargin..(windowSize.height - verticalMargin - popupContentSize.height)
        } ?: yCandidates.firstOrNull { candidate ->
            candidate in 0..(windowSize.height - popupContentSize.height)
        } ?: windowBoundaryY

        val finalX = x.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val finalY = y.coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height))
        val menuOffset = IntOffset(finalX, finalY)
        val menuBounds = IntRect(offset = menuOffset, size = popupContentSize)

        val pivotX = when {
            menuBounds.left >= anchorBounds.right -> 0f
            menuBounds.right <= anchorBounds.left -> 1f
            menuBounds.width == 0 -> 0f
            else -> {
                val intersectionCenter = (maxOf(anchorBounds.left, menuBounds.left) + minOf(anchorBounds.right, menuBounds.right)) / 2
                ((intersectionCenter - menuBounds.left).toFloat() / menuBounds.width).coerceIn(0f, 1f)
            }
        }
        val pivotY = when {
            menuBounds.top >= anchorBounds.bottom -> 0f
            menuBounds.bottom <= anchorBounds.top -> 1f
            menuBounds.height == 0 -> 0f
            else -> {
                val intersectionCenter = (maxOf(anchorBounds.top, menuBounds.top) + minOf(anchorBounds.bottom, menuBounds.bottom)) / 2
                ((intersectionCenter - menuBounds.top).toFloat() / menuBounds.height).coerceIn(0f, 1f)
            }
        }

        onPositionCalculated(TransformOrigin(pivotX, pivotY))
        return menuOffset
    }
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
 * A variant cell is a little wider than a plain picker cell: it carries an outline glyph beside
 * the kind's name, and "Right-angled" should not break across lines to pay for it.
 */
private val ToolVariantCellWidth = 124.dp

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
 * @param icons one outline per kind, drawn beside its name so the choice reads as a shape and not
 *   only as a word; empty keeps the label-only layout for pickers that have no shape to show.
 * @param editableIndex the kind that opens a dialog, or null when no kind does.
 */
@Composable
internal fun ToolVariantPicker(
    heading: String,
    labels: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    icons: List<ImageVector> = emptyList(),
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
                        icon = icons.getOrNull(index),
                        selected = index == selectedIndex,
                        width = ToolVariantCellWidth,
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
    icon: ImageVector?,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = 6.dp,
                end = if (onEdit != null) 22.dp else 6.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        ) {
            if (icon != null) {
                // 18dp, not the 16 the label would take: these glyphs are the whole point of the
                // row — two kinds that differ only in a leg or an apex have to read at a glance.
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
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
                    Icons.Outlined.Edit,
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
    val iconTint = tint ?: if (active) MaterialTheme.colorScheme.primary else Color.White
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
        ToolbarMenu(expanded = open, onDismissRequest = { open = false }) {
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
        ToolbarMenu(expanded = open, onDismissRequest = { open = false }) {
            if (heading != null) MenuHeading(heading)
            content { open = false }
        }
    }
}

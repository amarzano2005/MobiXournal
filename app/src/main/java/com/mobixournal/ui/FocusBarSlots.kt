package com.mobixournal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberPopupPanelColor

/** Renders a single slot inside the Focus mode floating Liquid Glass bar. */
@Composable
internal fun FocusBarSlotItem(
    kind: FocusBarSlotKind,
    vertical: Boolean,
    ui: EditorUiState,
    pane: PaneState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    style: ToolbarStyleCallbacks,
    favorites: ToolbarFavoritesCallbacks,
    layerCallbacks: ToolbarLayerCallbacks,
    pageCallbacks: ToolbarPagesCallbacks,
) {
    val surface = pane.surface
    val singleTool = kind.singleEditorTool()
    if (singleTool != null) {
        FocusSingleToolSlot(
            tool = singleTool,
            active = ui.tool == singleTool,
            onClick = { surface?.activateTool(singleTool, ui, settings, onSettingsChange) },
        )
        return
    }
    if (kind.isToolGroupSlot()) {
        FocusToolGroupSlot(
            groupId = kind.id,
            ui = ui,
            surface = surface,
            settings = settings,
            onSettingsChange = onSettingsChange,
        )
        return
    }
    when (kind) {
        FocusBarSlotKind.PEN -> FocusStrokeToolSlot(
            tool = EditorTool.PEN,
            heading = "Pen stroke",
            showLineStyle = true,
            ui = ui,
            surface = surface,
            settings = settings,
            onSettingsChange = onSettingsChange,
            style = style,
        )
        FocusBarSlotKind.HIGHLIGHTER -> FocusStrokeToolSlot(
            tool = EditorTool.HIGHLIGHTER,
            heading = "Highlighter stroke",
            showLineStyle = false,
            ui = ui,
            surface = surface,
            settings = settings,
            onSettingsChange = onSettingsChange,
            style = style,
        )
        FocusBarSlotKind.COLORS -> FocusQuickColorsSlot(
            tool = ui.tool,
            vertical = vertical,
            style = style,
            favorites = favorites,
        )
        FocusBarSlotKind.UNDO -> FocusActionSlot(
            icon = ChromeGlyph.UNDO.imageVector(),
            label = "Undo",
            enabled = pane.canUndo,
            onClick = { surface?.undo() },
        )
        FocusBarSlotKind.REDO -> FocusActionSlot(
            icon = ChromeGlyph.REDO.imageVector(),
            label = "Redo",
            enabled = pane.canRedo,
            onClick = { surface?.redo() },
        )
        FocusBarSlotKind.PAGES -> PagesPopupButton(pageCallbacks)
        FocusBarSlotKind.LAYERS -> LayersPopupButton(layerCallbacks)
        FocusBarSlotKind.GUIDES -> CompactGuidePopupButton(
            kind = settings.guideKind,
            onKind = {
                onSettingsChange(settings.copy(guideKind = it, guidePose = null))
                surface?.placeGuide(it)
            },
        )
        else -> Unit
    }
}

private fun FocusBarSlotKind.singleEditorTool(): EditorTool? = when (this) {
    FocusBarSlotKind.HAND -> EditorTool.HAND
    FocusBarSlotKind.IMAGE -> EditorTool.IMAGE
    FocusBarSlotKind.VSPACE -> EditorTool.VERTICAL_SPACE
    FocusBarSlotKind.LINE -> EditorTool.LINE
    FocusBarSlotKind.RECTANGLE -> EditorTool.RECTANGLE
    FocusBarSlotKind.ELLIPSE -> EditorTool.ELLIPSE
    FocusBarSlotKind.TRIANGLE -> EditorTool.TRIANGLE
    FocusBarSlotKind.TRAPEZOID -> EditorTool.TRAPEZOID
    FocusBarSlotKind.POLYGON -> EditorTool.POLYGON
    FocusBarSlotKind.AXIS -> EditorTool.COORDINATE_AXIS
    FocusBarSlotKind.GRAPH -> EditorTool.PLOT
    FocusBarSlotKind.TABLE -> EditorTool.TABLE
    else -> null
}

private fun FocusBarSlotKind.isToolGroupSlot(): Boolean = when (this) {
    FocusBarSlotKind.ERASER,
    FocusBarSlotKind.SELECT,
    FocusBarSlotKind.TEXT,
    FocusBarSlotKind.ARROW,
    FocusBarSlotKind.CIRCUIT,
    FocusBarSlotKind.CIRCUIT_ACTIVE,
    FocusBarSlotKind.LOGIC,
    -> true
    else -> false
}

/**
 * Pen or Highlighter slot: first tap activates the tool; second tap while already active (or
 * long-press) opens the Liquid Glass stroke customization menu (tip widths, line style, quick colours).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusStrokeToolSlot(
    tool: EditorTool,
    heading: String,
    showLineStyle: Boolean,
    ui: EditorUiState,
    surface: DrawingSurfaceView?,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    style: ToolbarStyleCallbacks,
) {
    val active = ui.tool == tool
    var menuOpen by remember { mutableStateOf(false) }
    var editingSlot by remember { mutableStateOf(-1) }
    var editingCustom by remember { mutableStateOf(false) }
    var addingColor by remember { mutableStateOf(false) }
    val slotSize = LocalRailSlotSize.current
    val tint = if (active) MaterialTheme.colorScheme.primary else rememberChromeInk()
    Box {
        Box(
            modifier = Modifier
                .size(slotSize)
                .clip(ModernTheme.railSlotShape)
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                .combinedClickable(
                    onClick = {
                        if (active) menuOpen = !menuOpen
                        else surface?.activateTool(tool, ui, settings, onSettingsChange)
                    },
                    onLongClick = {
                        if (!active) surface?.activateTool(tool, ui, settings, onSettingsChange)
                        menuOpen = true
                    },
                )
                .semantics { contentDescription = "Tool: ${tool.label}" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(tool.icon, contentDescription = null, tint = tint, modifier = Modifier.size(slotSize / 2))
        }
        MenuChevron()
        ToolbarMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, fill = rememberPopupPanelColor()) {
            MenuHeading(heading)
            ColorPaletteRows(
                selected = style.color,
                palette = style.palette,
                onPick = { c -> style.onColor(c) },
                onEditCustom = { editingCustom = true; menuOpen = false },
                onAdd = { addingColor = true; menuOpen = false },
                compact = true,
            )
            MenuHeading("Size")
            WidthSlotBar(
                width = style.width,
                widthSlots = style.widthSlots,
                onWidth = { pt -> style.onWidth(pt) },
                onEditSlot = { i -> editingSlot = i; menuOpen = false },
            )
            if (showLineStyle) {
                MenuHeading("Line style")
                LineStyleChips(lineStyle = style.lineStyle, onLineStyle = style.onLineStyle)
            }
        }
    }
    FocusStrokeDialogs(
        style = style,
        editingCustom = editingCustom,
        onDismissCustom = { editingCustom = false },
        addingColor = addingColor,
        onDismissAdd = { addingColor = false },
        editingSlot = editingSlot,
        onDismissSlot = { editingSlot = -1 },
    )
}

@Composable
private fun FocusStrokeDialogs(
    style: ToolbarStyleCallbacks,
    editingCustom: Boolean,
    onDismissCustom: () -> Unit,
    addingColor: Boolean,
    onDismissAdd: () -> Unit,
    editingSlot: Int,
    onDismissSlot: () -> Unit,
) {
    CustomColorEditor(
        visible = editingCustom,
        palette = style.palette,
        onDismiss = onDismissCustom,
        onRedefine = style.onRedefineCustom,
    )
    DialogPresence(addingColor) {
        CustomColorPickerDialog(
            initial = style.palette.custom ?: AppSettings.DEFAULT_CUSTOM_COLOR,
            palette = style.palette.colors,
            onConfirm = { newColor ->
                style.onAddColor(newColor)
                style.onColor(newColor)
                onDismissAdd()
            },
            onDismiss = onDismissAdd,
        )
    }
    DialogPresence(editingSlot in style.widthSlots.indices) {
        WidthSlotSliderDialog(
            label = PEN_WIDTH_LABELS[editingSlot],
            initial = style.widthSlots[editingSlot],
            onConfirm = { newPt -> style.onRedefineSlot(editingSlot, newPt); onDismissSlot() },
            onDismiss = onDismissSlot,
        )
    }
}

/**
 * Dual-mode tool group slot (Eraser whole/standard, Selection rect/lasso, Text/LaTeX): 1st tap
 * activates the group's current tool; 2nd tap while active or long-press opens [ToolGroupPicker].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusToolGroupSlot(
    groupId: String,
    ui: EditorUiState,
    surface: DrawingSurfaceView?,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val group = remember(groupId) { ALL_TOOL_GROUPS.first { it.id == groupId } }
    val selected = group.selected(settings.toolGroupSelections)
    val active = ui.tool in group.tools
    var open by remember { mutableStateOf(false) }
    val slotSize = LocalRailSlotSize.current
    val tint = if (active) MaterialTheme.colorScheme.primary else rememberChromeInk()
    Box {
        Box(
            modifier = Modifier
                .size(slotSize)
                .clip(ModernTheme.railSlotShape)
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                .combinedClickable(
                    onClick = {
                        if (active && group.tools.size > 1) open = true
                        else surface?.activateTool(selected, ui, settings, onSettingsChange)
                    },
                    onLongClick = { if (group.tools.size > 1) open = true },
                )
                .semantics { contentDescription = "Tool: ${selected.label}" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(selected.icon, contentDescription = null, tint = tint, modifier = Modifier.size(slotSize / 2))
        }
        if (group.tools.size > 1) MenuChevron()
        ToolbarMenu(expanded = open, onDismissRequest = { open = false }, fill = rememberPopupPanelColor()) {
            ToolGroupPicker(group, group.tools, selected) { picked ->
                val base = settings.copy(toolGroupSelections = group.withSelection(settings.toolGroupSelections, picked))
                surface?.activateTool(picked, ui, base, onSettingsChange)
                open = false
            }
        }
    }
}

@Composable
private fun FocusSingleToolSlot(
    tool: EditorTool,
    active: Boolean,
    onClick: () -> Unit,
) {
    val slotSize = LocalRailSlotSize.current
    val tint = if (active) MaterialTheme.colorScheme.primary else rememberChromeInk()
    Box(
        modifier = Modifier
            .size(slotSize)
            .clip(ModernTheme.railSlotShape)
            .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Tool: ${tool.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(tool.icon, contentDescription = null, tint = tint, modifier = Modifier.size(slotSize / 2))
    }
}

@Composable
private fun FocusActionSlot(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val slotSize = LocalRailSlotSize.current
    val ink = rememberChromeInk()
    Box(
        modifier = Modifier
            .size(slotSize)
            .clip(ModernTheme.railSlotShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) ink else ink.copy(alpha = 0.35f),
            modifier = Modifier.size(slotSize / 2),
        )
    }
}

/**
 * Quick colours slot: 3 favourite swatches arranged vertically when horizontal and horizontally
 * when vertical, plus a compact chevron opening the full [ColorSizePopup].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusQuickColorsSlot(
    tool: EditorTool,
    vertical: Boolean,
    style: ToolbarStyleCallbacks,
    favorites: ToolbarFavoritesCallbacks,
) {
    val colorOwner = favoriteToolFor(tool)
    val colors = favoritesFor(colorOwner, favorites)
    val slot = LocalRailSlotSize.current
    var editing by remember { mutableStateOf(-1) }
    var customEditing by remember { mutableStateOf(false) }
    val hairline = ModernTheme.hairlineBorder(chromeIsDark())
    val ink = rememberChromeInk()
    Box {
        Row(
            modifier = Modifier.size(slot),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (vertical) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        colors.forEachIndexed { i, c ->
                            QuickDot(c, c == style.color, 10.dp, hairline, "${colorOwner.label} favourite ${i + 1}",
                                onClick = { favorites.onPick(colorOwner, c) }, onLongClick = { editing = i })
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    ColorSizePopup(style) { open -> QuickChevron(open, ink, 14.dp) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    colors.forEachIndexed { i, c ->
                        QuickDot(c, c == style.color, 10.dp, hairline, "${colorOwner.label} favourite ${i + 1}",
                            onClick = { favorites.onPick(colorOwner, c) }, onLongClick = { editing = i })
                    }
                }
                Spacer(Modifier.width(2.dp))
                ColorSizePopup(style) { open -> QuickChevron(open, ink, 16.dp) }
            }
        }
        ToolbarMenu(expanded = editing in colors.indices, onDismissRequest = { editing = -1 }, fill = rememberPopupPanelColor()) {
            val idx = editing
            if (idx in colors.indices) {
                MenuHeading("${colorOwner.label} favourite ${idx + 1}")
                ColorPaletteRows(
                    selected = colors[idx],
                    palette = style.palette,
                    onPick = { picked -> favorites.onAssign(colorOwner, idx, picked); editing = -1 },
                    onEditCustom = { customEditing = true; editing = -1 },
                    compact = true,
                )
            }
        }
    }
    CustomColorEditor(visible = customEditing, palette = style.palette, onDismiss = { customEditing = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickDot(
    color: Int,
    selected: Boolean,
    size: Dp,
    hairline: Color,
    description: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else hairline,
                shape = CircleShape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun QuickChevron(open: () -> Unit, ink: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = open)
            .semantics { contentDescription = "Colour, tip size and line style" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = ink, modifier = Modifier.size(size * 0.8f))
    }
}

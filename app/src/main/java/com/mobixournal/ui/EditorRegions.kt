package com.mobixournal.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.mobixournal.render.GuideKind
import com.mobixournal.render.TriangleKind
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mobixournal.render.BarrelDoubleAction
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.clearPenDebug
import com.mobixournal.render.setPenDebug
import com.mobixournal.render.PlaceKind
import com.mobixournal.render.Placement
import com.mobixournal.render.SearchStatus
import com.mobixournal.ui.theme.rememberCanvasChromeColors
import android.view.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent

/**
 * The editor's top bar: undo/redo for the active pane, the tab overview, then the overflow menu.
 * When [AppSettings.showToolsInTopBar] is enabled, the empty title slot displays a compact row of
 * drawing tools and color button without increasing the 40dp height or shrinking the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    ui: EditorUiState,
    pane: PaneState,
    tabs: TabsUiState,
    settings: AppSettings? = null,
    onSettingsChange: ((AppSettings) -> Unit)? = null,
    onOpen: () -> Unit,
    onNewTab: () -> Unit,
    onSave: () -> Unit,
    onExportPdf: () -> Unit,
    splitView: Boolean,
    onToggleSplitView: () -> Unit,
) {
    val effectivePosition = if (settings?.showToolsInTopBar == true && settings.toolbarPosition == ToolbarPosition.TOP) {
        ToolbarPosition.LEFT
    } else {
        settings?.toolbarPosition ?: ToolbarPosition.LEFT
    }
    TopAppBar(
        navigationIcon = {
            if (settings != null && settings.showToolsInTopBar && effectivePosition == ToolbarPosition.LEFT && !ui.fullPage) {
                Spacer(Modifier.width(SideToolbarWidth))
            }
        },
        title = {
            if (settings != null && onSettingsChange != null && settings.showToolsInTopBar) {
                TopBarToolsRow(
                    ui = ui,
                    pane = pane,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                )
            }
        },
        modifier = Modifier.height(40.dp),
        actions = {
            SearchControls(pane)
            IconButton(onClick = { pane.surface?.undo() }, enabled = pane.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = { pane.surface?.redo() }, enabled = pane.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            TabOverviewButton(tabs)
            OverflowMenu(
                settings = settings,
                onSelectPenPreset = { preset ->
                    if (settings != null && onSettingsChange != null) {
                        val updated = settings.copy(
                            selectedPenPresetId = preset.id,
                            minimumPressure = preset.minimumPressure,
                            pressureMultiplier = preset.pressureMultiplier,
                        )
                        onSettingsChange(updated)
                        pane.surface?.applySettings(updated)
                    }
                },
                onOpen = onOpen,
                onNewTab = onNewTab,
                onSave = onSave,
                onSaveAs = { ui.showSaveAs = true },
                onImportPdf = { ui.showImportPdf = true },
                onExportPdf = onExportPdf,
                onSettings = { ui.showSettings = true },
                penDiagnostics = ui.penDiagnostics,
                onTogglePenDiagnostics = { ui.penDiagnostics = !ui.penDiagnostics },
                onOpenPenParameters = { ui.showPenParametersDialog = true },
                splitView = splitView,
                onToggleSplitView = onToggleSplitView,
            )
        },
    )
}

@Composable
private fun SearchControls(pane: PaneState) {
    fun apply(status: SearchStatus) {
        pane.searchCurrent = status.current
        pane.searchTotal = status.total
    }
    if (!pane.searchOpen) {
        IconButton(onClick = {
            pane.searchOpen = true
            pane.surface?.setSearchQuery(pane.searchQuery)?.let(::apply)
        }) {
            Icon(Icons.Filled.Search, contentDescription = "Search")
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        CompactSearchField(
            value = pane.searchQuery,
            onValueChange = {
                pane.searchQuery = it
                pane.surface?.setSearchQuery(it)?.let(::apply) ?: apply(SearchStatus())
            },
        )
        Text("${pane.searchCurrent}/${pane.searchTotal}")
        CompactIconButton(
            contentDescription = "Previous match",
            enabled = pane.searchTotal > 0,
            onClick = { pane.surface?.previousSearchHit()?.let(::apply) },
        ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match") }
        CompactIconButton(
            contentDescription = "Next match",
            enabled = pane.searchTotal > 0,
            onClick = { pane.surface?.nextSearchHit()?.let(::apply) },
        ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match") }
        CompactIconButton(contentDescription = "Close search", onClick = {
            pane.searchOpen = false
            pane.searchQuery = ""
            pane.surface?.clearSearch()?.let(::apply) ?: apply(SearchStatus())
        }) { Icon(Icons.Filled.Close, contentDescription = "Close search") }
    }
}

@Composable
private fun CompactIconButton(
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        icon()
    }
}

@Composable
private fun CompactSearchField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier
            .width(88.dp)
            .height(36.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text("Search", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        },
    )
}

/**
 * The control rail, wired to the active pane's surface. Every callback either drives the canvas
 * directly or writes back through [onSettingsChange] so the choice is persisted.
 */
@Composable
fun EditorToolbar(
    ui: EditorUiState,
    pane: PaneState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    audio: AudioUiState,
) {
    val surface = pane.surface
    val effectivePosition = if (settings.showToolsInTopBar && settings.toolbarPosition == ToolbarPosition.TOP) {
        ToolbarPosition.LEFT
    } else {
        settings.toolbarPosition
    }
    SideToolbar(
        horizontal = effectivePosition.isHorizontal,
        tool = ui.tool,
        onTool = { tool ->
            // One rule for every path: the outgoing tool's style goes into its own slot, the
            // incoming one's comes back out, and both are pushed to the canvas and persisted. See
            // EditorUiState.switchToolTo — it is what stops a fat figure or highlighter from
            // becoming the pen's width.
            surface?.activateTool(tool, ui, settings, onSettingsChange)
        },
        audio = audio,
        railOrder = settings.railOrder,
        railHidden = settings.railHidden,
        shapeOrder = settings.shapeOrder,
        shapeHidden = settings.shapeHidden,
        toolGroupSelections = settings.toolGroupSelections,
        onToolGroupPick = { group, picked ->
            // Re-facing the slot and activating the tool are one settings write, both built on the
            // same base: done as two writes off the composition snapshot, the second would clobber
            // the first and the slot's face (and the menu's tick) would stay on the old member.
            val base = settings.copy(
                toolGroupSelections = group.withSelection(settings.toolGroupSelections, picked),
            )
            surface?.activateTool(picked, ui, base, onSettingsChange)
        },
        styleCallbacks = rememberToolbarStyleCallbacks(ui, surface, settings, onSettingsChange),
        recognizeShapes = settings.recognizeShapes,
        onRecognizeShapes = {
            surface?.recognizeShapes = it
            onSettingsChange(settings.copy(recognizeShapes = it))
        },
        guideKind = settings.guideKind,
        onGuideKind = {
            surface?.placeGuide(it)
            onSettingsChange(settings.copy(guideKind = it))
        },
        layerCallbacks = toolbarLayerCallbacks(surface, pane),
        zoom = pane.zoom,
        onZoomIn = { surface?.zoomIn() },
        onZoomOut = { surface?.zoomOut() },
        onZoomReset = { surface?.resetZoom() },
        pageCallbacks = toolbarPagesCallbacks(pane, settings, onSettingsChange, surface),
        backgroundStyle = pane.backgroundStyle,
        onBackgroundStyle = { surface?.setPageBackgroundStyle(it) },
    )
}

@Composable
fun rememberToolbarStyleCallbacks(
    ui: EditorUiState,
    surface: DrawingSurfaceView?,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
): ToolbarStyleCallbacks = ToolbarStyleCallbacks(
    color = ui.color,
    onColor = { newColor ->
        ui.color = newColor
        surface?.colorArgb = newColor
        if (ui.tool == EditorTool.HIGHLIGHTER) {
            ui.highlighterColor = newColor
            onSettingsChange(settings.copy(highlighterColor = newColor))
        } else {
            onSettingsChange(settings.withColorUsed(newColor))
        }
    },
    palette = rememberColorPaletteState(settings, onSettingsChange),
    onRedefineCustom = { newColor -> redefineCustomColor(newColor, ui, surface, settings, onSettingsChange) },
    width = ui.width,
    onWidth = { newWidth ->
        ui.width = newWidth
        surface?.baseWidthPt = newWidth
        if (ui.tool == EditorTool.HIGHLIGHTER) ui.highlighterWidth = newWidth
        onSettingsChange(settings.withWidthFor(ui.tool, newWidth))
    },
    widthSlots = settings.penWidths,
    onRedefineSlot = { i, newPt -> redefineWidthSlot(i, newPt, ui, surface, settings, onSettingsChange) },
    lineStyle = ui.lineStyle,
    onLineStyle = { ui.lineStyle = it; surface?.currentLineStyle = it },
)

/**
 * A compact, horizontal scrollable row of drawing tools and color button that sits inside the
 * empty title slot of the 40dp top app bar, allowing quick access without shrinking the canvas.
 */
@Composable
fun TopBarToolsRow(
    ui: EditorUiState,
    pane: PaneState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = pane.surface
    Row(
        modifier = modifier
            .fillMaxHeight()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in visibleTopBarItems(settings.topBarOrder, settings.topBarHidden)) {
            val single = singleToolForTopBarId(item.id)
            if (single != null) {
                CompactSingleToolButton(
                    tool = single,
                    active = ui.tool == single,
                    onClick = { surface?.activateTool(single, ui, settings, onSettingsChange) },
                )
            } else if (item.id == "triangle") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompactTrianglePopupButton(
                        active = ui.tool == EditorTool.TRIANGLE,
                        triangleKind = settings.triangleKind,
                        onSelectKind = { kind ->
                            val updated = settings.copy(triangleKind = kind)
                            onSettingsChange(updated)
                            surface?.activateTool(EditorTool.TRIANGLE, ui, updated, onSettingsChange)
                        },
                        onOpenAnglesDialog = { ui.showScaleneAnglesDialog = true },
                        onClick = { surface?.activateTool(EditorTool.TRIANGLE, ui, settings, onSettingsChange) },
                    )
                    if (ui.tool == EditorTool.TRIANGLE && settings.triangleKind == TriangleKind.SCALENE) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { ui.showScaleneAnglesDialog = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Customize scalene angles",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            } else {
                val group = toolGroupForRailItem(item.id)
                if (group != null) {
                    CompactTopBarToolButton(
                        group = group,
                        members = group.tools,
                        selected = group.selected(settings.toolGroupSelections),
                        active = ui.tool in group.tools,
                        onTool = { tool ->
                            surface?.activateTool(tool, ui, settings, onSettingsChange)
                        },
                        onPick = { picked ->
                            val base = settings.copy(
                                toolGroupSelections = group.withSelection(settings.toolGroupSelections, picked),
                            )
                            surface?.activateTool(picked, ui, base, onSettingsChange)
                        },
                    )
                } else if (item.id == "guides") {
                    CompactGuidePopupButton(
                        kind = settings.guideKind,
                        onKind = {
                            onSettingsChange(settings.copy(guideKind = it))
                            pane.surface?.placeGuide(it)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSingleToolButton(
    tool: EditorTool,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            tool.icon,
            contentDescription = "Tool: ${tool.label}",
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactTrianglePopupButton(
    active: Boolean,
    triangleKind: TriangleKind,
    onSelectKind: (TriangleKind) -> Unit,
    onOpenAnglesDialog: () -> Unit,
    onClick: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                .combinedClickable(
                    onClick = { if (active) open = true else onClick() },
                    onLongClick = { open = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                EditorTool.TRIANGLE.icon,
                contentDescription = "Tool: Triangle",
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 1.dp, bottom = 1.dp)
                .size(10.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeading("Triangle")
            for (kind in TriangleKind.values()) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(kind.label)
                            if (kind == TriangleKind.SCALENE) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            open = false
                                            onSelectKind(TriangleKind.SCALENE)
                                            onOpenAnglesDialog()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Customize scalene angles",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    },
                    trailingIcon = {
                        if (kind == triangleKind) Icon(Icons.Filled.Check, contentDescription = "selected")
                    },
                    onClick = {
                        onSelectKind(kind)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CompactGuidePopupButton(
    kind: GuideKind,
    onKind: (GuideKind) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val active = kind != GuideKind.NONE
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                .clickable { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Straighten,
                contentDescription = "Drawing guides",
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeading("Drawing guide")
            for (option in GuideKind.values()) {
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = { if (option == kind) Icon(Icons.Filled.Check, contentDescription = "selected") },
                    onClick = { onKind(option); open = false },
                )
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactTopBarToolButton(
    group: ToolGroup,
    members: List<EditorTool> = group.tools,
    selected: EditorTool,
    active: Boolean,
    onTool: (EditorTool) -> Unit,
    onPick: (EditorTool) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                .combinedClickable(
                    onClick = { if (active && members.size > 1) open = true else onTool(selected) },
                    onLongClick = { if (members.size > 1) open = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                selected.icon,
                contentDescription = "Tool: ${selected.label}",
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        if (members.size > 1) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.dp, bottom = 1.dp)
                    .size(10.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeading(group.label)
            for (member in members) {
                DropdownMenuItem(
                    text = { Text(member.label) },
                    leadingIcon = { Icon(member.icon, contentDescription = null) },
                    trailingIcon = {
                        if (member == selected) Icon(Icons.Filled.Check, contentDescription = "selected")
                    },
                    onClick = { onPick(member); open = false },
                )
            }
        }
    }
}



private fun redefineCustomColor(
    newColor: Int,
    ui: EditorUiState,
    surface: DrawingSurfaceView?,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val old = settings.customColor
    onSettingsChange(settings.copy(customColor = newColor))
    if (ui.color == old) {
        ui.color = newColor
        surface?.colorArgb = newColor
        onSettingsChange(settings.copy(customColor = newColor).withColorUsed(newColor))
    }
}

private fun redefineWidthSlot(
    i: Int,
    newPt: Float,
    ui: EditorUiState,
    surface: DrawingSurfaceView?,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val old = settings.penWidths[i]
    val slots = settings.penWidths.toMutableList().also { it[i] = newPt }
    val active = ui.width == old
    val affectsShape = settings.defaultShapeSlot == i || (ui.tool in SHAPE_TOOLS && active)
    val newShapeWidth = if (affectsShape) newPt else settings.shapeWidth
    val newLastWidth = if (ui.tool == EditorTool.PEN && active) newPt else settings.lastWidth
    val updatedSettings = settings.copy(
        penWidths = slots,
        shapeWidth = newShapeWidth,
        lastWidth = newLastWidth,
    )
    onSettingsChange(updatedSettings)
    if (active || (ui.tool in SHAPE_TOOLS && affectsShape)) {
        val effectiveWidth = if (ui.tool in SHAPE_TOOLS) newShapeWidth else newPt
        ui.width = effectiveWidth
        surface?.baseWidthPt = effectiveWidth
    }
}

@Composable
private fun toolbarLayerCallbacks(surface: DrawingSurfaceView?, pane: PaneState): ToolbarLayerCallbacks =
    ToolbarLayerCallbacks(
        layers = pane.layers,
        hasSelection = pane.hasSelection,
        onAddLayer = { surface?.addLayer() },
        onDeleteLayer = { i -> surface?.deleteLayer(i) },
        onMergeLayerDown = { i -> surface?.mergeLayerDown(i) },
        onRenameLayer = { i, name -> surface?.renameLayer(i, name) },
        onMoveLayer = { from, to -> surface?.moveLayer(from, to) },
        onActivateLayer = { surface?.setActiveLayer(it) },
        onToggleLayerHidden = { i, visible -> surface?.setLayerHidden(i, visible) },
        onMoveSelectionToLayer = { surface?.moveSelectionToLayer(it) },
    )

@Composable
private fun toolbarPagesCallbacks(
    pane: PaneState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    surface: DrawingSurfaceView?,
): ToolbarPagesCallbacks =
    ToolbarPagesCallbacks(
        pageCount = pane.pageCount,
        currentPage = pane.currentPage,
        onAddPage = { surface?.addPage() },
        onRemovePage = { surface?.removePage() },
        onGoToPage = { surface?.goToPage(it) },
        pageSize = pane.pageSize,
        onPageSize = { w, h -> surface?.setPageSize(w, h) },
        defaultPageSize = settings.defaultPageWidthPt to settings.defaultPageHeightPt,
        pageColumns = settings.pageColumns,
        onPageColumns = {
            surface?.setColumns(it)
            onSettingsChange(settings.copy(pageColumns = it))
        },
        pagesEditMode = pane.pagesEditMode,
        onPagesEditMode = { pane.pagesEditMode = it; surface?.setPagesEditMode(it) },
        selectedPages = pane.selectedPages,
        onDeleteSelectedPages = { surface?.deleteSelectedPages() },
        onClearPageSelection = { surface?.clearPageSelection() },
        copiedPages = pane.copiedPages,
        onCopySelectedPages = { surface?.copySelectedPages() },
        onPastePages = { surface?.pasteCopiedPages() },
        onSaveAsDefault = { w, h ->
            onSettingsChange(settings.copy(defaultPageWidthPt = w, defaultPageHeightPt = h))
        },
    )

/**
 * One pane: its tab strip over its canvas. All of the surface's callbacks write into *that* pane's
 * [PaneState], never the active one, so a background pane keeps its own page, zoom and undo state up
 * to date while the toolbar drives the other. A touch anywhere in the pane (observed on the initial
 * pass, so the canvas still gets the event) hands it focus.
 */
/**
 * Applies the initial tool and style settings to the drawing surface.
 */
private fun DrawingSurfaceView.applyInitialStyle(ui: EditorUiState, settings: AppSettings) {
    applyTool(ui.tool)
    applySettings(settings)
    colorArgb = ui.color
    baseWidthPt = ui.width
    currentLineStyle = ui.lineStyle
}

/**
 * Binds the surface's state callbacks to the pane state holder.
 */
private fun DrawingSurfaceView.bindTo(state: PaneState) {
    onLayersChanged = {
        state.layers = visibleLayers()
        state.backgroundStyle = visiblePageBackgroundStyle()
        state.pageSize = visiblePageSize()
    }
    state.layers = visibleLayers()
    state.backgroundStyle = visiblePageBackgroundStyle()
    state.pageSize = visiblePageSize()
    onHistoryChanged = { u, r -> state.canUndo = u; state.canRedo = r }
    onZoomChanged = { z -> state.zoom = z }
    onPageCountChanged = { n -> state.pageCount = n }
    onPageSelectionChanged = { n -> state.selectedPages = n }
    onPageClipboardChanged = { n -> state.copiedPages = n }
    onCurrentPageChanged = { page ->
        state.currentPage = page
        state.backgroundStyle = visiblePageBackgroundStyle()
        state.pageSize = visiblePageSize()
    }
    onScrollChanged = { y, total, vp -> state.scrollY = y; state.contentHeight = total; state.viewportHeight = vp }
    onSelectionChanged = { s -> state.hasSelection = s }
    onTextSelectionChanged = { s -> state.hasTextSelection = s }
    onClipboardChanged = { c -> state.hasClipboard = c }
    onBackgroundRegionChanged = { r -> state.hasBackgroundRegion = r }
    onSplineChanged = { n -> state.splineNodes = n }
    onSearchChanged = { s -> state.searchCurrent = s.current; state.searchTotal = s.total }
}

/**
 * Binds editor action callbacks that need access to the editor UI and settings.
 */
private fun DrawingSurfaceView.bindEditorActions(
    ui: EditorUiState,
    index: Int,
    onActivePane: (Int) -> Unit,
    onPickImage: (Placement) -> Unit,
    getSettings: () -> AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    onToggleFullPage = { ui.fullPage = !ui.fullPage }
    onBarrelDoubleClick = { action ->
        when (action) {
            // The eraser the rail's slot stands for — whole-stroke by default — so the button lands on
            // the eraser the user actually uses instead of a fixed one.
            BarrelDoubleAction.TOGGLE_ERASER -> {
                val previous = ui.tool
                ui.toggleTool(preferredEraser(getSettings().toolGroupSelections))
                activateTool(ui.tool, ui, getSettings(), onSettingsChange, previous)
            }
            BarrelDoubleAction.TOGGLE_SELECT -> {
                val previous = ui.tool
                ui.toggleTool(EditorTool.SELECT)
                activateTool(ui.tool, ui, getSettings(), onSettingsChange, previous)
            }
            BarrelDoubleAction.TOGGLE_FULL_PAGE -> {
                ui.fullPage = !ui.fullPage
                applyTool(ui.tool)
            }
            else -> applyTool(ui.tool)
        }
    }
    onPlace = { kind, placement ->
        onActivePane(index)
        when (kind) {
            PlaceKind.TEXT -> ui.textPlacement = placement
            PlaceKind.TEX -> ui.texPlacement = placement
            PlaceKind.IMAGE -> onPickImage(placement)
        }
    }

    /** Passa alla PENNA con un colore fisso, mantenendo coerenti gli stati penna/evidenziatore. */
    fun switchToPen(color: Int) {
        val next = ui.switchToolTo(EditorTool.PEN, getSettings())
        ui.color = color
        colorArgb = color
        baseWidthPt = ui.width
        applyTool(EditorTool.PEN)
        onSettingsChange(next.withColorUsed(color))
    }

    onKeyPressed = { char ->
        val settings = getSettings()
        var consumed = false
        val hit: (String) -> Boolean = { it.isNotEmpty() && char.equals(it, ignoreCase = true) }

        // Toggle penna ↔ gomma
        if (!consumed && hit(settings.penEraserToggleKey)) {
            val newTool = if (ui.tool == EditorTool.PEN || ui.tool == EditorTool.HIGHLIGHTER) {
                preferredEraser(settings.toolGroupSelections)
            } else {
                EditorTool.PEN
            }
            activateTool(newTool, ui, settings, onSettingsChange)
            consumed = true
        }

        // Toggle mano
        if (!consumed && hit(settings.handToggleKey)) {
            val newTool = if (ui.tool == EditorTool.HAND) EditorTool.PEN else EditorTool.HAND
            activateTool(newTool, ui, settings, onSettingsChange)
            consumed = true
        }

        // Scorciatoia colore: un tasto per ogni colore della palette, ciascuno sulla penna.
        val color = settings.colorShortcutKeys.entries.firstOrNull { hit(it.value) }?.key
        if (!consumed && color != null) {
            switchToPen(color)
            consumed = true
        }

        // Scorciatoia strumento: un tasto per ogni strumento, attivato come da rail o da barrel.
        val target = settings.toolShortcutKeys.entries.firstOrNull { hit(it.value) }?.key
        if (!consumed && target != null) {
            activateTool(target, ui, settings, onSettingsChange)
            consumed = true
        }

        consumed
    }
}

@Composable
fun EditorPaneView(
    index: Int,
    ui: EditorUiState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    tabs: List<TabsUiState>,
    onActivePane: (Int) -> Unit,
    onSurfaceCreated: (Int, DrawingSurfaceView) -> Unit,
    onPickImage: (Placement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = ui.panes[index]
    val chrome = rememberCanvasChromeColors()
    val latestSettings = rememberUpdatedState(settings)
    Column(
        modifier = modifier.pointerInput(index) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                onActivePane(index)
                // Richiedi il focus per ricevere i tasti
                state.surface?.requestFocus()
            }
        },
    ) {
        if (!ui.fullPage) TabStrip(tabs[index.coerceIn(tabs.indices)], modifier = Modifier.fillMaxWidth())
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    DrawingSurfaceView(ctx).also {
                        it.applyInitialStyle(ui, settings)
                        it.bindTo(state)
                        it.bindEditorActions(
                            ui, index, onActivePane, onPickImage,
                            { latestSettings.value }, onSettingsChange,
                        )
                        // The diagnostics panel is fed straight from the canvas it reports on.
                        it.onPenDebug = { lines -> state.penDebugLines = lines }
                        state.surface = it
                        onSurfaceCreated(index, it)
                    }
                },
                update = {
                    it.applyChromeColors(chrome.backdrop, chrome.selection, chrome.guide)
                    it.setPenDebug(ui.penDiagnostics)
                },
                modifier = Modifier.fillMaxSize(),
            )
            ScrollThumb(
                scrollY = state.scrollY,
                totalHeightPx = state.contentHeight,
                viewportPx = state.viewportHeight,
                currentPage = state.currentPage,
                pageCount = state.pageCount,
                onScrollTo = { state.surface?.scrollToY(it) },
                modifier = Modifier.matchParentSize(),
            )
            // Corner is user-configurable (Appearance settings); the default is bottom-right. The
            // zoom badge rides directly above the page badge and shares its look, so the pair reads as
            // one stack; tapping the zoom one puts the view back at 100%.
            Column(
                modifier = Modifier
                    .align(pageCounterAlignment(settings.pageCounterVertical, settings.pageCounterHorizontal))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ZoomBadge(
                    zoom = state.zoom,
                    onClick = { state.surface?.resetZoom() },
                )
                PageCounter(
                    currentPage = state.currentPage,
                    pageCount = state.pageCount,
                )
            }
            // The pen diagnostics panel floats over the canvas it reports on, in the corner the
            // chrome leaves free — the events it logs are then exactly the ones it describes.
            if (ui.penDiagnostics) {
                PenDiagnosticsPanel(
                    lines = state.penDebugLines,
                    onClear = { state.surface?.clearPenDebug() },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
        }
    }
}

/** The top-bar overflow ("hamburger") menu: compact quick file actions, PDF row, pen presets, and settings. */
@Composable
private fun OverflowMenu(
    settings: AppSettings?,
    onSelectPenPreset: (PenPreset) -> Unit,
    onOpen: () -> Unit,
    onNewTab: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onImportPdf: () -> Unit,
    onExportPdf: () -> Unit,
    onSettings: () -> Unit,
    penDiagnostics: Boolean,
    onTogglePenDiagnostics: () -> Unit,
    onOpenPenParameters: () -> Unit,
    splitView: Boolean,
    onToggleSplitView: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.Menu, contentDescription = "Menu")
    }
    DropdownMenu(
        expanded = open,
        onDismissRequest = { open = false },
        modifier = Modifier.widthIn(min = 280.dp, max = 320.dp),
    ) {
        // Quick File Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MenuQuickAction(
                icon = Icons.AutoMirrored.Filled.NoteAdd,
                label = "New",
                onClick = { open = false; onNewTab() },
                modifier = Modifier.weight(1f),
            )
            MenuQuickAction(
                icon = Icons.Filled.FileOpen,
                label = "Open",
                onClick = { open = false; onOpen() },
                modifier = Modifier.weight(1f),
            )
            MenuQuickAction(
                icon = Icons.Filled.Save,
                label = "Save",
                onClick = { open = false; onSave() },
                modifier = Modifier.weight(1f),
            )
            MenuQuickAction(
                icon = Icons.Filled.SaveAs,
                label = "Save As",
                onClick = { open = false; onSaveAs() },
                modifier = Modifier.weight(1f),
            )
        }

        // PDF Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { open = false; onImportPdf() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import PDF", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { open = false; onExportPdf() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export PDF", style = MaterialTheme.typography.labelMedium)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Pen Presets
        if (settings != null && settings.penPresets.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Pen presets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
                        onClick = { open = false; onOpenPenParameters() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = "Pen parameters",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    settings.penPresets.forEach { preset ->
                        val isSelected = preset.id == settings.selectedPenPresetId
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                open = false
                                onSelectPenPreset(preset)
                            },
                            label = { Text(preset.name, style = MaterialTheme.typography.bodySmall, maxLines = 1) },
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        } else {
            DropdownMenuItem(
                text = { Text("Pen parameters…") },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                onClick = { open = false; onOpenPenParameters() },
            )
        }

        // Tools and Settings
        DropdownMenuItem(
            text = { Text(if (splitView) "Close split view" else "Split view") },
            leadingIcon = { Icon(Icons.Filled.VerticalSplit, contentDescription = null) },
            onClick = { open = false; onToggleSplitView() },
        )
        DropdownMenuItem(
            text = { Text(if (penDiagnostics) "Hide pen diagnostics" else "Pen diagnostics") },
            leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
            onClick = { open = false; onTogglePenDiagnostics() },
        )
        DropdownMenuItem(
            text = { Text("Settings") },
            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            onClick = { open = false; onSettings() },
        )
    }
}

@Composable
private fun MenuQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}


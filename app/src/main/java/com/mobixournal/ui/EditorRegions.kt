package com.mobixournal.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.mobixournal.render.GuideKind
import com.mobixournal.render.TrapezoidKind
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
            // Order is deliberate: split view sits directly right of the search button, and Save —
            // the more frequent action — takes the slot split view used to hold, right before the
            // overflow menu. Undo/redo stay paired between them.
            SearchControls(pane)
            IconButton(onClick = onToggleSplitView) {
                Icon(
                    Icons.Filled.VerticalSplit,
                    contentDescription = if (splitView) "Close split view" else "Split view",
                    tint = if (splitView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = { pane.surface?.undo() }, enabled = pane.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = { pane.surface?.redo() }, enabled = pane.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Filled.Save, contentDescription = "Save")
            }
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
            )
        },
    )
}

@Composable
private fun SearchIndexingDialog(
    progress: String?,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = "Loading text...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (progress != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = progress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
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
        }) {
            Icon(Icons.Filled.Search, contentDescription = "Search")
        }
        return
    }

    LaunchedEffect(pane, pane.surface, pane.searchOpen, pane.documentVersion) {
        if (pane.searchOpen) {
            val surface = pane.surface ?: return@LaunchedEffect
            if (!surface.isHandwritingIndexReady()) {
                surface.ensureHandwritingIndex {
                    surface.setSearchQuery(pane.searchQuery).let(::apply)
                }
            } else {
                surface.setSearchQuery(pane.searchQuery).let(::apply)
            }
        }
    }

    if (pane.searchIndexing) {
        SearchIndexingDialog(
            progress = pane.searchIndexingProgress,
            onCancel = {
                pane.surface?.cancelIndexing()
                pane.searchIndexing = false
            },
        )
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(pane.searchOpen, pane.searchIndexing) {
        if (pane.searchOpen && !pane.searchIndexing) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Surface(
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            if (pane.searchIndexing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .padding(start = 2.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(start = 2.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = pane.searchQuery,
                onValueChange = {
                    pane.searchQuery = it
                    pane.surface?.setSearchQuery(it)?.let(::apply) ?: apply(SearchStatus())
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    pane.surface?.nextSearchHit()?.let(::apply)
                }),
                modifier = Modifier
                    .widthIn(min = 80.dp, max = 150.dp)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (pane.searchQuery.isEmpty()) {
                            Text(
                                if (pane.searchIndexing) "Loading text..." else "Search document...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        inner()
                    }
                },
            )
            if (pane.searchQuery.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                SearchCounterBadge(pane.searchCurrent, pane.searchTotal)
            }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.width(2.dp))
            CompactIconButton(
                contentDescription = "Previous match",
                enabled = pane.searchTotal > 0,
                onClick = { pane.surface?.previousSearchHit()?.let(::apply) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Previous match",
                    modifier = Modifier.size(18.dp),
                )
            }
            CompactIconButton(
                contentDescription = "Next match",
                enabled = pane.searchTotal > 0,
                onClick = { pane.surface?.nextSearchHit()?.let(::apply) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Next match",
                    modifier = Modifier.size(18.dp),
                )
            }
            CompactIconButton(
                contentDescription = "Close search",
                onClick = {
                    pane.searchOpen = false
                    pane.searchQuery = ""
                    pane.surface?.cancelIndexing()
                    pane.surface?.clearSearch()?.let(::apply) ?: apply(SearchStatus())
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close search",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchCounterBadge(current: Int, total: Int) {
    if (total > 0) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                "$current/$total",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
        ) {
            Text(
                "0/0",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun CompactIconButton(
    contentDescription: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.size(36.dp),
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        icon()
    }
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
        for (item in visibleTopBarItems(settings.topBarOrder, settings.topBarHidden, settings.shapeHidden)) {
            val single = singleToolForTopBarId(item.id)
            if (single != null) {
                CompactSingleToolButton(
                    tool = single,
                    active = ui.tool == single,
                    onClick = { surface?.activateTool(single, ui, settings, onSettingsChange) },
                )
            } else if (item.id == "triangle" || item.id == "trapezoid") {
                val triangle = item.id == "triangle"
                val tool = if (triangle) EditorTool.TRIANGLE else EditorTool.TRAPEZOID
                val kindLabels = if (triangle) {
                    TriangleKind.values().map { it.label }
                } else {
                    TrapezoidKind.values().map { it.label }
                }
                val kind = if (triangle) settings.triangleKind.ordinal else settings.trapezoidKind.ordinal
                CompactShapeKindButton(
                    tool = tool,
                    active = ui.tool == tool,
                    heading = if (triangle) "Triangle" else "Trapezoid",
                    kinds = kindLabels,
                    selectedKind = kind,
                    editableKind = if (triangle) TriangleKind.SCALENE.ordinal else TrapezoidKind.SCALENE.ordinal,
                    onSelectKind = { index ->
                        val updated = if (triangle) {
                            settings.copy(triangleKind = TriangleKind.values()[index])
                        } else {
                            settings.copy(trapezoidKind = TrapezoidKind.values()[index])
                        }
                        onSettingsChange(updated)
                        surface?.activateTool(tool, ui, updated, onSettingsChange)
                    },
                    // The scalene variant is the one with angles to set, so while it is the live one the
                    // button keeps its pencil: one tap reopens the dialog instead of digging into a menu.
                    showEditor = ui.tool == tool &&
                        (if (triangle) settings.triangleKind == TriangleKind.SCALENE
                        else settings.trapezoidKind == TrapezoidKind.SCALENE),
                    editorHint = if (triangle) "Customize scalene angles" else "Customize scalene angles (trapezoid)",
                    onEditKind = {
                        if (triangle) ui.showScaleneAnglesDialog = true else ui.showTrapezoidAnglesDialog = true
                    },
                    onClick = { surface?.activateTool(tool, ui, settings, onSettingsChange) },
                )
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

/**
 * A figure tool that ships in several geometric variants (the triangle and the trapezoid): tapping
 * the button picks the tool, tapping it again — or a long press — opens the variant menu, and while a
 * variant that has something to configure (the scalene kind's angles) is the live one, a pencil sits
 * beside the button to reopen its dialog.
 *
 * Shared by both figures rather than copied, so the two behave identically: same tap-to-pick /
 * tap-again-to-change-the-variant gesture, same pencil, same menu shape.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactShapeKindButton(
    tool: EditorTool,
    active: Boolean,
    heading: String,
    kinds: List<String>,
    selectedKind: Int,
    editableKind: Int,
    onSelectKind: (Int) -> Unit,
    showEditor: Boolean,
    editorHint: String,
    onEditKind: () -> Unit,
    onClick: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
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
                    tool.icon,
                    contentDescription = "Tool: ${tool.label}",
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
                MenuHeading(heading)
                kinds.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label)
                                if (index == editableKind) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                open = false
                                                onSelectKind(index)
                                                onEditKind()
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = editorHint,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        },
                        trailingIcon = {
                            if (index == selectedKind) Icon(Icons.Filled.Check, contentDescription = "selected")
                        },
                        onClick = {
                            onSelectKind(index)
                            open = false
                        },
                    )
                }
            }
        }
        if (showEditor) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEditKind),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = editorHint,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
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
    onSearchIndexingChanged = { active, progress ->
        state.searchIndexing = active
        state.searchIndexingProgress = progress
    }
    onDocumentLoaded = {
        state.documentVersion++
    }
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

/** The top-bar overflow ("hamburger") menu: compact quick file actions, PDF row, pen subsection, and settings. */
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
) {
    var open by remember { mutableStateOf(false) }
    Box {
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

        // Pen Subsection
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
                    "Pen",
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
            if (settings != null && settings.penPresets.isNotEmpty()) {
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
                Spacer(Modifier.height(4.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        open = false
                        onTogglePenDiagnostics()
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (penDiagnostics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (penDiagnostics) "Hide pen diagnostics" else "Pen diagnostics",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (penDiagnostics) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Settings
        DropdownMenuItem(
            text = { Text("Settings") },
            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            onClick = { open = false; onSettings() },
        )
    }
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


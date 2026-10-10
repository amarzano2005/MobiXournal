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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
import com.mobixournal.ui.theme.rememberToolbarColor
import android.view.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent

/**
 * The editor's top bar: undo/redo for the active pane, document title, then the overflow menu.
 * In Modern UI, renders as a floating dock surface matching the Main Toolbar.
 * The title slot always displays the Secondary Toolbar (compact row of geometric figures and tools);
 * the plain document-title chip is only the fallback for callers that pass no [settings].
 * The **leading** slot carries the active tool indicator on a left-docked rail — the button that stands
 * between the two toolbars, over the rail's own column ([StandaloneToolIndicator]) — where the backup
 * dock-cap style instead only needs a spacer to keep the dock clear of that column.
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
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    splitView: Boolean,
    onToggleSplitView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The rail can only be docked to a vertical edge now, so the spacer that keeps the top bar's
    // content clear of it is simply needed whenever that edge is the left one.
    val railOnLeft = (settings?.toolbarPosition ?: ToolbarPosition.LEFT) == ToolbarPosition.LEFT
    TopAppBar(
        navigationIcon = {
            // In the current style the active tool indicator *is* the top bar's leading slot: it stands in
            // the corner between the two toolbars, and its width is what puts the figures' dock on the
            // document tabs' own line ([TOP_BAR_INDICATOR_WIDTH]). In the backup dock-cap style the slot
            // only keeps the top bar's content clear of the rail.
            if (settings != null && railOnLeft && !ui.fullPage) {
                if (!INDICATOR_AS_DOCK_CAP &&
                    onSettingsChange != null &&
                    standaloneIndicatorSlot(railOnLeft) == IndicatorSlot.LEADING
                ) {
                    StandaloneToolIndicator(
                        ui = ui,
                        styleCallbacks = rememberToolbarStyleCallbacks(
                            ui = ui,
                            surface = pane.surface,
                            settings = settings,
                            onSettingsChange = onSettingsChange,
                        ),
                        modifier = Modifier,
                    )
                } else {
                    Spacer(Modifier.width(SideToolbarModernTotalWidth))
                }
            }
        },
        title = {
            if (settings != null && onSettingsChange != null) {
                TopBarToolsRow(
                    ui = ui,
                    pane = pane,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                )
            } else {
                val title = tabs.titles.getOrNull(tabs.activeIndex)?.ifBlank { "Untitled" } ?: "MobiXournal"
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        },
        modifier = modifier.height(48.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
        actions = {
            // Order is deliberate: split view sits directly right of the search button, then the
            // paired undo/redo. The two glyph-shaped file actions follow — a compact quick Export PDF
            // and then Save, which lands the most-used of the two under the thumb at the far right —
            // so neither costs a trip into the overflow menu.
            SearchControls(pane)
            IconButton(onClick = onToggleSplitView, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.VerticalSplit,
                    contentDescription = if (splitView) "Close split view" else "Split view",
                    tint = if (splitView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                tonalElevation = 1.dp,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pane.surface?.undo() }, enabled = pane.canUndo, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { pane.surface?.redo() }, enabled = pane.canRedo, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(20.dp))
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                IconButton(onClick = onExportPdf, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.PictureAsPdf,
                        contentDescription = "Export PDF",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                IconButton(onClick = onSave, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
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
                onInsertGraph = { ui.showGraphDialog = true },
                onExportPdf = onExportPdf,
                onExportPagePng = onExportPagePng,
                onExportPageSvg = onExportPageSvg,
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
        IconButton(
            onClick = { pane.searchOpen = true },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = "Search",
                modifier = Modifier.size(20.dp),
            )
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
 * The editor's Main Toolbar (rail), wired to the active pane's surface. Every callback either drives
 * the canvas directly or writes back through [onSettingsChange] so the choice is persisted.
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
    SideToolbar(
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
        favoritesCallbacks = ToolbarFavoritesCallbacks(
            penFavorites = settings.penFavorites,
            highlighterFavorites = settings.highlighterFavorites,
            // A tap takes the colour (and the tool that owns it); the settings are read fresh each
            // time rather than captured, so a favourite added in Settings is reachable at once.
            onPick = { colorOwner, color ->
                pickFavoriteColor(surface, ui, settings, onSettingsChange, colorOwner, color)
            },
            onAssign = { colorOwner, index, color ->
                onSettingsChange(assignFavorite(settings, colorOwner, index, color))
            },
        ),
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
        pageCallbacks = toolbarPagesCallbacks(pane, ui, settings, onSettingsChange, surface),
        backgroundStyle = pane.backgroundStyle,
        onBackgroundStyle = { surface?.setPageBackgroundStyle(it) },
        backgroundConfig = pane.backgroundConfig,
        onBackgroundConfig = { surface?.setPageBackgroundConfig(it) },
        onStationery = { surface?.applyStationery(it) },
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
    onAddColor = { newColor -> addPenColor(newColor, settings, onSettingsChange) },
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
 * Secondary Toolbar: a compact, horizontal scrollable row of geometric figures and tools that sits
 * inside the top bar, allowing quick access without shrinking the canvas. It renders as a floating dock
 * surface enclosing only the figures, adapting its width dynamically to the number of visible figures.
 */
private val NON_INKING_TOOLS: Set<EditorTool> = setOf(
    EditorTool.ERASER,
    EditorTool.ERASER_WHOLE,
    EditorTool.HAND,
    EditorTool.SELECT,
    EditorTool.LASSO_SELECT,
    EditorTool.TEXT_SELECT,
    EditorTool.BG_SELECT,
    EditorTool.VERTICAL_SPACE,
    EditorTool.PLAY_OBJECT,
    EditorTool.IMAGE,
)

/**
 * Maps stroke width [width] to its pen-width size label ("S", "M", or "L") based on [widthSlots].
 */
fun sizeLetterFor(width: Float, widthSlots: List<Float>): String {
    val exactIndex = widthSlots.indexOfFirst { kotlin.math.abs(it - width) < 0.01f }
    if (exactIndex in PEN_WIDTH_LABELS.indices) {
        return PEN_WIDTH_LABELS[exactIndex]
    }
    if (widthSlots.isNotEmpty()) {
        val closestIndex = widthSlots.indices.minByOrNull { kotlin.math.abs(widthSlots[it] - width) } ?: 0
        return PEN_WIDTH_LABELS.getOrElse(closestIndex) { "M" }
    }
    return when {
        width < 1.0f -> "S"
        width < 2.5f -> "M"
        else -> "L"
    }
}

/**
 * The Secondary Toolbar dock's height and corner: what its end cap has to match to be one shape, and
 * what the standalone indicator borrows for a shape of its own ([StandaloneToolIndicator]).
 */
internal val TOP_BAR_DOCK_HEIGHT = 40.dp
internal val TOP_BAR_DOCK_CORNER = 20.dp

/** How much smaller the cap's inner corners are than the dock's: it caps one end, it doesn't echo it. */
internal val TOP_BAR_CAP_INNER_CORNER = 8.dp

/**
 * The end cap's shape: the dock's own corner ([TOP_BAR_DOCK_CORNER]) on the end the cap closes, and the
 * smaller [TOP_BAR_CAP_INNER_CORNER] on the side facing the figures. The cap's outer corner has to
 * match the dock's exactly, or the dock's own tone shows through as a crescent between the two curves
 * at the very corner the grey is meant to reach.
 *
 * It is a function rather than two dp values at the call site because the *mirror* is the fiddly part —
 * the rail can be docked to the right, and the cap then closes the dock's right end — so the rule is
 * pinned by a unit test instead of by eye.
 */
internal fun topBarCapShape(capOnLeft: Boolean): RoundedCornerShape {
    val outer = TOP_BAR_DOCK_CORNER
    val inner = TOP_BAR_CAP_INNER_CORNER
    return RoundedCornerShape(
        topStart = if (capOnLeft) outer else inner,
        bottomStart = if (capOnLeft) outer else inner,
        topEnd = if (capOnLeft) inner else outer,
        bottomEnd = if (capOnLeft) inner else outer,
    )
}

/**
 * Which of the indicator's two styles the chrome draws — the one line that makes the move reversible.
 *
 * `true` is the **dock cap** the indicator shipped with: the Secondary Toolbar dock's own end cap,
 * flush with the dock's edge and wearing the dock's corner ([ActiveToolIndicator]). `false` — the
 * current style — lifts the indicator out of the dock into a floating button of its own
 * ([StandaloneToolIndicator]) that stands *between the two toolbars*: in the corner the rail's column and
 * the dock leave between them, sized ([TOP_BAR_INDICATOR_WIDTH]) so the dock starts on the document tabs'
 * line — which from there on holds the figures alone.
 *
 * Both styles stay live code — the branch is compiled either way, so the backup cannot rot — and the
 * choice is a constant rather than a user setting, as asked: flip it and rebuild for the old look.
 */
internal val INDICATOR_AS_DOCK_CAP = false

/** The air between the standalone indicator and the dock it was cut out of. */
private val TOP_BAR_INDICATOR_GAP = 6.dp

/** Air on each side of the indicator's face while it fills the dock's own end as the cap. */
private val TOP_BAR_FACE_PADDING = 10.dp

/** The same, for the standalone pill: its width is spoken for ([TOP_BAR_INDICATOR_WIDTH]), so this is what
 *  is left of that width once the face has its icon, swatch and letter. */
private val TOP_BAR_FACE_PADDING_TIGHT = 5.dp

/**
 * The standalone indicator's own width — and, because the figures' dock starts right after it, the line
 * that dock starts on.
 *
 * Two insets sit between the window's edge and the dock: the 4dp the top bar keeps in front of its leading
 * slot and the 4dp between that slot and the dock (Material 3's own, one on each side of the slot). The
 * document tab strip starts 72dp in — the rail's column ([SideToolbarModernTotalWidth]) plus the tab chip's
 * own 4dp lead-in — so a 64dp slot is exactly what lands the dock on the tabs' line: the bar's content and
 * the tab row below it begin on one vertical line.
 *
 * The width is *fixed* rather than left to the face, so the dock cannot drift with the tool in play (the
 * size letter "S"/"M"/"L" is not the same width in every glyph) — the face is padded to fit inside it
 * instead. It also keeps the pill inside the rail's column, which is what leaves the pill standing in the
 * corner between the two toolbars rather than pushing the dock right.
 */
internal val TOP_BAR_INDICATOR_WIDTH = 64.dp

/** Which end of the figures' dock the standalone indicator stands at. */
internal enum class IndicatorSlot {
    /** The top bar's **leading** slot: the corner between the rail's column and the dock. */
    LEADING,
    /** **Trailing** the dock — for a right-docked rail, its end nearest the Main Toolbar. */
    TRAILING,
}

/**
 * Which side of the figures' dock the standalone indicator belongs on: always the side the Main Toolbar
 * is docked to, so the run always reads rail → indicator → figures rather than the indicator drifting to
 * the far edge from the hand's toolbar. On a left-docked rail that is the corner the rail and the dock
 * leave between them ([TOP_BAR_INDICATOR_WIDTH]), which is also the line the tabs start on.
 *
 * A rule read by both places that can draw it — [EditorTopBar]'s leading slot and [TopBarToolsRow]'s
 * trailing one — so the two cannot disagree about the mirror, and pinned by a unit test instead of by eye
 * (the same reason [topBarCapShape] is a function).
 */
internal fun standaloneIndicatorSlot(railOnLeft: Boolean): IndicatorSlot =
    if (railOnLeft) IndicatorSlot.LEADING else IndicatorSlot.TRAILING

/**
 * The indicator's **face**, shared by its two styles ([ActiveToolIndicator] and
 * [StandaloneToolIndicator]) so they can only differ in *which surface the face is drawn on* — and in how
 * much air that surface gives it ([horizontalPadding]) — never in what the indicator says:
 * - Selected tool (icon)
 * - Selected colour (swatch circle)
 * - Selected stroke size ("S", "M", or "L")
 *
 * The grey behind it is the scheme's `surfaceContainerHighest` role, a surface step and never the
 * accent: the indicator *reports* the live stroke, it is not a tool that can be picked, so the blue a
 * lit-up tool button wears made it read as a second active button beside the figures. Nothing draws the
 * stroke's own thickness either — the size letter reports it through [sizeLetterFor], with room to read
 * it.
 *
 * Tapping it opens the full Colour & Size pop-up ([ColorSizePopup]). The caller supplies the chrome —
 * the modifier chain that paints the surface under the face — because that chain is exactly what
 * differs between a cap flush inside the dock and a button floating on its own, and it supplies the air
 * around the face for the same reason: a cap has the whole dock's width to breathe in, while the
 * standalone pill is held to [TOP_BAR_INDICATOR_WIDTH] so it cannot push the dock off the tabs' line.
 */
@Composable
private fun ToolIndicatorFace(
    ui: EditorUiState,
    styleCallbacks: ToolbarStyleCallbacks,
    modifier: Modifier = Modifier,
    /** Air on each side of the face, inside whatever surface the caller paints. */
    horizontalPadding: Dp = TOP_BAR_FACE_PADDING,
) {
    val isHighlighter = ui.tool == EditorTool.HIGHLIGHTER
    val effectiveColor = if (isHighlighter) ui.highlighterColor else ui.color
    val hasColorAndSize = ui.tool !in NON_INKING_TOOLS
    val sizeLabel = sizeLetterFor(ui.width, styleCallbacks.widthSlots)

    ColorSizePopup(styleCallbacks) { open ->
        Box(
            modifier = modifier
                .clickable(onClick = open)
                .padding(horizontal = horizontalPadding)
                .semantics {
                    contentDescription = if (hasColorAndSize) {
                        "Active tool: ${ui.tool.label}, colour ${colorDisplayName(effectiveColor)}, size $sizeLabel"
                    } else {
                        "Active tool: ${ui.tool.label}"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = ui.tool.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                if (hasColorAndSize) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(effectiveColor))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Text(
                        text = sizeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The indicator's **dock cap** style — the Secondary Toolbar dock's own end, the end nearest the Main
 * Toolbar, rather than a chip floating inside it.
 *
 * It is as tall as the dock ([TOP_BAR_DOCK_HEIGHT]), flush with its edge, and wears the dock's corner
 * ([TOP_BAR_DOCK_CORNER]) on the end it caps, while the corners facing the figures stay smaller so the
 * cap reads as part of the bar rather than as a second bar. The flush, full-height grey is the point:
 * an inset chip leaves a crescent of the dock's own tone around itself, and the toolbar's rounded
 * border then encloses the dock instead of enclosing the indicator.
 *
 * Nothing divides the cap from the figures either: a vertical divider cut the dock in two for a
 * boundary the grey already draws, so the tools simply start a padding-width after the cap.
 *
 * It is kept as the restore path behind [INDICATOR_AS_DOCK_CAP] — the style the indicator wore before
 * it was moved out to [StandaloneToolIndicator] — and is private because that constant is now its only
 * way on screen.
 */
@Composable
private fun ActiveToolIndicator(
    ui: EditorUiState,
    styleCallbacks: ToolbarStyleCallbacks,
    /** True when the cap is the dock's left end, which is where the Main Toolbar is docked. */
    capOnLeft: Boolean = true,
    modifier: Modifier = Modifier,
) {
    ToolIndicatorFace(
        ui = ui,
        styleCallbacks = styleCallbacks,
        modifier = modifier
            .fillMaxHeight()
            .clip(topBarCapShape(capOnLeft))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/**
 * The indicator as a **floating button of its own** — the current style ([INDICATOR_AS_DOCK_CAP] off).
 * It is cut out of the dock and stands between the two toolbars instead of being the first thing *inside*
 * the figures' dock: in the corner the rail's column and the dock leave between them, so the run reads
 * rail → indicator → figures. Its width is what leaves the dock on the document tabs' line below
 * ([TOP_BAR_INDICATOR_WIDTH]), so the toolbar of figures starts exactly where the `Untitled` chip does.
 *
 * Freed of the dock's edge it can be a whole shape: the dock's corner ([TOP_BAR_DOCK_CORNER]) on all
 * four sides — a capsule at [TOP_BAR_DOCK_HEIGHT] — and the dock's tonal and shadow elevation and
 * hairline border, so it reads as one of the floating docks rather than as a bare patch of grey. A
 * [TOP_BAR_INDICATOR_GAP] keeps it visibly its own surface wherever it trails the dock.
 *
 * Its width is [TOP_BAR_INDICATOR_WIDTH], which is what keeps the figures' dock on the document tabs'
 * line: the dock starts where this pill ends (plus Material 3's own gap), so the pill is sized to the
 * corner between the rail's column and that line rather than to its own face. The face is padded tighter
 * ([TOP_BAR_FACE_PADDING_TIGHT]) to fit, and because the width is fixed rather than measured, the dock
 * cannot shift sideways when the tool in play changes the size letter.
 *
 * The face is shared with the cap ([ToolIndicatorFace]): the same icon, live colour and size letter,
 * and the same tap into the Colour & Size pop-up. Only the surface it is drawn on moved.
 */
@Composable
private fun StandaloneToolIndicator(
    ui: EditorUiState,
    styleCallbacks: ToolbarStyleCallbacks,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(TOP_BAR_DOCK_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .width(TOP_BAR_INDICATOR_WIDTH)
            .height(TOP_BAR_DOCK_HEIGHT),
    ) {
        ToolIndicatorFace(
            ui = ui,
            styleCallbacks = styleCallbacks,
            modifier = Modifier.fillMaxSize(),
            horizontalPadding = TOP_BAR_FACE_PADDING_TIGHT,
        )
    }
}

/**
 * Secondary Toolbar: a compact, horizontal scrollable row of geometric figures and tools that sits
 * inside the top bar, allowing quick access without shrinking the canvas. It renders as a floating dock
 * surface adapting its width dynamically to the visible figures.
 *
 * In the current style it encloses the **figures alone**: the active tool indicator was cut out of it
 * and now stands beside it as its own button ([StandaloneToolIndicator]), which on a left-docked rail is
 * the top bar's leading slot — so [EditorTopBar] is what places it, on the tab strip's starting line —
 * and on a right-docked rail trails the dock from here.
 *
 * In the backup **dock cap** style ([INDICATOR_AS_DOCK_CAP] on) the dock's end nearest the Main Toolbar
 * belongs to [ActiveToolIndicator]: it fills that end rather than floating inside the dock (see that
 * composable), so the grey cap and the dock's edge are one shape. Nothing divides the cap from the
 * figures — the grey already draws the boundary.
 *
 * With no visible figures there is nothing left to enclose: the dock is not drawn at all while the
 * indicator is out of it (the backup style still draws the cap, having the indicator inside).
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
    val items = visibleTopBarItems(settings.topBarOrder, settings.topBarHidden, settings.shapeHidden)
    val railOnLeft = settings.toolbarPosition == ToolbarPosition.LEFT
    val styleCallbacks = rememberToolbarStyleCallbacks(ui, surface, settings, onSettingsChange)

    val indicator = @Composable {
        ActiveToolIndicator(ui = ui, styleCallbacks = styleCallbacks, capOnLeft = railOnLeft)
    }
    val standaloneIndicator = @Composable {
        StandaloneToolIndicator(ui = ui, styleCallbacks = styleCallbacks)
    }

    val toolsRow = @Composable {
        Row(
            // In the backup style the cap is flush with the dock's edge, so the air at that end belongs to
            // the tools row rather than to a padding wrapped around both of them; with the indicator out of
            // the dock (the current style) this same padding is what gives both ends their air.
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (item in items) {
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

    val dock = @Composable {
        Surface(
            shape = RoundedCornerShape(TOP_BAR_DOCK_CORNER),
            // The dock is the app's implement colour — the same value the rail and the canvas surround take
            // (see `rememberToolbarColor`), so the tools and the desk are visibly one material.
            color = rememberToolbarColor(),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = modifier
                .wrapContentWidth()
                .height(TOP_BAR_DOCK_HEIGHT),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (INDICATOR_AS_DOCK_CAP) {
                    if (railOnLeft) {
                        indicator()
                        if (items.isNotEmpty()) toolsRow()
                    } else {
                        if (items.isNotEmpty()) toolsRow()
                        indicator()
                    }
                } else if (items.isNotEmpty()) {
                    // The figures alone, so the tools row's own 6dp carries both ends where the cap used
                    // to own one of them.
                    toolsRow()
                }
            }
        }
    }

    if (INDICATOR_AS_DOCK_CAP) {
        // The backup style keeps the indicator inside the dock, so the dock is always worth drawing.
        dock()
    } else if (standaloneIndicatorSlot(railOnLeft) == IndicatorSlot.LEADING) {
        // Leading slot: EditorTopBar draws the indicator there, so all that is left here is the dock —
        // and a dock with no figures in it is nothing at all.
        if (items.isNotEmpty()) dock()
    } else {
        // Trailing the dock: still the end nearest the Main Toolbar, with the gap that says the two are
        // separate surfaces.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TOP_BAR_INDICATOR_GAP),
        ) {
            if (items.isNotEmpty()) dock()
            standaloneIndicator()
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
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(shape)
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
 * the button picks the tool, tapping it again — or a long press — opens the variant menu
 * ([ToolVariantPicker], the same row layout the tool-group pickers use), and while a variant that has
 * something to configure (the scalene kind's angles) is the live one, a pencil sits beside the button
 * to reopen its dialog.
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
    val shape = RoundedCornerShape(8.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(shape)
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
                ToolVariantPicker(
                    heading = heading,
                    labels = kinds,
                    selectedIndex = selectedKind,
                    onPick = { index ->
                        onSelectKind(index)
                        open = false
                    },
                    editableIndex = editableKind,
                    editHint = editorHint,
                    // The scalene cell's pencil picks that kind and opens its angle dialog, so
                    // choosing "set my own angles" stays a single gesture.
                    onEdit = {
                        open = false
                        onSelectKind(editableKind)
                        onEditKind()
                    },
                )
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
    val shape = RoundedCornerShape(8.dp)
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(shape)
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
    val shape = RoundedCornerShape(8.dp)
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(shape)
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
            ToolGroupPicker(group, members, selected) { picked -> onPick(picked); open = false }
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

/**
 * Append [newColor] to the pen palette from the toolbar pop-up's **add colour** swatch — the same
 * de-duplicated, capped append that Settings → Colors performs, so a colour made at the canvas shows
 * up in every picker and in the palette list. The caller selects the new colour afterwards: being
 * asked for a colour and then having to pick it again would be two taps for one intent.
 *
 * A palette already at [AppSettings.MAX_PEN_COLORS] keeps the swatches it has: the new colour is then
 * only *used*, not stored. Evicting a swatch the user made — or refusing the colour they just chose —
 * would both be worse than a one-off colour that simply isn't a swatch yet.
 */
private fun addPenColor(
    newColor: Int,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val opaque = newColor or 0xFF000000.toInt()
    onSettingsChange(
        settings.copy(
            penColors = (settings.penColors + opaque).distinct().take(AppSettings.MAX_PEN_COLORS),
        ),
    )
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
    ui: EditorUiState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    surface: DrawingSurfaceView?,
): ToolbarPagesCallbacks =
    ToolbarPagesCallbacks(
        pageCount = pane.pageCount,
        currentPage = pane.currentPage,
        // A page added or removed moves every bookmark at or after it, so the flags stay on the pages
        // they were put on (a page added goes *after* the one in view, a removal takes the one in view).
        onAddPage = {
            surface?.addPage()
            pane.onBookmarksChange?.invoke(PageBookmarks.insertedAt(pane.bookmarks, pane.currentPage + 1))
        },
        onRemovePage = {
            surface?.removePage()
            pane.onBookmarksChange?.invoke(PageBookmarks.removedAt(pane.bookmarks, pane.currentPage))
        },
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
        bookmarks = pane.bookmarks,
        onBookmarkPage = { ui.bookmarkPage = it },
        outline = pane.outline,
        onGoToPdfPage = { surface?.goToPdfPage(it) },
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
        state.backgroundConfig = visiblePageBackgroundConfig()
        state.pageSize = visiblePageSize()
    }
    state.layers = visibleLayers()
    state.backgroundStyle = visiblePageBackgroundStyle()
    state.backgroundConfig = visiblePageBackgroundConfig()
    state.pageSize = visiblePageSize()
    onHistoryChanged = { u, r -> state.canUndo = u; state.canRedo = r }
    onZoomChanged = { z -> state.zoom = z }
    onPageCountChanged = { n -> state.pageCount = n }
    onPageSelectionChanged = { n -> state.selectedPages = n }
    onPageClipboardChanged = { n -> state.copiedPages = n }
    onCurrentPageChanged = { page ->
        state.currentPage = page
        state.backgroundStyle = visiblePageBackgroundStyle()
        state.backgroundConfig = visiblePageBackgroundConfig()
        state.pageSize = visiblePageSize()
    }
    onScrollChanged = { y, total, vp -> state.scrollY = y; state.contentHeight = total; state.viewportHeight = vp }
    onSelectionChanged = { s -> state.hasSelection = s }
    onSelectionRectChanged = { r -> state.selectionRect = r }
    onTextSelectionChanged = { s -> state.hasTextSelection = s }
    onPdfOutlineChanged = { outline -> state.outline = outline }
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
        // The canvas box doubles as the coordinate space for the floating selection bar: its px are
        // the surface's own view px, so the box the surface reports needs no conversion here.
        var canvasSizePx by remember { mutableStateOf(IntSize.Zero) }
        Box(modifier = Modifier.fillMaxWidth().weight(1f).onSizeChanged { canvasSizePx = it }) {
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
                    it.applyChromeColors(chrome.backdrop, chrome.pageOutline, chrome.selection, chrome.guide)
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
            // The selection's own options ride with the selection instead of sitting at the bottom
            // of the screen, where a hand holding the stylus has to reach for them.
            if (state.hasSelection && state.selectionRect != null) {
                SelectionActionAnchor(
                    pane = state,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    canvasSizePx = canvasSizePx,
                    modifier = Modifier.align(Alignment.TopStart),
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
    /** Open the 2D function plotter: a formula in, a plot onto the page. */
    onInsertGraph: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    onSettings: () -> Unit,
    penDiagnostics: Boolean,
    onTogglePenDiagnostics: () -> Unit,
    onOpenPenParameters: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = "Menu",
                modifier = Modifier.size(20.dp),
            )
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

        // The graph plotter: a formula in, a plot on the page as ordinary ink.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            OutlinedButton(
                onClick = { open = false; onInsertGraph() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Insert graph", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Export the active page alone, as a shareable raster image or a scalable vector file.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { open = false; onExportPagePng() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Page as PNG", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { open = false; onExportPageSvg() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Page as SVG", style = MaterialTheme.typography.labelMedium)
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


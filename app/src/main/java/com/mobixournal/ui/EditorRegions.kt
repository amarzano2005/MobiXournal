package com.mobixournal.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.mobixournal.render.GuideKind
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalSplit
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
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.input.key.key
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
import androidx.compose.foundation.isSystemInDarkTheme
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.rememberCanvasChromeColors
import com.mobixournal.ui.theme.rememberToolbarColor

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
            if (settings != null && onSettingsChange != null && settings.toolbarLayout != ToolbarLayout.COMPACT) {
                TopBarToolsRow(
                    ui = ui,
                    pane = pane,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                )
            } else {
                TabStrip(tabs, modifier = Modifier.fillMaxWidth())
            }
        },
        modifier = modifier.height(52.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
        actions = {
            val isDark = isSystemInDarkTheme()
            val glassColor = ModernTheme.glassSurface(isDark)
            val hairlineColor = if (isDark) ModernTheme.hairlineBorder else Color(0f, 0f, 0f, 0.12f)

            // Order is deliberate: split view sits directly right of the search button, then the
            // paired undo/redo. The two glyph-shaped file actions follow — a compact quick Export PDF
            // and then Save, which lands the most-used of the two under the thumb at the far right —
            // so neither costs a trip into the overflow menu.
            SearchControls(pane)
            Surface(
                shape = CircleShape,
                color = glassColor,
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(ModernTheme.hairlineStrokeWidthDp, hairlineColor),
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                IconButton(onClick = onToggleSplitView, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.VerticalSplit,
                        contentDescription = if (splitView) "Close split view" else "Split view",
                        tint = if (splitView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Surface(
                shape = ModernTheme.capsuleShape,
                color = glassColor,
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(ModernTheme.hairlineStrokeWidthDp, hairlineColor),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pane.surface?.undo() }, enabled = !pane.isReadOnly && pane.canUndo, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { pane.surface?.redo() }, enabled = !pane.isReadOnly && pane.canRedo, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo", modifier = Modifier.size(20.dp))
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = glassColor,
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(ModernTheme.hairlineStrokeWidthDp, hairlineColor),
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                IconButton(onClick = onExportPdf, enabled = !pane.isReadOnly, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.PictureAsPdf,
                        contentDescription = "Export PDF",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = glassColor,
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(ModernTheme.hairlineStrokeWidthDp, hairlineColor),
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                IconButton(onClick = onSave, enabled = !pane.isReadOnly, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            OverflowMenu(
                isReadOnly = pane.isReadOnly,
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
        val isDark = isSystemInDarkTheme()
        val glassColor = ModernTheme.glassSurface(isDark)
        val hairlineColor = if (isDark) ModernTheme.hairlineBorder else Color(0f, 0f, 0f, 0.12f)
        Surface(
            shape = CircleShape,
            color = glassColor,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(ModernTheme.hairlineStrokeWidthDp, hairlineColor),
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            IconButton(
                onClick = { pane.searchOpen = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(20.dp),
                )
            }
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
                    Icons.Outlined.Search,
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
                    Icons.Outlined.KeyboardArrowUp,
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
                    Icons.Outlined.KeyboardArrowDown,
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
                    Icons.Outlined.Close,
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
    modifier: Modifier = Modifier,
) {
    val surface = pane.surface
    SideToolbar(
        modifier = modifier,
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
            onSettingsChange(settings.copy(guideKind = it, guidePose = null))
            surface?.placeGuide(it)
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
        onShowGraph = { ui.showGraphDialog = true },
        horizontal = settings.toolbarLayout == ToolbarLayout.COMPACT,
        showStemSection = settings.toolbarLayout == ToolbarLayout.COMPACT,
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
                    } else if (item.id == "graph") {
                        // The function plotter sits in the tools row itself: it is a one-tap dialog, not
                        // a tool, so it needs no pop-up of its own — just the button that opens it.
                        CompactIconButton(
                            contentDescription = "Insert graph",
                            modifier = Modifier.size(32.dp),
                            onClick = { ui.showGraphDialog = true },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ShowChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else if (item.id == "guides") {
                        CompactGuidePopupButton(
                            kind = settings.guideKind,
                            onKind = {
                                onSettingsChange(settings.copy(guideKind = it, guidePose = null))
                                pane.surface?.placeGuide(it)
                            },
                        )
                    }
                }
            }
        }
    }

    val isDark = isSystemInDarkTheme()
    val glassColor = ModernTheme.glassSurface(isDark)
    val hairlineColor = if (isDark) ModernTheme.hairlineBorder else Color(0f, 0f, 0f, 0.12f)

    val dock = @Composable {
        Surface(
            shape = ModernTheme.capsuleShape,
            color = glassColor,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(ModernTheme.hairlineStrokeWidthDp, hairlineColor),
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
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.dp, bottom = 1.dp)
                    .size(10.dp),
            )
            ToolbarMenu(expanded = open, onDismissRequest = { open = false }) {
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
                    Icons.Outlined.Edit,
                    contentDescription = editorHint,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
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
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.dp, bottom = 1.dp)
                    .size(10.dp),
            )
        }
        ToolbarMenu(expanded = open, onDismissRequest = { open = false }) {
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
    onReadOnlyChanged = { ro -> state.isReadOnly = ro }
    state.isReadOnly = isReadOnly
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
    // Remember where an instrument was posed, so it comes back there after a restart. Only the
    // instruments qualify: the ruling snap and the chemistry lattice re-derive their anchor from the
    // page. Writing only on a real change also keeps the place/restore pass from echoing a settings
    // write back onto itself.
    onGuideChanged = { pose ->
        val remembered = pose?.takeIf { it.isRememberable }?.let { GuidePose(guideDrag.page, it) }
        val settings = getSettings()
        if (remembered != settings.guidePose) onSettingsChange(settings.copy(guidePose = remembered))
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
    Box(
        modifier = modifier.pointerInput(index) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                onActivePane(index)
                // Richiedi il focus per ricevere i tasti
                state.surface?.requestFocus()
            }
        },
    ) {
        // the surface's own view px, so the box the surface reports needs no conversion here.
        var canvasSizePx by remember { mutableStateOf(IntSize.Zero) }
        Box(modifier = Modifier.fillMaxSize().onSizeChanged { canvasSizePx = it }) {
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
            // of the screen, where a hand holding the stylus has to reach for them. The same bar also
            // stands in for Paste when nothing is selected (see [SelectionActionAnchor]).
            if (state.selectionRect != null || canPasteOnCanvas(state, ui)) {
                SelectionActionAnchor(
                    pane = state,
                    ui = ui,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    canvasSizePx = canvasSizePx,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
        if (!ui.fullPage && settings.toolbarLayout != ToolbarLayout.COMPACT) {
            val railInset = SideToolbarModernTotalWidth
            val startPadding = if (settings.toolbarPosition == ToolbarPosition.LEFT) railInset else 8.dp
            val endPadding = if (settings.toolbarPosition == ToolbarPosition.RIGHT) railInset else 8.dp
            TabStrip(
                tabs[index.coerceIn(tabs.indices)],
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = startPadding, end = endPadding, top = 54.dp),
            )
        }
    }
}

/** The top-bar overflow ("hamburger") menu: compact quick file actions, PDF row, pen subsection, and settings. */
@Composable
private fun OverflowMenu(
    isReadOnly: Boolean = false,
    settings: AppSettings?,
    onSelectPenPreset: (PenPreset) -> Unit,
    onOpen: () -> Unit,
    onNewTab: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onImportPdf: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    onSettings: () -> Unit,
    penDiagnostics: Boolean,
    onTogglePenDiagnostics: () -> Unit,
    onOpenPenParameters: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val glassColor = ModernTheme.glassSurface(isDark)
    val hairlineColor = if (isDark) ModernTheme.hairlineBorder else Color(0f, 0f, 0f, 0.12f)
    Box {
        Surface(
            shape = CircleShape,
            color = glassColor,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(ModernTheme.hairlineStrokeWidthDp, hairlineColor),
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            IconButton(
                onClick = { open = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.Menu,
                    contentDescription = "Menu",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        ToolbarMenu(
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
                icon = Icons.AutoMirrored.Outlined.NoteAdd,
                label = "New",
                onClick = { open = false; onNewTab() },
                modifier = Modifier.weight(1f),
            )
            MenuQuickAction(
                icon = Icons.Outlined.FileOpen,
                label = "Open",
                onClick = { open = false; onOpen() },
                modifier = Modifier.weight(1f),
            )
            MenuQuickAction(
                icon = Icons.Outlined.Save,
                label = "Save",
                enabled = !isReadOnly,
                onClick = { open = false; onSave() },
                modifier = Modifier.weight(1f),
            )
            MenuQuickAction(
                icon = Icons.Outlined.SaveAs,
                label = "Save As",
                enabled = !isReadOnly,
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
                enabled = !isReadOnly,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import PDF", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { open = false; onExportPdf() },
                enabled = !isReadOnly,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export PDF", style = MaterialTheme.typography.labelMedium)
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
                enabled = !isReadOnly,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Page as PNG", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { open = false; onExportPageSvg() },
                enabled = !isReadOnly,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        Icons.Outlined.Tune,
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
                    Icons.Outlined.BugReport,
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
                        Icons.Outlined.Check,
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
            leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
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
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.38f
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
    }
}


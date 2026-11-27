package com.mobixournal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import com.mobixournal.design.ToolGlyph
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.QuickAction
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.HudSlot
import com.mobixournal.render.PageSizeStep
import com.mobixournal.render.PlaceKind
import com.mobixournal.render.Placement
import com.mobixournal.render.SearchStatus
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import com.mobixournal.render.applyChromeColors
import com.mobixournal.render.replaceMathBlock
import com.mobixournal.render.replaceShapeElement
import com.mobixournal.render.repaintAfterResize
import com.mobixournal.render.repaintForExtent
import com.mobixournal.render.setChromeInsets
import com.mobixournal.render.resetZoom
import com.mobixournal.render.zoomIn
import com.mobixournal.render.zoomOut
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.ui.theme.DocumentRed
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.bandClosingEdge
import com.mobixournal.ui.theme.frameClosingEdge
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberCanvasChromeColors
import com.mobixournal.ui.theme.rememberChromeColor
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberClosingArgument
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor
import com.mobixournal.ui.theme.rememberGlassSurfaceColor
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
    /**
     * Every pane's tab session, in pane order. Split view draws one strip lane per pane from this;
     * [tabs] stays the focused pane's session for the single-pane and Compact layouts.
     */
    paneTabs: List<TabsUiState> = emptyList(),
    settings: AppSettings? = null,
    onSettingsChange: ((AppSettings) -> Unit)? = null,
    onOpen: () -> Unit,
    onNewTab: () -> Unit,
    onSave: () -> Unit,
    /** Ask the system for a PDF; its pages are chosen once the file has been read. */
    onImportPdf: () -> Unit,
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    /** **Save portable copy**: the copy embeds what this notebook only names ([OverflowMenu]). */
    onSavePortableCopy: () -> Unit = {}, onShare: (ShareFormat) -> Unit = {}, onSaveAsFormat: (com.mobixournal.format.SaveFormat) -> Unit = {},
    splitView: Boolean,
    onToggleSplitView: () -> Unit,
    audio: AudioUiState = AudioUiState(),
    modifier: Modifier = Modifier,
) {
    // The rail can only be docked to a vertical edge now, so the spacer that keeps the top bar's
    // content clear of it is simply needed whenever that edge is the left one.
    val railOnLeft = (settings?.toolbarPosition ?: ToolbarPosition.LEFT) == ToolbarPosition.LEFT
    val isDark = chromeIsDark()
    val glassColor = rememberGlassSurfaceColor()

    // **One header, one slab — and no line across it.** In the Standard layout the document tabs are this
    // bar's *second row*, inside the same `Surface`, and neither the slab nor the seam between its rows is
    // drawn: the header is one continuous surface of its own colour, closed by its own shadow, and its two
    // rows are told apart by their content (tools above, chips below) rather than by an edge.
    //
    // It used to wear a `BorderStroke` rim and a hairline *inside* itself between the rows
    // (`Modifier.topHairline`): on the OLED dark scheme both read as grey lines across the window, banding
    // what the design calls a single slab by three grey rules (removed 2026-10-13; `docs/chrome.md` owns the
    // rule, and the geometry is what a guard pins, not the ink).
    //
    // The tabs are inside the bar rather than on a slab of their own because that arrangement left the
    // header's bottom edge with a step: the slab had to be held out of the rail's column, so its left end
    // stopped where the rail's lane began and the header was a tab row shallower over the lane than over the
    // page — a notch of bare desk over the rail. A row of *this* bar cannot notch. The rail's lane therefore
    // starts below the whole slab (see [EditorBody]) — `RailSizing.kt` counts the tab row in.
    // A caller with no settings to read has nowhere to put a second row, so its tabs stay in the
    // title slot; the editor itself gives them a strip of their own under the tools.
    val tabsInline = settings == null || onSettingsChange == null
    val stripTabs = paneTabs.firstOrNull { it.focused } ?: tabs
    val laneEdge = if (settings == null || ui.fullPage) 0.dp else SideToolbarModernTotalWidth
    val docBandColor = rememberChromeColor().copy(alpha = if (isDark) 0.28f else 0.45f)
    val searchProgress = rememberSearchOpenProgress(pane.searchOpen)
    var row1RightWindowX by remember { mutableFloatStateOf(0f) }
    var searchAnchorRightWindowX by remember { mutableFloatStateOf(0f) }
    val splitTabProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (splitView && paneTabs.size >= 2 && !ui.fullPage) 1f else 0f,
        animationSpec = chromeSlideSpring(),
        label = "SplitTabFade",
    )
    Surface(
        color = glassColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().frameClosingEdge(laneEdge, railOnLeft)) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(docBandColor)
                        .stableStatusBarsPadding()
                        .height(HEADER_DOC_ROW_HEIGHT)
                        .onGloballyPositioned { coords ->
                            row1RightWindowX = coords.positionInWindow().x + coords.size.width
                        },
                ) {
                    TopAppBar(
                        navigationIcon = {
                            if (settings != null && !railOnLeft && !ui.fullPage) {
                                Spacer(Modifier.width(4.dp))
                            }
                        },
                        title = {
                            if (!splitView || paneTabs.size < 2) {
                                TabStrip(stripTabs, modifier = Modifier.fillMaxWidth())
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                        actions = {
                            EditorDocumentActions(
                                ui = ui,
                                pane = pane,
                                stripTabs = stripTabs,
                                settings = settings,
                                onSettingsChange = onSettingsChange,
                                onOpen = onOpen,
                                onNewTab = onNewTab,
                                onSave = onSave,
                                onImportPdf = onImportPdf,
                                onExportPagePng = onExportPagePng,
                                onExportPageSvg = onExportPageSvg,
                                onSavePortableCopy = onSavePortableCopy,
                                onShare = onShare,
                                onSaveAsFormat = onSaveAsFormat,
                                splitView = splitView,
                                onToggleSplitView = onToggleSplitView,
                                searchProgressProvider = { searchProgress.value },
                                onSearchAnchorRightWindowX = { searchAnchorRightWindowX = it },
                            )
                        },
                    )
                    val endPaddingPx = if (searchAnchorRightWindowX > 0f && row1RightWindowX > searchAnchorRightWindowX) {
                        (row1RightWindowX - searchAnchorRightWindowX).toInt()
                    } else {
                        0
                    }
                    SearchBarOverlay(
                        pane = pane,
                        progressProvider = { searchProgress.value },
                        endPaddingPx = endPaddingPx,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
                if (!tabsInline && !ui.fullPage) {
                    EditorToolsStripRow(
                        ui = ui,
                        pane = pane,
                        settings = settings!!,
                        onSettingsChange = onSettingsChange!!,
                        audio = audio,
                        railOnLeft = railOnLeft,
                    )
                }
                if (splitView && paneTabs.size >= 2 && !ui.fullPage) {
                    SplitTabRow(
                        tabs = paneTabs,
                        fraction = ui.splitFraction,
                        startInset = if (railOnLeft && settings != null) SideToolbarModernTotalWidth else 0.dp,
                        endInset = if (!railOnLeft && settings != null) SideToolbarModernTotalWidth else 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = splitTabProgress.value },
                    )
                }
            }
        }
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
        railOrder = settings.railOrder,
        railHidden = settings.railHidden,
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
        layerCallbacks = toolbarLayerCallbacks(surface, pane),
        pageCallbacks = toolbarPagesCallbacks(pane, ui, settings, onSettingsChange, surface),
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
    // A colour and a width are one-knob presets, so the rail goes through the same entry a
    // pen-tip-ring slot does (`applyToolPreset`): both write the tool's own slot, both persist the
    // same fields, and neither can invent a state the other could not reach.
    onColor = { newColor ->
        applyToolPreset(
            ToolPreset("rail", "Rail", ui.tool, newColor, ui.width, ui.lineStyle),
            ui, surface, settings, onSettingsChange,
        )
    },
    palette = rememberColorPaletteState(settings, onSettingsChange),
    onRedefineCustom = { newColor -> redefineCustomColor(newColor, ui, surface, settings, onSettingsChange) },
    onAddColor = { newColor -> addPenColor(newColor, settings, onSettingsChange) },
    width = ui.width,
    onWidth = { newWidth ->
        applyToolPreset(
            ToolPreset("rail", "Rail", ui.tool, ui.color, newWidth, ui.lineStyle),
            ui, surface, settings, onSettingsChange,
        )
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
        ActiveToolIndicator(
            ui = ui,
            styleCallbacks = styleCallbacks,
            capOnLeft = railOnLeft,
            triangleKind = settings.triangleKind,
            trapezoidKind = settings.trapezoidKind,
        )
    }

    val density = LocalDensity.current
    var availableWidthDp by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val collapsed = shouldCollapseTopBarItems(availableWidthDp, items)

    val renderItem = @Composable { item: RailItem ->
        if (isDividerId(item.id)) {
            TopBarToolDivider(air = 4.dp)
        } else {
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
                    kindIcons = if (triangle) {
                        TriangleKind.values().map(::triangleKindIcon)
                    } else {
                        TrapezoidKind.values().map(::trapezoidKindIcon)
                    },
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
                    showEditor = ui.tool == tool &&
                        (if (triangle) settings.triangleKind == TriangleKind.SCALENE
                        else settings.trapezoidKind == TrapezoidKind.SCALENE),
                    editorHint = if (triangle) "Customize scalene angles" else "Customize scalene angles (trapezoid)",
                    onEditKind = {
                        if (triangle) ui.showScaleneAnglesDialog = true else ui.showTrapezoidAnglesDialog = true
                    },
                    onClick = { surface?.activateTool(tool, ui, settings, onSettingsChange) },
                )
            } else if (item.id == "polygons") {
                PolygonSlotButton(
                    active = ui.tool == EditorTool.POLYGON,
                    sides = settings.polygonSides,
                    onPick = { sides ->
                        val updated = settings.copy(polygonSides = sides)
                        onSettingsChange(updated)
                        surface?.activateTool(EditorTool.POLYGON, ui, updated, onSettingsChange)
                    },
                    onClick = { surface?.activateTool(EditorTool.POLYGON, ui, settings, onSettingsChange) },
                )
            } else if (item.id == "guides") {
                CompactGuidePopupButton(
                    kind = settings.guideKind,
                    onKind = {
                        onSettingsChange(settings.copy(guideKind = it, guidePose = null))
                        pane.surface?.placeGuide(it)
                    },
                )
            } else {
                val group = secondaryToolGroup(item.id)
                if (group != null) {
                    CompactTopBarToolButton(
                        group = group,
                        members = group.tools,
                        selected = group.selected(settings.toolGroupSelections),
                        active = ui.tool in group.tools,
                        onTool = { tool -> surface?.activateTool(tool, ui, settings, onSettingsChange) },
                        onPick = { picked ->
                            val base = settings.copy(
                                toolGroupSelections = group.withSelection(settings.toolGroupSelections, picked),
                            )
                            surface?.activateTool(picked, ui, base, onSettingsChange)
                        },
                        triangleKind = settings.triangleKind,
                        trapezoidKind = settings.trapezoidKind,
                    )
                }
            }
        }
    }

    val toolsRow = @Composable {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (collapsed) {
                for (entry in compactTopBarEntries(items)) {
                    when (entry) {
                        is CompactTopBarEntry.Single -> renderItem(entry.item)
                        is CompactTopBarEntry.Group -> {
                            val group = entry.group
                            CompactTopBarToolButton(
                                group = group,
                                members = group.tools,
                                selected = group.selected(settings.toolGroupSelections),
                                active = ui.tool in group.tools,
                                onTool = { tool -> surface?.activateTool(tool, ui, settings, onSettingsChange) },
                                onPick = { picked ->
                                    val base = settings.copy(
                                        toolGroupSelections = group.withSelection(settings.toolGroupSelections, picked),
                                    )
                                    surface?.activateTool(picked, ui, base, onSettingsChange)
                                },
                                triangleKind = settings.triangleKind,
                                trapezoidKind = settings.trapezoidKind,
                            )
                        }
                    }
                }
            } else {
                for (item in items) {
                    renderItem(item)
                }
            }
        }
    }

    val glassColor = rememberGlassSurfaceColor()
    val dock = @Composable {
        if (INDICATOR_AS_DOCK_CAP) {
            Surface(
                shape = ModernTheme.capsuleShape,
                color = glassColor,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .wrapContentWidth()
                    .height(TOP_BAR_DOCK_HEIGHT)
                    .floatingRim(ModernTheme.capsuleCornerRadiusDp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (railOnLeft) {
                        indicator()
                        if (items.isNotEmpty()) {
                            TopBarToolDivider()
                            toolsRow()
                        }
                    } else {
                        if (items.isNotEmpty()) {
                            toolsRow()
                            TopBarToolDivider()
                        }
                        indicator()
                    }
                }
            }
        } else if (items.isNotEmpty()) {
            Row(
                modifier = Modifier.wrapContentWidth().height(TOP_BAR_DOCK_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                toolsRow()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged {
                if (it.width > 0) availableWidthDp = with(density) { it.width.toDp().value }
            },
        contentAlignment = if (railOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        if (INDICATOR_AS_DOCK_CAP) {
            dock()
        } else if (items.isNotEmpty()) {
            dock()
        }
    }
}

@Composable
private fun CompactSingleToolButton(
    tool: EditorTool,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else rememberChromeInk()
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
            // The rail's own glyph size ([ToolIconSize]): one set of vectors, one weight, so the
            // figures dock cannot read as thinner than the rail beside it.
            modifier = Modifier.size(ToolIconSize),
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
    kindIcons: List<ImageVector>,
    selectedKind: Int,
    editableKind: Int,
    onSelectKind: (Int) -> Unit,
    showEditor: Boolean,
    editorHint: String,
    onEditKind: () -> Unit,
    onClick: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val tint = if (active) MaterialTheme.colorScheme.primary else rememberChromeInk()
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
                val displayIcon = kindIcons.getOrElse(selectedKind) { tool.icon }
                Icon(
                    displayIcon,
                    contentDescription = "Tool: ${tool.label}",
                    tint = tint,
                    modifier = Modifier.size(ToolIconSize),
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
            ToolbarMenu(expanded = open, onDismissRequest = { open = false }, fill = com.mobixournal.ui.theme.rememberPopupPanelColor()) {
                ToolVariantPicker(
                    heading = heading,
                    labels = kinds,
                    icons = kindIcons,
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
    triangleKind: TriangleKind = TriangleKind.ISOSCELES,
    trapezoidKind: TrapezoidKind = TrapezoidKind.ISOSCELES,
) {
    var open by remember { mutableStateOf(false) }
    val tint = if (active) MaterialTheme.colorScheme.primary else rememberChromeInk()
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
                toolDisplayIcon(selected, triangleKind, trapezoidKind),
                contentDescription = "Tool: ${selected.label}",
                tint = tint,
                modifier = Modifier.size(ToolIconSize),
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
        ToolbarMenu(expanded = open, onDismissRequest = { open = false }, fill = com.mobixournal.ui.theme.rememberPopupPanelColor()) {
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
internal fun toolbarLayerCallbacks(surface: DrawingSurfaceView?, pane: PaneState): ToolbarLayerCallbacks =
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
internal fun toolbarPagesCallbacks(
    pane: PaneState,
    ui: EditorUiState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    surface: DrawingSurfaceView?,
): ToolbarPagesCallbacks {
    var liveSettings = settings
    return ToolbarPagesCallbacks(
        pageCount = pane.pageCount,
        currentPage = pane.currentPage,
        // A page added or removed moves every bookmark at or after it, so the flags stay on their pages.
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
        // The quick size buttons step by the user's own amount, in the sheet's own paper unit.
        sizeStepPt = PageSizeStep.pt(settings.pageSizeStepMm),
        // The quick size is read in the page format's own unit, so its step label follows the dialog.
        sizeUnit = settings.pageSizeUnit,
        onPageSizeUnit = { unit ->
            liveSettings = liveSettings.copy(pageSizeUnit = unit)
            onSettingsChange(liveSettings)
        },
        defaultPageSize = settings.defaultPageWidthPt to settings.defaultPageHeightPt,
        onSaveAsDefault = { w, h ->
            liveSettings = liveSettings.copy(defaultPageWidthPt = w, defaultPageHeightPt = h)
            onSettingsChange(liveSettings)
        },
        bookmarks = pane.bookmarks,
        onBookmarkPage = { ui.bookmarkPage = it },
        // The sheet's graphics ride the page menu, so its style is the page in view's own.
        backgroundStyle = pane.backgroundStyle,
        onBackgroundStyle = { surface?.setPageBackgroundStyle(it) },
        backgroundConfig = pane.backgroundConfig,
        onBackgroundConfig = { surface?.setPageBackgroundConfig(it) },
        onStationery = { surface?.applyStationery(it) },
    )
}

/**
 * Applies the initial tool and style settings to the drawing surface.
 */
private fun DrawingSurfaceView.applyInitialStyle(ui: EditorUiState, settings: AppSettings) {
    applyLiveStyle(ui)
    applySettings(settings)
}

/**
 * Pushes the **live** pen onto a canvas that has just become the focused pane.
 *
 * The rail, the colour pop-ups and the keyboard shortcuts all write through [EditorUiState], but
 * they only reach the surface of the pane that was active at the time. In split view the other
 * pane keeps whatever tool and style it had when it was last touched, so switching focus and then
 * drawing produced the *previous* tool — the toolbar said highlighter while the ink came out as a
 * pen. Touching a pane now adopts the shared style before that same touch reaches the canvas.
 */
private fun DrawingSurfaceView.applyLiveStyle(ui: EditorUiState) {
    applyTool(ui.tool)
    colorArgb = ui.color
    baseWidthPt = ui.width
    currentLineStyle = ui.lineStyle
}

/**
 * Binds the surface's state callbacks to the pane state holder.
 *
 * A selection appearing is reported to the pane and nothing else: a placement tool keeps its slot, so an
 * insertion does not move the tool under the hand (`render/PlacementPick.kt`).
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
    onCurrentPageChanged = { page ->
        state.currentPage = page
        state.pdfTextEntryMissed = false
        state.backgroundStyle = visiblePageBackgroundStyle()
        state.backgroundConfig = visiblePageBackgroundConfig()
        state.pageSize = visiblePageSize()
    }
    onScrollChanged = { y, total, vp, x -> state.scrollY = y; state.contentHeight = total; state.viewportHeight = vp; state.scrollX = x }
    onSelectionChanged = { s -> state.hasSelection = s }
    onSelectionRectChanged = { r -> state.selectionRect = r; state.selectionLiveTick++ }
    onTextSelectionChanged = { s -> state.hasTextSelection = s }
    onTextSelectionReport = { r ->
        state.textSelectionRect = r?.union
        state.textSelectionSpans = r?.spans ?: emptyList()
        state.textSelectionActiveSpan = r?.active
        state.textSelectionPhase = r?.phase ?: com.mobixournal.render.PdfTextActionPhase.ABSENT
        // The range exists, so the missed-press pill has nothing left to explain.
        if (r != null) state.pdfTextEntryMissed = false
    }
    pdfTextPress.onMissed = { state.pdfTextEntryMissed = true }
    onPdfOutlineChanged = { outline -> state.outline = outline }
    pageCommands.onOutlineChanged = { state.documentVersion++ }
    onClipboardChanged = { c -> state.hasClipboard = c }
    onBackgroundRegionChanged = { r -> state.hasBackgroundRegion = r }
    onSearchChanged = { s -> state.searchCurrent = s.current; state.searchTotal = s.total }
    onSearchIndexingChanged = { active, progress ->
        state.searchIndexing = active
        state.searchIndexingProgress = progress
    }
    onDocumentLoaded = {
        state.documentVersion++
        state.pdfTextEntryMissed = false
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
    onQuickAction = { action ->
        when (action) {
            // Into the eraser the rail's slot stands for, and back to whatever tool the user was
            // holding — a highlighter, a figure, a selection — never a hard-coded pen.
            QuickAction.TOGGLE_ERASER -> {
                val previous = ui.tool
                ui.toggleTool(ui.eraserSwitchTarget(getSettings()))
                activateTool(ui.tool, ui, getSettings(), onSettingsChange, previous)
            }
            QuickAction.TOGGLE_SELECT -> {
                val previous = ui.tool
                ui.toggleTool(EditorTool.SELECT)
                activateTool(ui.tool, ui, getSettings(), onSettingsChange, previous)
            }
            QuickAction.TOGGLE_FULL_PAGE -> {
                ui.fullPage = !ui.fullPage
                applyTool(ui.tool)
            }
            else -> applyTool(ui.tool)
        }
    }
    // A ring release commits through the same entries the rail uses: applyToolPreset for a colour
    // or a width, activateTool for a tool — a HUD choice can never mean something the user could
    // not have set by hand.
    onHudPick = { slot ->
        onActivePane(index)
        val settings = getSettings()
        val commitInk = { colour: Int, width: Float ->
            applyToolPreset(
                ToolPreset("radial-hud", "Ring", ui.tool, colour, width, ui.lineStyle),
                ui, this, settings, onSettingsChange,
            )
        }
        when (slot) {
            is HudSlot.Colour -> commitInk(slot.argb, ui.width)
            is HudSlot.Width -> commitInk(ui.color, slot.pt)
            // A face this build has no tool for (a name from another version) commits nothing.
            is HudSlot.ToolFace -> editorToolFor(slot.tool, settings)?.let { tool ->
                activateTool(tool, ui, settings, onSettingsChange)
            }
        }
    }
    onPlace = { kind, placement ->
        onActivePane(index)
        when (kind) {
            PlaceKind.TEXT -> ui.textPlacement = placement
            // A formula under the LaTeX slot's tap is **picked**, not stacked on (`openPlacedTex`): the slot
            // stays in hand after it places, so the tap that means "this one" opens that formula's editor
            // instead of asking for the source a second time.
            PlaceKind.TEX -> if (!ui.openPlacedTex(placement)) ui.texPlacement = placement
            PlaceKind.IMAGE -> onPickImage(placement)
            // The **Plot** slot's press on paper: the box the graph dialog fills. A press on a plot it made
            // never reaches here — that press is the pick, which selects the plot on the way down
            // (`beginPlacementPress`).
            PlaceKind.PLOT -> ui.plotPlacement = placement
        }
    }
    onLivingDoubleTap = { target ->
        onActivePane(index)
        ui.openLivingEditor(target)
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

    /** Switch to the PEN with a fixed colour, keeping the pen/highlighter state consistent. */
    fun switchToPen(color: Int) {
        val next = ui.switchToolTo(EditorTool.PEN, getSettings())
        ui.color = color
        colorArgb = color
        baseWidthPt = ui.width
        applyTool(EditorTool.PEN)
        onSettingsChange(next.withColorUsed(color))
    }

    onKeyPressed = { char ->
        routeShortcutKey(char, ui, getSettings(), onSettingsChange, ::switchToPen)
    }
}

/**
 * One pane: its canvas, and the surface's callbacks writing into *that* pane's [PaneState] — never the
 * active one — so a background pane keeps its own page, zoom and undo state up to date while the toolbar
 * drives the other. A touch anywhere in the pane (observed on the initial pass, so the canvas still gets
 * the event) hands it focus. Its document tabs are not here: they are the top bar's own second row
 * ([EditorTopBar]), which is the active pane's at any moment.
 */
@Composable
fun EditorPaneView(
    index: Int,
    ui: EditorUiState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onActivePane: (Int) -> Unit,
    onSurfaceCreated: (Int, DrawingSurfaceView) -> Unit,
    onPickImage: (Placement) -> Unit,
    /**
     * What the pane draws when it holds no document: the entry screen, **in that half**, so the notebooks
     * closed out of a split can be reopened straight back into it. Null while the pane has a tab, and the
     * pane then builds its canvas as it always did.
     */
    emptyEntry: (@Composable (Modifier) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state = ui.panes[index]
    val chrome = rememberCanvasChromeColors()
    val latestSettings = rememberUpdatedState(settings)
    // A touch anywhere in the pane — an entry card as much as the canvas — hands it focus on the initial
    // pass, before the card or the canvas handles the event, so what the touch does already belongs here.
    val paneFocus = Modifier.pointerInput(index) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onActivePane(index)
            // The touch is about to reach the canvas, so the focused pane must already hold the
            // tool and style the chrome is showing (see [applyLiveStyle]).
            state.surface?.applyLiveStyle(ui)
            // Request focus so key events arrive only when we are not editing an element or searching
            if (ui.editingCircuit == null && ui.editingPlot == null && ui.editingTableCell == null &&
                ui.editingShape == null && !state.searchOpen) state.surface?.requestFocus()
        }
    }
    val insetPadding = ApplyPaneChromeInsets(state, index)
    if (index == 1) DisposableEffect(Unit) { onDispose { SplitStandInBus.pane1Ready = false } }
    if (emptyEntry != null) {
        if (index == 1) SplitStandInBus.pane1Ready = true
        Box(modifier = modifier.then(paneFocus)) { emptyEntry(Modifier.fillMaxSize().padding(insetPadding)) }
        return
    }
    Column(
        modifier = modifier.then(paneFocus),
    ) {
        // The document tabs are the top bar's own second row ([EditorTopBar]) — one slab, with the
        // tools row above them — and in full-page mode there is no bar at all, so this pane is the
        // canvas and nothing else. No chrome sits on the page: see [EditorBody] for why that matters.
        // the surface's own view px, so the box the surface reports needs no conversion here.
        var canvasSizePx by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { size ->
                canvasSizePx = size
                // The pane changed size with no touch reaching the canvas — a split-divider drag, a pane
                // handed back to the single-pane layout, or the page-list lane leaving the row. The canvas
                // cannot hear about it through `onTouchEvent`, and `surfaceChanged` is not guaranteed
                // either, so it is told here — with **the size this box has just been placed at**, because
                // the platform view is laid out a moment later and a canvas that rebuilt itself from its
                // own width painted the first frame of the new extent with the old fit (a black strip
                // beside the page). See `render/DrawingSurfaceResize.kt` for the deferred repaint too.
                state.surface?.repaintForExtent(size.width, size.height)
            },
        ) {
            var surfaceReady by remember(index) { mutableStateOf(false) }
            val initChrome = LocalChromeInsetController.current.snapshotForPane(index, LocalSplitView.current)
            AndroidView(
                factory = { ctx ->
                    DrawingSurfaceView(ctx).also {
                        if (index == 1) it.setZOrderMediaOverlay(true)
                        it.setChromeInsets(initChrome.leftPx, initChrome.topPx, initChrome.rightPx, initChrome.bottomPx, false)
                        it.onFirstFrameDrawn = { surfaceReady = true; if (index == 1) SplitStandInBus.pane1Ready = true }
                        it.applyInitialStyle(ui, settings)
                        it.bindTo(state)
                        it.bindEditorActions(
                            ui, index, onActivePane, onPickImage,
                            { latestSettings.value }, onSettingsChange,
                        )
                        it.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                                it.repaintAfterResize()
                            }
                        }
                        // The diagnostics panel is fed straight from the canvas it reports on.
                        it.diagnostics.onSnapshot = { lines -> state.penDebugLines = lines }
                        state.surface = it
                        onSurfaceCreated(index, it)
                    }
                },
                update = {
                    it.applyChromeColors(
                        chrome.backdrop, chrome.pageOutline, chrome.selection, chrome.guide,
                        chrome.floating, chrome.ink,
                    )
                    it.setPenDebug(ui.penDiagnostics)
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (index == 1 && !surfaceReady) {
                Box(modifier = Modifier.fillMaxSize().background(Color(chrome.backdrop)))
            }
            ScrollThumb(
                scrollY = { state.scrollY },
                totalHeightPx = { state.contentHeight },
                viewportPx = { state.viewportHeight },
                currentPage = { state.currentPage },
                pageCount = { state.pageCount },
                onScrollTo = { state.surface?.scrollToY(it) },
                modifier = Modifier.matchParentSize().padding(insetPadding),
            )
            // The badges take the canvas edge opposite the Main Toolbar (see
            // [pageCounterAlignment]), so they never sit under the tools or the writing hand. The zoom
            // badge rides directly above the page badge and shares its look, so the pair reads as one
            // stack; tapping the zoom one puts the view back at 100%.
            PaneViewportBadges(
                state = state,
                modifier = Modifier
                    .align(pageCounterAlignment(settings.toolbarPosition))
                    .padding(insetPadding)
                    .padding(8.dp),
            )
            // The pen diagnostics panel floats over the canvas it reports on, in the corner the
            // chrome leaves free — the events it logs are then exactly the ones it describes.
            DialogPresence(ui.penDiagnostics) {
                PenDiagnosticsPanel(
                    lines = state.penDebugLines,
                    onClear = { state.surface?.clearPenDebug() },
                    modifier = Modifier.align(Alignment.TopStart).padding(insetPadding).padding(8.dp),
                )
            }
            // Both of the pane's selection bars ride with what they act on instead of sitting on the
            // bottom edge, where a hand holding the stylus has to reach for them (see [PaneSelectionBars]).
            PaneSelectionBars(
                pane = state,
                ui = ui,
                settings = settings,
                onSettingsChange = onSettingsChange,
                canvasSizePx = canvasSizePx,
                modifier = Modifier.align(Alignment.TopStart),
            )
            DialogPresence(ui.editingPlot != null) {
                PlotInspectorAnchor(state, ui, canvasSizePx, Modifier.fillMaxSize(), insetPadding)
            }
            DialogPresence(ui.editingTableCell != null) {
                TableEditorHost(ui = ui, pane = state, canvasSizePx = canvasSizePx, modifier = Modifier.fillMaxSize(), safeInsets = insetPadding)
            }
            DialogPresence(ui.editingMathBlock != null) {
                val block = rememberClosingArgument(ui.editingMathBlock) ?: return@DialogPresence
                LatexDialog(
                    initial = block.source,
                    title = "Edit formula",
                    confirmLabel = "Save",
                    onConfirm = { source ->
                        ui.editingMathBlock = null
                        if (source.isNotBlank()) {
                            state.surface?.replaceMathBlock(block, block.copy(source = source))
                        }
                    },
                    onDismiss = { ui.editingMathBlock = null },
                )
            }
            DialogPresence(ui.editingShape != null) {
                ShapeEditorHost(ui, state, canvasSizePx, Modifier.fillMaxSize(), insetPadding)
            }
            // The **Manage pages** card (its own file: the flag, the card and its two commands).
            PageRangeHost(ui, state.surface, state.pageCount)
            DialogPresence(ui.editingCircuit != null) {
                CircuitInspectorAnchor(state, ui, canvasSizePx, Modifier.fillMaxSize(), insetPadding)
            }
        }
    }
}

/**
 * The tab row's lead-in from the canvas's own edge: the chips begin this far inside it, on the side the
 * rail's lane takes.
 *
 * It is what the tabs kept when they moved from a band of their own into the header ([EditorTopBar]): the
 * band's content was inset by this much inside the pane, so adding it to the rail's lane lands the chips
 * on exactly the columns they occupied before — and on the figures' dock's own starting line.
 */
internal val TAB_STRIP_LEAD_IN = 8.dp


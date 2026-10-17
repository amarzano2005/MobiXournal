package com.mobixournal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.model.Tool
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.EraserMode
import com.mobixournal.render.GuideKind
import com.mobixournal.render.ImportPdfMode
import com.mobixournal.render.InputSettings
import com.mobixournal.render.PlaceKind
import com.mobixournal.render.Placement
import com.mobixournal.render.ShapeKind
import com.mobixournal.render.Uncompilable
import com.mobixournal.render.tracePenParameters
import com.mobixournal.ui.theme.rememberChromeColor
import com.mobixournal.ui.theme.rememberGlassSurfaceColor

/**
 * Push an [EditorTool] onto the surface: the three drawing tools set [Tool]; Hand toggles pan mode;
 * the authoring tools (text/image/LaTeX) set the surface's [DrawingSurfaceView.placeKind] so a tap
 * places that element instead of drawing.
 *
 * Two tools are variants of another rather than modes of their own: LASSO_SELECT is SELECT with a
 * freehand marquee, and ERASER_WHOLE is ERASER deleting whole strokes. Picking the tool is what sets
 * the variant, which is why neither has a separate mode menu.
 */
fun DrawingSurfaceView.applyTool(tool: EditorTool) {
    handMode = tool == EditorTool.HAND
    selectMode = tool == EditorTool.SELECT || tool == EditorTool.LASSO_SELECT
    backgroundSelectMode = tool == EditorTool.BG_SELECT
    lassoMode = tool == EditorTool.LASSO_SELECT
    textSelectMode = tool == EditorTool.TEXT_SELECT
    verticalSpaceMode = tool == EditorTool.VERTICAL_SPACE
    audioPlayMode = tool == EditorTool.PLAY_OBJECT
    placeKind = when (tool) {
        EditorTool.TEXT -> PlaceKind.TEXT
        EditorTool.IMAGE -> PlaceKind.IMAGE
        EditorTool.TEXIMAGE -> PlaceKind.TEX
        else -> null
    }
    shapeKind = when (tool) {
        EditorTool.LINE -> ShapeKind.LINE
        EditorTool.ARROW -> ShapeKind.ARROW
        EditorTool.DOUBLE_ARROW -> ShapeKind.DOUBLE_ARROW
        EditorTool.COORDINATE_AXIS -> ShapeKind.COORDINATE_AXIS
        EditorTool.RECTANGLE -> ShapeKind.RECTANGLE
        EditorTool.ELLIPSE -> ShapeKind.ELLIPSE
        EditorTool.SPLINE -> ShapeKind.SPLINE
        EditorTool.TRIANGLE -> ShapeKind.TRIANGLE
        EditorTool.SQUARE -> ShapeKind.SQUARE
        EditorTool.RHOMBUS -> ShapeKind.RHOMBUS
        EditorTool.TRAPEZOID -> ShapeKind.TRAPEZOID
        EditorTool.PENTAGON -> ShapeKind.PENTAGON
        EditorTool.HEXAGON -> ShapeKind.HEXAGON
        EditorTool.TABLE -> ShapeKind.TABLE
        EditorTool.RESISTOR -> ShapeKind.RESISTOR
        EditorTool.CAPACITOR -> ShapeKind.CAPACITOR
        EditorTool.INDUCTOR -> ShapeKind.INDUCTOR
        EditorTool.GROUND -> ShapeKind.GROUND
        EditorTool.DIODE -> ShapeKind.DIODE
        EditorTool.LED -> ShapeKind.LED
        EditorTool.ZENER_DIODE -> ShapeKind.ZENER_DIODE
        EditorTool.OPAMP -> ShapeKind.OPAMP
        EditorTool.BJT_NPN -> ShapeKind.BJT_NPN
        EditorTool.BJT_PNP -> ShapeKind.BJT_PNP
        EditorTool.DC_SOURCE -> ShapeKind.DC_SOURCE
        EditorTool.CURRENT_SOURCE -> ShapeKind.CURRENT_SOURCE
        EditorTool.AND_GATE -> ShapeKind.AND_GATE
        EditorTool.OR_GATE -> ShapeKind.OR_GATE
        EditorTool.NOT_GATE -> ShapeKind.NOT_GATE
        EditorTool.NAND_GATE -> ShapeKind.NAND_GATE
        EditorTool.NOR_GATE -> ShapeKind.NOR_GATE
        EditorTool.XOR_GATE -> ShapeKind.XOR_GATE
        EditorTool.XNOR_GATE -> ShapeKind.XNOR_GATE
        EditorTool.BUFFER_GATE -> ShapeKind.BUFFER_GATE
        EditorTool.SWITCH_OPEN -> ShapeKind.SWITCH_OPEN
        EditorTool.SWITCH_CLOSED -> ShapeKind.SWITCH_CLOSED
        EditorTool.TRANSFORMER -> ShapeKind.TRANSFORMER
        EditorTool.JUNCTION -> ShapeKind.JUNCTION
        EditorTool.DIMENSION -> ShapeKind.DIMENSION
        EditorTool.CHEM_BOND_SINGLE -> ShapeKind.CHEM_BOND_SINGLE
        EditorTool.CHEM_BOND_DOUBLE -> ShapeKind.CHEM_BOND_DOUBLE
        EditorTool.CHEM_BOND_TRIPLE -> ShapeKind.CHEM_BOND_TRIPLE
        EditorTool.CHEM_BENZENE -> ShapeKind.CHEM_BENZENE
        else -> null
    }
    if (tool == EditorTool.ERASER || tool == EditorTool.ERASER_WHOLE) {
        eraserMode =
            if (tool == EditorTool.ERASER_WHOLE) EraserMode.WHOLE_STROKE else EraserMode.STANDARD
    }
    when (tool) {
        EditorTool.PEN -> this.tool = Tool.PEN
        EditorTool.HIGHLIGHTER -> this.tool = Tool.HIGHLIGHTER
        EditorTool.ERASER, EditorTool.ERASER_WHOLE -> this.tool = Tool.ERASER
        // Shapes are drawn as ordinary pen strokes; the shapeKind above turns a drag into geometry.
        EditorTool.LINE, EditorTool.ARROW, EditorTool.DOUBLE_ARROW, EditorTool.COORDINATE_AXIS,
        EditorTool.RECTANGLE, EditorTool.ELLIPSE, EditorTool.SPLINE,
        EditorTool.TRIANGLE, EditorTool.SQUARE, EditorTool.RHOMBUS, EditorTool.TRAPEZOID,
        EditorTool.PENTAGON, EditorTool.HEXAGON,
        EditorTool.TABLE,
        EditorTool.RESISTOR, EditorTool.CAPACITOR, EditorTool.INDUCTOR,
        EditorTool.GROUND,
        EditorTool.DIODE, EditorTool.LED, EditorTool.ZENER_DIODE,
        EditorTool.OPAMP, EditorTool.BJT_NPN, EditorTool.BJT_PNP,
        EditorTool.DC_SOURCE, EditorTool.CURRENT_SOURCE,
        EditorTool.AND_GATE, EditorTool.OR_GATE,
        EditorTool.NOT_GATE, EditorTool.NAND_GATE, EditorTool.NOR_GATE,
        EditorTool.XOR_GATE, EditorTool.XNOR_GATE,
        EditorTool.BUFFER_GATE,
        EditorTool.SWITCH_OPEN, EditorTool.SWITCH_CLOSED, EditorTool.TRANSFORMER,
        EditorTool.JUNCTION, EditorTool.DIMENSION,
        EditorTool.CHEM_BOND_SINGLE, EditorTool.CHEM_BOND_DOUBLE,
        EditorTool.CHEM_BOND_TRIPLE, EditorTool.CHEM_BENZENE,
        -> this.tool = Tool.PEN
        else -> Unit // Hand / authoring tools keep the last drawing tool for when they're turned off
    }
}

/**
 * [EditorUiState.switchToolTo] with the surface push and the settings write folded into one step —
 * the entry point every tool switch goes through, whether it came from a rail slot, a keyboard
 * shortcut or the barrel button. Having one is what keeps those paths from drifting: each of them
 * lands the pen on the pen's own colour and width.
 */
fun DrawingSurfaceView.activateTool(
    target: EditorTool,
    ui: EditorUiState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    previous: EditorTool = ui.tool,
) {
    val next = ui.switchToolTo(target, settings, previous)
    colorArgb = ui.color
    baseWidthPt = ui.width
    applyTool(target)
    onSettingsChange(next)
}

/** Push the stylus/input [AppSettings] onto the surface (classifier settings, hover, pressure feel). */
fun DrawingSurfaceView.applySettings(s: AppSettings) {
    inputSettings = InputSettings(
        fingerDraws = s.fingerDraws,
        barrelAction = s.barrelAction,
        barrelDoubleAction = s.barrelDoubleAction,
    )
    showHover = s.showHover
    multiFingerShortcuts = s.multiFingerShortcuts
    pressureEnabled = s.pressureEnabled
    pressureMultiplier = s.pressureMultiplier
    minimumPressure = s.minimumPressure
    strokePrecision = s.strokePrecision
    recognizeShapes = s.recognizeShapes
    setColumns(s.pageColumns)
    snapToGrid = s.snapToGrid
    snapRotation = s.snapRotation
    tableRows = s.tableRows
    tableCols = s.tableCols
    tableHeader = s.tableHeader
    triangleKind = s.triangleKind
    scaleneAngleA = s.scaleneAngleA.toDouble()
    scaleneAngleB = s.scaleneAngleB.toDouble()
    scaleneAngleC = s.scaleneAngleC.toDouble()
    trapezoidKind = s.trapezoidKind
    trapezoidAngleA = s.trapezoidAngleA.toDouble()
    trapezoidAngleB = s.trapezoidAngleB.toDouble()
    // Only place a guide the surface isn't already showing — re-placing on every settings change
    // would yank a guide the user has carefully positioned back to the middle of the screen.
    if (s.guideKind == GuideKind.NONE) {
        if (guide != null) placeGuide(GuideKind.NONE)
    } else if (guide == null || guide?.guideKind != s.guideKind) {
        // A **remembered instrument pose** comes back exactly where it was left (this is the restore
        // path behind `AppSettings.guidePose`). Anything else is placed fresh at the viewport centre:
        // a kind with no pose of its own (the invisible rules), or a pose whose page is gone.
        val remembered = s.guidePose?.takeIf {
            it.guide.guideKind == s.guideKind && it.page in doc.pages.indices
        }
        if (remembered != null) restoreGuide(remembered.guide, remembered.page) else placeGuide(s.guideKind)
    }
    flingStrength = s.momentum
    momentumCurve = s.momentumCurve
    panSensitivity = s.panSensitivity
    // With pen diagnostics open, print the pressure filter as the surface now holds it: that line is
    // what turns "the sensitivity slider does nothing" into either the new value or a missing wire.
    tracePenParameters()
}

/**
 * The single editor screen: a Material 3 top bar (undo/redo plus an overflow menu for open/save/
 * settings), a vertical control [SideToolbar] down the docked edge (left or right), and the stylus canvas filling the
 * rest. The canvas is a classic [DrawingSurfaceView] hosted via [AndroidView] for low-latency stylus
 * rendering (see `docs/chrome.md`).
 *
 * The screen owns no loose state of its own: everything it remembers lives in [EditorUiState] (the
 * chrome) and [PaneState] (each canvas's mirror), and each region below — top bar, toolbar, pane,
 * overlays — is its own composable reading just the parts it needs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: (filename: String, format: SaveFormat) -> Unit,
    currentSaveFormat: () -> SaveFormat,
    /** The name the Save As dialog opens on: the open document's own name. */
    currentSaveName: () -> String = { "Untitled.xopp" },
    onImportPdf: (ImportPdfMode) -> Unit,
    onExportPdf: () -> Unit,
    /** Export the active page alone, as a PNG image or an SVG vector file. */
    onExportPagePng: () -> Unit = {},
    onExportPageSvg: () -> Unit = {},
    /**
     * What the last `.xopp` export could not draw, from whoever did the writing (the activity): living
     * entities an export has to cook into ink can be un-drawable, and a loss the user is not told about
     * is a document that quietly lies about its contents. Empty is the normal case.
     */
    exportGaps: List<Uncompilable> = emptyList(),
    onDismissExportGaps: () -> Unit = {},
    /** The document name waiting on consent to open read-only (written by a newer app version). */
    pendingNewerAppConsentName: String? = null,
    onConfirmNewerAppConsent: () -> Unit = {},
    onDismissNewerAppConsent: () -> Unit = {},
    /** An image-placement tap: open the file/gallery picker and insert what it returns. */
    onPickImage: (Placement) -> Unit,
    /** A pane's canvas has just been built: its index, then the view. */
    onSurfaceCreated: (Int, DrawingSurfaceView) -> Unit,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    audio: AudioUiState = AudioUiState(),
    /**
     * Identity of the document the chrome is showing, used to key its **page bookmarks** (see
     * [BookmarkStore]). Bookmarks are app-side navigation state — the `.xopp` format has no page
     * label — so they are stored per document key and never written to the file.
     */
    bookmarkKey: String = "",
    /** One tab session per pane, in pane order (see [com.mobixournal.panes.EditorPane]). */
    tabs: List<TabsUiState> = listOf(TabsUiState()),
    /** Whether both panes are shown side by side. */
    splitView: Boolean = false,
    onToggleSplitView: () -> Unit = {},
    /** Which pane the chrome drives; touching a pane's canvas makes it the active one. */
    activePane: Int = 0,
    onActivePane: (Int) -> Unit = {},
    /** A document transfer in flight (label shown), or null. Remote files can take a while. */
    busy: String? = null,
    /** Back was pressed with nothing left to dismiss: leave the app. */
    onExit: () -> Unit = {},
    /** The top app bar content; defaults to [EditorTopBar]. */
    topBar: (@Composable (EditorUiState, PaneState, TabsUiState) -> Unit)? = null,
    /** The rail/toolbar content; defaults to [EditorToolbar]. */
    paneChrome: @Composable (EditorUiState, PaneState, AppSettings, (AppSettings) -> Unit, AudioUiState) -> Unit = { ui, pane, settings, onSettingsChange, audio ->
        EditorToolbar(
            ui = ui,
            pane = pane,
            settings = settings,
            onSettingsChange = onSettingsChange,
            audio = audio,
        )
    },
) {
    val ui = rememberEditorUiState(settings)
    val pane = ui.pane(activePane)

    // Page bookmarks: loaded for the document being shown, persisted against it on every edit, and
    // handed to the canvas as the page flags it paints. `pane` is the pane the chrome drives, so a
    // split view keeps each document's bookmarks to itself.
    val context = LocalContext.current
    val bookmarkStore = remember(context) { BookmarkStore(context) }
    LaunchedEffect(pane, bookmarkKey) {
        pane.bookmarks = bookmarkStore.load(bookmarkKey)
        pane.onBookmarksChange = { updated ->
            pane.bookmarks = updated
            bookmarkStore.save(bookmarkKey, updated)
        }
    }
    LaunchedEffect(pane.surface, pane.bookmarks) {
        pane.surface?.applyBookmarkedPages(pane.bookmarkedPages)
    }

    // Back peels the editor's transient layers off one at a time before it ever exits.
    EditorBackHandler(ui = ui, pane = pane, busy = busy != null, onExit = onExit)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            // The **desk**, not transparency: the chrome's bands are laid out *beside* the canvas now (see
            // [EditorBody]), so a translucent bar or rail is read against this colour — the same one the
            // canvas paints around a page, so the whole screen reads as one surface with the page on it.
            containerColor = rememberChromeColor(),
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        ) { _ ->
            // Header band first, canvas in what is left: the bar is a row of the layout, not a layer over
            // the page, so no part of the page ever sits under it (see [EditorBody] for why that matters).
            Column(modifier = Modifier.fillMaxSize()) {
                if (!ui.fullPage) {
                    val activeTabs = tabs[activePane.coerceIn(tabs.indices)]
                    if (topBar != null) {
                        topBar(ui, pane, activeTabs)
                    } else {
                        EditorTopBar(
                            ui = ui,
                            pane = pane,
                            tabs = activeTabs,
                            settings = settings,
                            onSettingsChange = onSettingsChange,
                            onOpen = onOpen,
                            onNewTab = { activeTabs.onNew() },
                            onSave = onSave,
                            onExportPdf = onExportPdf,
                            onExportPagePng = onExportPagePng,
                            onExportPageSvg = onExportPageSvg,
                            splitView = splitView,
                            onToggleSplitView = onToggleSplitView,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                EditorBody(
                    ui = ui,
                    pane = pane,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    audio = audio,
                    splitView = splitView,
                    onActivePane = onActivePane,
                    onSurfaceCreated = onSurfaceCreated,
                    onPickImage = onPickImage,
                    paneChrome = paneChrome,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }

        // Re-apply settings to the live surface whenever the user changes them in Settings.
        LaunchedEffect(settings) {
            if (ui.tool in SHAPE_TOOLS) {
                ui.width = settings.shapeWidth
                ui.panes.forEach { it.surface?.baseWidthPt = settings.shapeWidth }
            }
            ui.panes.forEach { it.surface?.applySettings(settings) }
        }

        // Settings is overlaid on top of the still-composed editor rather than replacing it, so the
        // AndroidView-hosted DrawingSurfaceView is never detached — the drawing (and undo history)
        // survives the round trip to Settings and back.
        if (ui.showSettings) {
            SettingsScreen(
                settings = settings,
                onChange = onSettingsChange,
                onBack = { ui.showSettings = false },
            )
        }

        // A document is moving to or from storage — possibly a slow network share, so say so and
        // swallow taps until it lands rather than letting the canvas be edited mid-transfer.
        if (busy != null) TransferOverlay(busy)

        EditorOverlays(
            ui = ui,
            pane = pane,
            settings = settings,
            onSettingsChange = onSettingsChange,
            currentSaveFormat = currentSaveFormat,
            currentSaveName = currentSaveName,
            onSaveAs = onSaveAs,
            onImportPdf = onImportPdf,
            exportGaps = exportGaps,
            onDismissExportGaps = onDismissExportGaps,
            pendingNewerAppConsentName = pendingNewerAppConsentName,
            onConfirmNewerAppConsent = onConfirmNewerAppConsent,
            onDismissNewerAppConsent = onDismissNewerAppConsent,
        )

        if (!settings.hasSeenOnboarding) {
            OnboardingDialog(
                onDismiss = {
                    onSettingsChange(settings.copy(hasSeenOnboarding = true))
                },
                // The handedness step writes the rail's edge straight through, so that page of the
                // tour can be answered by watching the toolbar move behind the dialog — and the last
                // step writes the new-document format, which nothing on screen can show either way.
                toolbarPosition = settings.toolbarPosition,
                onToolbarPosition = { onSettingsChange(settings.copy(toolbarPosition = it)) },
                documentFormat = settings.newDocumentFormat,
                onDocumentFormat = { onSettingsChange(settings.copy(newDocumentFormat = it)) },
            )
        }
    }
}

/**
 * The rail and the drawing area, laid out as **bands that do not overlap**: whatever the chrome
 * occupies, the canvas does not. The vertical rail gets a lane down either edge (per
 * [AppSettings.toolbarPosition]), the horizontal dock a band along the bottom (Compact), and the
 * canvas takes the rest. In full-page mode the chrome is absent altogether and the canvas has the
 * screen.
 *
 * This is the point of the split. A floating bar over the page hides the ink under it and swallows the
 * stylus there — the top strip of a page is where a note taker's hand reaches first — and the rail's
 * lane over the page does the same down one side. Laying the chrome out *beside* the canvas instead of
 * over it means every pixel of the page the user can see is a pixel they can write on.
 */
@Composable
private fun EditorBody(
    ui: EditorUiState,
    pane: PaneState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    audio: AudioUiState,
    splitView: Boolean,
    onActivePane: (Int) -> Unit,
    onSurfaceCreated: (Int, DrawingSurfaceView) -> Unit,
    onPickImage: (Placement) -> Unit,
    paneChrome: @Composable (EditorUiState, PaneState, AppSettings, (AppSettings) -> Unit, AudioUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolbar: @Composable () -> Unit = {
        if (!ui.fullPage) {
            paneChrome(ui, pane, settings, onSettingsChange, audio)
        }
    }
    // The callbacks are read through the latest state because the movable content below is
    // remembered once: a frozen `settings` here would write the first composition's values back
    // over every later change.
    val latestSettings = rememberUpdatedState(settings)
    val latestOnSettingsChange = rememberUpdatedState(onSettingsChange)
    val latestOnActivePane = rememberUpdatedState(onActivePane)
    val latestOnSurfaceCreated = rememberUpdatedState(onSurfaceCreated)
    val latestOnPickImage = rememberUpdatedState(onPickImage)
    val paneAt: @Composable (Int, Modifier) -> Unit = { index, paneModifier ->
        EditorPaneView(
            index = index,
            ui = ui,
            settings = latestSettings.value,
            onSettingsChange = latestOnSettingsChange.value,
            onActivePane = latestOnActivePane.value,
            onSurfaceCreated = latestOnSurfaceCreated.value,
            onPickImage = latestOnPickImage.value,
            modifier = paneModifier,
        )
    }
    // **Movable**, not re-keyed: turning split view on or off (and full-page or the dock layout)
    // only changes *where* a pane sits in the tree. Without this, Compose disposes the pane's
    // `AndroidView` and builds a new one, which reloads the tab record — written on tab change and
    // pause, not on every stroke — so the live edits and the undo history are lost. Also, pane one
    // only exists while split view is on, so it is disposed with the split as documented.
    val firstPane = remember { movableContentOf<Modifier> { paneAt(0, it) } }
    val secondPane = remember { movableContentOf<Modifier> { paneAt(1, it) } }
    /** The drawing area: one pane, or both with a draggable bar between them. */
    val canvas: @Composable (Modifier) -> Unit = { canvasModifier ->
        if (splitView) {
            SplitLayout(
                fraction = ui.splitFraction,
                onFraction = { ui.splitFraction = it },
                modifier = canvasModifier,
                first = { firstPane(it) },
                second = { secondPane(it) },
            )
        } else {
            firstPane(canvasModifier)
        }
    }
    when {
        ui.fullPage -> Box(modifier = modifier) { canvas(Modifier.fillMaxSize()) }

        settings.toolbarLayout == ToolbarLayout.COMPACT -> {
            // The horizontal dock in a band of its own along the bottom edge, above the nav-bar inset.
            // The band's height is the rail's own (its shell carries the padding), and the canvas ends
            // where the band begins.
            Column(modifier = modifier) {
                canvas(Modifier.weight(1f).fillMaxWidth())
                Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) { toolbar() }
            }
        }

        else -> {
            // The vertical rail as a lane down the chosen edge, the canvas taking the rest. The lane is
            // the rail's own width ([SideToolbarModernTotalWidth]) and it starts at the **header's** bottom
            // edge — the bar plus the row of document tabs inside it — so the rail hangs directly under the
            // whole header and nothing overlaps anything. That is what costs the rail's spacing budget the
            // tab row; see `RailSizing.kt`.
            val railOnLeft = settings.toolbarPosition != ToolbarPosition.RIGHT
            Row(modifier = modifier) {
                if (railOnLeft) RailLane(toolbar)
                canvas(Modifier.weight(1f).fillMaxHeight())
                if (!railOnLeft) RailLane(toolbar)
            }
        }
    }
}

/** The rail's lane: exactly the width the rail occupies, and the height it may scroll in. */
@Composable
private fun RailLane(toolbar: @Composable () -> Unit) {
    val glassColor = rememberGlassSurfaceColor()
    Surface(
        color = glassColor,
        shadowElevation = 4.dp,
        modifier = Modifier.width(SideToolbarModernTotalWidth).fillMaxHeight(),
    ) {
        Box(modifier = Modifier.width(SideToolbarModernTotalWidth).fillMaxHeight()) {
            toolbar()
        }
    }
}

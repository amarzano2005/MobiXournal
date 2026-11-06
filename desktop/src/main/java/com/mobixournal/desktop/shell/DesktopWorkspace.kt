package com.mobixournal.desktop.shell
import com.mobixournal.desktop.sheet.placeCircuit
import com.mobixournal.desktop.ink.ColourEditorTarget
import com.mobixournal.desktop.ink.DesktopTablet
import com.mobixournal.desktop.ink.TabletInk
import com.mobixournal.desktop.ink.TabletWintab
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.PdfIndexLoader
import com.mobixournal.desktop.settings.InspectorPolicy
import com.mobixournal.desktop.settings.LocalNavigationState
import com.mobixournal.desktop.settings.RailSide
import com.mobixournal.desktop.sheet.HudUi
import com.mobixournal.desktop.sheet.PageCanvas
import com.mobixournal.desktop.sheet.SelectionAction
import com.mobixournal.desktop.sheet.selectionBarUi
import com.mobixournal.desktop.tools.DesktopTextBox
import com.mobixournal.desktop.tools.InkActions
import com.mobixournal.desktop.tools.InspectorLane
import com.mobixournal.desktop.tools.TextBoxUi
import com.mobixournal.desktop.tools.BandEdge
import com.mobixournal.desktop.tools.ToolRail
import com.mobixournal.desktop.tools.ToolRibbon
import com.mobixournal.desktop.tools.bandEdgeShade
import com.mobixournal.desktop.tools.bandSurface
import com.mobixournal.desktop.tools.resized
import com.mobixournal.desktop.tools.selectTool

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.mobixournal.format.model.LineStyle

/**
 * The document's half of the window: the sidebar in its lane, the tool strips in theirs, and the page.
 *
 * It is a separate composable because it is the **layout** and nothing else — every value it draws comes
 * from [state] and every gesture goes back through it, so the shell file keeps the state and the commands
 * (`SourceStructureTest` keeps both it and this one small).
 *
 * **The strips take a lane, as they do on the tablet** (decision 2026-10-28). The rail stands in a band down
 * the edge `RailSide` names and the ribbon in a band under the header; the canvas is handed what is left,
 * so the page can never be under a bar. The bands are the header's material (`bandSurface`), and each one
 * carries the tablet's own capsule — a pane of glass with a rim and a lift — standing *in* the band. Earlier
 * (2026-10-26) the strips were panes of glass lying **over** the sheet, which left the canvas full width and
 * the page running behind them; that is not how the tablet draws its rail, and one window is not a second
 * design language. The capsules are opaque to the pointer — their slots' input and their own scroll pane
 * claim every press that starts on them — so a drag begun on a strip is never a stroke drawn beneath it.
 */
/**
 * The bands' one shade, drawn on the page: the canvas draws the top line and the side line itself, so
 * they turn its own corner where the two bands meet and neither band's shade runs across the band
 * beside it (`docs/interface-contract.md` §1).
 */
@Composable
private fun Modifier.canvasBandShade(state: DesktopShellState): Modifier {
    val placement = state.prefs.stripPlacement
    val railOnLeft = state.prefs.railSide == RailSide.LEFT
    val bandOnLeft = state.sidebarVisible || (placement.showsRail && railOnLeft)
    val bandOnRight = (placement.showsRail && !railOnLeft) ||
        state.prefs.inspectorPolicy != InspectorPolicy.NEVER
    return this
        .bandEdgeShade(BandEdge.TOP)
        .then(if (bandOnLeft) Modifier.bandEdgeShade(BandEdge.START) else Modifier)
        .then(if (bandOnRight) Modifier.bandEdgeShade(BandEdge.END) else Modifier)
}

@Composable
internal fun PageWorkspace(state: DesktopShellState, tab: DesktopTab, tablet: DesktopTablet?) {
    CompositionLocalProvider(LocalNavigationState provides state.prefs.navigation) {
        Row(Modifier.fillMaxSize()) {
            if (state.sidebarVisible) WorkspaceSidebar(state, tab)
            val placement = state.prefs.stripPlacement
            val railOnLeft = state.prefs.railSide == RailSide.LEFT
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (placement.showsRibbon) StripRibbon(state)
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (placement.showsRail && railOnLeft) StripRail(state)
                    WorkspaceCanvas(state, tab, tablet)
                    if (placement.showsRail && !railOnLeft) StripRail(state)
                }
            }
            if (state.prefs.inspectorPolicy != InspectorPolicy.NEVER) {
                InspectorLane(state = state, tab = tab)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.WorkspaceCanvas(
    state: DesktopShellState,
    tab: DesktopTab,
    tablet: DesktopTablet?,
) {
    Box(Modifier.weight(1f).fillMaxHeight().canvasBandShade(state)) {
        // The PDF's text layer and Contents, read once per file off the UI thread and published
        // on the tab: the selection, the magnetic highlighter and the sidebar all read it there.
        PdfIndexLoader(tab)
        PageCanvas(
            tab = tab,
            panMode = state.panMode,
            tool = state.tools.current,
            ink = state.pageInk,
            shapeInk = state.shapeInk,
            circuitKind = state.activeCircuitKind,
            figures = state.prefs.figures.shape,
            recognizeShapes = state.prefs.figures.recognizeShapes,
            onPlaceLiving = { slot, bounds -> state.placeOrAsk(tab, slot, bounds) },
            onPlaceCircuit = { kind, span -> state.placeCircuit(tab, kind, span) },
            onEditLiving = { state.runSelection(SelectionAction.EDIT) },
            text = TextBoxUi(
                box = state.textBox.box,
                onPress = { x, y, pageW, pageH ->
                    state.textBox.begin(tab, x, y, pageW, pageH, state.ink, state::edited)
                },
                onChange = { text -> state.textBox.change(text) },
            ),
            pen = tablet?.pen,
            packets = tablet?.wintab,
            dialWheel = { tablet?.dialWheelRecently() == true },
            onLiveDrag = { cancel -> state.liveDrag = cancel },
            selectionBar = selectionBarFor(state, tab),
            onViewport = { tab.viewport = it },
            onFit = { tab.fit = it },
            onEdited = { index ->
                state.thumbnails[tab.key]?.remove(index)
                state.noteEdit()
            },
            modifier = Modifier.fillMaxSize(),
            hud = HudUi(
                open = { state.ring.open },
                onPress = { x, y, density -> state.ring.press(x, y, density) },
                slots = state.ring.slots,
                onPointerAt = { at, density -> state.ring.notePointer(at, density) },
                pressHeld = { state.ring.pressHeld },
                onRelease = { state.ring.release() },
            ),
        )
    }
}

/**
 * The page sidebar, in the lane it takes beside the canvas.
 *
 * A named composable rather than six inline lambdas: the row reads as the objects it arranges, and the
 * commands the sidebar runs — the page pick, the two drags, the deletes — are written once here.
 */
@Composable
private fun WorkspaceSidebar(state: DesktopShellState, tab: DesktopTab) {
    PageSidebar(
        tab = tab,
        cache = state.thumbnails.getOrPut(tab.key) { mutableMapOf() },
        onSelectPage = { index -> selectPage(tab, index) },
        // The row's own delete: one page, one undo step, and the document's PDF reference kept when the
        // page it lived on is the one dropped.
        onRemovePage = { index -> state.removePageAt(index) },
        // A thumbnail dragged onto another row: one undo step, the same command the Pages menu runs.
        onMovePage = { from, to -> state.movePage(from, to) },
        // A drag on one of the picked rows carries the whole pick, as one block and one undo step.
        onMovePages = { pages, target -> state.movePages(pages, target) },
        // The selection bar's delete: every picked page, with the PDF reference kept.
        onRemovePages = { pages -> state.removePages(pages) },
        // The `+` row in the list: a blank sheet after the page it follows, one undo step.
        onInsertPage = { index -> state.insertPageAt(index, before = false) },
        // The pick's clipboard, beside Delete: pages, in one undo step each way.
        onCopyPages = { state.copyPickedPages() },
        onCutPages = { state.cutPickedPages() },
        onPastePages = { state.pastePages() },
        // A row of the search results: that match becomes the one in view, and its page the page on screen.
        onSearchHit = { index -> state.showSearchHit(index) },
        // A page's thumbnail is re-painted after the edits go quiet, not per stroke.
        revision = state.thumbnailRevision,
    )
}

/**
 * The **ribbon**: the same tools as the rail, in a band across the top of the page, for `Top`.
 *
 * It is a named composable rather than an inline call so the layout above reads as the objects it
 * arranges, and so the strip's ink actions are written once (`inkActions`) instead of at each home.
 */
@Composable
private fun StripRibbon(state: DesktopShellState, modifier: Modifier = Modifier) {
    ToolRibbon(
        state = state.stripState,
        onSelect = state::selectTool,
        ink = state.inkActions(onLineStyle = state::pickLineStyle),
        modifier = modifier,
    )
}

/**
 * The **rail**: the tablet's own Main Toolbar, on whichever edge `RailSide` names.
 *
 * One composable, called on either side of the canvas, so a change to the rail cannot reach one edge and
 * miss the other.
 */
@Composable
private fun StripRail(state: DesktopShellState, modifier: Modifier = Modifier) {
    ToolRail(
        state = state.stripState,
        onSelect = state::selectTool,
        ink = state.inkActions(onLineStyle = state::pickLineStyle),
        modifier = modifier,
    )
}

/** The colour capsule's verbs, as the strips' one action set — built from the shell, not from a copy. */
private fun DesktopShellState.inkActions(
    onLineStyle: (LineStyle) -> Unit = {},
): InkActions = InkActions(
    onColour = ::pickColour,
    onWidth = ::pickWidth,
    onLineStyle = onLineStyle,
    onEditCustom = { editColour(ColourEditorTarget.CUSTOM) },
    onAddColour = { editColour(ColourEditorTarget.NEW) },
)

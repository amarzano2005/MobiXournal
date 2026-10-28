package com.mobixournal.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
@Composable
internal fun PageWorkspace(state: DesktopShellState, tab: DesktopTab, tablet: DesktopTablet?) {
    Row(Modifier.fillMaxSize()) {
        if (state.sidebarVisible) {
            PageSidebar(
                tab = tab,
                cache = state.thumbnails.getOrPut(tab.key) { mutableMapOf() },
                onSelectPage = { index -> selectPage(tab, index) },
                // The row's own delete: one page, one undo step, and the document's PDF reference kept
                // when the page it lived on is the one dropped.
                onRemovePage = { index -> state.removePageAt(index) },
                // A thumbnail dragged onto another row: one undo step, the same command the Pages menu runs.
                onMovePage = { from, to -> state.movePage(from, to) },
                // A drag on one of the picked rows carries the whole pick, as one block and one undo step.
                onMovePages = { pages, target -> state.movePages(pages, target) },
                // The selection bar's delete: every picked page, with the PDF reference kept.
                onRemovePages = { pages -> state.removePages(pages) },
                // A page's thumbnail is re-painted after the edits go quiet, not per stroke.
                revision = state.thumbnailRevision,
            )
        }
        val placement = state.prefs.stripPlacement
        val railOnLeft = state.prefs.railSide == RailSide.LEFT
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // The ribbon is a band of its own above the canvas, not a bar lying on the sheet.
            if (placement.showsRibbon) StripRibbon(state)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (placement.showsRail && railOnLeft) StripRail(state)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PageCanvas(
                        tab = tab,
                        panMode = state.panMode,
                        tool = state.tools.current,
                        // The page's ink, not the strip's: a highlighter writes a band, not a wide pen.
                        ink = state.pageInk,
                        // Figures, circuits and placements always take the pen's ink, never the highlighter's.
                        shapeInk = state.shapeInk,
                        // The component the Circuit slot is armed with — the picker's answer the drag reads.
                        circuitKind = state.activeCircuitKind,
                        // A STEM slot asks its values before the element exists, so the canvas hands the box over.
                        onPlaceLiving = { slot, bounds -> state.placeOrAsk(tab, slot, bounds) },
                        // A component is pulled out along the drag's own two ends, aimed at the page's pins.
                        onPlaceCircuit = { kind, span -> state.placeCircuit(tab, kind, span) },
                        // A double-click on the selected living element opens the same lane its pencil does —
                        // the tablet's double-tap, and one command rather than a second way to edit.
                        onEditLiving = { state.runSelection(SelectionAction.EDIT) },
                        // The Text slot types in place: the canvas opens the box, the field hands the typing
                        // back, and nothing reaches the page until the box is committed (`DesktopTextBox`).
                        text = TextBoxUi(
                            box = state.textBox.box,
                            onPress = { x, y, pageW, pageH ->
                                state.textBox.begin(tab, x, y, pageW, pageH, state.ink, state::edited)
                            },
                            onChange = { text -> state.textBox.change(text) },
                        ),
                        // The pen's own stream, whichever one this host has (`TabletWintab.kt` first, then
                        // `TabletInk.kt`); null leaves the canvas on the Compose pointer it always had.
                        pen = tablet?.pen,
                        // And, when the driver has packets, the stroke is drawn from those: the same contact,
                        // without waiting for Windows' mouse emulation to notice the touch.
                        packets = tablet?.wintab,
                        // Which wheel turned: the dial's notches arrive from the tablet's own HID device, so
                        // they zoom at the cursor while the mouse's wheel keeps scrolling.
                        dialWheel = { tablet?.dialWheelRecently() == true },
                        // A figure being dragged can be cancelled with `Esc`; the shell keeps the end of that,
                        // because the key reaches the window and not the canvas.
                        onLiveDrag = { cancel -> state.liveDrag = cancel },
                        // The bar under the outline: what the selection can be, and the one command it runs. The
                        // rules (what is available, and when) are the shell's, so the canvas only draws them.
                        selectionBar = selectionBarUi(tab, state.clipboard) { action -> state.runSelection(action) },
                        onViewport = { tab.viewport = it },
                        onFit = { tab.fit = it },
                        // An edit changes the page's picture, so the thumbnail cached for it is stale; the other
                        // pages keep theirs. It is only *dropped* here — re-rendering it now would cost a full
                        // software paint per stroke, so the sidebar paints it again once the burst is quiet
                        // (`ThumbnailRefresh`, keyed on `thumbnailRevision`). The edit also starts the clock on
                        // the crash snapshot, which the shell writes after the same burst.
                        onEdited = { index ->
                            state.thumbnails[tab.key]?.remove(index)
                            state.noteEdit()
                        },
                        // No chrome inset: the bands take their room out of the row, so the canvas is already
                        // the clear space a fitted page is framed in — nothing of the chrome lies over the sheet.
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // The rail is one composable with two homes — the lane before the canvas or the lane
                // after it, whichever edge `RailSide` names — so the two edges cannot drift into two
                // rails, and each lane takes its width from the row.
                if (placement.showsRail && !railOnLeft) StripRail(state)
            }
        }
        // The inspector, in a lane of its own on the right: it takes width from the row, so the page is
        // never resized by it. Its policy can only keep it shut — a column of blank controls is not an
        // answer the lane has.
        if (state.prefs.inspectorPolicy != InspectorPolicy.NEVER) {
            InspectorLane(state = state, tab = tab)
        }
    }
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
        // The ribbon also carries the pen's line style; the rail leaves it to the colour flyout.
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
        ink = state.inkActions(),
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

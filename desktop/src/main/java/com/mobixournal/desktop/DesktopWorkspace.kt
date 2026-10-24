package com.mobixournal.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The document's half of the window: the tool strips in their own lanes, the sidebar, the page.
 *
 * It is a separate composable because it is the **layout** and nothing else — every value it draws comes
 * from [state] and every gesture goes back through it, so the shell file keeps the state and the commands
 * (`SourceStructureTest` keeps both it and this one small). The strips are bands in their own lanes: the
 * ribbon takes a row under the header, the rail a column beside the canvas, and the page keeps the
 * rectangle that is left. Neither is ever laid over the sheet (`docs/chrome.md`).
 */
@Composable
internal fun PageWorkspace(state: DesktopShellState, tab: DesktopTab, tablet: DesktopTablet?) {
    Column(Modifier.fillMaxSize()) {
        if (state.toolPlacement.showsRibbon) {
            ToolRibbon(
                state = state.stripState,
                onSelect = state::selectTool,
                ink = InkActions(
                    onColour = state::pickColour,
                    onWidth = state::pickWidth,
                    onEditCustom = { state.editColour(ColourEditorTarget.CUSTOM) },
                    onAddColour = { state.editColour(ColourEditorTarget.NEW) },
                ),
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (state.sidebarVisible) {
                PageSidebar(
                    tab = tab,
                    cache = state.thumbnails.getOrPut(tab.key) { mutableMapOf() },
                    onSelectPage = { index -> selectPage(tab, index) },
                    // A page's thumbnail is re-painted after the edits go quiet, not per stroke.
                    revision = state.thumbnailRevision,
                )
            }
            if (state.toolPlacement.showsRail) {
                ToolRail(
                    state = state.stripState,
                    onSelect = state::selectTool,
                    ink = InkActions(
                        onColour = state::pickColour,
                        onWidth = state::pickWidth,
                        onEditCustom = { state.editColour(ColourEditorTarget.CUSTOM) },
                        onAddColour = { state.editColour(ColourEditorTarget.NEW) },
                    ),
                )
            }
            PageCanvas(
                tab = tab,
                panMode = state.panMode,
                tool = state.tools.current,
                ink = state.ink,
                // The component the Circuit slot is armed with: the picker's answer, read by the
                // placement when the press is lifted.
                circuitKind = state.activeCircuitKind,
                // A STEM slot asks its values before the element exists (the placement card in the
                // right-hand lane), so the canvas hands the box over instead of writing it.
                onPlaceLiving = { slot, bounds -> state.placeOrAsk(tab, slot, bounds) },
                // The Text slot types in place: the canvas opens the box, the field hands the typing
                // back, and nothing reaches the page until the box is committed (`DesktopTextBox`).
                text = TextBoxUi(
                    box = state.textBox.box,
                    onPress = { x, y, pageW, pageH ->
                        state.textBox.begin(tab, x, y, pageW, pageH, state.ink) { doc, index ->
                            state.thumbnails[doc.key]?.remove(index)
                            state.noteEdit()
                        }
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
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            // The inspector, in a lane of its own on the right: it takes width from the row, so the page
            // keeps the rectangle that is left and no glass is ever drawn over the sheet.
            InspectorLane(state = state, tab = tab)
        }
    }
}

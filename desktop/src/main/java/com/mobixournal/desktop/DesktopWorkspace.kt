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
                // The pen's own stream, when the window has Windows Ink (`desktop/TabletInk.kt`); null
                // leaves the canvas on the Compose pointer it always had.
                pen = tablet?.ink,
                // Which wheel turned: the dial's notches arrive from the tablet's own HID device, so
                // they zoom at the cursor while the mouse's wheel keeps scrolling.
                dialWheel = { tablet?.dialWheelRecently() == true },
                onViewport = { tab.viewport = it },
                onFit = { tab.fit = it },
                // An edit changes the page's picture, so the thumbnail cached for it is stale; the other
                // pages keep theirs. It also starts the clock on the crash snapshot: the shell writes one
                // after the edits go quiet.
                onEdited = { index ->
                    state.thumbnails[tab.key]?.remove(index)
                    state.noteEdit()
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

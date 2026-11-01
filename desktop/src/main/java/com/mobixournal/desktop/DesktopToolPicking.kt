package com.mobixournal.desktop

import com.mobixournal.render.GuideKind

/**
 * The window's **picking**: what a slot, a picker row, an accelerator or a toggle key does to the tool in
 * hand — and, for the guides' slot, to the instrument standing on the page.
 *
 * Extensions on [DesktopShellState] rather than a second state object: every answer is a decision over the
 * shell's own fields (the picked tool, the open picker, the active tab), so a copy of them would be a
 * second thing to keep in step. They live here rather than in `DesktopShell.kt` because that file is the
 * window's shape, not its tool behaviour, and `SourceStructureTest` keeps it inside its line budget.
 */

/**
 * Pick a tool (a slot, a picker row or its accelerator); the canvas reads it on the next press.
 *
 * Picking also closes the group picker: its job was to choose, and it has.
 */
internal fun DesktopShellState.selectTool(tool: DesktopTool) {
    // A text box is finished by leaving its tool: the typing is committed, never silently dropped.
    if (tool != DesktopTool.TEXT) textBox.commit(tabs.active, ::edited)
    if (selectionToolOf(tool) == null) tabs.active?.selection = null
    picks.pick(tool)
    openGroup = null
    // Picking an instrument **lays it on the page**: the guide is an input aid, not a tool that inks, so
    // the pick is the placement and the pose then belongs to the tab (`DesktopGuidePose`).
    guideKindOf(tool)?.let { placeGuide(it) }
}

/** The instrument standing on the active tab, or null — view state, so it belongs to the tab. */
internal val DesktopShellState.placedGuide: DesktopGuidePose? get() = tabs.active?.guide

/**
 * Lay [kind] on the active tab's page, where the reader is looking.
 *
 * The centre is the tab's visible page centre, so an instrument appears under the eye rather than in the
 * middle of a page the reader has scrolled away from — the same choice the tablet's `placeGuide` makes.
 * Nothing is written to the document: a guide is view state on both shells.
 */
internal fun DesktopShellState.placeGuide(kind: GuideKind) {
    val tab = tabs.active ?: return
    val sheet = runCatching { tab.document.page(tab.page) }.getOrNull() ?: return
    tab.guide = placedGuideOn(kind, tab.page, tab.visiblePageCenter(sheet))
}

/**
 * Take the instrument away and put the last acting tool back in hand — the tablet's menu's *Off*.
 *
 * The hand-back is the toggle's own arithmetic ([toggledTool]), so the guides behave like the erasers:
 * putting the slot away returns to what the reader was using, never to the pen by decree.
 */
internal fun DesktopShellState.clearGuide() {
    tabs.active?.guide = null
    if (tools.current in GUIDE_TOOLS) selectTool(picks.toggleTarget(DesktopTool.SETSQUARE))
    openGroup = null
}

/** A **toggle** press: pick the family's tool, or put back the last one ([toggledTool]). */
internal fun DesktopShellState.toggleTool(target: DesktopTool) {
    selectTool(picks.toggleTarget(target))
}

/** `Alt + Down`: open the picked tool's group picker, or close the one that is up. */
internal fun DesktopShellState.toggleToolPicker() {
    openGroup = toggledPicker(openGroup, tools)
}

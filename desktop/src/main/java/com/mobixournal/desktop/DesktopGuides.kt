package com.mobixournal.desktop

import com.mobixournal.design.ToolGlyph
import com.mobixournal.render.DrawingGuide
import com.mobixournal.render.GuideKind
import com.mobixournal.render.guideKind

/**
 * The window's **drawing guides**: the setsquare and compass the tablet lays against the page, as the PC
 * has them.
 *
 * A guide is **view state, never document content**. That is the tablet's own answer — `DrawingGuide`
 * states it, and what lands in `.mobix` or `.xopp` is an ordinary stroke — so the pose lives on the tab,
 * beside its viewport and its selection: it is not written to either format, undoing an edit never has to
 * restore it, and closing the tab drops it. What it does to the ink is [project].
 */

/** A placed instrument: the guide itself, and the page it is pinned to. */
data class DesktopGuidePose(
    /** The page the instrument stands on — it constrains strokes on that page and no other. */
    val page: Int,
    val guide: DrawingGuide,
) {
    /** Which instrument is standing there — the guides slot's face. */
    val kind: GuideKind get() = guide.guideKind
}

/**
 * The two instruments the window's guides slot offers — the tablet's chooser trimmed to this window's
 * scope. The protractor, the ruling snap and the chemistry lattice stay the tablet's for now.
 */
val DESKTOP_GUIDE_KINDS: List<GuideKind> = listOf(GuideKind.SETSQUARE, GuideKind.COMPASS)

/** The instrument [tool] lays down, or null for a slot that is not one. */
internal fun guideKindOf(tool: DesktopTool): GuideKind? = when (tool) {
    DesktopTool.SETSQUARE -> GuideKind.SETSQUARE
    DesktopTool.COMPASS -> GuideKind.COMPASS
    else -> null
}

/**
 * A freshly placed instrument of [kind] standing on [page], centred on [centre].
 *
 * The pose is the shared [GuideKind.place] arithmetic, so a setsquare placed here starts at the size and
 * angle the tablet's does rather than at a second opinion about how big a setsquare is.
 */
internal fun placedGuideOn(kind: GuideKind, page: Int, centre: Pair<Double, Double>): DesktopGuidePose? =
    kind.place(centre.first, centre.second)?.let { DesktopGuidePose(page, it) }

/**
 * ([x], [y]) on [page] pulled onto the placed guide's nearest drawing edge, or answered unchanged when no
 * guide is placed or the guide stands on another page.
 *
 * The reach limit is [DrawingGuide.project]'s own: a point further than `GRAB_PT` from an edge is the
 * hand's, which is what lets the pen leave the instrument without switching it off.
 */
fun DesktopGuidePose?.project(page: Int, x: Double, y: Double): Pair<Double, Double> =
    if (this != null && this.page == page) guide.project(x, y) else x to y

/**
 * The face a guides slot wears: the instrument that is **placed**, whether or not its own member is the
 * one picked, so a reader who went back to the pen can still see which instrument is on the sheet.
 *
 * Null for every other slot, which draws its own glyph.
 */
internal fun guideSlotGlyph(tool: DesktopTool, placed: GuideKind?): ToolGlyph? = when (tool) {
    DesktopTool.SETSQUARE -> if (placed == GuideKind.COMPASS) ToolGlyph.COMPASS else ToolGlyph.SETSQUARE
    DesktopTool.COMPASS -> ToolGlyph.COMPASS
    else -> null
}

/**
 * What the guides slot draws and the three commands it answers: which instrument is on the page, placing
 * one, and putting it away — the *Off* row of the tablet's menu.
 *
 * Bundled into [ToolStripState] like the figures' own answers, so the rail's slot and the ribbon's cannot
 * show different instruments.
 */
class GuideStrip(
    /** The instrument standing on the active tab, or null when none is. */
    val placed: GuideKind? = null,
    /** Place [kind] on the active tab's page, where the reader is looking. */
    val onPlace: (GuideKind) -> Unit = {},
    /** Take the instrument away and put the last acting tool back in hand. */
    val onOff: () -> Unit = {},
)

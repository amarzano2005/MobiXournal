package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.ToolStripState

import com.mobixournal.design.ToolGlyph
import com.mobixournal.render.DrawingGuide
import com.mobixournal.render.GuideKind
import com.mobixournal.render.guideKind
import kotlin.math.atan2
import kotlin.math.roundToInt

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
 * The **aim** an instrument is holding, in radians: the direction of its handle from its anchor.
 *
 * For the setsquare that is its long leg, so the readout is the angle the triangle is turned to; for the
 * compass it is the bearing of its pencil point, which is the direction the hand opened it toward. One
 * reading for both keeps the pill a number rather than a table of what each instrument means.
 */
fun DesktopGuidePose.aimRadians(): Double {
    val (hx, hy) = guide.grabHandle()
    return atan2(hy - guide.y, hx - guide.x)
}

/**
 * The pill's text: ([radians]) as whole degrees, folded into `0..359` and followed by a degree sign.
 *
 * Whole degrees because that is the precision a hand can aim at and a setsquare is read in; folded
 * because a negative aim (`-90°` pointing up) must read as `270°` rather than as a minus sign, which
 * said "turn back" where the instrument was simply pointing the other way. `359.6°` rounds to `0°`
 * rather than to `360°`, so the readout never leaves the scale it is on.
 */
fun guideAimLabel(radians: Double): String {
    val degrees = Math.toDegrees(radians)
    val whole = Math.round(degrees).toInt() % 360
    return "${(whole + 360) % 360}\u00B0"
}

/**
 * An instrument **being re-posed right now**: the pose, and whether Shift is holding its aim on a step of
 * `Snapping.snapAngle`.
 *
 * Gesture state, not view state: it exists for the length of the drag, so the pill follows the tip and the
 * snap tick is painted only while the angle is actually snapped. The pose itself is written to the tab
 * frame by frame, which is what the sheet paints.
 */
data class GuideAim(val pose: DesktopGuidePose, val snapped: Boolean) {

    /**
     * What the pill reads: the aim in degrees for an instrument that turns, its opening for the compass.
     *
     * A compass has no angle of its own — its handle always lies to the right of the hub, so a degree
     * readout would print `0\u00B0` while the hand opened it downward, which is a number that is true and
     * useless. What the hand is setting there is the radius, so that is what the pill says.
     */
    val label: String
        get() = when (val guide = pose.guide) {
            is DrawingGuide.Compass -> "${guide.radius.roundToInt()} pt"
            else -> guideAimLabel(pose.aimRadians())
        }

    /** The point the pill follows, in page pt — the instrument's own handle. */
    val tip: Pair<Double, Double> get() = pose.guide.grabHandle()

    /** The aim in radians, for the snap tick's direction. */
    val radians: Double get() = pose.aimRadians()
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

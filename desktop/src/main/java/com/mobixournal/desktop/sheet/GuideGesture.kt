package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.of

import com.mobixournal.render.DrawingGuide
import com.mobixournal.render.hasHandle
import kotlin.math.hypot

/**
 * The **hand on the instrument**: which part of a placed setsquare or compass a press took, and what a
 * drag does to its pose.
 *
 * The rules are the tablet's, not a second reading of them: which point is the handle and which region is
 * the body is `DrawingGuide.grabHandle`/`grabbableBody`, and the re-pose itself is the shared
 * `Setsquare.aimedAt`/`Compass.openedTo` — with `Snapping.snapAngle` when the hand holds Shift, so 0, 45
 * and 90 degrees land exactly as they do on the slate.
 *
 * Plain Kotlin, no Compose types: a JVM test can pose an instrument without a window.
 */

/** Which part of the instrument a press landed on. */
internal enum class GuideGrab { NONE, BODY, TIP }

/**
 * How near, in **screen pixels**, a press has to be to the handle to take it.
 *
 * Converted to page points by [guideReachPt] at the zoom in force, the same way `LivingGrips.gripAt`
 * turns a finger's reach into geometry: a handle that is easy to hit at 30 % must not be the size of a
 * coin at 300 %.
 */
internal const val GUIDE_HANDLE_PX = 24f

/** The handle's reach in page points at [zoom] and [density]. */
internal fun guideReachPt(zoom: Float, density: Float): Double =
    (GUIDE_HANDLE_PX / (zoom * density).coerceAtLeast(0.01f)).toDouble()

/**
 * Which part of [pose] a press at ([px], [py]) page pt took: the handle first, the body second, nothing
 * when it missed.
 *
 * A guide pinned to another page is not there to be grabbed, and the ruling/chemistry rules have nothing
 * to hold — both answer [GuideGrab.NONE], so a press on the sheet keeps drawing or panning.
 */
internal fun guidePress(
    pose: DesktopGuidePose?,
    page: Int,
    px: Double,
    py: Double,
    reachPt: Double,
): GuideGrab {
    val guide = pose?.takeIf { it.page == page }?.guide ?: return GuideGrab.NONE
    // The invisible rules have an anchor and nothing to hold, so they are not there to be grabbed.
    if (!guide.hasHandle) return GuideGrab.NONE
    val (handleX, handleY) = guide.grabHandle()
    if (hypot(px - handleX, py - handleY) <= reachPt) return GuideGrab.TIP
    return if (guide.grabbableBody(px, py, reachPt)) GuideGrab.BODY else GuideGrab.NONE
}

/**
 * The pose a drag makes, computed from the pose **as it was at grab time** — never from the last frame, so
 * a drag cannot compound its own error.
 *
 * A [GuideGrab.BODY] drag slides the instrument with the hand ([grabDx]/[grabDy] are the offset between
 * the press and the anchor, so the body does not jump under it); a [GuideGrab.TIP] drag turns the
 * setsquare (aim and length), opens the compass, or turns the protractor — the shared functions.
 * [snapAngle] is Shift: the angle lands on a step of `Snapping.snapAngle` rather than where the hand
 * stopped.
 */
internal fun guideDragged(
    start: DesktopGuidePose,
    grab: GuideGrab,
    grabDx: Double,
    grabDy: Double,
    px: Double,
    py: Double,
    snapAngle: Boolean,
): DesktopGuidePose {
    val guide = start.guide
    val posed = when {
        grab == GuideGrab.BODY -> guide.moved(px - grabDx - guide.x, py - grabDy - guide.y)
        guide is DrawingGuide.Setsquare -> guide.aimedAt(px, py, snapAngle)
        guide is DrawingGuide.Compass -> guide.openedTo(px, py)
        guide is DrawingGuide.Protractor -> guide.aimedAt(px, py, snapAngle)
        else -> guide
    }
    return start.copy(guide = posed)
}

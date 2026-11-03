package com.mobixournal.desktop.sheet

import androidx.compose.ui.geometry.Offset
import com.mobixournal.render.RadialHud
import kotlin.math.hypot

/**
 * The window's **pen-tip ring** — the desktop half of `docs/ui-modernization.md`, *The pen-tip HUD*.
 *
 * The geometry is the tablet's (`RadialHud` in `mobix-core`): the angles, the seam, the dead zone and the
 * slot band. What is the window's own is the state — where the ring is open, in canvas pixels — and the
 * **faces**: a [RingFace], which can be any tool of the window, not only the document's three writing tools.
 *
 * There is one way to open it — a key, or a pen button a driver turns into one — so there is one way to
 * close it: a press commits the face under it, a press inside the ring but off a face does nothing, and a
 * press well outside it or `Esc` puts it away. A ring is never held, so the release of the press that
 * opened it cannot commit anything (that flash of open-and-shut is what a held ring got wrong).
 *
 * Plain Kotlin over Ints and Floats, so every rule here is a JVM test (`DesktopRingTest`); the drawing is
 * [drawDesktopRadialHud]'s, the state [com.mobixournal.desktop.shell.DesktopRing]'s, and the faces
 * [RingContents]'s.
 */
internal data class DesktopHudState(
    val cx: Float,
    val cy: Float,
    /** The face under the pointer, or null when it is over none. */
    val active: Int? = null,
)

/**
 * The ring, as one value the workspace hands a canvas: whether it is up, what it holds, where the nib is.
 *
 * Its readings are lambdas rather than the state itself, because the canvas's pointer handler outlives a
 * recomposition: a captured `DesktopHudState?` would answer "closed" for the ring the shell had already
 * opened, where a live read answers the ring as it is when the press arrives. Every member has a default, so
 * a canvas with no shell behind it (a render, a probe) simply has nothing floating over the page.
 */
internal class HudUi(
    val open: () -> DesktopHudState? = { null },
    /** True when the ring took the press, in which case the page writes nothing. */
    val onPress: (x: Float, y: Float, density: Float) -> Boolean = { _, _, _ -> false },
    val slots: List<RingFace> = emptyList(),
    /** The nib's place, in canvas pixels, reported to the shell that anchors the ring there. */
    val onPointerAt: (at: Offset, density: Float) -> Unit = { _, _ -> },
    /**
     * True from the press the ring answered until that contact is over — its lift in either stream, or the
     * copy of it that the page swallows.
     *
     * One contact reaches this window **twice** — the driver's packets and Windows' copy of them as a mouse —
     * and the ring closes the moment it commits. The copy then arrives with nothing on screen, so without this
     * latch it reads as a page press: a click on a face inked the sheet under the ring it had just dismissed.
     */
    val pressHeld: () -> Boolean = { false },
    /** That contact is over, whichever stream saw its lift first. */
    val onRelease: () -> Unit = {},
)

/** The ring's geometry and its two pointer rules. Its faces are [RingContents]'s. */
internal object DesktopHud {

    /** Air between the slot band and the glass disc's edge, in dp — the tablet's own `HUD_AIR_DP`. */
    const val AIR_DP = 10f

    /** The ring opened at ([cx], [cy]) in the canvas's pixels. */
    fun openAt(cx: Float, cy: Float): DesktopHudState = DesktopHudState(cx = cx, cy = cy)

    /** The same ring with the face under ([x], [y]) lit — null when the pointer is over none. */
    fun tracked(
        state: DesktopHudState,
        x: Float,
        y: Float,
        count: Int,
        density: Float,
    ): DesktopHudState = state.copy(active = indexAt(state, x, y, count, density))

    /**
     * The face a press at ([x], [y]) commits, or null when it commits nothing: inside the dead zone, or off
     * the slot band. The arithmetic is `RadialHud.slotIndexAt`'s — this only converts the dp constants into
     * the canvas's pixels.
     */
    fun indexAt(
        state: DesktopHudState,
        x: Float,
        y: Float,
        count: Int,
        density: Float,
    ): Int? = RadialHud.slotIndexAt(
        cx = state.cx,
        cy = state.cy,
        x = x,
        y = y,
        count = count,
        ringRadiusPx = RadialHud.RING_RADIUS_DP * density,
        slotRadiusPx = RadialHud.SLOT_DIAMETER_DP * density / 2f,
        deadZonePx = RadialHud.DEAD_ZONE_DP * density,
    )

    /**
     * Whether ([x], [y]) is aimed **at the ring** — the glass disc plus its air — as opposed to well outside
     * it. A press that lands here but off a face was aimed at the ring and does nothing; one outside it is a
     * deliberate "not now" and closes the ring.
     */
    fun within(
        state: DesktopHudState,
        x: Float,
        y: Float,
        density: Float,
    ): Boolean {
        val reach = (RadialHud.RING_RADIUS_DP + RadialHud.SLOT_DIAMETER_DP / 2f + AIR_DP) * density
        return hypot(x - state.cx, y - state.cy) <= reach
    }
}

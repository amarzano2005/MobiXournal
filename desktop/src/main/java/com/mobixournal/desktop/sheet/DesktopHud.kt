package com.mobixournal.desktop.sheet

import androidx.compose.ui.geometry.Offset
import com.mobixournal.desktop.ink.PenBarrel
import com.mobixournal.design.PenPalette
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.format.model.Tool
import com.mobixournal.render.HudSlot
import com.mobixournal.render.RadialHud
import kotlin.math.hypot

/**
 * The window's **pen-tip ring** — the desktop half of `docs/ui-modernization.md`, *The pen-tip HUD*.
 *
 * It is the tablet's ring, not a second one: the order, the cap, the angles, the seam and the dead zone all
 * come from `RadialHud` (`mobix-core`), and this file only adds the two things a window has that a tablet
 * does not — the ring's own **open state** in canvas pixels, and the window's vocabulary for what a slot
 * commits ([DesktopTool] instead of the document's [Tool]).
 *
 * [DesktopHudState.cx]/[cy] are the canvas's pixels, because that is where the nib is: the pointer over the
 * page is reported by the canvas ([PageCanvas]'s `onPointerAt`) and the ring is anchored at the last reading,
 * so a flick is measured from the pen rather than from a palette parked on the screen.
 *
 * [DesktopHudState.picker] is how it will close. A ring opened with a **key** has no held button to release,
 * so it is a picker: a press commits a slot, a press inside the ring does nothing, a press well outside it or
 * `Esc` closes it. A ring opened by the **pen's button** (task `summon-the-ring-from-the-pen-button`) is held,
 * so its release commits and a tip that lands cancels it — the tablet's `HudTrigger`.
 *
 * Plain Kotlin over Ints and Floats, so every rule here is a JVM test (`DesktopRingTest`); the drawing is
 * [drawDesktopRadialHud]'s and the state is [com.mobixournal.desktop.shell.DesktopRing]'s.
 */
internal data class DesktopHudState(
    val cx: Float,
    val cy: Float,
    /** The slot under the pointer, or null when it is over no slot. */
    val active: Int? = null,
    /** True for a ring opened by the keyboard (commit on a press); false for the pen's held ring. */
    val picker: Boolean = true,
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
    val slots: List<HudSlot> = emptyList(),
    /** The nib's place, in canvas pixels, reported to the shell that anchors the ring there. */
    val onPointerAt: (at: Offset, density: Float) -> Unit = { _, _ -> },
    /**
     * The pen's **side button**, when the window gives it to the ring (`PenButton.HUD`) — null when it keeps
     * erasing, which is the default and the window's behaviour before the ring existed.
     */
    val barrel: PenBarrel? = null,
    /**
     * True while the **secondary button erases** — the default — and false when the button is the ring's.
     *
     * A lambda, not a value: the canvas's pointer handler is built once per gesture and outlives a
     * recomposition, so a setting read at composition would keep the old answer until the next gesture was
     * rebuilt. Windows' emulation reports the barrel as the secondary button, and the router must not hear it
     * as the eraser while the packets are using it as the summon.
     */
    val barrelErases: () -> Boolean = { true },
)

/** The ring's contents, its geometry and its two pointer rules. */
internal object DesktopHud {

    /**
     * The three writing tools the ring offers — the tablet's own three.
     *
     * All three are in [com.mobixournal.desktop.tools.DESKTOP_TOOLS_THAT_ACT], and a test says so: a slot
     * whose tool does nothing would be worse than an absent one, which is the rule the strips already keep.
     */
    val TOOLS: List<DesktopTool> = listOf(
        DesktopTool.PEN,
        DesktopTool.HIGHLIGHTER,
        DesktopTool.ERASER,
    )

    /** Air between the slot band and the glass disc's edge, in dp — the tablet's own `HUD_AIR_DP`. */
    const val AIR_DP = 10f

    /**
     * The ring's contents, in the tablet's order: the user's palette in their stored order (the editable
     * custom slot last among the swatches), then the three widths, then the three tools.
     */
    fun slots(
        palette: List<Int>,
        custom: Int?,
        widths: List<Float> = PenPalette.DEFAULT_WIDTHS,
        tools: List<DesktopTool> = TOOLS,
    ): List<HudSlot> = RadialHud.slots(palette, custom, widths, tools.mapNotNull(::documentTool))

    /** The document tool a window slot stands for, or null for a slot that is not a writing tool. */
    fun documentTool(tool: DesktopTool): Tool? = when (tool) {
        DesktopTool.PEN -> Tool.PEN
        DesktopTool.HIGHLIGHTER -> Tool.HIGHLIGHTER
        DesktopTool.ERASER -> Tool.ERASER
        else -> null
    }

    /** The window slot a ring face picks, or null when the face is a colour or a width. */
    fun slotOf(tool: Tool): DesktopTool? = when (tool) {
        Tool.PEN -> DesktopTool.PEN
        Tool.HIGHLIGHTER -> DesktopTool.HIGHLIGHTER
        Tool.ERASER -> DesktopTool.ERASER
    }

    /** The ring opened at ([cx], [cy]) in the canvas's pixels. */
    fun openAt(cx: Float, cy: Float, picker: Boolean = true): DesktopHudState =
        DesktopHudState(cx = cx, cy = cy, picker = picker)

    /** The same ring with the slot under ([x], [y]) lit — null when the pointer is over none. */
    fun tracked(
        state: DesktopHudState,
        x: Float,
        y: Float,
        count: Int,
        density: Float,
    ): DesktopHudState = state.copy(active = indexAt(state, x, y, count, density))

    /**
     * The slot a press or a release at ([x], [y]) commits, or null when it commits nothing: inside the dead
     * zone, or off the slot band. The arithmetic is `RadialHud.slotIndexAt`'s — this only converts the dp
     * constants into the canvas's pixels.
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
     * it. A press that lands here but off a slot was aimed at the ring and does nothing; one outside it is a
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

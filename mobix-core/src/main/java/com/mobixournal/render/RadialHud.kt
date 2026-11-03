package com.mobixournal.render

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One slot of the pen-tip radial HUD (`docs/ui-modernization.md`, *The pen-tip HUD*): the thing a
 * flick either commits or does not. Three kinds and no more — the ring is a writing control, not a
 * second toolbar, so it holds the palette, the three pen widths and the three writing tools, and the
 * rail keeps everything else.
 */
sealed interface HudSlot {

    /** A palette swatch, including the editable custom slot. */
    data class Colour(val argb: Int) : HudSlot

    /** One of the pen's S/M/L widths, in pt. */
    data class Width(val pt: Float) : HudSlot

    /**
     * A tool face, named in the vocabulary **both** shells share ([RingTool]): the tablet maps it to its
     * `EditorTool`, the window to its `DesktopTool`, so one ring can hold the same six tools on either.
     */
    data class ToolFace(val tool: RingTool) : HudSlot
}

/**
 * The tools a ring face can hold, in a vocabulary the two shells can both name.
 *
 * It is a *ring's* list rather than a toolbar's: the six faces the factory ring carries — a figure, the
 * two selections and the three writing tools. Each shell maps a name to its own enum (the tablet's
 * `EditorTool.LASSO_SELECT` and `SELECT`, the window's `DesktopTool.LASSO` and `MARQUEE` are the same two
 * selections under different names), so the ring's order is one fact and the tools are each shell's.
 */
enum class RingTool {
    PEN,
    HIGHLIGHTER,
    ERASER,
    LINE,
    LASSO,
    MARQUEE,
}

/**
 * The HUD's layout and its angle-to-slot arithmetic, as plain Kotlin: no `Canvas`, no `View`, so the
 * geometry is unit-tested on the JVM (`RadialHudTest`) instead of by eye on a tablet.
 *
 * The ring is fixed — the slot count follows from the slots supplied, never from how far the pen
 * moved — so a swatch is always in the same direction from the nib. Slot 0 sits at the **top** and
 * the ring runs clockwise, which is how the flick is read: [slotIndexAt] snaps the release's angle to
 * the nearest slot centre, so the boundary between two slots is exactly halfway between them and the
 * ring's seam (last slot back to the first) needs no special case.
 *
 * A release commits only when it lands **on the ring**: inside [DEAD_ZONE_DP] of the nib, or past the
 * slot band, [slotIndexAt] answers null and the release cancels. That is what makes "release outside
 * cancels" (the task's own wording) a property of the geometry rather than a branch somebody can
 * forget.
 */
object RadialHud {

    /** One slot's diameter, in dp — the contract's tool slot ([com.mobixournal.design.DesignTokens]). */
    const val SLOT_DIAMETER_DP = 40f

    /**
     * The distance from the nib to each slot's centre, in dp. Big enough that sixteen 40 dp slots
     * clear each other around the ring with a hairline of air between them
     * (`2 · 116 · sin(π/16) ≈ 45.2 dp` of arc per slot), small enough that the whole ring stays
     * inside the reach of a thumb-and-pen grip.
     */
    const val RING_RADIUS_DP = 116f

    /** A release this close to the nib belongs to no slot: the ring's centre is a dead zone. */
    const val DEAD_ZONE_DP = 32f

    /** Sixteen slots: ten swatches, three widths and the three writing tools. */
    const val MAX_SLOTS = 16

    /** The palette is capped so the widths and tools always have room on the ring. */
    const val MAX_PALETTE_SLOTS = 10

    /**
     * How many colours the **factory** ring carries: the palette's first five, black through orange.
     *
     * A ring is a flick, not a palette: five swatches are what a hand can tell apart without looking,
     * and the strips' own flyout is where the rest of the palette lives. Both shells draw the same five
     * (`factoryColours`), so a fresh install rings identically on the tablet and on the window.
     */
    const val FACTORY_COLOUR_SLOTS = 5

    /**
     * The six tools the factory ring carries, clockwise after the widths.
     *
     * A figure, the two selections and the three writing tools, ending on the **pen** so the tool a
     * writer returns to most often sits beside the colours at the top of the ring.
     */
    val FACTORY_TOOLS: List<RingTool> = listOf(
        RingTool.LINE,
        RingTool.LASSO,
        RingTool.MARQUEE,
        RingTool.HIGHLIGHTER,
        RingTool.ERASER,
        RingTool.PEN,
    )

    /** The palette as the factory ring draws it: distinct, and no more than [FACTORY_COLOUR_SLOTS]. */
    fun factoryColours(colours: List<Int>): List<Int> = colours.distinct().take(FACTORY_COLOUR_SLOTS)

    /**
     * **The factory ring**, in one call: [factoryColours]'s five (the custom colour kept last among them),
     * the pen's three widths as the shell stores them, and [FACTORY_TOOLS].
     *
     * Both shells build their automatic ring here, so a fresh install rings the same on the tablet and on
     * the window — and a reader's own palette cannot quietly grow the ring past the five colours a flick can
     * tell apart. A custom colour that is not already among them takes the fifth place rather than a sixth.
     */
    fun factorySlots(colours: List<Int>, custom: Int?, widths: List<Float>): List<HudSlot> {
        val palette = factoryColours(colours)
        val customIsNew = custom != null && custom !in palette
        val room = if (customIsNew) FACTORY_COLOUR_SLOTS - 1 else FACTORY_COLOUR_SLOTS
        val chosen = palette.take(room) + listOfNotNull(custom?.takeIf { customIsNew })
        return slots(chosen, null, widths, FACTORY_TOOLS)
    }

    /**
     * Build the ring: the palette first (in the user's own order, with the editable custom colour
     * last), then the widths, then the tools. The custom slot is the ring's whole excuse for being
     * one flick — a HEX colour that previously had no one-tap home — so it is kept even when the
     * palette is already at [MAX_PALETTE_SLOTS] by dropping the palette's last stored swatch.
     */
    fun slots(
        colors: List<Int>,
        customColor: Int?,
        widths: List<Float>,
        tools: List<RingTool>,
    ): List<HudSlot> {
        val ring = ArrayList<HudSlot>(MAX_SLOTS)
        val paletteCap = (MAX_SLOTS - widths.size - tools.size).coerceIn(0, MAX_PALETTE_SLOTS)
        if (paletteCap > 0) {
            val distinct = colors.distinct()
            val customIsNew = customColor != null && customColor !in distinct
            val room = if (customIsNew) (paletteCap - 1).coerceAtLeast(0) else paletteCap
            for (color in distinct.take(room)) ring += HudSlot.Colour(color)
            if (customIsNew) ring += HudSlot.Colour(customColor!!)
        }
        for (width in widths) ring += HudSlot.Width(width)
        for (tool in tools) ring += HudSlot.ToolFace(tool)
        return ring.take(MAX_SLOTS)
    }

    /** Slot [index]'s centre angle, in degrees clockwise from straight up, for a ring of [count]. */
    fun slotAngleDegrees(index: Int, count: Int): Float {
        if (count <= 0) return 0f
        return index * 360f / count
    }

    /** The centre of slot [index], in view pixels, for a ring centred at ([cx], [cy]). */
    fun slotCenter(
        cx: Float,
        cy: Float,
        index: Int,
        count: Int,
        ringRadiusPx: Float,
    ): Pair<Float, Float> {
        // -90° puts slot 0 straight up; adding the clockwise angle walks the ring to the right.
        val radians = Math.toRadians((slotAngleDegrees(index, count) - 90f).toDouble())
        return (cx + (cos(radians) * ringRadiusPx).toFloat()) to
            (cy + (sin(radians) * ringRadiusPx).toFloat())
    }

    /**
     * The slot a release at ([x], [y]) commits, or null when it commits nothing: inside the dead
     * zone, or off the slot band. The angle is snapped to the nearest slot centre, so crossing a
     * boundary flips the slot and crossing the seam from the last slot to the first is the same
     * arithmetic as any other pair.
     */
    fun slotIndexAt(
        cx: Float,
        cy: Float,
        x: Float,
        y: Float,
        count: Int,
        ringRadiusPx: Float,
        slotRadiusPx: Float,
        deadZonePx: Float = DEAD_ZONE_DP,
    ): Int? {
        if (count <= 0) return null
        val dx = x - cx
        val dy = y - cy
        val distance = hypot(dx, dy)
        if (distance < deadZonePx) return null
        if (abs(distance - ringRadiusPx) > slotRadiusPx) return null
        val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val clockwise = ((degrees + 90f) % 360f + 360f) % 360f
        val step = 360f / count
        return ((clockwise / step).roundToInt() % count + count) % count
    }
}

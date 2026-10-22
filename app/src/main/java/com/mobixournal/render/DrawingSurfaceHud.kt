/**
 * The pen-tip radial HUD's state and its half of the input path: the summon, the flick that tracks a
 * slot, and the release that commits one or cancels.
 *
 * The HUD is a **hover** gesture (docs/ui-modernization.md, *The pen-tip HUD*): the barrel button is
 * pressed with the tip off the glass, the pen moves until the slot it wants is under it, and the
 * release commits that slot. Nothing is drawn at the nib while the tip is down, so a flick can never
 * leave a stroke; a tip that lands while the ring is open cancels it instead (see `handleTouch`).
 *
 * The geometry itself lives in [RadialHud] — plain Kotlin, tested on the JVM — and this file is the
 * view's adapter over it: pixels from the view's density, a repaint, and a haptic tick per boundary.
 */
package com.mobixournal.render

import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import com.mobixournal.design.ToolGlyph
import com.mobixournal.format.model.Tool
import kotlin.math.sqrt

/** The open ring: where its centre is, and the slot the pen is over (null when over no slot). */
internal data class HudState(val cx: Float, val cy: Float, val active: Int? = null)

/** True when Settings → Stylus → Barrel button → *While held* is set to the HUD. */
internal val DrawingSurfaceView.barrelSummonsHud: Boolean
    get() = inputSettings.barrelAction == BarrelAction.HUD

/**
 * Where the ring is anchored: the nib as the last hover reported it. A pen that only sends key
 * events has no hover position, so the last one is still the best answer — and the view centre is
 * the fallback for a session where the pen has never hovered.
 */
internal fun DrawingSurfaceView.nibAnchor(): Pair<Float, Float> =
    if (hovering) hoverX to hoverY else if (hoverX != 0f || hoverY != 0f) hoverX to hoverY
    else width / 2f to height / 2f

/**
 * Open the ring at the nib, or close it without committing when it is already open — the "second
 * press dismisses" half of the gesture. A press that arrives while the tip is on the glass is
 * ignored: the ring is a hover surface, and opening it mid-stroke would steal the pen from the page.
 */
internal fun DrawingSurfaceView.summonRadialHud() {
    if (hud != null) {
        dismissRadialHud(commit = false)
        return
    }
    if (hudSlots.isEmpty()) return
    if (current != null || erasing || placing || scrolling) return
    val (x, y) = nibAnchor()
    hud = HudState(x, y)
    render()
}

/** Track the pen over the open ring: the slot under it lights up, with a tick per boundary crossed. */
internal fun DrawingSurfaceView.trackRadialHud(x: Float, y: Float) {
    val open = hud ?: return
    val density = resources.displayMetrics.density
    val index = RadialHud.slotIndexAt(
        cx = open.cx,
        cy = open.cy,
        x = x,
        y = y,
        count = hudSlots.size,
        ringRadiusPx = RadialHud.RING_RADIUS_DP * density,
        slotRadiusPx = RadialHud.SLOT_DIAMETER_DP * density / 2f,
        deadZonePx = RadialHud.DEAD_ZONE_DP * density,
    )
    if (index == open.active) return
    hud = open.copy(active = index)
    // A tick on each boundary crossing is what makes the ring readable without looking at it; a
    // release into the dead zone or off the ring has no slot to tick. `performHapticFeedback`
    // already answers the device's own haptics preference, so there is no second switch to keep.
    if (index != null && hudHaptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    render()
}

/**
 * Take the ring off screen. [commit] is the release: the slot under the pen runs, through the one
 * callback the editor owns; a cancelled gesture passes false and changes nothing.
 *
 * The ring is cleared and repainted *before* the callback runs, so a tool switch's own repaint
 * cannot draw a stale ring over the page.
 */
internal fun DrawingSurfaceView.dismissRadialHud(commit: Boolean) {
    val open = hud ?: return
    hud = null
    render()
    if (!commit) return
    val slot = open.active?.let { hudSlots.getOrNull(it) } ?: return
    onHudPick?.invoke(slot)
}

// --- the pen-tip radial HUD ---------------------------------------------------------------

/**
 * Paint the open ring: the floating glass disc, the hairline ring, one face per slot and the accent
 * ring around the slot the pen is over. Everything rides the canvas chrome ([CanvasChrome]) and the
 * theme's floating fill and ink, so the HUD is the same material as a menu — it is a floating
 * surface that happens to be round, not a scrim laid over the page.
 *
 * Faces are read at a glance, which is the point of the ring: a swatch is its own colour, a width is
 * a dot whose radius is the width, and a tool is its catalogue glyph ([drawToolGlyph]).
 */
internal fun DrawingSurfaceView.drawRadialHud(canvas: Canvas) {
    val open = hud ?: return
    val count = hudSlots.size
    if (count == 0) return
    val density = resources.displayMetrics.density
    val slotRadius = RadialHud.SLOT_DIAMETER_DP * density / 2f
    val ringRadius = RadialHud.RING_RADIUS_DP * density
    canvas.drawCircle(open.cx, open.cy, ringRadius + slotRadius + HUD_AIR_DP * density, chrome.hudSurface)
    canvas.drawCircle(open.cx, open.cy, ringRadius, chrome.hudRim)
    for (index in 0 until count) {
        val (x, y) = RadialHud.slotCenter(open.cx, open.cy, index, count, ringRadius)
        val picked = open.active == index
        // The picked slot sits on the contract's steel plate, so an ink-coloured glyph or a thin
        // width dot still reads against the glass.
        if (picked) canvas.drawCircle(x, y, slotRadius, chrome.hudPlate)
        when (val slot = hudSlots[index]) {
            is HudSlot.Colour -> {
                chrome.hudSwatch.color = slot.argb
                canvas.drawCircle(x, y, slotRadius - 2f * density, chrome.hudSwatch)
                canvas.drawCircle(x, y, slotRadius - 2f * density, chrome.hudSwatchRim)
            }
            is HudSlot.Width -> canvas.drawCircle(x, y, widthDotRadius(slot.pt, density), chrome.hudInk)
            is HudSlot.ToolFace -> drawToolGlyph(
                canvas,
                glyphFor(slot.tool),
                x,
                y,
                slotRadius,
                chrome.hudGlyph,
                chrome.hudGlyphPath,
            )
        }
        if (picked) canvas.drawCircle(x, y, slotRadius + 3f * density, chrome.hudActive)
    }
    // The nib marker: the ring is anchored *here*, which is what makes it a control at the pen
    // rather than a palette somewhere on the screen.
    canvas.drawCircle(open.cx, open.cy, 3f * density, chrome.hudInk)
}

/** Air between the slot band and the glass disc's edge, in dp. */
private const val HUD_AIR_DP = 10f

/** A width slot's dot: 2.5 dp at the thinnest tip, about 12 dp at the widest. */
private fun widthDotRadius(pt: Float, density: Float): Float =
    (2.5f + 9.5f * sqrt((pt / 15f).coerceIn(0.1f, 1f))) * density

/** The catalogue glyph for a writing tool; anything else shows the pen, the ring's default. */
private fun glyphFor(tool: Tool): ToolGlyph = when (tool) {
    Tool.HIGHLIGHTER -> ToolGlyph.HIGHLIGHTER
    Tool.ERASER -> ToolGlyph.ERASER
    else -> ToolGlyph.PEN
}

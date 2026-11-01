package com.mobixournal.desktop.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawToolGlyph
import com.mobixournal.design.DesignTokens
import com.mobixournal.design.PenPalette
import com.mobixournal.render.HudSlot
import com.mobixournal.render.RadialHud
import kotlin.math.sqrt

/**
 * The window's **ring**, painted — the desktop adapter over the shared geometry, beside the tablet's
 * `drawRadialHud`, which paints the same ring on its canvas.
 *
 * It is a **floating surface** (`docs/interface-contract.md` §1) that happens to be round: the glass at
 * `floatingPanelAlpha`, a hairline rim, the steel plate under the picked slot and the accent ring around it.
 * Every colour is a [DesktopTheme] token — no literal, which is what `DesktopThemeTest` sweeps — so the two
 * shells cannot show two materials, and the window's own window can be read on paper, on ink and on the black
 * desk.
 *
 * Faces are read without labels, exactly as on the tablet: a swatch is its own colour inside a rim (a white
 * swatch would otherwise vanish on the glass), a width is a dot of that width, and a tool is its
 * `ToolGlyphCatalogue` glyph through the window's adapter.
 *
 * Drawn into the page's canvas, in the canvas's own pixels: [DesktopHudState.cx]/[cy] are the nib.
 */
internal fun DrawScope.drawDesktopRadialHud(
    state: DesktopHudState,
    slots: List<HudSlot>,
    density: Float,
) {
    if (slots.isEmpty()) return
    val centre = Offset(state.cx, state.cy)
    val slotRadius = RadialHud.SLOT_DIAMETER_DP * density / 2f
    val ringRadius = RadialHud.RING_RADIUS_DP * density
    val hairline = (DesignTokens.HAIRLINE_STROKE_WIDTH_DP * density).toFloat()

    // The glass: one disc under the whole ring, which is what makes it a surface rather than a scatter of
    // buttons — and the rim that says where it ends.
    drawCircle(
        color = DesktopTheme.floating,
        radius = ringRadius + slotRadius + DesktopHud.AIR_DP * density,
        center = centre,
    )
    drawCircle(
        color = DesktopTheme.hairline,
        radius = ringRadius,
        center = centre,
        style = Stroke(width = hairline),
    )

    slots.forEachIndexed { index, slot ->
        val (x, y) = RadialHud.slotCenter(state.cx, state.cy, index, slots.size, ringRadius)
        val at = Offset(x, y)
        val picked = state.active == index
        // The picked slot sits on the steel plate, so a black swatch or a thin width dot still reads.
        if (picked) drawCircle(color = DesktopTheme.activeSlot, radius = slotRadius, center = at)
        when (slot) {
            is HudSlot.Colour -> {
                drawCircle(color = Color(slot.argb), radius = slotRadius - 2f * density, center = at)
                // A rim of chrome ink at 35 %: white would vanish on the light glass, black on a dark swatch.
                drawCircle(
                    color = DesktopTheme.chromeInk.copy(alpha = SWATCH_RIM_ALPHA),
                    radius = slotRadius - 2f * density,
                    center = at,
                    style = Stroke(width = hairline),
                )
            }
            is HudSlot.Width -> drawCircle(
                color = DesktopTheme.chromeInk,
                radius = widthDotRadius(slot.pt, density),
                center = at,
            )
            is HudSlot.ToolFace -> DesktopHud.slotOf(slot.tool)?.let { tool ->
                val side = slotRadius * GLYPH_BOX_SCALE
                drawToolGlyph(
                    tool = tool,
                    color = DesktopTheme.chromeInk,
                    side = side,
                    at = Offset(x - side / 2f, y - side / 2f),
                )
            }
        }
        if (picked) {
            drawCircle(
                color = DesktopTheme.accent,
                radius = slotRadius + 3f * density,
                center = at,
                style = Stroke(width = hairline),
            )
        }
    }

    // The nib marker: the ring is anchored *here*, which is what makes it a control at the pen.
    drawCircle(color = DesktopTheme.chromeInk, radius = 3f * density, center = centre)
}

/** The swatch's rim: chrome ink at 35 %, the tablet's `hudSwatchRim`. */
private const val SWATCH_RIM_ALPHA = 0.35f

/** A glyph drawn at 120 % of a slot's radius, which is the tablet's optical size inside its 40 dp face. */
private const val GLYPH_BOX_SCALE = 1.2f

/** A width slot's dot: 2.5 dp at the thinnest tip, about 12 dp at the widest — the tablet's own ramp. */
private fun widthDotRadius(pt: Float, density: Float): Float =
    (2.5f + 9.5f * sqrt((pt / PenPalette.WIDTH_MAX).coerceIn(0.1f, 1f))) * density

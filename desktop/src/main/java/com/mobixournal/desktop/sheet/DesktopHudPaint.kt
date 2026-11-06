package com.mobixournal.desktop.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawToolGlyph
import com.mobixournal.design.DesignTokens
import com.mobixournal.design.PenPalette
import com.mobixournal.render.RadialHud

/**
 * The window's **ring**, painted — the desktop adapter over the shared geometry, beside the tablet's
 * `drawRadialHud`, which paints the same ring on its canvas.
 *
 * It is a **floating surface** (`docs/interface-contract.md` §1) that happens to be round: the glass at
 * `floatingPanelAlpha`, a hairline rim, the steel plate under the picked face and the accent ring around it.
 * Every colour is a [DesktopTheme] token — no literal, which is what `DesktopThemeTest` sweeps.
 *
 * [drawRing] is the one painter, in the caller's pixels: the page over a page's canvas (with the nib marker
 * under it), and the Settings page's own drawing beside the rows that fill it. Two painters would be two
 * answers to "what does a face look like", which is how the tablet's and the window's rings would drift.
 *
 * Faces are read without labels, exactly as on the tablet: a swatch is its own colour inside a rim (a white
 * swatch would otherwise vanish on the glass), a width is a dot of that width, and a tool is its
 * `ToolGlyphCatalogue` glyph through the window's adapter — any tool, since a face is a [RingFace.Tool].
 */
internal fun DrawScope.drawDesktopRadialHud(
    state: DesktopHudState,
    faces: List<RingFace>,
    density: Float,
) {
    drawRing(
        centre = Offset(state.cx, state.cy),
        faces = faces,
        active = state.active,
        ringRadius = RadialHud.RING_RADIUS_DP * density,
        slotRadius = RadialHud.SLOT_DIAMETER_DP * density / 2f,
        hairline = (DesignTokens.HAIRLINE_STROKE_WIDTH_DP * density).toFloat(),
        air = DesktopHud.AIR_DP * density,
        nib = density,
    )
}

/**
 * The ring at ([centre]), in pixels: the glass disc, the rim, one face per slot and the accent around
 * [active]. [nib] is the size of the nib marker (0 for none) — a page draws it, a settings preview does not.
 */
internal fun DrawScope.drawRing(
    centre: Offset,
    faces: List<RingFace>,
    active: Int?,
    ringRadius: Float,
    slotRadius: Float,
    hairline: Float,
    air: Float,
    nib: Float = 0f,
) {
    if (faces.isEmpty()) return

    // The glass: one disc under the whole ring, which is what makes it a surface rather than a scatter of
    // buttons — and the rim that says where it ends.
    drawCircle(color = DesktopTheme.floating, radius = ringRadius + slotRadius + air, center = centre)
    drawCircle(color = DesktopTheme.hairline, radius = ringRadius, center = centre, style = Stroke(width = hairline))

    faces.forEachIndexed { index, face ->
        val (x, y) = RadialHud.slotCenter(centre.x, centre.y, index, faces.size, ringRadius)
        val at = Offset(x, y)
        val picked = active == index
        // The picked face sits on the steel plate, so a black swatch or a thin width dot still reads.
        if (picked) drawCircle(color = DesktopTheme.activeSlot, radius = slotRadius, center = at)
        when (face) {
            is RingFace.Colour -> {
                drawCircle(color = Color(face.argb), radius = slotRadius - 2f * hairline, center = at)
                // A rim of chrome ink at 35 %: white would vanish on the light glass, black on a dark swatch.
                drawCircle(
                    color = DesktopTheme.chromeInk.copy(alpha = SWATCH_RIM_ALPHA),
                    radius = slotRadius - 2f * hairline,
                    center = at,
                    style = Stroke(width = hairline),
                )
            }
            is RingFace.Width -> drawCircle(
                color = DesktopTheme.chromeInk,
                radius = widthDotRadius(face.pt, slotRadius),
                center = at,
            )
            is RingFace.Tool -> {
                val side = slotRadius * GLYPH_BOX_SCALE
                drawToolGlyph(
                    tool = face.tool,
                    color = DesktopTheme.chromeInk,
                    side = side,
                    at = Offset(x - side / 2f, y - side / 2f),
                )
            }
        }
        if (picked) {
            drawCircle(
                color = DesktopTheme.accent,
                radius = slotRadius + 3f * hairline,
                center = at,
                style = Stroke(width = hairline),
            )
        }
    }

    if (nib > 0f) drawCircle(color = DesktopTheme.chromeInk, radius = 3f * nib, center = centre)
}

/** The swatch's rim: chrome ink at 35 %, the tablet's `hudSwatchRim`. */
private const val SWATCH_RIM_ALPHA = 0.35f

/** A glyph drawn at 120 % of a slot's radius, which is the tablet's optical size inside its 40 dp face. */
private const val GLYPH_BOX_SCALE = 1.2f

/**
 * A width face's dot, in dp: the **shipped widths' own spread**, 6 dp at S and 14 dp at L.
 *
 * Measured against the pointer's whole range (0.5–15 pt) the three factory widths land 4.4, 5.4 and 6.5 dp
 * across, which the eye reads as one size — the exact thing the face exists to answer. Across 0.85–2.6 pt
 * they are 6, 10 and 14 dp, so S, M and L are told apart before a label is read. The Settings page's own
 * width dots draw the same ramp (`RingSettings.widthDot`).
 */
internal fun ringWidthDotDiameter(pt: Float): Float {
    val smallest = PenPalette.DEFAULT_WIDTHS.min()
    val largest = PenPalette.DEFAULT_WIDTHS.max()
    val t = ((pt - smallest) / (largest - smallest)).coerceIn(0f, 1f)
    return 6f + 8f * t
}

/** The radius a [ringWidthDotDiameter]'s dot is drawn at, scaled by the slot it sits in. */
private fun widthDotRadius(pt: Float, slotRadius: Float): Float =
    ringWidthDotDiameter(pt) * slotRadius / RadialHud.SLOT_DIAMETER_DP

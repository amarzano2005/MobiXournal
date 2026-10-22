package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens

/**
 * The floating glass's **edge**, as the tablet draws it.
 *
 * A floating surface in this app is not a filled box with a border. It wears one drawn edge: a hairline
 * shaded along its length — bright at the top, faint at the bottom, so the panel reads as lit from above
 * — and a feathered inner ring that walks that stroke into the fill instead of stopping at it. The
 * numbers are `DesignTokens.RIM_*` (`docs/ui-modernization.md`, the token block), the same ones
 * `Modifier.floatingRim()` in the Android module reads, so the two shells cannot disagree about how
 * bright an edge is: only about who draws it.
 *
 * The rim is drawn **after** the content, because an edge drawn behind reads as something the panel sits
 * on rather than as the panel's own boundary. A raised shadow is deliberately absent — a desktop `Popup`
 * is only as large as its panel, so the blur would land inside it, which is the same measurement that
 * made the tablet's menus take a rim instead of an elevation.
 */

/** [glassRim]'s stroke, for a surface that already knows its own radius and ink. */
internal fun DrawScope.drawGlassRim(cornerRadiusPx: Float, ink: Color) {
    val width = DesignTokens.RIM_WIDTH_DP.dp.toPx()
    // Half a hairline in from the edge: a stroke straddles its path, and the outer half would be spent on
    // the corner's own curve.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                ink.copy(alpha = DesignTokens.RIM_ALPHA_TOP),
                ink.copy(alpha = DesignTokens.RIM_ALPHA_BOTTOM),
            ),
            startY = 0f,
            endY = size.height,
        ),
        topLeft = Offset(width / 2f, width / 2f),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(cornerRadiusPx),
        style = Stroke(width = width),
    )
    // The feather: a second, wider and fainter stroke just inside the first, so the edge dissolves into
    // the fill rather than ending on a hard line.
    val innerWidth = width * DesignTokens.RIM_INNER_WIDTH_SCALE
    val innerInset = width + innerWidth / 2f
    if (size.width > innerInset * 2 && size.height > innerInset * 2) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    ink.copy(alpha = DesignTokens.RIM_ALPHA_TOP * DesignTokens.RIM_INNER_ALPHA_SCALE),
                    ink.copy(alpha = DesignTokens.RIM_ALPHA_BOTTOM * DesignTokens.RIM_INNER_ALPHA_SCALE),
                ),
                startY = 0f,
                endY = size.height,
            ),
            topLeft = Offset(innerInset, innerInset),
            size = Size(size.width - innerInset * 2, size.height - innerInset * 2),
            cornerRadius = CornerRadius(maxOf(0f, cornerRadiusPx - width)),
            style = Stroke(width = innerWidth),
        )
    }
}

/**
 * The family's edge on a surface that has its own fill and corner: draw the content, then the rim.
 *
 * Bands do **not** take this (`docs/chrome.md`): the header, the rail/ribbon, the sidebar and the status
 * bar are geometry of their own, not panels lying over the page.
 */
@Composable
internal fun Modifier.glassRim(
    cornerRadius: Dp = DesktopTheme.panelRadius,
    ink: Color = DesktopTheme.ink,
): Modifier {
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    return drawWithContent {
        drawContent()
        drawGlassRim(radiusPx, ink)
    }
}

/**
 * A floating surface: the fill, the corner and the edge in one modifier, so a panel cannot take the fill
 * and leave the edge behind — which is how the window's flyouts ended up as plain boxes next to a tablet
 * whose every pop-up wears a rim.
 *
 * The fill defaults to [DesktopTheme.floating] — the glass at `floatingPanelAlpha` — and that default is
 * the point: a floating surface is **translucent**, and what it lies over shows through it. The first
 * cut of this modifier filled the panels with [DesktopTheme.panel], which is the same RGB at full
 * opacity, so the window had the edge and none of the glass: over the desk the two are identical, and
 * over a white sheet the panel read as a solid charcoal card. Only a surface that has a written reason
 * to be solid passes `panel` (the failed-open card does: it straddles the desk-and-sheet edge, where a
 * translucent fill reads as two materials split down its middle).
 */
@Composable
internal fun Modifier.glassSurface(
    fill: Color = DesktopTheme.floating,
    cornerRadius: Dp = DesktopTheme.panelRadius,
): Modifier = clip(RoundedCornerShape(cornerRadius)).background(fill).glassRim(cornerRadius)

/**
 * The **sheen** that makes glass read as glass on a desk that cannot show its transparency.
 *
 * A vertical wash of white — [DesktopTheme.sheenTop] along the lit top edge falling to
 * [DesktopTheme.sheenBottom] at the bottom — painted **under** the content, above the fill. It is the
 * one ingredient the window was missing: over the true-black desk a near-black fill at 85 % and an
 * opaque one are the same pixels, so without a lit edge a band is a flat rectangle whatever its alpha
 * (`docs/ui-modernization.md`).
 */
internal fun Modifier.glassSheen(
    top: Color = DesktopTheme.sheenTop,
    bottom: Color = DesktopTheme.sheenBottom,
): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(colors = listOf(top, bottom), startY = 0f, endY = size.height))
}

/**
 * A **band** of chrome — the header, the status bar, the sidebar, a tool lane — as the tablet draws it:
 * the glass fill, the sheen down its face, and **one** cast shadow underneath for [DesktopTheme.bandShadow].
 *
 * A band deliberately takes **no rim** (`docs/chrome.md`): on the true-black scheme a rim above and one
 * below a 40 dp slab read as two grey rules across the window, and that is the pair the tablet removed.
 * The shadow is its whole edge.
 */
@Composable
internal fun Modifier.bandSurface(
    fill: Color = DesktopTheme.glass,
    shadow: Dp = DesktopTheme.bandShadow,
): Modifier = shadow(shadow, RectangleShape, clip = false)
    .background(fill)
    .glassSheen()

/**
 * A **floating capsule** — the rail, the ribbon, the header's controls — the tablet's recipe in full:
 * the lift it has room to cast ([DesktopTheme.capsuleShadow]), the glass fill, the sheen, and the
 * family's drawn edge.
 *
 * The difference from [bandSurface] is exactly the difference on the tablet: a band is a slab of chrome
 * with one shadow and no rim; a capsule is an object lying in that chrome, so it carries a rim and casts
 * a smaller shadow of its own.
 */
@Composable
internal fun Modifier.glassCapsule(
    cornerRadius: Dp = DesktopTheme.capsuleRadius,
    fill: Color = DesktopTheme.glass,
    shadow: Dp = DesktopTheme.capsuleShadow,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return shadow(shadow, shape, clip = false)
        .clip(shape)
        .background(fill)
        .glassSheen()
        .glassRim(cornerRadius)
}

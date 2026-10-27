package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.draw.clip
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
 * The **hover step** of a chrome surface: the steel at [DesktopTheme.hoverSlot] while the pointer rests
 * on it, and nothing at all otherwise.
 *
 * Every interactive surface wears this (`docs/interface-contract.md` §6): a tab, a sidebar row, a
 * menu option, a tool slot. Without it a pointer over the window got no answer about
 * what it was over — only the tool slots had a tooltip, and the rest were silent.
 *
 * [active] suppresses the hover fill, because a picked surface already answers: a tab cannot be both
 * selected and merely pointed at, and painting both would put two greys on one capsule.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.hoverFill(
    shape: Shape,
    active: Boolean = false,
    fill: Color = DesktopTheme.hoverSlot,
): Modifier {
    var hovered by remember { mutableStateOf(false) }
    return this
        .onPointerEvent(PointerEventType.Enter) { hovered = true }
        .onPointerEvent(PointerEventType.Exit) { hovered = false }
        .background(if (hovered && !active) fill else Color.Transparent, shape)
}

/**
 * A **glass capsule that answers the pointer inside its own fill** — the same hover step as [hoverFill],
 * drawn as the capsule's fill rather than as a second background laid over the first.
 *
 * The two are not interchangeable on a weighted child: an overlay is a layer of its own, and on a menu chip
 * inside a popup it could be painted at the label's bounds instead of the capsule's, lighting a corner of a
 * control the pointer was over. One background cannot disagree with the shape it fills. [active] keeps the
 * given fill, for a chip that is already picked.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.hoverCapsule(
    cornerRadius: Dp = DesktopTheme.capsuleRadius,
    fill: Color = DesktopTheme.glass,
    hoverFill: Color = DesktopTheme.hoverSlot,
    shadow: Dp = DesktopTheme.capsuleShadow,
    active: Boolean = false,
): Modifier {
    var hovered by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .onPointerEvent(PointerEventType.Enter) { hovered = true }
        .onPointerEvent(PointerEventType.Exit) { hovered = false }
        .shadow(shadow, shape, clip = false)
        .clip(shape)
        .background(if (hovered && !active) hoverFill else fill)
        .glassRim(cornerRadius)
}

/**
 * A **band** of chrome — the header, the status bar, the sidebar, a tool lane — as the tablet draws it:
 * the glass fill and **one** cast shadow underneath for [DesktopTheme.bandShadow].
 *
 * A band deliberately takes **no rim** (`docs/chrome.md`): on the true-black scheme a rim above and one
 * below a 40 dp slab read as two grey rules across the window, and that is the pair the tablet removed.
 * The shadow is its whole edge. It takes **no sheen** either: the tablet's bands are the flat token fill,
 * and a lit gradient over the window's bands made the same material read a shade lighter on the PC.
 * Lightness is the fill's job, and the fill is the shared token.
 */
@Composable
internal fun Modifier.bandSurface(
    fill: Color = DesktopTheme.glass,
    shadow: Dp = DesktopTheme.bandShadow,
): Modifier = shadow(shadow, RectangleShape, clip = false)
    .background(fill)

/**
 * A **floating capsule** — the rail, the ribbon, the header's controls — the tablet's recipe in full:
 * the lift it has room to cast ([DesktopTheme.capsuleShadow]), the glass fill and the family's drawn
 * edge.
 *
 * The difference from [bandSurface] is exactly the difference on the tablet: a band is a slab of chrome
 * with one shadow and no rim; a capsule is an object lying in that chrome, so it carries a rim and casts
 * a smaller shadow of its own. Both take the same fill — no gradient of the window's own — so the two
 * shells' capsules are one material, not two shades of charcoal.
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
        .glassRim(cornerRadius)
}

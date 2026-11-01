package com.mobixournal.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobixournal.render.DrawingGuide
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The placed **instrument**, painted over the sheet: the setsquare's triangle, the compass's circle, the
 * dot a hand holds, and the tick that marks a snapped aim.
 *
 * These are the tablet's two pictures (`DrawingSurfacePaint.drawGuide`) at the window's sizes, in the same
 * amber — one token (`DesignTokens.GUIDE_RGB`), so the two shells cannot paint two instruments. Chrome,
 * never ink: the sheet holds no element for a guide, and the overlay is redrawn from the tab's pose every
 * frame like the selection's outline.
 *
 * Page space, like the rest of `paintLiveOverlay`: [pxPerPt] is zoom × density.
 */
internal fun DrawScope.paintPlacedGuide(
    pose: DesktopGuidePose?,
    pxPerPt: Float,
    aim: GuideAim? = null,
) {
    val guide = pose?.guide ?: return
    val strokePt = 2.dp.toPx() / pxPerPt
    val dotPt = GUIDE_DOT_PX / pxPerPt
    when (guide) {
        is DrawingGuide.Setsquare -> {
            val corners = guide.corners()
            val path = Path().apply {
                moveTo((corners[0].first * pxPerPt).toFloat(), (corners[0].second * pxPerPt).toFloat())
                lineTo((corners[1].first * pxPerPt).toFloat(), (corners[1].second * pxPerPt).toFloat())
                lineTo((corners[2].first * pxPerPt).toFloat(), (corners[2].second * pxPerPt).toFloat())
                close()
            }
            // The wash first, then the edge: a translucent body under a solid outline, which is what
            // makes the triangle read as an instrument lying on the paper rather than as a selection.
            drawPath(path, DesktopTheme.guideFill)
            drawPath(path, DesktopTheme.guide, style = Stroke(width = strokePt))
        }
        is DrawingGuide.Compass -> {
            drawCircle(
                color = DesktopTheme.guide,
                radius = (guide.radius * pxPerPt).toFloat(),
                center = Offset((guide.x * pxPerPt).toFloat(), (guide.y * pxPerPt).toFloat()),
                style = Stroke(width = strokePt),
            )
            drawCircle(
                color = DesktopTheme.guide,
                radius = dotPt,
                center = Offset((guide.x * pxPerPt).toFloat(), (guide.y * pxPerPt).toFloat()),
            )
        }
        // The protractor, the ruling and the chemistry rules are not in this window's scope
        // (`DESKTOP_GUIDE_KINDS`): a pose can never hold one, so there is nothing to draw for it.
        else -> return
    }
    val (tipX, tipY) = guide.grabHandle()
    drawCircle(
        color = DesktopTheme.guide,
        radius = dotPt,
        center = Offset((tipX * pxPerPt).toFloat(), (tipY * pxPerPt).toFloat()),
    )
    // A snapped aim wears a tick across itself at the tip: the one moment the readout is an honest
    // "this landed on the step" rather than the hand's own angle.
    aim?.takeIf { it.snapped }?.let { snapped -> drawAimTick(snapped, pxPerPt) }
}

/**
 * The **snap marker**: a short line across the aim at the instrument's tip.
 *
 * Drawn only while Shift holds the angle on a step of `Snapping.snapAngle` (which is why it is
 * [GuideAim.snapped] and not a second calculation of the snap): a tick across the direction being aimed,
 * at right angles to it, the way a drafting mark is made. [pxPerPt] is zoom × density.
 */
private fun DrawScope.drawAimTick(aim: GuideAim, pxPerPt: Float) {
    val (tipX, tipY) = aim.tip
    val half = (GUIDE_TICK_PX / 2f) / pxPerPt
    // Perpendicular to the aim: (cos, sin) turned a quarter turn is (-sin, cos).
    val ux = -sin(aim.radians) * half
    val uy = cos(aim.radians) * half
    val cx = (tipX * pxPerPt).toFloat()
    val cy = (tipY * pxPerPt).toFloat()
    drawLine(
        color = DesktopTheme.guide,
        start = Offset(cx - ux.toFloat(), cy - uy.toFloat()),
        end = Offset(cx + ux.toFloat(), cy + uy.toFloat()),
        strokeWidth = 2.dp.toPx() / pxPerPt.coerceAtLeast(0.01f),
    )
}

/**
 * The readout pill, **hosted** in the sheet's canvas: it decides which page the instrument stands on, so the
 * surface that owns the page passes the four values it already holds and nothing else.
 */
@Composable
internal fun GuideAimLayer(tab: DesktopTab, pointer: PenPointer, sheet: SheetLayout, density: Float) {
    GuideAimPill(
        aim = pointer.guideAim.value?.takeIf { it.pose.page == tab.page },
        viewport = sheet.viewport,
        zoom = sheet.zoom,
        density = density,
    )
}

/**
 * The **readout pill**: the aim in whole degrees, riding beside the tip while the instrument is being
 * posed.
 *
 * A floating surface in the interface contract's material — the glass capsule with its rim and its lift —
 * and one number, no sentence. The ink turns to [DesktopTheme.accent] while Shift is snapping, so the
 * pill says the same thing the tick does. Hidden the moment the drag ends, because the pose is then
 * settled and a permanent label would be a caption on the page.
 */
@Composable
internal fun GuideAimPill(
    aim: GuideAim?,
    viewport: Viewport,
    zoom: Float,
    density: Float,
) {
    val live = aim ?: return
    val (tipX, tipY) = live.tip
    val x = (tipX * zoom).toFloat() * density + viewport.offsetX * density + GUIDE_PILL_GAP_PX * density
    val y = (tipY * zoom).toFloat() * density + viewport.offsetY * density - GUIDE_PILL_LIFT_PX * density
    Box(Modifier.offset { IntOffset(x.roundToInt().coerceAtLeast(0), y.roundToInt().coerceAtLeast(0)) }) {
        Text(
            text = live.label,
            color = if (live.snapped) DesktopTheme.accent else DesktopTheme.ink,
            style = DesktopTheme.numeric,
            modifier = Modifier
                // The floating material, not the band: a band's alpha is thinned for the desk and turned
                // this pill grey over white paper (its own `FloatingSurface` siblings' fill).
                .glassCapsule(
                    cornerRadius = DesktopTheme.capsuleRadius,
                    fill = DesktopTheme.floating,
                    shadow = DesktopTheme.bandShadow,
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** The hold dot's radius, in screen pixels — the tablet's `HANDLE_DRAW_PX`. */
private const val GUIDE_DOT_PX = 4f

/** The snap tick's length, in screen pixels: long enough to read as a mark, short enough not to be an edge. */
private const val GUIDE_TICK_PX = 12f

/** How far beside its tip the readout pill sits, in dp. */
private const val GUIDE_PILL_GAP_PX = 14f

/** How far above its tip the readout pill sits, in dp — clear of the finger on the handle. */
private const val GUIDE_PILL_LIFT_PX = 30f

package com.mobixournal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The **closing edge of a band**: a shadow drawn inside the band's own last `bandShadowDp`, from
 * nothing at the top of the strip to [bandShadowAlpha] of the scheme's scrim at its lowest pixel.
 *
 * A band on the tablet is a row of the *window*, so it must not cast. Whatever it cast could only land
 * on the surface composed after it: the header's shadow fell on the rail's lane and on the canvas —
 * both painted it over, so it was never seen — while the lane's own fell back **up** onto the header in
 * the lane's column alone, so the header seemed to close on a soft edge over the rail and on a hard
 * step over the page. Its own edge has neither problem: one direction (downward), cast on nothing.
 * `docs/interface-contract.md` owns the rule and
 * `ChromeGeometryGuardTest.bandsShadeTheirOwnClosingEdgeAndCastNothing` the enforcement.
 *
 * @param startInset how far in from the band's left end the edge begins — the header passes the rail's
 *   lane width here when the lane is docked left, so the shade starts where the canvas starts and the
 *   joint between the two arms of the frame stays one unbroken face. [bandSideEdge] draws the rest.
 * @param endInset the same at the band's right end, for a lane docked right.
 *
 * Applied as `drawWithContent` so the shade lands on the band's rows *and* on any control standing in
 * them, exactly as a band's edge does — it is the band's own bottom, not a layer between two bands.
 */
@Composable
fun Modifier.bandClosingEdge(startInset: Dp = 0.dp, endInset: Dp = 0.dp): Modifier {
    val ink = MaterialTheme.colorScheme.scrim.copy(alpha = bandShadowAlpha(chromeIsDark()))
    val depth = ModernTheme.bandShadowDp
    return drawWithContent {
        drawContent()
        val strip = depth.toPx()
        val left = startInset.toPx()
        val right = size.width - endInset.toPx()
        if (right <= left) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, ink),
                startY = size.height - strip,
                endY = size.height,
            ),
            topLeft = Offset(left, size.height - strip),
            size = Size(right - left, strip),
        )
    }
}

/**
 * [bandClosingEdge] for a band that makes one arm of the frame: the edge stops where the rail's lane
 * begins ([laneWidth], on the side the lane is docked) so the lane's own [bandSideEdge] carries it on
 * down the page's margin — one shadow around one frame, with no soft line across the joint where a
 * full-width edge used to cross the lane's column and make two arms read as two surfaces.
 *
 * @param laneWidth the lane's width, or `0.dp` when there is no lane (full-page), which leaves the edge
 *   across the band's whole width.
 */
@Composable
fun Modifier.frameClosingEdge(laneWidth: Dp, laneOnLeft: Boolean): Modifier = bandClosingEdge(
    startInset = if (laneOnLeft) laneWidth else 0.dp,
    endInset = if (laneOnLeft) 0.dp else laneWidth,
)

/**
 * The lane's **edge facing the canvas**, the second arm of the frame's single shadow: the same
 * `bandShadowDp` and the same scrim as [bandClosingEdge], turned ninety degrees and drawn inside the
 * lane along the side the page begins on. With the rail docked left that is the lane's right edge, with
 * it docked right the lane's left edge — so the shade leaves the header's closing edge and runs down the
 * page's margin as one continuous corner, never a step at the joint.
 *
 * @param canvasOnRight true when the rail sits on the left and the page begins at the lane's right edge.
 */
@Composable
fun Modifier.bandSideEdge(canvasOnRight: Boolean): Modifier {
    val ink = MaterialTheme.colorScheme.scrim.copy(alpha = bandShadowAlpha(chromeIsDark()))
    val depth = ModernTheme.bandShadowDp
    return drawWithContent {
        drawContent()
        val strip = depth.toPx()
        val brush = if (canvasOnRight) {
            Brush.horizontalGradient(
                colors = listOf(Color.Transparent, ink),
                startX = size.width - strip,
                endX = size.width,
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(ink, Color.Transparent),
                startX = 0f,
                endX = strip,
            )
        }
        drawRect(
            brush = brush,
            topLeft = Offset(if (canvasOnRight) size.width - strip else 0f, 0f),
            size = Size(strip, size.height),
        )
    }
}

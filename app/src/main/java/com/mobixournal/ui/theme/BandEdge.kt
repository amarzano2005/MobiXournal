package com.mobixournal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The **closing edge of a band**: a shadow drawn inside the band's own last `bandShadowDp`, from
 * nothing at the top of the strip to [bandShadowAlpha] of the scheme's scrim at its lowest pixel.
 *
 * A band on the tablet is a row of the *window*, so it must not cast. Whatever it cast could only land
 * on the surface composed after it: the header's shadow fell on the rail's lane and on the canvas —
 * both painted it over, so it was never seen — while the lane's own fell back **up** onto the header in
 * the lane's column alone, so the header seemed to close on a soft edge over the rail and on a hard
 * step over the page. Its own edge has neither problem: full width, one direction (downward), cast on
 * nothing. `docs/interface-contract.md` owns the rule and
 * `ChromeGeometryGuardTest.bandsShadeTheirOwnClosingEdgeAndCastNothing` the enforcement.
 *
 * Applied as `drawWithContent` so the shade lands on the band's rows *and* on any control standing in
 * them, exactly as a band's edge does — it is the band's own bottom, not a layer between two bands.
 */
@Composable
fun Modifier.bandClosingEdge(): Modifier {
    val ink = MaterialTheme.colorScheme.scrim.copy(alpha = bandShadowAlpha(chromeIsDark()))
    val depth = ModernTheme.bandShadowDp
    return drawWithContent {
        drawContent()
        val strip = depth.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, ink),
                startY = size.height - strip,
                endY = size.height,
            ),
        )
    }
}

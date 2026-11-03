package com.mobixournal.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The air a rounded surface keeps between its own edge and anything it holds.
 *
 * A 20 dp corner cuts the outermost few dp of a panel's straight edge away, so a line of text or the
 * border of a pill drawn at the container's own padding lands on the curve and reads as clipped. This is
 * the number that keeps them off it — one value for settings cards, floating panels and pop-ups, so the
 * whole app breathes the same way instead of each surface guessing.
 */
internal val PanelEdgeAir: Dp = 6.dp

/**
 * The air a pill keeps between its rounded end and its label.
 *
 * A pill is read by its outline: 10 dp of text padding looks fine in a wide box and cramped in a 28 dp
 * tall capsule, because the curve eats into the text's own line. [PanelEdgeAir] is too little here — the
 * label is inside the shape, not beside it.
 */
internal val PillTextInset: Dp = 12.dp

/** The same air as [PanelEdgeAir], as a modifier, for a container that pads in one call. */
fun Modifier.panelEdgeAir(horizontal: Dp = PanelEdgeAir, vertical: Dp = PanelEdgeAir): Modifier =
    padding(horizontal = horizontal, vertical = vertical)

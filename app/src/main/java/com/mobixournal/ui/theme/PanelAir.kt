package com.mobixournal.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.PanelAir

/**
 * The air a rounded surface keeps between its own edge and anything it holds — read from
 * [com.mobixournal.design.PanelAir], so the tablet and the `desktop/` window cannot drift apart.
 */
internal val PanelEdgeAir: Dp = PanelAir.EDGE_DP.dp

/** The air a pill keeps between its rounded end and its label (see [PanelAir.PILL_TEXT_DP]). */
internal val PillTextInset: Dp = PanelAir.PILL_TEXT_DP.dp

/** The same air as [PanelEdgeAir], as a modifier, for a container that pads in one call. */
fun Modifier.panelEdgeAir(horizontal: Dp = PanelEdgeAir, vertical: Dp = PanelEdgeAir): Modifier =
    padding(horizontal = horizontal, vertical = vertical)

package com.mobixournal.desktop.tools

import androidx.compose.ui.unit.dp
import com.mobixournal.design.PanelAir
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The window reads its panel air from the **shared** token in `mobix-core`, not from a number of its own.
 *
 * Both shells draw the same panels (the settings surface, the tool flyouts, the menu), so a margin that
 * lives twice is a margin that drifts: the tablet had 6 dp at a card's edge and the window 2 dp under the
 * settings header, for the same 20 dp corner. This test fails if the desktop ever stops reading
 * [PanelAir] — the tablet pins its side in `SettingsRhythmTest`.
 */
class DesktopPanelAirTest {

    @Test
    fun `the window reads its edge air from the shared token`() {
        assertEquals(PanelAir.EDGE_DP.dp, PanelEdgeAir)
        assertEquals(PanelAir.PILL_TEXT_DP.dp, PillTextInset)
    }
}

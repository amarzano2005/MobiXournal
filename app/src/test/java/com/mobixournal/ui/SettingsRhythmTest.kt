package com.mobixournal.ui

import androidx.compose.ui.unit.dp
import com.mobixournal.design.PanelAir
import com.mobixournal.ui.theme.PanelEdgeAir
import com.mobixournal.ui.theme.PillTextInset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings screen's vertical rhythm: **a cell is closer to its neighbour than one group is to the
 * next.**
 *
 * A row's press has to reach the divider, so the air between two cells belongs to the rows themselves
 * ([SettingsCellAir]) — invisible, but it counts twice, once per row. When the group gap was *smaller*
 * than that (20 dp against 36) the cards ran together while the rows belonging to one card drifted
 * apart, which is what "little space, badly proportioned" described. The side menu's pills had the
 * opposite fault: 2 dp a side, so four targets read as one block with seams in it.
 *
 * Pinned as arithmetic on the tokens, because on this screen the numbers *are* the rule.
 */
class SettingsRhythmTest {

    @Test
    fun `two cells are closer together than two groups`() {
        val cells = settingsCellInset(reach = 16.dp) * 2
        assertTrue(
            "two cells ($cells) must stay under the gap between two cards ($SettingsGroupGap)",
            cells < SettingsGroupGap,
        )
    }

    @Test
    fun `a header sits closer to its card than one group sits to the next`() {
        assertTrue(
            "a header ($SettingsHeaderGap) names its own card, so it cannot be as far as a group break",
            SettingsHeaderGap < SettingsGroupGap,
        )
    }

    @Test
    fun `outside a card a cell has no lit air, so dialogs keep their own inset`() {
        assertEquals("no card, no air", 0.dp, settingsCellAir(reach = 0.dp))
        assertEquals("just the shared inset", SettingsCellPad, settingsCellInset(reach = 0.dp))
    }

    @Test
    fun `every pill is one height and one step, and the field is not one of them`() {
        assertEquals("a pill is a 48 dp target", 48.dp, SettingsSidebarRowHeight)
        assertTrue(
            "the pills are separated, not stacked",
            SettingsSidebarRowGap >= 4.dp,
        )
        assertTrue(
            "the search field stands further off the list than the pills do from each other",
            SettingsSidebarGapAfterField > SettingsSidebarRowGap * 2,
        )
    }

    @Test
    fun `the edge air is the shared number, not a local one`() {
        assertEquals("the card's air is the app-wide token", PanelAir.EDGE_DP.dp, PanelEdgeAir)
        assertEquals("a pill's inset is the app-wide token", PanelAir.PILL_TEXT_DP.dp, PillTextInset)
    }
}

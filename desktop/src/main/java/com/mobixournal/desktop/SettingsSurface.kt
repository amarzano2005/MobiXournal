package com.mobixournal.desktop

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * **The Settings surface**: the window's own panel for every choice it keeps.
 *
 * It **floats over the page** — the glass at `floatingPanelAlpha`, the family's rim, the 20 dp panel
 * corner — and the document stays drawn around it, because a setting is changed while looking at the
 * thing it changes. The first version took the whole body between the header and the status bar and
 * painted a blank page behind it: on a desktop that reads as a second window, and it threw away the
 * comparison the reader was making (`docs/interface-contract.md` §1 — a surface that comes and goes may
 * lie over the page; a band may not).
 *
 * Its proportions are a panel's, not a screen's: the index is a narrow list, the page is a column, a
 * setting is one row ([SettingsRow]), and the panel itself is capped at [MAX_WIDTH] × [MAX_HEIGHT] so a
 * 21:9 monitor does not stretch a label across the desk.
 *
 * Nothing here decides anything: every row reports a value from [DesktopSettingsState] and writes the next
 * one back, which is what makes a change visible on the page behind it the moment it is clicked.
 */
@Composable
internal fun SettingsSurface(policy: DesktopSettingsState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val panelWidth = minOf(maxWidth * 0.9f, MAX_WIDTH)
        val panelHeight = minOf(maxHeight * 0.88f, MAX_HEIGHT)
        Column(
            Modifier
                .width(panelWidth)
                .height(panelHeight)
                .glassSurface(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", color = DesktopTheme.ink, style = DesktopTheme.title)
                Spacer(Modifier.weight(1f))
                SettingsSearchField(
                    query = policy.query,
                    onQuery = policy::search,
                    onFocus = { focused -> policy.typing = focused },
                    modifier = Modifier.width(240.dp),
                )
                Spacer(Modifier.width(8.dp))
                // The same cross the inspector lane closes with: one glyph for "put this away" everywhere.
                LaneClose { policy.close() }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .width(SettingsMetrics.INDEX_WIDTH)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    for (section in policy.sections) {
                        SettingsSectionRow(
                            section = section,
                            selected = section == policy.shownSection,
                            onClick = { policy.show(section) },
                        )
                    }
                }
                Column(
                    Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 22.dp, top = 2.dp, bottom = 18.dp),
                ) {
                    DesktopSettingPage(policy, policy.shownSection)
                }
            }
        }
    }
}

/** The panel's cap: wide enough for a row of four chips, and no wider than a settings page reads. */
private val MAX_WIDTH = 900.dp
private val MAX_HEIGHT = 640.dp

/** The panel's own title row — shorter than a band's, because a panel is not the window's chrome. */
private val HEADER_HEIGHT = 46.dp

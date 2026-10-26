package com.mobixournal.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * **The Settings surface**: the window's own page for every choice it keeps.
 *
 * It is a **section of the window**, not a dialog: it takes the body between the header and the status bar,
 * so the page is not painted underneath and no glass is ever drawn on a sheet (`docs/interface-contract.md`;
 * the same rule the tool strips live by).
 *
 * Its proportions are a **window's**: the index is a narrow list of names, the page is a column capped at
 * [SettingsMetrics.PAGE_MAX_WIDTH] so a 21:9 monitor does not stretch a label across the screen, and a
 * setting is one row rather than a stack of name, sentence and full-width control. The first version of this
 * page reused the tablet's stacked blocks, which on a desktop left a thousand pixels of empty card beside
 * every choice.
 *
 * Nothing here decides anything: every row reports a value from [DesktopSettingsState] and writes the next
 * one back, which is what makes a change visible on the window behind it the moment it is clicked.
 */
@Composable
internal fun SettingsSurface(policy: DesktopSettingsState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().bandSurface()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(DesktopTheme.headerHeight)
                .padding(start = 18.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Settings", color = DesktopTheme.ink, style = DesktopTheme.title)
            Spacer(Modifier.weight(1f))
            SettingsSearchField(
                query = policy.query,
                onQuery = policy::search,
                onFocus = { focused -> policy.typing = focused },
                modifier = Modifier.width(260.dp),
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
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
                    .widthIn(max = SettingsMetrics.PAGE_MAX_WIDTH)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 28.dp, top = 4.dp, bottom = 20.dp),
            ) {
                DesktopSettingPage(policy, policy.shownSection)
            }
            // The rest of the body keeps the band's material: the page column is capped, not centred on
            // nothing, so a wide window reads as a page of settings rather than a stretched form.
            Spacer(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

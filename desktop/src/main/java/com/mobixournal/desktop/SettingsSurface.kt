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
 * the same rule the tool strips live by). The index on the left lists the sections, the search field above
 * them narrows it, and the page on the right is [DesktopSettingPage]'s.
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
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Settings", color = DesktopTheme.ink, style = DesktopTheme.title)
            Spacer(Modifier.width(20.dp))
            SettingsSearchField(
                query = policy.query,
                onQuery = policy::search,
                onFocus = { focused -> policy.typing = focused },
                modifier = Modifier.width(280.dp),
            )
            Spacer(Modifier.weight(1f))
            // The same cross the inspector lane closes with: one glyph for "put this away" everywhere.
            LaneClose { policy.close() }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .width(INDEX_WIDTH)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                for (section in policy.sections) {
                    SettingsSectionRow(
                        section = section,
                        selected = section == policy.shownSection,
                        onClick = { policy.show(section) },
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 28.dp, top = 8.dp, bottom = 24.dp),
            ) {
                DesktopSettingPage(policy, policy.shownSection)
            }
        }
    }
}

/** How wide the index column is: the section titles and their one-line summaries, and no wider. */
private val INDEX_WIDTH = 300.dp

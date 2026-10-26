package com.mobixournal.desktop

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * **The Settings surface**: the window's own panel for every choice it keeps.
 *
 * It **floats over the page** — the glass, the family's rim, the 20 dp panel corner — and the document stays
 * drawn around it, because a setting is changed while looking at the thing it changes. The first version
 * took the whole body between the header and the status bar and painted a blank page behind it: on a desktop
 * that reads as a second window, and it threw away the comparison the reader was making
 * (`docs/interface-contract.md` §1 — a surface that comes and goes may lie over the page; a band may not).
 *
 * Its fill is [DesktopTheme.settingsPanel], the glass almost solid: a settings page is read, and at the
 * floating alpha the page's ink came up through its labels.
 *
 * The panel is **draggable by its title bar**. It opens in the middle, which is over the sheet the reader
 * came to compare against; dragging it is how the thing being changed is kept in view. The offset is clamped
 * so the panel cannot be lost off an edge, and the window's own placement is not stored — a file that
 * remembered "moved left" would open a fresh window with a panel half off the screen.
 *
 * Its proportions are a panel's, not a screen's: the index is a narrow list, the page is a column, a setting
 * is one row ([SettingsRow]), and the panel itself is capped at [MAX_WIDTH] × [MAX_HEIGHT] so a 21:9 monitor
 * does not stretch a label across the desk.
 *
 * Nothing here decides anything: every row reports a value from [DesktopSettingsState] and writes the next
 * one back, which is what makes a change visible on the page behind it the moment it is clicked.
 */
@Composable
internal fun SettingsSurface(policy: DesktopSettingsState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val panelWidth = minOf(maxWidth * 0.9f, MAX_WIDTH)
        val panelHeight = minOf(maxHeight * 0.88f, MAX_HEIGHT)
        val density = LocalDensity.current
        // How far the panel may travel before its edge leaves the window: half the room it has around it.
        val roomX = with(density) { ((maxWidth - panelWidth) / 2f).toPx() }
        val roomY = with(density) { ((maxHeight - panelHeight) / 2f).toPx() }
        val dx = policy.panelDx.coerceIn(-roomX, roomX)
        val dy = policy.panelDy.coerceIn(-roomY, roomY)
        Column(
            Modifier
                .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                .width(panelWidth)
                .height(panelHeight)
                .glassSurface(fill = DesktopTheme.settingsPanel),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The title area is the handle: a header is where a window is grabbed, and the field and
                // the close glyph keep their own pointer handling beside it.
                Text(
                    "Settings",
                    color = DesktopTheme.ink,
                    style = DesktopTheme.title,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(policy) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                policy.dragPanel(drag.x, drag.y)
                            }
                        }
                        .padding(top = 13.dp),
                )
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

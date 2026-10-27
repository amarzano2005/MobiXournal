package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
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
 * floating alpha the page's own ink came up through its labels.
 *
 * The panel is **draggable by its title bar**. It opens in the middle, which is over the sheet the reader
 * came to compare against; dragging it is how the thing being changed is kept in view. The offset is clamped
 * so the panel cannot be lost off an edge, and the window's own placement is not stored — a file that
 * remembered "moved left" would open a fresh window with a panel half off the screen.
 *
 * Its proportions are a panel's, not a screen's: the index is a narrow list of four areas
 * ([SettingsIndex]), the page is a column, a setting is one row ([SettingsRow]), the panel is capped at
 * [MAX_WIDTH] × [MAX_HEIGHT] so a 21:9 monitor does not stretch a label across the desk, and the footer is
 * the **help line** ([SettingsHelpLine]) that says what the row under the pointer does.
 *
 * Nothing here decides anything: every row reports a value from [DesktopSettingsState] and writes the next
 * one back, which is what makes a change visible on the page behind it the moment it is clicked.
 */
@Composable
internal fun SettingsSurface(policy: DesktopSettingsState, modifier: Modifier = Modifier) {
    // What the help line is saying: the sentence of the row under the pointer, written by the row itself.
    val help = remember { mutableStateOf("") }
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
            SettingsPanelHeader(policy)
            CompositionLocalProvider(LocalSettingsHelp provides help) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    SettingsIndex(policy)
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
            // At rest the line says what the open page is about; the sentence of the row under the pointer
            // replaces it while it is there. The page above therefore needs no summary of its own.
            SettingsHelpLine(help.value.ifEmpty { policy.shownSection?.summary.orEmpty() })
        }
    }
}

/** The panel's title row: the drag handle, the search field and the close glyph. */
@Composable
private fun SettingsPanelHeader(policy: DesktopSettingsState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The title area is the handle: a header is where a window is grabbed, and the field and the
        // close glyph keep their own pointer handling beside it.
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
}

/**
 * The **index**: the four areas as eyebrows, and their sections under them.
 *
 * Ten names in one flat column is a list read top to bottom; four short groups is a list scanned by its
 * shape, and the tablet groups the same subjects under the same words (`DesktopSettingArea`). A section's
 * sentence is not drawn here — the index is names only, and the help line says what a section covers when
 * the pointer is on it.
 */
@Composable
private fun SettingsIndex(policy: DesktopSettingsState) {
    Column(
        Modifier
            .width(SettingsMetrics.INDEX_WIDTH)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        for (area in DesktopSettingArea.entries) {
            val rows = policy.sections.filter { it.area == area }
            if (rows.isEmpty()) continue
            Text(
                area.title,
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
                modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 2.dp),
            )
            for (section in rows) {
                SettingsSectionRow(
                    section = section,
                    selected = section == policy.shownSection,
                    onClick = { policy.show(section) },
                )
            }
        }
    }
}

/**
 * The panel's **help line**: one sentence of the label role, under a hairline.
 *
 * It is reserved even while it is empty, so the page above it does not jump by a row's height every time
 * the pointer leaves a setting — and it is the only place a setting's explanation is drawn, which is what
 * lets every row be one line tall (`docs/desktop-settings.md`).
 */
@Composable
private fun SettingsHelpLine(sentence: String) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DesktopTheme.hairline))
    Row(
        Modifier
            .fillMaxWidth()
            .height(HELP_HEIGHT)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            sentence,
            color = DesktopTheme.inkMuted,
            style = DesktopTheme.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The panel's cap: wide enough for a row of four chips, and no wider than a settings page reads. */
private val MAX_WIDTH = 900.dp
private val MAX_HEIGHT = 640.dp

/** The panel's own title row — shorter than a band's, because a panel is not the window's chrome. */
private val HEADER_HEIGHT = 46.dp

/** The help line under the body: one sentence, at the label role, and never more than one line. */
private val HELP_HEIGHT = 27.dp

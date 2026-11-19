package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.tools.DesktopTextField
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.glyph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph

/**
 * The header's **search field**: the window's one way into `DocumentSearch`.
 *
 * It sits in the band beside the tabs rather than in the sidebar because the sidebar belongs to the
 * pages and can be shut: a search whose field disappears with the page list would be a feature that
 * depends on another one's state. On the flat band the field is the contract's **field**, not a glass
 * capsule (`docs/interface-contract.md` §6): the `panelField` tone, one hairline that turns accent while
 * it is typed in, the catalogue's magnifier on its left and its count and steps on the right.
 *
 * **Keys are the field's own**: `Enter` steps forward, `Shift+Enter` back and `Esc` closes the search, in
 * `onPreviewKeyEvent` so the text field sees the keys before it inserts anything. The global table only
 * owns `Ctrl+F`, which is what opens this.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HeaderSearchField(
    tab: DesktopTab,
    onQuery: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember(tab) { FocusRequester() }
    DesktopTextField(
        value = tab.searchQuery,
        onValueChange = onQuery,
        placeholder = "Search document\u2026",
        height = HEADER_CONTROL,
        modifier = Modifier
            .width(SEARCH_FIELD_WIDTH)
            // The field's own keys, ahead of the text field: Enter is a step, never a newline, and Esc
            // closes the search instead of leaving the caret where it is.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.Escape -> {
                        onClose()
                        true
                    }
                    event.key == Key.Enter && event.isShiftPressed -> {
                        onPrevious()
                        true
                    }
                    event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                        onNext()
                        true
                    }
                    else -> false
                }
            },
        leadingIcon = {
            Canvas(Modifier.size(14.dp)) {
                drawChromeGlyph(
                    ChromeGlyph.SEARCH,
                    if (tab.searchQuery.isNotBlank()) DesktopTheme.accent else DesktopTheme.inkMuted,
                )
            }
        },
        focusRequester = focus,
        onClear = { onQuery("") },
        clearDescription = "Clear search",
        trailing = { SearchFieldActions(tab, tab.search, onNext, onPrevious) },
    )
    // The field owns the keyboard while the search is **asked for** (`Ctrl+F`) — once per request, not
    // per keystroke, and not on a tab switch, where the reader is looking at the page instead.
    LaunchedEffect(tab, tab.searchOpen) {
        if (tab.searchOpen) focus.requestFocus()
    }
}

/**
 * The field's **answer**, right of its text: the count and the two steps.
 *
 * Everything here belongs to having a query, not to having an answer, so a query still being read keeps
 * its steps on screen. While the walk has not answered *this* query the count is replaced by
 * **Searching…** — `0 / 0` means *this query found nothing*, and printing it over a document still being
 * read was a lie the reader had to wait out (`DesktopTab.isSearchPending`).
 */
@Composable
private fun RowScope.SearchFieldActions(
    tab: DesktopTab,
    search: DesktopSearch,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    if (tab.searchQuery.isEmpty()) return
    Spacer(Modifier.width(4.dp))
    if (tab.isSearchPending) {
        Text("Searching\u2026", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
    } else {
        Text(
            "${search.position} / ${search.total}",
            color = if (search.total == 0) DesktopTheme.inkMuted else DesktopTheme.inkSecondary,
            style = DesktopTheme.numeric,
        )
    }
    Spacer(Modifier.width(2.dp))
    // The two steps, for a mouse: `Enter` and `Shift+Enter` are the same commands, and both move the sheet
    // to the word (`DesktopSearchCommands.stepSearch`). Up is "earlier in the document", the direction the
    // results list is read in. They are greyed while the answer on hand is the *previous* query's, so a step
    // cannot walk a list the counter is not showing.
    val canStep = !tab.isSearchPending && search.total > 0
    SearchStepButton(ChromeGlyph.CHEVRON_UP, "Previous match", canStep, onPrevious)
    SearchStepButton(ChromeGlyph.CHEVRON_DOWN, "Next match", canStep, onNext)
}

/**
 * One of the field's two steps: a catalogue chevron, muted while there is nothing to step to. It is the
 * same command the keys run, so the arrows cannot walk a list the counter disagrees with.
 */
@Composable
private fun SearchStepButton(glyph: ChromeGlyph, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(16.dp)
            // The window's own hover step, drawn **with** the shape (`docs/interface-contract.md` §6): a
            // 4 dp control corner, the same corner every command and field on the band wears.
            .hoverFill(DesktopTheme.controlShape)
            .clickable(enabled = enabled) { onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            drawChromeGlyph(glyph, if (enabled) DesktopTheme.inkSecondary else DesktopTheme.inkMuted)
        }
    }
}

/**
 * How much of the band the field takes: wide enough for a phrase **and its two steps**, narrow enough to
 * leave the tabs room (200 dp, down from the 284 dp capsule the band used to carry).
 */
private val SEARCH_FIELD_WIDTH = 200.dp

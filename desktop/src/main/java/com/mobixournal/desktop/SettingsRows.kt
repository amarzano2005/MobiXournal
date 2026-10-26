package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * The Settings surface's own **rows**: the glue between a setting and the control that changes it.
 *
 * The controls are the window's — the same steel, the same accent and the same hover step as the lane's
 * (`InspectorControls.kt`) — but the **shape of a row is a desktop's, not a phone's**: the name of the
 * setting and its one line of explanation on the left, the control itself at the right edge of the row,
 * hugging its own words. The first version of this page stacked a label, an explanation and a full-width
 * segment row per setting, which is how a settings *screen* is built on a 6-inch pointer: on a 1440-wide
 * window it read as four bands of buttons per card and pushed the last setting of Interface below the
 * fold. A row that fills its width is a touch target; a row that ends in a control is a preference.
 *
 * A row is therefore also the **hit area** — 40 dp, the contract's smallest control — so the switch at its
 * end can be the slim 32 × 18 pill and still be reachable from the keyboard and the pointer.
 */

/** The surface's measures, in one place so a row cannot quietly grow back into a touch target. */
internal object SettingsMetrics {

    /** The index column: a section's name and its one-line summary, and no wider. */
    val INDEX_WIDTH = 232.dp

    /** One setting is one row: 40 dp, the contract's smallest interactive measure. */
    val ROW_HEIGHT = 40.dp

    /** The tallest a control draws — inside a row, never taller than it. */
    val CONTROL_HEIGHT = 30.dp

    /**
     * The page's own column, capped: an ultrawide window keeps a readable line length instead of
     * stretching one setting over 3 440 px. The index owns the rest.
     */
    val PAGE_MAX_WIDTH = 720.dp

    /** The index's row: a title and a summary, tighter than a page row because it is a list. */
    val INDEX_ROW_HEIGHT = 44.dp
}

/**
 * **One group of settings**, under its own name.
 *
 * The card is the lane's recipe (`DesktopTheme.card`, the 10 dp slot corner, no rim — a band takes none),
 * and the heading is sentence case in the heading role: the inspector's uppercase eyebrow belongs to a
 * panel read while drawing, where a short shout helps; a settings page is read, not glanced at.
 */
@Composable
internal fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(title, color = DesktopTheme.ink, style = DesktopTheme.heading)
    Spacer(Modifier.height(5.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .background(DesktopTheme.card, DesktopTheme.railSlotShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        content()
    }
}

/**
 * **One setting**: its name (and the sentence that says what the answer means) on the left, [control] at
 * the right edge.
 *
 * With [onClick] the whole row is the hit area and takes the hover step — that is what a switch, a choice
 * or a readout wants, because aiming at a 32 dp pill in a 1 000 px row is work nobody asked for. Without
 * it the row is a label beside a control that handles its own pointer (a field).
 */
@Composable
internal fun SettingsRow(
    label: String,
    note: String? = null,
    onClick: (() -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    val press = rememberUpdatedState(onClick)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SettingsMetrics.ROW_HEIGHT)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        .hoverFill(DesktopTheme.railSlotShape)
                        .pointerInput(onClick) { detectTapGestures { press.value?.invoke() } }
                },
            )
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = DesktopTheme.ink, style = DesktopTheme.body)
            if (note != null) {
                Text(note, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
            }
        }
        Spacer(Modifier.width(16.dp))
        control()
    }
}

/** A sentence under a group: what an answer does, or what it deliberately does not. */
@Composable
internal fun SettingsNote(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(text, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
}

/**
 * **One answer of a few**, as a row: the names of the answers, and the picked one.
 *
 * [options] are values, not words, so a renamed enum cannot leave a row reporting a name the store does
 * not know; the pills are as wide as their own labels and never share the row out equally, which is what
 * made four words look like four tables of buttons.
 */
@Composable
internal fun <T> SettingsChoice(
    label: String,
    note: String? = null,
    options: List<T>,
    picked: T,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
) {
    val answer = rememberUpdatedState(onPick)
    SettingsRow(label = label, note = note) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (option in options) {
                SettingsPill(
                    text = optionLabel(option),
                    selected = option == picked,
                    onPick = { answer.value(option) },
                )
            }
        }
    }
}

/**
 * One **pill**: an answer that is one of a few.
 *
 * It draws the window's own states — steel when picked, the hover step under the pointer, the accent ring
 * when it holds the keyboard — because a settings page is navigated with Tab as often as with the mouse and
 * a control that only answers the pointer is a control half the window cannot reach. `Enter` and `Space`
 * take it, as every other button in the window does.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SettingsPill(text: String, selected: Boolean, onPick: () -> Unit) {
    val pick = rememberUpdatedState(onPick)
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .height(SettingsMetrics.CONTROL_HEIGHT)
            .background(
                if (selected) DesktopTheme.activeSlot else DesktopTheme.activeSlot.copy(alpha = 0.35f),
                DesktopTheme.capsuleShape,
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) DesktopTheme.accent else Color.Transparent,
                shape = DesktopTheme.capsuleShape,
            )
            .hoverFill(DesktopTheme.capsuleShape, active = selected)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar) {
                    pick.value()
                    true
                } else {
                    false
                }
            }
            .pointerInput(Unit) { detectTapGestures { pick.value() } }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) DesktopTheme.accent else DesktopTheme.ink,
            style = DesktopTheme.body,
            maxLines = 1,
        )
    }
}

/** The switch's drawn half: 32 × 18 inside the row that is the hit area ([SettingsRow]'s `onClick`). */
@Composable
internal fun SettingsSwitch(on: Boolean) {
    Box(
        Modifier
            .width(32.dp)
            .height(18.dp)
            .background(if (on) DesktopTheme.accent else DesktopTheme.activeSlot, DesktopTheme.capsuleShape),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .height(14.dp)
                .width(14.dp)
                .background(if (on) DesktopTheme.desk else DesktopTheme.inkSecondary, DesktopTheme.capsuleShape),
        )
    }
}

/**
 * **A section of the index**, on the left of the surface.
 *
 * The picked one takes the steel slot and the accent title — the same two states a tool slot wears — and a
 * resting pointer takes the hover step, never the picked fill (`docs/interface-contract.md` §6).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SettingsSectionRow(
    section: DesktopSettingSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SettingsMetrics.INDEX_ROW_HEIGHT)
            .background(if (selected) DesktopTheme.activeSlot else Color.Transparent, DesktopTheme.railSlotShape)
            .hoverFill(DesktopTheme.railSlotShape, active = selected)
            .pointerInput(section) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            section.title,
            color = if (selected) DesktopTheme.accent else DesktopTheme.ink,
            style = DesktopTheme.body,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            section.summary,
            color = DesktopTheme.inkMuted,
            style = DesktopTheme.label,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/**
 * The search field: a capsule of the window's glass that takes the keyboard as soon as the surface opens.
 *
 * [onFocus] is what keeps the window's shortcuts out of it — while the field holds the keyboard every
 * letter is the search, and while it does not the surface still swallows the page's keys
 * (`DesktopSettingsState.onKey`).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SettingsSearchField(
    query: String,
    onQuery: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    // The surface opens on a list of three sections; the field is a question, not the reason it is up, so
    // it is focused but never sends anything: the *first* keystroke is what narrows the list.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        modifier
            .height(SettingsMetrics.CONTROL_HEIGHT)
            .background(DesktopTheme.glass, DesktopTheme.capsuleShape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text("Search settings", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        }
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            textStyle = DesktopTheme.body.copy(color = DesktopTheme.ink),
            singleLine = true,
            cursorBrush = SolidColor(DesktopTheme.accent),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged { onFocus(it.isFocused) },
        )
    }
}

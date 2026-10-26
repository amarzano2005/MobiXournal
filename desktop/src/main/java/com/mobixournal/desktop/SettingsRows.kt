package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * The Settings surface's own **rows**: the glue between a setting and the control that changes it.
 *
 * The controls themselves are the lane's (`InspectorControls.kt` — `ChoiceRow`, `SwitchRow`, `Readout`,
 * `SectionCard`), because the contract has one recipe per control and this is the same window: a settings
 * row and an inspector row are the same capsule, the same picked tone and the same glyphs. What is added
 * here is the *setting* shape — a label, the sentence that says what the answer means, and the value —
 * so a page reads as a list of answers instead of a list of controls (`docs/interface-contract.md`).
 */

/**
 * **One answer of a few**, as a labelled row.
 *
 * [options] are values, not words: the row maps them to [optionLabel] for [ChoiceRow] and back again for
 * [onPick], so a caller never spells a setting twice and a renamed enum cannot leave a row reporting a name
 * the store does not know.
 */
@Composable
internal fun <T> SettingsChoice(
    label: String,
    subtitle: String,
    options: List<T>,
    picked: T,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
) {
    Spacer(Modifier.height(6.dp))
    Text(label, color = DesktopTheme.ink, style = DesktopTheme.body)
    if (subtitle.isNotBlank()) {
        Text(subtitle, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
    }
    Spacer(Modifier.height(4.dp))
    ChoiceRow(options.map(optionLabel), optionLabel(picked)) { chosen ->
        options.firstOrNull { optionLabel(it) == chosen }?.let(onPick)
    }
    Spacer(Modifier.height(6.dp))
}

/** A sentence under a card: what an answer does, or what it deliberately does not. */
@Composable
internal fun SettingsNote(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
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
            .background(if (selected) DesktopTheme.activeSlot else Color.Transparent, DesktopTheme.railSlotShape)
            .hoverFill(DesktopTheme.railSlotShape, active = selected)
            .pointerInput(section) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            section.title,
            color = if (selected) DesktopTheme.accent else DesktopTheme.ink,
            style = DesktopTheme.heading,
        )
        Spacer(Modifier.height(2.dp))
        Text(section.summary, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
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
            .height(DesktopTheme.headerControlSize)
            .background(DesktopTheme.glass, DesktopTheme.capsuleShape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text("Search settings", color = DesktopTheme.inkMuted, style = DesktopTheme.body)
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

package com.mobixournal.ui

import androidx.compose.runtime.Composable

/**
 * Appearance: which Material 3 colour scheme the app's chrome is painted with.
 *
 * These are **rows of the Interface page**, not a page behind a door ([SettingsSection.inline]): the theme is
 * one answer out of three, so a section row that opened a page to ask a single question cost the reader a tap
 * to read an answer the list could show. The mode is a **select** — the answer in hand at the end of one row,
 * the other two in a menu under it ([OptionGroup]) — and it wears the section's own glyph, so the entry that
 * stopped being a page is still read as the same entry in the same list
 * (`docs/chrome-settings.md`). Search still reaches the row as a page of its own, because a hit for "dark"
 * opens the section.
 */
@Composable
fun AppearanceSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    OptionGroup(
        title = "Appearance",
        subtitle = "The app's theme: light, dark, or whatever the system is set to.",
        options = ThemeMode.values().toList(),
        selected = settings.themeMode,
        label = { it.label },
        leadingIcon = SettingsSection.APPEARANCE.icon,
        onSelect = { mode -> onChange(settings.copy(themeMode = mode)) },
    )
}

package com.mobixournal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.mobixournal.MobiXournalApp

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
 *
 * Picking a mode repaints the live window immediately and updates the app's `-night` qualifier
 * ([MobiXournalApp.applyNightMode]) without prompting for a restart.
 */
@Composable
fun AppearanceSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    OptionGroup(
        // The row's words are the entry's words (`SettingsSection`), not a second copy of them: this row
        // used to carry a reworded title and subtitle, and the two drifted apart on the day it moved out
        // of its own page.
        title = SettingsSection.APPEARANCE.title,
        subtitle = SettingsSection.APPEARANCE.summary,
        options = ThemeMode.values().toList(),
        selected = settings.themeMode,
        label = { it.label },
        leadingIcon = SettingsSection.APPEARANCE.icon,
        onSelect = { mode ->
            if (mode != settings.themeMode) {
                onChange(settings.copy(themeMode = mode))
                MobiXournalApp.applyNightMode(context, mode)
            }
        },
    )
}

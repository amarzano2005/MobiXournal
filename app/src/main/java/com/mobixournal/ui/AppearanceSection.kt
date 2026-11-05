package com.mobixournal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mobixournal.MobiXournalApp
import com.mobixournal.ui.theme.DialogPresence

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
 * **A change is followed by [ThemeRestartDialog].** The scheme repaints the live window, but the startup
 * screen and the home-screen icon are read when the app launches, so the answer either comes back now
 * (restart) or at the next start — and it is the user who picks, not a silent recreate that would drop them
 * out of their document mid-page.
 */
@Composable
fun AppearanceSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    var restartPending by remember { mutableStateOf(false) }
    OptionGroup(
        title = "Appearance",
        subtitle = "The app's theme: light, dark, or whatever the system is set to.",
        options = ThemeMode.values().toList(),
        selected = settings.themeMode,
        label = { it.label },
        leadingIcon = SettingsSection.APPEARANCE.icon,
        onSelect = { mode ->
            if (mode != settings.themeMode) {
                onChange(settings.copy(themeMode = mode))
                restartPending = true
            }
        },
    )
    DialogPresence(restartPending) {
        ThemeRestartDialog(
            onRestartNow = {
                restartPending = false
                MobiXournalApp.restartForTheme(context)
            },
            onRestartLater = { restartPending = false },
        )
    }
}

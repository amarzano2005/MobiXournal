package com.mobixournal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.mobixournal.MobiXournalApp

/**
 * Appearance: which Material 3 colour scheme the app's chrome is painted with.
 *
 * Rendered inside its own [SettingsGroup] card ("Theme") when opened from the grouped index or
 * tablet sidebar, while wearing [SettingsSection.APPEARANCE]'s own title and summary so the two
 * shells' wording stays paired (`SettingsCopyParityTest`).
 *
 * Picking a mode repaints the live window immediately and updates the app's `-night` qualifier
 * ([MobiXournalApp.applyNightMode]) without prompting for a restart.
 */
@Composable
fun AppearanceSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    SettingsGroup("Theme") {
        OptionGroup(
            // The row's words are the entry's words (`SettingsSection`), not a second copy of them:
            // `SettingsCopyParityTest` pins both references so the shells cannot drift apart.
            title = SettingsSection.APPEARANCE.title,
            subtitle = SettingsSection.APPEARANCE.summary,
            options = ThemeMode.values().toList(),
            selected = settings.themeMode,
            label = { it.label },
            onSelect = { mode ->
                if (mode != settings.themeMode) {
                    onChange(settings.copy(themeMode = mode))
                    MobiXournalApp.applyNightMode(context, mode)
                }
            },
        )
    }
}

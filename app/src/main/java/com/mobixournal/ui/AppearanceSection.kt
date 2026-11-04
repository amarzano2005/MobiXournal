package com.mobixournal.ui

import androidx.compose.runtime.Composable

/**
 * Appearance: which Material 3 colour scheme the app's chrome is painted with.
 *
 * The mode is a **select** — the answer in hand at the end of one row, the other two in a menu under it
 * ([OptionGroup]). Three chips in a row printed all three answers on the page for a question nobody changes
 * twice, and a chip row is a filter's grammar, not a setting's: this page's other choices are all rows that
 * open their list, and the theme is one of them (`docs/chrome-settings.md`).
 */
@Composable
fun AppearanceSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Theme") {
        OptionGroup(
            title = "Mode",
            subtitle = "Colours the app's chrome and both system bars. System follows the device setting.",
            options = ThemeMode.values().toList(),
            selected = settings.themeMode,
            label = { it.label },
            onSelect = { mode -> onChange(settings.copy(themeMode = mode)) },
        )
        SettingsDivider()
        SwitchRow(
            title = "Use system colours",
            subtitle = "Android 12+: tint the app with the wallpaper. Off keeps the app's blue.",
            checked = settings.dynamicColor,
            onCheckedChange = { onChange(settings.copy(dynamicColor = it)) },
        )
    }
}

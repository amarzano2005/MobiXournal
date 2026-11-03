package com.mobixournal.ui

import androidx.compose.runtime.Composable

/** Appearance: which Material 3 colour scheme the app's chrome is painted with. */
@Composable
fun AppearanceSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Theme") {
        OptionGroup(
            title = "Mode",
            subtitle = "Colours the app's chrome and both system bars. System follows the device setting.",
            options = ThemeMode.values().toList(),
            selected = settings.themeMode,
            label = { it.label },
            onSelect = { onChange(settings.copy(themeMode = it)) },
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

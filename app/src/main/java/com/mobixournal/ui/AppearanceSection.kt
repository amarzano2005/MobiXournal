package com.mobixournal.ui

import androidx.compose.runtime.Composable

/** Appearance: which Material 3 colour scheme the app's chrome is painted with. */
@Composable
fun AppearanceSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Theme") {
        OptionGroup(
            title = "Mode",
            subtitle = "Colours the top bar, tab strip, tool rail, canvas surround and both system bars. " +
                "System follows the device's light/dark setting.",
            options = ThemeMode.values().toList(),
            selected = settings.themeMode,
            label = { it.label },
            onSelect = { onChange(settings.copy(themeMode = it)) },
        )
        SettingsDivider()
        SwitchRow(
            title = "Use system colours",
            subtitle = "On Android 12+ takes colours from your wallpaper. Off uses the app's fixed blue.",
            checked = settings.dynamicColor,
            onCheckedChange = { onChange(settings.copy(dynamicColor = it)) },
        )
    }
}

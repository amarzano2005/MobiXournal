package com.mobixournal.ui

import androidx.compose.runtime.Composable

/**
 * Appearance: which Material 3 colour scheme the app's chrome is painted with, and whether the wallpaper
 * tints it.
 *
 * These are **rows of the Interface page**, not a page behind a door ([SettingsSection.inline]): the theme is
 * one answer out of three and the switch beside it is one toggle, so a section row that opened a page to ask
 * a single question cost the reader a tap to read an answer the list could show. The mode is a **select** —
 * the answer in hand at the end of one row, the other two in a menu under it ([OptionGroup]) — which is the
 * grammar every other choice on these pages keeps, rather than three chips printed on the page for a decision
 * nobody changes twice (`docs/chrome-settings.md`). Search still reaches the two rows as a page of their own,
 * because a hit for "dark" or "wallpaper" opens the section.
 */
@Composable
fun AppearanceSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    OptionGroup(
        title = "Appearance",
        subtitle = "Theme and wallpaper colours.",
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

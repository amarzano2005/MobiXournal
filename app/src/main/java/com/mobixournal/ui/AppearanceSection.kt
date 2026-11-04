package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Appearance: which Material 3 colour scheme the app's chrome is painted with.
 *
 * The mode is **three chips in a row**, not a row that opens a list of three: it is the one question on this
 * page, every answer is a word a reader already knows, and hiding two of them behind a tap made choosing a
 * theme a two-step errand for a one-step decision. The chips sit in the settings block inset like the note
 * under them, so a theme change is one tap away from the page the user opened.
 */
@Composable
fun AppearanceSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Theme") {
        Text(
            text = "Mode",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.settingsBlockInset(),
        )
        Text(
            text = "Colours the app's chrome and both system bars. System follows the device setting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.settingsBlockInset(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (mode in ThemeMode.values()) {
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { onChange(settings.copy(themeMode = mode)) },
                    label = { Text(mode.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SettingsDivider()
        SwitchRow(
            title = "Use system colours",
            subtitle = "Android 12+: tint the app with the wallpaper. Off keeps the app's blue.",
            checked = settings.dynamicColor,
            onCheckedChange = { onChange(settings.copy(dynamicColor = it)) },
        )
    }
}

package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.BuildConfig
import com.mobixournal.design.ThemeMode
import java.util.Locale

/**
 * Compact 3-tile quick controls strip at the top of Settings for the most-changed mobile
 * preferences: theme mode, finger drawing, and toolbar handedness.
 */
@Composable
internal fun SettingsQuickControls(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickControlTile(
                label = "Theme",
                value = settings.themeMode.shortLabel(),
                active = true,
                onClick = { onChange(settings.copy(themeMode = settings.themeMode.next())) },
                modifier = Modifier.weight(1f),
            )
            QuickControlTile(
                label = "Finger",
                value = if (settings.fingerDraws) "Draw" else "Pan only",
                active = settings.fingerDraws,
                onClick = { onChange(settings.copy(fingerDraws = !settings.fingerDraws)) },
                modifier = Modifier.weight(1f),
            )
            QuickControlTile(
                label = "Toolbar",
                value = settings.toolbarPosition.label,
                active = true,
                onClick = {
                    val next = if (settings.toolbarPosition == ToolbarPosition.LEFT) {
                        ToolbarPosition.RIGHT
                    } else {
                        ToolbarPosition.LEFT
                    }
                    onChange(settings.copy(toolbarPosition = next))
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickControlTile(
    label: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
    val valueColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Concise live readout shown on the trailing edge of a [SectionRow] so the current configuration
 * is readable from the index without drilling into every page.
 */
internal fun sectionLiveValue(section: SettingsSection, settings: AppSettings): String? = when (section) {
    SettingsSection.STYLUS -> String.format(Locale.US, "%.1fx", settings.pressureMultiplier)
    SettingsSection.GESTURES -> if (settings.fingerDraws) "Draw" else "Pan only"
    SettingsSection.EDITOR -> if (settings.recognizeShapes) "Snap on" else "Freehand"
    SettingsSection.TOOLBAR -> settings.toolbarPosition.label
    SettingsSection.FIGURES -> "${settings.penColors.size} colors"
    SettingsSection.COLORS -> "${settings.penColors.size} colors"
    SettingsSection.RING -> null
    SettingsSection.SHORTCUTS -> {
        val count = settings.toolShortcutKeys.size + settings.colorShortcutKeys.size
        if (count > 0) "$count bound" else null
    }
    SettingsSection.NAVIGATION -> if (settings.momentum > 0f) "Smooth" else "Crisp"
    SettingsSection.APPEARANCE -> settings.themeMode.shortLabel()
    SettingsSection.DOCUMENTS -> ".${settings.newDocumentFormat.name.lowercase(Locale.US)}"
    SettingsSection.STORAGE -> "${settings.pdfCacheLimitMb} MB"
    SettingsSection.BACKUP -> null
    SettingsSection.ABOUT -> "v${BuildConfig.VERSION_NAME}"
}

private fun ThemeMode.shortLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun ThemeMode.next(): ThemeMode = when (this) {
    ThemeMode.DARK -> ThemeMode.LIGHT
    ThemeMode.LIGHT -> ThemeMode.SYSTEM
    ThemeMode.SYSTEM -> ThemeMode.DARK
}

/**
 * Flat 1-tap grouped index of all [SettingsArea]s and their [SettingsSection]s for mobile.
 */
@Composable
internal fun GroupedSettingsIndex(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsQuickControls(settings = settings, onChange = onChange)
        SettingsArea.entries.forEach { area ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = area.title.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
                SettingsCard {
                    area.sections.forEachIndexed { index, entry ->
                        SectionRow(
                            title = entry.title,
                            summary = entry.summary,
                            icon = entry.icon,
                            valueLabel = sectionLiveValue(entry, settings),
                            onClick = { onOpenSection(entry) },
                        )
                        if (index < area.sections.size - 1) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.BuildConfig
import com.mobixournal.render.ShapeRecognition
import java.util.Locale

/**
 * Concise live readout shown on the trailing edge of settings index and sidebar rows so the
 * active configuration is readable without drilling into every page.
 */
internal fun sectionLiveValue(section: SettingsSection, settings: AppSettings): String? = when (section) {
    SettingsSection.STYLUS ->
        if (!settings.pressureEnabled) "Fixed"
        else String.format(Locale.US, "%.1fx", settings.pressureMultiplier)
    SettingsSection.GESTURES -> gestureSummaryLabel(settings)
    SettingsSection.EDITOR -> settings.defaultTool.label
    SettingsSection.TOOLBAR -> settings.toolbarPosition.label
    SettingsSection.FIGURES -> when (settings.shapeRecognition) {
        ShapeRecognition.ON_LIFT -> "On lift"
        ShapeRecognition.ON_HOLD -> "On hold"
        ShapeRecognition.OFF -> "Off"
    }
    SettingsSection.COLORS -> "${settings.penColors.size} colors"
    SettingsSection.RING -> "${ringFaces(settings).size} faces"
    SettingsSection.SHORTCUTS -> {
        val count = totalBoundShortcuts(settings)
        if (count > 0) "$count bound" else "None"
    }
    SettingsSection.NAVIGATION -> if (settings.momentum > 0f) "Smooth" else "Crisp"
    SettingsSection.APPEARANCE -> settings.themeMode.shortLabel()
    SettingsSection.DOCUMENTS ->
        if (settings.newDocumentFormat == NewDocumentFormat.MOBIX) ".mobix" else ".xopp"
    SettingsSection.STORAGE -> "${settings.pdfCacheLimitMb} MB"
    SettingsSection.BACKUP -> null
    SettingsSection.ABOUT -> "v${BuildConfig.VERSION_NAME}"
}

private fun gestureSummaryLabel(settings: AppSettings): String {
    val two = quickActionLabel(settings.twoFingerTapAction)
    val three = quickActionLabel(settings.threeFingerTapAction)
    return when {
        two == "Off" && three == "Off" -> "Off"
        three == "Off" -> two
        two == "Off" -> three
        else -> "$two / $three"
    }
}

private fun ThemeMode.shortLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

/**
 * Flat 1-tap grouped index of all [SettingsArea]s and their [SettingsSection]s for narrow screens.
 */
@Composable
internal fun GroupedSettingsIndex(
    settings: AppSettings,
    onOpenSection: (SettingsSection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsArea.entries.forEach { area ->
            Column {
                Text(
                    text = area.title.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                )
                SettingsCard {
                    area.sections.forEachIndexed { index, entry ->
                        SectionRow(
                            title = entry.title,
                            summary = null,
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

/**
 * Search matches across all [SettingsSection]s, each showing its parent area and live value.
 */
@Composable
internal fun SearchResultRows(
    query: String,
    results: List<SettingsSection>,
    settings: AppSettings,
    onOpen: (SettingsSection) -> Unit,
) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "No setting matches “$query”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    SettingsCard {
        results.forEachIndexed { index, entry ->
            val where = areaOf(entry)?.title
            SectionRow(
                title = entry.title,
                summary = if (where == null) entry.summary else "$where · ${entry.summary}",
                icon = entry.icon,
                valueLabel = sectionLiveValue(entry, settings),
            ) { onOpen(entry) }
            if (index < results.size - 1) {
                SettingsDivider()
            }
        }
    }
}

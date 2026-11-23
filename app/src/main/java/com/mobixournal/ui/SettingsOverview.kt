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
    SettingsSection.FOCUS_MODE -> "${resolveFocusBarSlots(settings.focusBarSlots).size} tools"
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

/** Concise live readout for each consolidated [SettingsDestination] on the mobile index. */
internal fun destinationLiveValue(destination: SettingsDestination, settings: AppSettings): String = when (destination) {
    SettingsDestination.PEN_INPUT ->
        if (!settings.pressureEnabled) "Fixed"
        else String.format(Locale.US, "%.1fx", settings.pressureMultiplier)
    SettingsDestination.CANVAS_TOOLS ->
        pageSizeLabel(settings.defaultPageWidthPt, settings.defaultPageHeightPt, settings.pageSizeUnit)
    SettingsDestination.PALETTE_RING -> "${ringFaces(settings).size} faces"
    SettingsDestination.WORKSPACE -> "${settings.themeMode.shortLabel()} · ${settings.toolbarPosition.label}"
    SettingsDestination.FOCUS_MODE -> "${resolveFocusBarSlots(settings.focusBarSlots).size} tools"
    SettingsDestination.SHORTCUTS -> {
        val count = totalBoundShortcuts(settings)
        if (count > 0) "$count bound" else "None"
    }
    SettingsDestination.FILES_DATA ->
        if (settings.newDocumentFormat == NewDocumentFormat.MOBIX) ".mobix" else ".xopp"
    SettingsDestination.ABOUT -> "v${BuildConfig.VERSION_NAME}"
}

/**
 * Flat 1-tap grouped index of the seven dynamic [SettingsDestination]s for narrow screens.
 */
@Composable
internal fun GroupedSettingsIndex(
    settings: AppSettings,
    onOpenDestination: (SettingsDestination) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsGroupCategory.entries.forEach { category ->
            val destinations = SettingsDestination.entries.filter { it.category == category }
            if (destinations.isEmpty()) return@forEach
            Column {
                Text(
                    text = category.title.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                )
                SettingsCard {
                    destinations.forEachIndexed { index, dest ->
                        SectionRow(
                            title = dest.title,
                            summary = dest.subtitle,
                            icon = dest.icon,
                            valueLabel = destinationLiveValue(dest, settings),
                            onClick = { onOpenDestination(dest) },
                        )
                        if (index < destinations.size - 1) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search matches across all [SettingsSection] aliases, routing each hit to its [SettingsDestination].
 */
@Composable
internal fun SearchResultRows(
    query: String,
    results: List<SettingsSection>,
    onOpen: (SettingsDestination) -> Unit,
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
            val dest = entry.toDestination()
            SectionRow(
                title = entry.title,
                summary = if (dest.title == entry.title) dest.category.title else dest.title,
                icon = entry.icon,
            ) { onOpen(dest) }
            if (index < results.size - 1) {
                SettingsDivider()
            }
        }
    }
}

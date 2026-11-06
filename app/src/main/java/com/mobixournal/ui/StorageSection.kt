package com.mobixournal.ui

import androidx.compose.runtime.Composable

/** Storage budgets and PDF handling: the largest text import and how much of the background-PDF
 * cache to keep. */
@Composable
fun StorageSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Import") {
        OptionGroup(
            title = "Text import limit",
            subtitle = "Maximum file size for plain text import (larger files are refused).",
            options = AppSettings.TEXT_IMPORT_LIMIT_CHOICES,
            selected = settings.textImportLimitMb,
            label = { "$it MB" },
            onSelect = { onChange(settings.copy(textImportLimitMb = it)) },
        )
    }
    SettingsGroup("Cache") {
        OptionGroup(
            title = "PDF cache limit",
            subtitle = "Maximum cache size for rendered PDF backgrounds (least recently used are evicted).",
            options = AppSettings.PDF_CACHE_LIMIT_CHOICES,
            selected = settings.pdfCacheLimitMb,
            label = { "$it MB" },
            onSelect = { onChange(settings.copy(pdfCacheLimitMb = it)) },
        )
    }
    SettingsGroup("Audio") {
        SwitchRow(
            title = "Save standalone audio recordings",
            subtitle = "Export standalone .m4a copies to Music/Recordings for external playback.",
            checked = settings.saveStandaloneAudio,
            onCheckedChange = { onChange(settings.copy(saveStandaloneAudio = it)) },
        )
    }
}

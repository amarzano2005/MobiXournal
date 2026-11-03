package com.mobixournal.ui

import androidx.compose.runtime.Composable

/** Storage budgets and PDF handling: the largest text import and how much of the background-PDF
 * cache to keep. */
@Composable
fun StorageSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Import") {
        OptionGroup(
            title = "Text import limit",
            subtitle = "A text file is typeset into a PDF whole, so a huge log takes minutes. Larger files are refused.",
            options = AppSettings.TEXT_IMPORT_LIMIT_CHOICES,
            selected = settings.textImportLimitMb,
            label = { "$it MB" },
            onSelect = { onChange(settings.copy(textImportLimitMb = it)) },
        )
    }
    SettingsGroup("Cache") {
        OptionGroup(
            title = "PDF cache limit",
            subtitle = "Backgrounds are cached so a file reopens instantly. Past this, the least recently used are dropped.",
            options = AppSettings.PDF_CACHE_LIMIT_CHOICES,
            selected = settings.pdfCacheLimitMb,
            label = { "$it MB" },
            onSelect = { onChange(settings.copy(pdfCacheLimitMb = it)) },
        )
    }
    SettingsGroup("Audio") {
        SwitchRow(
            title = "Save standalone audio recordings",
            subtitle = "Also saves a copy of each recording to Music/Recordings as .m4a.",
            checked = settings.saveStandaloneAudio,
            onCheckedChange = { onChange(settings.copy(saveStandaloneAudio = it)) },
        )
    }
}

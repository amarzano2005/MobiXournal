package com.mobixournal.ui

import androidx.compose.runtime.Composable

/** Storage budgets and PDF handling: the largest text import and how much of the background-PDF
 * cache to keep. */
@Composable
fun StorageSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Import") {
        OptionGroup(
            title = "Text import limit",
            subtitle = "Opening a text file typesets all of it into a PDF, so a huge log takes minutes " +
                "and thousands of pages. Files over this size are refused with a message instead.",
            options = AppSettings.TEXT_IMPORT_LIMIT_CHOICES,
            selected = settings.textImportLimitMb,
            label = { "$it MB" },
            onSelect = { onChange(settings.copy(textImportLimitMb = it)) },
        )
    }
    SettingsGroup("Cache") {
        OptionGroup(
            title = "PDF cache limit",
            subtitle = "Generated and imported backgrounds are kept so reopening a file is instant. " +
                "Past this, the oldest unused ones are dropped; they regenerate on the next open.",
            options = AppSettings.PDF_CACHE_LIMIT_CHOICES,
            selected = settings.pdfCacheLimitMb,
            label = { "$it MB" },
            onSelect = { onChange(settings.copy(pdfCacheLimitMb = it)) },
        )
    }
    SettingsGroup("Audio") {
        SwitchRow(
            title = "Save standalone audio recordings",
            subtitle = "Automatically export an audio copy to the device's public media folder (Music / Recordings) " +
                "upon completing a recording, in addition to binding it into the notebook.",
            checked = settings.saveStandaloneAudio,
            onCheckedChange = { onChange(settings.copy(saveStandaloneAudio = it)) },
        )
    }
}

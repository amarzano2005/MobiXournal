package com.mobixournal.ui

import androidx.compose.runtime.Composable

/**
 * Documents: the format a **new** document is born in.
 *
 * It is asked here and, the first time, by the tour ([OnboardingStepDocumentFormat] asks the format).
 * It is a *default for new work*, not a mode: a document that was opened keeps the format it came in
 * (`docs/mobix-format.md`), and Save As converts either way, so this page never has to say what the
 * already-open document is.
 *
 * The note under the format group is the trade-off in one breath, for the same reason the onboarding
 * step shows it: the format is the boundary of what the app can do, so the choice should be made
 * knowing which side of it a file full of graphs and tables ends up on.
 */
@Composable
fun DocumentsSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("New documents") {
        OptionGroup(
            title = "Default format",
            subtitle = "Used for new documents. Opened files keep their format; Save As converts.",
            options = NewDocumentFormat.values().toList(),
            selected = settings.newDocumentFormat,
            label = { it.label },
            onSelect = { onChange(settings.copy(newDocumentFormat = it)) },
        )
        SettingsNote(
            ".mobix preserves live interactive elements (plots, tables, shapes). .xopp exports elements " +
                "as standard ink for universal compatibility. Save As converts either way.",
        )
    }
}

package com.mobixournal.ui

import androidx.compose.runtime.Composable

/**
 * Documents: the format a **new** document is born in.
 *
 * One control, because the format policy is one question — asked here and, the first time, by the
 * tour ([OnboardingStep5DocumentFormat]). It is a *default for new work*, not a mode: a document that
 * was opened keeps the format it came in, and Save As converts either way, so this page never has to
 * say what the already-open document is (`docs/mobix-format.md`).
 *
 * The note under the group is the trade-off in one breath, for the same reason the onboarding step
 * shows it: the format is the boundary of what the app can do, so the choice should be made knowing
 * which side of it a file full of graphs and tables ends up on.
 */
@Composable
fun DocumentsSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("New documents") {
        OptionGroup(
            title = "Default format",
            subtitle = "What a document you create from scratch is written in. A document you open " +
                "keeps its own format, and Save As converts between them at any time.",
            options = NewDocumentFormat.values().toList(),
            selected = settings.newDocumentFormat,
            label = { it.label },
            onSelect = { onChange(settings.copy(newDocumentFormat = it)) },
        )
        SettingsNote(
            "A MobiXournal document keeps its graphs, tables, shapes and circuits as the things they " +
                "were authored as, so they can be reopened and corrected — and desktop Xournal++ needs " +
                "the companion plugin to open one. A Xournal++ document opens on the desktop as it is, " +
                "with those entities drawn as ink: portable, but no longer editable as a formula.",
        )
    }
}

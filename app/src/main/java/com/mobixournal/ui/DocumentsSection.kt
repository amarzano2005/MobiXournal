package com.mobixournal.ui

import androidx.compose.runtime.Composable

/**
 * Documents: the format a **new** document is born in, and the **canvas geometry** it is born on.
 *
 * Two controls, because they are the two halves of one question — asked here and, the first time, by
 * the tour ([OnboardingStep5DocumentFormat] asks the format; the geometry is a choice for Settings,
 * where a board is a deliberate thing to make rather than a step in a tour). They are *defaults for new
 * work*, not modes: a document that was opened keeps the format and the geometry it came in
 * (`docs/mobix-format.md`, `docs/infinite-canvas.md`), and Save As converts either way, so this page
 * never has to say what the already-open document is.
 *
 * The geometry control is offered whatever the format says, and its subtitle carries the rule rather
 * than the control disappearing: a `.xopp` has no chunk container, so the choice simply does not apply
 * there ([NewDocumentCanvas.modeFor]) — a radio group that came and went with another control would
 * hide which of the two decided.
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
        OptionGroup(
            title = "Canvas geometry",
            subtitle = "How the space of a new MobiXournal document is shaped: fixed pages, or a canvas " +
                "that grows as you write. A Xournal++ document has no chunk container, so one created " +
                "as a .xopp is always paged.",
            options = NewDocumentCanvas.values().toList(),
            selected = settings.newDocumentCanvas,
            label = { it.label },
            onSelect = { onChange(settings.copy(newDocumentCanvas = it)) },
        )
    }
}

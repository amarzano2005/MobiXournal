package com.mobixournal.ui

import com.mobixournal.format.SaveFormat

/**
 * The format a **new** document is born in — the one choice the format policy asks the user to make,
 * once (the onboarding tour's last step, `OnboardingDocumentFormat`) and changeable any time under
 * **Settings → App & data → Documents** (`DocumentsSection`).
 *
 * It is a choice between two *documents*, not two files: [MOBIX] keeps a graph, a table or a component
 * as the recipe it was authored as, while [XOPP] is the file desktop Xournal++ reads, so those entities
 * travel as the ink they are drawn as (`docs/mobix-format.md`, *Which format a document is written
 * in*).
 *
 * It decides **only what a new document starts as**. A document that was opened keeps its own format
 * ([SaveFormat] is sticky per document) and Save As converts in either direction — which is why this is
 * a default and not a mode.
 *
 * @property label What Settings and the onboarding step call the choice; it names the file that lands.
 * @property format The sticky [SaveFormat] a blank document adopts.
 */
enum class NewDocumentFormat(val label: String, val format: SaveFormat) {
    /**
     * This app's own document — a ZIP of named parts holding living entities, stylus telemetry and a
     * paged model. The recommended default, because it is the only format where a graph stays a
     * formula and a page can be read without the ones before it.
     */
    MOBIX("MobiXournal (.mobix)", SaveFormat.MOBIX),

    /**
     * The interchange document desktop Xournal++ reads and writes: gzip-compressed XML, with a graph
     * flattened to the ink it draws as. Nothing has to be installed on the desktop for it, which is why
     * it is the choice to make when the file has to travel.
     */
    XOPP("Xournal++ (.xopp)", SaveFormat.ORIGINAL),
}

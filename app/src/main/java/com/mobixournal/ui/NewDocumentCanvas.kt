package com.mobixournal.ui

import com.mobixournal.format.SaveFormat
import com.mobixournal.format.mobix.CanvasMode

/**
 * The **canvas geometry** a new document is born in — the second half of "what is a new document",
 * whose first half is [NewDocumentFormat].
 *
 * It is a choice between four *spaces*, not four views: fixed pages, a width-locked strip that extends
 * downwards, a height-locked strip that extends rightwards, or an unconstrained board. The modes
 * themselves are `format/mobix`'s (`CanvasMode`, owned by `docs/infinite-canvas.md`);
 * this is the one place the user is asked, and it decides **only what a new document starts as** —
 * a document that was opened keeps its own geometry exactly as it keeps its own format.
 *
 * The geometry is a `.mobix` fact, which is why [modeFor] collapses the choice to
 * [CanvasMode.PAGINATED] for a `.xopp`: a `.xopp` is a sequence of pages with no chunk container to
 * hold a board, so the setting is *not applicable* there rather than silently ignored — the same rule
 * [NewDocumentFormat] states for the format itself.
 *
 * @property label What Settings calls the choice.
 * @property mode The geometry a `.mobix` created from scratch is written in.
 */
enum class NewDocumentCanvas(val label: String, val mode: CanvasMode) {
    /** Fixed pages, the `.xopp` shape and the shape every new document had before this setting. */
    PAGINATED("Paged", CanvasMode.PAGINATED),

    /**
     * One locked width, extending downwards as far as the writing goes — the linear-notes shape. The
     * locked width is the default page size ([AppSettings.defaultPageWidthPt]).
     */
    INFINITE_VERTICAL("Infinite vertical", CanvasMode.INFINITE_VERTICAL),

    /**
     * One locked height, extending rightwards — annotations on a timeline, a process chart, a
     * panorama. The locked height is the default page size ([AppSettings.defaultPageHeightPt]).
     */
    INFINITE_HORIZONTAL("Infinite horizontal", CanvasMode.INFINITE_HORIZONTAL),

    /** No locked dimension at all: a free board that grows in every direction, negative included. */
    INFINITE_2D("Infinite board", CanvasMode.INFINITE_2D),
    ;

    /**
     * The geometry a new document written in [format] takes: this choice's [mode] for a `.mobix`, and
     * [CanvasMode.PAGINATED] for a `.xopp` — which has no chunks to keep a board in.
     */
    fun modeFor(format: SaveFormat): CanvasMode =
        if (format == SaveFormat.MOBIX) mode else CanvasMode.PAGINATED

    companion object {
        /** The choice [mode] stands for — how the page-format dialog shows where a document is now. */
        fun of(mode: CanvasMode): NewDocumentCanvas = entries.firstOrNull { it.mode == mode } ?: PAGINATED
    }
}

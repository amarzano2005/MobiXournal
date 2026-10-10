package com.mobixournal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.LayerInfo

/** How many panes the editor can show. Two: one document per half of the screen. */
const val PANE_COUNT = 2

/**
 * Everything the chrome mirrors out of **one** canvas.
 *
 * The canvas is a [DrawingSurfaceView] living outside the Compose tree, so it pushes its state up
 * through callbacks; this is where that lands. [EditorScreen] keeps one instance per pane, so in
 * split view the two documents keep separate zoom, page, layer and undo state — the toolbar simply
 * reads whichever pane is active (see [com.mobixournal.panes.EditorPane]).
 */
class PaneState {
    /** The canvas this pane wraps; null until the surface is created. */
    var surface by mutableStateOf<DrawingSurfaceView?>(null)
    
    /** Current zoom factor (1.0 = 100%, fit-to-width on first load). */
    var zoom by mutableStateOf(1f)
    
    /** Total number of pages in the document. */
    var pageCount by mutableStateOf(1)

    /** How many pages are picked in the overview grid — drives the bulk-delete entries in the Pages menu. */
    var selectedPages by mutableStateOf(0)
    
    /** How many pages are on the page clipboard (after copy in the overview). */
    var copiedPages by mutableStateOf(0)

    /** Overview edit mode: off means the grid is display/navigation only (see `DrawingSurfaceView`). */
    var pagesEditMode by mutableStateOf(false)
    
    /** Current page index, 0-based. */
    var currentPage by mutableStateOf(0)

    // Vertical scroll geometry (content px) fed from the surface, driving the right-edge scroll thumb.
    /** Current vertical scroll offset in pixels. */
    var scrollY by mutableStateOf(0f)
    /** Total height of the scrollable content in pixels. */
    var contentHeight by mutableStateOf(0f)
    /** Height of the visible viewport in pixels. */
    var viewportHeight by mutableStateOf(0f)

    /** Whether undo is available. */
    var canUndo by mutableStateOf(false)
    /** Whether redo is available. */
    var canRedo by mutableStateOf(false)
    /** Whether there is an active element selection. */
    var hasSelection by mutableStateOf(false)
    /**
     * The active selection's box in this canvas's own view px, or null when nothing is selected.
     * Republished by the surface whenever the selection or the view moves, and used to float the
     * contextual action bar next to what is selected.
     */
    var selectionRect by mutableStateOf<android.graphics.RectF?>(null)
    /** Whether there is an active text selection (caret inside a text box). */
    var hasTextSelection by mutableStateOf(false)
    /** Whether there is content on the clipboard (cut or copied selection/pages). */
    var hasClipboard by mutableStateOf(false)
    /** Whether a released background-select region is waiting to be copied or cut. */
    var hasBackgroundRegion by mutableStateOf(false)
    /** Control points in the open spline, or 0 when no spline is being laid down. */
    var splineNodes by mutableStateOf(0)
    /** Whether the search bar is open. */
    var searchOpen by mutableStateOf(false)
    /** Current search query text. */
    var searchQuery by mutableStateOf("")
    /** Current match index (1-based) in the search results. */
    var searchCurrent by mutableStateOf(0)
    /** Total number of search matches found. */
    var searchTotal by mutableStateOf(0)
    /** Whether handwriting AI recognition is currently indexing the document. */
    var searchIndexing by mutableStateOf(false)
    /** Current indexing progress message (e.g. "Processing page 1 of 3..."). */
    var searchIndexingProgress by mutableStateOf<String?>(null)
    /**
     * The lines of the live pen-diagnostics log, oldest first, while [EditorUiState.penDiagnostics]
     * is on (see `PenInputLog`). Filled by the canvas as events arrive.
     */
    var penDebugLines by mutableStateOf<List<String>>(emptyList())
    /** List of visible layers on the current page. */
    var layers by mutableStateOf<List<LayerInfo>>(emptyList())
    /** Background style of the current page (plain/lined/ruled/graph/dotted), or null for PDF backgrounds. */
    var backgroundStyle by mutableStateOf<String?>(null)

    /** The visible page's ruling parameters (desktop's `<background config=…>`), or null. */
    var backgroundConfig by mutableStateOf<String?>(null)
    /** Page size as (widthPt, heightPt) in points, or null when unavailable. */
    var pageSize by mutableStateOf<Pair<Double, Double>?>(null)
    /** Monotonically increasing version counter bumped whenever a new document is loaded into the canvas. */
    var documentVersion by mutableStateOf(0)
}

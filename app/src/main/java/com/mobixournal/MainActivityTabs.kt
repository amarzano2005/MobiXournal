package com.mobixournal

import android.graphics.Bitmap
import android.net.Uri
import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.model.Document
import com.mobixournal.io.canvasGeometryOf
import com.mobixournal.io.rememberCanvasMode
import com.mobixournal.panes.EditorPane
import com.mobixournal.render.DrawingSurfaceDefaults
import com.mobixournal.render.PageThumbnail
import com.mobixournal.render.PdfPageCache
import com.mobixournal.render.blankDocument
import com.mobixournal.render.repaintAfterResize
import com.mobixournal.render.setBackgroundResolver
import com.mobixournal.render.setInfiniteCanvas
import com.mobixournal.tabs.DocColors
import com.mobixournal.tabs.OpenTab
import com.mobixournal.tabs.TabStore
import com.mobixournal.ui.NewDocumentCanvas
import com.mobixournal.ui.TabsUiState
import java.io.File

// --- tabs: several documents open at once, restored on the next launch ----------------------
//
// [MainActivity]'s tab/session half, kept out of the activity file itself.
//
// A pane has exactly one canvas, so a tab switch is a swap: snapshot the outgoing document out of
// the surface ([snapshotActiveTab]), then load the incoming one into it ([showTab]). Everything a
// tab needs to come back — its document, source URI, save format, background PDF and page — lives
// in its [OpenTab] record, which is also what [TabStore] writes to disk.
//
// Every function here takes the [EditorPane] it applies to, defaulting to the pane in focus, so
// split view is just "the same thing, twice" — each pane keeps its own session and its own file.
//
// Undo history belongs to the surface, not the document, so it does not follow a tab switch: the
// incoming tab starts with a clean history. Its content, including unsaved edits, is intact.

/** A pane's tab strip view of its session, re-read whenever [MainActivity.tabsTick] changes. */
internal fun MainActivity.tabsUiState(p: EditorPane): TabsUiState {
    tabsTick.value // read so Compose re-invokes this when the session changes
    val dots = DocColors.assign(panes.flatMap { pane -> pane.tabs.tabs.map(OpenTab::docKey) })
    val index = panes.indexOf(p)
    return TabsUiState(
        titles = p.tabs.tabs.map { it.title },
        dotColors = p.tabs.tabs.map { dots[it.docKey] },
        activeIndex = p.tabs.activeIndex,
        // Split view shows one strip lane per pane, so a strip has to know whose it is: the badge
        // names the pane, and the focused one wears the accent (see `TabsUiState`).
        paneIndex = index.coerceAtLeast(0),
        paneCount = if (splitView.value) panes.size else 1,
        focused = activePane.value == index,
        // Never-saved: the shape is still the tab's to decide (see `TabsUiState.canvasEditable`).
        canvasEditable = p.tabs.active?.uri == null,
        onFocus = { if (index >= 0) activePane.value = index },
        onSelect = { selectTab(it, p) },
        onClose = { closeTab(it, p) },
        onNew = { newTab(p) },
        onMove = { sendTabToOtherPane(it, p, keepHere = false) },
        onMirror = { sendTabToOtherPane(it, p, keepHere = true) },
        onReorder = { from, to -> reorderTab(from, to, p) },
        // The showing tab's live edits are only in the canvas until it is snapshotted, so the
        // overview would otherwise preview a stale page for the tab you are looking at.
        onOverview = { snapshotActiveTab(p) },
        preview = { index, widthPx, onReady -> previewTabPage(p, index, widthPx, onReady) },
    )
}

/**
 * Hand [from]'s tab at [index] to the other pane — the long-press actions on a tab.
 *
 * With [keepHere] false this is a *move*: the tab leaves this pane (which falls back to a blank
 * document if it was the last one) and opens in the other. With [keepHere] true it is a *mirror*:
 * a second **view** of the same document opens in the other pane. The two views share one
 * document — the new tab keeps the original's [OpenTab.docKey], so `MirrorSync` pushes every edit
 * from either side straight into the other — while keeping their own scroll, zoom and page, and
 * both write back to the same file. Split view is opened if it was closed, since otherwise there
 * is nowhere to put it.
 */
internal fun MainActivity.sendTabToOtherPane(index: Int, from: EditorPane, keepHere: Boolean) {
    val other = panes.firstOrNull { it !== from } ?: return
    snapshotActiveTab(from)
    val source = from.tabs.tabs.getOrNull(index) ?: return
    hydrate(other)
    val newId = TabStore.newId()
    // A tab still waiting to be parsed is handed over as a *file*: its snapshot is copied under
    // the copy's new id, so the other pane can hydrate it itself rather than us parsing a whole
    // document inside this long-press.
    if (!source.hydrated) other.store.adopt(from.store, source.id, newId)
    other.tabs.open(source.copy(id = newId))
    if (!keepHere) {
        val showing = from.tabs.active?.id
        from.tabs.close(index)
        if (from.tabs.isEmpty) from.tabs.open(blankTab(from))
        from.tabs.active?.takeIf { it.id != showing }?.let { show(it, from) }
        // The document just moved: hand the tools to the pane it landed in, so the next stroke edits
        // the tab the user chose to move rather than the one left behind.
        activePane.value = panes.indexOf(other).coerceAtLeast(0)
    }
    // Only paints now if that pane already has a canvas; otherwise [restoreTabs] shows it when
    // split view builds one.
    other.tabs.active?.let { show(it, other) }
    splitView.value = true
    from.persist()
    other.persist()
    tabsTick.value++
}

/** Load a pane's stored session into its manager without touching a canvas, if it has none yet. */
internal fun MainActivity.hydrate(p: EditorPane) {
    if (!p.tabs.isEmpty) return
    val session = p.store.load() ?: return
    session.tabs.forEach(p.tabs::open)
    p.tabs.select(session.activeIndex)
}

/** Copy the live canvas back into the showing tab's record, so switching away doesn't lose it. */
internal fun MainActivity.snapshotActiveTab(p: EditorPane = pane) {
    // A mirrored edit may still be waiting on its coalesced pass; apply it first so the records this
    // is about to read (and the other pane's canvas) are current.
    mirrors.flush()
    val view = p.surface ?: return
    // A tab still being parsed *or fetched* in the background hasn't reached the canvas yet, so what
    // is on it belongs to the tab before it — copying that in would overwrite the pending document
    // (and, for a slow remote/cloud open, persist an empty page over a good snapshot).
    if (p.tabs.active?.hydrated == false || p.tabs.active?.opening == true) return
    p.tabs.updateActive {
        it.copy(
            document = view.toDocument(),
            format = p.saveFormat,
            pdfPath = view.pdfSourceFile()?.absolutePath,
            // The resolver a paged `.mobix` reads its backgrounds through, captured like the PDF
            // path: the tab has to be able to hand the next canvas the same one.
            backgrounds = view.backgroundResolver,
            page = view.visiblePageIndex(),
        )
    }
}

/**
 * Put [p]'s showing tab on its canvas, parsing its snapshot first if it is still a lazy
 * placeholder from the session index ([TabStore.load]).
 *
 * The parse is gzip + XML over a whole document, far too slow for the main thread, so an
 * unhydrated tab is read on a worker and handed to [showTab] when it lands — the canvas keeps
 * whatever it was showing for those few frames instead of freezing. A tab whose selection has
 * moved on by then is dropped: only the still-showing tab is painted.
 */
internal fun MainActivity.show(tab: OpenTab, p: EditorPane = pane, then: () -> Unit = {}) {
    if (tab.hydrated) {
        showTab(tab, p)
        then()
        return
    }
    Thread {
        val full = p.store.hydrate(tab)
        runOnUiThread {
            if (p.tabs.active?.id != full.id) {
                then()
                return@runOnUiThread
            }
            if (!full.hydrated) {
                // The snapshot exists but couldn't be parsed. Say so and leave it on disk untouched —
                // the tab stays unhydrated so no later persist can overwrite it with a blank page.
                toast(getString(R.string.restore_failed, tab.title))
                then()
                return@runOnUiThread
            }
            p.tabs.updateActive { if (it.id == full.id) full else it }
            showTab(full, p)
            tabsTick.value++
            then()
        }
    }.start()
}

/** Load [tab] onto [p]'s canvas: its document, background PDF, save format and page. */
internal fun MainActivity.showTab(tab: OpenTab, p: EditorPane = pane) {
    val view = p.surface ?: return
    if (audio.isPlaying || audio.nowPlaying != null) {
        audio.stopPlayback()
        karaokeDriver?.stop()
    }
    lastAudioRef = null
    miniPlayerVisible.value = false
    audioTick.value++
    p.saveFormat = tab.format
    p.pendingSaveName = tab.title
    val pdf = tab.pdfPath?.let(::File)?.takeIf(File::exists)
    view.isReadOnly = tab.isReadOnly
    // Backgrounds first: a paged `.mobix` resolves its PDF when a page names it, so the file the
    // tab recorded may be null while the resolver can still answer.
    view.setBackgroundResolver(tab.backgrounds)
    view.setPdfSource(pdf?.let(PdfPageCache::shared))
    view.clearPdfAnalysis() // cleared until extraction below finishes
    view.load(tab.document)
    // …and whether it is a **board**: the tab carries the mode it was born on or opened with, and the
    // rest of the geometry comes from the file's own `canvas.json` (a document this session opened) or
    // from the strip its mode runs in (one created here). Without this, switching away from a chunked
    // document and back would draw it as a page stack — the rewrite `docs/mobix-format.md` refuses the
    // *save* over, showing up in the *view*.
    view.setInfiniteCanvas(io.canvasGeometryOf(tab.document, tab.canvasMode))
    if (tab.page > 0) view.goToPage(tab.page)
    if (pdf != null) analyzePdfInBackground(pdf, view)
}

/**
 * Rasterise the page tab [index] of [p] is showing, for the tab overview grid, and hand it back on
 * the main thread.
 *
 * Both halves run off the main thread: a tab still waiting to be parsed ([OpenTab.hydrated] false)
 * costs a whole gzip + XML read, and even a parsed one can be a document of many thousand strokes,
 * neither of which belongs in the tap that opened the grid. Hydrating here deliberately does *not*
 * write the parsed tab back into the session — looking at the overview shouldn't undo the lazy
 * restore that keeps a cold start off the ANR watchdog.
 *
 * The whole grid's previews share **one** worker ([MainActivity.previewWorker]) so only a single
 * document is ever parsed at a time. Fanning a thread out per tab instead put every open document
 * in memory at once, which is an out-of-memory kill on a session of large files.
 */
internal fun MainActivity.previewTabPage(
    p: EditorPane,
    index: Int,
    widthPx: Int,
    onReady: (Bitmap?) -> Unit,
) {
    val tab = p.tabs.tabs.getOrNull(index) ?: return onReady(null)
    previewWorker.execute {
        val full = if (tab.hydrated) tab else runCatching { p.store.hydrate(tab) }.getOrNull()
        val page = full?.document?.pages?.getOrNull(full.page)
        val bitmap = page?.let { runCatching { PageThumbnail.render(it, widthPx) }.getOrNull() }
        runOnUiThread { onReady(bitmap) }
    }
}

/** Switch [p] to the tab at [index], snapshotting the one being left. */
internal fun MainActivity.selectTab(index: Int, p: EditorPane = pane) {
    // A chip is a statement about *its* pane: selecting one from a lane the tools aren't on has to
    // hand them over too, or the toolbar would keep editing the other half.
    activePane.value = panes.indexOf(p).coerceAtLeast(0)
    snapshotActiveTab(p)
    if (audio.isPlaying || audio.nowPlaying != null) {
        audio.stopPlayback()
        karaokeDriver?.stop()
    }
    lastAudioRef = null
    miniPlayerVisible.value = false
    audioTick.value++
    if (!p.tabs.select(index)) return
    p.tabs.active?.let { show(it, p) }
    tabsTick.value++
    p.persist()
}

/**
 * Reorder [p]'s strip: the tab at [from] moves to [to] — the drag gesture on a tab. No document
 * is loaded or unloaded, so the canvas is left alone; only the strip order and the saved session
 * change.
 */
internal fun MainActivity.reorderTab(from: Int, to: Int, p: EditorPane = pane) {
    if (!p.tabs.move(from, to)) return
    tabsTick.value++
    p.persist()
}

/**
 * Close [p]'s tab at [index]. Closing the last one leaves a fresh blank document rather than an
 * empty pane, and the canvas is only reloaded when the *showing* tab actually changed.
 */
internal fun MainActivity.closeTab(index: Int, p: EditorPane = pane) {
    // Closing a tab in a lane focuses that pane: what takes its place (or the fallback blank page)
    // is now what the chrome will act on, so it must be the pane in focus.
    activePane.value = panes.indexOf(p).coerceAtLeast(0)
    snapshotActiveTab(p)
    if (audio.isPlaying || audio.nowPlaying != null) {
        audio.stopPlayback()
        karaokeDriver?.stop()
    }
    lastAudioRef = null
    miniPlayerVisible.value = false
    audioTick.value++
    val showing = p.tabs.active?.id
    p.tabs.close(index) ?: return
    if (p.tabs.isEmpty) p.tabs.open(blankTab(p))
    p.tabs.active?.takeIf { it.id != showing }?.let { show(it, p) }
    tabsTick.value++
    p.persist()
    prunePdfCache() // the closed tab's background PDF is now unreferenced
}

/** Open a fresh blank document in a new tab of [p] and switch to it. */
internal fun MainActivity.newTab(
    p: EditorPane = pane,
    canvasMode: CanvasMode = newDocumentCanvas,
) {
    // The "+" belongs to a lane, so the new document opens — and the tools land — in that pane.
    activePane.value = panes.indexOf(p).coerceAtLeast(0)
    snapshotActiveTab(p)
    p.tabs.open(blankTab(p, canvasMode))
    p.tabs.active?.let { show(it, p) }
    tabsTick.value++
    p.persist()
}

/**
 * A fresh blank document in the format **new documents are born in**, on the canvas geometry they are
 * born **on** — the two choices made in Settings → App & data ([newDocumentFormat] and
 * [newDocumentCanvas], pushed down by [applyNewDocumentFormat] and [applyNewDocumentCanvas]).
 *
 * Both are set on the tab rather than left to the pane's defaults because the tab's values *are* the
 * sticky ones ([OpenTab.format], [OpenTab.canvasMode], restored with the tab): a new document started
 * as `.mobix` stays one, one started as `.xopp` is what plain Save offers to write, and a document
 * started on an infinite canvas is **written as chunks from its first save** rather than converted
 * later — which is the whole point of creating it in the mode (`docs/infinite-canvas.md`).
 *
 * The document itself is still one blank page: on a board that page *is* the first chunk (the strip
 * coordinates come from the page index, `MobixDocument.of`), so the same blank sheet is a page in one
 * geometry and chunk `(0, 0)` in another.
 *
 * **A strip is born the size of the pane.** On a page list the sheet's size is the paper — A4 is A4
 * because a printer will see it — but an infinite canvas has no printer in its future until an export,
 * and a strip drawn at A4 width on a 2400 px screen is a narrow band of paper with grey down both sides:
 * "infinite" paper that visibly ends a quarter of the way across. So the **bound** of a strip is the
 * pane's own extent in pt (px at 100%, which is what the sheet's scale is): the sheet covers the whole
 * canvas the moment it opens, scrolling adds more of it, and the page-format dialog is where that number
 * is changed afterwards.
 *
 * Only the *locked* axis moves. The running one carries no size of the sheet (it goes on forever) and is
 * read only by an export — its cut length — so it keeps the default paper rather than inheriting a screen
 * height, and a 2D board covers the canvas by construction and so keeps it too.
 */
internal fun MainActivity.blankTab(
    p: EditorPane = pane,
    canvasMode: CanvasMode = newDocumentCanvas,
): OpenTab {
    val format = if (canvasMode != CanvasMode.PAGINATED) {
        com.mobixournal.format.SaveFormat.MOBIX
    } else {
        newDocumentFormat
    }
    val surface = p.surface
    val panePt = if (surface != null && surface.width > 0 && surface.height > 0) {
        Pair(surface.width.toDouble(), surface.height.toDouble())
    } else {
        Pair(DrawingSurfaceDefaults.defaultPageWidth, DrawingSurfaceDefaults.defaultPageHeight)
    }
    val doc = boundToPane(blankDocument(), canvasMode, panePt)
    return OpenTab(
        TabStore.newId(),
        MainActivity.UNTITLED,
        doc,
        format = format,
        canvasMode = canvasMode,
    )
}

/**
 * [document] with the fixed side of [mode]'s strip set to the pane's own extent — the one number a
 * board's birth (and its conversion, [applyCanvasModeToDocument]) takes from the screen.
 *
 * Applied to **every** page, so a document converted into a strip has all its cells on the same sheet
 * (they share one bound: the axis is a property of the board, not of a tile), and left alone for a page
 * list (whose paper is the printer's) and for a 2D board (which locks nothing, so it has no side to
 * take: `CanvasGeometry.fixedExtentPt`). The running axis is deliberately untouched — it carries no
 * size of the sheet and is read only by an export, as its cut length.
 */
internal fun boundToPane(
    document: Document,
    mode: CanvasMode,
    panePt: Pair<Double, Double>,
): Document = when (mode) {
    CanvasMode.INFINITE_VERTICAL ->
        document.copy(pages = document.pages.map { it.copy(width = panePt.first) })
    CanvasMode.INFINITE_HORIZONTAL ->
        document.copy(pages = document.pages.map { it.copy(height = panePt.second) })
    CanvasMode.PAGINATED, CanvasMode.INFINITE_2D -> document
}

/**
 * Bring back the tabs [p] was last closed with, or start it on a single blank one. Called once
 * that pane's canvas exists; guarded so a surface rebuilt within this activity doesn't restore
 * twice (and so the right-hand pane only restores when split view actually opens it).
 */
internal fun MainActivity.restoreTabs(p: EditorPane, then: () -> Unit = {}) {
    // A pane that already has a session is being handed a *replacement* canvas (split view was
    // switched off and on again): put its showing document straight back onto the new surface.
    if (!p.tabs.isEmpty) {
        p.tabs.active?.let { show(it, p, then) } ?: then()
        return
    }
    isRestoringTabs = true
    // Reading the index and parsing the showing tab's snapshot is disk + gzip + XML work, so it
    // happens on a worker; only the (cheap) hand-off to the canvas runs on the main thread. Doing
    // it inline is what pushed the first frame past the ANR watchdog on a big restored session.
    Thread {
        val session = p.store.load()
        runOnUiThread {
            if (p.tabs.isEmpty) {
                if (session == null) {
                    p.tabs.open(blankTab(p))
                } else {
                    session.tabs.forEach(p.tabs::open)
                    p.tabs.select(session.activeIndex)
                }
                val active = p.tabs.active
                if (active != null) {
                    show(active, p) {
                        isRestoringTabs = false
                        then()
                    }
                } else {
                    isRestoringTabs = false
                    then()
                }
                tabsTick.value++
            } else {
                isRestoringTabs = false
                then()
            }
        }
    }.start()
}

/** Write the focused pane's session out, on every tab change and on the way to the background. */
internal fun MainActivity.persistTabs() {
    mirrors.flush() // don't write a session that predates a pending mirrored edit
    pane.persist()
    prunePdfCache()
}

/**
 * Drop the background PDFs no open tab refers to any more. Each open allocates its own file
 * (`PdfStore`), so without this sweep the folders would grow with every document opened.
 *
 * The live surfaces are counted alongside the tab records: a document whose PDF was just
 * imported has not been snapshotted back into its tab yet, and deleting the file out from under
 * the canvas is precisely the bug this store exists to prevent.
 */
internal fun MainActivity.prunePdfCache() {
    val live = panes.flatMap { p ->
        p.tabs.tabs.map(OpenTab::pdfPath) + listOf(p.surface?.pdfSourceFile()?.absolutePath)
    }
    // The documents still open keep their page readers: a paged `.mobix` reads its pages through
    // one, so a reader whose document is gone from every tab *and* every canvas is safe to close.
    val liveDocuments = panes.flatMap { p ->
        p.tabs.tabs.map { it.document.documentId } + listOf(p.surface?.toDocument()?.documentId)
    }
    io.prunePagedOpens(liveDocuments)
    // Pixmap copies aren't recorded in a tab (a reopened document re-resolves them), so only the
    // pictures the live canvases are decoding from are kept.
    val liveImages = panes.flatMap { p ->
        p.surface?.imageSources()?.values?.map(File::getAbsolutePath).orEmpty()
    }
    io.prune(live, liveImages)
}

/**
 * Turn split view on or off. Leaving it snapshots the right-hand pane and hands focus back to the
 * left one, so a menu action never targets a canvas that is no longer on screen. The pane's tabs
 * are kept (and stay on disk), so turning split view back on brings the same documents back.
 *
 * A toggle hands every pane a canvas of a different width in the *same* frame — the one layout change
 * a canvas cannot hear about through an input event — so each surface is told to rebuild from the
 * extent it ends up with (see `render/DrawingSurfaceResize.kt`). The pane's own Compose box announces
 * its new size on the traversal that follows, and this is the belt to that braces: the call is posted,
 * so it runs once the frame has committed the new width, and a pane left drawing the split's extent
 * cannot survive the toggle. The zoom level is preserved as the user set it, ensuring switching
 * between single view and split view remains completely stable.
 */
internal fun MainActivity.toggleSplitView() {
    val on = !splitView.value
    if (!on) {
        panes.forEach { snapshotActiveTab(it); it.persist() }
        activePane.value = 0
    }
    splitView.value = on
    panes.forEach { pane -> pane.surface?.post { pane.surface?.repaintAfterResize() } }
}

/**
 * Starts a fresh blank page/tab with [mode]'s canvas geometry.
 *
 * Called when the user confirms the canvas mode change popup:
 * because modifying the canvas geometry on the current page is not possible,
 * a new blank document opens on the chosen canvas mode.
 */
internal fun MainActivity.applyCanvasModeToDocument(mode: NewDocumentCanvas) {
    newTab(pane, mode.mode)
}

/** The file name behind a `content://` URI, for the tab's label. Falls back to `UNTITLED`. */
internal fun MainActivity.displayName(uri: Uri): String = io.displayName(uri, MainActivity.UNTITLED)

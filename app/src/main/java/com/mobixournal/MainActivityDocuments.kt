package com.mobixournal

import com.mobixournal.ui.openVerdictNotice

import android.content.Intent
import android.net.Uri
import com.mobixournal.audio.documentAudioFiles
import com.mobixournal.format.SaveFormat
import com.mobixournal.io.LoadedFile
import com.mobixournal.io.StorageLimits
import com.mobixournal.io.UriStaging
import com.mobixournal.io.withPortability
import com.mobixournal.io.mobixNameFor
import com.mobixournal.io.pdfNameFor
import com.mobixournal.io.saveNameFor
import com.mobixournal.io.xoppNameFor
import com.mobixournal.portability.PortabilitySummary
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.resetZoom
import com.mobixournal.render.ImageImport
import com.mobixournal.render.ImportPdfMode
import com.mobixournal.render.PdfImport
import com.mobixournal.render.blankDocument
import com.mobixournal.render.PdfOutlineCache
import com.mobixournal.render.PdfOutlineExtractor
import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PdfPageCache
import com.mobixournal.render.PdfTextExtractor
import com.mobixournal.render.PdfTextIndexCache
import com.mobixournal.render.setBackgroundResolver
import com.mobixournal.render.setDefaultPageSize
import com.mobixournal.tabs.OpenTab
import com.mobixournal.tabs.TabStore
import com.mobixournal.ui.AppSettings
import com.mobixournal.ui.PdfImportPrompt
import java.io.File

// --- document I/O: open, import, save --------------------------------------------------------
//
// [MainActivity]'s half of the document pipeline, kept out of the activity file itself: opening a
// file into a tab, adopting a PDF or an image as annotatable pages, and writing the document back
// out. The policy lives in [com.mobixournal.io.DocumentIo]; these are the activity-side steps that
// stage the bytes off the UI thread, drive the canvas, and keep the tab strip in step.

/**
 * Open [uri] **in a new tab**: push the tab first so the load lands in it, then snapshot the
 * loaded document back into it. A failed open takes its half-built tab down with it rather than
 * leaving an empty stub in the strip.
 */
internal fun MainActivity.openDocument(uri: Uri) {
    val openingPane = pane
    val sourceName = displayName(uri)
    snapshotActiveTab()
    // `opening = true` until the bytes land: the tab's blank stand-in must never be snapshotted while
    // the canvas still shows the previous document — on a slow remote/cloud fetch that is exactly how
    // an empty page ends up persisted over the tab's cached content.
    val placeholder = OpenTab(
        TabStore.newId(), displayName(uri), blankDocument(), uri.toString(), opening = true,
    )
    val created = tabs.open(placeholder)
    pendingSaveName = displayName(uri)
    tabsTick.value++
    // Keep the grant so plain Save can write back here later, even after a restart.
    io.persist(uri)
    // The entry screen's card for this notebook; its face follows once the bytes have landed (below).
    rememberRecentNotebook(uri, displayName(uri))
    // The bytes may be coming off a network share, so fetch them on a worker and only touch the
    // canvas once they have landed. A failure takes the half-built tab back down with it.
    inBackground("Opening $sourceName.", {
        val staged = io.stageIn(uri, "open")
        // The portability report walks every page, so it is answered **here**, on the worker that fetched the
        // file, and not inside the open: a paged `.mobix` must reach its first frame having read no page.
        try { staged to io.read(staged, sourceName, uri).withPortability() } catch (failure: Throwable) {
            staged.delete()
            throw failure
        }
    }) { result ->
        val focus = activePane.value
        activePane.value = panes.indexOf(openingPane)
        try {
        // Staging names are unique per open, so the copy has to be swept once it has been read
        // or the cache would grow a file per document opened.
        result.mapCatching { (staged, loaded) ->
            try {
                check(tabs.active?.id == placeholder.id) { "The opening tab is no longer selected" }
                loadDocument(loaded, uri, placeholder, created)
            } finally { if (!io.retainsStaged(staged)) staged.delete() }
        }
            .onSuccess {
                // If the open was intercepted by a newer-app consent prompt, opening stays true until
                // the user accepts or declines. Otherwise the fetch landed normally.
                if (pendingNewerAppConsent.value == null) {
                    tabs.updateActive { if (it.id == placeholder.id) it.copy(opening = false) else it }
                    // The document that just landed *is* the archive it came from, so its snapshot is
                    // that copy rather than an encode of the whole container (see `MobixTabSnapshots`).
                    snapshotActiveTab()
                    // The card's face, from the pages already in hand: the entry screen reads a PNG
                    // instead of parsing this document again.
                    tabs.active?.let { tab -> runCatching { rememberFaceOf(uri, tab.document.pages.first()) } }
                }
            }
            .onFailure {
                toast("Open failed: ${it.message}")
                tabs.tabs.indexOfFirst { it.id == placeholder.id }.takeIf { it >= 0 }?.let(tabs::close)
                tabs.active?.let { show(it) }
            }
        tabsTick.value++
        persistTabs()
        } finally { activePane.value = focus }
    }
}

/**
 * Push the Storage preferences down into [io], which enforces them (refusing an oversized text
 * import, and trimming the PDF cache on the next prune). Called on load and on every edit, since
 * [DocumentIo] outlives any one settings value.
 */
internal fun MainActivity.applyStorageLimits(settings: AppSettings) {
    io.limits = StorageLimits(
        textImportBytes = settings.textImportLimitBytes,
        pdfCacheBytes = settings.pdfCacheLimitBytes,
        // The pixmap copies share the one user-facing cache budget rather than adding a second
        // number to Settings; both stores hold background pictures for open documents.
        imageCacheBytes = settings.pdfCacheLimitBytes,
    )
}

/**
 * Push the Editor's **default page size** down into the blank-sheet factories, so every *created*
 * sheet is born at the size the user chose: a new document, the blank document a pane falls back to
 * when its last tab closes, and **Add page**. Called on load and on every edit, like
 * [applyStorageLimits] — the render layer outlives any one settings value, so the default has to be
 * kept current rather than read once.
 */
internal fun MainActivity.applyDefaultPageSize(settings: AppSettings) {
    setDefaultPageSize(settings.defaultPageWidthPt, settings.defaultPageHeightPt)
}

/**
 * The format a **brand-new** document is born in — Settings → App & data' choice, `.mobix` until the
 * user says otherwise.
 *
 * It is process state rather than a field on the activity because the one place it is read
 * ([blankTab]) is a plain function with no settings in hand, exactly like the default page size
 * ([setDefaultPageSize]): a blank document is created from a tab-strip tap, from closing the last tab,
 * and from a restored session, and none of them knows about a Compose-held settings value.
 *
 * Only a *new* document consults it. A document that was opened carries its own format in its tab
 * ([OpenTab.format]), which is what keeps a `.xopp` a `.xopp` — so this is never a mode, and Save As
 * still converts either way.
 */
internal var newDocumentFormat: SaveFormat = SaveFormat.MOBIX

/**
 * Push the **new-document format** down into [blankTab]. Called on load and on every settings change,
 * like [applyDefaultPageSize] — the choice is made in the onboarding tour and in Settings, and both
 * have to reach the next blank document without a restart.
 */
internal fun MainActivity.applyNewDocumentFormat(settings: AppSettings) {
    newDocumentFormat = settings.newDocumentFormat.format
}

/**
 * Push every settings value the **non-Compose layers** hold: the storage budgets, the default page
 * size, and the format new documents are born in.
 *
 * They travel together because they are pushed at the same two moments — the settings that come off
 * disk at startup, and every later change made in Settings or in the onboarding tour — and because
 * missing one is invisible until the feature it drives misbehaves: a forgotten page size shows up as
 * an A4 sheet, a forgotten format as a document saved in the wrong one. One call site means adding
 * another is one line, in one place.
 */
internal fun MainActivity.applySettingsToLayers(settings: AppSettings) {
    applyStorageLimits(settings)
    applyDefaultPageSize(settings)
    applyNewDocumentFormat(settings)
}

/**
 * Apply a loaded document onto the canvas and sync state with [isReadOnly].
 */
private fun MainActivity.applyLoadedDoc(loaded: LoadedFile.Doc, source: Uri, isReadOnly: Boolean) {
    saveFormat = loaded.format
    surface?.isReadOnly = isReadOnly
    surface?.setPdfSource(loaded.pdf?.let(PdfPageCache::shared))
    surface?.clearPdfAnalysis() // cleared until extraction below finishes
    // The pictures behind pixmap backgrounds were copied out to local files on load;
    // hand them over before the document, so the first frame already has them.
    surface?.setImageSources(loaded.images)
    // A `.mobix` opened page by page adopts a page's background when that page is read, so the
    // canvas is handed the resolver that does it (null for every document resolved at open).
    surface?.setBackgroundResolver(loaded.backgrounds)
    // …and because that adoption happens at the frame that needs the bytes, a background the archive
    // does not hold is discovered there rather than at the open: reported once per reference, in the
    // same words the open uses for a document whose references resolve up front.
    // …unless the open already produced the document-wide report below: then this per-page notice would
    // be the same fact said a second time, once per page, which is exactly what the report replaces.
    surface?.onMissingBackground = { references ->
        if (loaded.portability == null) {
            toast(
                if (references.size == 1) {
                    "Background not found: ${references.first()}; that page will be blank"
                } else {
                    "Backgrounds not found: ${references.joinToString(", ")}; those pages will be blank"
                },
            )
        }
    }
    surface?.load(loaded.document)
    // A document opens at **100% zoom**, whatever the previous one was left at: opening a file is a
    // fresh look at it, and a remembered 250% from some other document is a zoom nobody asked for.
    surface?.resetZoom()
    if (loaded.pdf != null) analyzePdfInBackground(loaded.pdf)
    val portability = loaded.portability?.let(PortabilitySummary::of)
    when {
        // One sentence for the whole file, once per open: the references are in it, so there is no
        // toast per page and no second sentence for the same fact.
        portability != null && !portability.isPortable -> toast(portability.sentence())
        // A `.xopp` has no archive directory to check, so its own two facts keep their own sentences.
        loaded.portability == null && loaded.pdf == null && loaded.missingPdf ->
            toast("Background PDF not found; those pages will be blank")
        loaded.portability == null && loaded.pdf == null && loaded.missingImage ->
            toast("Background image not found; those pages will be blank")
    }
}

/** Put a staged local copy of the document onto the canvas ([DocumentIo] decided what it is). */
internal fun MainActivity.loadDocument(
    loaded: LoadedFile,
    source: Uri,
    placeholder: OpenTab? = null,
    created: Int = -1,
) {
    when (loaded) {
        // A raw PDF isn't a document to parse — it becomes a fresh annotatable one over its pages.
        is LoadedFile.Pdf -> {
            // A PDF opened from outside becomes a **new document**, so it is born in the format new
            // documents are born in (Settings → App & data), never in `.xopp` behind the reader's
            // back: the default is `.mobix`, which bundles the PDF beside its ink as it bundles audio.
            // A PDF we typeset from a text file lives only in the prunable cache, so there is no
            // stable path to link: that one goes out as the package which embeds its bytes.
            saveFormat = if (loaded.generated) SaveFormat.ZIPPED else newDocumentFormat
            surface?.isReadOnly = false
            adoptPdf(loaded.file, ImportPdfMode.REPLACE, source.toString(), inStore = loaded.generated)
            // The tab came from a PDF, so it has no `.xopp` on disk yet: drop the source URI so
            // plain Save asks for a destination instead of writing document bytes over the PDF,
            // and suggest the PDF's own name with the extension swapped. The PDF itself stays on
            // as the page background, so the saved file still opens over it in desktop Xournal++.
            pendingSaveName = saveNameFor(displayName(source), saveFormat)
            tabs.updateActive {
                it.copy(title = pendingSaveName, uri = null, isReadOnly = false, format = saveFormat)
            }
            tabsTick.value++
            return
        }
        // An image likewise isn't a document to parse — it becomes a one-page document with the
        // picture as that page's pixmap background.
        is LoadedFile.Image -> {
            saveFormat = SaveFormat.ORIGINAL
            surface?.isReadOnly = false
            if (!adoptImage(loaded.file, source.toString())) {
                toast("Couldn't read that image")
                return
            }
            // Like a PDF import, the tab has no `.xopp` behind it yet: drop the source URI so a
            // plain Save asks where to put one instead of writing document bytes over the image.
            pendingSaveName = xoppNameFor(loaded.name)
            tabs.updateActive { it.copy(title = pendingSaveName, uri = null, isReadOnly = false) }
            tabsTick.value++
            return
        }
        is LoadedFile.Doc -> {
            // The gate decided at open (`MobixCapabilities`); the notice and the state it disables are
            // two readings of that one verdict (docs/mobix-schema-spec.md, *Declared capabilities*).
            val notice = openVerdictNotice(displayName(source), loaded.verdict)
            if (notice != null && notice.asksForAnswer) {
                pendingNewerAppConsent.value = PendingConsent(
                    notice = notice,
                    onConfirm = {
                        applyLoadedDoc(loaded, source, isReadOnly = notice.readOnly)
                        placeholder?.let { p ->
                            tabs.updateActive { if (it.id == p.id) it.copy(opening = false, isReadOnly = notice.readOnly) else it }
                        }
                        snapshotActiveTab()
                        tabsTick.value++
                        persistTabs()
                    },
                    onDismiss = {
                        val targetId = placeholder?.id
                        val idx = if (targetId != null) tabs.tabs.indexOfFirst { it.id == targetId } else -1
                        if (idx >= 0) {
                            closeTab(idx)
                        } else if (created >= 0) {
                            tabs.close(created)
                            if (tabs.isEmpty) tabs.open(blankTab())
                            tabs.active?.let { show(it) }
                        }
                        tabsTick.value++
                        persistTabs()
                    },
                )
                return
            }
            // A document whose unmodelled constructs are preserved opens for editing with one line to
            // read, not a question: it is fully editable everywhere the app can draw.
            notice?.let { toast(it.body) }
            applyLoadedDoc(loaded, source, isReadOnly = notice?.readOnly == true)
        }
    }
    // The document may reference recordings made elsewhere; fetch them so its strokes replay.
    pullAudioSidecars()
}

/**
 * Import a PDF as annotatable pages — one page per PDF page, rendered as backgrounds.
 *
 * [ImportPdfMode.REPLACE] makes the PDF the whole document; [ImportPdfMode.APPEND] keeps the open
 * document and adds the PDF's pages after it (one undoable edit).
 *
 * A `.xopp` can reference only **one** background PDF, so appending onto a document that already
 * has one goes through [appendMergedPdf]: the two PDFs are merged into a single joined PDF that
 * becomes the document's one background source.
 */
internal fun MainActivity.importPdf(uri: Uri, mode: ImportPdfMode = ImportPdfMode.REPLACE) {
    importPdf(uri, mode, indices = null)
}

/**
 * Read [uri] far enough to **ask** which of its pages to bring: stage the bytes (the PDF may live on a
 * network share) and count its pages, then raise the card. This is the window's order of questions —
 * pick the file, then say which pages and where — and nothing is imported until it is answered.
 */
internal fun MainActivity.beginPdfImport(uri: Uri) {
    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    val name = displayName(uri)
    inBackground("Reading $name…", { io.stageIn(uri, "import") }) { result ->
        result.mapCatching { staged ->
            val count = try {
                PdfPageCache(staged).use { it.pageCount }
            } catch (failure: Throwable) {
                staged.delete()
                throw failure
            }
            pendingPdfImport = PdfImportPrompt(
                file = staged,
                reference = uri.toString(),
                name = name,
                pageCount = count,
            )
        }.onFailure { toast("PDF import failed: ${it.message}") }
    }
}

/** Put the import card away, dropping the staged copy it was holding. */
internal fun MainActivity.cancelPdfImport() {
    pendingPdfImport?.file?.delete()
    pendingPdfImport = null
}

/**
 * Answer the import card with [mode], bringing the pages its range names.
 *
 * A range the card cannot use keeps it up rather than importing something the reader did not ask for:
 * the *Import* button is dead in that state, and this guard is the same rule behind it.
 */
internal fun MainActivity.confirmPdfImport(mode: ImportPdfMode) {
    val prompt = pendingPdfImport ?: return
    val range = prompt.range as? PageRangePlan.Pages ?: return
    pendingPdfImport = null
    val source = prompt.file
    try {
        adoptPdf(source, mode, prompt.reference, indices = range.first..range.last)
    } finally {
        source.delete()
    }
}

/**
 * Import a PDF, optionally only the pages [indices] names — the run's own position is not the page's
 * number, so `pageno` stays the source page's own (see [PdfImport.pagesFor]).
 */
internal fun MainActivity.importPdf(uri: Uri, mode: ImportPdfMode, indices: IntRange?) {
    // Persist read access so the same content URI still resolves when the saved .xopp is reopened
    // later (this is the reference plain Save records for domain="absolute"). Best-effort: import
    // still works if the grant isn't persistable.
    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    // The PDF may live on a network share: fetch it on a worker, then build pages from the copy.
    inBackground("Importing ${displayName(uri)}…", { io.stageIn(uri, "import") }) { result ->
        // adoptPdf copies the bytes into the PdfStore, so the staged copy is scratch either way.
        result.mapCatching { staged -> try { adoptPdf(staged, mode, uri.toString(), indices = indices) } finally { staged.delete() } }
            .onFailure { toast("PDF import failed: ${it.message}") }
    }
}

/**
 * Turn an already-local [source] PDF into annotatable pages, per [mode]. [inStore] says [source]
 * *is* already the store's own never-rewritten copy (a text import's generated PDF), so it is
 * used in place rather than duplicated — which also keeps its cache entry alive with the tab.
 */
internal fun MainActivity.adoptPdf(
    source: File,
    mode: ImportPdfMode,
    reference: String,
    inStore: Boolean = false,
    indices: IntRange? = null,
) {
    val file = if (inStore) source else io.adoptPdf(source)
    val view = surface
    val existing = view?.pdfSourceFile()
    if (mode == ImportPdfMode.APPEND && existing != null && view.hasPdfBackground()) {
        appendMergedPdf(view, existing, file, indices)
        return
    }
    val cache = PdfPageCache.shared(file)
    view?.setPdfSource(cache)
    view?.clearPdfAnalysis() // cleared until extraction below finishes
    when (mode) {
        // A replaced document keeps none of the old one's pixmap pictures.
        ImportPdfMode.REPLACE -> {
            view?.setImageSources(emptyMap())
            view?.load(PdfImport.documentFor(cache, reference, indices))
        }
        ImportPdfMode.APPEND -> view?.appendPages(PdfImport.pagesFor(cache, reference, indices = indices))
    }
    if (view == null) cache.close() // nobody took the claim; don't hold the renderer open
    // Freshly imported bytes: whatever was extracted for this path before doesn't describe them.
    PdfTextIndexCache.forget(file)
    analyzePdfInBackground(file)
}

/**
 * Turn an already-local [source] image into a one-page document whose background is the picture
 * ([ImageImport]). [reference] is what the background records as its `filename` — the source
 * `content://` URI, whose read grant the open path has already persisted, so a saved `.xopp`
 * still resolves the image when reopened.
 *
 * The document links the picture by location — a pixmap background is a reference, and the
 * picture stays where the user keeps it — but a copy is kept in the image store anyway, so the
 * page still renders once the staged file is swept and bundles into a ZIP save. Returns false
 * when the file doesn't decode, leaving the canvas untouched.
 */
internal fun MainActivity.adoptImage(source: File, reference: String): Boolean {
    val (widthPx, heightPx) = ImageImport.pixelSize(source) ?: return false
    surface?.setPdfSource(null)
    surface?.clearPdfAnalysis()
    // The document links the picture by its source URI, but the staged copy is swept and the
    // grant expires — so keep our own copy and render (and bundle) from that.
    surface?.setImageSources(mapOf(reference to io.adoptImage(source)))
    surface?.load(ImageImport.documentFor(widthPx, heightPx, reference))
    return true
}

/**
 * Append [incoming]'s pages to a document that already has a background PDF, by **merging** the
 * two into one joined PDF ([PdfMerger]) — the only shape a `.xopp` can represent, since it holds
 * a single background reference.
 *
 * The joined file lands in `filesDir` (not the cache) so the link a plain Save records survives;
 * successive appends ping-pong between two names so a merge never writes the file it's reading.
 * The document's one reference is re-pointed at the joined PDF and the new pages' `pageno` values
 * are renumbered against it, both in a single undoable edit.
 */
internal fun MainActivity.appendMergedPdf(
    view: DrawingSurfaceView,
    existing: File,
    incoming: File,
    indices: IntRange? = null,
) {
    val offset = view.pdfSourcePageCount()
    // Size the new pages from the incoming PDF before anything is closed or replaced. Only the picked
    // run is brought, and it lands where the merge puts it: after the base's own pages, so a page keeps
    // the number it has in the joined file.
    val added = PdfPageCache(incoming).use { cache ->
        val run = indices ?: 0 until cache.pageCount
        PdfImport.pagesFor(cache, reference = null, pageNoOffset = offset, indices = run.first..run.last)
    }
    val joined = io.merge(existing, incoming)
    PdfTextIndexCache.forget(joined) // the merge rewrote these bytes; any cached result is stale
    PdfOutlineCache.forget(joined)
    view.setPdfSource(PdfPageCache.shared(joined)) // releases the old rasteriser, freeing the old file
    view.clearPdfAnalysis() // cleared until extraction below finishes
    view.appendPdfPages(added, joined.absolutePath)
    analyzePdfInBackground(joined)
}

/**
 * Read a PDF's derived layers — its positioned text and its Contents outline — off the UI thread
 * (both walks are slow on a big file), then attach them to the canvas.
 *
 * Both are read in one thread on purpose: they load and parse the same bytes, and doing that twice
 * for one open buys nothing. Either may already be cached by the other pane showing the same file (a
 * mirrored document), so each half is skipped when it is; only what is missing is actually walked.
 */
internal fun MainActivity.analyzePdfInBackground(file: File, into: DrawingSurfaceView? = surface) {
    // Bind the destination canvas up front: with two panes open, the focus may well have moved by the
    // time a big PDF finishes extracting, and both results belong to the pane that asked.
    val view = into ?: return
    val cachedText = PdfTextIndexCache.get(file)
    val cachedOutline = PdfOutlineCache.get(file)
    if (cachedText != null && cachedOutline != null) {
        view.setPdfTextIndex(cachedText)
        view.setPdfOutline(cachedOutline)
        return
    }
    Thread {
        val text = cachedText ?: PdfTextExtractor().extract(file)
        val outline = cachedOutline ?: PdfOutlineExtractor().extract(file)
        PdfTextIndexCache.put(file, text)
        PdfOutlineCache.put(file, outline)
        view.post {
            if (view.pdfSourceFile()?.absoluteFile != file.absoluteFile) return@post
            view.setPdfTextIndex(text)
            view.setPdfOutline(outline)
        }
    }.start()
}

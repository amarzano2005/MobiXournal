package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Page

/**
 * The canvas's page source over a document it already holds in memory — a `.xopp`, or a new one.
 *
 * [pages] is read **fresh on every call**, not captured: a `PageBox` holds this source across an edit
 * that replaced the document, and still resolves the page the document now has. The pages it hands
 * back are the very objects the document holds, so a `.xopp` canvas draws exactly what it drew before.
 *
 * A document opened streaming hands the canvas a reader-backed source instead
 * (`carry-the-page-source-into-the-editor`).
 */
internal fun canvasPageSource(pages: () -> List<Page>): PageSource = object : PageSource {
    override val pageCount: Int get() = pages().size

    override fun pageId(index: Int): String? = pages().getOrNull(index)?.id

    override fun page(index: Int): Page {
        val list = pages()
        require(index in list.indices) { "page index $index out of bounds (0..${list.size - 1})" }
        return list[index]
    }
}

/**
 * The page at [index] through the canvas's [DrawingSurfaceView.pageSource], or null when the index is
 * out of range. Every page the canvas draws or hit-tests is read through here.
 */
internal fun DrawingSurfaceView.pageAt(index: Int): Page? =
    if (index in 0 until pageSource.pageCount) pageSource.page(index) else null

/**
 * Hand the canvas where a background's bytes are (`io/BundledPageAssets.kt`), for a document whose
 * pages are read on demand.
 *
 * A `.mobix` opened page by page adopts a page's picture or its PDF **when that page is read**, which
 * can be long after the load — so the resolver's adopted copies become the map the picture decoder
 * reads from (live, so a page drawn later finds the file its own read adopted), and a background PDF
 * no page has named yet is opened the first time one names it (`pdfSourceFor`).
 *
 * Passing null — a `.xopp`, or any document whose backgrounds were resolved at open — only drops the
 * resolver: the copies already handed over stay, so a caller that keeps using `setImageSources` is
 * unaffected.
 */
internal fun DrawingSurfaceView.setBackgroundResolver(resolver: BackgroundResolver?) {
    if (resolver === backgroundResolver) return
    backgroundResolver = resolver
    if (resolver != null) imageBackgrounds = resolver.images
    // The same reference may mean different bytes now, and a remembered decode failure would keep a
    // page that can be resolved from ever being drawn.
    imageSource.clear()
    requestRender()
}

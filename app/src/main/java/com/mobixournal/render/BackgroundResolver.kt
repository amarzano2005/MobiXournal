package com.mobixournal.render

import com.mobixournal.format.model.Background
import java.io.File

/**
 * A page background's bytes as a local file, resolved **when a page names it** rather than when the
 * document was opened.
 *
 * This is what lets a `.mobix` open page by page and still show its pictures and its PDF: a
 * background's bytes live in the archive under an asset name that only the page's own JSON carries
 * (`docs/mobix-format.md`), so adopting them for *every* page at open is exactly the walk the paged
 * open exists to avoid. A page read through the source adopts the backgrounds that page names
 * (`io/BundledPageAssets.kt`), the renderer asks for one when it has to draw a page without a local
 * copy, and the rest of the document's pages are never touched.
 *
 * Implemented by whoever owns the archive and the app's stores — the document I/O layer, which is
 * why the interface lives here: the renderer depends on the *question* ("what file is this
 * background?") and not on the answer.
 */
interface BackgroundResolver {

    /** The local copy of [background]'s bytes, adopting it on first use; null when unreachable. */
    fun file(background: Background): File?

    /**
     * The copies adopted so far, keyed by the reference the document names a picture under — the same
     * key [DrawingSurfaceView.openBackgroundImage] reads one by, and a **live** view: a page drawn
     * later finds the file its own read adopted.
     */
    val images: Map<String, File>

    /**
     * The backgrounds this resolver was asked for and could **not** produce, **drained**: each answer is
     * reported once and then forgotten, so a caller can say so to the user without repeating itself.
     *
     * It exists because asking is what happens *latest*: a page's bundled bytes are adopted when the
     * page is read, which can be a frame, a scroll or a session after the open. A document that resolves
     * its backgrounds at open reports them there instead (`LoadedFile.Doc.missingPdf`/`missingImage`), so
     * this is the lazy half of the same fact — and it is deliberately per **ask** rather than per page,
     * because asking is the only moment anything knows, and a page nobody has drawn is not yet missing
     * anything.
     */
    fun drainMissing(): List<String> = emptyList()
}

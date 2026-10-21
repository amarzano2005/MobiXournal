package com.mobixournal.format.model

/**
 * A page background. Mirrors `<background type=…>`; the three concrete types map to the
 * `solid` / `pixmap` / `pdf` values. Styles and domains are kept as raw strings so unknown
 * desktop values round-trip unchanged.
 *
 * [extraAttrs] carries the attributes we don't interpret so they survive a load/save round trip.
 */
sealed interface Background {

    /** Attributes on the `<background>` element we don't interpret, preserved for round-trip. */
    val extraAttrs: Map<String, String>

    /**
     * `type="solid"`: a ruled/graph/plain sheet in a single colour.
     *
     * @param color The background colour as an ARGB int.
     * @param style The ruling style (e.g., "plain", "ruled", "graph").
     * @param config Desktop Xournal++'s comma-separated ruling parameters (`r1` spacing, `m1` margin,
     *   `lw` line width, `bli`/`blw` bold lines) — see `BackgroundRuling`. Null when the page rules at
     *   desktop's defaults, which keeps the attribute off the file entirely.
     * @param extraAttrs Extra attributes preserved for round-trip.
     */
    data class Solid(
        val color: Int,
        val style: String,
        val config: String? = null,
        override val extraAttrs: Map<String, String> = emptyMap(),
    ) : Background

    /**
     * `type="pixmap"`: an image behind the page.
     *
     * @param domain The image domain (e.g., "absolute" for filesystem paths).
     * @param filename The path or URI to the image file.
     * @param extraAttrs Extra attributes preserved for round-trip.
     */
    data class Pixmap(
        val domain: String,
        val filename: String,
        override val extraAttrs: Map<String, String> = emptyMap(),
        /**
         * The `.mobix` entry the picture's bytes are bundled under, or null when the background is
         * an external reference like a gzip `.xopp`'s. See [Pdf.bundledAsset] for why the reference
         * and the bundle are both kept.
         */
        val bundledAsset: String? = null,
    ) : Background

    /**
     * `type="pdf"`: an annotated PDF page.
     *
     * @param filename The PDF file path or URI, or null if embedded in a ZIP package.
     * @param pageNo The 0-based page index within the PDF.
     * @param domain The domain for the filename (e.g., "attach" for ZIP-embedded PDFs).
     * @param extraAttrs Extra attributes preserved for round-trip.
     */
    data class Pdf(
        val filename: String?,
        val pageNo: Int,
        val domain: String?,
        override val extraAttrs: Map<String, String> = emptyMap(),
        /**
         * The `.mobix` entry this PDF's bytes are bundled under (`background/background.pdf`), or
         * null when it is an external reference.
         *
         * A native document that bundles its background keeps **both** facts on purpose: the bundle
         * is what makes the file self-contained when it is shared or synced, and [domain]/[filename]
         * are what the `.xopp` bridge needs to re-emit the reference desktop Xournal++ expects. A
         * `.xopp` export therefore keeps working whether or not the bytes could be carried along.
         */
        val bundledAsset: String? = null,
    ) : Background
}

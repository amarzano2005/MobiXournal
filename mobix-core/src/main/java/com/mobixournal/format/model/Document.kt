package com.mobixournal.format.model

/**
 * What this app writes into the root `<xournal>` element's `creator` attribute, and what it reports
 * for a file that carried none.
 *
 * Desktop Xournal++ writes `Xournal++ <version>`; this is the Android port, so it identifies itself
 * distinctly — the two never have to be told apart by guesswork when a document's provenance
 * matters.
 */
const val DEFAULT_CREATOR: String = "MobiXournal"

/**
 * The in-memory analogue of a `.xopp` file. See `docs/xopp-format.md` for the on-disk schema
 * this mirrors. All geometry is in points (pt, 1/72 inch); origin top-left. Colors are ARGB
 * ints (`0xAARRGGBB`) — the same layout Android's `Color` uses.
 */
data class Document(
    /** The `creator` attribute from the root `<xournal>` element. */
    val creator: String = DEFAULT_CREATOR,
    /** The `fileversion` attribute from the root `<xournal>` element (desktop Xournal++ uses "4"). */
    val fileVersion: String = "4",
    /** The pages in document order — z-order for layers within each page. */
    val pages: List<Page> = emptyList(),
    /** Base64 PNG thumbnail of page 1, verbatim, if the file carried one. */
    val preview: String? = null,
    /** The `<title>` text the file carried; null means the writer's default banner. */
    val title: String? = null,

    /**
     * The `.mobix` **bridge block** this file carried, as its JSON payload, or null when it carried
     * none.
     *
     * It rides in an application comment (`Xopp.BRIDGE_COMMENT`, written by `XoppWriter` and read back
     * by `Xopp.parseXml`) because a `.xopp` has no element that could hold living elements: the export
     * writes them flattened into ink, and this block is what lets the document come back as the
     * entities it was. It is **opaque here**: the `.xopp` layer carries it and nothing more, the way it
     * carries a `RawElement` it does not interpret — the `.mobix` layer owns its schema and its
     * verification rule (`docs/mobix-format.md`), and a document that has no block is not affected by
     * its existence in any way.
     */
    val bridge: String? = null,

    /**
     * The document's identity in the native `.mobix` format (`MobixManifest.documentId`), or null for
     * a document that has never been one.
     *
     * It lives here because it has to outlive the file: the manifest is where the identity is written,
     * and a document opened from a `.mobix` must carry its own id so that **saving it again is the same
     * document** rather than a new one — the difference between an edit and a duplicate to a merge or a
     * sync client ([mobix-format.md](mobix-format.md)). A `.xopp` has no such notion and never
     * writes one, so a document read from a `.xopp` is null here and gets an id the day it is first
     * saved as a `.mobix`.
     */
    val documentId: String? = null,
)

/**
 * A page in the document: dimensions in points, a background, and stacked layers.
 *
 * @param width Page width in points (1/72 inch).
 * @param height Page height in points (1/72 inch).
 * @param background The page's background (ruled sheet, pixmap image, or PDF).
 * @param layers The drawable layers in z-order (first = bottom, last = top).
 * @param extraAttrs Attributes on `<page>` we don't interpret, kept in source order so they round-trip.
 * @param id The page's stable identity; see [ElementId]. `.xopp` has no page ids and never sets one,
 *   so this is null for anything read from a `.xopp`; the `.mobix` manifest's `pages[]` entry carries
 *   it (never the page's own JSON), and it is what lets a page be *reordered* without becoming a
 *   different page to a merge.
 */
data class Page(
    val width: Double,
    val height: Double,
    val background: Background,
    val layers: List<Layer>,
    /** Attributes on `<page>` we don't interpret, kept in source order so they round-trip. */
    val extraAttrs: Map<String, String> = emptyMap(),
    /** The page's stable identity in the native `.mobix` format; see [ElementId.PAGE]. */
    val id: String? = null,
)

/**
 * A layer is an ordered list of drawables; document order is z-order and must be preserved.
 * [name] mirrors desktop Xournal++'s optional `<layer name="…">` attribute (null when the source
 * omitted it). Layer *visibility* is a view-only editor state and is deliberately not stored here,
 * since the `.xopp` format has no place for it (a hidden layer still round-trips with its content).
 *
 * @param elements The drawable elements in this layer (strokes, text, images).
 * @param name The optional layer name from the XML, or null if unnamed.
 * @param extraAttrs Attributes on `<layer>` we don't interpret, kept in source order so they round-trip.
 */
data class Layer(
    val elements: List<Element>,
    val name: String? = null,
    /** Attributes on `<layer>` we don't interpret, kept in source order so they round-trip. */
    val extraAttrs: Map<String, String> = emptyMap(),
)

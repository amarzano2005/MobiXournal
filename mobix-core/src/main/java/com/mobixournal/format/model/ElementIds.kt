package com.mobixournal.format.model

import java.util.UUID

/**
 * Element identity: the stable `id` every element carries in the native `.mobix` format
 * (`docs/mobix-schema-spec.md`).
 *
 * Identity is a *document* fact, not a format detail, which is why it lives on the model rather than
 * in the `.mobix` layer: it is what a conflict-free merge keys on (two devices editing one page union
 * their elements by id and resolve the ones that collide by timestamp — `docs/mobix-format.md`),
 * and what lets an edit change an element without the element becoming a different one.
 *
 * Two rules the minting below is built to satisfy:
 *
 *  - **Minted once, at creation, and never re-minted.** A move, a restyle or a copy's *duplicate* are
 *    edits; only a genuinely new element gets a new id. A reader that finds an element without one
 *    mints it (a `.xopp` file has no ids at all, so an import mints every element exactly once), and
 *    from then on the id is in the file and stays put.
 *  - **Not derivable from content.** Two identical strokes drawn twice are two elements, so a hash of
 *    the geometry would be exactly wrong: [mint] takes randomness, never a position or a digest.
 *
 * The prefix is cosmetic — it makes a log line or a JSON diff readable — and `stk_3f9c1a7b2e40` is
 * what an id looks like on disk.
 */
object ElementId {

    /** A pen/highlighter/eraser stroke's id prefix. */
    const val STROKE = "stk"

    /** A text box's id prefix. */
    const val TEXT = "txt"

    /** An image's id prefix. */
    const val IMAGE = "img"

    /** A rendered LaTeX formula's id prefix. */
    const val LATEX = "tex"

    /** A living table's id prefix. */
    const val TABLE = "tbl"

    /** A function plot's id prefix. */
    const val PLOT = "plt"

    /** A parametric shape's id prefix. */
    const val SHAPE = "shp"

    /** A circuit component's id prefix. */
    const val CIRCUIT = "ckt"

    /** A spatial chunk's id prefix. */
    const val CHUNK = "chk"

    /**
     * A **page**'s id prefix. A page is a document part rather than an element, but its id needs the
     * same two properties for the same reasons: stable across saves (a merge matches two copies of a
     * page by it, `docs/mobix-format.md`) and never derived from content. It is written in the
     * `.mobix` manifest's `pages[]` entry, not in the page's own JSON.
     */
    const val PAGE = "pg"

    /**
     * A fresh id for a new element of kind [prefix]: 48 bits of randomness, hex, prefixed so the kind
     * is readable from the id alone. 48 bits is deliberate — an id only has to be unique *within a
     * document*, and a short one keeps the JSON of a page with thousands of strokes small.
     */
    fun mint(prefix: String): String =
        prefix + "_" + UUID.randomUUID().toString().replace("-", "").take(12)

    /**
     * [element] with an id, minted once: an element that already carries one is returned **itself**, not
     * a copy of itself — same value either way, but the reference is how a caller tells an identified
     * element from one that needed minting ([withIds], [Document.withIdentity]) without comparing
     * elements by value, which for an [ImageElement] means comparing whole byte arrays.
     *
     * A [RawElement] is returned as it is: an element this build does not model has no id field of its
     * own, and its identity (if the build that wrote it had one) is inside the JSON we promised not to
     * touch.
     */
    fun withId(element: Element): Element = when (element) {
        is Stroke -> element.id?.let { element } ?: element.copy(id = mint(STROKE))
        is TextElement -> element.id?.let { element } ?: element.copy(id = mint(TEXT))
        is ImageElement -> element.id?.let { element } ?: element.copy(id = mint(IMAGE))
        is TexImageElement -> element.id?.let { element } ?: element.copy(id = mint(LATEX))
        is TableElement -> element.id?.let { element } ?: element.copy(id = mint(TABLE))
        is PlotElement -> element.id?.let { element } ?: element.copy(id = mint(PLOT))
        is ParametricShapeElement -> element.id?.let { element } ?: element.copy(id = mint(SHAPE))
        is CircuitElement -> element.id?.let { element } ?: element.copy(id = mint(CIRCUIT))
        is RawElement -> element
    }

    /**
     * The id [element] carries, or null when it has none — including for a [RawElement], which has no id
     * field of ours at all. What a caller asks when it needs to *check* identity rather than assign it
     * ([withId]), since [Element] itself declares none.
     */
    fun idOf(element: Element): String? = when (element) {
        is Stroke -> element.id
        is TextElement -> element.id
        is ImageElement -> element.id
        is TexImageElement -> element.id
        is TableElement -> element.id
        is PlotElement -> element.id
        is ParametricShapeElement -> element.id
        is CircuitElement -> element.id
        is RawElement -> null
    }

    /**
     * [page] with an id on every element that has none — what a `.xopp` import does, and the reason an
     * element's identity is **in the file** from the first write on: a reader that finds an element
     * without one mints one, so an import that did not write them would hand out different ids on
     * every open, and a merge would see the whole page as new.
     *
     * A page that needed nothing is returned as it is, so the caller can skip a rebuild rather than
     * hand a mirror an identical document to re-render.
     */
    fun withIds(page: Page): Page {
        var changed = false
        val layers = page.layers.map { layer ->
            var layerChanged = false
            val elements = layer.elements.map { element ->
                withId(element).also { if (it !== element) layerChanged = true }
            }
            if (layerChanged) {
                changed = true
                layer.copy(elements = elements)
            } else {
                layer
            }
        }
        return if (changed) page.copy(layers = layers) else page
    }
}

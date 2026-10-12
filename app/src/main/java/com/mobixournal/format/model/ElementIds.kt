package com.mobixournal.format.model

import java.util.UUID

/**
 * Element identity: the stable `id` every element carries in the native `.mobix` format
 * (`docs/mobix-schema-spec.md`).
 *
 * Identity is a *document* fact, not a format detail, which is why it lives on the model rather than
 * in the `.mobix` layer: it is what a conflict-free merge keys on (two devices editing one page union
 * their elements by id and resolve the ones that collide by timestamp — `docs/sync-and-storage.md`),
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

    /**
     * A fresh id for a new element of kind [prefix]: 48 bits of randomness, hex, prefixed so the kind
     * is readable from the id alone. 48 bits is deliberate — an id only has to be unique *within a
     * document*, and a short one keeps the JSON of a page with thousands of strokes small.
     */
    fun mint(prefix: String): String =
        prefix + "_" + UUID.randomUUID().toString().replace("-", "").take(12)
}

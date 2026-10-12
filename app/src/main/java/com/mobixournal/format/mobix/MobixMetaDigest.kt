package com.mobixournal.format.mobix

import com.mobixournal.format.Xopp
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page

/**
 * The digest a bridge claim carries: **the ink, in the `.xopp` writer's own spelling**
 * (`docs/mobix-format.md`).
 *
 * It exists so the block's claim that "these elements are that table" can be *checked* rather than
 * believed. A file that came back from desktop Xournal++ — saved, or edited, or merely re-written —
 * may hold different ink at the position a claim names, and the claim must then be discarded instead of
 * reinterpreting someone's drawing as a table they never edited.
 *
 * Two decisions make the digest comparable at all, and neither is an accident:
 *
 *  - **It is the format's own serialization, not a hash of our object graph.** The exporting side has
 *    freshly computed `Stroke`s and the importing side has ones parsed back out of the file, and those
 *    two are not bit-identical model values (a coordinate survives its written precision, not the
 *    `Double` it started as). Writing both through one writer is what gives them a common form — a
 *    second, "canonical" spelling of a stroke would be a second thing to keep in step with `.xopp`.
 *  - **It is computed from the written form's fixed point.** The text is written, read back and written
 *    again, because *that* is the form both sides can agree on: the export's ink, once written, is what
 *    the import reads, and the writer is idempotent over the pair (the property `XmlEqualityRoundTripTest`
 *    asserts). Skipping it would make the digest depend on whether the value had been through a file
 *    yet, which is exactly the difference between the two callers.
 *
 * The wrapper document around the elements is fixed by this file (creator, page size, background), so
 * the digest is a function of the elements alone. Like every other spelling here it is a *schema*
 * statement: if how the writer spells a stroke ever changes, older blocks stop verifying — and they are
 * discarded, which is the safe direction.
 */
internal object MobixMetaDigest {

    /** The wrapper's creator, so the digest cannot drift with the app's own version. */
    private const val WRAPPER_CREATOR = "mobix-digest"

    /** The lowercase hex SHA-256 of [elements] as this app's `.xopp` writer spells them. */
    fun of(elements: List<Element>): String =
        MobixAssetSink.sha256(agreedForm(elements).toByteArray(Charsets.UTF_8))

    /** The written form both the exporter and the importer end up holding. */
    private fun agreedForm(elements: List<Element>): String {
        val written = Xopp.toXml(wrapper(elements))
        return Xopp.toXml(Xopp.parseXml(written))
    }

    /** [elements] held by a minimal document — one page, one layer, nothing else to vary. */
    private fun wrapper(elements: List<Element>): Document = Document(
        creator = WRAPPER_CREATOR,
        pages = listOf(
            Page(
                width = 0.0,
                height = 0.0,
                background = Background.Solid(0, "plain"),
                layers = listOf(Layer(elements)),
            ),
        ),
    )
}

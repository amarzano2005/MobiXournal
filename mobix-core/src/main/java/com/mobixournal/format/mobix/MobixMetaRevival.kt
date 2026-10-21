package com.mobixournal.format.mobix

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page

/**
 * The import half of the bridge block: the living entities a `.xopp` claims to have been flattened
 * from, put back **only where the ink still proves it** (`docs/mobix-format.md`).
 *
 * The block is a claim, not truth, because a `.xopp` is a file other programs write: desktop
 * Xournal++ drops the comment on its own save, or keeps it while the drawing beside it changes. So
 * every entry is checked against the document in front of us —
 *
 *  - the page and layer it names must exist, and the index range must fit inside the layer;
 *  - the ink in that range must hash to the digest the claim records ([MobixMetaDigest]);
 *  - overlapping claims are refused, first one wins: two entities cannot both have been written into
 *    one stretch of ink, so at most the first is telling the truth and the rest would double-draw.
 *
 * and anything that fails those checks is dropped while the ink it named stays exactly where it is, as
 * the ordinary strokes it is. That is the whole rule: **the drawing the user can see is never
 * reinterpreted**, so a document that came back edited opens as the edit, not as a table with the edit
 * swallowed by it.
 *
 * A document with no block, or with one this build cannot honour, comes back untouched — which is the
 * same answer as a `.mobix` that never went through an export at all.
 */
internal object MobixMetaRevival {

    /**
     * [document] with every claim that still verifies replaced by the entity it names.
     *
     * Elements that are revived keep the identity the block recorded (that is what makes a merge see an
     * unchanged entity rather than a new one); the ink that stays keeps whatever the caller assigns it
     * afterwards, exactly as a `.xopp` import does.
     */
    fun apply(document: Document): Document {
        val claims = document.bridge?.let(MobixMetaBlock::decode)?.claims.orEmpty()
        if (claims.isEmpty()) return document
        return document.copy(
            pages = document.pages.mapIndexed { index, page ->
                revive(page, claims.filter { it.page == index })
            },
        )
    }

    private fun revive(page: Page, claims: List<MobixMetaClaim>): Page {
        if (claims.isEmpty()) return page
        return page.copy(
            layers = page.layers.mapIndexed { index, layer ->
                revive(layer, claims.filter { it.layer == index })
            },
        )
    }

    private fun revive(layer: Layer, claims: List<MobixMetaClaim>): Layer {
        val usable = claims
            .filter { it.at >= 0 && it.count > 0 && it.at + it.count <= layer.elements.size }
            .sortedBy { it.at }
        if (usable.isEmpty()) return layer

        val out = mutableListOf<Element>()
        var cursor = 0
        for (claim in usable) {
            if (claim.at < cursor) continue // overlaps ink an earlier claim already took
            val ink = layer.elements.subList(claim.at, claim.at + claim.count)
            if (MobixMetaDigest.of(ink) != claim.sha256) continue
            out += layer.elements.subList(cursor, claim.at)
            out += claim.element
            cursor = claim.at + claim.count
        }
        if (cursor == 0) return layer
        out += layer.elements.subList(cursor, layer.elements.size)
        return layer.copy(elements = out)
    }
}

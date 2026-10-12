package com.mobixournal.format.model

import java.util.UUID

/**
 * The identity a `.mobix` document carries, minted into the **model** rather than into the file.
 *
 * Every page and every element of a `.mobix` has an id, and the document has one of its own in the
 * manifest (`docs/mobix-schema-spec.md`). Those ids are what a conflict-free merge keys on — two
 * devices editing one page union their elements by id and resolve the ones that collide by timestamp
 * (`docs/sync-and-storage.md`) — so a document saved twice must hand out the *same* ids the second
 * time. Minting them inside the writer does not achieve that: the writer mints on a copy, the open
 * document never learns the ids, and the next save mints a fresh set, which a merge (or a sync client)
 * reads as the whole page replaced. [Document.documentId] makes the same argument for the document
 * itself.
 *
 * So a save **adopts** the identity before it encodes: the identified document is what gets written,
 * and every later edit inherits the ids, because an edit copies the elements it changes.
 *
 * An already identified document is returned **itself**: asking twice costs nothing, cannot mint a
 * second id, and tells the caller there is nothing to adopt — which is what lets it skip a redraw and
 * a notification to a mirrored pane.
 */
fun Document.withIdentity(): Document {
    var changed = documentId == null
    val pages = pages.map { page ->
        val identified = ElementId.withIds(page)
        val withPageId =
            if (identified.id == null) identified.copy(id = ElementId.mint(ElementId.PAGE)) else identified
        if (withPageId !== page) changed = true
        withPageId
    }
    return if (changed) copy(documentId = documentId ?: mintDocumentId(), pages = pages) else this
}

/**
 * A fresh document id.
 *
 * A UUID, unlike the short prefixed id an element gets ([ElementId.mint]): an element id only has to
 * be unique *within* a document, while a document id is the one identity that can meet an id minted by
 * another copy of this app on another device — and a merge that mistook two documents for one would be
 * far worse than a longer string in a manifest.
 */
private fun mintDocumentId(): String = UUID.randomUUID().toString()

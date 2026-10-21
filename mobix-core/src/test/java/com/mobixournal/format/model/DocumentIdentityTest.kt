package com.mobixournal.format.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The identity a save **adopts** before a `.mobix` is written, rather than one the writer mints into a
 * copy the open document never sees (`docs/mobix-schema-spec.md`, and the merge it exists for:
 * `docs/sync-and-storage.md`).
 *
 * The property that matters is not that ids exist but that the *same* ids come back. A document whose
 * elements are minted twice is, to a merge or a sync client, a whole page replaced instead of an edit —
 * which is what this app did while the writer owned the minting.
 */
class DocumentIdentityTest {

    private fun stroke(id: String? = null) = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(10.0, 10.0, 2.0), StrokePoint(20.0, 20.0, 2.0)),
        uniformWidth = false,
        id = id,
    )

    private fun page(elements: List<Element>, id: String? = null) = Page(
        width = 200.0,
        height = 400.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements)),
        id = id,
    )

    private fun document(vararg elements: Element, documentId: String? = null): Document =
        Document(pages = listOf(page(elements.toList())), documentId = documentId)

    @Test
    fun aDocumentWithNoIdentityGetsOneOnItselfItsPagesAndItsElements() {
        val identified = document(stroke(), stroke()).withIdentity()

        assertNotNull("the document itself is identified", identified.documentId)
        assertNotNull("so is its page", identified.pages[0].id)
        assertTrue(
            "and every element, which is what the file's page JSON carries",
            identified.pages[0].layers[0].elements.all { ElementId.idOf(it) != null },
        )
    }

    @Test
    fun adoptingTwiceHandsBackTheSameDocument() {
        val once = document(stroke()).withIdentity()

        // The *same instance*, not merely an equal one: a save that finds nothing to adopt must not hand
        // a mirrored pane a document to re-render, and must not be able to mint a second identity.
        assertSame("nothing left to adopt the second time", once, once.withIdentity())
    }

    @Test
    fun whatIsAlreadyIdentifiedIsKeptAndOnlyWhatIsNotIsMinted() {
        val identified = document(stroke("stk_known")).withIdentity()
        assertEquals("a known id survives", "stk_known", ElementId.idOf(identified.pages[0].layers[0].elements[0]))

        val mixed = document(stroke("stk_known"), stroke()).withIdentity()
        val elements = mixed.pages[0].layers[0].elements
        assertEquals("and is not replaced beside one that has to be minted", "stk_known", ElementId.idOf(elements[0]))
        assertNotNull("while the element without one gets one", ElementId.idOf(elements[1]))
    }

    @Test
    fun aDocumentThatReadItsOwnIdKeepsIt() {
        val read = "6195705e-0540-4b49-99bc-a4a65f839e4a"

        assertEquals("a .mobix re-saved is the same document", read, document(stroke(), documentId = read).withIdentity().documentId)
    }

    @Test
    fun twoDocumentsThatWereNeverIdentifiedAreTwoDocuments() {
        val first = document(stroke()).withIdentity()
        val second = document(stroke()).withIdentity()

        assertTrue("a fresh id each, or a merge would weld them together", first.documentId != second.documentId)
    }

    @Test
    fun aPageThatNeededNothingIsHandedBackUntouched() {
        val page = document(stroke()).withIdentity().pages[0]

        assertSame("an identified page is not rebuilt", page, ElementId.withIds(page))
    }

    @Test
    fun anElementThisBuildDoesNotModelIsLeftAlone() {
        val raw = RawElement(name = "image", attrs = mapOf("id" to "img_from_a_newer_build"))
        val identified = document(raw).withIdentity()

        // A preserved element has no id field of ours: whatever identity it carries is inside the markup
        // we promised not to touch, so it is returned as it is rather than given a second one.
        assertSame("preserved markup is never re-identified", raw, identified.pages[0].layers[0].elements[0])
    }
}

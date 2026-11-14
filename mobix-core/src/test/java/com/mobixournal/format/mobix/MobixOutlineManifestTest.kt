package com.mobixournal.format.mobix

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.withIdentity
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Contents tree where it is stored: `manifest.json`'s `outline`, and the round trip through the
 * document layer that an open-and-save is.
 *
 * Two facts decide whether the key is safe to add. **Absence means none**, so every file written before
 * the key existed — and every `.xopp`, which has nowhere to put it — opens exactly as it did, and a
 * document with no tree does not grow an empty one on save. And **the tree is the manifest's**, so it
 * has to reach the model the shells edit and come back out of a save unchanged: a tree that survived the
 * codec but not the document layer would be a bookmark nobody could keep.
 */
class MobixOutlineManifestTest {

    /** A fixed clock: every timestamp here is a number the test chose, not one the machine picked. */
    private val now = 1_775_952_000_000L

    private fun manifestJson(outline: OutlineTree): String =
        MobixManifest.forNewDocument("0f6a3f2e-6b0c-4f43-9c1a-2d7f3f7c1a10", CanvasMode.PAGINATED, now)
            .copy(outline = outline)
            .encode()

    private fun decode(text: String): MobixManifest = MobixManifest.decode(text)

    private fun tree() = OutlineTree(
        nodes = listOf(
            OutlineNode(
                "bkm_1",
                "Geometry",
                OutlineTarget.Sheet(2),
                children = listOf(OutlineNode("bkm_2", "Angles", OutlineTarget.Page("pg_1"))),
            ),
        ),
    )

    @Test
    fun `a manifest with no outline opens with none`() {
        val manifest = decode(manifestJson(OutlineTree.EMPTY))

        assertTrue(manifest.outline.isEmpty)
    }

    @Test
    fun `an empty outline is written as no key at all`() {
        val text = manifestJson(OutlineTree.EMPTY)

        assertFalse(
            "\"no index\" and \"an empty one\" are one fact, and a file should say it once: $text",
            text.contains("\"outline\""),
        )
    }

    @Test
    fun `an outline round-trips through the manifest`() {
        val text = manifestJson(tree())

        val manifest = decode(text)

        assertEquals(tree(), manifest.outline)
        assertEquals("and writing the manifest again produces the same key", text, manifest.encode())
    }

    @Test
    fun `an outline that is not an object fails the open`() {
        val text = manifestJson(tree()).replace("\"outline\":{", "\"outline\":[{\"id\":\"bkm_1\"},")

        val message = try {
            decode(text)
            error("a list is not a tree")
        } catch (refused: MobixContainer.MalformedMobixException) {
            refused.message.orEmpty()
        }

        assertTrue("the refusal names the field: $message", message.contains("outline"))
    }

    @Test
    fun `a tree from a newer version fails the open`() {
        val text = manifestJson(tree()).replace("\"outline\":{\"version\":1", "\"outline\":{\"version\":2")

        try {
            decode(text)
            error("a newer node schema is not ours to guess")
        } catch (refused: MobixContainer.MalformedMobixException) {
            assertTrue(
                "the open fails on the version rather than half-reading the tree: ${refused.message}",
                refused.message.orEmpty().contains("version 2"),
            )
        }
    }

    @Test
    fun `an open document carries the tree and a save writes it back`() {
        val document = MobixDocument.open(archive(tree())).toDocument()

        assertEquals("the model the shells edit has it", tree(), document.outline)

        val saved = MobixDocument.open(
            MobixContainer.open(
                ByteArrayInputStream(
                    MobixDocument.of(document, appVersion = "1.3.0", nowMillis = now).toBytes(now),
                ),
            ),
        ).toDocument()

        assertEquals("and it comes out of a save as it went in", tree(), saved.outline)
    }

    /** A one-page `.mobix` carrying [outline], built by the writer the app itself saves with. */
    private fun archive(outline: OutlineTree): MobixContainer.Archive {
        val page = Page(
            width = 595.0,
            height = 842.0,
            background = Background.Solid(0xFFFFFFFF.toInt(), style = "plain"),
            layers = listOf(Layer(emptyList())),
        )
        val model = Document(pages = listOf(page), outline = outline).withIdentity()
        return MobixContainer.open(
            ByteArrayInputStream(MobixDocument.of(model, appVersion = "1.3.0", nowMillis = now).toBytes(now)),
        )
    }
}

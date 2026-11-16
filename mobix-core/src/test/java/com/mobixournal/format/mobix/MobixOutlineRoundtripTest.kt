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
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Contents tree through the two saves a shell actually performs (`docs/pdf.md`).
 *
 * A full save is `MobixDocument.of` writing the manifest whole; an incremental save is the same writer
 * with `MobixIncrementalSave.saveOver`, which streams every page it did not touch and rewrites only the
 * manifest — so the tree has exactly one place to survive, and a change to it must reach the file even
 * when no page moved.
 */
class MobixOutlineRoundtripTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A fixed clock: every timestamp here is a number the test chose, not one the machine picked. */
    private val now = 1_775_952_000_000L

    private fun page() = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(Layer(emptyList())),
    )

    private fun tree(chapter: String = "Chapter") = OutlineTree(
        nodes = listOf(
            OutlineNode(
                "bkm_1",
                chapter,
                OutlineTarget.Sheet(1),
                children = listOf(OutlineNode("bkm_2", "Section", OutlineTarget.Page("pg_1"))),
            ),
        ),
    )

    /** A one-page notebook whose Contents is [outline], with the identity a save needs. */
    private fun document(outline: OutlineTree) = Document(pages = listOf(page()), outline = outline).withIdentity()

    private fun reopen(bytes: ByteArray): Document =
        MobixDocument.open(MobixContainer.open(ByteArrayInputStream(bytes))).toDocument()

    @Test
    fun `a full save and reopen keeps the tree`() {
        val saved = MobixDocument.of(document(tree()), appVersion = "1.3.0", nowMillis = now).toBytes(now)

        assertEquals(tree(), reopen(saved).outline)
    }

    @Test
    fun `an incremental save writes an edited tree even when no page moved`() {
        val file = File(temp.root, "outline-roundtrip.mobix")
        val model = document(tree())
        file.outputStream().use { MobixDocument.of(model, appVersion = "1.3.0", nowMillis = now).save(it, now) }

        val edited = model.copy(outline = tree(chapter = "Renamed on this device"))
        val out = ByteArrayOutputStream()
        MobixEntrySource.fromFile(file).use { source ->
            MobixIncrementalSave.saveOver(
                source = source,
                document = edited,
                appVersion = "1.3.0",
                nowMillis = now + 1_000,
                output = out,
            )
        }

        val reopened = reopen(out.toByteArray())
        assertEquals("the rename reached the file", "Renamed on this device", reopened.outline.nodes.single().title)
        assertEquals("and so did the row's target", OutlineTarget.Sheet(1), reopened.outline.nodes.single().target)
    }

    @Test
    fun `an incremental save that changes only pages leaves the tree as it was`() {
        val file = File(temp.root, "outline-kept.mobix")
        val model = document(tree())
        file.outputStream().use { MobixDocument.of(model, appVersion = "1.3.0", nowMillis = now).save(it, now) }

        val out = ByteArrayOutputStream()
        MobixEntrySource.fromFile(file).use { source ->
            MobixIncrementalSave.saveOver(source, model, appVersion = "1.3.0", nowMillis = now + 1_000, output = out)
        }

        assertEquals(tree(), reopen(out.toByteArray()).outline)
    }

    @Test
    fun `a reopened tree can be edited again and saved back`() {
        val first = MobixDocument.of(document(tree()), appVersion = "1.3.0", nowMillis = now).toBytes(now)
        val opened = reopen(first)
        val second = MobixDocument.of(
            opened.copy(
                outline = opened.outline.copy(
                    nodes = opened.outline.nodes.map { it.copy(title = "Second pass") },
                ),
            ),
            appVersion = "1.3.0",
            nowMillis = now + 1_000,
        ).toBytes(now + 1_000)

        assertEquals("Second pass", reopen(second).outline.nodes.single().title)
    }
}

package com.mobixournal.format.mobix

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The digest rules (`docs/mobix-format.md`, *What a reader must refuse*): a part's bytes must hash to
 * what the manifest recorded, and an asset's bytes must hash to the name they are filed under.
 *
 * Both used to be written and never checked, so a page edited or damaged after the save decoded as
 * whatever it happened to contain — the failure mode a checksum exists to catch, and one that is silent
 * without this test.
 */
class MobixDigestVerificationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pageEntry = MobixFormat.pageEntry(1)

    private fun textPage(): String =
        """{"widthPt":595,"heightPt":842,"layers":[{"elements":[{"type":"text","content":"hi","x":1,"y":2}]}]}"""

    private fun picturePage(asset: String): String =
        """{"widthPt":595,"heightPt":842,"layers":[{"elements":[{"type":"image","asset":"$asset",""" +
            """"bounds":{"left":0,"top":0,"right":1,"bottom":1}}]}]}"""

    private fun write(
        file: File,
        pageJson: String,
        recordedDigest: String,
        extras: List<MobixContainer.Entry> = emptyList(),
    ) {
        val pageBytes = pageJson.toByteArray(Charsets.UTF_8)
        val manifest = MobixManifest.forNewDocument(
            documentId = "doc_digest",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = 1700000000000L,
            title = "Digests",
            defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            appVersion = "1.0.0",
        ).copy(pages = listOf(MobixPart(id = "pg_001", entry = pageEntry, sha256 = recordedDigest)))
            .encode().toByteArray(Charsets.UTF_8)
        val canvas = MobixCanvasFile(CanvasMode.PAGINATED, 595.0, 842.0).encode().toByteArray(Charsets.UTF_8)
        val entries = listOf(
            MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest),
            MobixContainer.Entry(MobixFormat.CANVAS_ENTRY, canvas),
            MobixContainer.Entry(pageEntry, pageBytes),
        ) + extras
        FileOutputStream(file).use { MobixContainer.save(entries, it) }
    }

    @Test
    fun aPageWhoseRecordedDigestIsWrongIsRefusedOnMaterialisation() {
        val file = temp.newFile("wrong-digest.mobix")
        write(file, textPage(), recordedDigest = "0".repeat(64))

        MobixPagedReader(MobixEntrySource.fromFile(file)).use { reader ->
            val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) { reader.getPage(0) }
            assertTrue("the refusal names the entry: ${failure.message}", pageEntry in failure.message.orEmpty())
            assertTrue("and says what was recorded: ${failure.message}", "recorded" in failure.message.orEmpty())
            assertTrue("and what was found: ${failure.message}", "found" in failure.message.orEmpty())
        }

        // The eager read asks the same question: one rule, both paths.
        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            file.inputStream().use { MobixDocument.open(MobixContainer.open(it)).toDocument() }
        }
        assertTrue(failure.message.orEmpty(), pageEntry in failure.message.orEmpty())
    }

    @Test
    fun aCleanDocumentStillOpensAndStreams() {
        val file = temp.newFile("clean.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(3))

        MobixPagedReader(MobixEntrySource.fromFile(file)).use { reader ->
            assertEquals(3, reader.pageCount)
            assertEquals("pg_003", reader.getPage(2).id)
        }
        file.inputStream().use { stream ->
            val document: Document = MobixDocument.open(MobixContainer.open(stream)).toDocument()
            assertEquals(3, document.pages.size)
        }
    }

    @Test
    fun anAssetWhoseNameIsNotItsDigestIsRefused() {
        val claimed = "a".repeat(64)
        val asset = "img_$claimed.webp"
        val file = temp.newFile("wrong-asset.mobix")
        write(
            file,
            picturePage(asset),
            recordedDigest = "",
            extras = listOf(MobixContainer.Entry("${MobixFormat.ASSETS_DIR}$asset", "not that picture".toByteArray())),
        )

        MobixPagedReader(MobixEntrySource.fromFile(file)).use { reader ->
            val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) { reader.getPage(0) }
            assertTrue(
                "the refusal names the asset: ${failure.message}",
                asset in failure.message.orEmpty(),
            )
        }
    }
}

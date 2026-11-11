package com.mobixournal.portability

import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.MobixPayload
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.mobix.TrackedEntrySource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The portability audit (`PortabilityAudit`, `docs/mobix-assets.md` → *Is it inside the file?*): every
 * reference a document makes, classified as inside, absent or unrecoverable — and the proof that the
 * classification costs a page read and nothing else.
 *
 * Every fixture is written through the real container, so a reader sees an ordinary archive and a
 * passing claim is about the format rather than about a stub.
 */
class PortabilityAuditTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** Bytes that do not compress — a picture or a PDF rather than a run of one byte. */
    private fun payload(size: Int): ByteArray =
        ByteArray(size).also { java.util.Random(size.toLong()).nextBytes(it) }

    private fun fileOf(name: String, pages: List<Page>, pdf: ByteArray? = null, audio: (String) -> MobixPayload? = { null }): File {
        val file = File(temp.root, name)
        SyntheticNotebook.write(
            file,
            pages,
            backgrounds = { background -> pdf?.takeIf { background is Background.Pdf }?.let(MobixPayload::of) },
            audio = audio,
        )
        return file
    }

    private fun page(index: Int, background: Background = Background.Solid(0xFFFFFFFF.toInt(), "plain"), elements: List<com.mobixournal.format.model.Element> = emptyList()): Page =
        Page(
            width = SyntheticNotebook.WIDTH_PT,
            height = SyntheticNotebook.HEIGHT_PT,
            background = background,
            layers = listOf(Layer(elements)),
            id = SyntheticNotebook.idOf(index),
        )

    @Test
    fun aDocumentWhosePayloadsAreBundledReadsAsFullyPortable() {
        val picture = ImageElement(10.0, 10.0, 110.0, 110.0, payload(4096))
        val pages = listOf(
            page(0, elements = listOf(picture)),
            page(1, background = Background.Pdf(filename = "notes.pdf", pageNo = 0, domain = "absolute")),
        )
        val file = fileOf("portable.mobix", pages, pdf = payload(8192))

        MobixPagedReader.open(file).use { reader ->
            val report = PortabilityAudit.audit(reader)

            assertTrue("every reference is inside the file: ${report.missing}", report.isPortable)
            assertEquals("the document's own id", reader.manifest.documentId, report.documentId)
            assertEquals("the picture and the page's paper", 2, report.entries.size)
            assertEquals(
                "image, pdf",
                setOf(PortabilityKind.IMAGE, PortabilityKind.PDF),
                report.entries.map { it.kind }.toSet(),
            )
            assertEquals(listOf(0, 1), report.entries.map { it.pageIndex })
            assertTrue(
                "a bundled entry is reported by the name its bytes live under",
                report.entries.first().reference.startsWith("${MobixFormat.ASSETS_DIR}img_"),
            )
            assertEquals(0, report.counts[PortabilityStatus.REFERENCED_ABSENT])
            assertEquals(0, report.counts[PortabilityStatus.UNRECOVERABLE])
        }
    }

    @Test
    fun backgroundsThatAreOnlyFileNamesAreReferencesOutsideTheArchive() {
        val pages = listOf(
            page(0, background = Background.Pdf(filename = "notes.pdf", pageNo = 3, domain = "absolute")),
            page(1, background = Background.Pixmap(domain = "absolute", filename = "scan.png")),
        )
        val file = fileOf("references.mobix", pages)

        MobixPagedReader.open(file).use { reader ->
            val report = PortabilityAudit.audit(reader)

            assertFalse("a reference with no bytes is not portability", report.isPortable)
            assertEquals(2, report.counts[PortabilityStatus.REFERENCED_ABSENT])
            assertEquals(
                listOf("notes.pdf", "scan.png"),
                report.entries.map { it.reference },
            )
            assertEquals(
                listOf(PortabilityKind.PDF, PortabilityKind.PIXMAP),
                report.entries.map { it.kind },
            )
            assertTrue(
                "neither is a corrupt document: the format allows a background to stay a reference",
                report.entries.none { it.status == PortabilityStatus.UNRECOVERABLE },
            )
        }
    }

    @Test
    fun aStrokeNamingAnAbsentSidecarIsAReferenceAndNotAUnrecoverableDocument() {
        val stroke = SyntheticNotebook.stroke(10.0, id = "stk_audio").copy(
            extraAttrs = mapOf("fn" to "2026-08-02T09-14-33.wav", "ts" to "1200"),
        )
        val file = fileOf("sidecar.mobix", listOf(page(0, elements = listOf(stroke))))

        MobixPagedReader.open(file).use { reader ->
            val report = PortabilityAudit.audit(reader)
            val entry = report.entries.single()

            assertEquals(PortabilityStatus.REFERENCED_ABSENT, entry.status)
            assertEquals(PortabilityKind.AUDIO, entry.kind)
            assertEquals("2026-08-02T09-14-33.wav", entry.reference)
            assertEquals("the element that made the reference", "stk_audio", entry.elementId)
        }
    }

    @Test
    fun aBundledRecordingIsInsideTheFileEvenThoughItsSidecarNameStays() {
        val stroke = SyntheticNotebook.stroke(10.0, id = "stk_audio").copy(
            extraAttrs = mapOf("fn" to "take.wav", "ts" to "0"),
        )
        val recording = MobixPayload.of("RIFF0000WAVE".toByteArray())
        val file = fileOf("bundled-audio.mobix", listOf(page(0, elements = listOf(stroke))), audio = { recording })

        MobixPagedReader.open(file).use { reader ->
            val report = PortabilityAudit.audit(reader)

            assertEquals(PortabilityStatus.EMBEDDED, report.entries.single().status)
            assertTrue(
                "the recorded entry, not the sidecar name the bridge keeps",
                report.entries.single().reference.contains(MobixFormat.AssetKind.AUDIO.prefix),
            )
        }
    }

    @Test
    fun anAssetNameWithNoEntryIsACorruptDocument() {
        val missing = MobixFormat.assetEntry("a".repeat(64), MobixFormat.AssetKind.IMAGE, "pdf")
        val pages = listOf(
            page(0, background = Background.Pdf(filename = "notes.pdf", pageNo = 0, domain = "absolute", bundledAsset = missing)),
        )
        val file = fileOf("corrupt.mobix", pages)

        MobixPagedReader.open(file).use { reader ->
            val report = PortabilityAudit.audit(reader)
            val entry = report.entries.single()

            assertEquals(PortabilityStatus.UNRECOVERABLE, entry.status)
            assertEquals(
                "the entry name is reported as the document wrote it, never searched for another container",
                missing,
                entry.reference,
            )
            assertEquals(1, report.counts[PortabilityStatus.UNRECOVERABLE])
        }
    }

    @Test
    fun aReferenceLeavingTheDocumentsFolderIsUnrecoverable() {
        val pages = listOf(
            page(0, background = Background.Pixmap(domain = "absolute", filename = "../../etc/paper.png")),
            page(1, background = Background.Pixmap(domain = "absolute", filename = "/home/me/paper.png")),
            page(2, background = Background.Pixmap(domain = "absolute", filename = "C:\\scans\\paper.png")),
            page(3, background = Background.Pixmap(domain = "attach", filename = "paper.png")),
        )
        val file = fileOf("outside.mobix", pages)

        MobixPagedReader.open(file).use { reader ->
            val report = PortabilityAudit.audit(reader)

            assertEquals(
                "a traversal, an absolute path and a Windows path are not this document's bytes; a plain " +
                    "name beside the document is",
                listOf(
                    PortabilityStatus.UNRECOVERABLE,
                    PortabilityStatus.UNRECOVERABLE,
                    PortabilityStatus.UNRECOVERABLE,
                    PortabilityStatus.REFERENCED_ABSENT,
                ),
                report.entries.map { it.status },
            )
        }
    }

    @Test
    fun theSamePictureOnTwoPagesSharesOneDedupeKey() {
        val picture = ImageElement(10.0, 10.0, 110.0, 110.0, payload(2048))
        val pages = listOf(page(0, elements = listOf(picture)), page(1, elements = listOf(picture)))
        val file = fileOf("dedupe.mobix", pages)

        MobixPagedReader.open(file).use { reader ->
            val report = PortabilityAudit.audit(reader)
            val grouped = report.byReference()

            assertEquals("two references, both written twice", 2, report.entries.size)
            assertEquals("one key for the picture", 1, grouped.keys.count { it.startsWith("image:") })
            assertEquals(
                "the key names the kind and the reference, so a caller groups without parsing",
                "image:" + report.entries.first().reference,
                report.entries.first().dedupeKey,
            )
        }
    }

    @Test
    fun aTwoHundredPageNotebookIsReadOncePerPageAndOpensNoPayload() {
        val paper = page(0, background = Background.Pdf(filename = "notes.pdf", pageNo = 0, domain = "absolute"))
        val pages = listOf(paper) + SyntheticNotebook.pages(200).drop(1)
        val file = fileOf("two-hundred.mobix", pages, pdf = payload(16 * 1024))

        MobixEntrySource.fromFile(file).use { source ->
            val tracked = TrackedEntrySource(source)
            MobixPagedReader(tracked).use { reader ->
                val report = PortabilityAudit.audit(reader)

                assertEquals("one reference for the bundled paper", 1, report.entries.size)
                assertEquals(PortabilityStatus.EMBEDDED, report.entries.single().status)
                assertEquals(200, reader.pageCount)

                for (index in 0 until reader.pageCount) {
                    assertEquals(
                        "page ${index + 1} is materialised exactly once by the walk",
                        1,
                        tracked.openCount(SyntheticNotebook.entryOf(index)),
                    )
                }
                val assets = tracked.entryNames.filter { it.startsWith(MobixFormat.ASSETS_DIR) }
                assertTrue("the document does carry a bundled payload", assets.isNotEmpty())
                for (name in assets) {
                    assertEquals(
                        "$name is classified from the directory alone — a report must not inflate the " +
                            "file it is reporting on",
                        0,
                        tracked.openCount(name),
                    )
                }
            }
        }
    }

    @Test
    fun aDocumentHeldInMemoryIsAuditedWithoutAnArchive() {
        val pages = listOf(page(0, elements = listOf(ImageElement(0.0, 0.0, 10.0, 10.0, payload(512)))))
        val report = PortabilityAudit.audit(
            com.mobixournal.format.model.Document(pages = pages, documentId = "doc_memory"),
            entries = emptySet(),
        )

        assertEquals("doc_memory", report.documentId)
        assertTrue("a picture's bytes are in the model, so they are inside the document", report.isPortable)
        assertEquals(1, report.counts[PortabilityStatus.EMBEDDED])
    }

    @Test
    fun anAuditedPageSourceIsTheCallersToClose() {
        val file = fileOf("owner.mobix", SyntheticNotebook.pages(2))
        MobixPagedReader.open(file).use { reader ->
            val source = PageSource.of(reader)
            val report = PortabilityAudit.audit(source, reader.manifest.documentId, reader.source.entryNames)

            assertEquals("nothing to report on plain pages", 0, report.entries.size)
            assertTrue(report.isPortable)
            assertEquals(
                "auditing leaves the reader usable: the report own no lifetime",
                2,
                reader.pageCount,
            )
        }
    }
}

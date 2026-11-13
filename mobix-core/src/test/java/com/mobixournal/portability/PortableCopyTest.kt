package com.mobixournal.portability

import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.MobixPayload
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.mobix.carriedPayloads
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The portable copy (`PortableCopy`, `docs/mobix-assets.md` → *A portable copy*): embed what can be
 * read, leave what cannot as a reference, and prove the written file by re-opening it.
 *
 * The fixtures are real archives written through the container, so "the external one was embedded" is a
 * claim about bytes on disk rather than about a map the test itself built.
 */
class PortableCopyTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val now = 1_790_000_000_000L

    private fun payload(size: Int): ByteArray =
        ByteArray(size).also { java.util.Random(size.toLong()).nextBytes(it) }

    private fun page(index: Int, background: Background): Page = Page(
        width = SyntheticNotebook.WIDTH_PT,
        height = SyntheticNotebook.HEIGHT_PT,
        background = background,
        layers = listOf(Layer(emptyList())),
        id = SyntheticNotebook.idOf(index),
    )

    /**
     * A notebook with one **bundled** PDF background and one **external** one: the first page's bytes
     * travel in the archive, the second page only names a file.
     */
    private fun mixed(source: File) {
        val bundled = payload(4096)
        SyntheticNotebook.write(
            source,
            listOf(
                page(0, Background.Pdf(filename = "bundled.pdf", pageNo = 0, domain = "absolute")),
                page(1, Background.Pdf(filename = "external.pdf", pageNo = 0, domain = "absolute")),
            ),
            backgrounds = { background ->
                if (background is Background.Pdf && background.filename == "bundled.pdf") MobixPayload.of(bundled) else null
            },
        )
    }

    private class OpenDocument(val file: File, val document: com.mobixournal.format.model.Document, val entries: Set<String>)

    private fun open(file: File): OpenDocument {
        val archive = MobixContainer.open(ByteArrayInputStream(file.readBytes()))
        return OpenDocument(file, MobixDocument.open(archive).toDocument(), archive.names.toSet())
    }

    private fun portableCopy(
        opened: OpenDocument,
        destination: File,
        openBytes: (PortabilityEntry) -> ByteArray?,
    ): PortableCopyResult = PortableCopy.write(
        source = PageSource.of(opened.document),
        archiveEntries = opened.entries,
        document = opened.document,
        destination = destination,
        appVersion = "2.1.0",
        nowMillis = now,
        openBytes = openBytes,
        carried = carriedPayloads(opened.document, opened.file, { null }),
    )

    @Test
    fun aBundledAndAnExternalBackgroundEndAsOnePortableFile() {
        val source = File(temp.root, "mixed.mobix").also(::mixed)
        val opened = open(source)
        val external = payload(8192)
        val copy = File(temp.root, "mixed-portable.mobix")

        val result = portableCopy(opened, copy) { entry ->
            if (entry.reference == "external.pdf") external else null
        }

        assertEquals("one reference was outside and is now inside", 1, result.embedded.size)
        assertEquals("external.pdf", result.embedded.single().reference)
        assertTrue("nothing is left outside: ${result.remaining}", result.isPortable)
        assertEquals("both PDFs are entries in the copy", 2, result.verifiedEntries)

        assertTrue("the copy is a normal .mobix, not a second container", PortableCopy.isReadableCopy(copy))
        MobixPagedReader.open(copy).use { reader ->
            val report = PortabilityAudit.audit(reader)
            assertEquals("zero referenced-absent references after the copy", 0, report.counts[PortabilityStatus.REFERENCED_ABSENT])
            assertEquals(0, report.counts[PortabilityStatus.UNRECOVERABLE])
            assertTrue(report.isPortable)
        }
        assertTrue(
            "the page that named a file now carries its bytes",
            MobixContainer.open(ByteArrayInputStream(copy.readBytes())).text("pages/page-0002.json")!!
                .contains("assets/img_"),
        )
        assertTrue(
            "and the bundled one is carried through unchanged",
            MobixContainer.open(ByteArrayInputStream(copy.readBytes()))
                .entries.filter { it.name.startsWith(MobixFormat.ASSETS_DIR) }
                .map { it.bytes.size }
                .containsAll(listOf(4096, 8192)),
        )
    }

    @Test
    fun aReferenceThatCannotBeReadStaysAListedReference() {
        val source = File(temp.root, "unreadable.mobix").also(::mixed)
        val opened = open(source)
        val copy = File(temp.root, "unreadable-portable.mobix")

        val result = portableCopy(opened, copy) { null }

        assertTrue("nothing was embedded", result.embedded.isEmpty())
        assertEquals("the reference is still reported, not hidden", listOf("external.pdf"), result.remaining.map { it.reference })
        assertEquals(PortabilityStatus.REFERENCED_ABSENT, result.remaining.single().status)
        assertFalse("and the copy does not claim portability", result.isPortable)
        assertEquals("the bundled PDF still travelled", 1, result.verifiedEntries)
        assertTrue("the copy is still a readable document", PortableCopy.isReadableCopy(copy))
    }

    @Test
    fun aPayloadPastTheEntryLimitIsLeftAsAReference() {
        val source = File(temp.root, "oversized.mobix").also(::mixed)
        val opened = open(source)
        val copy = File(temp.root, "oversized-portable.mobix")
        val tight = com.mobixournal.format.mobix.MobixSafetyCaps(maxEntryBytes = 1024)

        val result = PortableCopy.write(
            source = PageSource.of(opened.document),
            archiveEntries = opened.entries,
            document = opened.document,
            destination = copy,
            appVersion = "2.1.0",
            nowMillis = now,
            openBytes = { payload(8192) },
            carried = carriedPayloads(opened.document, opened.file, { null }),
            caps = tight,
        )

        assertTrue("an entry the container would refuse is not embedded", result.embedded.isEmpty())
        assertEquals(listOf("external.pdf"), result.remaining.map { it.reference })
    }

    @Test
    fun theOriginalDocumentAndItsFileAreUntouched() {
        val source = File(temp.root, "original.mobix").also(::mixed)
        val before = source.readBytes()
        val opened = open(source)
        val copy = File(temp.root, "original-portable.mobix")

        portableCopy(opened, copy) { payload(2048) }

        assertTrue("a portable copy never rewrites the file it read", before.contentEquals(source.readBytes()))
        assertTrue("the destination is a separate file", copy.length() > 0 && copy.absolutePath != source.absolutePath)
    }

    /**
     * The suffix is the only thing that distinguishes a hand-off copy from a notebook, and both shells
     * decide "is this portable?" from it — so the rule is pinned here, including the case-insensitive
     * spelling and the backup name that must not be mistaken for one.
     */
    @Test
    fun aPortableCopyIsRecognisedByItsOwnSuffix() {
        assertTrue(PortableCopy.isPortableCopy("notes.pmobix"))
        assertTrue(PortableCopy.isPortableCopy("Notes.PMOBIX"))
        assertFalse(PortableCopy.isPortableCopy("notes.mobix"))
        assertFalse(PortableCopy.isPortableCopy("notes.pmobix.bak"))
        assertFalse(PortableCopy.isPortableCopy("notes"))
    }
}

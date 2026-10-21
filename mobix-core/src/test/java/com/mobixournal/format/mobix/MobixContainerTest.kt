package com.mobixournal.format.mobix

import com.mobixournal.format.Xopp
import com.mobixournal.format.XoppZip
import com.mobixournal.format.mobix.MobixContainer.Entry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `.mobix` container's promises, each one a rule of its own from `docs/mobix-schema-spec.md`: the
 * mimetype is first and stored, the type — not the file name — tells the two ZIP formats apart, a
 * document always carries a manifest, and an entry this build does not model survives a rewrite.
 * Everything else in the format sits on those four, so they are pinned before there is anything else
 * that could break them.
 */
class MobixContainerTest {

    private fun manifest() = Entry(MobixFormat.MANIFEST_ENTRY, """{"formatVersion":1}""".toByteArray())

    private fun archiveOf(vararg entries: Entry): ByteArray = MobixContainer.toBytes(entries.toList())

    /** Every entry of [bytes] as (name, compression method, content), in archive order. */
    private fun rawEntries(bytes: ByteArray): List<Triple<String, Int, ByteArray>> {
        val out = mutableListOf<Triple<String, Int, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e: ZipEntry = zip.nextEntry ?: break
                out += Triple(e.name, e.method, zip.readBytes())
                zip.closeEntry()
            }
        }
        return out
    }

    /** A ZIP built by hand, so a test can write a foreign shape our writer would never produce. */
    private fun handWritten(zip: (ZipOutputStream) -> Unit): ByteArray =
        ByteArrayOutputStream().also { out -> ZipOutputStream(out).use(zip) }.toByteArray()

    /** A real ZIP-package `.xopp`, so "is this a `.mobix`?" is asked of the format that competes. */
    private fun xoppZip(): ByteArray = ByteArrayOutputStream().also { out ->
        XoppZip.save(
            Xopp.parseXml(
                """
                <?xml version="1.0" standalone="no"?>
                <xournal creator="xournalpp 1.1.1+dev" fileversion="4">
                <page width="595.27559100" height="841.88976400">
                <background type="solid" color="#ffffffff" style="graph"/>
                <layer><stroke tool="pen" color="#000000ff" width="0.85">10.0 20.0 11.0 21.0</stroke></layer>
                </page>
                </xournal>
                """.trimIndent(),
            ),
            pdf = null,
            output = out,
        )
    }.toByteArray()

    @Test
    fun theMimetypeIsTheFirstEntryAndStoredUncompressed() {
        val entries = rawEntries(archiveOf(manifest(), Entry(MobixFormat.pageEntry(1), "{}".toByteArray())))

        assertEquals("the mimetype comes first", MobixFormat.MIMETYPE_ENTRY, entries.first().first)
        assertEquals("and is stored, not deflated", ZipEntry.STORED, entries.first().second)
        assertArrayEquals(
            "carrying the format's type",
            MobixFormat.MIMETYPE.toByteArray(Charsets.UTF_8),
            entries.first().third,
        )
        // Everything else is compressed: a container that does not shrink its payload is a folder.
        assertEquals(ZipEntry.DEFLATED, entries[1].second)
    }

    @Test
    fun everyEntryRoundTripsByteForByte() {
        val binary = ByteArray(256) { it.toByte() }
        val entries = listOf(
            manifest(),
            Entry(MobixFormat.pageEntry(1), """{"type":"page"}""".toByteArray(Charsets.UTF_8)),
            Entry(MobixFormat.assetEntry("a".repeat(64), MobixFormat.AssetKind.IMAGE), binary),
            Entry(MobixFormat.BRIDGE_ENTRY, "caffè latte".toByteArray(Charsets.UTF_8)),
        )

        val opened = MobixContainer.open(ByteArrayInputStream(MobixContainer.toBytes(entries)))

        assertEquals(MobixFormat.MIMETYPE, opened.mimetype)
        assertEquals(entries.map { it.name }, opened.names)
        for (entry in entries) assertArrayEquals(entry.name, entry.bytes, opened.bytes(entry.name))
        assertEquals("text() decodes as UTF-8", "caffè latte", opened.text(MobixFormat.BRIDGE_ENTRY))
    }

    @Test
    fun anEntryThisBuildDoesNotModelSurvivesARewrite() {
        // `chunks/` belongs to the infinite-canvas mode, which this build does not read yet — the
        // stand-in for "written by a newer version". Open-then-save must not act as a filter.
        val payload = listOf(
            manifest(),
            Entry(MobixFormat.chunkEntry(-3, 2), """{"elements":[]}""".toByteArray()),
            Entry("index/search.bin", ByteArray(64) { (it * 7).toByte() }),
        )

        val opened = MobixContainer.open(ByteArrayInputStream(MobixContainer.toBytes(payload)))
        val rewritten = MobixContainer.open(ByteArrayInputStream(MobixContainer.toBytes(opened)))

        assertEquals("order preserved, so an incremental write stays predictable", opened.names, rewritten.names)
        for (entry in payload) assertArrayEquals(entry.name, entry.bytes, rewritten.bytes(entry.name))
    }

    @Test
    fun aZipPackageXoppIsNotAMobix() {
        val xopp = xoppZip()

        assertFalse("it is a ZIP, but not our ZIP", MobixContainer.isMobix(ByteArrayInputStream(xopp)))
        assertEquals(
            "its own type is reported, not our own assumed",
            "application/x-xopp-zip",
            MobixContainer.mimetypeOf(ByteArrayInputStream(xopp)),
        )
        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixContainer.open(ByteArrayInputStream(xopp))
        }
        assertTrue(
            "the message names the type it found: ${failure.message}",
            "application/x-xopp-zip" in failure.message.orEmpty(),
        )
    }

    @Test
    fun aPlainZipIsRejectedForDeclaringNoType() {
        val plain = handWritten { zip ->
            zip.putNextEntry(ZipEntry(MobixFormat.MANIFEST_ENTRY))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }

        assertNull(MobixContainer.mimetypeOf(ByteArrayInputStream(plain)))
        assertFalse(MobixContainer.isMobix(ByteArrayInputStream(plain)))
        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixContainer.open(ByteArrayInputStream(plain))
        }
        assertTrue(
            "the message says what was missing: ${failure.message}",
            MobixFormat.MIMETYPE_ENTRY in failure.message.orEmpty(),
        )
    }

    @Test
    fun anArchiveWithoutAManifestIsNotADocument() {
        val bytes = handWritten { zip ->
            zip.putNextEntry(ZipEntry(MobixFormat.MIMETYPE_ENTRY))
            zip.write(MobixFormat.MIMETYPE.toByteArray())
            zip.closeEntry()
        }

        assertEquals("the type is ours", MobixFormat.MIMETYPE, MobixContainer.mimetypeOf(ByteArrayInputStream(bytes)))
        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixContainer.open(ByteArrayInputStream(bytes))
        }
        assertTrue(
            "the manifest is what is missing: ${failure.message}",
            MobixFormat.MANIFEST_ENTRY in failure.message.orEmpty(),
        )
    }

    @Test
    fun aDeflatedMimetypeIsStillRead() {
        // Another tool need not follow our write rules; a reader that insisted on STORED would refuse
        // a file it can read perfectly well.
        val bytes = handWritten { zip ->
            zip.putNextEntry(ZipEntry(MobixFormat.MIMETYPE_ENTRY))
            zip.write(MobixFormat.MIMETYPE.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(MobixFormat.MANIFEST_ENTRY))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }

        assertEquals(MobixFormat.MIMETYPE, MobixContainer.mimetypeOf(ByteArrayInputStream(bytes)))
        assertEquals(listOf(MobixFormat.MANIFEST_ENTRY), MobixContainer.open(ByteArrayInputStream(bytes)).names)
    }

    @Test
    fun saveRefusesToWriteADocumentWithoutAManifest() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            MobixContainer.toBytes(listOf(Entry(MobixFormat.pageEntry(1), "{}".toByteArray())))
        }
        assertTrue(failure.message.orEmpty(), MobixFormat.MANIFEST_ENTRY in failure.message.orEmpty())
    }

    @Test
    fun saveRefusesACallerSuppliedMimetype() {
        // The container owns its own type entry: a caller's copy could only mislabel the file.
        val failure = assertThrows(IllegalArgumentException::class.java) {
            MobixContainer.toBytes(listOf(Entry(MobixFormat.MIMETYPE_ENTRY, "text/plain".toByteArray()), manifest()))
        }
        assertTrue(failure.message.orEmpty(), MobixFormat.MIMETYPE_ENTRY in failure.message.orEmpty())
    }

    @Test
    fun saveRefusesDuplicateEntryNames() {
        assertThrows(IllegalArgumentException::class.java) {
            MobixContainer.toBytes(listOf(manifest(), Entry(MobixFormat.MANIFEST_ENTRY, "{}".toByteArray())))
        }
    }

    @Test
    fun requireNamesTheMissingEntry() {
        val opened = MobixContainer.open(ByteArrayInputStream(archiveOf(manifest())))

        assertNull(opened.entry("pages/page-0009.json"))
        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            opened.require("pages/page-0009.json")
        }
        assertTrue(failure.message.orEmpty(), "pages/page-0009.json" in failure.message.orEmpty())
    }

    @Test
    fun theContainerVocabularyIsTheFormats() {
        // One home for these strings: the writer, the reader and the docs all read them from here.
        assertEquals(".mobix", MobixFormat.EXTENSION)
        assertEquals("application/vnd.mobixournal+zip", MobixFormat.MIMETYPE)
        assertEquals("pages/page-0001.json", MobixFormat.pageEntry(1))
        assertEquals("pages/page-0010.json", MobixFormat.pageEntry(10))
        assertEquals("the width is padding, not a limit", "pages/page-10000.json", MobixFormat.pageEntry(10000))
        assertEquals("chunks/chunk_0_0.json", MobixFormat.chunkEntry(0, 0))
        assertEquals("a negative chunk is a name of its own, not a sign-less twin", "chunks/chunk_-3_2.json", MobixFormat.chunkEntry(-3, 2))
        assertEquals("chunks/chunk_2_-1.json", MobixFormat.chunkEntry(2, -1))
        assertEquals("assets/img_${"ab".repeat(32)}.webp", MobixFormat.assetEntry("ab".repeat(32), MobixFormat.AssetKind.IMAGE))
        assertEquals(
            "a PNG keeps its own container rather than being re-encoded",
            "assets/img_${"ab".repeat(32)}.png",
            MobixFormat.assetEntry("ab".repeat(32), MobixFormat.AssetKind.IMAGE, suffix = "png"),
        )
        assertEquals("assets/aud_${"cd".repeat(32)}.ogg", MobixFormat.assetEntry("cd".repeat(32), MobixFormat.AssetKind.AUDIO))
        assertThrows(IllegalArgumentException::class.java) { MobixFormat.pageEntry(0) }
        assertThrows(IllegalArgumentException::class.java) { MobixFormat.assetEntry("not-a-hash", MobixFormat.AssetKind.IMAGE) }
        assertTrue(MobixFormat.isPageEntry(MobixFormat.pageEntry(7)))
        assertTrue(MobixFormat.isChunkEntry(MobixFormat.chunkEntry(-1, -1)))
        assertFalse("the manifest is not a page", MobixFormat.isPageEntry(MobixFormat.MANIFEST_ENTRY))
    }
}

package com.mobixournal.portability

import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixPayload
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `MobixAssetValidator` over the golden corpus and one mutation per rule (`docs/mobix-assets.md` →
 * *A validator, not only reader behaviour*).
 *
 * Every mutation rewrites a real archive rather than hand-building JSON: the rules are about a ZIP's
 * entries, so the fixtures are ZIPs.
 */
class MobixAssetValidatorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun payload(size: Int): ByteArray =
        ByteArray(size).also { java.util.Random(size.toLong()).nextBytes(it) }

    /** Bytes the asset sink recognises as a PNG, so the name it writes carries the `png` suffix. */
    private fun png(size: Int): ByteArray = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + payload(size)

    private fun synthetic(name: String, pages: List<Page>, pdf: ByteArray? = null, audio: (String) -> MobixPayload? = { null }): File {
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

    /**
     * [source] rewritten to [target] with [mutate] applied to the entry list: each pair replaces the
     * bytes of an entry or adds one when the name is new.
     */
    private fun rewrite(source: File, target: File, mutate: (MutableMap<String, ByteArray>) -> Unit) {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipFile(source).use { zip ->
            for (entry in zip.entries()) entries[entry.name] = zip.getInputStream(entry).use { it.readBytes() }
        }
        mutate(entries)
        ZipOutputStream(target.outputStream()).use { out ->
            for ((name, bytes) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }

    /** A well-formed name for [bytes] of [kind]. */
    private fun nameOf(bytes: ByteArray, kind: MobixFormat.AssetKind = MobixFormat.AssetKind.IMAGE, suffix: String = "png"): String =
        MobixFormat.assetEntry(MobixAssetSink.sha256(bytes), kind, suffix)

    private fun rules(validation: AssetValidation): List<AssetRule> = validation.findings.map { it.rule }

    // ---- the golden corpus is clean ----------------------------------------------------------------

    @Test
    fun everyGoldenFixtureValidatesClean() {
        val directory = File(javaClass.classLoader!!.getResource("fixtures/mobix-v1")!!.toURI())
        val fixtures = directory.listFiles().orEmpty().filter { it.extension == "mobix" }.sortedBy { it.name }
        assertTrue("the corpus is present", fixtures.size >= 18)

        for (fixture in fixtures) {
            val validation = MobixAssetValidator.validate(fixture)
            assertTrue(
                "${fixture.name} breaks no rule: ${validation.findings}",
                validation.clean,
            )
        }
    }

    // ---- clean, and what "clean" does not mean ----------------------------------------------------

    @Test
    fun aDocumentWithBundledPayloadsIsCleanAndItsOrphansAreOnlyReported() {
        val picture = ImageElement(10.0, 10.0, 110.0, 110.0, payload(4096))
        val file = synthetic(
            "clean.mobix",
            listOf(
                page(0, elements = listOf(picture)),
                page(1, background = Background.Pdf(filename = "notes.pdf", pageNo = 0, domain = "absolute")),
            ),
            pdf = payload(8192),
        )

        val validation = MobixAssetValidator.validate(file)

        assertEquals("the picture and the PDF", 2, validation.assetCount)
        assertTrue("a bounded, content-addressed file breaks no rule: ${validation.findings}", validation.clean)
        assertEquals(listOf<AssetRule>(), rules(validation))
        assertEquals("the limits the check applied travel with the answer", 20_000, validation.caps.maxEntries)
    }

    // ---- one mutation per rule --------------------------------------------------------------------

    @Test
    fun aNameWithBadHexIsANameGrammarFinding() {
        val file = synthetic("bad-hex.mobix", listOf(page(0, elements = listOf(ImageElement(0.0, 0.0, 10.0, 10.0, payload(256))))))
        val mutated = File(temp.root, "bad-hex-mutated.mobix")
        rewrite(file, mutated) { entries -> entries["assets/img_${"z".repeat(64)}.png"] = payload(64) }

        val validation = MobixAssetValidator.validate(mutated)

        assertFalse(validation.clean)
        assertEquals(listOf(AssetRule.NAME_GRAMMAR), rules(validation))
        assertEquals("assets/img_${"z".repeat(64)}.png", validation.findings.single().entry)
    }

    @Test
    fun anUnknownPrefixIsItsOwnFinding() {
        val file = synthetic("prefix.mobix", listOf(page(0)))
        val mutated = File(temp.root, "prefix-mutated.mobix")
        val bytes = payload(128)
        rewrite(file, mutated) { entries -> entries["assets/vid_${MobixAssetSink.sha256(bytes)}.png"] = bytes }

        val validation = MobixAssetValidator.validate(mutated)

        assertEquals(listOf(AssetRule.UNKNOWN_PREFIX), rules(validation))
        assertTrue("the detail names the vocabulary a writer must use", "img" in validation.findings.single().detail)
    }

    @Test
    fun bytesThatDoNotHashToTheirNameAreADigestMismatch() {
        val file = synthetic("digest.mobix", listOf(page(0)))
        val mutated = File(temp.root, "digest-mutated.mobix")
        // A name whose digest belongs to other bytes: the shape is right, the content is not.
        rewrite(file, mutated) { entries -> entries[nameOf(payload(64))] = payload(65) }

        val validation = MobixAssetValidator.validate(mutated)

        assertEquals(listOf(AssetRule.DIGEST_MISMATCH), rules(validation))
        assertTrue(
            "the finding says both digests, so a reader can tell a damaged entry from a renamed one",
            "the name claims" in validation.findings.single().detail,
        )
    }

    @Test
    fun aReferencedEntryTheArchiveDoesNotCarryIsAnError() {
        val missing = MobixFormat.assetEntry("b".repeat(64), MobixFormat.AssetKind.IMAGE, "pdf")
        val file = synthetic(
            "missing.mobix",
            listOf(page(0, background = Background.Pdf(filename = "notes.pdf", pageNo = 0, domain = "absolute", bundledAsset = missing))),
        )

        val validation = MobixAssetValidator.validate(file)

        assertEquals(listOf(AssetRule.MISSING_ENTRY), rules(validation))
        assertEquals(missing, validation.findings.single().entry)
        assertEquals(0, validation.assetCount)
    }

    @Test
    fun anEntryNoPartNamesIsAnOrphanWarningAndStillClean() {
        val file = synthetic("orphan.mobix", listOf(page(0)))
        val mutated = File(temp.root, "orphan-mutated.mobix")
        val bytes = payload(256)
        rewrite(file, mutated) { entries -> entries[nameOf(bytes)] = bytes }

        val validation = MobixAssetValidator.validate(mutated)

        assertEquals(listOf(AssetRule.ORPHAN_ENTRY), rules(validation))
        assertEquals("reporting an orphan is not refusing the file", AssetSeverity.WARNING, validation.findings.single().severity)
        assertTrue(
            "an orphan is allowed by the preserve-unknown rule, so it is reported and the file stays clean",
            validation.clean,
        )
    }

    @Test
    fun aPayloadWrittenUnderAnotherSuffixIsStillThatPicture() {
        // Two entries address the same picture: the model keeps bytes rather than names, so matching by
        // name alone would report a live payload as an orphan.
        val picture = png(512)
        val file = synthetic("suffix.mobix", listOf(page(0, elements = listOf(ImageElement(0.0, 0.0, 10.0, 10.0, picture)))))
        val mutated = File(temp.root, "suffix-mutated.mobix")
        rewrite(file, mutated) { entries -> entries[nameOf(picture, suffix = "bin")] = picture.copyOf() }

        val validation = MobixAssetValidator.validate(mutated)

        assertEquals("the writer's entry and the second container", 2, validation.assetCount)
        assertTrue(
            "both hash to the picture's digest, so neither is an orphan: ${validation.findings}",
            validation.clean,
        )
    }

    @Test
    fun aHostileArchiveIsRefusedByTheEntryCapRatherThanWalked() {
        val file = synthetic("small.mobix", listOf(page(0)))
        val tight = com.mobixournal.format.mobix.MobixSafetyCaps(maxEntries = 2)

        val validation = MobixAssetValidator.validate(file, tight)

        assertFalse("a file past the cap is not clean", validation.clean)
        assertEquals(listOf(AssetRule.CONTAINER_LIMIT), rules(validation))
        assertEquals("nothing was examined", 0, validation.assetCount)
    }

    @Test
    fun validationWritesNothing() {
        val picture = ImageElement(0.0, 0.0, 10.0, 10.0, payload(300))
        val file = synthetic("untouched.mobix", listOf(page(0, elements = listOf(picture))))
        val before = file.readBytes()

        MobixAssetValidator.validate(file)

        assertTrue("a validator is a reading: the file is byte-identical", before.contentEquals(file.readBytes()))
    }
}

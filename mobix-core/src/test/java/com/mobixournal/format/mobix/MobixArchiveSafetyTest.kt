package com.mobixournal.format.mobix

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an archive is allowed to make the reader do before it is refused
 * (`docs/mobix-format.md`, *What a reader must refuse*).
 *
 * Two rules are asserted here because they are cheap to get wrong and expensive to get wrong quietly:
 * an entry name is an **identity**, so a duplicate, a traversal, an absolute path, a backslash or a
 * control character is refused rather than normalised; and a corrupt container is a **malformed
 * document**, one error type a shell can show, not a raw JDK exception.
 */
class MobixArchiveSafetyTest {

    private val manifest =
        """{"formatVersion":1,"documentId":"d","canvasMode":"PAGINATED"}""".toByteArray()

    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                fun put(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                put(MobixFormat.MIMETYPE_ENTRY, MobixFormat.MIMETYPE.toByteArray())
                for ((name, bytes) in entries) put(name, bytes)
            }
        }.toByteArray()

    private fun opening(vararg entries: Pair<String, ByteArray>) =
        MobixContainer.open(ByteArrayInputStream(archive(*entries)))

    private fun refusal(vararg entries: Pair<String, ByteArray>): MobixContainer.MalformedMobixException =
        assertThrows(MobixContainer.MalformedMobixException::class.java) { opening(*entries) }

    @Test
    fun aDuplicateEntryNameIsRefused() {
        // The JDK's writer refuses to emit a duplicate, so the archive that carries one has to be
        // imagined rather than built: the rule is asserted on the validator the reader runs.
        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixEntryNames.validate(listOf("pages/page-0001.json", "pages/page-0001.json"))
        }

        assertTrue(failure.message.orEmpty(), "twice" in failure.message.orEmpty())
        assertTrue(
            "and names the entry that appeared twice",
            "pages/page-0001.json" in failure.message.orEmpty(),
        )
    }

    @Test
    fun aTraversingNameIsRefused() {
        assertTrue("../pages/page-0001.json" in refusal(
            MobixFormat.MANIFEST_ENTRY to manifest,
            "../pages/page-0001.json" to "{}".toByteArray(),
        ).message.orEmpty())
    }

    @Test
    fun anAbsoluteNameIsRefused() {
        assertTrue("/pages/page-0001.json" in refusal(
            MobixFormat.MANIFEST_ENTRY to manifest,
            "/pages/page-0001.json" to "{}".toByteArray(),
        ).message.orEmpty())
    }

    @Test
    fun aWindowsAbsoluteNameIsRefused() {
        assertTrue("C:/pages/page-0001.json" in refusal(
            MobixFormat.MANIFEST_ENTRY to manifest,
            "C:/pages/page-0001.json" to "{}".toByteArray(),
        ).message.orEmpty())
    }

    @Test
    fun aBackslashIsRefusedRatherThanNormalised() {
        assertTrue("backslash" in refusal(
            MobixFormat.MANIFEST_ENTRY to manifest,
            "pages\\page-0001.json" to "{}".toByteArray(),
        ).message.orEmpty())
    }

    @Test
    fun aControlCharacterInANameIsRefused() {
        assertTrue("control character" in refusal(
            MobixFormat.MANIFEST_ENTRY to manifest,
            "pages/page-0001\n.json" to "{}".toByteArray(),
        ).message.orEmpty())
    }

    @Test
    fun aTruncatedArchiveIsAMalformedDocumentNotAnIoError() {
        val bytes = archive(MobixFormat.MANIFEST_ENTRY to manifest)
        val truncated = bytes.copyOf(bytes.size / 2)

        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixContainer.open(ByteArrayInputStream(truncated))
        }
        assertTrue("the refusal says what happened: ${failure.message}", failure.message.orEmpty().isNotBlank())
    }

    /**
     * Where [name]'s local header starts.
     *
     * A ZIP's local header is fixed-size up to the name (`PK\3\4`, version, flags, method, time, date,
     * CRC, sizes, two lengths = 30 bytes), so finding the signature followed by the name is exact — and
     * this is how the two corrupt shapes the JDK's writer will not produce get built.
     */
    private fun localHeaderOf(bytes: ByteArray, name: String): Int {
        val needle = name.toByteArray()
        val signature = intArrayOf(0x50, 0x4B, 0x03, 0x04)
        for (start in 0..(bytes.size - 30 - needle.size)) {
            if (signature.withIndex().any { (i, b) -> bytes[start + i].toInt() != b }) continue
            if (needle.withIndex().all { (i, b) -> bytes[start + 30 + i] == b }) return start
        }
        throw AssertionError("the fixture has no local header for $name")
    }

    private fun ByteArray.rewriting(name: String, change: (ByteArray, Int) -> Unit): ByteArray =
        copyOf().also { change(it, localHeaderOf(it, name)) }

    @Test
    fun anEncryptedEntryIsAMalformedDocument() {
        // General-purpose flag bit 0 is the encryption bit; the JDK's writer never sets it, so the
        // archive is built honestly and then marked encrypted, which is what a hostile tool would ship.
        val bytes = archive(MobixFormat.MANIFEST_ENTRY to manifest)
            .rewriting(MobixFormat.MANIFEST_ENTRY) { data, header ->
                data[header + 6] = (data[header + 6].toInt() or 0x01).toByte()
            }

        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixContainer.open(ByteArrayInputStream(bytes))
        }
        assertTrue(
            "the refusal says the entry is encrypted: ${failure.message}",
            "encrypt" in failure.message.orEmpty(),
        )
        assertTrue("and keeps the JDK's own exception", failure.cause is java.util.zip.ZipException)
    }

    /**
     * An archive whose [name] entry is **stored** rather than deflated.
     *
     * A deflated entry keeps its checksum in a trailing descriptor (flag bit 3), so a lie written into
     * the header would be ignored; a stored entry records it in the header, which is the field a file
     * damaged in transfer actually disagrees with.
     */
    private fun archiveWithStored(name: String, bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                fun put(entryName: String, payload: ByteArray, stored: Boolean) {
                    val entry = ZipEntry(entryName)
                    if (stored) {
                        entry.method = ZipEntry.STORED
                        entry.size = payload.size.toLong()
                        entry.compressedSize = payload.size.toLong()
                        entry.crc = CRC32().apply { update(payload) }.value
                    }
                    zip.putNextEntry(entry)
                    zip.write(payload)
                    zip.closeEntry()
                }
                put(MobixFormat.MIMETYPE_ENTRY, MobixFormat.MIMETYPE.toByteArray(), stored = false)
                put(name, bytes, stored = true)
            }
        }.toByteArray()

    @Test
    fun aBadEntryCrcIsAMalformedDocument() {
        // A recorded CRC that disagrees with the bytes is the shape of a file damaged in transfer: it
        // inflates, but not to what the archive promised, so the reader must refuse instead of decoding
        // it.
        val bytes = archiveWithStored(MobixFormat.MANIFEST_ENTRY, manifest)
            .rewriting(MobixFormat.MANIFEST_ENTRY) { data, header ->
                data[header + 14] = (data[header + 14].toInt() xor 0xFF).toByte()
            }

        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixContainer.open(ByteArrayInputStream(bytes))
        }
        assertTrue(
            "the refusal names the failed check: ${failure.message}",
            "CRC" in failure.message.orEmpty(),
        )
        assertTrue("and keeps the JDK's own exception", failure.cause is java.util.zip.ZipException)
    }

    @Test
    fun aHealthyArchiveStillOpens() {
        val opened = opening(MobixFormat.MANIFEST_ENTRY to manifest)

        assertTrue(opened.names.contains(MobixFormat.MANIFEST_ENTRY))
    }
}

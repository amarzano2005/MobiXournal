package com.mobixournal.portability

import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDigests
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.MobixPayload
import com.mobixournal.format.mobix.MobixSafetyCaps
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.withIdentity
import java.io.File

/**
 * What a portable copy did: where it landed, which references it **embedded**, which it had to leave as
 * references (so the copy says what it could not do instead of claiming portability), and how many
 * `assets/` entries re-opening it verified.
 */
data class PortableCopyResult(
    val destination: File,
    val embedded: List<PortabilityEntry>,
    val remaining: List<PortabilityEntry>,
    val verifiedEntries: Int,
) {
    /** True when nothing is left outside the copy — every reference it names is now bytes in the file. */
    val isPortable: Boolean get() = remaining.isEmpty()
}

/**
 * **Save a portable copy** of a document: embed the bytes of every reference whose file can still be
 * read, and say plainly which ones could not be.
 *
 * The rule the whole feature turns on is that this writes **a normal `.mobix`** — never a second
 * container type, never a bundle with a manifest of its own. What makes the copy portable is that its
 * pages point at `assets/` entries the archive carries (`MobixAssetSink` names them by content), which
 * is exactly what the writer already does when a caller can supply a background's bytes. So the work
 * here is three steps, and none of them invents a format:
 *
 *  1. **Classify** the document ([PortabilityAudit]) — which references are not inside it.
 *  2. **Ask for their bytes** through [openBytes] and hand them to the writer as the background or
 *     recording provider it already takes. A reference whose bytes are missing, unreadable or past the
 *     archive limits is simply not supplied: it stays a reference in the model, and therefore in the
 *     written file and in the report this returns.
 *  3. **Re-open the copy and verify it** — every `assets/` entry must hash to the name it is stored
 *     under, the check `MobixPagedReader` applies to any payload (it is what makes the copy trustworthy
 *     rather than merely written). A copy that fails that check is deleted and reported as a failure,
 *     because a broken "portable" file is worse than no copy.
 *
 * Nothing is downloaded, and the original document and its file are untouched: the copy is a new file
 * at [write]'s destination.
 *
 * The **desktop window** calls it from its menu's *Save portable copy* chip
 * (`desktop/src/main/java/com/mobixournal/desktop/io/PortableCopyCommand.kt`); the tablet's own command is
 * still the open task in `TODO.toml`, so `app/` has no call site yet.
 */
object PortableCopy {

    /**
     * Write a portable copy of [document] to [destination].
     *
     * @param source the document's pages, as the caller reads them — the audit walks them once, and
     *   adopting a background is not part of this (the bytes come from [openBytes] instead).
     * @param archiveEntries the archive's central directory the document came from, for "is it present?".
     * @param document the model to write. Its pages are what the copy contains; its references are what
     *   the audit classifies.
     * @param openBytes the bytes behind one absent reference, or null when they cannot be obtained. The
     *   caller resolves a path or a sidecar the way its own renderer does — this class never assumes a
     *   folder.
     * @param carried payloads a source-less write still has to keep: the already-bundled entries the
     *   model names (`carriedPayloads` builds them from the archive the document was opened from).
     * @throws VerificationFailed when the copy re-opens with an entry that does not hash to its name;
     *   the partial file is deleted first.
     */
    fun write(
        source: PageSource,
        archiveEntries: Set<String>,
        document: Document,
        destination: File,
        appVersion: String,
        nowMillis: Long,
        openBytes: (PortabilityEntry) -> ByteArray?,
        carried: Map<String, MobixPayload> = emptyMap(),
        caps: MobixSafetyCaps = MobixSafetyCaps.Default,
    ): PortableCopyResult {
        val report = PortabilityAudit.audit(source, document.documentId, archiveEntries, caps)
        val wanted = LinkedHashMap<String, PortabilityEntry>()
        for (entry in report.entries) {
            if (entry.status != PortabilityStatus.REFERENCED_ABSENT) continue
            wanted.putIfAbsent(entry.dedupeKey, entry)
        }

        val backgrounds = HashMap<String, MobixPayload>()
        val recordings = HashMap<String, MobixPayload>()
        for (entry in wanted.values) {
            val bytes = runCatching { openBytes(entry) }.getOrNull() ?: continue
            // A payload past the container's own per-entry limit is not one this writer may add: it
            // stays a reference, which is the honest answer rather than a copy that cannot be opened.
            if (bytes.isEmpty() || bytes.size.toLong() > caps.maxEntryBytes) continue
            val payload = MobixPayload.of(bytes)
            when (entry.kind) {
                PortabilityKind.PDF, PortabilityKind.PIXMAP -> backgrounds[entry.reference] = payload
                PortabilityKind.AUDIO -> recordings[entry.reference] = payload
                // A picture inside an element is bytes the model already holds; a font is a hint.
                PortabilityKind.IMAGE, PortabilityKind.TEX_IMAGE, PortabilityKind.FONT -> Unit
            }
        }

        destination.parentFile?.mkdirs()
        val written = MobixDocument.of(document.withIdentity(), appVersion, nowMillis, payloads = carried)
        written.save(
            output = destination.outputStream(),
            nowMillis = nowMillis,
            backgrounds = { background -> backgroundPayload(background, backgrounds) },
            audio = { name -> recordings[name] },
        )

        val remaining = verifyCopy(destination, caps)
        val stillMissing = remaining.map { it.dedupeKey }.toSet()
        return PortableCopyResult(
            destination = destination,
            embedded = wanted.values.filter { it.dedupeKey !in stillMissing },
            remaining = remaining,
            verifiedEntries = countVerified(destination, caps),
        )
    }

    /** The payload a background's own reference asks for, or null when the writer must keep the name. */
    private fun backgroundPayload(
        background: Background,
        supplied: Map<String, MobixPayload>,
    ): MobixPayload? = when (background) {
        is Background.Pdf -> background.filename?.let(supplied::get)
        is Background.Pixmap -> supplied[background.filename]
        is Background.Solid -> null
    }

    /**
     * Re-open [copy] and report what it still leaves outside itself. The walk also proves every page
     * it names decodes, so a copy that was written badly fails here rather than on a reader's device.
     */
    private fun verifyCopy(copy: File, caps: MobixSafetyCaps): List<PortabilityEntry> =
        MobixEntrySource.fromFile(copy).use { entries ->
            verifyEntries(entries, caps)
            MobixPagedReader(entries, caps = caps).let { reader -> PortabilityAudit.audit(reader).missing }
        }

    /** Fail on the first `assets/` entry that does not hash to its own name — the copy's own promise. */
    private fun verifyEntries(entries: MobixEntrySource, caps: MobixSafetyCaps) {
        for (name in entries.entryNames.filter { it.startsWith(MobixFormat.ASSETS_DIR) }.sorted()) {
            val claimed = MobixDigests.digestIn(name) ?: continue
            val bytes = caps.read(entries, name, caps.maxAssetBytes) ?: run {
                copyFailed("the copy lists \"$name\" and does not carry it")
            }
            val actual = MobixAssetSink.sha256(bytes)
            if (actual != claimed) {
                copyFailed("\"$name\" hashes to $actual in the copy: it was written damaged")
            }
        }
    }

    /** How many `assets/` entries the copy carries — the number the result reports as verified. */
    private fun countVerified(copy: File, caps: MobixSafetyCaps): Int =
        MobixEntrySource.fromFile(copy).use { entries ->
            entries.entryNames.count { name ->
                name.startsWith(MobixFormat.ASSETS_DIR) && MobixDigests.digestIn(name) != null &&
                    caps.read(entries, name, caps.maxAssetBytes)?.let { MobixAssetSink.sha256(it) } ==
                    MobixDigests.digestIn(name)
            }
        }

    private fun copyFailed(why: String): Nothing {
        throw VerificationFailed(why)
    }

    /** The copy re-opened as something other than a faithful document; nothing partial is left behind. */
    class VerificationFailed(message: String) : IllegalStateException(message)

    /** True when [file] is a `.mobix` this build would open — what "a normal `.mobix`" means. */
    fun isReadableCopy(file: File): Boolean =
        file.isFile && runCatching { MobixContainer.isMobix(file.inputStream().buffered()) }.getOrDefault(false)
}

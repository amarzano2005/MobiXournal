package com.mobixournal.format.mobix

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import java.io.OutputStream

/**
 * An **incremental save** of a `.mobix` ([MobixIncrementalSaver] is the repacker this plans for): a save
 * that touched one page writes the manifest and that page, and streams every untouched page, the
 * background PDF and the bundled pictures out of the archive it was opened from.
 *
 * The decision is **derived, never maintained**: nothing here is told what changed, because a dirty flag
 * is a second copy of the answer that drifts the first time someone forgets to set it. Two facts make
 * the comparison cheap enough to run on every save:
 *
 *  - a **part** (a page or a chunk) is hashed by the same `sha256` the manifest already records for it,
 *    so a part whose bytes hash to what the previous manifest says is byte-identical without reading it;
 *  - an **asset** is named after the hash of its own bytes ([MobixAssetSink]), so an entry name the
 *    source already holds *is* these bytes — which is why a five-megabyte picture costs one string
 *    comparison.
 *
 * What is left — the envelope's own entries (`canvas.json`, a bridge file, a preserved entry) — is small
 * enough to compare byte for byte. `manifest.json` is always rewritten: it carries this file's
 * `modified` stamp, so it is never the same bytes twice.
 *
 * A page that is **not in [MobixSavePlan.entries] at all** is the case the comparison never has to
 * reach: the page was never built, because the archive already holds it ([MobixPageReuse]) — so the
 * bytes are streamed like any other unchanged entry, and the hash this diff would have computed is the
 * one the source manifest already recorded.
 */
internal object MobixIncrementalSave {

    /**
     * Repack [plan] over [source], which holds the archive this document was opened from.
     *
     * [plan] is what a full write would hold — [MobixDocument.encode] produced it — so the archive that
     * comes out describes the same document either way, and a caller with no source has the plain write
     * as an exact fallback. Entries the source holds that this build does not model are streamed
     * verbatim, which is container rule 4 (`docs/mobix-format.md`).
     */
    fun repack(
        source: MobixEntrySource,
        plan: MobixSavePlan,
        output: OutputStream,
    ): MobixIncrementalSaveStats = MobixIncrementalSaver.repack(
        source = source,
        dirtyEntries = dirtyEntries(plan.entries, MobixManifest.read(source), source),
        output = output,
        explicitOrder = plan.order,
    )

    /**
     * Save [document] **over** the archive [source] holds — the whole incremental save in one step:
     * which pages the source already has ([MobixPageReuse]), the build that skips them, and the repack
     * that streams them back out.
     *
     * This is what the app's save path does when a save lands back on the file the document was opened
     * from, kept here rather than in the I/O layer because nothing about it needs a device: it is source,
     * document and output, which is what lets a JVM test drive the shipped save over a real archive.
     *
     * @param bridgeSource what the document was called on disk, for the source record a `.mobix` carries
     *   (`MobixBridgeFile`), when the caller knows it.
     * @param canvasMode the mode the document was opened in and must be written back in — a chunked
     *   document stays chunked ([MobixDocument.of], `docs/infinite-canvas.md`), and the same mode reaches
     *   the reuse plan, which is what lets an untouched chunk be copied rather than rebuilt.
     * @param chunkCoords where each page sits on an infinite canvas, in page order, when the caller knows
     *   (a document opened from a chunked file does: the coordinates are the source manifest's own).
     * @param canvas the board geometry a chunked document was opened with, when the caller knows it: the
     *   `canvas.json` it must write back ([MobixDocument.of], `docs/infinite-canvas.md`).
     */
    fun saveOver(
        source: MobixEntrySource,
        document: Document,
        appVersion: String,
        nowMillis: Long,
        output: OutputStream,
        backgroundBytes: (Background) -> ByteArray? = { null },
        audioBytes: (String) -> ByteArray? = { null },
        bridgeSource: String? = null,
        canvasMode: CanvasMode = CanvasMode.PAGINATED,
        chunkCoords: List<Pair<Int, Int>>? = null,
        canvas: MobixCanvasFile? = null,
    ): MobixIncrementalSaveStats {
        val reuse = MobixPageReuse.of(document.pages, source, canvasMode)
        val mobix = MobixDocument.of(
            document = document,
            appVersion = appVersion,
            nowMillis = nowMillis,
            source = bridgeSource,
            reuse = reuse,
            canvasMode = canvasMode,
            chunkCoords = chunkCoords,
            canvas = canvas,
        )
        return repack(source, mobix.encode(nowMillis, backgroundBytes, audioBytes, reuse), output)
    }

    /**
     * The entries that must be freshly written, and only those. [previous] is the source's own manifest,
     * or null when it has none (then everything is written, which is what a full save would do).
     */
    fun dirtyEntries(
        entries: List<MobixContainer.Entry>,
        previous: MobixManifest?,
        source: MobixEntrySource,
    ): Map<String, ByteArray> {
        // What the previous write recorded for each part it wrote: an entry name and the hash of the
        // bytes that were in it. This is the manifest paying for the diff.
        val recorded = HashMap<String, String>()
        previous?.let { manifest ->
            (manifest.pages + manifest.chunks).forEach { recorded[it.entry] = it.sha256 }
        }

        val dirty = LinkedHashMap<String, ByteArray>()
        for (entry in entries) {
            if (entry.name == MobixFormat.MANIFEST_ENTRY) {
                dirty[entry.name] = entry.bytes
                continue
            }
            val digest = MobixAssetSink.sha256(entry.bytes)
            // An asset's name is the hash of its own bytes: the same name is the same payload.
            if (entry.name.contains(digest) && entry.name in source.entryNames) continue
            // A part the manifest described: its hash settles it either way, and — the point here —
            // nothing is inflated to find out. A recorded hash that is not this one means the bytes
            // moved, which is exactly the entry the save is about to write.
            val recordedHash = recorded[entry.name]
            if (recordedHash != null) {
                if (recordedHash == digest) continue
                dirty[entry.name] = entry.bytes
                continue
            }
            // The envelope: small, and nobody recorded its bytes, so it is simply compared.
            if (source.readBytes(entry.name)?.contentEquals(entry.bytes) == true) continue
            dirty[entry.name] = entry.bytes
        }
        return dirty
    }
}

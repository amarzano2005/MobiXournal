package com.mobixournal.format.mobix

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
 */
internal object MobixIncrementalSave {

    /**
     * Repack [entries] over [source], which holds the archive this document was opened from.
     *
     * [entries] is what a full write would hold — [MobixDocument.encode] produced it — so the archive
     * that comes out describes the same document either way, and a caller with no source has the plain
     * write as an exact fallback. Entries the source holds that this build does not model are streamed
     * verbatim, which is container rule 4 (`docs/mobix-format.md`).
     */
    fun repack(
        source: MobixEntrySource,
        entries: List<MobixContainer.Entry>,
        output: OutputStream,
    ): MobixIncrementalSaveStats = MobixIncrementalSaver.repack(
        source = source,
        dirtyEntries = dirtyEntries(entries, MobixManifest.read(source), source),
        output = output,
        explicitOrder = entries.map { it.name },
    )

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
            // A part the manifest described: its hash settles it, without inflating anything.
            if (recorded[entry.name] == digest) continue
            // The envelope: small, so it is simply compared.
            if (source.readBytes(entry.name)?.contentEquals(entry.bytes) == true) continue
            dirty[entry.name] = entry.bytes
        }
        return dirty
    }
}

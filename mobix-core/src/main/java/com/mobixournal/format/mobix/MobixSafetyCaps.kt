package com.mobixournal.format.mobix

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The **one cap policy** the read path enforces (`docs/mobix-format.md`, *What a reader must refuse*).
 *
 * A `.mobix` is untrusted input — downloaded, synced, mailed by someone else — and a reader that
 * allocates whatever an archive asks for turns a 20 kB file into an `OutOfMemoryError`. Each threshold
 * below is derived from the **largest legitimate document** this app is expected to open (a 200-page
 * notebook with bundled recordings and pictures), not guessed, and each refusal is a
 * [MobixContainer.MalformedMobixException] that names the entry and the rule rather than a JDK error a
 * shell would have to translate.
 *
 * The class is a **value**, so a test can enforce the same rules at a size it can build in milliseconds:
 * production always runs [Default]. The distinction matters — a cap exercised only at 256 MB is a cap
 * nobody re-verifies — and it keeps the numbers and the mechanism in one place instead of a constant
 * near each call site drifting from its documentation.
 *
 * A document under every cap behaves exactly as it did before this class existed: the caps refuse, they
 * do not alter what is read.
 */
data class MobixSafetyCaps(

    /**
     * How many entries an archive may carry.
     *
     * A 200-page notebook is ~200 page entries plus two or three assets a page, so under 1 000; a large
     * chunked board (1 000 pages of occupied tiles) stays under 5 000, and a document that resaves
     * accumulates preserved entries too. 20 000 is an order of magnitude above the largest document the
     * app is expected to open, which is what "motivated" means here — not tight, but far from a hostile
     * archive's millions.
     */
    val maxEntries: Int = 20_000,

    /**
     * How large one entry's **uncompressed** payload may be.
     *
     * The largest legitimate single entry is a bundled recording: this format's Ogg/Opus assets are
     * ~15 MB an hour (`docs/audio.md`), so 256 MiB is about seventeen hours in one file — well past any
     * lecture, and still small enough that a few of them cannot exhaust a phone's heap.
     */
    val maxEntryBytes: Long = 256L * 1024 * 1024,

    /**
     * How large an archive's payloads may add up to once inflated.
     *
     * 200 pages of dense ink are a few MB of JSON each, and a notebook may bundle a lecture's recording
     * and a PDF background: 1 GiB is the point past which a document is not a notebook on a tablet any
     * more. The cap is on the **read**, not the compressed file, which is the number that fills memory.
     */
    val maxArchiveBytes: Long = 1024L * 1024 * 1024,

    /**
     * How far an entry may inflate beyond the bytes it is stored in.
     *
     * Deflate's own ceiling is ~1032:1 (a single byte repeated), so a ratio past 1000 is not a document
     * that compresses well — it is a number no compressor produces, and refusing it costs no judgement
     * call about what content "should" compress to. Legitimate entries sit far below: JSON deflates
     * 5–10×, audio or a PDF barely at all, and even a synthetic page of repeated bytes measured 113× in
     * this repository's own fixtures — which a 100× cap wrongly refused.
     */
    val maxExpansionRatio: Int = 1000,

    /**
     * How many elements one page (or chunk) may declare.
     *
     * A busy handwritten page is a few thousand strokes; a page of a printed PDF with per-word text boxes
     * reaches tens of thousands. 50 000 is above anything a person draws, and below the point where
     * decoding a page would take long enough to look like a hang.
     */
    val maxElementsPerPage: Int = 50_000,

    /**
     * How many points one stroke may carry.
     *
     * A ten-minute stroke recorded at 240 Hz is ~144 000 points, so consecutive inking never approaches
     * this; 500 000 is that with room for a high-rate tablet, and it bounds the one array a stroke's
     * telemetry can make unbounded.
     */
    val maxPointsPerStroke: Int = 500_000,

    /**
     * How many bytes of `assets/` payloads one document may pull into memory.
     *
     * Assets are read whole (a picture is decoded from its bytes), so this is the budget that keeps a
     * document with hundreds of bundled pages of audio from filling the heap. Same derivation as
     * [maxArchiveBytes]: a real notebook's assets are a lecture and a handful of pictures.
     */
    val maxAssetBytes: Long = 1024L * 1024 * 1024,

    /**
     * How large the `mimetype` payload may be before it is not a type token at all.
     *
     * The entry holds one short ASCII media type; [MobixFormat.MIMETYPE] is 29 bytes. 256 leaves room for
     * parameters (`; charset=…`) and nothing else, so the one entry a reader touches before it knows
     * anything about the file is still read without trusting its length.
     */
    val maxMimetypeBytes: Long = 256,
) {

    /** Refuse an archive that lists more entries than this policy allows. */
    fun refuseBeyondEntryCount(count: Int) {
        if (count > maxEntries) {
            refuse("this archive carries more than $maxEntries entries — it is not a notebook")
        }
    }

    /** Refuse one entry whose inflated payload is past [maxEntryBytes]. */
    fun refuseBeyondEntryBytes(name: String, bytes: Long) {
        if (bytes > maxEntryBytes) {
            refuse("archive entry \"$name\" is larger than $maxEntryBytes bytes")
        }
    }

    /** Refuse an archive whose inflated payloads add up past [maxArchiveBytes]. */
    fun refuseBeyondArchiveBytes(total: Long) {
        if (total > maxArchiveBytes) {
            refuse("this document expands to more than $maxArchiveBytes bytes")
        }
    }

    /** Refuse an entry that inflates past [maxExpansionRatio] times its stored size. */
    fun refuseBeyondExpansion(name: String, compressed: Long, size: Long) {
        // Unknown sizes (a streamed ZIP reader reports -1) cannot be compared; the per-entry and
        // per-archive caps still bound the allocation. A stored entry has a ratio of 1 by definition.
        if (compressed <= 0 || size <= 0) return
        if (size / compressed > maxExpansionRatio) {
            refuse(
                "archive entry \"$name\" inflates ${size / compressed}× its stored size" +
                    " (the limit is $maxExpansionRatio×)",
            )
        }
    }

    /** Refuse a page (or chunk) that declares more elements than [maxElementsPerPage]. */
    fun refuseBeyondPageElements(elements: Int) {
        if (elements > maxElementsPerPage) {
            refuse("a page declares $elements elements; the limit is $maxElementsPerPage")
        }
    }

    /** Refuse a stroke that carries more points than [maxPointsPerStroke]. */
    fun refuseBeyondStrokePoints(points: Int) {
        if (points > maxPointsPerStroke) {
            refuse("a stroke carries $points points; the limit is $maxPointsPerStroke")
        }
    }

    /** Refuse a document whose assets have added up past [maxAssetBytes]. */
    fun refuseBeyondAssetBytes(total: Long) {
        if (total > maxAssetBytes) {
            refuse("this document's assets add up to more than $maxAssetBytes bytes")
        }
    }

    /**
     * [stream] read whole, refusing as soon as it passes [cap] — read in chunks, so a hostile entry costs
     * the buffered prefix and no more than the cap, never its declared size.
     */
    fun readBounded(stream: InputStream, name: String, cap: Long = maxEntryBytes): ByteArray =
        readBounded(stream, name, cap, newReadBuffer())

    /**
     * [readBounded] recycling [buffer], the chunk it reads through.
     *
     * A caller that reads **many** entries passes one buffer for all of them: an entry count is capped
     * in the tens of thousands, so a fresh 64 KiB chunk per entry would allocate gigabytes to read an
     * archive that is a megabyte (the committed many-entry file measured 1.25 GiB before this).
     */
    internal fun readBounded(stream: InputStream, name: String, cap: Long, buffer: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            total += n
            // Against the cap this read was given, not the entry threshold: an asset is read under the
            // asset budget, and a caller that passes a tighter cap means it.
            if (total > cap) refuse("archive entry \"$name\" is larger than $cap bytes")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * Entry [name] of [source], read under this policy: the expansion ratio first (a file-backed source
     * knows the stored size before anything is inflated), then the bounded read.
     */
    fun read(source: MobixEntrySource, name: String, cap: Long = maxEntryBytes): ByteArray? {
        // Asked from the directory before anything is inflated, and asked as a question: a counting
        // source must not see this as a read of the entry.
        source.storedSizes(name)?.let { refuseBeyondExpansion(name, it.compressed, it.inflated) }
        return source.openEntry(name)?.use { readBounded(it, name, cap) }
    }

    /** A fresh read chunk, for a caller that has no buffer of its own to recycle. */
    internal fun newReadBuffer(): ByteArray = ByteArray(READ_BUFFER_BYTES)

    private fun refuse(why: String): Nothing = throw MobixContainer.MalformedMobixException(why)

    companion object {
        /** What production runs: every threshold at its documented value. */
        val Default = MobixSafetyCaps()

        /** The chunk a bounded read grows by — the same buffer size the container writes with. */
        private const val READ_BUFFER_BYTES = 64 * 1024
    }
}

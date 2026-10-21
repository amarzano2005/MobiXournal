package com.mobixournal.format.mobix

import java.io.OutputStream

/**
 * One entry of a `.mobix` **as the archive already stores it** — the compressed bytes, plus the three
 * ZIP fields a writer has to repeat to put them back untouched (`method`, `crc`, `compressedSize`).
 *
 * This is what makes an incremental save cost the parts that changed instead of the whole document: a
 * repack copies these bytes rather than inflating an entry, decoding its JSON, re-encoding it and
 * re-deflating it ([MobixIncrementalSave]). None of the numbers are a caller's to compute — they are
 * read out of the source archive and only ever forwarded.
 *
 * **A decorator must forward it.** A source that wraps another ([MobixEntrySource]) and answers `null`
 * here does not fail: it silently turns every repack that goes through it back into a full rewrite. The
 * contract is therefore part of [MobixEntrySource] rather than an optional side channel, so the type
 * system asks the question at the place it matters.
 */
interface MobixRawEntry {

    /** The entry's uncompressed size, in bytes. */
    val size: Long

    /** The ZIP compression method the bytes are stored with (stored or deflated). */
    val method: Int

    /** The CRC-32 the ZIP directory records for the **uncompressed** bytes. */
    val crc: Long

    /** The size of the bytes [copyTo] writes — the compressed size. */
    val compressedSize: Long

    /** Write the entry's stored bytes to [sink] exactly as the archive holds them. */
    fun copyTo(sink: OutputStream)
}

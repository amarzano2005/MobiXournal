package com.mobixournal.audio

import java.io.File

/**
 * **Which recording a save may bundle**, given what the recorder is doing.
 *
 * A bundled recording is content-addressed: its `assets/aud_<sha256>.ogg` entry is named by the hash of
 * its bytes and the archive's own asset check re-hashes the entry to prove it. A file the recorder is
 * **still writing** cannot honour that contract — the name is computed from the bytes at one moment and
 * the bytes are copied at a later one, so the save fails its own check with `DIGEST_MISMATCH`. The
 * recording in progress is therefore left out of *this* save: its stroke keeps the sidecar name it
 * replayed from, and the next save (after the recorder has finished the file) bundles it as usual.
 *
 * Every other recording in the store is finished by definition and bundles normally, which is why the
 * rule is about the file being **written now** rather than "a recording is running".
 */
internal object AudioBundleRule {

    /**
     * [file] when it can be bundled for [name], or null while [name] is the recording being written.
     *
     * [current] is the recorder's own file name ([AudioSession.currentFile]) and [recording] whether a take is
     * under way at all. A **paused** take counts as running: the reader can resume mid-save, which would grow the
     * file the archive is copying. Only a stopped recorder has finished the file it names; every other recording
     * in the store is finished by definition and stays bundleable.
     */
    fun bundleable(name: String, current: String?, recording: Boolean, file: File?): File? =
        file?.takeUnless { recording && name == current }
}

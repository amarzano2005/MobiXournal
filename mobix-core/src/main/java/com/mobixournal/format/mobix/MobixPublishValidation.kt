package com.mobixournal.format.mobix

import com.mobixournal.portability.MobixAssetValidator
import java.io.File

/**
 * Prove a staged `.mobix` before it replaces the document on disk.
 *
 * The answer is the one a publish needs (`docs/document-session.md`): the archive opens, its `mimetype`
 * is this format's, its `formatVersion` parses and this build may write it, and every `assets/` entry
 * hashes to the name it carries with every reference resolving. A `File` is validated, never repaired:
 * a refusal means the caller keeps the original and removes the staging file.
 *
 * The suffix is not consulted — a portable copy (`notes.pmobix`) is checked as the `.mobix` it is.
 * What this cannot see is a destination that is not a file; a SAF stream is the Android path's problem.
 */
object MobixPublishValidation {

    /** A staged file that must not replace the document, with the reason a person can act on. */
    class Invalid(message: String) : IllegalStateException(message)

    /**
     * Validate [file] as a document this build may publish, for [appVersion].
     *
     * The document is opened to read its manifest and capabilities, and the asset walk runs on the same
     * file. Nothing is written and the caller keeps ownership of [file].
     */
    fun validate(file: File, appVersion: String) {
        val container = runCatching { MobixContainer.open(file.inputStream().buffered()) }
            .getOrElse { failure ->
                throw Invalid("The written file is not a .mobix archive: ${failure.message}")
            }
        if (container.mimetype != MobixFormat.MIMETYPE) {
            throw Invalid("The written file declares ${container.mimetype}, not ${MobixFormat.MIMETYPE}.")
        }
        val document = runCatching {
            MobixEntrySource.fromFile(file).use { source -> MobixDocument.open(source) }
        }.getOrElse { failure ->
            throw Invalid("The written document could not be reopened: ${failure.message}")
        }
        val verdict = document.verdict(appVersion)
        if (!verdict.mayWrite) {
            throw Invalid("The written document may not be written back: ${verdict.reason}")
        }
        val assets = MobixAssetValidator.validate(file)
        if (!assets.clean) {
            val first = assets.errors.first()
            throw Invalid(
                "The written document failed its asset check (${assets.errors.size} error(s)): " +
                    "${first.rule} ${first.entry} ${first.detail}".trim(),
            )
        }
    }
}

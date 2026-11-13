package com.mobixournal.format.mobix

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Replace a document file in one step, and never leave a truncated one where a whole one was.
 *
 * This is the file half of the publish contract (`docs/document-session.md`): the bytes are written to a
 * staging file **in the destination's own folder**, the finished file is handed to [validate] — the
 * document that reopened, the digests that matched — and only then moved over the target. A write that
 * throws, a file that fails validation and a move the filesystem refuses all leave the target exactly as
 * it was, and remove the staging file.
 *
 * The target may be any name a save owns, including a portable copy (`notes.pmobix`): the suffix is not
 * consulted, because the container is recognised by its `mimetype`, never by its extension.
 *
 * What this does **not** cover is a destination that is not a file — a SAF `content://` stream cannot be
 * replaced atomically by contract, so the Android path verifies its copy and keeps a backup instead
 * (`docs/data-path.md`). The Windows lock-release retry belongs to the desktop's own replace.
 */
object AtomicPublish {

    /** A publish that could not be attempted, or whose bytes were refused, said in words to act on. */
    class Refused(message: String) : IllegalStateException(message)

    /**
     * Write [target] through [write], prove it with [validate], then replace [target] with it.
     *
     * [write] receives the staging file and returns whatever the caller needs from the write; it must
     * write the complete document there, not to [target]. [validate] runs only after the staging file is
     * non-empty, and throwing from it refuses the publish. Both run on the caller's thread.
     */
    fun <T> publish(target: File, validate: (File) -> Unit = {}, write: (File) -> T): T {
        val folder = target.absoluteFile.parentFile
            ?: throw Refused("That destination has no folder to write into.")
        if (!folder.isDirectory && !folder.mkdirs() && !folder.isDirectory) {
            throw Refused("That destination's folder could not be created.")
        }
        val staged = File.createTempFile("mobix-", ".publishing", folder)
        try {
            val answer = write(staged)
            if (!staged.isFile || staged.length() == 0L) {
                throw Refused("The write produced no bytes, so the document was left untouched.")
            }
            validate(staged)
            move(staged, target)
            return answer
        } finally {
            // After a successful move the staging name is gone; after a refusal it is litter.
            staged.delete()
        }
    }

    /** One rename where the volume allows it, a plain replace where it does not. */
    private fun move(staged: File, target: File) {
        try {
            Files.move(
                staged.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

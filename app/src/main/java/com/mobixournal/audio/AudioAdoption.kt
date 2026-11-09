package com.mobixournal.audio

import java.io.File
import java.io.InputStream
import java.nio.file.*

/** Publish only a complete sidecar: a failed/cancelled extraction must preserve the previous file. */
internal fun adoptAudioFile(target: File, source: InputStream) {
    val pending = File.createTempFile("adopt-", ".part", target.parentFile)
    try {
        pending.outputStream().use { out -> source.copyTo(out) }
        try {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally { pending.delete() }
}

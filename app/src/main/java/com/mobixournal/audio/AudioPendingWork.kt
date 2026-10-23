package com.mobixournal.audio

import java.io.File

/**
 * A post-recording job that has to outlive the session that started it.
 *
 * The end of a lecture is exactly when the user closes the app, so anything still running then is
 * lost unless it is written down first: the standalone export and the deferred transcription both
 * leave an entry here when they start and remove it when they finish, and the next launch picks up
 * whatever is left ([com.mobixournal.resumePendingAudioWork]).
 *
 * @property audioName the bare sidecar name the job belongs to (`2026-10-22T09-00-00.ogg`).
 */
data class PendingAudioWork(val kind: Kind, val audioName: String) {

    enum class Kind { EXPORT, TRANSCRIBE }
}

/**
 * The on-disk form of the pending jobs: one `kind|audioName` line each, read and written whole.
 *
 * Nothing here touches Android, so the encoding — including what it does with a line it does not
 * understand — is pinned by a JVM test.
 */
object AudioPendingWork {

    /** The file inside the app's files directory that holds the queue. */
    const val FILE_NAME = "audio-pending.txt"

    private const val SEPARATOR = '|'

    fun file(dir: File): File = File(dir, FILE_NAME)

    /** The jobs in [file]; empty when it does not exist or cannot be read. */
    fun read(file: File): List<PendingAudioWork> {
        if (!file.isFile) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return decode(text)
    }

    /** Encode [jobs] into the queue's text form, one line each. */
    fun encode(jobs: List<PendingAudioWork>): String =
        jobs.joinToString(separator = "\n", postfix = "\n") { "${it.kind.name.lowercase()}$SEPARATOR${it.audioName}" }

    /** Decode the queue's text form, skipping blank and unrecognised lines. */
    fun decode(text: String): List<PendingAudioWork> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val kind = Kind.of(line.substringBefore(SEPARATOR)) ?: return@mapNotNull null
            val name = line.substringAfter(SEPARATOR, "").trim()
            if (name.isEmpty()) null else PendingAudioWork(kind, name)
        }
        .distinct()
        .toList()

    /** Add [job] unless it is queued already — a job already waiting needs no second entry. */
    fun add(file: File, job: PendingAudioWork) {
        val jobs = read(file)
        if (job in jobs) return
        write(file, jobs + job)
    }

    /** Drop [job] — it finished, or it can never finish. */
    fun remove(file: File, job: PendingAudioWork) {
        val jobs = read(file)
        if (job !in jobs) return
        write(file, jobs - job)
    }

    fun write(file: File, jobs: List<PendingAudioWork>) {
        runCatching {
            file.parentFile?.mkdirs()
            if (jobs.isEmpty()) file.delete() else file.writeText(encode(jobs))
        }
    }

    private enum class Kind(val label: String) {
        EXPORT("export"),
        TRANSCRIBE("transcribe"),
        ;

        companion object {
            fun of(label: String): PendingAudioWork.Kind? = when (label.lowercase()) {
                EXPORT.label -> PendingAudioWork.Kind.EXPORT
                TRANSCRIBE.label -> PendingAudioWork.Kind.TRANSCRIBE
                else -> null
            }
        }
    }
}

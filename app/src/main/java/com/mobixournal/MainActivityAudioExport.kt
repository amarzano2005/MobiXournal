package com.mobixournal

import androidx.lifecycle.lifecycleScope
import com.mobixournal.audio.AudioPendingWork
import com.mobixournal.audio.AudioStandaloneExporter
import com.mobixournal.audio.AudioTranscoder
import com.mobixournal.audio.PendingAudioWork
import com.mobixournal.audio.StandaloneAudioTap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The post-recording work that must survive the end of a lecture.
 *
 * The owner's habit is the constraint this file exists for: the lecture ends, the file is saved, the
 * app is closed. Anything that starts at that moment — re-encoding the standalone `.m4a` — is running
 * against a process that is about to go away. Two rules keep it honest:
 *
 *  - **The expensive part happens during the lecture.** [StandaloneAudioTap] encodes the `.m4a` from
 *    the same PCM the recorder captures, so by Stop the only thing left is copying a file.
 *  - **Whatever is left is written down.** [AudioPendingWork] holds the jobs that did not finish, and
 *    [resumePendingAudioWork] picks them up on the next launch instead of losing them with the
 *    process.
 */

/** Where the `.m4a` encoded during a recording waits until the export copies it out. */
private fun MainActivity.standaloneDir(): File = File(filesDir, "audio-standalone").apply { mkdirs() }

/** The `.m4a` that belongs to the recording [name], whether or not it was produced yet. */
private fun MainActivity.standaloneCopyFor(name: String): File =
    File(standaloneDir(), AudioTranscoder.exportedFileName(name))

private fun MainActivity.pendingAudioFile(): File = AudioPendingWork.file(filesDir)

private fun exportJob(name: String) = PendingAudioWork(PendingAudioWork.Kind.EXPORT, name)

/** The recording [name] names inside the app's audio store. */
private fun MainActivity.recordingFor(name: String): File = File(filesDir, "audio/$name")

/**
 * Start encoding the standalone `.m4a` while the microphone is open, when the user asked for a
 * standalone copy. A tap that cannot open (no AAC encoder, no storage) is simply absent, and the
 * export falls back to converting the finished recording.
 */
internal fun MainActivity.beginStandaloneExport(name: String) {
    endStandaloneExport()
    val tap = StandaloneAudioTap(standaloneCopyFor(name))
    if (!tap.start()) return
    standaloneTap = tap
    audio.addPcmListener(tap.listener)
}

/**
 * Unhook the parallel encoder from the microphone and hand it over, without waiting for it to finish.
 *
 * Called the moment the recorder stops: the listener has to go before another chunk can arrive, which
 * is cheap, while finalising the file joins a worker and belongs off the main thread ([exportStandaloneInBackground]).
 */
internal fun MainActivity.detachStandaloneTap(): StandaloneAudioTap? {
    val tap = standaloneTap ?: return null
    standaloneTap = null
    audio.removePcmListener(tap.listener)
    return tap
}

/** Stop the parallel encoder and answer the `.m4a` it finished — null when it has no audio in it. */
internal fun MainActivity.endStandaloneExport(): File? = detachStandaloneTap()?.stop()

/**
 * Send the finished recording to the standalone export **without** making the save wait for it.
 *
 * The job is queued first, so a copy that the closing app interrupts is picked up next launch rather
 * than lost. Finalising [tap] and copying the `.m4a` it produced happen together on a background
 * thread: what is left at this point is a copy, not a conversion.
 */
internal fun MainActivity.exportStandaloneInBackground(recording: File, tap: StandaloneAudioTap?) {
    AudioPendingWork.add(pendingAudioFile(), exportJob(recording.name))
    lifecycleScope.launch(Dispatchers.IO) {
        if (runPendingExport(recording.name, tap?.stop())) {
            withContext(Dispatchers.Main) {
                toast("Standalone audio exported as ${AudioTranscoder.CONTAINER_SUFFIX}")
            }
        }
    }
}

/** Throw away a copy the user turned off mid-recording, so no stray `.m4a` is left behind. */
internal fun MainActivity.discardStandaloneExport(tap: StandaloneAudioTap?) {
    if (tap == null) return
    lifecycleScope.launch(Dispatchers.IO) { tap.stop()?.delete() }
}

/**
 * Copy one queued export into the device's media collection, and answer whether it landed.
 *
 * A job whose recording and pre-encoded copy are both gone is dropped instead of retried forever; a
 * copy that fails for any other reason (a media store that refused it) stays queued for next launch.
 */
private fun MainActivity.runPendingExport(name: String, ready: File? = null): Boolean {
    val prepared = ready ?: standaloneCopyFor(name).takeIf { it.isFile }
    val source = prepared ?: recordingFor(name).takeIf { it.isFile }
    if (source == null) {
        AudioPendingWork.remove(pendingAudioFile(), exportJob(name))
        return false
    }
    val exported = AudioStandaloneExporter.exportStandalone(this, source)
    if (exported) {
        AudioPendingWork.remove(pendingAudioFile(), exportJob(name))
        if (prepared != null) prepared.delete()
    }
    return exported
}

/**
 * Pick up whatever the last session left unfinished — what makes closing the app straight after a
 * lecture safe.
 *
 * Exports run here as ordinary background work: the `.m4a` is already encoded, so what is left is a
 * copy.
 */
internal fun MainActivity.resumePendingAudioWork() {
    val exports = AudioPendingWork.read(pendingAudioFile())
        .map { it.audioName }
    if (exports.isEmpty()) return
    lifecycleScope.launch(Dispatchers.IO) { exports.forEach { runPendingExport(it) } }
}

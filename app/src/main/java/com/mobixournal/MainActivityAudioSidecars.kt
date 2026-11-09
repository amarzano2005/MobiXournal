package com.mobixournal

import androidx.lifecycle.lifecycleScope
import com.mobixournal.audio.AudioReferenceIndex
import com.mobixournal.audio.readAudioPreview
import com.mobixournal.format.mobix.MobixFormat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

/** SAF listing/copying and audio discovery must not run in an open-document callback on main. */
internal fun MainActivity.syncAudioSidecars(export: Boolean) {
    val folder = audioFolder ?: return
    val document = surface?.toDocument() ?: return
    val owner = pane.tabs.active?.id
    val recording = audio.currentFile
    audioWork.activate(owner)
    audioWork.sidecars = lifecycleScope.launch {
        val job = currentCoroutineContext()
        try {
            val message = withContext(Dispatchers.IO) {
              audioWork.sidecarLock.withLock {
                if (!export && !audioWork.owns(owner)) return@withLock null
                val refs = readAudioPreview(document, io.openSessions.stagedOf(document), AudioReferenceIndex(),
                    { name, token, payload -> if (export) audio.register(name, token, payload) }, job::ensureActive)
                val names = refs.entries.filter { item -> export ||
                    (item.bundledEntry == null && !MobixFormat.isAssetEntry(item.reference.filename, MobixFormat.AssetKind.AUDIO))
                }.mapTo(LinkedHashSet()) { it.reference.filename }
                if (recording != null) names.removeAll { com.mobixournal.audio.AudioInkAligner.isMatchingAudio(it, recording) }
                if (export) {
                    audio.exportSidecars(folder, names)
                    val compressed = names.count { !it.endsWith(".wav", ignoreCase = true) }
                    if (compressed > 0) "$compressed recording(s) here are compressed - desktop Xournal++ plays WAV only" else null
                } else {
                    val pulled = audio.importSidecars(folder, names)
                    val missing = names.count { !audio.hasRecording(it) }
                    when { pulled > 0 -> "Loaded $pulled recording(s)"
                        missing > 0 -> "$missing recording(s) referenced but not in the audio folder"
                        else -> null }
                }
              }
            }
            if (pane.tabs.active?.id == owner) { audioTick.value++; message?.let(::toast) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (pane.tabs.active?.id == owner) toast("Audio files could not be synchronized. Try choosing the audio folder again.")
        }
    }
}

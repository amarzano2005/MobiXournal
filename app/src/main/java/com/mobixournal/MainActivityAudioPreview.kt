package com.mobixournal

import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.mobixournal.audio.*
import com.mobixournal.format.model.Document
import kotlinx.coroutines.*
import com.mobixournal.render.scrollToContentBounds

/** Never let Compose's structural key comparison enumerate a lazy document. */
internal class AudioIdentity(private val value: Any?) {
    override fun equals(other: Any?) = other is AudioIdentity && value === other.value
    override fun hashCode() = System.identityHashCode(value)
}

internal class AudioPreview(val owner: String?, val document: Document?, val clip: String?,
    val page: Int?, val waveform: List<Float>)

@Composable internal fun MainActivity.rememberAudioPreview(visible: Boolean): AudioPreview {
    val tab = pane.tabs.active
    val document = surface?.toDocument()?.takeIf { tab?.hydrated == true && !tab.opening }
    val page = surface?.ink?.currentPage ?: 0
    val playing = audio.nowPlaying
    val owner = tab?.id
    DisposableEffect(owner) { audioWork.activate(owner); onDispose { audioWork.cancelOwner(owner) } }
    val result by produceState(AudioPreview(null, null, null, null, emptyList()),
        owner, AudioIdentity(document), page, playing, audio.isRecording, visible) {
        if (document == null || audio.isRecording) {
            value = AudioPreview(owner, document, null, null, emptyList())
        } else {
            val job = currentCoroutineContext()
            try {
                value = withContext(Dispatchers.IO) {
                    // A clip that is already **on** is the clip, whatever reading the page says: the
                    // name comes from the session, and a page that cannot be read at this instant (a
                    // reader between two views, a tab closing) must not take the waveform of a
                    // recording that is playing away with it.
                    if (playing != null) {
                        AudioPreview(owner, document, playing, page, emptyList())
                    } else {
                        // The page in front is the only page this reads, and it is the page the canvas
                        // is already holding: no archive, no index, no second reader. Reading it is also
                        // what registers the recordings the page bundles.
                        val refs = runCatching { audioReferencesOn(document, page, job::ensureActive) }
                            .getOrNull().orEmpty()
                        // The **name** costs nothing — a registered recording is a map lookup. Only a
                        // player that is actually showing may *resolve* one, because resolving adopts a
                        // bundled payload into the store, and copying a lecture's audio behind a closed
                        // player is what made opening a file with audio take seconds.
                        val clip = refs.firstOrNull { audio.hasRecording(it.filename) }?.filename
                            ?: if (visible) refs.firstOrNull { name ->
                                job.ensureActive(); audio.recordingFile(name.filename) != null
                            }?.filename else null
                        AudioPreview(owner, document, clip, clip?.let { page }, emptyList())
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Missing/corrupt audio leaves a usable editor, with no stale preview. */ }
        }
    }
    val metadata = result.takeIf { it.owner == owner && document != null && !audio.isRecording }
        ?: AudioPreview(owner, document, null, null, emptyList())
    // The clip the bars belong to is the one that is **on**, read from the session, and only failing that
    // the page's own candidate. The two paths can spell one recording differently — a sidecar name on the
    // stroke, the archive's asset entry in the store — and withholding the bars when they disagree is
    // exactly how the waveform came to be missing on documents whose audio was bundled elsewhere.
    val waveformClip = playing ?: metadata.clip
    val shown = visible || playing != null
    val waveform by produceState<List<Float>>(emptyList(), owner, waveformClip, shown) {
        value = emptyList()
        val clip = waveformClip
        if (shown && clip != null) {
            val job = currentCoroutineContext()
            try {
                value = withContext(Dispatchers.IO) {
                    audio.recordingFile(clip)?.let {
                        AudioWaveformExtractor.extract(it, checkCancelled = job::ensureActive)
                    }.orEmpty()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* A preview failure must not hide an available recording. */ }
        }
    }
    return AudioPreview(owner, document, metadata.clip, metadata.page, waveform)
}

/** User-triggered discovery also stays off main; a result belongs to the tab that requested it. */
internal fun MainActivity.withAudioCandidate(preparePlayer: Boolean = false, deep: Boolean = false,
    action: (AudioRef?) -> Unit) {
    val tab = pane.tabs.active ?: return action(null)
    if (!tab.hydrated || tab.opening) return action(null)
    val document = surface?.toDocument() ?: return action(null)
    val page = surface?.ink?.currentPage ?: tab.page
    val fallback = tabAudioRefs[tab.id]
    audioWork.activate(tab.id)
    audioWork.request?.cancel()
    audioWork.seek?.cancel()
    audioWork.request = lifecycleScope.launch {
        val job = currentCoroutineContext()
        val ref = try {
            withContext(Dispatchers.IO) {
                val playable = { ref: AudioRef -> job.ensureActive(); audio.recordingFile(ref.filename) != null }
                audioReferencesOn(document, page, job::ensureActive).firstOrNull(playable)
                    ?: fallback?.takeIf(playable)
                    // Only the explicit "play the document's audio" gesture looks past the page in
                    // front, and only it pays for a walk of the other pages.
                    ?: if (deep) firstPlayableAudio(document, job::ensureActive) else null
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        if (pane.tabs.active?.id == tab.id && pane.tabs.active?.opening == false) {
            val ready = ref == null || !preparePlayer || audio.preparePlayback(ref)
            if (pane.tabs.active?.id != tab.id || pane.tabs.active?.opening != false || !audioWork.owns(tab.id)) return@launch
            if (!ready) {
                toast("Recording could not be played. Try another recording.")
            } else action(ref?.copy(startMs = 0))
        }
    }
}

/** The recordings the page at [index] replays from — read through the document the canvas paints. */
private fun MainActivity.audioReferencesOn(document: Document, index: Int,
    checkCancelled: () -> Unit = {}): List<AudioRef> {
    checkCancelled()
    val page = document.pages.getOrNull(index) ?: return emptyList()
    return audioReferences(page)
}

/**
 * The first recording in [document] that this session can actually play, pages walked in order.
 *
 * Only the gesture that asks for *the* recording calls this; the chrome never does, because a walk of
 * every page on a page-by-page `.mobix` is exactly the cost that made an audio notebook unusable.
 */
private fun MainActivity.firstPlayableAudio(document: Document, checkCancelled: () -> Unit): AudioRef? {
    for (index in 0 until document.pages.size) {
        checkCancelled()
        for (ref in audioReferences(document.pages[index])) {
            checkCancelled()
            if (audio.recordingFile(ref.filename) != null) return ref
        }
    }
    return null
}

internal fun MainActivity.withPreparedAudio(ref: AudioRef, action: () -> Unit) {
    // **Every clip starts at 0:00.** A stroke's own offset records when it was drawn — telemetry about
    // the ink — and is not a place the user asked playback to begin (`docs/audio.md`, mode 2).
    val fromStart = ref.copy(startMs = 0)
    val owner = pane.tabs.active?.id
    audioWork.activate(owner)
    audioWork.request?.cancel()
    audioWork.seek?.cancel()
    audioWork.request = lifecycleScope.launch {
        try {
            withContext(Dispatchers.IO) { audio.recordingFile(fromStart.filename) }
            if (pane.tabs.active?.id == owner) {
                val ready = audio.preparePlayback(fromStart)
                if (pane.tabs.active?.id != owner || pane.tabs.active?.opening != false || !audioWork.owns(owner)) return@launch
                if (ready) action()
                else toast("Recording could not be played. Try opening the document again.")
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (pane.tabs.active?.id == owner) toast("Recording could not be opened. Try opening the document again.") }
    }
}

internal fun MainActivity.requestAudioScroll(ms: Int) {
    val view = surface ?: return
    val document = view.toDocument()
    val clip = audio.nowPlaying ?: return
    val owner = pane.tabs.active?.id
    audioWork.activate(owner)
    audioWork.seek?.cancel()
    audioWork.seek = lifecycleScope.launch {
        val job = currentCoroutineContext()
        val hit = try {
            // The clip's timeline is built **once** per document and clip, and every later step of the
            // same drag answers from it: a step is a filter over a few hundred records, not a walk of the
            // notebook. The walk is deliberately not cancelled mid-way — one that finishes is what makes
            // the next ninety steps free, and it holds one page at a time.
            withContext(Dispatchers.IO) {
                AudioSeekIndex.timelineOf(document, clip) {
                    withAudioPreviewPages(document, io.openSessions.stagedOf(document), {}) { safe ->
                        AudioInkAligner.timeline(safe, clip)
                    }
                }
                job.ensureActive()
                AudioSeekIndex.at(ms, AudioInkAligner.DEFAULT_TIMESTAMP_TOLERANCE_MS).firstOrNull()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        if (hit != null && pane.tabs.active?.id == owner && surface === view &&
            view.toDocument() === document && audio.nowPlaying == clip) view.scrollToContentBounds(hit.first, hit.second)
    }
}

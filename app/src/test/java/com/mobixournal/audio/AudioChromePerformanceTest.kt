package com.mobixournal.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The chrome's preview is what a `.mobix` of a lecture is judged by: it must know the clip on the page
 * in front **without** walking the notebook, and it must never pay for the waveform before it is shown.
 *
 * Read off the source, because the failure this pins is structural rather than a wrong value: a
 * discovery step that goes back to "open the archive, map page ids, inflate every page" cannot be
 * caught by a unit test that only runs one page, and the cost only shows on a real 200-page notebook.
 */
class AudioChromePerformanceTest {

    private val source: String = listOf(File("").absoluteFile, File("..").absoluteFile)
        .map { java.io.File(it, "app/src/main/java/com/mobixournal/MainActivityAudioPreview.kt") }.first { it.isFile }
        .readText()
        .substringBefore("/** User-triggered")

    @Test
    fun thePreviewNeverWalksTheNotebookToFindItsClip() {
        assertFalse("Hidden controls must still discover recordings", "if (visible && document" in source)
        assertFalse("Discovery must stay on the page in front",
            "for (index in 0 until document.pages.size)" in source)
        assertFalse("Discovery must not open a second reader over the archive",
            "MobixPagedReader" in source || "withAudioPreviewPages" in source)
    }

    @Test
    fun thePreviewResolvesTheClipItWillPlay() {
        // The clip is found by asking the audio store whether it resolves — a registration-only check
        // cannot see a bundled recording that nothing has adopted yet, which is a `.mobix` opened on a
        // page whose audio has never played.
        assertTrue("Discovery asks the audio store, not a registry of names",
            "audio.recordingFile(name.filename)" in source)
    }

    @Test
    fun waveformDecodingNeverDelaysDiscovery() {
        assertFalse("Waveform decoding must not delay discovery", "AudioWaveformExtractor.extract" in source.substringBefore("val waveform by produceState"))
        assertTrue("Document edits must not restart waveform decoding, and the bars follow the clip that is on",
            "emptyList(), owner, waveformClip, shown" in source)
    }
}

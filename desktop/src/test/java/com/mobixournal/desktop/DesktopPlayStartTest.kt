package com.mobixournal.desktop

import com.mobixournal.audio.withAudio
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.AudioRef
import com.mobixournal.format.model.withPage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **Where the pill's Play starts a take**: at its own beginning.
 *
 * A stroke's `ts` links that stroke to the sound — it is not where the recording begins. The page's last
 * element answered with its own stamp, so Play on a document that had just been recorded dropped the first
 * seconds of the take (and a fresh document, recorded over several strokes, began in the middle of it).
 * The take's head is 0, and the karaoke then has the whole writing to fill in as the sound arrives.
 */
class DesktopPlayStartTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val recording = "take.wav"

    @Test
    fun `play starts the recording at its head, not at the last stroke's stamp`() {
        val tab = DesktopTab(recordedDocument())

        val ref = DesktopAudioSession.firstRef(tab)

        assertEquals("the take the page names", recording, ref?.filename)
        assertEquals("from the beginning of the take", 0, ref?.startMs ?: -1)
    }

    /** A one-page document whose strokes were written 0 s and 4.2 s into the same take. */
    private fun recordedDocument(): DesktopDocument {
        val file = File(temp.root, "recorded.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
        val opened = DocumentLoader.load(file)
        val model = opened.model
        val page = model.pages[0]
        val layer = page.layers.first()
        val early = SyntheticNotebook.stroke(40.0, "stk_early").withAudio(AudioRef(recording, 0))
        val late = SyntheticNotebook.stroke(90.0, "stk_late").withAudio(AudioRef(recording, 4_200))
        val edited = page.copy(
            layers = listOf(layer.copy(elements = layer.elements + early + late)) + page.layers.drop(1),
        )
        return DesktopDocument(
            file = opened.file,
            source = opened.source,
            model = model.withPage(0, edited),
            container = opened.container,
            backdrop = opened.backdrop,
            shape = opened.shape,
        )
    }
}

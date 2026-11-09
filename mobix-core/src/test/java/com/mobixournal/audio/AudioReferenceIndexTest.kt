package com.mobixournal.audio

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.*
import org.junit.Assert.*
import org.junit.Test

class AudioReferenceIndexTest {
    private fun page(name: String, ms: Int = 123) = Page(100.0, 100.0, Background.Solid(-1, "plain"),
        listOf(Layer(listOf(Stroke(Tool.PEN, 0, "round", listOf(StrokePoint(1.0, 1.0, 1.0)),
            uniformWidth = true).withAudio(AudioRef(name, ms))))))

    @Test fun playbackReadsNoPagesAfterIndexingAndEditsAndUndoReadOnlyTheirOverlay() {
        var reads = 0
        val base = object : PageSource {
            override val pageCount = 200
            override fun pageId(index: Int) = "p$index"
            override fun page(index: Int): Page { reads++; return page("clip$index.wav") }
        }.asPageList()
        val original = Document(pages = base)
        val index = AudioReferenceIndex()
        val first = index.read(original)
        assertEquals(200, reads)
        repeat(120) { assertEquals(7, first.firstPage("folder/CLIP7.WAV")); index.read(original) }
        assertEquals("UI ticks must not decode the five-page LRU again", 200, reads)
        val edited = original.withPage(7, page("new.wav"))
        assertEquals(7, index.read(edited).firstPage("new.wav"))
        assertEquals(200, reads)
        assertNull(index.read(original).firstPage("new.wav"))
        assertEquals(201, reads)
    }

    @Test fun candidateUsesTheCurrentPageOffsetButDocumentFallbackStartsAtZero() {
        val refs = AudioReferenceIndex().read(Document(pages = listOf(page("a.wav", 500), page("b.wav", 700))))
        assertEquals(AudioRef("b.wav", 700), refs.candidate(1) { true })
        assertEquals(AudioRef("a.wav", 0), refs.candidate(9) { true })
        assertNull(refs.candidate(1) { false })
    }

    @Test fun aFocusedReadInflatesOnlyThePagesItNamesAndTheRestStaysOutOfIt() {
        var reads = 0
        val base = object : PageSource {
            override val pageCount = 200
            override fun pageId(index: Int) = "p$index"
            override fun page(index: Int): Page { reads++; return page("clip$index.wav") }
        }.asPageList()
        val document = Document(pages = base)
        val index = AudioReferenceIndex()
        val focused = index.read(document, only = listOf(5))
        assertEquals("a page turn must not inflate the notebook", 1, reads)
        assertEquals(setOf(5), focused.entries.map { it.page }.toSet())
        assertEquals(AudioRef("clip5.wav", 123), focused.candidate(5) { true })
        index.read(document, only = listOf(5, 6))
        assertEquals(2, reads)
        assertEquals("a document-wide answer is what a full read is for", 200, index.read(document).entries.size)
        assertEquals(200, reads)
    }

    @Test fun aPagesOwnReferencesAreListedOncePerFileInStrokeOrder() {
        val first = AudioRef("first.wav", 10)
        val later = AudioRef("first.wav", 900)
        val second = AudioRef("second.wav", 40)
        fun stroke(x: Double, ref: AudioRef?): Stroke {
            val plain = Stroke(Tool.PEN, 0, "round", listOf(StrokePoint(x, x, 1.0)), uniformWidth = true)
            return if (ref == null) plain else plain.withAudio(ref)
        }
        val layers = listOf(Layer(listOf(stroke(1.0, null), stroke(2.0, first), stroke(3.0, later), stroke(4.0, second))))
        assertEquals(listOf(first, second), audioReferences(Page(100.0, 100.0, Background.Solid(-1, "plain"), layers)))
        assertTrue("a page with no recording names none",
            audioReferences(Page(100.0, 100.0, Background.Solid(-1, "plain"), listOf(Layer(emptyList())))).isEmpty())
    }

    @Test fun cancelledScansCanResumeWithoutPublishingAnIncompleteIndex() {
        val document = Document(pages = List(10) { page("$it.wav") })
        val index = AudioReferenceIndex()
        var checks = 0
        try { index.read(document, checkCancelled = { if (++checks == 4) throw java.util.concurrent.CancellationException() }); fail() }
        catch (_: java.util.concurrent.CancellationException) { }
        assertEquals(10, index.read(document).entries.size)
    }
}

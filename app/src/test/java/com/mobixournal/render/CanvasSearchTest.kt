package com.mobixournal.render

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The canvas's search as an object: what a query answers, where it points, and what happens to an index
 * that a newer document or a cancel has made stale.
 *
 * Both executors are direct here, so "in the background" and "posted to the main thread" are the same
 * synchronous step and the generation guard can be checked by hand: a deferred queue stands in for the
 * real worker where cancellation has to be observed mid-job.
 */
class CanvasSearchTest {

    /** Runs a task where it stands — the main thread, or the worker, in a test. */
    private class Direct : Executor {
        override fun execute(command: Runnable) = command.run()
    }

    /** Holds tasks until the test releases them, so a job can be cancelled while it is "running". */
    private class Queue : Executor {
        private val tasks = ArrayDeque<Runnable>()

        /** How many jobs have been handed to the worker — the count a test asserts on. */
        var runs = 0
            private set

        override fun execute(command: Runnable) {
            tasks += command
            runs++
        }

        fun release() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }

    private val typed = TextElement("Sans", 10.0, 20.0, 30.0, 0, "Alpha beta\nalpha")
    private var document = document(typed)
    private val pdf: PdfTextIndex? = null
    private val pushes = mutableListOf<SearchStatus>()
    private val revealed = mutableListOf<SearchHit>()
    private var repaints = 0

    private val worker = Queue()
    private val search = CanvasSearch(
        document = { document },
        pdfText = { pdf },
        onChanged = { pushes += it },
        onIndexing = { _, _ -> },
        onRepaint = { repaints++ },
        reveal = { revealed += it },
        background = worker,
        main = Direct(),
        queries = Direct(),
    )

    @Test
    fun `a query finds the typed text and stands on the first hit`() {
        val status = search.setQuery("alpha")

        assertEquals(2, search.hits.size)
        assertEquals(0, search.current)
        assertEquals(SearchStatus(current = 1, total = 2), status)
        assertEquals("the top bar is told, once per query", status, pushes.last())
        assertTrue(repaints >= 1)
    }

    @Test
    fun `next and previous wrap around the hits`() {
        search.setQuery("alpha")

        search.next()
        assertEquals(1, search.current)
        search.next()
        assertEquals("a next from the last hit wraps to the first", 0, search.current)
        search.previous()
        assertEquals(1, search.current)
        search.previous()
        assertEquals(0, search.current)
        assertEquals("every step reveals its hit", 4, revealed.size)
    }

    @Test
    fun `a hit is handed to the canvas to reveal`() {
        search.setQuery("beta")
        search.next()

        assertEquals(1, revealed.size)
        assertEquals(0, revealed.single().pageIndex)
    }

    @Test
    fun `an empty query clears the highlights without dropping the index`() {
        search.setQuery("alpha")
        worker.release()

        val cleared = search.setQuery("")

        assertEquals(0, cleared.total)
        assertEquals(0, cleared.current)
        assertTrue(search.hits.isEmpty())
        assertEquals(-1, search.current)
        assertEquals("a clear is not a reason to re-index", 1, worker.runs)
    }

    @Test
    fun `an edit voids the index and requeries, unless the gesture is still running`() {
        search.setQuery("alpha")
        worker.release()
        val builds = worker.runs

        search.edited(midGesture = true)
        assertEquals("a live move keeps the highlights it has", 2, search.hits.size)
        assertEquals("and starts no new index", builds, worker.runs)

        search.edited(midGesture = false)
        assertEquals(2, search.hits.size)
        assertEquals("the document moved on, so its index must", builds + 1, worker.runs)
    }

    @Test
    fun `a superseded index is never installed`() {
        document = document(typed, stroke(0.0, 0.0, 40.0, 0.0))
        val pending = Queue()
        val stale = CanvasSearch(
            document = { document },
            pdfText = { null },
            background = pending,
            main = Direct(),
            queries = Direct(),
        )

        stale.ensureIndex()
        assertEquals("a job is in flight", 1, pending.runs)

        stale.cancel()
        pending.release()

        assertNull("its result describes a document we no longer have", stale.handwriting)
    }

    @Test
    fun `a reload drops the hits and indexes the new document`() {
        search.setQuery("alpha")
        worker.release()
        val builds = worker.runs

        document = document(TextElement("Sans", 10.0, 20.0, 30.0, 0, "alpha again"))
        search.reload()

        assertTrue("the hits of the old document are gone", search.hits.isEmpty())
        assertEquals(builds + 1, worker.runs)

        worker.release()
        assertEquals("and the query is answered against the new one", 1, search.hits.size)
    }

    @Test
    fun `a document with nothing to recognise is ready without a run`() {
        document = document()

        assertTrue(search.isReady())
        assertEquals(0, search.setQuery("anything").total)
    }

    private fun stroke(vararg coords: Double): Stroke {
        require(coords.size % 2 == 0)
        return Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = null,
            points = (coords.indices step 2).map { StrokePoint(coords[it], coords[it + 1], 1.5) },
            uniformWidth = true,
        )
    }

    private fun document(vararg elements: Element): Document =
        Document(pages = listOf(blankPage().copy(layers = listOf(Layer(elements.toList())))))
    @Test
    fun `queued older queries and clear cannot publish stale hits`() {
        val queries = Queue()
        val deferred = CanvasSearch({ document }, { pdf }, background = worker, main = Direct(), queries = queries)
        deferred.setQuery("alpha")
        deferred.setQuery("beta")
        queries.release()
        assertEquals(1, deferred.hits.size)
        assertEquals("beta", deferred.query)
        deferred.setQuery("alpha")
        deferred.clear()
        queries.release()
        assertTrue(deferred.hits.isEmpty())
    }
}

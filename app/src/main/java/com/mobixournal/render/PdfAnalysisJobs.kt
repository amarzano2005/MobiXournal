package com.mobixournal.render

import java.io.File
import java.util.concurrent.Executors

/**
 * **PDF text and outline extraction, one job per source revision.**
 *
 * The two derived layers of an imported PDF — its positioned words and its Contents outline — are read
 * off the UI thread. Doing that naively (`Thread { … }.start()` per request) has three costs that grow
 * with the feature: a document mirrored into the second pane asked twice for the same bytes and paid
 * for two full parses; a reader flipping between tabs could stack up walks over files that had already
 * been replaced; and nothing bounded how many ran at once on a tablet with one big core or two.
 *
 * So requests are keyed by the **source revision** — path, length and modification time — and:
 *
 *  - a request for a walk already running **joins** it instead of starting a second one, and every
 *    listener is answered when it finishes;
 *  - the work runs on a pool of [DEFAULT_WORKERS] threads, so a burst of opens queues rather than
 *    spawning a thread each;
 *  - a merge or a re-import writes new bytes, which changes the key, so its answer can never come from
 *    the job that was reading the old file.
 *
 * What a caller does with the answer is its own business: the app drops a result whose canvas now shows
 * a different file, which is how a tab switch discards a walk it no longer needs.
 */
internal class PdfAnalysisJobs(
    /** The reading of one file's words — injected so a test can count the walks. */
    private val readText: (File) -> PdfTextIndex = { PdfTextExtractor().extract(it) },
    /** The reading of one file's outline — the same walk's other answer. */
    private val readOutline: (File) -> PdfOutline = { PdfOutlineExtractor().extract(it) },
    workers: Int = DEFAULT_WORKERS,
) {

    /** What one walk produced, under the [key] of the revision it read. */
    class Result(val key: String, val text: PdfTextIndex, val outline: PdfOutline)

    /** One in-flight walk and everyone waiting on it. */
    private class Job {
        val listeners = ArrayList<(Result) -> Unit>()
    }

    private val running = HashMap<String, Job>()

    private val pool = Executors.newFixedThreadPool(workers) { task ->
        // A daemon thread: a walk that outlives the activity must not keep the process alive.
        Thread(task, "pdf-analysis").apply { isDaemon = true }
    }

    /**
     * Answer [file]'s text and outline to [onDone], on the worker thread.
     *
     * [text] and [outline] are the halves already known (from another view of the same file, or from a
     * previous open); only the missing halves are read, which is what makes a partially cached file cost
     * one walk rather than two. A failure answers empty layers rather than nothing, so a caller's
     * "indexing" state always ends.
     */
    fun request(
        file: File,
        text: PdfTextIndex? = null,
        outline: PdfOutline? = null,
        onDone: (Result) -> Unit,
    ) {
        val key = keyOf(file)
        synchronized(running) {
            val existing = running[key]
            if (existing != null) {
                existing.listeners += onDone
                return
            }
            running[key] = Job().also { it.listeners += onDone }
        }
        pool.execute {
            val result = runCatching { Result(key, text ?: readText(file), outline ?: readOutline(file)) }
                .getOrElse { Result(key, PdfTextIndex(emptyList()), PdfOutline.EMPTY) }
            val listeners = synchronized(running) { running.remove(key)?.listeners.orEmpty() }
            listeners.forEach { listener -> listener(result) }
        }
    }

    /** The revision [file] is at — what makes a walk's answer belong to these bytes and no others. */
    private fun keyOf(file: File): String =
        "${file.absolutePath}|${file.length()}|${file.lastModified()}"

    private companion object {
        /**
         * Walks at once. Two, not one: a 200-page scan should not hold the second pane's document
         * behind it, and two parses are what a tablet can stream without competing with the canvas.
         */
        const val DEFAULT_WORKERS = 2
    }
}

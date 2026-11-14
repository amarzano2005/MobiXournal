package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * **One walk per source revision**, and never more running than the pool has threads.
 *
 * These are the costs a mirrored document used to pay twice and a burst of opens could pay all at once:
 * a request for a file already being read must join that read, a second file must wait for a free worker,
 * and new bytes must never be answered from the walk that was reading the old ones. None of it is visible
 * in the words the canvas ends up with, so each claim is counted here instead.
 */
class PdfAnalysisJobsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(name: String) = File(temp.root, name).apply { writeText("%PDF-1.7 test") }

    private fun emptyText(): PdfTextIndex = PdfTextIndex(emptyList())

    @Test
    fun twoRequestsForOneFileShareOneWalk() {
        val walked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val walks = AtomicInteger()
        val answered = CountDownLatch(2)
        val jobs = PdfAnalysisJobs(
            readText = {
                walks.incrementAndGet()
                walked.countDown()
                release.await(5, TimeUnit.SECONDS)
                emptyText()
            },
            readOutline = { PdfOutline.EMPTY },
        )
        val source = file("shared.pdf")

        jobs.request(source) {
            assertEquals("the answer belongs to these bytes", source.absolutePath, it.key.substringBefore('|'))
            answered.countDown()
        }
        // The first walk is inside the stripper now; the second view of the same file arrives while it runs.
        assertTrue("the walk started", walked.await(5, TimeUnit.SECONDS))
        jobs.request(source) { answered.countDown() }
        release.countDown()

        assertTrue("both callers were answered", answered.await(5, TimeUnit.SECONDS))
        assertEquals("one walk, not two", 1, walks.get())
    }

    @Test
    fun aSecondFileWaitsForAFreeWorkerInsteadOfAddingOne() {
        val inFlight = AtomicInteger()
        val most = AtomicInteger()
        val answered = CountDownLatch(2)
        val jobs = PdfAnalysisJobs(
            readText = {
                val now = inFlight.incrementAndGet()
                most.accumulateAndGet(now) { a, b -> maxOf(a, b) }
                try {
                    // Long enough for the second file to be submitted while this one holds the only worker.
                    Thread.sleep(60)
                    emptyText()
                } finally {
                    inFlight.decrementAndGet()
                }
            },
            readOutline = { PdfOutline.EMPTY },
            workers = 1,
        )

        jobs.request(file("first.pdf")) { answered.countDown() }
        jobs.request(file("second.pdf")) { answered.countDown() }

        assertTrue("both were answered", answered.await(10, TimeUnit.SECONDS))
        assertEquals("one walk at a time on one worker", 1, most.get())
    }

    @Test
    fun rewrittenBytesAreANewRevisionAndAreWalkedAgain() {
        val walks = AtomicInteger()
        val jobs = PdfAnalysisJobs(
            readText = { walks.incrementAndGet(); emptyText() },
            readOutline = { PdfOutline.EMPTY },
        )
        val source = file("rewritten.pdf")

        val first = CountDownLatch(1)
        jobs.request(source) { first.countDown() }
        assertTrue(first.await(5, TimeUnit.SECONDS))

        // A merge or a re-import writes new bytes: the length changes, so the key does too.
        source.appendText("more")
        val second = CountDownLatch(1)
        jobs.request(source) { second.countDown() }
        assertTrue(second.await(5, TimeUnit.SECONDS))

        assertEquals("the old bytes' answer was not reused", 2, walks.get())
    }

    @Test
    fun aHalfAlreadyKnownIsNotWalkedAgain() {
        val outlines = AtomicInteger()
        val jobs = PdfAnalysisJobs(
            readText = { emptyText() },
            readOutline = { outlines.incrementAndGet(); PdfOutline.EMPTY },
        )
        val answered = CountDownLatch(1)

        jobs.request(file("half.pdf"), text = emptyText()) { answered.countDown() }

        assertTrue(answered.await(5, TimeUnit.SECONDS))
        assertEquals("the cached text was used and only the outline read", 1, outlines.get())
    }
}

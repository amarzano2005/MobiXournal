package com.mobixournal.concurrent

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class LatestWriteQueueTest {
    @Test fun runningWriteKeepsOnlyTheNewestPendingCheckpoint() {
        val executor = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val writes = java.util.Collections.synchronizedList(mutableListOf<String>())
        val queue = LatestWriteQueue<String>(executor) {
            if (it == "a") { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
            writes += it
            true
        }
        try {
            queue.submit("a")
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            queue.submit("b"); queue.submit("c")
            assertFalse(queue.awaitLatest(1))
            release.countDown()
            assertTrue(queue.awaitLatest(5000))
            assertEquals(listOf("a", "c"), writes)
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun failedAttemptIsNotDurableAndTheNextRequestCanRetry() {
        val tasks = java.util.ArrayDeque<Runnable>()
        var success = false
        val queue = LatestWriteQueue<Int>({ tasks.add(it) }) { success }
        queue.submit(1); tasks.remove().run()
        assertFalse(queue.awaitLatest(0))
        success = true
        queue.submit(1); tasks.remove().run()
        assertTrue(queue.awaitLatest(0))
    }
}

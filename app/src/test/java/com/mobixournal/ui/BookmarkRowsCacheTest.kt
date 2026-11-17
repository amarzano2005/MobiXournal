package com.mobixournal.ui

import com.mobixournal.format.mobix.PageSize
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.*
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class BookmarkRowsCacheTest {
    @Test fun cancellingAClosedPanelDoesNotPublishItsOldResult() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val page = Page(595.0, 842.0, Background.Pdf("book.pdf", 0, "absolute"), listOf(Layer(emptyList())))
        val pages = object : AbstractList<Page>() {
            override val size = 1
            override fun get(index: Int): Page { entered.countDown(); release.await(); return page }
        }
        val tree = OutlineTree(nodes = listOf(OutlineNode("bkm_a", "Chapter", OutlineTarget.Sheet(0))))
        val cache = BookmarkRowsCache()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            cache.load(pages, tree, null)
        }
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        job.cancel()
        release.countDown()
        job.join()
        assertNull(cache.cached(pages, tree, null))
    }
    @Test fun identityTargetsUseMetadataAndReopeningReusesTheProjection() = runBlocking {
        var ids = 0
        val source = object : PageSource {
            override val pageCount = 2000
            override fun pageId(index: Int): String { ids++; return "pg_$index" }
            override fun pageSize(index: Int) = PageSize(595.0, 842.0)
            override fun page(index: Int): Page = error("Bookmark ids must not decode pages")
        }
        val pages = source.asPageList()
        val tree = OutlineTree(nodes = (0..1999).map { OutlineNode("bkm_$it", "Row $it", OutlineTarget.Page("pg_$it")) })
        val cache = BookmarkRowsCache()
        val rows = cache.load(pages, tree, null)
        assertEquals(2000, ids)
        assertEquals(1999, rows.last().page)
        assertSame(rows, cache.load(pages, tree, null))
        assertEquals(2000, ids)
    }

    @Test fun failedReadsAreNotCachedAndRetryCanSucceedOnAWorker() = runBlocking {
        val caller = Thread.currentThread()
        var failed = true
        val page = Page(595.0, 842.0, Background.Pdf("book.pdf", 0, "absolute"), listOf(Layer(emptyList())))
        val pages = object : AbstractList<Page>() {
            override val size = 1
            override fun get(index: Int): Page {
                assertNotSame(caller, Thread.currentThread())
                if (failed) throw IOException("Unreadable page")
                return page
            }
        }
        val tree = OutlineTree(nodes = listOf(OutlineNode("bkm_a", "Chapter", OutlineTarget.Sheet(0))))
        val cache = BookmarkRowsCache()
        try { cache.load(pages, tree, null); fail("Must report failure") } catch (_: IOException) { }
        assertNull(cache.cached(pages, tree, null))
        failed = false
        assertEquals(0, cache.load(pages, tree, null).single().page)
    }
}

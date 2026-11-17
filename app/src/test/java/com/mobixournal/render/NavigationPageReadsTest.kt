package com.mobixournal.render

import com.mobixournal.format.mobix.PageSize
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.*
import org.junit.Assert.*
import org.junit.Test

class NavigationPageReadsTest {
    private fun paper() = Page(595.0, 842.0, Background.Solid(-1, "plain"), listOf(Layer(emptyList())), id = "pg_new")

    @Test fun canvasMetadataDoesNotDecodeLazyPagesEvenAfterAnEditOrInsertion() {
        var reads = 0
        val source = object : PageSource {
            override val pageCount = 2000
            override fun pageId(index: Int) = "pg_$index"
            override fun pageSize(index: Int) = PageSize(595.0, 842.0)
            override fun page(index: Int): Page { reads++; error("Metadata must not decode page $index") }
        }
        var pages = source.asPageList()
        val canvas = canvasPageSource { pages }
        fun check() { repeat(canvas.pageCount) { assertNotNull(canvas.pageId(it)); assertEquals(595.0, canvas.pageSize(it).widthPt, 0.0) } }
        check()
        pages = EditedPages.of(pages, 700, paper())
        check()
        pages = SplicedPages(pages, 5, paper())
        check()
        pages = SplicedPages(pages, 3, null)
        check()
        assertEquals(0, reads)
    }
}

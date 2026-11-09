package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.*
import org.junit.Assert.*
import org.junit.Test

class PageTextIndexTest {
    @Test fun repeatedQueriesReadNoPagesAndAnEditReadsOnlyItsPage() {
        var reads = 0
        val pages = List(20) { Page(100.0, 100.0, Background.Solid(-1, "plain"),
            listOf(Layer(listOf(TextElement("Sans", 10.0, 1.0, 2.0, 0, "alpha beta"))))) }
        val source = object : PageSource {
            override val pageCount = pages.size
            override fun pageId(index: Int): String? = null
            override fun page(index: Int): Page { reads++; return pages[index] }
        }
        val versions = LongArray(20)
        val index = PageTextIndex()
        fun find(query: String) = index.find(source, { versions[it] }, null, { emptyList() }, query, null)
        assertEquals(DocumentSearch.find(source, null as PdfTextIndex?, "alpha"), find("alpha"))
        val initial = reads
        assertEquals(20, find("beta").size)
        assertEquals(initial, reads)
        versions[7]++
        find("alpha")
        assertEquals(initial + 1, reads)
        index.clear()
        find("alpha")
        assertEquals(initial + 21, reads)
    }
}

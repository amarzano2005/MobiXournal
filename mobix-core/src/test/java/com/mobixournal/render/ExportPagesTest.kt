package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **What an export's page answer means**, before either shell draws a card.
 *
 * The three answers a reader can give are one rule, and the interesting cases are the edges: a page in view
 * that no longer exists, a field that names nothing, and the fact that [ExportPageScope.ALL] is the document
 * in its own order rather than the source PDF's. A test rather than a comment because the window and the
 * tablet read this same function — a divergence here is two different files from one answer.
 */
class ExportPagesTest {

    @Test
    fun allPagesIsTheDocumentInItsOwnOrder() {
        val plan = ExportPages.resolve(ExportPageScope.ALL, current = 3, text = "", pageCount = 4)

        assertEquals((0..3).toList(), plan.indices())
    }

    @Test
    fun theCurrentPageIsTheOneInView() {
        val plan = ExportPages.resolve(ExportPageScope.CURRENT, current = 2, text = "", pageCount = 5)

        assertEquals(listOf(2), plan.indices())
    }

    @Test
    fun aCurrentPagePastTheEndIsBroughtBackToTheLastSheet() {
        // A tab whose page was deleted while the card was up still exports a page rather than refusing.
        val plan = ExportPages.resolve(ExportPageScope.CURRENT, current = 9, text = "", pageCount = 3)

        assertEquals(listOf(2), plan.indices())
    }

    @Test
    fun aTypedSelectionIsTheSameGrammarTheImportCardTeaches() {
        val plan = ExportPages.resolve(ExportPageScope.SELECTED, current = 0, text = "2,4-6", pageCount = 9)

        assertEquals(listOf(1, 3, 4, 5), plan.indices())
    }

    @Test
    fun aTypedSelectionKeepsTheDocumentsOrderWhateverOrderItWasTyped() {
        // `4,2` names two pages; the export holds them as the notebook does, not as it was typed.
        val plan = ExportPages.resolve(ExportPageScope.SELECTED, current = 0, text = "4,2", pageCount = 6)

        assertEquals(listOf(1, 3), plan.indices())
    }

    @Test
    fun anEmptyFieldIsARefusalWithASentence() {
        val plan = ExportPages.resolve(ExportPageScope.SELECTED, current = 0, text = " ", pageCount = 6)

        assertTrue("a typed answer that names nothing is refused: $plan", plan is PageRangePlan.Invalid)
        assertNull(plan.indices())
        assertEquals("Type the pages to bring, like 2,4-6,9.", (plan as PageRangePlan.Invalid).message)
    }

    @Test
    fun aDocumentWithNoPagesIsRefusedWhateverTheAnswer() {
        ExportPageScope.entries.forEach { scope ->
            val plan = ExportPages.resolve(scope, current = 0, text = "1", pageCount = 0)

            assertTrue("$scope", plan is PageRangePlan.Invalid)
        }
    }

    @Test
    fun theReadoutCountsTheAnswerOutOfTheDocumentAndRefusalsSpeakForThemselves() {
        val chosen = ExportPages.resolve(ExportPageScope.SELECTED, 0, "2,4", 8)

        assertEquals("2 pages of 8", ExportPages.readout(chosen, 8))
        assertEquals("1 page", ExportPages.count(1))
        assertEquals("3 pages", ExportPages.count(3))
        assertEquals(
            "Pages are numbered 1 to 8.",
            ExportPages.readout(ExportPages.resolve(ExportPageScope.SELECTED, 0, "99", 8), 8),
        )
    }
}

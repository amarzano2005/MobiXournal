package com.mobixournal.render

import com.mobixournal.format.model.MathFlavor
import com.mobixournal.format.model.TableAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a drop of external text becomes: the RFC 4180 quoting of a copied spreadsheet range, the
 * header/alignment rules of the table it turns into, and the line between a formula and prose.
 */
class ExternalIngestTest {

    private val black = 0xFF000000.toInt()

    private fun ingested(text: String, width: Double = 320.0): Ingested? =
        ExternalIngest.fromText(text, left = 40.0, top = 80.0, color = black, defaultWidthPt = width)

    private fun tableOf(text: String, width: Double = 320.0): com.mobixournal.format.model.TableElement =
        (ingested(text, width) as Ingested.Table).element

    @Test
    fun `quoted CSV fields keep delimiters and doubled quotes`() {
        val rows = ExternalIngest.parseDelimited(
            "name,note\n\"Doe, Jane\",\"She said \"\"hi\"\"\"",
            ',',
        )
        assertEquals(listOf("name", "note"), rows[0])
        assertEquals(listOf("Doe, Jane", "She said \"hi\""), rows[1])
    }

    @Test
    fun `a tab separated block is detected and becomes a table`() {
        val table = tableOf("Parameter\tValue\nfrequency\t50 Hz\nperiod\t20 ms")
        assertEquals(2, table.colWidths.size)
        assertEquals(3, table.rowHeights.size)
        assertEquals("Parameter", table.cell(0, 0).text)
        assertEquals("50 Hz", table.cell(1, 1).text)
    }

    @Test
    fun `a text first row is a header and a numeric one is not`() {
        assertTrue(tableOf("name,note\nx,1").headerRow)
        assertFalse(tableOf("1,2\n3,4").headerRow)
    }

    @Test
    fun `numeric cells are centred and prose cells are left aligned`() {
        val table = tableOf("name,value\nalpha,1.5")
        assertEquals(TableAlign.LEFT, table.cell(1, 0).align)
        assertEquals(TableAlign.CENTER, table.cell(1, 1).align)
    }

    @Test
    fun `every row is padded to the widest one`() {
        val table = tableOf("a,b\nc,d,e")
        assertEquals(3, table.colWidths.size)
        assertEquals("", table.cell(0, 2).text)
        assertEquals("e", table.cell(1, 2).text)
    }

    @Test
    fun `the table's columns divide the requested width exactly`() {
        val table = tableOf("a,b,c\n1,2,3", width = 300.0)
        assertEquals(300.0, table.colWidths.sum(), 0.0001)
        assertEquals(40.0, table.left, 0.0)
        assertEquals(80.0, table.top, 0.0)
    }

    @Test
    fun `a latex line becomes a living math block`() {
        val math = (ingested("\\frac{a}{b}") as Ingested.Math).element
        assertEquals("\\frac{a}{b}", math.source)
        assertEquals(MathFlavor.LATEX, math.flavor)
        assertEquals(40.0, math.left, 0.0)
        assertEquals(80.0, math.top, 0.0)
        assertEquals(math.left + com.mobixournal.format.model.MathBlockElement.DEFAULT_WIDTH_PT, math.right, 0.0)
    }

    @Test
    fun `a caret makes a short line a formula`() {
        assertTrue(ingested("x^2 + y^2") is Ingested.Math)
        assertTrue(ingested("a_1 + b_2") is Ingested.Math)
    }

    @Test
    fun `prose becomes a text box with the dropped text intact`() {
        val text = (ingested("Remember the milk\nand the eggs") as Ingested.Text).element
        assertEquals("Remember the milk\nand the eggs", text.content)
        assertEquals("Sans", text.font)
        assertEquals(12.0, text.size, 0.0)
        assertEquals(black, text.color)
    }

    @Test
    fun `blank input is not content`() {
        assertNull(ingested("   \n\t\n"))
    }
}

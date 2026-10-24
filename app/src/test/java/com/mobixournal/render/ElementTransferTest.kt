package com.mobixournal.render

import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The selection's transferable payload: the `.mobix` element codec in an envelope, so a copy keeps
 * every field a file keeps, an image keeps its bytes, and a foreign or corrupt clipboard is refused
 * instead of crashing the canvas.
 */
class ElementTransferTest {

    private val black = 0xFF000000.toInt()

    private fun stroke() = Stroke(
        tool = Tool.PEN,
        color = black,
        capStyle = "round",
        points = listOf(
            StrokePoint(10.0, 20.0, 2.0, pressure = 0.5f, tilt = 12f, azimuth = 45f, dtMs = 8),
            StrokePoint(40.0, 60.0, 3.0),
        ),
        uniformWidth = false,
        id = "stk_transfer",
    )

    private fun text() = TextElement("Sans", 12.0, 72.0, 96.0, black, "Hello", id = "txt_transfer")

    private fun math() = MathBlockElement(
        left = 30.0, top = 200.0, right = 150.0, bottom = 232.0,
        source = "x^2 + y^2", color = black, id = "mth_transfer",
    )

    private fun table() = TableElement(
        left = 60.0, top = 300.0,
        colWidths = listOf(60.0, 60.0), rowHeights = listOf(24.0),
        cells = listOf(listOf(TableCell("A"), TableCell("B"))),
        id = "tbl_transfer",
    )

    @Test
    fun aSelectionRoundTripsInOrderWithItsGeometry() {
        val elements = listOf(stroke(), text(), math(), table())
        val back = ElementTransfer.decode(ElementTransfer.encode(elements))
        assertEquals(elements, back)
    }

    @Test
    fun anImageKeepsItsBytesThroughThePayload() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)
        val image = ImageElement(10.0, 10.0, 60.0, 60.0, bytes)
        val back = ElementTransfer.decode(ElementTransfer.encode(listOf(image))).single() as ImageElement
        assertArrayEquals(bytes, back.data)
        assertEquals(10.0, back.left, 0.0)
        assertEquals(60.0, back.bottom, 0.0)
    }

    @Test
    fun aMalformedPayloadIsEmptyRatherThanAnException() {
        assertTrue(ElementTransfer.decode("not json at all").isEmpty())
        assertTrue(ElementTransfer.decode("""{"version":1,"elements":"nope"}""").isEmpty())
        assertFalse(ElementTransfer.hasPayload(null))
        assertFalse(ElementTransfer.hasPayload("{}"))
    }

    @Test
    fun anUnknownVersionIsRefusedRatherThanHalfRead() {
        assertTrue(ElementTransfer.decode("""{"version":2,"elements":[]}""").isEmpty())
        assertTrue(ElementTransfer.decode("""{"elements":[]}""").isEmpty())
    }

    @Test
    fun plainTextNamesEveryFamily() {
        val summary = ElementTransfer.plainText(listOf(stroke(), text(), math(), table()))
        assertEquals("pen\nHello\nx^2 + y^2\ntable", summary)
    }
}

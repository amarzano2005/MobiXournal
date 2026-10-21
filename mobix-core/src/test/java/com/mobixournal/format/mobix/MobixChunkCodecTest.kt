package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MobixChunk] and [MobixChunkCodec].
 */
class MobixChunkCodecTest {

    @Test
    fun chunkEntryNameParsingHandlesSignedCoordinates() {
        assertEquals(0 to 0, MobixFormat.parseChunkEntry("chunks/chunk_0_0.json"))
        assertEquals(-3 to 2, MobixFormat.parseChunkEntry("chunks/chunk_-3_2.json"))
        assertEquals(5 to -10, MobixFormat.parseChunkEntry("chunks/chunk_5_-10.json"))
        assertEquals(-1 to -1, MobixFormat.parseChunkEntry("chunks/chunk_-1_-1.json"))

        assertNull(MobixFormat.parseChunkEntry("pages/page-0001.json"))
        assertNull(MobixFormat.parseChunkEntry("chunks/not_a_chunk.json"))
        assertNull(MobixFormat.parseChunkEntry("chunks/chunk_a_b.json"))
    }

    @Test
    fun roundTripChunkSerializationWithLayersAndStrokes() {
        val stroke = Stroke(
            tool = Tool.PEN,
            color = 0xFF123456.toInt(),
            capStyle = "round",
            points = listOf(
                StrokePoint(10.0, 20.0, width = 2.5),
                StrokePoint(15.0, 25.0, width = 2.5),
            ),
            uniformWidth = true,
            id = "stk_test_1234",
        )
        val layer = Layer(elements = listOf(stroke), name = "InkLayer")
        val chunk = MobixChunk(
            cx = -2,
            cy = 3,
            layers = listOf(layer),
            background = Background.Solid(0xFFF0F0F0.toInt(), "ruled", "0.5,0.5"),
            widthPt = 1024.0,
            heightPt = 1024.0,
            id = "chk_test_tile",
        )

        val sink = MobixAssetSink()
        val encodedObj = MobixChunkCodec.encode(chunk, sink)
        val jsonText = JsonWriter.write(encodedObj)

        assertTrue(jsonText.contains("\"cx\":-2"))
        assertTrue(jsonText.contains("\"cy\":3"))
        assertTrue(jsonText.contains("\"widthPt\":1024"))
        assertTrue(jsonText.contains("InkLayer"))

        val parsedObj = JsonParser.parse(jsonText) as com.mobixournal.format.json.JsonNode.Obj
        val decoded = MobixChunkCodec.decode(parsedObj, MobixAssetSource { null })

        assertEquals(-2, decoded.cx)
        assertEquals(3, decoded.cy)
        assertEquals(1024.0, decoded.widthPt, 0.001)
        assertEquals(1024.0, decoded.heightPt, 0.001)
        assertEquals(1, decoded.layers.size)
        assertEquals("InkLayer", decoded.layers[0].name)
        val decodedStroke = decoded.layers[0].elements[0] as Stroke
        assertEquals("stk_test_1234", decodedStroke.id)
        assertEquals(0xFF123456.toInt(), decodedStroke.color)
        assertEquals(2, decodedStroke.points.size)

        val bg = decoded.background as? Background.Solid
        assertNotNull(bg)
        assertEquals("ruled", bg!!.style)
    }

    @Test
    fun chunkFallbackCoordinatesWhenOmittedInJson() {
        val minimalJson = """{"widthPt":1024.0,"heightPt":1024.0,"layers":[]}"""
        val node = JsonParser.parse(minimalJson) as com.mobixournal.format.json.JsonNode.Obj
        val decoded = MobixChunkCodec.decode(node, MobixAssetSource { null }, fallbackCx = 4, fallbackCy = -5)

        assertEquals(4, decoded.cx)
        assertEquals(-5, decoded.cy)
        assertTrue(decoded.layers.isEmpty())
        assertNull(decoded.background)
    }

    @Test
    fun chunkConversionToAndFromPage() {
        val chunk = MobixChunk(
            cx = 1,
            cy = 2,
            layers = listOf(Layer(elements = emptyList(), name = "L1")),
            background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            id = "chk_1_2",
        )
        val page = chunk.toPage()
        assertEquals(1024.0, page.width, 0.001)
        assertEquals(1024.0, page.height, 0.001)
        assertEquals("chk_1_2", page.id)
        assertEquals(1, page.layers.size)

        val restoredChunk = MobixChunk.fromPage(page, cx = 1, cy = 2)
        assertEquals(1, restoredChunk.cx)
        assertEquals(2, restoredChunk.cy)
        assertEquals(page.layers, restoredChunk.layers)
        assertEquals(page.id, restoredChunk.id)
    }
}

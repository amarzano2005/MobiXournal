package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.toJson
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.RawElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The examples the element chapters publish (`docs/mobix-element-fields.md`,
 * `docs/mobix-elements.md`, `docs/mobix-living-entities.md`), read as files rather than as prose.
 *
 * An example in a specification is a promise that the document it shows is a document: if this build
 * cannot model one of its own examples, or models it into something that shifts on the next save, the
 * example is wrong — or the codec is — and either way an outside implementer would be reading a
 * description of a file that does not exist.
 */
class MobixElementExampleTest {

    /** Any payload the assets resolve to; the examples name `assets/…` entries, not real pixels. */
    private val payload = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    private fun model(json: String): Element? = MobixElementCodec.decode(
        JsonParser.parse(json) as JsonNode.Obj,
        MobixAssetSource { name -> if (name.startsWith("assets/")) payload else null },
    )

    /** Modelled (not preserved as raw) and stable: two saves of the example are the same file. */
    private fun assertExample(json: String) {
        val first = model(json)
        assertNotNull("the documented example is not an element: $json", first)
        assertFalse("the documented example is not modelled by this build: $json", first is RawElement)
        val once = MobixElementCodec.encode(first!!, MobixAssetSink()).toJson()
        val again = MobixElementCodec.encode(model(once)!!, MobixAssetSink()).toJson()
        assertEquals("the documented example does not survive a second save", once, again)
    }

    private val stroke = """
        { "type": "stroke", "id": "stk_01", "tool": "PEN", "color": "#1a237eff",
          "lineStyle": "PLAIN", "capStyle": "round",
          "bounds": { "left": 101.7, "top": 204.4, "right": 104.5, "bottom": 207.5 },
          "points": [
            { "x": 102.4, "y": 205.1, "width": 1.2, "pressure": 0.55, "tilt": 0.248, "azimuth": 0.558, "dtMs": 0 },
            { "x": 103.8, "y": 206.8, "width": 1.4, "pressure": 0.62, "tilt": 0.258, "azimuth": 0.578, "dtMs": 6 }
          ] }
    """.trimIndent()

    private val text = """
        { "type": "text", "id": "txt_01", "font": "Sans Bold", "size": 12.0, "x": 72.0, "y": 90.0,
          "color": "#000000ff", "content": "E = mc^2" }
    """.trimIndent()

    private val image = """
        { "type": "image", "id": "img_01",
          "bounds": { "left": 40.0, "top": 40.0, "right": 240.0, "bottom": 200.0 },
          "asset": "assets/img_a1b2c3d4.webp" }
    """.trimIndent()

    private val texImage = """
        { "type": "texImage", "id": "tex_01",
          "bounds": { "left": 100.0, "top": 300.0, "right": 280.0, "bottom": 330.0 },
          "latex": "\\int_0^1 x^2 dx", "color": "#000000ff",
          "asset": "assets/img_e5f6g7h8.png" }
    """.trimIndent()

    private val table = """
        { "type": "table", "id": "tbl_01",
          "bounds": { "left": 72.0, "top": 120.0, "right": 472.0, "bottom": 196.0 },
          "colWidths": [120.0, 180.0, 100.0], "rowHeights": [28.0, 24.0, 24.0],
          "headerRow": true, "borderColor": "#212121ff", "borderWidth": 1.0,
          "cells": [
            [ { "text": "Parametro", "align": "CENTER", "bold": true, "fillColor": "#00000015" },
              { "text": "Formula", "align": "CENTER", "bold": true, "fillColor": "#00000015" },
              { "text": "Valore", "align": "CENTER", "bold": true, "fillColor": "#00000015" } ],
            [ { "text": "Frequenza", "align": "LEFT", "bold": false, "fillColor": null },
              { "text": "f = 1 / T", "align": "CENTER", "bold": false, "fillColor": null },
              { "text": "50 Hz", "align": "RIGHT", "bold": false, "fillColor": null } ]
          ] }
    """.trimIndent()

    private val plot = """
        { "type": "plot", "id": "plt_01",
          "bounds": { "left": 100.0, "top": 200.0, "right": 400.0, "bottom": 380.0 },
          "xMin": -6.28, "xMax": 6.28, "samples": 300,
          "showCriticalPoints": true, "includeDerivative": false,
          "shadeIntegral": { "from": 0.0, "to": 3.14 },
          "curves": [
            { "kind": "CARTESIAN", "source": "sin(x) * exp(-0.1 * x)",
              "color": "#d32f2fff", "strokeWidth": 1.5, "lineStyle": "PLAIN" }
          ],
          "parameters": { "a": 1.0, "b": 2.0 },
          "axisColor": "#37474fff",
          "summaryText": "Roots: x = {0.0, 3.14, -3.14}..." }
    """.trimIndent()

    private val shape = """
        { "type": "parametricShape", "id": "shp_01", "shape": "TRIANGLE_SCALENE",
          "bounds": { "left": 100.0, "top": 100.0, "right": 250.0, "bottom": 240.0 },
          "vertices": [ { "x": 100.0, "y": 100.0 }, { "x": 250.0, "y": 120.0 }, { "x": 160.0, "y": 240.0 } ],
          "strokeColor": "#0d47a1ff", "strokeWidth": 2.0, "fillColor": "#2196f31a",
          "showAngles": true, "showSideLengths": true }
    """.trimIndent()

    private val circuit = """
        { "type": "circuit", "id": "ckt_res_01", "symbol": "RESISTOR",
          "value": "10 kΩ", "labelSide": "below",
          "position": { "x": 300.0, "y": 150.0 }, "rotation": 90.0,
          "strokeColor": "#d84315ff", "strokeWidth": 1.6,
          "pins": [
            { "id": "p1", "relX": -24.0, "relY": 0.0, "connectedTo": "net_node_A" },
            { "id": "p2", "relX": 24.0, "relY": 0.0, "connectedTo": "net_node_B" }
          ] }
    """.trimIndent()

    private val mathBlock = """
        { "type": "mathBlock", "id": "mth_01",
          "source": "\\frac{\\pi}{2}", "color": "#1f6febff",
          "bounds": { "left": 120.0, "top": 240.0, "right": 200.0, "bottom": 268.0 } }
    """.trimIndent()

    @Test
    fun theGeometryExamplesAreDocuments() {
        assertExample(stroke)
        assertExample(text)
        assertExample(image)
        assertExample(texImage)
    }

    @Test
    fun theLivingExamplesAreDocuments() {
        assertExample(table)
        assertExample(plot)
        assertExample(shape)
        assertExample(circuit)
        assertExample(mathBlock)
    }
}

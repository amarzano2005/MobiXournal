package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The element and page codecs (`docs/mobix-schema-spec.md`): what a stroke keeps, what a picture does
 * not carry, what an element this build has no model for does, and how a page and its background
 * survive a round trip.
 */
class MobixElementCodecTest {

    private fun sourceOf(sink: MobixAssetSink): MobixAssetSource {
        val byName = sink.entries().associate { it.name to it.bytes }
        return MobixAssetSource { byName[it] }
    }

    private fun point(x: Double, y: Double, width: Double = 1.4) = StrokePoint(x, y, width)

    private fun pressureStroke() = Stroke(
        tool = Tool.PEN,
        color = 0xFF1A237E.toInt(),
        capStyle = "round",
        points = listOf(
            StrokePoint(102.4, 205.1, 1.2, pressure = 0.55f, tilt = 14.2f, azimuth = 32.0f, dtMs = 0),
            StrokePoint(103.8, 206.8, 1.4, pressure = 0.62f, tilt = 14.8f, azimuth = 33.1f, dtMs = 6),
        ),
        uniformWidth = false,
        lineStyle = LineStyle.DASHED,
        extraAttrs = linkedMapOf("ts" to "14250", "fn" to "aud_7a9f1c.opus"),
        id = "stk_01",
    )

    private fun roundTrip(element: Element, sink: MobixAssetSink = MobixAssetSink()): Element? =
        MobixElementCodec.decode(MobixElementCodec.encode(element, sink), sourceOf(sink))

    @Test
    fun aPressureStrokeKeepsItsWidthsItsTelemetryAndItsAudioLink() {
        val stroke = pressureStroke()

        val json = MobixElementCodec.encode(stroke, MobixAssetSink())
        val back = roundTrip(stroke) as Stroke

        assertEquals(stroke, back)
        assertEquals("per-point widths are the points', not a lineWidth", false, json.has("lineWidth"))
        assertTrue("audioRef object is emitted in .mobix format", json.has("audioRef"))
        val audioRefNode = json.getObj("audioRef")!!
        assertEquals("aud_7a9f1c.opus", audioRefNode.getStrOrNull("assetId"))
        assertEquals(14250, audioRefNode.getInt("startMs", 0))
        assertEquals(6.0, back.points[1].dtMs!!.toDouble(), 0.0)
        assertEquals(14.8f, back.points[1].tilt!!)
        assertEquals(
            "the attributes the .xopp bridge must not lose",
            mapOf("ts" to "14250", "fn" to "aud_7a9f1c.opus"),
            back.extraAttrs,
        )
    }

    @Test
    fun aConstantWidthStrokeIsToldFromAPressureOneByItsLineWidth() {
        val uniform = pressureStroke().copy(
            uniformWidth = true,
            points = listOf(point(10.0, 10.0, 3.0), point(20.0, 10.0, 3.0)),
        )

        val json = MobixElementCodec.encode(uniform, MobixAssetSink())
        val back = roundTrip(uniform) as Stroke

        assertEquals(3.0, (json.value("lineWidth") as JsonNode.Num).value, 0.0)
        assertTrue("a width attribute means the constant form", back.uniformWidth)
        assertEquals(uniform, back)
    }

    @Test
    fun boundsIsDerivedWrittenAndNeverTrusted() {
        val stroke = pressureStroke()
        val written = (MobixElementCodec.encode(stroke, MobixAssetSink()).value("bounds") as JsonNode.Obj).map

        assertEquals("the ink's own extents, half a width out", 102.4 - 0.7, (written["left"] as JsonNode.Num).value, 0.0001)
        assertEquals(206.8 + 0.7, (written["bottom"] as JsonNode.Num).value, 0.0001)

        // A file whose cached bounds disagree with its geometry is drawn from the geometry.
        val lying = MobixElementCodec.encode(stroke, MobixAssetSink()).let { json ->
            JsonNode.Obj(
                LinkedHashMap<String, JsonNode>().also {
                    it.putAll(json.map)
                    it["bounds"] = JsonNode.obj(
                        "left" to JsonNode.of(-999.0), "top" to JsonNode.of(-999.0),
                        "right" to JsonNode.of(-999.0), "bottom" to JsonNode.of(-999.0),
                    )
                },
            )
        }
        val back = MobixElementCodec.decode(lying, MobixAssetSource { null }) as Stroke

        assertEquals(stroke.points, back.points)
        assertEquals(
            "and a rewrite recomputes it rather than passing the lie along",
            written["left"].toString(),
            (MobixElementCodec.encode(back, MobixAssetSink()).value("bounds") as JsonNode.Obj).map["left"].toString(),
        )
    }

    @Test
    fun anElementThisBuildHasNoModelForIsPreservedVerbatim() {
        // An element type from a newer build: the reason the format has a `type` discriminator.
        val future = """{"type":"3dPlotElement","id":"p3d_01","surface":"z = x^2 - y^2","samples":40}"""
        val node = com.mobixournal.format.json.JsonParser.parse(future) as JsonNode.Obj

        val preserved = MobixElementCodec.decode(node, MobixAssetSource { null }) as RawElement

        assertEquals("3dPlotElement", preserved.name)
        assertEquals(RawElement.Syntax.JSON, preserved.syntax)
        assertEquals(future, preserved.body)
        assertEquals(
            "and re-encodes as the same object",
            future,
            com.mobixournal.format.json.JsonWriter.write(MobixElementCodec.encode(preserved, MobixAssetSink())),
        )
    }

    @Test
    fun anObjectWithoutATypeIsNotAnElement() {
        val node = com.mobixournal.format.json.JsonParser.parse("""{"x":1}""") as JsonNode.Obj
        assertNull(MobixElementCodec.decode(node, MobixAssetSource { null }))
    }

    @Test
    fun aPictureIsStoredOnceAndAddressedByItsContent() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
        val sink = MobixAssetSink()
        val first = ImageElement(10.0, 20.0, 110.0, 120.0, png, id = "img_1")
        val second = ImageElement(200.0, 20.0, 300.0, 120.0, png, id = "img_2")

        val json = MobixElementCodec.encode(first, sink)
        MobixElementCodec.encode(second, sink)

        assertEquals("the same bytes are one entry", 1, sink.count)
        val name = (json.value("asset") as JsonNode.Str).value
        assertTrue(name.startsWith("assets/img_"))
        assertTrue(name.endsWith(".png"))
        assertTrue("never base64 in the JSON", name.length < 100)
        val back = MobixElementCodec.decode(json, sourceOf(sink)) as ImageElement
        assertArrayEquals(png, back.data)
        assertEquals(first, back)
    }

    @Test
    fun anImageWhoseAssetIsMissingIsRefused() {
        val json = MobixElementCodec.encode(
            ImageElement(0.0, 0.0, 10.0, 10.0, byteArrayOf(1, 2, 3), id = "img_1"),
            MobixAssetSink(),
        )
        assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixElementCodec.decode(json, MobixAssetSource { null })
        }
    }

    @Test
    fun aFormulaWithNoRenderingIsStillAFormula() {
        val tex = TexImageElement(
            left = 100.0, top = 300.0, right = 280.0, bottom = 330.0,
            latex = "\\int_0^1 x^2 dx", color = 0xFF000000.toInt(),
            data = null, latexInAttribute = false, id = "tex_1",
        )

        val json = MobixElementCodec.encode(tex, MobixAssetSink())
        val back = roundTrip(tex) as TexImageElement

        assertFalse(json.has("asset"))
        assertEquals("the source spelling is kept for the bridge", false, back.latexInAttribute)
        assertEquals(tex, back)
    }

    @Test
    fun aTextElementCarriesItsFontAndContent() {
        val text = TextElement("Sans Bold", 12.0, 72.0, 90.0, 0xFF000000.toInt(), "E = mc²", id = "txt_1")

        assertEquals(text, roundTrip(text) as TextElement)
    }

    @Test
    fun aValueTheBuildDoesNotKnowIsRefusedRatherThanDefaulted() {
        val badTool = """{"type":"stroke","tool":"CRAYON","points":[]}"""
        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixElementCodec.decode(
                com.mobixournal.format.json.JsonParser.parse(badTool) as JsonNode.Obj,
                MobixAssetSource { null },
            )
        }
        assertTrue(failure.message.orEmpty(), "CRAYON" in failure.message.orEmpty())
        assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixElementCodec.decode(
                com.mobixournal.format.json.JsonParser.parse("""{"type":"stroke","lineStyle":"WAVY"}""") as JsonNode.Obj,
                MobixAssetSource { null },
            )
        }
    }

    @Test
    fun anElementWithoutAnIdentityGetsOneAndKeepsIt() {
        val legacy = """{"type":"stroke","tool":"PEN","points":[{"x":1,"y":2,"width":1}]}"""
        val node = com.mobixournal.format.json.JsonParser.parse(legacy) as JsonNode.Obj

        val minted = MobixElementCodec.decode(node, MobixAssetSource { null }, mint = { "stk_fixed" }) as Stroke
        assertEquals("stk_fixed", minted.id)
        val again = roundTrip(minted) as Stroke
        assertEquals("and the id is in the file from then on", "stk_fixed", again.id)
        assertEquals(
            "an id already in the file is never replaced",
            "stk_01",
            (roundTrip(pressureStroke()) as Stroke).id,
        )
    }

    // ---- pages ----------------------------------------------------------------------------------

    @Test
    fun aPageRoundTripsWithItsLayersBackgroundAndUnknownChildren() {
        val page = Page(
            width = 595.27559100,
            height = 841.88976400,
            background = Background.Solid(0xFFFFFFFF.toInt(), "ruled", "0.50,0.5,0.5,0.10,1.0,4"),
            layers = listOf(
                Layer(elements = listOf(pressureStroke()), name = "Notes"),
                Layer(elements = listOf(RawElement("watermark", body = "<x/>")), extraAttrs = linkedMapOf("hidden" to "true")),
            ),
        )
        val sink = MobixAssetSink()

        val back = MobixPageCodec.decode(MobixPageCodec.encode(page, sink), sourceOf(sink))

        assertEquals(page, back)
        assertEquals("Notes", back.layers.first().name)
        assertEquals(RawElement.Syntax.XML, (back.layers[1].elements.single() as RawElement).syntax)
    }

    @Test
    fun aPdfBackgroundCanBeBundledAndStillRemembersWhereItCameFrom() {
        val pdf = "%PDF-1.7".toByteArray(Charsets.US_ASCII)
        val external = Background.Pdf(filename = "notes.pdf", pageNo = 3, domain = "absolute")
        val page = Page(595.0, 841.0, external, listOf(Layer(elements = emptyList())))

        // Without the bytes the document keeps the reference, exactly like a gzip .xopp.
        val unreferenced = MobixPageCodec.decode(
            MobixPageCodec.encode(page, MobixAssetSink(), backgroundBytes = { null }),
            MobixAssetSource { null },
        )
        assertEquals(external, unreferenced.background)

        // With them, the same facts plus a self-contained bundle under the document's own name.
        val sink = MobixAssetSink()
        val json = MobixPageCodec.encode(page, sink, backgroundBytes = { pdf })
        val back = MobixPageCodec.decode(json, sourceOf(sink)).background as Background.Pdf

        assertTrue(
            "the PDF is one payload in assets/, like every other binary: ${sink.entries().map { it.name }}",
            sink.entries().single().name.startsWith("assets/img_") && sink.entries().single().name.endsWith(".pdf"),
        )
        assertEquals("notes.pdf", back.filename)
        assertEquals("absolute", back.domain)
        assertEquals(3, back.pageNo)
        assertEquals(sink.entries().single().name, back.bundledAsset)
        assertArrayEquals(
            "and the bundled bytes are the ones we put in",
            pdf,
            sourceOf(sink).bytes(back.bundledAsset!!),
        )
    }

    @Test
    fun aBundledBackgroundSurvivesAPassThatNeverReadsItsBytes() {
        // Re-encoding from the model must not orphan the entry the document's bytes live in: the bundle
        // is referenced even when nobody handed the codec the payload.
        val bundled = Background.Pdf(null, 0, "attach", bundledAsset = "assets/img_deadbeef.pdf")
        val reEncoded = MobixPageCodec.encode(
            Page(595.0, 841.0, bundled, emptyList()),
            MobixAssetSink(),
            backgroundBytes = { null },
        )

        assertEquals(
            "assets/img_deadbeef.pdf",
            (reEncoded.value("background") as JsonNode.Obj).getStrOrNull("asset"),
        )
    }

    @Test
    fun aPageWithoutABackgroundIsAPlainSheet() {
        val back = MobixPageCodec.decode(
            com.mobixournal.format.json.JsonParser.parse("""{"widthPt":595,"heightPt":841}""") as JsonNode.Obj,
            MobixAssetSource { null },
        )
        assertEquals("plain", (back.background as Background.Solid).style)
        assertTrue(back.layers.isEmpty())
    }

    @Test
    fun aPixmapBackgroundKeepsItsReferenceAndItsBundle() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val pixmap = Background.Pixmap(domain = "absolute", filename = "/home/me/scan.png")
        val sink = MobixAssetSink()

        val json = MobixPageCodec.encode(
            Page(595.0, 841.0, pixmap, emptyList()),
            sink,
            backgroundBytes = { png },
        )
        val back = MobixPageCodec.decode(json, sourceOf(sink)).background as Background.Pixmap

        assertEquals("/home/me/scan.png", back.filename)
        assertEquals(1, sink.count)
        assertTrue(back.bundledAsset!!.startsWith("assets/img_"))
    }
}

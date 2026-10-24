package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.MathFlavor
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.render.MathBlockInk
import com.mobixournal.render.XoppSynthesis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The living math block: its native `.mobix` spelling, the `<teximage>` it compiles to for `.xopp`,
 * and the rule that makes both safe — the **source** is the only authority, so nothing a reader sees
 * can disagree with what a user typed.
 */
class MathBlockCodecTest {

    private fun encode(element: MathBlockElement): JsonNode.Obj =
        MobixElementCodec.encode(element, MobixAssetSink())

    private fun decode(json: String) = MobixElementCodec.decode(
        JsonParser.parse(json) as JsonNode.Obj,
        MobixAssetSource { null },
    )

    private fun block() = MathBlockElement(
        left = 40.0,
        top = 60.0,
        right = 190.0,
        bottom = 96.0,
        source = "\\frac{\\pi}{2}",
        color = 0xFF1F6FEB.toInt(),
        flavor = MathFlavor.LATEX,
        extraAttrs = mapOf("future" to "kept"),
        id = "mth_test",
    )

    @Test
    fun aMathBlockRoundTripsWithItsSourceBoxAndFlavour() {
        val back = MobixElementCodec.decode(encode(block()), MobixAssetSource { null }) as MathBlockElement
        assertEquals(block(), back)
        assertEquals(MathFlavor.LATEX, back.flavor)
    }

    @Test
    fun typstSourceKeepsItsLanguageRatherThanBeingReadAsLatex() {
        val typst = block().copy(flavor = MathFlavor.TYPST, source = "sum_(i=1)^n i")
        val back = MobixElementCodec.decode(encode(typst), MobixAssetSource { null }) as MathBlockElement
        assertEquals(MathFlavor.TYPST, back.flavor)
        assertEquals(typst.source, back.source)
    }

    @Test
    fun aBlockWithoutASourceIsPreservedRatherThanHalfRead() {
        val json = """{"type":"mathBlock","bounds":{"left":0,"top":0,"right":10,"bottom":10}}"""
        val back = decode(json)
        assertTrue("unreadable mathBlock stays as raw markup", back is com.mobixournal.format.model.RawElement)
    }

    @Test
    fun theXoppBridgeWritesAStandardTexImageCarryingTheSource() {
        val document = Document(
            pages = listOf(
                Page(
                    width = 595.0,
                    height = 842.0,
                    background = com.mobixournal.format.model.Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                    layers = listOf(Layer(listOf(block()))),
                ),
            ),
        )
        val result = XoppSynthesis.synthesize(document, "doc_test")
        val tex = result.document.pages.single().layers.single().elements.single()
        assertTrue("a math block exports as a teximage", tex is TexImageElement)
        tex as TexImageElement
        assertEquals("\\frac{\\pi}{2}", tex.latex)
        assertEquals(0xFF1F6FEB.toInt(), tex.color)
        assertEquals(40.0, tex.left, 0.0)
        assertEquals(96.0, tex.bottom, 0.0)
        assertNull("the block carries no rendered bitmap", tex.data)
        assertEquals("kept", tex.extraAttrs["future"])
    }

    @Test
    fun aTypstBlockMarksItsLanguageOnTheTexImage() {
        val typst = block().copy(flavor = MathFlavor.TYPST, source = "x^2")
        val tex = MathBlockInk.texImage(typst)
        assertEquals("TYPST", tex.extraAttrs[MathBlockInk.FLAVOR_ATTR])
        assertEquals("x^2", tex.latex)
    }
}

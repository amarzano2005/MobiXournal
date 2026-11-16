package com.mobixournal.render

import com.mobixournal.format.Xopp
import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixAssetSource
import com.mobixournal.format.mobix.MobixElementCodec
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The underline a selected PDF passage becomes: one rule per line segment under the words' own boxes, the pen's
 * colour and width on an ordinary stroke, and the two things that make it **interoperable** — the `.xopp`
 * document it sits in round-trips to the same model, and the `.mobix` element codec reads the stroke back
 * unchanged.
 */
class PdfTextUnderlineTest {

    /** Two lines of one sheet: `one two` above `three four`, each word with its own box. */
    private fun twoLines(): List<PdfWord> = listOf(
        PdfWord("one", 0.0, 0.0, 20.0, 12.0),
        PdfWord("two", 30.0, 0.0, 50.0, 12.0),
        PdfWord("three", 0.0, 20.0, 20.0, 32.0),
        PdfWord("four", 30.0, 20.0, 50.0, 32.0),
    )

    @Test
    fun oneRuleUnderEachLineOfAMultilinePassage() {
        val rules = PdfTextUnderline.rules(twoLines())
        assertEquals("one rule per line, never one long slope", 2, rules.size)
        assertEquals(0.0, rules[0].left, 1e-9)
        assertEquals(50.0, rules[0].right, 1e-9)
        assertEquals("just under the first line's own boxes", 12.0 + PdfTextUnderline.GAP_PT, rules[0].y, 1e-9)
        assertEquals(32.0 + PdfTextUnderline.GAP_PT, rules[1].y, 1e-9)
    }

    @Test
    fun twoColumnsOnOneBaselineAreTwoRules() {
        val columns = listOf(
            PdfWord("left", 0.0, 0.0, 20.0, 12.0),
            PdfWord("right", 300.0, 0.0, 340.0, 12.0),
        )
        val rules = PdfTextUnderline.rules(columns)
        assertEquals("a gutter is never bridged", 2, rules.size)
        assertEquals(0.0, rules[0].left, 1e-9)
        assertEquals(300.0, rules[1].left, 1e-9)
    }

    @Test
    fun aReverseSelectionUnderlinesThePassageInReadingOrder() {
        val index = PdfTextIndex(listOf(twoLines()))
        val sheet = PdfSheetRef("notes.pdf", 0)
        // The reader dragged upwards: anchor is the last word, focus the first.
        val words = PdfTextSelection.boxes(index, PdfTextRange(sheet, 3, 1))
        val rules = PdfTextUnderline.rules(words)
        assertEquals(2, rules.size)
        assertEquals("the rules follow the text, not the drag", 12.0 + PdfTextUnderline.GAP_PT, rules[0].y, 1e-9)
        assertEquals(32.0 + PdfTextUnderline.GAP_PT, rules[1].y, 1e-9)
    }

    @Test
    fun aRotatedOrCroppedSheetNeedsNoSecondTransform() {
        // The boxes a rotated page's text layer reports are already in the visual page frame — here a page whose
        // crop starts at 500/300 — and the rule is placed from them, not from a page origin the code would guess.
        val rotated = listOf(PdfWord("turned", 500.0, 300.0, 540.0, 312.0))
        val rule = PdfTextUnderline.rules(rotated).single()
        assertEquals(500.0, rule.left, 1e-9)
        assertEquals(540.0, rule.right, 1e-9)
        assertEquals(312.0 + PdfTextUnderline.GAP_PT, rule.y, 1e-9)
    }

    @Test
    fun aBandNarrowerThanTheMinimumIsNotWorthARule() {
        assertTrue(PdfTextUnderline.rules(listOf(PdfWord("i", 10.0, 0.0, 11.0, 12.0))).isEmpty())
    }

    @Test
    fun theRuleIsThePensOwnOrdinaryStroke() {
        val stroke = PdfTextUnderline.strokes(twoLines(), color = 0xFF1A237E.toInt(), widthPt = 1.4).first()
        assertEquals(Tool.PEN, stroke.tool)
        assertEquals(0xFF1A237E.toInt(), stroke.color)
        assertEquals("round", stroke.capStyle)
        assertTrue("a constant width, never a pressure profile", stroke.uniformWidth)
        assertEquals(LineStyle.PLAIN, stroke.lineStyle)
        assertEquals(2, stroke.points.size)
        assertEquals(stroke.points[0].y, stroke.points[1].y, 1e-9)
        assertEquals(1.4, stroke.points[0].width, 1e-9)
        assertTrue("ordinary ink, with no figure or annotation attributes", stroke.extraAttrs.isEmpty())
    }

    @Test
    fun aWidthBelowThePalettesFloorIsClamped() {
        val stroke = PdfTextUnderline.strokes(twoLines(), color = 0xFF000000.toInt(), widthPt = 0.05).first()
        assertEquals(PdfTextUnderline.MIN_PEN_WIDTH_PT, stroke.points[0].width, 1e-9)
    }

    @Test
    fun theUnderlineSurvivesAXoppRoundTrip() {
        val strokes = PdfTextUnderline.strokes(twoLines(), color = 0xFF000000.toInt(), widthPt = 1.2)
        val document = Document(
            pages = listOf(
                Page(
                    width = 595.0,
                    height = 842.0,
                    background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                    layers = listOf(Layer(strokes)),
                ),
            ),
        )
        val reopened = Xopp.parseXml(Xopp.toXml(document))
        assertEquals("the rules are ordinary strokes in the file, not markup", document, reopened)
        assertEquals(2, reopened.pages.single().layers.single().elements.size)
    }

    @Test
    fun theUnderlineSurvivesTheMobixElementCodec() {
        val sink = MobixAssetSink()
        val stroke = PdfTextUnderline.strokes(twoLines(), color = 0xFF1A237E.toInt(), widthPt = 1.2).first()
        val json = MobixElementCodec.encode(stroke, sink)
        val byName = sink.entries().associate { it.name to it.bytes }
        val back = MobixElementCodec.decode(json, MobixAssetSource { byName[it] })
        assertNotNull(back)
        back as Stroke
        assertEquals(
            "a rule reads back as the stroke it was written as, down to the geometry the codec minted an id for",
            stroke.copy(id = back.id),
            back,
        )
        assertEquals(Tool.PEN, back.tool)
    }
}

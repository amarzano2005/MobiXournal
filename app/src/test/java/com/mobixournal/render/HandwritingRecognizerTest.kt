package com.mobixournal.render

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingRecognizerTest {

    private fun stroke(vararg coords: Double): Stroke {
        require(coords.size % 2 == 0)
        val pts = (coords.indices step 2).map {
            StrokePoint(coords[it], coords[it + 1], 1.5)
        }
        return Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = null,
            points = pts,
            uniformWidth = true,
        )
    }

    private fun letterT(x: Double, y: Double): List<Stroke> = listOf(
        stroke(x, y, x + 20.0, y),
        stroke(x + 10.0, y, x + 10.0, y + 30.0),
    )

    private fun letterO(x: Double, y: Double): List<Stroke> = listOf(
        stroke(
            x + 10.0, y,
            x, y + 10.0,
            x, y + 20.0,
            x + 10.0, y + 30.0,
            x + 20.0, y + 20.0,
            x + 20.0, y + 10.0,
            x + 10.0, y,
        ),
    )

    private fun letterD(x: Double, y: Double): List<Stroke> = listOf(
        stroke(x, y, x, y + 30.0),
        stroke(x, y, x + 18.0, y + 10.0, x + 18.0, y + 20.0, x, y + 30.0),
    )

    private fun letterI(x: Double, y: Double): List<Stroke> = listOf(
        stroke(x + 5.0, y, x + 5.0, y + 30.0),
    )

    @Test fun recognizesIndividualCharacters() {
        val oStrokes = letterO(50.0, 50.0)
        val recognizedO = HandwritingRecognizer.recognizePage(oStrokes)
        assertEquals(1, recognizedO.size)
        assertTrue(recognizedO[0].text.contains("O", ignoreCase = true) || recognizedO[0].text.contains("0"))

        val tStrokes = letterT(100.0, 50.0)
        val recognizedT = HandwritingRecognizer.recognizePage(tStrokes)
        assertEquals(1, recognizedT.size)
        assertEquals("T", recognizedT[0].text)
    }

    @Test fun groupsStrokesIntoWordsByHorizontalGap() {
        // "TO" followed by a gap then "DO"
        val strokes = buildList {
            addAll(letterT(20.0, 50.0))
            addAll(letterO(45.0, 50.0))
            // Big gap (35pt)
            addAll(letterD(110.0, 50.0))
            addAll(letterO(135.0, 50.0))
        }

        val words = HandwritingRecognizer.recognizePage(strokes)
        println("words in gap test: ${words.map { "${it.text} @ ${it.bounds}" }}")
        assertEquals(2, words.size)
        assertTrue(words[0].text.startsWith("T", ignoreCase = true))
        assertTrue(words[1].text.startsWith("D", ignoreCase = true))
    }

    @Test fun separatesMultipleLines() {
        val line1 = letterT(20.0, 40.0)
        val line2 = letterO(20.0, 120.0)

        val words = HandwritingRecognizer.recognizePage(line1 + line2)
        assertEquals(2, words.size)
        assertEquals("T", words[0].text)
        assertTrue(words[1].text.contains("O", ignoreCase = true) || words[1].text.contains("0"))
    }

    @Test fun buildsIndexFromDocument() {
        val strokes = letterT(30.0, 30.0) + letterO(55.0, 30.0)
        val doc = Document(
            pages = listOf(
                blankPage().copy(layers = listOf(Layer(strokes))),
            ),
        )

        val index = HandwritingIndex.build(doc)
        assertTrue(index.hasAnyText)
        val pageWords = index.words(0)
        assertEquals(1, pageWords.size)
        assertTrue(pageWords[0].bounds.left >= 30.0)
    }
}

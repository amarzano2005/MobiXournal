package com.mobixournal.desktop.ink
import com.mobixournal.desktop.io.DocumentLoader

import com.mobixournal.format.model.Stroke
import com.mobixournal.render.Bounds
import com.mobixournal.render.HandwritingRecognizer
import com.mobixournal.render.HandwrittenWord
import com.mobixournal.render.InkWordEngine
import java.io.File

/**
 * What the two readers make of a document's ink, printed page by page — the measurement a cursive
 * complaint needs (`docs/search.md`).
 *
 * Search answers a query; this answers "is the model even plugged in, and what does it read". It opens
 * nothing on screen, so it runs from the packaged executable (`MobiXournal.exe --probe-handwriting
 * <file>`): the packaged runtime is exactly where the bridge could fail while every unit test passes.
 *
 * For each page with ink it prints the stroke count, the model's words (box plus candidates) and the pure
 * engine's, side by side — a word the model reads and the fallback does not is the model working, and a
 * page where both are empty is the ink never reaching either.
 */
internal fun runHandwritingProbe(file: File?) {
    println("handwriting: windows=${WinInk.available} recognizers=${WinInk.recognizerCount()}")
    if (file == null) {
        println("handwriting: no document named (--probe-handwriting <file>)")
        return
    }
    val document = runCatching { DocumentLoader.load(file) }.getOrElse {
        println("handwriting: ${file.path} did not open — $it")
        return
    }
    println("handwriting: ${file.name} pages=${document.source.pageCount}")
    val none = object : InkWordEngine {
        override fun recognize(strokes: List<Stroke>, bounds: Bounds): HandwrittenWord? = null
    }
    for (index in 0 until document.source.pageCount) {
        val page = runCatching { document.source.page(index) }.getOrNull() ?: continue
        val strokes = page.layers.flatMap { it.elements.filterIsInstance<Stroke>() }
        if (strokes.isEmpty()) continue
        val model = runCatching { HandwritingRecognizer.recognize(page, engine = WinInkEngine) }
        val pure = runCatching { HandwritingRecognizer.recognize(page, engine = none) }
        val heights = strokes.map { HandwritingRecognizer.strokeBounds(it) }.map { it.bottom - it.top }.sorted()
        val median = heights[heights.size / 2]
        println(
            "handwriting: page $index strokes=${strokes.size} " +
                "medianStrokeHeight=${"%.1f".format(median)} " +
                "modelWords=${model.getOrNull()?.size} pureWords=${pure.getOrNull()?.size}",
        )
        describe("model", model.getOrNull().orEmpty())
        describe("pure", pure.getOrNull().orEmpty())
    }
}

/** The first words of one reading, with the box they cover, so a wrong grouping is visible too. */
private fun describe(reader: String, words: List<HandwrittenWord>) {
    words.take(25).forEach { word ->
        println(
            "handwriting:   $reader box=${"%.0f".format(word.bounds.left)}," +
                "${"%.0f".format(word.bounds.top)} " +
                "${"%.0f".format(word.bounds.right - word.bounds.left)}x" +
                "${"%.0f".format(word.bounds.bottom - word.bounds.top)} " +
                "text=${word.text} candidates=${word.candidates.take(4)}",
        )
    }
}

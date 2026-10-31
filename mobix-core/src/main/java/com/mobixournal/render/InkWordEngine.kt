package com.mobixournal.render

import com.mobixournal.format.model.Stroke

/**
 * The one call a **recogniser** has to answer: read a word out of the strokes that form it.
 *
 * Word and line segmentation, the bounds, and what a recognised word *is* are all shared
 * ([HandwritingRecognizer], [HandwrittenWord]); only the reading itself needs a model, and a model
 * needs a runtime. So the reading is this interface: the tablet plugs Google ML Kit's digital-ink
 * model in, a plain JVM has no model and keeps [FallbackInkEngine], and both answer in the same shape.
 *
 * Returning null means "I read nothing here" — the caller then asks the fallback rather than treating
 * the word as empty.
 */
fun interface InkWordEngine {

    /** The word [strokes] draw, or null when this engine cannot read them. */
    fun recognize(strokes: List<Stroke>, bounds: Bounds): HandwrittenWord?
}

/**
 * An engine that can read a whole **line** in one call, rather than a word at a time.
 *
 * A model that has to be reached through a heavy bridge pays for every call — Windows' recognizer, for
 * one, costs about 55 ms just to activate and about 17 ms to answer, so a page of 419 words took 34 s
 * read word by word. Reading a line is the same one call for all of its words, and a model segments a
 * connected cursive line better than an isolated word (`docs/search.md`).
 *
 * The answer is **one entry per word, in the order given** — null where this engine read nothing there,
 * which the caller fills with [FallbackInkEngine] as it does for a null [recognize].
 */
interface InkLineEngine : InkWordEngine {

    /** What the words of one line say, aligned with [words]; a null entry is a word left unread. */
    fun recognizeLine(words: List<List<Stroke>>, bounds: List<Bounds>): List<HandwrittenWord?>
}

/**
 * Which engine [HandwritingRecognizer] asks by default.
 *
 * A shell plugs its own engine **once, at startup**, and every later recognition — the tablet's index,
 * a spoken-to-ink document, a search — goes through it without threading an engine through the call
 * chain. [FallbackInkEngine] is the default because it is the only engine that needs nothing but
 * Kotlin: it is what a JVM test and the desktop window use, and what the tablet keeps when the ML Kit
 * models are still downloading.
 */
object InkEngines {

    @Volatile
    var platform: InkWordEngine = FallbackInkEngine
}

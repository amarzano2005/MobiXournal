package com.mobixournal.render

import com.mobixournal.format.model.Stroke

/**
 * The tablet's own reading of a word: Google ML Kit's digital-ink model, when a model is downloaded
 * and the call is off the main thread.
 *
 * This is the **only** part of recognition Android owns. Everything else — which strokes form a word,
 * where it sits, what to do when nothing is read — is shared with the desktop window
 * ([HandwritingRecognizer]), which plugs no engine at all and keeps [FallbackInkEngine].
 *
 * Answering null means "no model, or the model read nothing": the shared recogniser then asks
 * [FallbackInkEngine], exactly as it did before the engine became a seam.
 */
object AndroidInkEngine : InkWordEngine {

    override fun recognize(strokes: List<Stroke>, bounds: Bounds): HandwrittenWord? {
        if (!MlKitInkEngine.isAvailable()) return null
        val candidates = MlKitInkEngine.recognizeWord(strokes)
        if (candidates.isEmpty()) return null
        return HandwrittenWord(
            text = candidates.first(),
            bounds = bounds,
            confidence = 1.0,
            candidates = candidates.take(5),
        )
    }
}

package com.mobixournal.io

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * **Whether a save is writing right now**, as the tablet's chrome reads it.
 *
 * A save runs on a background thread (`MainActivity.inBackground`), so the control that started it has
 * nothing to hold on to unless someone says so. This is that someone: [begin]/[end] bracket the encode and
 * the Save control draws its turning arc from [saving] (`ui/SaveControl.kt`), so the reader is told the
 * document is being written without a notice laid over the page.
 *
 * It is module state rather than a field on the activity for one mechanical reason: `MainActivity` and
 * `EditorScreen` are both at their recorded line ceilings (`SourceStructureTest`), and threading a flag
 * through them is a parameter on two files that may not grow. Its subject is a single answer to a single
 * question, and `docs/data-path.md` owns the rule.
 *
 * It counts saves, not readers: two writes at once (a quiet auto save and a Save the reader pressed) are
 * still "writing", and the second one's [end] must not clear the first one's mark.
 */
object SaveActivity {

    /** True while at least one save is writing. Snapshot state, because the top bar draws it. */
    var saving by mutableStateOf(false)
        private set

    /** The writes in flight, so the mark clears on the last one rather than on the first. */
    private var writes = 0

    /** A save has started: say so, and keep saying it until [end]. */
    fun begin() {
        writes++
        saving = true
    }

    /** That save is done — written, refused, or failed; the mark goes when the last one ends. */
    fun end() {
        writes = (writes - 1).coerceAtLeast(0)
        saving = writes > 0
    }
}

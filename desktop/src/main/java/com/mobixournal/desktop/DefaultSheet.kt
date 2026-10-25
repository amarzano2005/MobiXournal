package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The sheet size a **new page** is born with, as the format section's *Set as default* stores it.
 *
 * The settings file is where the choice lives — it outlives the window — and this is what the panel reads,
 * so the *Default sheet* line changes the moment it is stored instead of on the next start. Seeded from
 * the file when the shell is built, which is the one time the two are known to agree.
 */
internal class DefaultSheet(private val settings: DesktopSettings) {

    /** The stored default, in points, portrait or landscape exactly as it was set. */
    var size: Pair<Double, Double> by mutableStateOf(settings.pageWidthPt to settings.pageHeightPt)
        private set

    /**
     * Store [widthPt] × [heightPt] for every new sheet.
     *
     * A size that is not a page is not a default: the settings writer refuses it too, and refusing it
     * here keeps the panel's reading and the file's content from disagreeing over a bad number.
     */
    fun store(widthPt: Double, heightPt: Double) {
        if (!(widthPt > 0.0) || !(heightPt > 0.0)) return
        settings.setDefaultPageSize(widthPt, heightPt)
        size = widthPt to heightPt
    }
}

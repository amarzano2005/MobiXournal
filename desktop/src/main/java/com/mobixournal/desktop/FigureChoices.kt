package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.design.PenPalette

/**
 * The **figure decisions**: the width a figure is drawn at and the variant a triangle or a trapezoid takes.
 *
 * One class rather than fields spread through the settings state, because they are one subject
 * ([FigureShape]) and the page reads them together — the drag asks for the width and the variant at the
 * same instant, and neither is allowed to be the stale half of a pair. The store is written on every
 * change, so a reopened window draws the figure the last one drew.
 */
internal class FigureChoices(private val store: DesktopSettings) {

    /** The width a figure is drawn at, in points: a figure has no pressure, so it is its own answer. */
    var width by mutableStateOf(store.figureWidth)
        private set

    /** The triangle's and trapezoid's answers — the variant a drag draws, and its angles. */
    var shape by mutableStateOf(store.figureShape())
        private set

    /** A width the reader picked in Settings ▸ Figures, held to the palette's own range. */
    fun chooseWidth(value: Float) {
        val clamped = PenPalette.clampWidth(value)
        width = clamped
        store.figureWidth = clamped
    }

    /** One figure's answer changed — the variant, or an angle a scalene one is built from. */
    fun chooseShape(value: FigureShape) {
        shape = value
        store.writeFigureShape(value)
    }

    /** Read both back from the store — after a settings backup has replaced it. */
    fun reload() {
        width = store.figureWidth
        shape = store.figureShape()
    }
}

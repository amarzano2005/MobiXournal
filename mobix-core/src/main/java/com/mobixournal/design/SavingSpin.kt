package com.mobixournal.design

/**
 * The **saving mark**: the quarter arc both shells draw in place of the Save glyph while a write is in
 * flight, in the colour that glyph wore.
 *
 * The numbers live here, in the platform-free core, for the reason the glyph catalogue does: the window
 * and the tablet each animate their own `Canvas`, and a motion that drifted between them would be two
 * products' spinner — one slower, one with a different sweep — saying the same thing. Neither shell
 * imports Compose here: this is the specification, and a drawing adapter per shell reads it
 * (`desktop/shell/SavingArc.kt`, `ui/SaveControl.kt`).
 *
 * Why a mark at all: a Save control that keeps offering a floppy while the bytes go out cannot answer the
 * question the person pressing it is asking — *did it save?* — and the badge-on-the-page alone left the
 * button saying nothing about what it was doing.
 */
object SavingSpin {

    /** One full turn, in milliseconds — brisk enough to read as work, slow enough not to strobe. */
    const val TURN_MS = 840

    /** How much of the circle the arc covers, in degrees: a quarter, closed with a round cap at each end. */
    const val SWEEP_DEGREES = 90f

    /** The arc's stroke, in the 24-unit box a glyph is drawn on — a touch lighter than a glyph's stem. */
    const val STROKE_UNITS = 2.4f

    /** The grid a glyph path is drawn on, so the stroke and the box scale together. */
    const val GLYPH_BOX = 24f
}

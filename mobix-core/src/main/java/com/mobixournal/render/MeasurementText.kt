package com.mobixournal.render

import java.util.Locale

/**
 * Points per millimetre — the figure labels and the dimension label both speak millimetres, because a
 * measurement on a page is read as a ruler reading, not as a typographic unit.
 */
const val MM_PER_PT: Double = 25.4 / 72.0

/** Shorter drags than this (pt) are a stray tap, not a measurement. */
private const val MIN_MEASURED_PT = 1.0

/**
 * Pure half of the dimension label: the measured length in pt rendered in millimetres, or null when
 * too short to be a measurement.
 *
 * It lives beside the ink compilers rather than with the label's painter because the picture a shape's
 * ink carries (and therefore every shell that draws it) needs the same spelling — a measurement the
 * desktop renders as "12.7 mm" and the tablet as "12.70 mm" would be two answers to one question.
 */
fun measurementText(lengthPt: Double): String? {
    if (lengthPt < MIN_MEASURED_PT) return null
    // A tenth of a millimetre is finer than any stylus places a point; more digits would be noise.
    return String.format(Locale.US, "%.1f mm", lengthPt * MM_PER_PT)
}

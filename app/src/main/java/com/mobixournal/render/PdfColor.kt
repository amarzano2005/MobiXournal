package com.mobixournal.render

import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.mobixournal.format.XoppColor.alpha
import com.mobixournal.format.XoppColor.red
import com.mobixournal.format.XoppColor.green
import com.mobixournal.format.XoppColor.blue

/**
 * ARGB colour helpers for [PDPageContentStream]. Converts Android's `0xAARRGGBB` format to
 * PDFBox's separate RGB parameters.
 */

/** Set the stroking colour from an ARGB int (`0xAARRGGBB`). */
fun PDPageContentStream.setStrokingArgb(argb: Int) {
    setStrokingColor(argb.red, argb.green, argb.blue)
}

/** Set the non-stroking (fill) colour from an ARGB int (`0xAARRGGBB`). */
fun PDPageContentStream.setNonStrokingArgb(argb: Int) {
    setNonStrokingColor(argb.red, argb.green, argb.blue)
}

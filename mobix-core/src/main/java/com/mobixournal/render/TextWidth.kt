package com.mobixournal.render

/**
 * How wide a piece of text really is, in the same units as its size — the answer only a shell with a
 * font engine can give.
 *
 * The format's own layer has no glyphs: [ElementBounds] approximates a string's width from a
 * per-character factor, which is fine for a hit test but a few points too wide for **centring** a
 * string in a cell — the shift is half the error, and on a narrow column it reads as text that is not
 * centred at all. So [TableInk] takes this when the caller has one: the tablet passes the
 * `android.graphics.Paint` that will draw the very same string, the desktop its
 * `TextMeasurer`, and the exports — which have no font engine at all — fall back to the estimate.
 *
 * The implementation must measure the text **at [sizePt]**, in points, not in screen pixels: the
 * width it returns is compared with a table's own cell widths, which are in points too.
 */
fun interface TextWidth {

    /** The advance of [text] at [sizePt], in points; [bold] picks the heavier face the ink carries. */
    fun of(text: String, sizePt: Double, bold: Boolean): Double
}

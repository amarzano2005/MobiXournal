package com.mobixournal.design

/**
 * **How big the import card may be, and when its two page fields stop sitting side by side** — one rule
 * for both shells, so the window and the tablet answer a narrow place the same way.
 *
 * The card used to ask for a fixed 420 dp: on a PC that is right, but a window can be dragged narrower, a
 * split-screen pane is a phone's width, and a reader who turns text up to 150 % has fields whose labels no
 * longer fit a row. None of that is a different design — it is the same card given less room — so the rule
 * is arithmetic rather than a second layout: the card takes what it is offered up to [MAX_WIDTH_DP], and
 * the range stays on one line only while both the width and the text scale leave it somewhere to stand.
 *
 * These are computed from the room the **caller** has (a `BoxWithConstraints` bound, a scene's size), not
 * from the screen: a dialog on a tablet in split screen lives in a pane, and its own box is the honest
 * measure. What to do with the answer lives in the shells — a [stacked] card puts each field on its own
 * line, and both bodies scroll inside the height they are given so the buttons never leave the screen.
 */
object ImportCardLayout {

    /** The card at its roomy width — exactly what the window has always drawn, and still the ceiling. */
    const val MAX_WIDTH_DP: Float = 420f

    /** Air between the card and the edge of the space it sits in, on each side. */
    const val EDGE_DP: Float = 16f

    /** Under this width the two page fields go on their own lines rather than sharing one. */
    const val STACK_WIDTH_DP: Float = 360f

    /** Above this text scale they do too, whatever the width: a 150 % label is a third wider. */
    const val STACK_FONT_SCALE: Float = 1.2f

    /**
     * The width to draw the card at in a space [availableDp] wide: the room it leaves after [EDGE_DP] on
     * each side, capped at [MAX_WIDTH_DP]. It **shrinks with the window** rather than holding a floor of
     * its own — a card that insisted on 240 dp in a 200 dp window would be clipped, and being narrow is
     * better than being cut off.
     */
    fun cardWidth(availableDp: Float): Float = (availableDp - 2 * EDGE_DP).coerceIn(0f, MAX_WIDTH_DP)

    /** Whether the range's two fields should stack, given the card's width and the reader's text scale. */
    fun stacked(widthDp: Float, fontScale: Float): Boolean =
        widthDp < STACK_WIDTH_DP || fontScale > STACK_FONT_SCALE
}

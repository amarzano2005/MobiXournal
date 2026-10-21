package com.mobixournal.render

/**
 * The handful of drawing tokens the **ink compilers** need, kept platform-free.
 *
 * A living entity is compiled to ordinary ink by `LivingInk` and its family compilers, and those
 * compilers are pure geometry — they must not reach into a shell's theme to find out how wide a figure
 * label is or how strong a wash is. These constants are the whole of what they need from it, so they
 * live here rather than in the Android canvas' token object (which also holds window-chrome colours the
 * compilers must never see).
 */
object InkTokens {

    /** The font a figure label inside compiled ink is written in. */
    const val FIGURE_LABEL_FONT = "Sans"

    /** Its size, in page points. */
    const val FIGURE_LABEL_SIZE_PT = 8.0

    /**
     * The alpha of a translucent fill — the drag-select band and a plot's shaded integral share it, so a
     * shade reads as a highlight over the graph rather than as a second curve.
     */
    const val BAND_FILL_ALPHA = 0x22
}

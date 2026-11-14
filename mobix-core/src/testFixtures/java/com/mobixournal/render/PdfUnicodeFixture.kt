package com.mobixournal.render

/**
 * The one text run both shells' PDF exports are measured against — *one fixture, both emitters*.
 *
 * A document must come out of the tablet's export and out of the window's with the same characters, so
 * the run that broke both (everything a base-14 font cannot carry) is written down once and read by
 * `PdfUnicodeExportTest` (app, `com.tom_roush` PDFBox) and `DesktopPdfUnicodeTest` (window,
 * `org.apache.pdfbox`). A shell that drifts shows up as one of the two tests disagreeing with this
 * constant, not as a page the owner finds exported wrong a release later.
 */
object PdfUnicodeFixture {

    /**
     * Accented Latin, a vulgar fraction and a superscript, Greek, Cyrillic, a subscript, a combining
     * accent, an astral emoji, and the CP1252 punctuation set — the characters the old code-range guard
     * dropped, plus the ones it let through wrongly.
     */
    const val MIXED = "café ñ ½ ² — αβγ Ж H₂ e\u0301 😀 …’“”€™•"

    /** A run WinAnsi carries whole: it must stay in a base-14 face, and add no font file to the export. */
    const val WIN_ANSI = "Hello café ñ ½ ² — “quoted” €5"

    /**
     * The four cases the old range guard got wrong, each on its own so a failure names the one that broke:
     * a character WinAnsi encodes **above** U+00FF (`€`, which the range dropped), a C1 control
     * (`\u0085`, which the range let through where WinAnsi means `…`), a surrogate pair (one astral
     * codepoint, which the range split into two substitutes) and a combining mark (`e` + U+0301, which must
     * survive as its own codepoint after the base letter).
     */
    const val CP1252_ABOVE_LATIN1 = "… – — ‘ ’ “ ” € ™ Š ž ƒ ‹ › † ‡ • ‰"
    const val CONTROL = "\u0085"
    const val ASTRAL = "😀"
    const val COMBINING = "e\u0301"
}

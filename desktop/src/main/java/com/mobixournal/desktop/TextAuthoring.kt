package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import com.mobixournal.format.model.TextElement

/**
 * The window's **Text tool**, as values instead of a gesture: where a press puts a box, what the box is
 * made of, and which key ends the typing.
 *
 * The tablet's text tool is a tap that leaves a box you write in; the window's is the same tap, drawn in
 * the canvas instead of in a dialog over the page. Everything that can be decided without Compose lives
 * here, so a JVM test owns the box's geometry and the commit rule rather than a scene having to be
 * driven to find out what a press means.
 *
 * A box is written **once**, on its commit: nothing is put on the page while it is being typed, so an
 * empty box leaves no element and no undo step behind — the same rule the tablet's own text commit
 * follows (`docs/text-and-markdown.md`).
 */

/** The font a new box is written with — Xournal++'s default CSS-ish family. */
internal const val TEXT_FONT: String = "Sans"

/** How much of the page's width a new box spans: a third, so a line of prose fits before it wraps. */
internal const val TEXT_WIDTH_FRACTION: Double = 1.0 / 3.0

/** A new box's size follows the pen's width: a thicker nib writes larger letters. */
internal const val TEXT_SIZE_PER_WIDTH: Double = 10.0

/** The smallest and largest size a pen width can ask for, in page points. */
internal const val TEXT_MIN_SIZE_PT: Double = 8.0
internal const val TEXT_MAX_SIZE_PT: Double = 48.0

/** One line's height as a multiple of the font's size — the painter's own ratio. */
internal const val TEXT_LINE_HEIGHT: Double = 1.3

/** The size a box is written at for a pen [widthPt] wide: the nib scaled, kept inside a readable range. */
internal fun textSizeFor(widthPt: Float): Double =
    (widthPt * TEXT_SIZE_PER_WIDTH).coerceIn(TEXT_MIN_SIZE_PT, TEXT_MAX_SIZE_PT)

/** The width of a new box on a page [pageW] points wide. */
internal fun textBoxWidth(pageW: Double): Double = pageW * TEXT_WIDTH_FRACTION

/**
 * The text element a press at ([x], [y]) starts: anchored at the press, one line high, a third of the
 * page wide, in [ink]'s colour.
 *
 * The anchor is the box's top-left, so the caret lands where the press did, and the box is kept inside
 * the sheet — a press near the right or the bottom edge would otherwise put the typing finger off the
 * paper, where a `.xopp` has no meaning.
 */
internal fun textPlacement(pageW: Double, pageH: Double, x: Double, y: Double, ink: DesktopInk): TextElement {
    val size = textSizeFor(ink.width)
    val line = size * TEXT_LINE_HEIGHT
    val left = x.coerceIn(0.0, (pageW - textBoxWidth(pageW)).coerceAtLeast(0.0))
    val top = y.coerceIn(0.0, (pageH - line).coerceAtLeast(0.0))
    return TextElement(
        font = TEXT_FONT,
        size = size,
        x = left,
        y = top,
        color = ink.colour,
        content = "",
    )
}

/**
 * A text box being typed — the element it will become, plus the page it belongs to, and the text as it
 * stands.
 *
 * It is a *draft*: the page is not edited until [TextBoxDraft.element] is committed, which is what keeps
 * an abandoned box from leaving a step in the undo history. Immutable, like every other value the window
 * holds, so Compose can compare two drafts and a test can read the one that was typed.
 */
data class TextBoxDraft(
    val tabKey: String,
    val page: Int,
    val pageW: Double,
    val pageH: Double,
    val x: Double,
    val y: Double,
    val size: Double,
    val colour: Int,
    val text: String = "",
) {

    /** The box's width in page points: a third of the page, the width the field draws at. */
    val width: Double get() = textBoxWidth(pageW)

    /** One line's height in page points. */
    val lineHeight: Double get() = size * TEXT_LINE_HEIGHT

    /** True while there is nothing to write — a commit of this box leaves no element behind. */
    val blank: Boolean get() = text.isBlank()

    /** The element this typing describes. */
    fun element(): TextElement = TextElement(
        font = TEXT_FONT,
        size = size,
        x = x,
        y = y,
        color = colour,
        content = text,
    )

    /** The same box with the typed text replaced. */
    fun withText(text: String): TextBoxDraft = copy(text = text)

    /**
     * True when ([px], [py]) is inside the box (plus [padPt] of slack) — a press there belongs to the
     * field rather than to a new box.
     */
    fun contains(px: Double, py: Double, padPt: Double = 0.0): Boolean =
        px >= x - padPt && px <= x + width + padPt &&
            py >= y - padPt && py <= y + lineHeight + padPt

    /** How many lines the typing needs, so the field grows with it instead of scrolling a one-line slit. */
    fun lines(): Int = text.count { it == '\n' } + 1
}

/** The draft a press at ([x], [y]) opens on [tab]'s page, in [ink]. */
internal fun textBoxAt(
    tab: DesktopTab,
    x: Double,
    y: Double,
    pageW: Double,
    pageH: Double,
    ink: DesktopInk,
): TextBoxDraft {
    val placement = textPlacement(pageW, pageH, x, y, ink)
    return TextBoxDraft(
        tabKey = tab.key,
        page = tab.page,
        pageW = pageW,
        pageH = pageH,
        x = placement.x,
        y = placement.y,
        size = placement.size,
        colour = placement.color,
    )
}

/** The one key a live box hands the shell, instead of swallowing the whole keyboard. */
enum class TextBoxKey {
    /** `Esc`: write the box (or drop it when there is nothing in it) and give the keyboard back. */
    COMMIT,
}

/**
 * What [key] means while a text box is up: `Esc` commits, and **nothing else is the shell's** — no `Space`
 * to pan, no `B` for the pen, no `Delete` — because every other key is a character being typed.
 */
internal fun textBoxCommand(key: Key): TextBoxKey? =
    if (key == Key.Escape) TextBoxKey.COMMIT else null

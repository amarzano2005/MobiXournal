package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.mobixournal.format.FontDescription
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import com.mobixournal.render.ElementBounds
import com.mobixournal.render.ElementRef

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
 *
 * A press on a box that is already there **re-opens it** — the tablet's rule: with the Text tool picked,
 * tapping words on the sheet edits those words instead of drawing a second box on top of them. The edit
 * keeps the box's own font description, size and colour, and its place in the page's z-order, so text
 * written by the tablet or by desktop Xournal++ stays exactly the text it was.
 */

/** The font a new box is written with — Xournal++'s default CSS-ish family. */
internal const val TEXT_FONT: String = "Sans"

/**
 * The families the lane offers, the same three the tablet's text dialog does.
 *
 * The format carries a free-form Pango description, so a file may name any family and it is kept: this
 * list is only what the picker *offers*, never what it accepts.
 */
internal val TEXT_FAMILIES: List<String> = listOf("Sans", "Serif", "Monospace")

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
 *
 * [typing] carries the text **and the caret**: a box opened on an existing element has to keep the caret
 * the user clicked into, and a draft built from the text alone would hand the field back a caret pinned
 * to the end after every keystroke — which is what made fixing a word in the middle impossible.
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
    /** The font description the box writes: a new box's is [TEXT_FONT], a re-opened one keeps its own. */
    val font: String = TEXT_FONT,
    /** What is typed and where the caret is — the one state the field hands back and forth. */
    val typing: TextFieldValue = TextFieldValue(),
    /** The box being re-typed, or null for a new one — what decides replace-in-place on commit. */
    val editing: TextElement? = null,
) {

    /** The typing, as the element and the painter read it. */
    val text: String get() = typing.text

    /** The box's width in page points: a third of the page, the width the field draws at. */
    val width: Double get() = textBoxWidth(pageW)

    /** One line's height in page points. */
    val lineHeight: Double get() = size * TEXT_LINE_HEIGHT

    /** True while there is nothing to write — a commit of this box leaves no element behind. */
    val blank: Boolean get() = text.isBlank()

    /**
     * The element this typing describes.
     *
     * A re-opened box keeps the **identity** and the unmodelled attributes of the element it replaces: the
     * id is what a `.mobix` incremental save and the export metadata key an element by, so a retype that
     * minted a fresh one would turn "the same box, corrected" into "one box removed, another added".
     */
    fun element(): TextElement = TextElement(
        font = font,
        size = size,
        x = x,
        y = y,
        color = colour,
        content = text,
        extraAttrs = editing?.extraAttrs ?: emptyMap(),
        id = editing?.id,
    )

    /** The same box with the typed text replaced (the caret going to its end, as typing does). */
    fun withText(text: String): TextBoxDraft = withField(TextFieldValue(text, TextRange(text.length)))

    /** The same box with the field's own answer — text **and** caret — taken over. */
    fun withField(value: TextFieldValue): TextBoxDraft = copy(typing = value)

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

/**
 * The draft a press at ([x], [y]) opens on [tab]'s page, in [ink]: the box already under the press when
 * there is one, a new box at the press otherwise.
 */
internal fun textBoxAt(
    tab: DesktopTab,
    x: Double,
    y: Double,
    pageW: Double,
    pageH: Double,
    ink: DesktopInk,
): TextBoxDraft {
    val hit = runCatching { tab.document.page(tab.page) }.getOrNull()?.let { textBoxHit(it, x, y) }
    if (hit != null) return editingBox(tab, pageW, pageH, hit)
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

/**
 * The text box at ([xPt], [yPt]) on [page], or null: the **top-most** one, since a box written over
 * another is the one the eye — and the press — lands on. Searched from the last layer backwards, the same
 * order and the same padded bounds the tablet's own pick uses (`ElementEdits.pickText`), so both shells
 * pick the same box at the same point.
 */
internal fun textBoxHit(page: Page, xPt: Double, yPt: Double): TextElement? {
    for (layer in page.layers.asReversed()) {
        for (element in layer.elements.asReversed()) {
            if (element !is TextElement) continue
            if (ElementBounds.of(element).expand(ElementBounds.TAP_PAD).contains(xPt, yPt)) return element
        }
    }
    return null
}

/**
 * The field a press on [element] opens: its own words, caret at the end of them, and its own face, size
 * and colour — so a retype corrects the box instead of restyling it.
 */
internal fun editingBox(
    tab: DesktopTab,
    pageW: Double,
    pageH: Double,
    element: TextElement,
): TextBoxDraft = TextBoxDraft(
    tabKey = tab.key,
    page = tab.page,
    pageW = pageW,
    pageH = pageH,
    x = element.x,
    y = element.y,
    size = element.size,
    colour = element.color,
    font = element.font,
    typing = TextFieldValue(element.content, TextRange(element.content.length)),
    editing = element,
)

/** A page rewritten by one text edit, plus the reference the selection should name afterwards. */
internal class TextRewrite(val page: Page, val ref: ElementRef?)

/**
 * [page] with the text box [old] replaced by [fresh] in the very place it held — or removed when [fresh]
 * is null. Null when [old] is not on the page.
 *
 * Identity, not equality: the box a press hit **is** the element the page holds, and two boxes with the
 * same words in the same place are still two different boxes. Keeping the layer and the index is what
 * makes a correction an edit of the page's order rather than a removal plus an append to the top layer.
 */
internal fun Page.replaceTextBox(old: TextElement, fresh: TextElement?): TextRewrite? {
    for ((layerIndex, layer) in layers.withIndex()) {
        val at = layer.elements.indexOfFirst { it === old }
        if (at < 0) continue
        val elements = layer.elements.toMutableList()
        if (fresh == null) elements.removeAt(at) else elements[at] = fresh
        val rewritten = copy(
            layers = layers.toMutableList().also { it[layerIndex] = layer.copy(elements = elements) },
        )
        return TextRewrite(rewritten, if (fresh == null) null else ElementRef(layerIndex, at))
    }
    return null
}

/** The one key a live box hands the shell, instead of swallowing the whole keyboard. */
enum class TextBoxKey {
    /** `Esc`: write the box (or drop it when there is nothing in it) and give the keyboard back. */
    COMMIT,
}

/**
 * [this] with its family changed — the bold and italic tokens it already carries are kept, because the
 * picker says *which face*, not *how much of it*.
 */
internal fun TextElement.withFamily(family: String): TextElement {
    val current = FontDescription.parse(font)
    return copy(font = current.copy(family = family.ifBlank { TEXT_FONT }).compose())
}

/** [this] with `Bold` added or taken away, the family and italic kept. */
internal fun TextElement.withBold(bold: Boolean): TextElement {
    val current = FontDescription.parse(font)
    return copy(font = current.copy(bold = bold).compose())
}

/** [this] with `Italic` added or taken away, the family and bold kept. */
internal fun TextElement.withItalic(italic: Boolean): TextElement {
    val current = FontDescription.parse(font)
    return copy(font = current.copy(italic = italic).compose())
}

/**
 * What [key] means while a text box is up: `Esc` commits, and **nothing else is the shell's** — no `Space`
 * to pan, no `B` for the pen, no `Delete` — because every other key is a character being typed.
 */
internal fun textBoxCommand(key: Key): TextBoxKey? =
    if (key == Key.Escape) TextBoxKey.COMMIT else null

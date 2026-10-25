package com.mobixournal.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.mobixournal.format.FontDescription
import kotlin.math.roundToInt

/**
 * The Text tool as the canvas receives it: the box being typed, the press that opens one, and what the
 * field hands back.
 *
 * One value instead of three parameters — the canvas's signature is the page's whole wiring already, and
 * a slot's parts belong together. The default is a slot with nothing up and nowhere to report to, which is
 * what a canvas driven by a test has.
 */
data class TextBoxUi(
    val box: TextBoxDraft? = null,
    val onPress: ((x: Double, y: Double, pageW: Double, pageH: Double) -> Unit)? = null,
    /**
     * The field's answer: the text **and** the caret. A string alone would lose the caret on every
     * keystroke, so a word could only ever be corrected at the end of the box.
     */
    val onChange: (TextFieldValue) -> Unit = {},
)

/**
 * The Text tool's **field, drawn on the page**: a real text box where the press landed, in the ink's own
 * colour and size, so what is typed is what the sheet will paint.
 *
 * The field is not a dialog over the page — the tablet's text tool writes in place, and a box that opened
 * elsewhere would have the user typing in one place while looking at another. It is mounted **inside the
 * canvas**, above the sheet's picture and below nothing else, positioned by the same mapping the ink is
 * transformed by (`page point × zoom + viewport`, in the canvas's pixels).
 *
 * It wears **no surface at all** — no fill, no rim, no padding. A field over paper that drew the window's
 * floating glass was a charcoal slab lying on the sheet, the one material class of
 * `docs/interface-contract.md` the element does not belong to: what is being typed is *the page's own
 * content*, the same thing a stroke being drawn is, and the ink drafts are not glass either. So the words
 * appear in the document's own colour and face at the document's own place — and, with the padding gone,
 * exactly where the commit will write them, instead of a few points off to the right. The caret is the
 * field's whole affordance: a token-coloured bar at the point the next character will land, which is also
 * the contract's visible-focus state.
 *
 * The typing is the caller's: [onChange] hands the field's value back and the shell keeps it in its
 * draft, so nothing reaches the document until the box is committed — an abandoned box leaves no element
 * and no undo step. `Enter` is a new line (the field is not single-line), and the shell owns `Esc`.
 *
 * A box opened on an existing element shows **that element's** words, caret in them, in its own face,
 * size and colour — and the sheet stops painting the element underneath while the field is up
 * (`PagePicture`'s skip), so the old text is not doubled behind the new.
 */
@Composable
internal fun TextBoxHost(
    box: TextBoxDraft?,
    zoom: Float,
    density: Float,
    offsetX: Float,
    offsetY: Float,
    onChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (box == null) return
    val focus = remember { FocusRequester() }
    // The sheet's own mapping: page points → canvas pixels, the same sum the ink is drawn with.
    val leftPx = ((box.x.toFloat() * zoom + offsetX) * density).roundToInt()
    val topPx = ((box.y.toFloat() * zoom + offsetY) * density).roundToInt()
    val face = textFace(box.font)
    val measurer = rememberTextMeasurer()
    val style = TextStyle(
        color = Color(box.colour),
        fontSize = (box.size.toFloat() * zoom).sp,
        fontFamily = face.family,
        fontWeight = face.weight,
        fontStyle = face.style,
        textAlign = TextAlign.Start,
    )
    // Focus per box, not per keystroke: a new press is a new box and should take the keyboard again —
    // and a box opened **on** existing words drops the caret where the press landed, which is the one
    // thing that makes correcting a word in the middle of a line a click rather than a run of arrows.
    // The index is the field's to work out: it measures the words with its own style and the same rule the
    // page paints them by (**no invented wrap width** — a `.xopp` text box carries no width, so a line is
    // wrapped by its own newlines and nothing else), which is what makes the caret land on the letter under
    // the pointer instead of one from a line the painter never drew.
    LaunchedEffect(box.tabKey, box.page, box.x, box.y) {
        runCatching { focus.requestFocus() }
        val editing = box.editing ?: return@LaunchedEffect
        val anchor = box.caretAnchorPx(zoom, density)
        val layout = runCatching {
            measurer.measure(AnnotatedString(editing.content), style = style)
        }.getOrNull() ?: return@LaunchedEffect
        val offset = layout.getOffsetForPosition(anchor)
        if (box.typing.selection.start != offset || box.typing.selection.end != offset) {
            onChange(box.typing.copy(selection = TextRange(offset)))
        }
    }
    Box(
        modifier
            .offset { IntOffset(leftPx, topPx) },
    ) {
        BasicTextField(
            // The draft *is* the field's state, caret included: a `TextFieldValue` rebuilt here would
            // move the caret to the end after every keystroke and no arrow or click could hold it
            // anywhere else.
            value = box.typing,
            onValueChange = onChange,
            textStyle = style,
            cursorBrush = SolidColor(DesktopTheme.accent),
            modifier = Modifier.focusRequester(focus),
        )
    }
}

/**
 * A `.xopp` font description as the face Compose paints: family, and the bold/italic its tokens name.
 *
 * Shared by the painter and the field, so the text being typed and the text that gets written are the
 * same face — a field in another font would show a line break in a different place than the page does,
 * and a `Sans Bold` read as plain Sans is a different box from the one the tablet and Xournal++ draw.
 */
internal data class TextFace(
    val family: FontFamily,
    val weight: FontWeight,
    val style: FontStyle,
)

internal fun textFace(font: String): TextFace {
    val description = FontDescription.parse(font)
    return TextFace(
        family = textFontFamily(description.family),
        weight = if (description.bold) FontWeight.Bold else FontWeight.Normal,
        style = if (description.italic) FontStyle.Italic else FontStyle.Normal,
    )
}

/**
 * Xournal++'s font families are CSS-ish names; map the ones we can name to a platform family.
 */
internal fun textFontFamily(family: String): FontFamily = when {
    family.contains("mono", ignoreCase = true) -> FontFamily.Monospace
    family.contains("serif", ignoreCase = true) && !family.contains("sans", ignoreCase = true) -> FontFamily.Serif
    else -> FontFamily.SansSerif
}

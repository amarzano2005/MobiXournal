package com.mobixournal.desktop.io
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.shell.WindowKeys

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.DesignTokens
import com.mobixournal.render.Bounds
import com.mobixournal.render.PdfBarBox
import com.mobixournal.render.PdfSelectionSpan
import com.mobixournal.render.PdfTextBarPlacement
import com.mobixournal.render.PdfTextHandle
import com.mobixournal.render.PdfTextHandlePoint
import kotlin.math.roundToInt

/**
 * What a PDF text selection carries on screen: the box its bar is anchored to, and what the bar does.
 *
 * A value rather than three parameters, so a canvas that has no text selection passes one `null` and the
 * bar does not exist — a Copy button over nothing would be chrome pretending to be a feature.
 *
 * The two **writing** verbs ([onHighlight], [onUnderline]) are null on a document the window opened
 * read-only and [editBlockedReason] then says why: the words can still be copied, and the bar keeps both
 * verbs in place, **disabled with the reason a screen reader reads** rather than silently removing them —
 * a reader who wonders where Underline went is told it is the page, not the button.
 */
internal data class PdfTextCopyUi(
    val bounds: Bounds?,
    val onCopy: () -> Unit,
    val onHighlight: (() -> Unit)? = null,
    /** The pen's own ink, written under each line of the passage by **Underline**. */
    val onUnderline: (() -> Unit)? = null,
    /** Why the writing verbs are unavailable, when they are — `Read-only document` for a locked page. */
    val editBlockedReason: String? = null,
    val onDeselect: (() -> Unit)? = null,
    /**
     * The washed bands of the selection, in page points — what the capsule hangs from, one per line, exactly
     * as the page is washed. Empty falls back to [bounds] as a single band, which is what a caller that only
     * knows the union (a test, a future shell) can hand over.
     */
    val spans: List<PdfSelectionSpan> = emptyList(),
    /** The band under the focus end of the range — the line the reader's pointer last touched. */
    val active: PdfSelectionSpan? = null,
    /**
     * False while a drag is still making the range: the range stays live and the capsule waits for the
     * release, so nothing chases the pointer and nothing covers the words being selected.
     */
    val settled: Boolean = true,
    /**
     * The selection's two endpoint **handles**, in page points — the dots the pointer drags and a keyboard
     * moves (`PdfTextHandles`). Empty when the range has no handles to offer (no text layer, a replaced index).
     */
    val handles: List<PdfTextHandlePoint> = emptyList(),
    /** The dot on the end the reader last touched, so the capsule can stand clear of it. */
    val focusHandle: PdfTextHandlePoint? = null,
    /** A counted move of one end (left/right arrow on the focused handle); true when the range changed. */
    val onHandleNudge: ((PdfTextHandle, Int) -> Boolean)? = null,
)

/**
 * The bar a **PDF text selection** wears, where the element selection's bar would stand: **Copy** and
 * **Highlight**.
 *
 * It sits on the same opaque 8 dp surface, at the same offset rule ([selectionBarOffset]) as the element bar,
 * so the window does not grow a second place for a selection's actions to appear. Two words and not three: the words
 * are already selected, so what a reader can do with them is take them elsewhere or mark them on the page —
 * and deselecting is a click on the page, which needs no button.
 *
 * **Its own size is measured, not assumed.** It used to place itself with a hard-coded 78 × 30 dp capsule,
 * which is true only at 100 % text and one font: a wider glyph, a larger scale or the window's own density
 * moved the capsule past the pane's edge while the clamp still believed it was 78 dp wide. The measured box
 * is what the clamp uses, so the bar stays inside the pane at every edge — and it grew a word without a
 * second hard-coded width to get wrong.
 *
 * The ring is the keyboard's: a bar in the middle of the page draws no focus of its own, so Tab landing on
 * Copy or Highlight has to be visible.
 */
@Composable
internal fun PdfTextCopyBar(
    ui: PdfTextCopyUi,
    zoom: Float,
    density: Float,
    offsetX: Float,
    offsetY: Float,
    canvas: Size,
    modifier: Modifier = Modifier,
) {
    if (ui.bounds == null || !ui.settled || canvas.width <= 0f || canvas.height <= 0f) return
    var barPx by remember { mutableStateOf(Size.Zero) }
    var focused by remember { mutableStateOf(false) }
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    val pxPerPt = zoom * density
    val origin = Offset(offsetX * density, offsetY * density)
    val bands = ui.spans.ifEmpty {
        ui.bounds?.let { listOf(PdfSelectionSpan(it.left, it.top, it.right, it.bottom)) } ?: emptyList()
    }
    // Placed beside the line the pointer last touched, from the measured capsule and the **current** bands: a
    // scroll or a zoom moves it with the page, and no spring is sent chasing anything. Null means no selected
    // line is visible in the pane, and the capsule is not drawn at all.
    val placed = PdfTextBarPlacement.offset(
        spans = bands.map { it.toBox(pxPerPt, origin) },
        active = ui.active?.toBox(pxPerPt, origin),
        barWidth = barPx.width,
        barHeight = barPx.height,
        canvasWidth = canvas.width,
        canvasHeight = canvas.height,
        gapPx = 8f * density,
        marginPx = 6f * density,
        // The endpoint's dot is a target, not a place to stand: a capsule over it would hide the grip the reader
        // is about to drag again (`PdfTextBarPlacement`).
        avoid = ui.focusHandle?.toBox(pxPerPt, origin),
    ) ?: return
    Box(
        modifier
            .onSizeChanged { barPx = Size(it.width.toFloat(), it.height.toFloat()) }
            .offset { IntOffset(placed.x.roundToInt(), placed.y.roundToInt()) },
    ) {
        Row(
            Modifier
                // Opaque, like the element bar: a glass capsule over white text greyed the words it was
                // meant to act on (`docs/interface-contract.md` §1).
                .glassSurface(cornerRadius = DesktopTheme.surfaceRadius)
                // The capsule shows no Deselect word — a click on the page and Escape already drop the
                // selection, and a third word was the widest thing in a two-word bar. It stays reachable as
                // this surface's accessibility action, so a keyboard reader keeps the same door.
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(DESELECT_ACTION) {
                            ui.onDeselect?.invoke()
                            ui.onDeselect != null
                        },
                    )
                }
                .onFocusChanged {
                    focused = it.isFocused
                    WindowKeys.focusControl(owner, focused)
                }
                // The actions carry their own targets now, so the capsule keeps only a hair of edge air.
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BarWord(label = "Copy", glyph = ChromeGlyph.COPY, onClick = ui.onCopy)
            // Both writing verbs keep their place on a read-only page, dimmed and explained: hiding them made
            // a reader wonder whether the app had the verb at all.
            BarWord(
                label = "Highlight",
                glyph = null,
                onClick = ui.onHighlight,
                blockedReason = ui.editBlockedReason,
            )
            BarWord(
                label = "Underline",
                glyph = null,
                onClick = ui.onUnderline,
                blockedReason = ui.editBlockedReason,
            )
        }
    }
}

/** The dismissal the bar no longer spells out, kept for assistive input. */
private const val DESELECT_ACTION = "Deselect"

/** A page-point band in window pixels, which is the placement's own currency. */
private fun PdfSelectionSpan.toBox(pxPerPt: Float, origin: Offset): PdfBarBox = PdfBarBox(
    left = (left * pxPerPt).toFloat() + origin.x,
    top = (top * pxPerPt).toFloat() + origin.y,
    right = (right * pxPerPt).toFloat() + origin.x,
    bottom = (bottom * pxPerPt).toFloat() + origin.y,
)

/** A handle's dot in the placement's currency: its screen target around the page-point centre. */
private fun PdfTextHandlePoint.toBox(pxPerPt: Float, origin: Offset): PdfBarBox = PdfBarBox(
    left = (x * pxPerPt).toFloat() + origin.x,
    top = (y * pxPerPt).toFloat() + origin.y,
    right = (x * pxPerPt).toFloat() + origin.x,
    bottom = (y * pxPerPt).toFloat() + origin.y,
)

/**
 * One word of the bar.
 *
 * The words are the bar's **targets**, so each is its own click — the capsule used to be one button because it
 * had one word, and a second verb inside a single target would have made the whole capsule ambiguous.
 *
 * Each word is now a compact pill on the shared 40 dp control: it used to be the bare label, whose own text was
 * the whole hit area — the smallest target on the window, inside the smallest bar. Its face is
 * [DesignTokens.COMPACT_ACTION_HEIGHT_DP]; the square around it, and the pill's padding, are what the pointer
 * actually gets. The focus ring moved onto the pill, so Tab says which **word** it landed on rather than ringing
 * the whole capsule.
 */
@Composable
private fun BarWord(label: String, glyph: ChromeGlyph?, onClick: (() -> Unit)?, blockedReason: String? = null) {
    var focused by remember { mutableStateOf(false) }
    val enabled = onClick != null
    Box(
        modifier = Modifier
            // A minimum, not a height: at 200 % text the pill grows with its label instead of cropping it,
            // and the target can only ever be larger than the shared control.
            .heightIn(min = DesignTokens.COMPACT_BUTTON_SIZE_DP.dp)
            .onFocusChanged { focused = enabled && it.hasFocus }
            .semantics {
                // The reason is part of the word, not a tooltip: a reader with a screen reader hears why the
                // action is unavailable instead of finding a button that does nothing.
                if (!enabled) {
                    disabled()
                    stateDescription = blockedReason ?: "Unavailable"
                }
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = { onClick?.invoke() }),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                // A word is a control of the bar: 4 dp corner, the pointer step, and the accent ring only
                // while the keyboard is on it — the same five states every other button has.
                .heightIn(min = 32.dp)
                .hoverFill(DesktopTheme.controlShape)
                .border(
                    width = 1.dp,
                    color = if (focused) DesktopTheme.accent else Color.Transparent,
                    shape = DesktopTheme.controlShape,
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (glyph != null) {
                Canvas(Modifier.size(14.dp)) { drawChromeGlyph(glyph, DesktopTheme.inkSecondary) }
            }
            Text(
                text = label,
                color = if (enabled) DesktopTheme.ink else DesktopTheme.ink.copy(alpha = 0.45f),
                style = DesktopTheme.label,
            )
        }
    }
}

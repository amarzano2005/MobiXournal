package com.mobixournal.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.render.PdfBarBox
import com.mobixournal.render.PdfTextBarPlacement
import com.mobixournal.ui.theme.liquidGlassSpringAnimation
import kotlin.math.roundToInt

/** Gap between the PDF text bar and the line it acts on. */
private val TEXT_BAR_GAP = 10.dp

/** Smallest distance the PDF text bar keeps from the canvas edges. */
private val TEXT_BAR_MARGIN = 8.dp

/**
 * The **PDF text selection's** bar: present only once the range has **settled**, and anchored to the **line the
 * reader is on**.
 *
 * Two things it used to get wrong, both of them rendering rather than behaviour. It was mounted for as long as
 * a range existed, so it followed the pointer through the whole drag — flashing over the words being selected
 * and moving under the finger. And it hung from the union of a multiline passage, which put it below the *last*
 * line however far the endpoint was, and on a passage filling the page it landed on the text itself. Now the
 * phase decides whether it exists at all ([com.mobixournal.render.PdfTextActionPhase.showsBar]) and the shared
 * [PdfTextBarPlacement] decides where: beside the endpoint's own band, above it when the pane ends below, clear
 * of the handle and inside the pane — and **nowhere** when the selection has been scrolled out of view.
 *
 * The offset is computed from the measured capsule and the current bands on every composition, so a scroll or a
 * zoom moves the bar with the page instead of sending a spring after it; the capsule itself still arrives with
 * the contract's presence motion, played **once** per visibility transition because that is the only time this
 * composable mounts.
 */
@Composable
internal fun TextSelectionAnchor(
    pane: PaneState,
    canvasSizePx: IntSize,
    onCopy: () -> Unit,
    onHighlight: () -> Unit,
    onUnderline: () -> Unit,
    onDeselect: () -> Unit,
    canEdit: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Measured before the gates, so a settled range that reappears (another selection, a scroll back into view)
    // is placed at its real size on the first frame instead of being parked at the margin and then moved.
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val closing = com.mobixournal.ui.theme.LocalFloatingSurfaceClosing.current
    val spans = com.mobixournal.ui.theme.rememberClosingArgument(
        pane.textSelectionSpans.takeIf { pane.textSelectionPhase.showsBar && pane.textSelectionRect != null },
    ) ?: return
    val active = com.mobixournal.ui.theme.rememberClosingArgument(pane.textSelectionActiveSpan)
    if (!closing && (!pane.textSelectionPhase.showsBar || pane.textSelectionRect == null)) return
    val gapPx = with(LocalDensity.current) { TEXT_BAR_GAP.roundToPx() }
    val marginPx = with(LocalDensity.current) { TEXT_BAR_MARGIN.roundToPx() }
    val placed = PdfTextBarPlacement.offset(
        spans = spans.map { it.toBarBox() },
        active = active?.toBarBox(),
        barWidth = barSize.width.toFloat(),
        barHeight = barSize.height.toFloat(),
        canvasWidth = canvasSizePx.width.toFloat(),
        canvasHeight = canvasSizePx.height.toFloat(),
        gapPx = gapPx.toFloat(),
        marginPx = marginPx.toFloat(),
    ) ?: return
    TextSelectionBar(
        onCopy = onCopy,
        onHighlight = onHighlight,
        onUnderline = onUnderline,
        onDeselect = onDeselect,
        canEdit = canEdit,
        modifier = modifier
            .offset { IntOffset(placed.x.roundToInt(), placed.y.roundToInt()) }
            // The app's one floating-surface motion, in place: the layer holds the glass and not just its rows.
            .liquidGlassSpringAnimation(origin = TransformOrigin(0.5f, 0f))
            .onSizeChanged { barSize = it },
    )
}

/** A reported view-pixel band, in the placement's own currency. */
private fun android.graphics.RectF.toBarBox(): PdfBarBox = PdfBarBox(left, top, right, bottom)

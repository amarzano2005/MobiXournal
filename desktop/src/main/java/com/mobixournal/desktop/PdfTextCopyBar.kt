package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.render.Bounds
import kotlin.math.roundToInt

/**
 * What a PDF text selection carries on screen: the box its Copy bar is anchored to, and what the bar
 * does.
 *
 * A value rather than two parameters, so a canvas that has no text selection passes one `null` and the
 * bar does not exist — a Copy button over nothing would be chrome pretending to be a feature.
 */
internal data class PdfTextCopyUi(val bounds: Bounds?, val onCopy: () -> Unit)

/** The bar's own size: one button, one glyph, one word. */
private val COPY_BAR_WIDTH = 78.dp
private val COPY_BAR_HEIGHT = 30.dp

/**
 * The Copy bar a **PDF text selection** wears, where the element selection's bar would stand.
 *
 * It is one button because the text selection has one verb: the words are already selected, and the only
 * thing a reader can do with them is take them elsewhere. It sits in the same glass capsule, at the same
 * offset rule ([selectionBarOffset]) as the element bar, so the window does not grow a second place for a
 * selection's actions to appear.
 */
@Composable
internal fun PdfTextCopyBar(
    ui: PdfTextCopyUi,
    zoom: Float,
    density: Float,
    offsetX: Float,
    offsetY: Float,
    canvas: Size,
) {
    if (ui.bounds == null || canvas.width <= 0f || canvas.height <= 0f) return
    val bar = Size(COPY_BAR_WIDTH.value * density, COPY_BAR_HEIGHT.value * density)
    Box(
        Modifier.offset {
            selectionBarOffset(
                bounds = ui.bounds,
                pxPerPt = zoom * density,
                originPx = Offset(offsetX * density, offsetY * density),
                canvas = canvas,
                bar = bar,
                gapPx = 8f * density,
            )
        },
    ) {
        Row(
            Modifier
                .glassCapsule(cornerRadius = DesktopTheme.panelRadius, shadow = DesktopTheme.bandShadow)
                .clickable { ui.onCopy() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Canvas(Modifier.size(14.dp)) { drawChromeGlyph(ChromeGlyph.COPY, DesktopTheme.inkSecondary) }
            Spacer(Modifier.width(2.dp))
            Text(
                text = "Copy",
                color = DesktopTheme.inkSecondary,
                style = DesktopTheme.label,
                modifier = Modifier.padding(end = 2.dp),
            )
        }
    }
}

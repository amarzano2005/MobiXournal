package com.mobixournal.desktop.io
import com.mobixournal.desktop.tools.glassCapsule
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.sheet.selectionBarOffset
import com.mobixournal.desktop.shell.WindowKeys

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.render.Bounds
import kotlin.math.roundToInt

/**
 * What a PDF text selection carries on screen: the box its bar is anchored to, and what the bar does.
 *
 * A value rather than three parameters, so a canvas that has no text selection passes one `null` and the
 * bar does not exist — a Copy button over nothing would be chrome pretending to be a feature. [onHighlight]
 * is null on a document the window opened read-only: the words can still be copied, but nothing may be
 * written on the page.
 */
internal data class PdfTextCopyUi(
    val bounds: Bounds?,
    val onCopy: () -> Unit,
    val onHighlight: (() -> Unit)? = null,
)

/**
 * The bar a **PDF text selection** wears, where the element selection's bar would stand: **Copy** and
 * **Highlight**.
 *
 * It sits in the same glass capsule, at the same offset rule ([selectionBarOffset]) as the element bar, so the
 * window does not grow a second place for a selection's actions to appear. Two words and not three: the words
 * are already selected, so what a reader can do with them is take them elsewhere or mark them on the page —
 * and deselecting is a click on the page, which needs no button.
 *
 * **Its own size is measured, not assumed.** It used to place itself with a hard-coded 78 × 30 dp capsule,
 * which is true only at 100 % text and one font: a wider glyph, a larger scale or the window's own density
 * moved the capsule past the pane's edge while the clamp still believed it was 78 dp wide. The measured box
 * is what the clamp uses, so the bar stays inside the pane at every edge — and it grew a word without a
 * second hard-coded width to get wrong.
 *
 * The ring is the keyboard's: a capsule in the middle of the page draws no focus of its own, so Tab landing
 * on Copy or Highlight has to be visible.
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
    if (ui.bounds == null || canvas.width <= 0f || canvas.height <= 0f) return
    var barPx by remember { mutableStateOf(Size.Zero) }
    var focused by remember { mutableStateOf(false) }
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    Box(
        modifier
            .onSizeChanged { barPx = Size(it.width.toFloat(), it.height.toFloat()) }
            .offset {
                selectionBarOffset(
                    bounds = ui.bounds,
                    pxPerPt = zoom * density,
                    originPx = Offset(offsetX * density, offsetY * density),
                    canvas = canvas,
                    bar = barPx,
                    gapPx = 8f * density,
                )
            },
    ) {
        Row(
            Modifier
                .glassCapsule(cornerRadius = DesktopTheme.panelRadius, shadow = DesktopTheme.bandShadow)
                .border(
                    width = 1.dp,
                    color = if (focused) DesktopTheme.accent else Color.Transparent,
                    shape = RoundedCornerShape(DesktopTheme.panelRadius),
                )
                .onFocusChanged {
                    focused = it.isFocused
                    WindowKeys.focusControl(owner, focused)
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BarWord(label = "Copy", glyph = ChromeGlyph.COPY, onClick = ui.onCopy)
            // A read-only document keeps its Copy and grows no Highlight: the verb would be a dead button.
            ui.onHighlight?.let { highlight ->
                BarWord(label = "Highlight", glyph = null, onClick = highlight)
            }
        }
    }
}

/**
 * One word of the bar.
 *
 * The words are the bar's **targets**, so each is its own click — the capsule used to be one button because it
 * had one word, and a second verb inside a single target would have made the whole capsule ambiguous.
 */
@Composable
private fun BarWord(label: String, glyph: ChromeGlyph?, onClick: () -> Unit) {
    Row(
        Modifier.clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (glyph != null) {
            Canvas(Modifier.size(14.dp)) { drawChromeGlyph(glyph, DesktopTheme.inkSecondary) }
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text = label,
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
            modifier = Modifier.padding(end = 2.dp),
        )
    }
}

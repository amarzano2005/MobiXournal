package com.mobixournal.desktop.io
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.tools.StripPill
import com.mobixournal.desktop.tools.StripSide
import com.mobixournal.desktop.tools.ToolStripMetrics

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.mobixournal.design.ChromeGlyph

/**
 * The toolbar slot for inserting an image element from disk onto the page canvas.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ImageSlot(onInsertImage: () -> Unit, side: StripSide) {
    var hovered by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val slotPx = with(density) { ToolStripMetrics.slotSize.roundToPx() }
    val gapPx = with(density) { ToolStripMetrics.tooltipGap.roundToPx() }
    Box {
        Box(
            Modifier
                .size(ToolStripMetrics.slotSize)
                .background(
                    if (hovered) DesktopTheme.hoverSlot else Color.Transparent,
                    DesktopTheme.railSlotShape,
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .pointerInput(onInsertImage) { detectTapGestures { onInsertImage() } },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(ToolStripMetrics.glyphSize)) {
                drawChromeGlyph(ChromeGlyph.PICTURE, DesktopTheme.chromeInk)
            }
        }
        if (hovered) {
            StripPill(
                label = "Insert image",
                accent = "",
                offset = when (side) {
                    StripSide.RIGHT -> IntOffset(slotPx + gapPx, 0)
                    StripSide.BELOW -> IntOffset(0, slotPx + gapPx)
                },
            )
        }
    }
}

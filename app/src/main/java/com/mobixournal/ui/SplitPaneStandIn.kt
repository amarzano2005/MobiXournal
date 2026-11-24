package com.mobixournal.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.mobixournal.format.model.Page
import com.mobixournal.render.drawSplitStandInPage
import com.mobixournal.ui.theme.rememberCanvasChromeColors

/**
 * Stand-in content rendered inside Split View's right overlay while the pane slides in/out or while
 * Pane 1's `DrawingSurfaceView` prepares its first live frame, preventing black-hole flashes.
 */
internal data class SplitStandInState(
    val page: Page? = null,
    val snapshot: Bitmap? = null,
    val tabId: String? = null,
)

/** Shared bridge between `MainActivityTabs`, `EditorPaneView`, and `SplitLayout` for Pane 1's stand-in. */
internal object SplitStandInBus {
    var state by mutableStateOf(SplitStandInState())
    var pane1Ready by mutableStateOf(false)
}

private val standInBlitPaint = Paint(Paint.FILTER_BITMAP_FLAG)

@Composable
internal fun SplitPaneStandIn(
    standIn: SplitStandInState,
    topInsetPxProvider: () -> Float,
    rightInsetPxProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val chrome = rememberCanvasChromeColors()
    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        val w = size.width.toInt()
        val h = size.height.toInt()
        if (w <= 0 || h <= 0) return@Canvas
        drawRect(color = Color(chrome.backdrop))
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val snap = standIn.snapshot
            if (snap != null && !snap.isRecycled) {
                native.drawBitmap(snap, null, RectF(0f, 0f, size.width, size.height), standInBlitPaint)
            } else {
                val page = standIn.page ?: return@drawIntoCanvas
                drawSplitStandInPage(
                    canvas = native,
                    page = page,
                    viewWidthPx = w,
                    viewHeightPx = h,
                    topInsetPx = topInsetPxProvider(),
                    rightInsetPx = rightInsetPxProvider(),
                    outlineColor = chrome.pageOutline,
                )
            }
        }
    }
}

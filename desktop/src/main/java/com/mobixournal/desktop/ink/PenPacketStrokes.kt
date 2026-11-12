package com.mobixournal.desktop.ink
import com.mobixournal.desktop.sheet.HudUi
import com.mobixournal.desktop.sheet.PageCanvas
import com.mobixournal.desktop.sheet.PageInkGesture
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.sheet.pageGestures
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.tools.DesktopTool

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.mobixournal.render.MagneticHighlighter
import com.mobixournal.render.ShapeRecognition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Let go of the packet source.
 *
 * The subscription stops; the stroke the packets were carrying is **committed where it stopped**, because a
 * hot-plug rebuild replaces the source under a line that may be half drawn and that ink is the user's; and
 * the Compose gesture is handed the pen back — the ownership flag is what stands it aside, and a flag stuck
 * on `true` would leave the next slot's drag (a figure, a grab line) unable to start at all.
 */
private fun releasePackets(stop: (() -> Unit)?, stroke: PenPacketStroke, owns: MutableState<Boolean>) {
    stop?.invoke()
    stroke.finish()
    owns.value = false
}

/**
 * The tablet's packets, drawing this canvas's stroke.
 *
 * The gesture is the canvas's **own**, not the one `pageGestures` builds per pointer gesture: a packet
 * stream has no beginning and no end to wrap, it simply stops when the pen lifts. Everything else is the
 * shared path — `PenPacketStroke` converts, `PageInkGesture` routes, the router and the pressure filter
 * decide — and this only keeps the subscription tied to the canvas's lifetime, plus the tick that finishes a
 * stroke whose packets stop arriving.
 *
 * [draft] is the pen's **own** list: the Compose pointer keeps `PageCanvas`'s other draft, so the emulated
 * mouse neither begins, extends nor ends this stroke.
 *
 * Its own file because it is the packet path and nothing else, and `PageCanvas` is already the page's
 * state, gestures and painter (`SourceStructureTest`).
 */
@Composable
internal fun PenPacketStrokes(
    tab: DesktopTab,
    tool: DesktopTool,
    ink: DesktopInk,
    density: Float,
    pageW: Float,
    pageH: Float,
    viewport: Viewport,
    draft: SnapshotStateList<InkSample>,
    onEdited: (Int) -> Unit,
    origin: () -> Offset,
    extent: () -> Size,
    owns: MutableState<Boolean>,
    /** Where the driver last put the pen, in this canvas's pixels — the ring follows it. */
    hover: MutableState<Offset?>,
    /** When that reading arrived, so a mouse move can tell whether the driver is still talking. */
    penAtNanos: MutableState<Long>,
    /** The magnetic highlighter's live bands, painted in place of the freehand preview. */
    bands: MutableState<List<MagneticHighlighter.Band>>? = null,
    /** The pen-tip ring: while it is up the packets answer it and lay no ink under it. */
    hud: HudUi = HudUi(),
    /** When freehand strokes snap to geometric primitives — on lift or on a held tip, never both. */
    recognition: ShapeRecognition = ShapeRecognition.OFF,
    packets: PenPacketSource?,
) {
    // Read live: a packet can arrive after a zoom, and it must map to the page the reader is looking at.
    val liveViewport by rememberUpdatedState(viewport)
    val gesture = remember(tab, tool, ink, pageW, pageH) {
        // `startOnDown = true`: a pen on the glass starts its stroke at the press, so a touch that never
        // travels is a dot and a very light stroke is not mistaken for a click.
        PageInkGesture(
            tab = tab,
            tool = tool,
            ink = ink,
            view = { liveViewport },
            pageW = pageW,
            pageH = pageH,
            density = density,
            draft = draft,
            onEdited = onEdited,
            startOnDown = true,
            bands = bands,
            recognition = recognition,
        )
    }
    // One ring update per frame, not one per packet: the driver sends 400 readings a second and each
    // write to [hover] repaints the canvas.
    val showAt: (Offset?) -> Unit = { at ->
        if (hover.value != at) hover.value = at
        penAtNanos.value = System.nanoTime()
    }
    val stroke = remember(gesture) {
        PenPacketStroke(
            gesture = gesture,
            origin = origin,
            size = extent,
            onOwnership = { owns.value = it },
            onHover = showAt,
            hud = hud,
            density = density,
        )
    }
    DisposableEffect(packets, stroke) {
        val stop = packets?.listen(stroke::packet)
        onDispose { releasePackets(stop, stroke, owns) }
    }
    // One publish per frame: the packets buffered since the last repaint are handed to the gesture
    // here, so 400 packets a second cost one invalidation per frame instead of 400 — and the same
    // point also notices a pen lifted without its last packet, which must not leave a stroke half drawn,
    // and a tip that has come to rest, whose figure the dwell puts in place of the preview.
    LaunchedEffect(stroke) {
        // The silence that ends a stroke is *absence*, and absence produces no packets to wait for — so it
        // is a timer, not a frame. It runs on `delay`, which asks for no repaint at all.
        launch {
            while (true) {
                delay(PenPacketStroke.STALE_CHECK_MS)
                val now = System.nanoTime()
                // Rest is the one event with no *move* to wait for — a held pen repeats its position, or
                // says nothing — so this silent timer is what lets a dwell replace the preview (`ShapeDwell`).
                gesture.dwellCheck(now)
                stroke.finishIfStale(now)
            }
        }
        // Frames are asked for by packets: applying batches here invalidates at next frame.
        while (true) {
            stroke.awaitPacket()
            stroke.flush()
            stroke.publishHover()
        }
    }
}

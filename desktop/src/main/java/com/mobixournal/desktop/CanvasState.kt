package com.mobixournal.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.toSize
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.render.SelectionRegion

/**
 * The page state a canvas keeps across recompositions — and releases with the page it belongs to.
 *
 * Lifted out of `PageCanvas` so the composable that owns a page is layout and wiring, and so the picture,
 * the drafts and the karaoke driver are remembered **once**, by one call, in the page's own keys.
 */
internal class CanvasState(
    val memory: CanvasMemory,
    val picture: PagePicture,
    /** While a clip sounds: the writing follows the recording (`DesktopKaraoke`). */
    val karaoke: DesktopKaraoke?,
)

/** The state for [tab]'s current page: its drafts, its recorded picture and its karaoke driver. */
@Composable
internal fun rememberCanvasState(tab: DesktopTab): CanvasState {
    val memory = remember(tab, tab.page) { CanvasMemory() }
    val picture = rememberSheetPicture(tab)
    val karaoke = rememberKaraoke(DesktopAudioSession, tab, tab.page)
    return remember(memory, picture, karaoke) { CanvasState(memory, picture, karaoke) }
}

/**
 * The pen's own state, lifted out of `PageCanvas`: whether the tablet's packets own the stroke in
 * progress, where the ring is, when the driver last spoke, and the vertical-space grab line.
 *
 * Two input paths write these (the packets, and the Compose moves of a window with no stream), which is
 * why they are remembered by the canvas rather than by the path that happens to run.
 */
internal class PenPointer {
    val owns = mutableStateOf(false)
    val hover = mutableStateOf<Offset?>(null)
    val atNanos = mutableStateOf(0L)
    val grabLine = mutableStateOf<Double?>(null)

    /** The region a selection drag is tracing right now: the sheet paints it, the gesture writes it. */
    val previewRegion = mutableStateOf<SelectionRegion?>(null)

    /**
     * The living element a placement drag is drawing right now — a component being pulled out along the
     * pen, shown exactly as it will land. Nothing of it is on the page until the lift.
     */
    val previewLiving = mutableStateOf<Element?>(null)
}

/**
 * The page state a canvas keeps across recompositions: the two strokes in progress and the pen's own
 * readings.
 *
 * Remembered **per tab and page**, so a page change starts clean, and lifted out of the composable because
 * the canvas is already the page's layout and wiring (`SourceStructureTest` counts both).
 */
internal class CanvasMemory {
    /** The Compose pointer's stroke, in page pt. */
    val draft: SnapshotStateList<InkSample> = mutableStateListOf()

    /** The tablet's stroke, drawn from its packets — a draft of its own, painted beside [draft]. */
    val penDraft: SnapshotStateList<InkSample> = mutableStateListOf()

    val pointer = PenPointer()

    /**
     * Where this canvas sits in the window, in its client pixels — a packet arrives in the window's own
     * space and the canvas resolves it against this (`PenPacketStroke`). Kept here rather than as two
     * locals so both readings are written by one call and a canvas keeps them across recompositions.
     */
    var origin by mutableStateOf(Offset.Zero)
        private set
    var extent by mutableStateOf(Size.Zero)
        private set

    /** The canvas's box, as the layout reports it: its corner and its size. */
    fun track(coordinates: LayoutCoordinates) {
        origin = coordinates.boundsInWindow().topLeft
        extent = coordinates.size.toSize()
    }
}

/** The page the tab is on, re-read when the document is edited; a failure is why it could not be read. */
@Composable
internal fun rememberedPage(tab: DesktopTab): Result<Page> =
    remember(tab, tab.page, tab.document.edits) { runCatching { tab.document.page(tab.page) } }

/**
 * The page's recorded picture, released with the page it belongs to: it holds native drawing
 * instructions, so a page outliving its picture would leak them.
 */
@Composable
internal fun rememberSheetPicture(tab: DesktopTab): PagePicture {
    val picture = remember(tab, tab.page) { PagePicture() }
    DisposableEffect(picture) { onDispose { picture.close() } }
    return picture
}

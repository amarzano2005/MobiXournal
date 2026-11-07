package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.InkSample
import com.mobixournal.desktop.ink.PenContactStream
import com.mobixournal.desktop.settings.DesktopNavigationState
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.toolPointerIcon
import com.mobixournal.desktop.tools.DesktopTool

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * The sheet's **input surface**, as one modifier: the tool's pointer, the pen's readings, the wheel and the
 * gestures a press can start.
 *
 * The pointer over the page is the *tool's*: a ring is the pointer while a pen, a highlighter or an eraser is
 * in hand (an arrow beside it pointed at nothing), a hand for the hand, and the desktop's arrow for every
 * other slot (`DesktopCursor.toolPointerIcon`). The pen-tip ring does not change it: its faces are aimed at
 * with that same ring, which its layer paints above the glass. Split out of the composable so the surface that
 * owns a page stays layout and wiring (`SourceStructureTest`), and out of `PageCanvas` itself because the
 * input a page takes is its own topic: what the wheel does, what a press starts, and — since the Scrolling
 * settings are about scrolling rather than about the wheel — how far the hand travels and how it lets go.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.sheetInput(
    tab: DesktopTab,
    panMode: Boolean,
    tool: DesktopTool,
    ink: DesktopInk,
    shapeInk: DesktopInk,
    pen: PenContactStream?,
    sheet: SheetLayout,
    pointer: PenPointer,
    draft: SnapshotStateList<InkSample>,
    /** Whether the tool draws a ring: the pointer is blank behind it, the arrow otherwise. */
    ringVisible: Boolean,
    /** A mouse move that is a copy of the contact must not drag the ring back (`PenPacketStroke`). */
    followMouse: () -> Boolean,
    dialWheel: () -> Boolean,
    targets: PagePressTargets,
    density: Float,
    onViewport: (Viewport) -> Unit,
    onFit: (FitMode) -> Unit,
    onEdited: (Int) -> Unit,
    hud: HudUi = HudUi(),
    navigation: DesktopNavigationState,
    scroller: SmoothScroller,
): Modifier = this
    .pointerHoverIcon(toolPointerIcon(tool, ringVisible = ringVisible, panMode = panMode))
    .hoverTracking(pointer, followMouse)
    .pageWheelScroll(tab, sheet, dialWheel, density, navigation, scroller, onFit, onViewport)
    .sheetGestures(
        tab, panMode, tool, ink, shapeInk, pen, sheet, pointer, draft, targets, onViewport, onEdited,
        hud = hud,
        // A pen's press arrives twice; while the driver's packets are the pen's voice, their own lift ends
        // the contact a ring answered — the mouse copy must not hand the page that contact a frame early.
        penVoice = { !followMouse() },
        // The hand takes the viewport from the wheel's glide, before it writes its own travel.
        cancelGlide = { scroller.cancel() },
        // Settings ▸ Scrolling is about scrolling, not about the wheel: the hand travels the same
        // distance per dp, and carries on after the release only while *Smooth scrolling* is on — with
        // the same fluidity curve, because it is the same glide (`flingBy`).
        handGain = handScrollGain(navigation.scrollStepDp),
        onFling = { dx, dy -> flingHand(scroller, navigation, tab, sheet, onViewport, dx, dy) },
    )

/**
 * The hand's release, as Settings ▸ Scrolling describes it: nothing when *Smooth scrolling* is off (the sheet
 * stops where it was left), otherwise the flick's projected travel through the fluidity a notch glides with.
 */
private fun flingHand(
    scroller: SmoothScroller,
    navigation: DesktopNavigationState,
    tab: DesktopTab,
    sheet: SheetLayout,
    onViewport: (Viewport) -> Unit,
    dx: Float,
    dy: Float,
) {
    if (!navigation.smoothScroll) return
    scroller.flingBy(
        dx = dx, dy = dy,
        currentViewport = { tab.viewport },
        pageW = sheet.pageW, pageH = sheet.pageH,
        viewW = sheet.viewW, viewH = sheet.viewH,
        fluidity = navigation.scrollFluidity,
        onViewport = onViewport,
    )
}

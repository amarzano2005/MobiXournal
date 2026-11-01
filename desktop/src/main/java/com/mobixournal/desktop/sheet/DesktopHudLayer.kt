package com.mobixournal.desktop.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.onGloballyPositioned
import com.mobixournal.desktop.settings.DesktopTheme

/**
 * The pen-tip ring over a page, and the two readings it needs from the canvas.
 *
 * The ring is drawn in a canvas of its own, laid over the sheet's: the page's paint (replayed from its
 * recorded picture) stays exactly what it was, and a surface that comes and goes costs it nothing. Both
 * surfaces fill the same box, so the ring's pixels are the page's pixels and the anchor the shell is handed
 * is the nib the page's own gestures read.
 *
 * A pen that hovers reports where it is before any button is pressed, which is what lets the ring open under
 * the nib rather than at a remembered corner — [PageCanvas]'s `nibReporting` publishes that place, and this
 * layer follows the hover so a flick lights the slot it is over.
 */
@Composable
internal fun DesktopHudLayer(hud: HudUi, memory: CanvasMemory, density: Float) {
    val hover = memory.pointer.hover.value
    LaunchedEffect(hover) { hover?.let { hud.onPointerAt(it, density) } }
    val open = hud.open() ?: return
    Canvas(Modifier.fillMaxSize()) { drawDesktopRadialHud(open, hud.slots, density) }
}

/**
 * The ring's own press, while it is up: a slot under the point commits, a press inside the ring does nothing,
 * one well outside it puts the ring away — and the rest of the gesture is drained, so the lift cannot become
 * the start of a stroke under a surface that had just closed.
 *
 * It is asked **before** the page's other paths, because the ring is what the precise hand is using: a
 * contact aimed at a face must not also lay ink on the page the ring floats over.
 */
internal suspend fun AwaitPointerEventScope.ringTakesThePress(hud: HudUi, at: Offset, density: Float) {
    hud.onPress(at.x, at.y, density)
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull() ?: break
        change.consume()
        if (change.changedToUpIgnoreConsumed() || !change.pressed) break
    }
}

/**
 * The canvas's own surface: the black desk behind the page, a clip so nothing paints outside it, and the
 * **nib's place** reported to the shell — the centre of the canvas until a pointer has been over the page at
 * all, which is the only answer a keyboard summon has when the mouse has never entered the window.
 *
 * It is a modifier of its own because the ring's anchor is a reading of the *layout* (`onGloballyPositioned`
 * runs the moment the canvas has a size), where a `LaunchedEffect` would arrive a frame late and leave the
 * ring anchored at 0, 0 for the first summon.
 */
internal fun Modifier.nibReporting(memory: CanvasMemory, density: Float, hud: HudUi): Modifier =
    background(DesktopTheme.desk)
        .clipToBounds()
        .onGloballyPositioned { coordinates ->
            memory.track(coordinates)
            val at = memory.pointer.hover.value
                ?: Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
            hud.onPointerAt(at, density)
        }

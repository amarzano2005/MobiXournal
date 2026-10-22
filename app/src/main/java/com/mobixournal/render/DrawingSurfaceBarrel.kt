/**
 * [DrawingSurfaceView]'s barrel-button half of the input layer: the key-event path a Bluetooth pen's
 * button takes, the press/release edges both streams share, the click action a double-click runs, and
 * the quick-action executor the barrel and the finger taps both use.
 *
 * Split out of `DrawingSurfaceInput.kt` by subject (the file budget, `SourceStructureTest`): the
 * routing of a gesture belongs there, the button that changes what the next gesture means belongs
 * here. The latch and the detectors it feeds are [BarrelButtonState], [BarrelClickDetector] and
 * [VendorClickGate] — pure, JVM-tested, and unchanged by the move.
 */
package com.mobixournal.render

import android.view.KeyEvent
import android.view.MotionEvent
import com.mobixournal.format.model.Tool

/**
 * Treat a stylus barrel *button key* as the barrel modifier — the path a Bluetooth pen's button takes
 * on Android 14 and later, where button presses arrive as `KeyEvent`s as well as, or instead of,
 * `MotionEvent` button bits (see [BarrelButtonState]). Returns true when [event] was a barrel button,
 * so the caller can consume it rather than let it do something else.
 */
fun DrawingSurfaceView.onStylusButtonKey(event: KeyEvent): Boolean {
    // The event's own source plus the whole device's: a pen's button may be tagged on either, and the
    // broader check ([BarrelButtonState.isBarrelKey]) is what catches a vendor pen that reports its
    // button as an undocumented, characterless key.
    val source = event.source or (event.device?.sources ?: 0)
    val firmwareClick = isVendorKeycode(event.keyCode)
    if (!BarrelButtonState.isBarrelKey(event.keyCode, source, event.unicodeChar, firmwareClick)) return false
    when (event.action) {
        // Auto-repeat is dropped here rather than deeper in: a held Bluetooth key repeats, and every
        // repeat would otherwise read as a fresh click of the pen's button.
        KeyEvent.ACTION_DOWN ->
            if (event.repeatCount == 0) pressBarrelButton(event.eventTime, firmwareClick = firmwareClick)
        KeyEvent.ACTION_UP -> releaseBarrelButton()
    }
    return true
}

/**
 * True when Android has no name for [keyCode], so it can only be a **vendor** HID code and never a
 * real keyboard key. Android names every key code it defines (`KeyEvent.keyCodeToString` answers
 * `KEYCODE_<NAME>`), and falls back to the bare number for one it does not know — which is how the
 * Honor Choice Pencil's side button presents itself: key code `755`, source `SOURCE_KEYBOARD`, from a
 * device named "HONOR CHOICE Pencil" (that is the line the diagnostics panel reports). Treating those
 * as the barrel is what makes the button work on a pen whose firmware invented its own key code,
 * instead of the app having to learn one code per pen. `KEYCODE_UNKNOWN` itself is never a button.
 */
internal fun isVendorKeycode(keyCode: Int): Boolean {
    if (keyCode == 0) return false
    val name = KeyEvent.keyCodeToString(keyCode)
    return name == "KEYCODE_UNKNOWN" || name == keyCode.toString()
}

/**
 * A barrel press edge, however it was reported: latch it, repaint the preview, run the click action.
 *
 * [firmwareClick] marks a press that came from a pen whose firmware already did the recognising — an
 * unnamed vendor key code, which such pens send once for a whole physical double-click. This edge *is*
 * the gesture then: waiting for a second edge would mean waiting forever, which is exactly how the
 * button reads as dead. [VendorClickGate] keeps it to one action per gesture all the same, and two
 * further rules exist so that a press is never lost:
 *
 *  - The press does **not** depend on the latch *changing*: a pen that never sends the key up (or
 *    sends it where the app never sees it) would otherwise leave the latch held and silence every
 *    later press for good.
 *  - A click is a discrete command, not a held modifier, so it is **not** dropped while the pen is on
 *    the glass: the stroke drawn so far is finished first and then the action applies. A click made
 *    right after erasing, without lifting the pen, is exactly the click a user makes.
 */
internal fun DrawingSurfaceView.pressBarrelButton(eventTime: Long, firmwareClick: Boolean = false) {
    val changed = barrelButton.onKey(true)
    if (changed && hovering) render()
    // The HUD is the button's action when Settings says so: open on the press, commit on the key-up.
    // It replaces both the click action and the held modifier, which is what choosing it means.
    if (barrelSummonsHud) {
        summonRadialHud()
        return
    }
    if (firmwareClick) {
        if (!vendorClicks.fires(eventTime)) {
            notePenDebug("CLICK swallowed (same gesture)")
            return
        }
        if (current != null || erasing) endGesture()
        runBarrelClickAction()
        return
    }
    if (!changed) return
    // A double-click is a button-only gesture: with the tip down the button is the held modifier
    // instead, and firing an undo mid-stroke would be exactly wrong.
    if (current != null || erasing) return
    if (!barrelClicks.press(eventTime)) return
    runBarrelClickAction()
}

/**
 * Run the configured barrel click action and say so in the diagnostics panel: the event lines say
 * what arrived, this says what the app made of it.
 */
internal fun DrawingSurfaceView.runBarrelClickAction() {
    val action = inputSettings.barrelDoubleAction
    notePenDebug("CLICK -> " + action.name.lowercase().replace('_', ' '))
    runQuickAction(action)
}

/** The barrel button was let go (a key-up): the held modifier is over. */
internal fun DrawingSurfaceView.releaseBarrelButton() {
    if (barrelButton.onKey(false) && hovering) render()
    // The release is the HUD's commit: the slot under the pen runs, or (over no slot) nothing does.
    if (hud != null) dismissRadialHud(commit = true)
}

/** The on-screen tool collapsed to the classifier's [ActiveTool]. */
internal fun DrawingSurfaceView.activeTool(): ActiveTool = when {
    verticalSpaceMode -> ActiveTool.VERTICAL_SPACE
    placeKind != null -> ActiveTool.PLACE
    handMode -> ActiveTool.HAND
    backgroundSelectMode -> ActiveTool.BACKGROUND_SELECT
    textSelectMode -> ActiveTool.TEXT_SELECT
    selectMode -> ActiveTool.SELECT
    tool == Tool.ERASER -> ActiveTool.ERASER
    tool == Tool.HIGHLIGHTER -> ActiveTool.HIGHLIGHTER
    else -> ActiveTool.PEN
}

/** Feed one off-glass event's button state to [barrelClicks] and run the action it completes. */
internal fun DrawingSurfaceView.trackBarrelClicks(event: MotionEvent) {
    val changed = barrelButton.onMotion(event.buttonState, buttonKindOf(event))
    // Holding the barrel arms the eraser before the tip ever lands, so the hover preview has to
    // swap to the tip outline on the button edge — not on the first touch.
    if (changed && hovering) render()
    // Only an edge changes anything: a held button repeats its bit on every hover move.
    if (!changed) return
    if (!barrelButton.held) {
        // The button's motion-stream release is the HUD's commit, exactly as the key-up is.
        if (hud != null) dismissRadialHud(commit = true)
        return
    }
    if (barrelSummonsHud) {
        summonRadialHud()
        return
    }
    // Only a press *edge* is a click: a held button repeats its bit on every hover move.
    if (!barrelClicks.press(event.eventTime)) return
    runQuickAction(inputSettings.barrelDoubleAction)
}

/**
 * Run a [QuickAction] — the one executor behind both the barrel double-click and the canvas's
 * two/three-finger taps, so the same choice behaves the same whichever gesture asked for it.
 */
internal fun DrawingSurfaceView.runQuickAction(action: QuickAction) {
    when (action) {
        QuickAction.NONE -> Unit
        QuickAction.UNDO -> undo()
        QuickAction.REDO -> redo()
        // The ring's second summon: the click that completes the double-click opens it, and that
        // press's release is its commit — the held-HUD gesture with one click in front of it.
        QuickAction.HUD -> summonRadialHud()
        // TOGGLE_ERASER / TOGGLE_SELECT / TOGGLE_FULL_PAGE are applied by the editor, which owns the
        // UI state a toggle needs; the surface just reports the action.
        else -> onQuickAction?.invoke(action)
    }
}

package com.mobixournal.ui

import androidx.compose.runtime.Composable
import com.mobixournal.render.QuickAction

/**
 * The label a [QuickAction] wears wherever the user picks one — the barrel button's double-click and
 * the finger taps — so the same choice is never named two ways.
 */
fun quickActionLabel(action: QuickAction): String = when (action) {
    QuickAction.NONE -> "Off"
    QuickAction.UNDO -> "Undo"
    QuickAction.REDO -> "Redo"
    QuickAction.TOGGLE_ERASER -> "Toggle eraser"
    QuickAction.TOGGLE_SELECT -> "Toggle select"
    QuickAction.TOGGLE_FULL_PAGE -> "Toggle full page"
    QuickAction.HUD -> "Radial HUD"
}

/**
 * The canvas's finger gestures: what a still **two-finger tap** and a still **three-finger tap** do.
 *
 * Both are eyes-free gestures for the hand not holding the stylus, so each is a choice from one
 * shared vocabulary ([QuickAction]) rather than a fixed meaning — undo and redo by default, because
 * that is what most writers want without reaching for the toolbar, but a tool or chrome flip is just
 * as reasonable a pairing. `Off` switches a tap out entirely: a finger tap that silently rewrites the
 * document has to be something the user chose.
 *
 * A tap only counts while it stays still — the fingers must not travel past the touch slop, and the
 * whole gesture must finish inside a third of a second — so panning, pinching and drawing never
 * trigger one.
 */
@Composable
fun FingerGesturesSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Two-finger tap") {
        TapActionPicker(
            selected = settings.twoFingerTapAction,
            onSelect = { onChange(settings.copy(twoFingerTapAction = it)) },
            hint = "Tap with two fingers and let go without moving them.",
        )
    }
    SettingsGroup("Three-finger tap") {
        TapActionPicker(
            selected = settings.threeFingerTapAction,
            onSelect = { onChange(settings.copy(threeFingerTapAction = it)) },
            hint = "Tap with three fingers and let go without moving them.",
        )
    }
}

/** One tap's action, as a labelled radio group over the shared quick actions. */
@Composable
private fun TapActionPicker(selected: QuickAction, onSelect: (QuickAction) -> Unit, hint: String) {
    OptionGroup(
        title = "Action",
        subtitle = "$hint A tap that turns into a pan or a pinch never fires it.",
        // The ring is anchored at the nib and closed by the button's release; a tap has neither, so
        // `HUD` is the barrel double-click's alone and is kept out of this list.
        options = QuickAction.values().filter { it != QuickAction.HUD },
        selected = selected,
        label = { quickActionLabel(it) },
        onSelect = onSelect,
    )
}

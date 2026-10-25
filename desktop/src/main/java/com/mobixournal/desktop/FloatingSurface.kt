package com.mobixournal.desktop

import androidx.compose.ui.window.PopupProperties

/**
 * The properties every floating surface of the window opens with: **it never takes the keyboard**.
 *
 * A Compose Desktop `Popup` is focusable by default, and a focusable popup takes the window's keyboard
 * focus while it is up — including a tool's hover pill, which appears under the pointer as it passes over
 * the strip. The window's shortcuts are dispatched from the root of the composition, so every one of
 * those surfaces silently turned the keyboard off until the reader clicked back into the page: the pen
 * stayed where it was and `Ctrl+Z`, `G`, `Esc` did nothing, which reads as the shortcuts "stopping after
 * some buttons".
 *
 * None of these surfaces edits text — the ones that do (the colour panel, page setup, the inspector's
 * fields, a tab's rename) are overlays inside the window rather than popups, so they keep their focus
 * normally. A popup is a menu: it closes on a pick or on a click outside, and neither needs the keyboard.
 */
internal val FloatingSurface = PopupProperties(focusable = false, dismissOnClickOutside = true)

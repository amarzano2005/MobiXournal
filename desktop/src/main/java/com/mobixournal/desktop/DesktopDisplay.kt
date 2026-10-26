package com.mobixournal.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import java.awt.DisplayMode
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment

/**
 * The **screen**, as the window can ask it for a rate.
 *
 * A refresh rate is not a preference the window can apply to itself: it is the panel's own mode, and
 * changing it is `GraphicsDevice.setDisplayMode`, which only a full-screen window has any business doing.
 * So the Display section's target rate is offered **while the window is full screen** and nowhere else
 * (`docs/desktop-workstation.md` → *Display*), and this file is the whole of that conversation with the
 * platform: the rates offered, the mode a target resolves to, and the request itself.
 *
 * It is an interface rather than a direct `GraphicsDevice` call because the interesting half is a
 * **refusal**: a laptop panel has no 144 Hz mode, a remote session refuses every mode change, and in both
 * cases the only correct behaviour is to leave the display exactly as it was and keep reporting its real
 * rate in the status bar. That decision is testable here; the platform call behind it is one line.
 */
internal interface DisplaySurface {

    /** The mode the display is in right now. */
    val current: DisplayMode

    /** Every mode the display advertises. Empty when it will not say. */
    val modes: List<DisplayMode>

    /** Ask for [mode]; false when the platform refused or could not apply it. */
    fun request(mode: DisplayMode): Boolean
}

/** The rates the Display section offers as a target, in the order the row shows them. */
internal val TARGET_HZ = listOf(60, 90, 120, 144)

/** The target the window asks for on a machine that has never chosen one: the 120 Hz it draws at. */
internal const val DEFAULT_TARGET_HZ = 120

/** The largest and smallest targets the store will keep, so a hand-edited file cannot ask for 0 Hz. */
internal val TARGET_HZ_RANGE = TARGET_HZ.first()..TARGET_HZ.last()

/**
 * The display the window draws on, or null when there is none to ask (a headless run, or a device that
 * refuses mode changes at all — a remote desktop session).
 */
internal fun screenSurface(): DisplaySurface? {
    if (GraphicsEnvironment.isHeadless()) return null
    return runCatching {
        val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        if (device.isDisplayChangeSupported) AwtSurface(device) else null
    }.getOrNull()
}

/** The platform's answer, narrowed to [DisplaySurface]: one device, and the mode it is in. */
private class AwtSurface(private val device: GraphicsDevice) : DisplaySurface {

    override val current: DisplayMode get() = device.displayMode

    override val modes: List<DisplayMode>
        get() = runCatching { device.displayModes?.toList().orEmpty() }.getOrDefault(emptyList())

    /**
     * Ask for [mode] and answer whether the display is now in it.
     *
     * The platform throws for an unsupported mode and silently ignores some others, so the answer is read
     * back from the device rather than trusted: a refusal that reported success is exactly the case the
     * status bar must not be lied to.
     */
    override fun request(mode: DisplayMode): Boolean = runCatching {
        device.displayMode = mode
        device.displayMode.refreshRate == mode.refreshRate
    }.getOrDefault(false)
}

/**
 * The mode to ask for at [hz]: the display's own **resolution and depth** at exactly that rate, or null
 * when it does not advertise one.
 *
 * Resolution is not negotiable — changing the target rate must never also resize the desktop — and the
 * rate is matched exactly rather than approximated, because a 90 Hz target answered with 60 Hz is a
 * setting that did nothing while looking applied.
 */
internal fun modeFor(surface: DisplaySurface, hz: Int): DisplayMode? {
    val current = surface.current
    return surface.modes.firstOrNull { mode ->
        mode.refreshRate == hz &&
            mode.width == current.width &&
            mode.height == current.height &&
            mode.bitDepth == current.bitDepth
    }
}

/**
 * Ask [surface] for [hz]; false when it offers no such mode or refused.
 *
 * A **false answer changes nothing**: a display with no 144 Hz mode keeps the mode it had, and the status
 * bar keeps reporting the rate it is really at (`refreshLabel`). Returning true is the only case where the
 * window may say something happened.
 */
internal fun requestRefresh(surface: DisplaySurface?, hz: Int): Boolean {
    if (surface == null) return false
    val current = surface.current
    if (current.refreshRate == hz) return true
    val mode = modeFor(surface, hz) ?: return false
    return surface.request(mode)
}

/**
 * The window's **whole-screen mode**: the window's placement, and the display mode the target asked for.
 *
 * Entering remembers both — the placement to go back to, and the display's own mode — so pressing `F11`
 * twice is a round trip rather than a second decision. Leaving restores the placement first (the window
 * must be back in its own frame before the panel changes mode under it) and then the display's rate.
 */
internal object FullScreenMode {

    private var placement: WindowPlacement? = null
    private var mode: DisplayMode? = null

    /**
     * Take the whole screen, and ask the display for [hz]; a refusal leaves the panel's mode alone.
     *
     * Called again with another target while the window is already out there, it only re-asks — the
     * placement it has to go back to is the one saved the first time.
     */
    fun enter(window: ComposeWindow, hz: Int, surface: DisplaySurface? = screenSurface()) {
        if (placement == null) {
            placement = window.placement
            mode = surface?.current
            window.placement = WindowPlacement.Fullscreen
        }
        requestRefresh(surface, hz)
    }

    /** Give the screen back: the window's own placement, then the display's own rate. */
    fun leave(window: ComposeWindow?, surface: DisplaySurface? = screenSurface()) {
        if (placement == null) return
        if (window != null) runCatching { window.placement = placement ?: WindowPlacement.Floating }
        placement = null
        val restore = mode
        mode = null
        if (restore != null) runCatching { surface?.takeIf { it.current != restore }?.request(restore) }
    }

    /** True while the window is on the whole screen, as this object knows it. */
    val active: Boolean get() = placement != null
}

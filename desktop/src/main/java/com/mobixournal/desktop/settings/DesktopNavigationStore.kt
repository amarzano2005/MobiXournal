package com.mobixournal.desktop.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Properties

val LocalNavigationState = staticCompositionLocalOf {
    DesktopNavigationState(DesktopNavigationStore(Properties()) {})
}

/**
 * Desktop navigation preferences: zoom rate per step (mouse wheel, dial, keyboard) and scrolling behavior.
 *
 * Stored under `navigation.*` in `desktop.properties`.
 */
class DesktopNavigationStore(
    private val properties: Properties,
    private val save: () -> Unit,
) {
    /** Wheel and dial zoom factor rate per notch (default 0.12 = 12%). */
    var zoomStepWheel: Float
        get() = properties.getProperty(KEY_ZOOM_STEP_WHEEL)?.toFloatOrNull()?.coerceIn(ZOOM_WHEEL_MIN, ZOOM_WHEEL_MAX)
            ?: DEFAULT_ZOOM_STEP_WHEEL
        set(value) {
            properties.setProperty(KEY_ZOOM_STEP_WHEEL, value.coerceIn(ZOOM_WHEEL_MIN, ZOOM_WHEEL_MAX).toString())
            save()
        }

    /** Keyboard zoom multiplier for Ctrl + and Ctrl - (default 1.25 = 25%). */
    var zoomStepKeyboard: Float
        get() = properties.getProperty(KEY_ZOOM_STEP_KEYBOARD)?.toFloatOrNull()?.coerceIn(ZOOM_KEYBOARD_MIN, ZOOM_KEYBOARD_MAX)
            ?: DEFAULT_ZOOM_STEP_KEYBOARD
        set(value) {
            properties.setProperty(KEY_ZOOM_STEP_KEYBOARD, value.coerceIn(ZOOM_KEYBOARD_MIN, ZOOM_KEYBOARD_MAX).toString())
            save()
        }

    /** Whether mouse wheel scrolling glides smoothly between notches. */
    var smoothScroll: Boolean
        get() = properties.getProperty(KEY_SMOOTH_SCROLL)?.toBooleanStrictOrNull() ?: DEFAULT_SMOOTH_SCROLL
        set(value) {
            properties.setProperty(KEY_SMOOTH_SCROLL, value.toString())
            save()
        }

    /** Distance in dp the sheet travels per wheel notch (default 64 dp). */
    var scrollStepDp: Float
        get() = properties.getProperty(KEY_SCROLL_STEP_DP)?.toFloatOrNull()?.coerceIn(SCROLL_STEP_MIN, SCROLL_STEP_MAX)
            ?: DEFAULT_SCROLL_STEP_DP
        set(value) {
            properties.setProperty(KEY_SCROLL_STEP_DP, value.coerceIn(SCROLL_STEP_MIN, SCROLL_STEP_MAX).toString())
            save()
        }

    /** Scroll fluidity: 0.10 (snappy) to 0.90 (soft glide), default 0.60. */
    var scrollFluidity: Float
        get() = properties.getProperty(KEY_SCROLL_FLUIDITY)?.toFloatOrNull()?.coerceIn(FLUIDITY_MIN, FLUIDITY_MAX)
            ?: DEFAULT_SCROLL_FLUIDITY
        set(value) {
            properties.setProperty(KEY_SCROLL_FLUIDITY, value.coerceIn(FLUIDITY_MIN, FLUIDITY_MAX).toString())
            save()
        }

    fun resetToDefaults() {
        properties.remove(KEY_ZOOM_STEP_WHEEL)
        properties.remove(KEY_ZOOM_STEP_KEYBOARD)
        properties.remove(KEY_SMOOTH_SCROLL)
        properties.remove(KEY_SCROLL_STEP_DP)
        properties.remove(KEY_SCROLL_FLUIDITY)
        save()
    }

    companion object {
        const val KEY_ZOOM_STEP_WHEEL = "navigation.zoomStepWheel"
        const val KEY_ZOOM_STEP_KEYBOARD = "navigation.zoomStepKeyboard"
        const val KEY_SMOOTH_SCROLL = "navigation.smoothScroll"
        const val KEY_SCROLL_STEP_DP = "navigation.scrollStepDp"
        const val KEY_SCROLL_FLUIDITY = "navigation.scrollFluidity"

        const val DEFAULT_ZOOM_STEP_WHEEL = 0.12f
        const val DEFAULT_ZOOM_STEP_KEYBOARD = 1.25f
        const val DEFAULT_SMOOTH_SCROLL = true
        const val DEFAULT_SCROLL_STEP_DP = 64f
        const val DEFAULT_SCROLL_FLUIDITY = 0.60f

        val ZOOM_WHEEL_MIN = 0.02f
        val ZOOM_WHEEL_MAX = 0.30f
        val ZOOM_WHEEL_STEP = 0.01f

        val ZOOM_KEYBOARD_MIN = 1.05f
        val ZOOM_KEYBOARD_MAX = 1.75f
        val ZOOM_KEYBOARD_STEP = 0.05f

        val SCROLL_STEP_MIN = 16f
        val SCROLL_STEP_MAX = 160f
        val SCROLL_STEP_STEP = 8f

        val FLUIDITY_MIN = 0.10f
        val FLUIDITY_MAX = 0.90f
        val FLUIDITY_STEP = 0.05f

        /** Converts fluidity (0.10..0.90) into exponential decay per second (higher = settles faster). */
        fun decayRate(fluidity: Float): Float {
            val clamped = fluidity.coerceIn(FLUIDITY_MIN, FLUIDITY_MAX)
            return 8f + (1f - clamped) * 40f
        }
    }
}

/**
 * Live Compose state for [DesktopNavigationStore], updating settings reactively.
 */
class DesktopNavigationState(private val store: DesktopNavigationStore) {
    var zoomStepWheel by mutableStateOf(store.zoomStepWheel)
        private set
    var zoomStepKeyboard by mutableStateOf(store.zoomStepKeyboard)
        private set
    var smoothScroll by mutableStateOf(store.smoothScroll)
        private set
    var scrollStepDp by mutableStateOf(store.scrollStepDp)
        private set
    var scrollFluidity by mutableStateOf(store.scrollFluidity)
        private set

    fun chooseZoomStepWheel(value: Float) {
        val clamped = value.coerceIn(DesktopNavigationStore.ZOOM_WHEEL_MIN, DesktopNavigationStore.ZOOM_WHEEL_MAX)
        zoomStepWheel = clamped
        store.zoomStepWheel = clamped
    }

    fun chooseZoomStepKeyboard(value: Float) {
        val clamped = value.coerceIn(DesktopNavigationStore.ZOOM_KEYBOARD_MIN, DesktopNavigationStore.ZOOM_KEYBOARD_MAX)
        zoomStepKeyboard = clamped
        store.zoomStepKeyboard = clamped
    }

    fun chooseSmoothScroll(value: Boolean) {
        smoothScroll = value
        store.smoothScroll = value
    }

    fun chooseScrollStepDp(value: Float) {
        val clamped = value.coerceIn(DesktopNavigationStore.SCROLL_STEP_MIN, DesktopNavigationStore.SCROLL_STEP_MAX)
        scrollStepDp = clamped
        store.scrollStepDp = clamped
    }

    fun chooseScrollFluidity(value: Float) {
        val clamped = value.coerceIn(DesktopNavigationStore.FLUIDITY_MIN, DesktopNavigationStore.FLUIDITY_MAX)
        scrollFluidity = clamped
        store.scrollFluidity = clamped
    }

    fun resetToDefaults() {
        store.resetToDefaults()
        reload()
    }

    fun reload() {
        zoomStepWheel = store.zoomStepWheel
        zoomStepKeyboard = store.zoomStepKeyboard
        smoothScroll = store.smoothScroll
        scrollStepDp = store.scrollStepDp
        scrollFluidity = store.scrollFluidity
    }
}

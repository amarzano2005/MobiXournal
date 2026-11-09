package com.mobixournal.desktop

import com.mobixournal.desktop.settings.DesktopNavigationState
import com.mobixournal.desktop.settings.DesktopNavigationStore
import com.mobixournal.desktop.sheet.SmoothScroller
import com.mobixournal.desktop.sheet.Viewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class NavigationSettingsTest {

    @Test
    fun `navigation store loads factory defaults when properties are empty`() {
        val props = Properties()
        var saved = false
        val store = DesktopNavigationStore(props) { saved = true }

        assertEquals(DesktopNavigationStore.DEFAULT_ZOOM_STEP_WHEEL, store.zoomStepWheel, 0.001f)
        assertEquals(DesktopNavigationStore.DEFAULT_ZOOM_STEP_KEYBOARD, store.zoomStepKeyboard, 0.001f)
        assertEquals(DesktopNavigationStore.DEFAULT_SMOOTH_SCROLL, store.smoothScroll)
        assertEquals(DesktopNavigationStore.DEFAULT_SCROLL_STEP_DP, store.scrollStepDp, 0.001f)
        assertEquals(DesktopNavigationStore.DEFAULT_SCROLL_FLUIDITY, store.scrollFluidity, 0.001f)
        assertFalse(saved)
    }

    @Test
    fun `navigation store saves and coerces within defined ranges`() {
        val props = Properties()
        var savedCount = 0
        val store = DesktopNavigationStore(props) { savedCount++ }

        store.zoomStepWheel = 0.20f
        assertEquals(0.20f, store.zoomStepWheel, 0.001f)
        assertEquals("0.2", props.getProperty(DesktopNavigationStore.KEY_ZOOM_STEP_WHEEL))

        // Clamping check for zoom
        store.zoomStepWheel = 0.99f
        assertEquals(DesktopNavigationStore.ZOOM_WHEEL_MAX, store.zoomStepWheel, 0.001f)

        store.zoomStepKeyboard = 1.50f
        assertEquals(1.50f, store.zoomStepKeyboard, 0.001f)

        store.smoothScroll = false
        assertFalse(store.smoothScroll)

        store.scrollStepDp = 120f
        assertEquals(120f, store.scrollStepDp, 0.001f)

        store.scrollFluidity = 0.85f
        assertEquals(0.85f, store.scrollFluidity, 0.001f)

        assertTrue(savedCount >= 5)

        // Reset to defaults
        store.resetToDefaults()
        assertEquals(DesktopNavigationStore.DEFAULT_ZOOM_STEP_WHEEL, store.zoomStepWheel, 0.001f)
        assertEquals(DesktopNavigationStore.DEFAULT_SMOOTH_SCROLL, store.smoothScroll)
    }

    @Test
    fun `navigation state updates reactively and resets cleanly`() {
        val props = Properties()
        val store = DesktopNavigationStore(props) {}
        val state = DesktopNavigationState(store)

        state.chooseZoomStepWheel(0.08f)
        assertEquals(0.08f, state.zoomStepWheel, 0.001f)
        assertEquals(0.08f, store.zoomStepWheel, 0.001f)

        state.chooseSmoothScroll(false)
        assertFalse(state.smoothScroll)
        assertFalse(store.smoothScroll)

        state.chooseScrollStepDp(96f)
        assertEquals(96f, state.scrollStepDp, 0.001f)

        state.chooseScrollFluidity(0.40f)
        assertEquals(0.40f, state.scrollFluidity, 0.001f)

        state.resetToDefaults()
        assertEquals(DesktopNavigationStore.DEFAULT_ZOOM_STEP_WHEEL, state.zoomStepWheel, 0.001f)
        assertEquals(DesktopNavigationStore.DEFAULT_SMOOTH_SCROLL, state.smoothScroll)
        assertEquals(DesktopNavigationStore.DEFAULT_SCROLL_STEP_DP, state.scrollStepDp, 0.001f)
    }

    @Test
    fun `decay rate decreases as fluidity increases`() {
        val snappyDecay = DesktopNavigationStore.decayRate(0.10f)
        val balancedDecay = DesktopNavigationStore.decayRate(0.60f)
        val glidingDecay = DesktopNavigationStore.decayRate(0.90f)

        assertTrue("snappy decay should be higher than balanced", snappyDecay > balancedDecay)
        assertTrue("balanced decay should be higher than gliding", balancedDecay > glidingDecay)
    }

    @Test
    fun `smooth scroller accumulates target and can be cancelled`() {
        // The standalone settings fixture needs its own clock; cancellation is tested before any frame.
        val scope = CoroutineScope(Job() + androidx.compose.runtime.BroadcastFrameClock())
        val scroller = SmoothScroller(scope)

        var vp = Viewport(zoom = 1f, offsetX = 0f, offsetY = -500f)

        scroller.scrollBy(
            dx = 0f,
            dy = -100f,
            currentViewport = { vp },
            pageW = 600f,
            pageH = 2000f,
            viewW = 800f,
            viewH = 600f,
            fluidity = 0.60f,
            onViewport = { vp = it },
        )

        assertEquals(-600f, scroller.targetY, 0.001f)
        assertTrue(scroller.isRunning)

        // Cancel stops glide
        scroller.cancel()
        assertFalse(scroller.isRunning)
    }
}

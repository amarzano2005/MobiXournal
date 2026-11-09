package com.mobixournal.desktop

import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.HAND_GAIN_MAX
import com.mobixournal.desktop.sheet.HAND_GAIN_MIN
import com.mobixournal.desktop.sheet.PEN_GAIN
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.sheet.WHEEL_STEP_DP
import com.mobixournal.desktop.sheet.handScrollGain
import com.mobixournal.desktop.sheet.notchTravelDp
import com.mobixournal.desktop.sheet.penScrollGain
import com.mobixournal.desktop.shell.DesktopShell
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.selectTool
import com.mobixournal.format.mobix.SyntheticNotebook

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **hand pan** as a drag: the sheet follows the pointer, it keeps its place when something
 * else scrolls it mid-gesture, and it answers the pen whatever button the emulation reports for it.
 *
 * The second claim is the one that is easy to lose: the pen's own packets pan this same sheet from the
 * driver's readings (`PenPacketPan`), so a drag that measured its travel from the offset it saw at the press
 * wrote that stale position back on its next event — the page travelled with the hand and then jumped home,
 * which on the tablet read as a scroll that bounced instead of scrolling.
 *
 * A wheel **glide** is the third writer of that offset and is not covered here: a `withFrameNanos`
 * animation does not run on `ImageComposeScene`'s clock (a scene rendered in a tight loop is not the same
 * clock as a window's), so a test of "the wheel, then the hand" in this harness measures the harness. The
 * rule it has to keep is stated where it lives: a glide ends when it settles, writes nothing after it is
 * cancelled, and lets go the moment another writer moves the sheet (`SmoothScroller`).
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopHandPanTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 1000
    private val viewH = 700

    private fun notebook(): File = temp.newFile("hand-pan.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    /**
     * A page larger than the window on both axes at 200 %, so a pan has a range to travel in: a page that
     * fits is centred by the clamp, which would pin the offset and answer nothing either way.
     */
    private fun state(): DesktopShellState = DesktopShellState(returning()).also { state ->
        state.open(notebook())
        val tab = state.tabs.active!!
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 2f, offsetX = 0f, offsetY = 0f)
        state.selectTool(DesktopTool.HAND)
    }

    /**
     * This test's own settings, with the first run's format question already answered — the window would
     * otherwise open on that card, whose blocker takes the very drags these pans are about.
     */
    private fun returning() = DesktopSettings(File(temp.root, "hand-pan.properties")).also {
        it.documentFormatChosen = true
    }

    @Test
    fun `the hand pans the sheet with the pointer`() {
        val state = state()
        val tab = state.tabs.active!!
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            event(scene, PointerEventType.Press, 400f, 400f, pressed = true, at = 1_000L)
            event(scene, PointerEventType.Move, 400f, 350f, pressed = true, at = 1_016L)
            // Up the page by 50 px, and the sheet goes with the hand: exactly the travel reported.
            assertEquals("the drag moves the sheet with the pointer", -50f, tab.viewport.offsetY, 0.5f)
            assertEquals("and not across it", 0f, tab.viewport.offsetX, 0.5f)

            event(scene, PointerEventType.Move, 400f, 300f, pressed = true, at = 1_032L)
            assertEquals("a second move adds to the first, never repeats it", -100f, tab.viewport.offsetY, 0.5f)
            event(scene, PointerEventType.Release, 400f, 300f, pressed = false, at = 1_048L)
        } finally {
            scene.close()
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a pan carries on from where another writer left the sheet`() {
        val state = state()
        val tab = state.tabs.active!!
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            event(scene, PointerEventType.Press, 400f, 400f, pressed = true, at = 1_000L)
            event(scene, PointerEventType.Move, 400f, 350f, pressed = true, at = 1_016L)
            assertEquals(-50f, tab.viewport.offsetY, 0.5f)

            // The pen's packets scroll this very sheet between two mouse events: one viewport write, the
            // way `PenPacketPan.flush` makes it.
            tab.viewport = tab.viewport.copy(offsetY = -150f)
            Snapshot.sendApplyNotifications()
            scene.render()

            // The next event is the drag's own 50 px, measured from where the sheet is **now**.
            event(scene, PointerEventType.Move, 400f, 300f, pressed = true, at = 1_032L)
            assertEquals(
                "the sheet must not jump back to where the gesture began",
                -200f,
                tab.viewport.offsetY,
                0.5f,
            )
            event(scene, PointerEventType.Release, 400f, 300f, pressed = false, at = 1_048L)
        } finally {
            scene.close()
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a stylus in the hand slot pans the sheet`() {
        val state = state()
        val tab = state.tabs.active!!
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            // The pen reaches Compose as a stylus, not a mouse: the pan must not depend on the emulated
            // mouse's button bits, or a host whose tablet sends no packets loses the hand entirely.
            stylus(scene, PointerEventType.Press, 400f, 400f, pressed = true, at = 1_000L)
            stylus(scene, PointerEventType.Move, 400f, 350f, pressed = true, at = 1_016L)
            assertEquals("the stylus pans the sheet", -50f, tab.viewport.offsetY, 0.5f)
            stylus(scene, PointerEventType.Move, 400f, 300f, pressed = true, at = 1_032L)
            assertEquals("and keeps going", -100f, tab.viewport.offsetY, 0.5f)
            stylus(scene, PointerEventType.Release, 400f, 300f, pressed = false, at = 1_048L)
        } finally {
            scene.close()
            state.tabs.closeAll()
        }
    }

    /**
     * A pen contact the emulation reports with **no button bit at all** — the shape that made the Hand slot
     * a dead gesture: the pan was gated on the primary bit, which this contact never carries.
     */
    @Test
    fun `a pen contact that reports no button still pans the hand slot`() {
        val state = state()
        val tab = state.tabs.active!!
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            stylus(scene, PointerEventType.Press, 400f, 400f, true, 1_000L, buttons = PointerButtons())
            stylus(scene, PointerEventType.Move, 400f, 350f, true, 1_016L, buttons = PointerButtons())
            assertEquals(
                "the slot decides the pan, not the emulation's button state",
                -50f,
                tab.viewport.offsetY,
                0.5f,
            )
            stylus(scene, PointerEventType.Release, 400f, 350f, false, 1_032L, buttons = PointerButtons())
        } finally {
            scene.close()
            state.tabs.closeAll()
        }
    }

    /**
     * Settings ▸ Scrolling's *Scroll distance* is the hand's distance too: at 128 dp — twice the default —
     * the same 50 px of pointer travel moves the sheet 100 dp. Read through the shell's own navigation
     * state, so the setting the Settings page writes is the one the pan reads.
     */
    @Test
    fun `the hand travels the Scrolling distance`() {
        val state = state()
        val tab = state.tabs.active!!
        state.prefs.navigation.chooseScrollStepDp(128f)
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            event(scene, PointerEventType.Press, 400f, 400f, pressed = true, at = 1_000L)
            event(scene, PointerEventType.Move, 400f, 350f, pressed = true, at = 1_016L)
            assertEquals("twice the Scrolling distance, twice the travel", -100f, tab.viewport.offsetY, 0.5f)
            event(scene, PointerEventType.Release, 400f, 350f, pressed = false, at = 1_032L)
        } finally {
            scene.close()
            state.prefs.navigation.chooseScrollStepDp(64f)
            state.tabs.closeAll()
        }
    }

    /** The default distance reads as 1:1, and the slider's ends are bounded into a usable gain. */
    @Test
    fun `the Scrolling distance becomes a bounded gain`() {
        assertEquals(1f, handScrollGain(64f), 0.001f)
        assertEquals(2f, handScrollGain(128f), 0.001f)
        assertEquals(HAND_GAIN_MIN, handScrollGain(1f), 0.001f)
        assertEquals(HAND_GAIN_MAX, handScrollGain(1_000f), 0.001f)
    }

    /**
     * The **compromise** the inputs keep: one wheel notch and a hand drag of [WHEEL_STEP_DP] travel the same
     * dp, at every *Scroll distance* — the setting scales the notch and the hand by the same number, so
     * neither is the odd one out and a reader who tunes one has tuned the other.
     */
    @Test
    fun `a notch and a hand drag travel the same distance`() {
        // The default is the reference notch itself, and twice the distance is twice the travel.
        assertEquals(64f, notchTravelDp(1f, 64f), 0.001f)
        assertEquals(128f, notchTravelDp(1f, 128f), 0.001f)
        // And the ends are bounded **once**, for both: a notch at the slider's top travels what the hand's
        // capped gain travels, not the raw setting.
        assertEquals(WHEEL_STEP_DP * HAND_GAIN_MAX, notchTravelDp(1f, 512f), 0.001f)
        listOf(16f, 32f, 64f, 128f, 256f, 512f).forEach { step ->
            assertEquals(
                "one notch against ${WHEEL_STEP_DP.toInt()} dp of hand at $step dp",
                notchTravelDp(1f, step),
                WHEEL_STEP_DP * handScrollGain(step),
                0.001f,
            )
        }
    }

    /**
     * The **pen** is the one input with an extra ([PEN_GAIN]) — it covers more than a hand, and less than the
     * 1.6 that read as another rule beside the wheel — and *Scroll distance* still scales it like the others.
     */
    @Test
    fun `the pen keeps one extra over the same Scrolling distance`() {
        assertEquals(PEN_GAIN, penScrollGain(64f), 0.001f)
        assertEquals(2f * PEN_GAIN, penScrollGain(128f), 0.001f)
        assertTrue("the extra is a compromise, not a second rule", PEN_GAIN in 1f..1.6f)
    }

    /** One **stylus** event at ([x], [y]) in window pixels — the pen as Compose sees it. */
    private fun stylus(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        pressed: Boolean,
        at: Long,
        /** The button bits the emulation reports; null means the primary one, as a real click does. */
        buttons: PointerButtons? = null,
    ) {
        scene.sendPointerEvent(
            eventType = type,
            pointers = listOf(
                ComposeScenePointer(
                    id = PointerId(3),
                    position = Offset(x, y),
                    pressed = pressed,
                    type = PointerType.Stylus,
                ),
            ),
            buttons = buttons ?: if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
            keyboardModifiers = PointerKeyboardModifiers(),
            timeMillis = at,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }

    /** One mouse event at ([x], [y]) in window pixels. */
    private fun event(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        pressed: Boolean,
        at: Long,
    ) {
        scene.sendPointerEvent(
            eventType = type,
            pointers = listOf(
                ComposeScenePointer(
                    id = PointerId(1),
                    position = Offset(x, y),
                    pressed = pressed,
                    type = PointerType.Mouse,
                ),
            ),
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
            keyboardModifiers = PointerKeyboardModifiers(),
            timeMillis = at,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }
}

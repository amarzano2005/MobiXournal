package com.mobixournal

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobixournal.format.model.Tool
import com.mobixournal.render.BarrelAction
import com.mobixournal.render.BarrelButtonState
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.InputSettings
import com.mobixournal.render.RadialHud
import com.mobixournal.ui.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A **visual** capture of the pen-tip radial HUD in the real editor, for the emulator pass the
 * `docs/ui-modernization.md` HUD tasks ask for.
 *
 * `adb shell input` cannot inject a stylus barrel key on the AVD (key code 308 arrives as 0), so the
 * ring cannot be opened from the shell. This test starts the real activity, finds the canvas in it,
 * binds the barrel to the HUD exactly as Settings does, writes one stroke so the ring is over ink, and
 * opens the ring with a barrel **key-down**. `UiAutomation.takeScreenshot` then captures the composited
 * screen — SurfaceView layer included — and writes it to the app's external files directory, where
 * `adb pull /sdcard/Android/data/com.mobixournal/files/radial-hud.png` can fetch it.
 *
 * It asserts only that the ring opened and the screenshot was written: the behaviour itself (summon,
 * track, commit, cancel) is `StylusInputTest`'s job.
 */
@RunWith(AndroidJUnit4::class)
class RadialHudScreenshotTest {

    @Test
    fun captureTheOpenRingOverInk() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // A connected test reinstalls the app, so this is a first launch every run: mark the
        // onboarding seen and grant the two permissions its dialogs would otherwise raise, so the
        // canvas — not a dialog — is what the screenshot shows.
        context.getSharedPreferences("xopp_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("has_seen_onboarding", true)
            .putString("barrel_action", "HUD")
            .commit()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.RECORD_AUDIO",
        ).close()

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()

        val view = findCanvas(activity.window.decorView)
        assertNotNull("the editor's canvas is in the activity", view)
        view!!

        instrumentation.runOnMainSync {
            view.inputSettings = InputSettings(barrelAction = BarrelAction.HUD)
            val settings = AppSettings()
            view.hudSlots = RadialHud.slots(
                colors = settings.penColors,
                customColor = settings.customColor,
                widths = settings.penWidths,
                tools = listOf(Tool.PEN, Tool.HIGHLIGHTER, Tool.ERASER),
            )
            drawStroke(view)
            view.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, BarrelButtonState.KEYCODE_STYLUS_BUTTON_PRIMARY),
            )
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(600)

        val shot = instrumentation.uiAutomation.takeScreenshot()
        // Downloads rather than the app's own external files: the connected-test teardown uninstalls
        // the app, and that deletes `/sdcard/Android/data/<pkg>` — with the evidence in it.
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "radial-hud.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            // `Images` only accepts DCIM and Pictures; Pictures is the visible one to pull from.
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val resolver = instrumentation.targetContext.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        assertNotNull("a Downloads entry for the screenshot", uri)
        resolver.openOutputStream(uri!!)!!.use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        instrumentation.runOnMainSync {
            view.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, BarrelButtonState.KEYCODE_STYLUS_BUTTON_PRIMARY),
            )
        }
    }

    /**
     * The commit half, end to end in the real editor: open the ring on the canvas, flick it onto the
     * fourth palette swatch (the default palette's blue) and release — the canvas takes that colour
     * through the editor's own `applyToolPreset` path, which is what makes the rail follow.
     */
    @Test
    fun aReleaseOnASwatchCommitsItThroughTheEditorsOwnPath() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("xopp_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("has_seen_onboarding", true)
            .putString("barrel_action", "HUD")
            .commit()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.RECORD_AUDIO",
        ).close()

        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        instrumentation.waitForIdleSync()
        val view = findCanvas(activity.window.decorView)!!
        val blue = AppSettings().penColors[3]

        instrumentation.runOnMainSync {
            // The ring is bound to the barrel by the settings file; open it at the nib fallback (the
            // canvas centre) and flick to the blue slot, button held, then release.
            view.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, BarrelButtonState.KEYCODE_STYLUS_BUTTON_PRIMARY),
            )
            val density = view.resources.displayMetrics.density
            val (x, y) = RadialHud.slotCenter(
                view.width / 2f, view.height / 2f, index = 3, count = view.hudSlots.size,
                ringRadiusPx = RadialHud.RING_RADIUS_DP * density,
            )
            hover(view, x, y, MotionEvent.BUTTON_STYLUS_PRIMARY)
            hover(view, x, y, 0)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(400)

        assertEquals("the release committed the blue swatch", blue, view.colorArgb)
        assertEquals("the ring closed on release", null, view.hud)

        val shot = instrumentation.uiAutomation.takeScreenshot()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "radial-hud-commit.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A hover event at ([x], [y]) carrying [buttonState], tip off the glass. */
    private fun hover(view: DrawingSurfaceView, x: Float, y: Float, buttonState: Int) {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS },
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 0f; size = 1f },
        )
        val event = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_HOVER_MOVE,
            1, props, coords, 0, buttonState, 1f, 1f, 0, 0, 0x00004002 /* SOURCE_STYLUS */, 0,
        )
        try {
            view.onHoverEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** Depth-first search for the first [DrawingSurfaceView] under [root]. */
    private fun findCanvas(root: View): DrawingSurfaceView? {
        if (root is DrawingSurfaceView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findCanvas(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /** One pressure-less stylus stroke, so the ring has ink under it. */
    private fun drawStroke(view: DrawingSurfaceView) {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS },
        )
        fun send(action: Int, x: Float, y: Float, downTime: Long) {
            val coords = arrayOf(
                MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f },
            )
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, 1, props, coords,
                0, 0, 1f, 1f, 0, 0, 0x00004002 /* SOURCE_STYLUS */, 0,
            )
            try {
                view.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        val down = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, 300f, 500f, down)
        var x = 300f
        repeat(8) {
            x += 40f
            send(MotionEvent.ACTION_MOVE, x, 500f + (it % 2) * 30f, down)
        }
        send(MotionEvent.ACTION_UP, x, 530f, down)
    }
}

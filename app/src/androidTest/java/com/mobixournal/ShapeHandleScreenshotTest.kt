package com.mobixournal

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.render.DrawingSurfaceView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A **visual** capture of a living figure in the real editor, for the emulator pass the
 * `living-parametric-shape-with-interactive-vertex-handles` task asks for.
 *
 * `adb shell input` cannot draw one continuous freehand polygon, so the triangle is injected as
 * stylus `MotionEvent`s straight into the canvas, then a tap selects it — which is what makes the
 * corner handles and the selection bar appear. `UiAutomation.takeScreenshot` captures the composited
 * screen (SurfaceView layer included) and hands it to the device's Pictures collection — the app is
 * uninstalled when the run ends, so its own files directory would not survive to be pulled:
 *
 * ```sh
 * adb pull /sdcard/Pictures/shape-handles.png
 * adb pull /sdcard/Pictures/shape-panel.png
 * ```
 *
 * (A reinstall changes who owns the previous capture, so a later run may leave the old file beside
 * the new one as `shape-handles (1).png`; pull the newest.)
 *
 * The first shows the selected figure with its corner dots, the second the **Edit shape** panel that
 * the bar's button opens. Both halves of the panel's entry point are therefore exercised here; the
 * drag itself is `ShapeVertexInputTest`'s job.
 */
@RunWith(AndroidJUnit4::class)
class ShapeHandleScreenshotTest {

    @Test
    fun captureASelectedFigureAndItsEditPanel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // A connected test reinstalls the app, so this is a first launch every run: skip onboarding
        // and the permission dialogs, or a dialog is what the screenshots show.
        context.getSharedPreferences("xopp_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("has_seen_onboarding", true)
            .commit()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.RECORD_AUDIO",
        ).close()

        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()

        val view = findCanvas(activity.window.decorView)
        assertNotNull("the editor's canvas is in the activity", view)
        view!!

        instrumentation.runOnMainSync {
            drawTriangle(view)
            view.selectMode = true
            val map = pageMap(view.width)
            stylusGesture(view, listOf(map.x(230.0) to map.y(190.0)))
        }
        instrumentation.waitForIdleSync()
        assertTrue(
            "the drawn triangle is a living figure",
            shapesOf(view).isNotEmpty(),
        )

        // The selection bar fades in once measured, so let it finish before capturing.
        SystemClock.sleep(800)
        capture("shape-handles.png")

        // And the panel its button opens, which is the only way to reach the two badges.
        val edit = find(instrumentation.uiAutomation.rootInActiveWindow, "Edit shape")
        assertNotNull("the selection bar offers Edit shape for a figure", edit)
        assertTrue(
            "and the button takes the tap",
            edit!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),
        )
        SystemClock.sleep(600)
        instrumentation.waitForIdleSync()
        assertTrue("the Edit shape panel opened", hasText(instrumentation, "Show side lengths"))
        capture("shape-panel.png")

        // Both readouts on, so the capture shows where the labels land: outside each side, inside
        // each corner — the placement that used to put every number on the outline.
        assertTrue("the panel's two switches are there", clickAllSwitches(instrumentation) == 2)
        assertTrue("the panel's Done button takes the tap", clickText(instrumentation, "Done"))
        SystemClock.sleep(1200)
        instrumentation.waitForIdleSync()
        capture("shape-labels.png")
    }

    /**
     * Tap every switch in the open panel, and answer how many there were. Compose exposes a
     * `SwitchRow`'s toggle as a checkable node, which is the one thing here that is not addressed by
     * its text.
     */
    private fun clickAllSwitches(instrumentation: android.app.Instrumentation): Int {
        val found = ArrayList<android.view.accessibility.AccessibilityNodeInfo>()
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isCheckable) found += node
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(instrumentation.uiAutomation.rootInActiveWindow)
        for (node in found) {
            if (!node.isChecked) node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        }
        return found.size
    }

    // ---- the figure ----------------------------------------------------------------------------

    /** Draw the triangle the recogniser turns into a figure, in page pt. */
    private fun drawTriangle(view: DrawingSurfaceView) {
        val map = pageMap(view.width)
        val corners = listOf(100.0 to 120.0, 360.0 to 120.0, 230.0 to 340.0)
        val points = ArrayList<Pair<Float, Float>>()
        for (c in corners.indices) {
            val (x0, y0) = corners[c]
            val (x1, y1) = corners[(c + 1) % corners.size]
            for (i in 0 until 24) {
                val t = i / 24.0
                points += map.x(x0 + (x1 - x0) * t) to map.y(y0 + (y1 - y0) * t)
            }
        }
        points += map.x(corners[0].first) to map.y(corners[0].second)
        stylusGesture(view, points)
    }

    private fun shapesOf(view: DrawingSurfaceView): List<ParametricShapeElement> =
        view.toDocument().pages
            .flatMap { p -> p.layers.flatMap { it.elements } }
            .filterIsInstance<ParametricShapeElement>()

    // ---- input ---------------------------------------------------------------------------------

    private fun stylusGesture(view: DrawingSurfaceView, points: List<Pair<Float, Float>>) {
        val downTime = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        })
        fun send(action: Int, x: Float, y: Float) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = 1f
                size = 1f
            })
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, 1, props, coords,
                0, 0, 1f, 1f, 0, 0, SOURCE_STYLUS, 0,
            )
            try {
                view.onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        val (x0, y0) = points.first()
        send(MotionEvent.ACTION_DOWN, x0, y0)
        for ((x, y) in points.drop(1)) send(MotionEvent.ACTION_MOVE, x, y)
        send(MotionEvent.ACTION_UP, points.last().first, points.last().second)
    }

    // ---- the screen ----------------------------------------------------------------------------

    /**
     * Write the composited screen to the Pictures collection, replacing the previous capture of the
     * same [name] so repeated runs leave one stable file to pull rather than `name (3).png`.
     */
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val shot: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
        val resolver = instrumentation.targetContext.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(name),
            null,
        )?.use { existing ->
            while (existing.moveToNext()) {
                val id = existing.getLong(0)
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val uri = resolver.insert(collection, values) ?: error("no media store entry for $name")
        resolver.openOutputStream(uri)!!.use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Click the node carrying [text], or its nearest clickable ancestor (a button's label is not). */
    private fun clickText(instrumentation: android.app.Instrumentation, text: String): Boolean {
        var node = find(instrumentation.uiAutomation.rootInActiveWindow, null, text)
        while (node != null) {
            if (node.isClickable) {
                return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            }
            node = node.parent
        }
        return false
    }

    private fun find(
        node: android.view.accessibility.AccessibilityNodeInfo?,
        label: String?,
        text: String? = null,
    ): android.view.accessibility.AccessibilityNodeInfo? {
        if (node == null) return null
        if (label != null && node.contentDescription?.toString() == label) return node
        if (text != null && node.text?.toString() == text) return node
        for (i in 0 until node.childCount) find(node.getChild(i), label, text)?.let { return it }
        return null
    }

    private fun hasText(instrumentation: android.app.Instrumentation, text: String): Boolean {
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.text?.toString() == text) return true
            for (i in 0 until node.childCount) if (walk(node.getChild(i))) return true
            return false
        }
        return walk(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun findCanvas(root: View): DrawingSurfaceView? {
        if (root is DrawingSurfaceView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findCanvas(root.getChildAt(i))?.let { return it }
        }
        return null
    }

    /**
     * Page pt -> view px for the page fitted to a canvas [width] px wide, mirroring `PageStacker`.
     */
    private class PageMap(val scale: Float, val left: Float, val top: Float) {
        fun x(pageX: Double): Float = left + (pageX * scale).toFloat()
        fun y(pageY: Double): Float = top + (pageY * scale).toFloat()
    }

    private fun pageMap(width: Int): PageMap {
        val scale = (width / A4_WIDTH_PT).toFloat()
        val left = (maxOf(width.toFloat(), A4_WIDTH_PT.toFloat() * scale) - A4_WIDTH_PT.toFloat() * scale) / 2f
        return PageMap(scale, left, GAP_PX)
    }

    private companion object {
        const val A4_WIDTH_PT = 595.276

        /** Mirrors `DrawingSurfaceDefaults.GAP_PX`. */
        const val GAP_PX = 24f
        const val SOURCE_STYLUS = 0x00004002
    }
}

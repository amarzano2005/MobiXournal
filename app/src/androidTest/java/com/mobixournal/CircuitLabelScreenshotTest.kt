package com.mobixournal

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitLabelSide
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Layer
import com.mobixournal.render.CircuitInk
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.relayout
import com.mobixournal.render.render
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A **visual** capture of a circuit component's value label: above its symbol, below it, on its left
 * and its right, and beside a turned symbol — the placements the label work exists for.
 *
 * The components are written straight into the page (`view.doc`) rather than dragged out with the
 * circuit tools: what is being looked at is where the *label* lands, and the placement rules are the
 * renderer's. The screen is captured through `UiAutomation.takeScreenshot`, so the SurfaceView's own
 * frame is what the image holds:
 *
 * ```sh
 * adb pull /sdcard/Pictures/circuit-labels.png
 * ```
 */
@RunWith(AndroidJUnit4::class)
class CircuitLabelScreenshotTest {

    @Test
    fun captureAComponentsLabelAboveAndBelow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("xopp_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("has_seen_onboarding", true)
            .commit()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant com.mobixournal.v2 android.permission.POST_NOTIFICATIONS",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant com.mobixournal.v2 android.permission.RECORD_AUDIO",
        ).close()

        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        val view = findCanvas(activity.window.decorView)
        assertNotNull("the editor's canvas is in the activity", view)
        view!!

        instrumentation.runOnMainSync {
            val document = view.toDocument()
            val page = document.pages.first()
            val layer = page.layers.firstOrNull() ?: Layer(emptyList())
            val placed = layer.copy(
                elements = layer.elements + listOf(
                    component("R1 10 kΩ", x = 150.0, y = 140.0, rotation = 0.0, side = CircuitLabelSide.ABOVE),
                    component("R2 4.7 kΩ", x = 150.0, y = 260.0, rotation = 0.0, side = CircuitLabelSide.BELOW),
                    component("R3 220 Ω", x = 430.0, y = 140.0, rotation = 90.0, side = CircuitLabelSide.LEFT),
                    component("R4 1 MΩ", x = 430.0, y = 300.0, rotation = 90.0, side = CircuitLabelSide.RIGHT),
                ),
            )
            view.doc = document.copy(
                pages = listOf(page.copy(layers = listOf(placed) + page.layers.drop(1))),
            )
            view.relayout()
            view.render()
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(700)

        // Every label must be clear of the symbol it belongs to — the thing the screenshot is for.
        for (element in elementsOf(view).filterIsInstance<CircuitElement>()) {
            val ink = CircuitInk.ink(element)!!
            val strokes = ink.filterIsInstance<com.mobixournal.format.model.Stroke>()
            val texts = ink.filterIsInstance<com.mobixournal.format.model.TextElement>()
            assertTrue("the component draws", strokes.isNotEmpty())
            val symbol = strokes.flatMap { it.points }
            val left = symbol.minOf { it.x }
            val right = symbol.maxOf { it.x }
            val top = symbol.minOf { it.y }
            val bottom = symbol.maxOf { it.y }
            for (text in texts) {
                val textBottom = text.y + text.size
                val clear = textBottom <= top || text.y >= bottom || text.x >= right || text.x + 40.0 <= left
                assertTrue(
                    "${element.value} at (${text.x}, ${text.y}) lies over its symbol " +
                        "($left,$top - $right,$bottom)",
                    clear,
                )
            }
        }
        capture("circuit-labels.png")
    }

    /** A resistor with two pins on its horizontal axis, so its ink is derived like a placed one. */
    private fun component(value: String, x: Double, y: Double, rotation: Double, side: CircuitLabelSide) =
        CircuitElement(
            symbol = "RESISTOR",
            positionX = x,
            positionY = y,
            rotation = rotation,
            strokeWidth = 1.6,
            value = value,
            labelSide = side,
            pins = listOf(CircuitPin("p1", -40.0, 0.0), CircuitPin("p2", 40.0, 0.0)),
            id = "ckt_shot_${value.hashCode()}",
        )

    private fun elementsOf(view: DrawingSurfaceView): List<com.mobixournal.format.model.Element> =
        view.toDocument().pages.flatMap { p -> p.layers.flatMap { it.elements } }

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
                resolver.delete(ContentUris.withAppendedId(collection, existing.getLong(0)), null, null)
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

    private fun findCanvas(root: View): DrawingSurfaceView? {
        if (root is DrawingSurfaceView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findCanvas(root.getChildAt(i))?.let { return it }
        }
        return null
    }
}

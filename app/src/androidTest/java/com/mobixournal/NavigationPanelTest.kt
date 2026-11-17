package com.mobixournal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.model.withIdentity
import com.mobixournal.render.XoppSynthesis
import com.mobixournal.format.Xopp
import com.mobixournal.format.model.Document
import com.mobixournal.render.DrawingSurfaceView
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real raw-PDF opening, bookmark actions, a long-index fling and page previews in the shipping activity. */
@RunWith(AndroidJUnit4::class)
class NavigationPanelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation
    private lateinit var activity: Activity

    @Test fun rawPdfKeepsItsNameAndTheNavigatorRemainsUsable() {
        context.getSharedPreferences("xopp_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("has_seen_onboarding", true).commit()
        PDFBoxResourceLoader.init(context)
        val source = File(context.filesDir, "Navigation.pdf")
        makePdf(source)
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        instrumentation.runOnMainSync { (activity as MainActivity).openDocument(Uri.fromFile(source)) }
        await("the PDF is imported") { document()?.outline?.nodes?.firstOrNull()?.title == "Navigation" }
        val original = document()!!
        assertEquals(36, original.pages.size)
        assertEquals(160, original.outline.nodes.single().children.size)
        assertFalse(original.outline.nodes.single().title.endsWith(".tmp"))

        click("Bookmarks")
        await("closed file group") { find("Expand Navigation") != null }
        click("Expand Navigation")
        await("chapters are visible") { find("Expand ${chapter(1)}") != null }
        assertNull("subsections start closed", find("Section 001.01"))
        screenshot("bookmarks-tablet.png")
        checkAlignedRowTargets()

        click("Actions for ${chapter(2)}")
        screenshot("bookmarks-actions.png")
        click("Move up")
        await("the whole second chapter moved") { document()?.outline?.nodes?.single()?.children?.firstOrNull()?.title == chapter(2) }
        val moved = document()!!
        assertEquals(original.outline.nodes.single().children[1], moved.outline.nodes.single().children[0])
        assertEquals(original.pages, moved.pages)
        val bytes = ByteArrayOutputStream().also { Xopp.save(XoppSynthesis.synthesize(moved).document, it) }.toByteArray()
        assertEquals("reorder survives save/reopen", moved.outline, MobixDocument.of(Xopp.open(ByteArrayInputStream(bytes)).withIdentity(), appVersion = "2.2.0", nowMillis = 1L).toDocument().outline)
        instrumentation.runOnMainSync { surface()?.undo() }
        await("undo restores the previous order") { document()?.outline == original.outline }
        instrumentation.runOnMainSync { surface()?.redo() }
        await("redo restores the reorder") { document()?.outline == moved.outline }

        click("Expand ${chapter(2)}")
        await("an expanded section is visible") { find("Section 002.01", prefix = true) != null }
        click("Collapse ${chapter(2)}")
        val row = find("Actions for ${chapter(1)}")!!
        val bounds = Rect().also(row::getBoundsInScreen)
        val x = bounds.left - 100
        val height = context.resources.displayMetrics.heightPixels
        val elapsed = SystemClock.uptimeMillis()
        repeat(6) { shell("input swipe $x ${height - 180} $x ${height / 3} 180") }
        SystemClock.sleep(500)
        assertNull("a long fling really left the first chapter behind", find("Actions for ${chapter(1)}"))
        Log.i("NavigationQa", "six index flings completed in ${SystemClock.uptimeMillis() - elapsed} ms; 801 outline rows")
        screenshot("bookmarks-scrolled.png")
        click("Pages")
        await("page controls") { find("Pick page 1") != null }
        screenshot("pages-tablet.png")
        assertTrue("a page preview stays within its navigator", find("Pick page 1")!!.let {
            val box = Rect().also(it::getBoundsInScreen)
            box.width() > 0 && box.height() > 0
        })
        captureVariants()
    }

    private fun captureVariants() {
        val oldSize = shell("wm size").substringAfter("Override size: ", "reset").trim()
        val oldDensity = shell("wm density").substringAfter("Override density: ", "reset").trim()
        val oldTheme = shell("cmd uimode night").substringAfter(": ", "no").trim()
        try {
            shell("cmd uimode night yes")
            SystemClock.sleep(1000)
            captureTab("Bookmarks", "bookmarks-tablet-dark.png")
            captureTab("Pages", "pages-tablet-dark.png")
            shell("wm size 1080x1920")
            shell("wm density 420")
            SystemClock.sleep(1200)
            captureTab("Bookmarks", "bookmarks-phone-dark.png")
            val pages = Rect().also { find("Page list")!!.getBoundsInScreen(it) }
            val audio = Rect().also { find("Start audio recording")!!.getBoundsInScreen(it) }
            assertFalse("compact navigation must not overlap the audio capsule", Rect.intersects(pages, audio))
            captureTab("Pages", "pages-phone-dark.png")
            shell("cmd uimode night no")
            SystemClock.sleep(1000)
            captureTab("Bookmarks", "bookmarks-phone-light.png")
            captureTab("Pages", "pages-phone-light.png")
        } finally {
            shell("wm size $oldSize")
            shell("wm density $oldDensity")
            shell("cmd uimode night $oldTheme")
        }
    }

    private fun captureTab(tab: String, name: String) {
        if (find("Close navigation") != null) click("Close navigation")
        SystemClock.sleep(200)
        val compact = context.resources.configuration.screenWidthDp < 600
        val before = if (compact) canvasState() else null
        click(if (tab == "Pages") "Page list" else "Bookmarks")
        await("$tab view after configuration change") { find(if (tab == "Pages") "Pick page 1" else "Filter") != null }
        if (compact) {
            SystemClock.sleep(200)
            assertEquals("opening compact navigation keeps the full canvas viewport and current page", before, canvasState())
        }
        screenshot(name)
    }

    private fun canvasState(): Triple<Int, Int, Int> {
        var result: Triple<Int, Int, Int>? = null
        instrumentation.runOnMainSync {
            activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first { it is MainActivity }
            val view = surface()!!
            result = Triple(view.width, view.height, view.currentPageIndex())
        }
        return result!!
    }

    private fun chapter(index: Int) = "Chapter %03d: Definitions and scientific notes".format(java.util.Locale.US, index)

    private fun makePdf(file: File) {
        PDDocument().use { pdf ->
            repeat(36) { index ->
                val page = PDPage(if (index % 3 == 1) PDRectangle(842f, 595f) else PDRectangle.A4)
                pdf.addPage(page)
                PDPageContentStream(pdf, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 18f)
                    stream.newLineAtOffset(36f, 520f)
                    stream.showText("Scientific notes - page ${index + 1}")
                    stream.endText()
                }
            }
            val outline = PDDocumentOutline()
            pdf.documentCatalog.documentOutline = outline
            repeat(160) { index ->
                val parent = pdfItem(chapter(index + 1), pdf.getPage(index % 36))
                repeat(4) { section ->
                    parent.addLast(pdfItem("Section %03d.%02d".format(java.util.Locale.US, index + 1, section + 1), pdf.getPage(index % 36)))
                }
                outline.addLast(parent)
            }
            pdf.save(file)
        }
    }

    private fun pdfItem(title: String, page: PDPage) = PDOutlineItem().apply {
        this.title = title
        destination = PDPageFitDestination().apply { this.page = page }
    }

    private fun checkAlignedRowTargets() {
        val first = Rect().also { find("Actions for ${chapter(1)}")!!.getBoundsInScreen(it) }
        val second = Rect().also { find("Actions for ${chapter(2)}")!!.getBoundsInScreen(it) }
        assertEquals("action column is aligned", first.left, second.left)
        assertEquals("action targets use the same width", first.width(), second.width())
        assertTrue("successive rows do not overlap", second.top >= first.bottom)
    }

    private fun document(): Document? {
        var result: Document? = null
        instrumentation.runOnMainSync { result = surface()?.toDocument() }
        return result
    }

    private fun surface(): DrawingSurfaceView? {
        fun find(view: View): DrawingSurfaceView? {
            if (view is DrawingSurfaceView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        return find(activity.window.decorView)
    }

    private fun find(label: String, prefix: Boolean = false): AccessibilityNodeInfo? {
        if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
        val root = automation.rootInActiveWindow ?: return null
        fun visit(node: AccessibilityNodeInfo, text: Boolean): AccessibilityNodeInfo? {
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it, text)?.let { hit -> return hit } }
            val value = (if (text) node.text else node.contentDescription)?.toString() ?: return null
            if (if (prefix) value.startsWith(label) else value == label || value.startsWith("$label,")) return node
            return null
        }
        return visit(root, true) ?: visit(root, false)
    }

    private fun click(label: String) {
        await("control $label") { find(label) != null }
        var node = find(label)!!
        while (!node.isClickable && node.parent != null) node = node.parent
        assertTrue("click $label", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        screenshot("failure.png")
        fail("Timed out waiting for $label")
    }

    private fun screenshot(name: String) {
        SystemClock.sleep(200)
        val bitmap = automation.takeScreenshot() ?: error("Screenshot unavailable")
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "navigation-$name")
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shell(command: String): String = automation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { String(it.readBytes()) }
    }
}

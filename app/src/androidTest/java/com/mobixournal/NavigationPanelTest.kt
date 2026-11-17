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
import com.mobixournal.format.model.*
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.mobix.PageSize
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.io.IOException
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
    private var previousImeSetting: String? = null

    @org.junit.After fun cleanup() {
        previousImeSetting?.let {
            if (it == "0" || it == "1") shell("settings put secure show_ime_with_hard_keyboard $it")
            else shell("settings delete secure show_ime_with_hard_keyboard")
        }
        if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun renameOpensTheSoftwareKeyboardWithoutTouchingTheField() = checkRenameKeyboard("tablet")

    @Test fun compactRenameOpensTheSoftwareKeyboardWithoutTouchingTheField() {
        val size = shell("wm size").substringAfter("Override size: ", "reset").trim()
        val density = shell("wm density").substringAfter("Override density: ", "reset").trim()
        try {
            shell("wm size 1080x1920")
            shell("wm density 420")
            checkRenameKeyboard("phone")
        } finally { shell("wm size $size"); shell("wm density $density") }
    }

    private fun checkRenameKeyboard(layout: String) {
        previousImeSetting = shell("settings get secure show_ime_with_hard_keyboard").trim()
        shell("settings put secure show_ime_with_hard_keyboard 1")
        context.getSharedPreferences("xopp_settings", Context.MODE_PRIVATE).edit().putBoolean("has_seen_onboarding", true).commit()
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
        val file = heavyNativeFixture()
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync { (activity as MainActivity).openDocument(Uri.fromFile(file)) }
        await("bookmarked native document") { document()?.outline?.nodes?.size == 800 }
        click("Bookmarks")
        click("Actions for Heavy chapter 0")
        click("Rename")
        await("automatic software keyboard") {
            var visible = false
            instrumentation.runOnMainSync {
                visible = activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
            }
            visible
        }
        screenshot("bookmark-rename-keyboard-$layout.png")
        typeBookmark("Keyboard confirmed")
        click("Save bookmark")
        await("renamed entry appears") { find("Actions for Keyboard confirmed") != null }
    }

    @Test fun suppliedPdfOpensBookmarksOnEachFirstTap() {
        val remote = InstrumentationRegistry.getArguments().getString("navigationPdf")
        org.junit.Assume.assumeNotNull(remote)
        require(remote!!.matches(Regex("/data/local/tmp/[A-Za-z0-9._-]+")))
        context.getSharedPreferences("xopp_settings", Context.MODE_PRIVATE).edit().putBoolean("has_seen_onboarding", true).commit()
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
        val file = File(context.filesDir, "00)  APPUNTI COMPLETI.pdf")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cat $remote")).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync { (activity as MainActivity).openDocument(Uri.fromFile(file)) }
        await("the supplied 449-page PDF", 60_000) { document()?.pages?.size == 449 && document()?.outline?.isEmpty == false }
        val model = document()!!
        val root = model.outline.nodes.first().title
        val samples = java.util.concurrent.CopyOnWriteArrayList<Long>()
        val thread = android.os.HandlerThread("SuppliedPdfFrames").apply { start() }
        val listener = android.view.Window.OnFrameMetricsAvailableListener { _, frame, _ ->
            samples += frame.getMetric(android.view.FrameMetrics.TOTAL_DURATION) / 1_000_000
        }
        instrumentation.runOnMainSync { activity.window.addOnFrameMetricsAvailableListener(listener, android.os.Handler(thread.looper)) }
        val times = mutableListOf<Long>()
        repeat(6) {
            val start = SystemClock.uptimeMillis()
            click("Bookmarks")
            await("supplied PDF bookmarks after one tap") { find("Actions for $root") != null }
            times += SystemClock.uptimeMillis() - start
            if (it == 0) screenshot("supplied-pdf-bookmarks.png")
            click("Close navigation")
        }
        instrumentation.runOnMainSync { activity.window.removeOnFrameMetricsAvailableListener(listener) }
        thread.quitSafely()
        val frames = samples.sorted()
        Log.i("NavigationQa", "Supplied PDF: pages=${model.pages.size}; outline=${model.outline.size}; opening ms=$times; frames=${frames.size}; p95=${frames.getOrNull(((frames.size - 1) * 0.95).toInt().coerceAtLeast(0))}ms")
        click("Bookmarks")
        click("Expand $root")
        screenshot("supplied-pdf-expanded.png")
    }

    @Test fun heavyPagedNavigationOpensOnEachTapAndReadFailuresCanBeRetried() {
        context.getSharedPreferences("xopp_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("has_seen_onboarding", true).commit()
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
        val file = heavyNativeFixture()
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.runOnMainSync { (activity as MainActivity).openDocument(Uri.fromFile(file)) }
            await("1200 native pages") { document()?.pages?.size == 1200 }
            val model = document()!!
            val source = PageSource.of(model.pages)
            val reads = AtomicInteger()
            val faulty = AtomicBoolean(false)
            val counted = object : PageSource {
                override val pageCount get() = source.pageCount
                override fun pageId(index: Int) = source.pageId(index)
                override fun pageSize(index: Int) = source.pageSize(index)
                override fun page(index: Int): com.mobixournal.format.model.Page {
                    reads.incrementAndGet()
                    if (faulty.get() && index >= 10) throw IOException("Simulated unreadable offscreen page")
                    return source.page(index)
                }
            }
            val pages = counted.asPageList()
            instrumentation.runOnMainSync { surface()!!.load(model.copy(pages = pages)) }
            SystemClock.sleep(300)
            reads.set(0)
            val frames = java.util.concurrent.CopyOnWriteArrayList<Long>()
            val frameThread = android.os.HandlerThread("NavigationFrameMetrics").apply { start() }
            val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                val total = metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION) / 1_000_000
                frames += total
                if (total > 80) Log.i("NavigationQa", "Slow frame total=${total}ms layout=${metrics.getMetric(android.view.FrameMetrics.LAYOUT_MEASURE_DURATION) / 1_000_000} draw=${metrics.getMetric(android.view.FrameMetrics.DRAW_DURATION) / 1_000_000} sync=${metrics.getMetric(android.view.FrameMetrics.SYNC_DURATION) / 1_000_000} command=${metrics.getMetric(android.view.FrameMetrics.COMMAND_ISSUE_DURATION) / 1_000_000} swap=${metrics.getMetric(android.view.FrameMetrics.SWAP_BUFFERS_DURATION) / 1_000_000} unknown=${metrics.getMetric(android.view.FrameMetrics.UNKNOWN_DELAY_DURATION) / 1_000_000}")
            }
            instrumentation.runOnMainSync {
                activity.window.addOnFrameMetricsAvailableListener(listener, android.os.Handler(frameThread.looper))
            }
            val openingTimes = mutableListOf<Long>()
            repeat(6) {
                val start = SystemClock.uptimeMillis()
                click("Bookmarks")
                await("bookmarks after one tap") { find("Actions for Heavy chapter 0") != null }
                openingTimes += SystemClock.uptimeMillis() - start
                click("Close navigation")
            }
            instrumentation.runOnMainSync { activity.window.removeOnFrameMetricsAvailableListener(listener) }
            frameThread.quitSafely()
            val orderedFrames = frames.sorted()
            assertTrue("opening and closing must produce measured frames", orderedFrames.isNotEmpty())
            val p95 = orderedFrames[((orderedFrames.size - 1) * 0.95).toInt()]
            Log.i("NavigationQa", "Navigator frames: n=${frames.size}; p95=${p95}ms; max=${orderedFrames.last()}ms")
            assertTrue("navigation must not repeatedly decode 1200 pages: ${reads.get()}", reads.get() < 200)
            assertTrue("each first-tap opening remains responsive: $openingTimes", openingTimes.all { it < 3000 })
            Log.i("NavigationQa", "1200 pages, 800 bookmarks: opening ms=$openingTimes; page reads=${reads.get()}")
            click("Page list")
            await("heavy page preview") { find("Show page 1") != null }
            screenshot("heavy-pages.png")
            click("Close navigation")
            faulty.set(true)
            val unavailable = OutlineTree(nodes = listOf(OutlineNode("bkm_unavailable", "Unavailable sheet", OutlineTarget.Sheet(9999))))
            instrumentation.runOnMainSync { surface()!!.load(model.copy(pages = pages, outline = unavailable)) }
            click("Bookmarks")
            await("recoverable read error") { find("Try again") != null }
            screenshot("bookmark-read-error.png")
            faulty.set(false)
            click("Try again")
            await("successful retry") { find("Actions for Unavailable sheet") != null }
            screenshot("bookmark-read-recovered.png")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun heavyNativeFixture(): File {
        val points = (0..127).map { StrokePoint(30.0 + it * 2, 50.0 + it % 11, 1.0) }
        val stroke = Stroke(tool = Tool.PEN, color = -16777216, points = points, uniformWidth = false, capStyle = "round")
        val pages = (0..1199).map { com.mobixournal.format.model.Page(595.0, 842.0,
            Background.Solid(-1, "plain"), listOf(Layer(listOf(stroke))), id = "pg_$it") }
        val outline = OutlineTree(nodes = (0..799).map {
            OutlineNode("bkm_$it", "Heavy chapter $it", OutlineTarget.Page("pg_${it * 1199 / 799}"))
        })
        return File(context.filesDir, "Heavy.mobix").also { file ->
            file.outputStream().use { MobixDocument.of(Document(pages = pages, outline = outline).withIdentity(),
                appVersion = "2.2.0", nowMillis = 1L).save(it, 1L) }
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "navigation-heavy.mobix")
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/vnd.mobixournal+zip")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(uri)!!.use { output -> file.inputStream().use { it.copyTo(output) } }
        }
    }

    @Test fun bookmarkDragScrollsAtEdgesAndCancellationWritesNothing() {
        context.getSharedPreferences("xopp_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("has_seen_onboarding", true).commit()
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
        val file = heavyNativeFixture()
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync { (activity as MainActivity).openDocument(Uri.fromFile(file)) }
        await("native fixture") { document()?.outline?.nodes?.size == 800 }
        val original = document()!!.outline
        click("Bookmarks")
        await("first drag handle") { find("Reorder Heavy chapter 0") != null }
        val from = Rect().also { find("Reorder Heavy chapter 0")!!.getBoundsInScreen(it) }
        val next = Rect().also { find("Reorder Heavy chapter 1")!!.getBoundsInScreen(it) }
        nativeBookmarkDrag(from.centerX(), from.centerY(), next.centerY(), cancel = true)
        SystemClock.sleep(200)
        assertEquals("cancellation must not reorder", original, document()!!.outline)
        val bottom = Rect().also { automation.rootInActiveWindow.getBoundsInScreen(it) }.bottom - 40
        nativeBookmarkDrag(from.centerX(), from.centerY(), bottom, hold = 1400)
        await("edge scrolling carries the bookmark beyond its starting viewport") {
            document()!!.outline.nodes.indexOfFirst { it.id == original.nodes.first().id } > 15
        }
        instrumentation.runOnMainSync { surface()!!.undo() }
        await("a long drag is one undo step") { document()!!.outline == original }
    }

    private fun nativeBookmarkDrag(x: Int, from: Int, to: Int, cancel: Boolean = false, hold: Long = 0) {
        val began = SystemClock.uptimeMillis()
        fun event(action: Int, y: Float) {
            val event = android.view.MotionEvent.obtain(began, SystemClock.uptimeMillis(), action, x.toFloat(), y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            automation.injectInputEvent(event, true)
            event.recycle()
        }
        event(android.view.MotionEvent.ACTION_DOWN, from.toFloat())
        repeat(20) { step ->
            SystemClock.sleep(20)
            event(android.view.MotionEvent.ACTION_MOVE, from + (to - from) * (step + 1) / 20f)
        }
        if (hold > 0) { SystemClock.sleep(hold); screenshot("bookmark-drag-lifted.png") }
        event(if (cancel) android.view.MotionEvent.ACTION_CANCEL else android.view.MotionEvent.ACTION_UP, to.toFloat())
    }

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
        await("the PDF is imported") { document()?.outline?.nodes?.firstOrNull()?.title == chapter(1) }
        val original = document()!!
        assertEquals(36, original.pages.size)
        assertEquals(160, original.outline.nodes.size)
        assertFalse(original.outline.nodes.any { it.title.endsWith(".tmp") })

        click("Bookmarks")
        await("chapter roots are directly visible") { find("Expand ${chapter(1)}") != null }
        assertNull("the filename is not a bookmark", find("Actions for Navigation"))
        val filterBounds = Rect().also { find("Filter bookmarks")!!.getBoundsInScreen(it) }
        val addBounds = Rect().also { find("Add bookmark")!!.getBoundsInScreen(it) }
        assertTrue("the compact filter aligns with its adjacent action", filterBounds.height() <= addBounds.height() + 8)
        assertNull("subsections start closed", find("Section 001.01"))
        screenshot("bookmarks-tablet.png")
        checkAlignedRowTargets()

        click("Actions for ${chapter(2)}")
        screenshot("bookmarks-actions.png")
        assertNull("reorder is no longer an overflow command", find("Move up"))
        shell("input keyevent 4")
        await("the reorder handle") { find("Reorder ${chapter(2)}") != null }
        val gripFrom = Rect().also { find("Reorder ${chapter(2)}")!!.getBoundsInScreen(it) }
        val gripTo = Rect().also { find("Reorder ${chapter(1)}")!!.getBoundsInScreen(it) }
        shell("input swipe ${gripFrom.centerX()} ${gripFrom.centerY()} ${gripTo.centerX()} ${gripTo.centerY()} 600")
        await("the whole second chapter moved") { document()?.outline?.nodes?.firstOrNull()?.title == chapter(2) }
        val moved = document()!!
        assertEquals(original.outline.nodes[1], moved.outline.nodes[0])
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
        Log.i("NavigationQa", "six index flings completed in ${SystemClock.uptimeMillis() - elapsed} ms; 800 outline rows")
        screenshot("bookmarks-scrolled.png")
        click("Add bookmark")
        await("the add draft is shown without leaving Bookmarks") { find("Save bookmark") != null }
        typeBookmark("New visible bookmark")
        click("Save bookmark")
        await("new bookmark appears immediately in the open list") { find("Actions for New visible bookmark") != null }
        click("Actions for New visible bookmark")
        previousImeSetting = shell("settings get secure show_ime_with_hard_keyboard").trim()
        shell("settings put secure show_ime_with_hard_keyboard 1")
        click("Rename")
        await("Rename automatically shows the software keyboard") {
            var visible = false
            instrumentation.runOnMainSync {
                visible = activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
            }
            visible
        }
        screenshot("bookmark-rename-keyboard.png")
        typeBookmark("Renamed immediately")
        click("Save bookmark")
        await("renamed bookmark updates immediately") { find("Actions for Renamed immediately") != null }
        screenshot("bookmark-added-live.png")
        typeBookmark("No matching titles", last = false)
        click("Add bookmark")
        typeBookmark("Visible after filtering")
        click("Save bookmark")
        await("new bookmark is revealed despite the previous filter") { find("Actions for Visible after filtering") != null }
        click("Pages")
        await("page controls") { find("Show page 1") != null }
        screenshot("pages-tablet.png")
        assertTrue("a page preview stays within its navigator", find("Show page 1")!!.let {
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
        await("$tab view after configuration change") { find(if (tab == "Pages") "Show page 1" else "Filter bookmarks") != null }
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

    private fun typeBookmark(value: String, last: Boolean = true) {
        var field: AccessibilityNodeInfo? = null
        await("editable bookmark field") {
            if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
            val fields = mutableListOf<AccessibilityNodeInfo>()
            fun collect(node: AccessibilityNodeInfo) {
                if (node.isVisibleToUser && node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) fields += node
                for (i in 0 until node.childCount) node.getChild(i)?.let(::collect)
            }
            automation.rootInActiveWindow?.let(::collect)
            val ordered = fields.distinctBy { Rect().also(it::getBoundsInScreen).centerY() }
                .sortedBy { Rect().also(it::getBoundsInScreen).centerY() }
            field = if (last && ordered.size >= 2) ordered.last() else if (!last) ordered.firstOrNull() else null
            field != null
        }
        field!!.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        field!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        assertTrue(field!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        instrumentation.waitForIdleSync()
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

    private fun await(label: String, timeout: Long = 30000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
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

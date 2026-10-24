package com.mobixournal.desktop

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The packet buffer is filled by the window procedure and drained by the frame ticker.
 *
 * Those are two threads, and `kotlin.collections.ArrayDeque` is not thread-safe: a `removeFirst()` that
 * runs while the driver's thread is writing the same slot returns `null` — measured at ~350 000 times in
 * half a second of hammering — which is exactly the failure the window reported: "parameter specified as
 * non-null is null: PageInkGesture.move, parameter pointer". This test hammers both sides and asserts
 * that neither sees a broken sample, and that the strokes the packets drew are still committed.
 */
class PenPacketStrokeConcurrencyTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun strokes(tab: DesktopTab) =
        tab.document.page(0).layers.flatMap { it.elements }.filterIsInstance<com.mobixournal.format.model.Stroke>()

    @Test
    fun `packets arriving while a frame drains them never break the queue`() {
        val file: File = temp.newFile("concurrent.mobix").also { f ->
            SyntheticNotebook.write(f, SyntheticNotebook.pages(2))
        }
        val tab = DesktopTabs().open(DocumentLoader.load(file))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        try {
            @Suppress("UNCHECKED_CAST")
            val draft = mutableStateListOf<InkSample>() as SnapshotStateList<InkSample>
            val gesture = PageInkGesture(
                tab = tab,
                tool = DesktopTool.PEN,
                ink = DesktopInk(width = 4f),
                view = { Viewport(zoom = 1f) },
                pageW = SyntheticNotebook.WIDTH_PT.toFloat(),
                pageH = SyntheticNotebook.HEIGHT_PT.toFloat(),
                density = 1f,
                draft = draft,
                onEdited = {},
                startOnDown = true,
            )
            val stroke = PenPacketStroke(
                gesture = gesture,
                origin = { androidx.compose.ui.geometry.Offset.Zero },
                size = { androidx.compose.ui.geometry.Size(2000f, 3000f) },
            )
            val failure = AtomicReference<Throwable?>()
            val start = CountDownLatch(1)

            val writer = Thread {
                start.await()
                var nanos = 1_000_000_000L
                repeat(20_000) { index ->
                    runCatching {
                        stroke.packet(
                            PenPacket(
                                x = 30f + (index % 400),
                                y = 30f + (index % 300),
                                pressure = 0.5f,
                                tiltRad = null,
                                azimuthRad = null,
                                rotationRad = null,
                                tip = index % 500 != 499,
                                eraser = false,
                                atNanos = nanos,
                            ),
                        )
                    }.onFailure { failure.compareAndSet(null, it) }
                    nanos += 2_500_000L
                }
            }

            val reader = Thread {
                start.await()
                repeat(60_000) {
                    runCatching {
                        stroke.flush()
                        stroke.publishHover()
                        stroke.finishIfStale(System.nanoTime())
                    }.onFailure { failure.compareAndSet(null, it) }
                }
            }

            writer.start()
            reader.start()
            start.countDown()
            writer.join(30_000)
            reader.join(30_000)
            assertNull("the queue survived both threads: ${failure.get()}", failure.get())
            assertTrue("the packets drew strokes", strokes(tab).isNotEmpty())
        } finally {
            tab.document.close()
        }
    }

    /**
     * The hammer above can miss a narrow window, so the **rule** is asserted too: every entry point a
     * second thread can reach is `@Synchronized`. Removing the lock is the regression, and this fails on
     * the modifiers rather than on a lucky timing.
     */
    @Test
    fun `every entry point the packet and frame threads share is synchronized`() {
        val entryPoints = mapOf(
            "packet" to PenPacket::class.java,
            "flush" to null,
            "publishHover" to null,
            "finishIfStale" to Long::class.javaPrimitiveType!!,
        )
        for ((name, argument) in entryPoints) {
            val method = if (argument == null) {
                PenPacketStroke::class.java.getDeclaredMethod(name)
            } else {
                PenPacketStroke::class.java.getDeclaredMethod(name, argument)
            }
            assertTrue(
                "$name must be synchronized: the driver's thread and the frame thread both call it",
                java.lang.reflect.Modifier.isSynchronized(method.modifiers),
            )
        }
    }
}

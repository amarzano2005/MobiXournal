package com.mobixournal.desktop

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
     * The hammer above can miss a narrow window, so the **shape** of the hand-off is asserted too.
     *
     * The driver's entry point must stay a lock-free enqueue: a monitor there is what froze the window,
     * because the message thread took it and then wrote snapshot state while the frame thread waited for
     * it inside a frame. `packet` is therefore not `synchronized`, and everything else runs from
     * [PenPacketStroke.flush] on the frame thread — which the test above proves by using both threads at
     * once and seeing no `null` and no lost stroke.
     */
    @Test
    fun `the driver's entry point is a lock-free enqueue`() {
        val packet = PenPacketStroke::class.java.getDeclaredMethod("packet", PenPacket::class.java)
        assertFalse(
            "packet() must not take a monitor: the driver's thread may never wait on a frame",
            java.lang.reflect.Modifier.isSynchronized(packet.modifiers),
        )
        val inbox = PenPacketStroke::class.java.getDeclaredField("inbox").type
        assertEquals(
            "the hand-off is a queue two threads may share without a lock",
            java.util.concurrent.ConcurrentLinkedQueue::class.java,
            inbox,
        )
    }
}

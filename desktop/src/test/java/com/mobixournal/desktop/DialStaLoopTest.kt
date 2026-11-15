package com.mobixournal.desktop

import com.mobixournal.desktop.ink.DialStaLoop
import com.mobixournal.desktop.ink.DialUser32
import com.sun.jna.Native
import com.sun.jna.Pointer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The dial's own STA thread, as a pump — driven by a fake Win32 instead of Windows.
 *
 * `TabletDial` now lives on a thread that runs `GetMessageW` in a loop, because AWT's event queue never
 * pumps a Win32 message and the controller's hidden message window would therefore never be called.
 * That claim is testable without a radial controller: what matters is that a task posted from another
 * thread **runs on the pump's thread** and hands its answer back, that ordinary messages are translated
 * and dispatched rather than swallowed, and that `quit` ends the loop and leaves later calls answering
 * their default instead of hanging.
 */
class DialStaLoopTest {

    /** A Win32 message queue in memory: `GetMessageW` blocks on it exactly as the real one does. */
    private class FakeUser32 : DialUser32 {
        val queued = LinkedBlockingQueue<Int>()
        val dispatched = LinkedBlockingQueue<Int>()

        /** The real call is only there to make Windows create the queue; nothing arrives at peek time. */
        override fun PeekMessageW(msg: Pointer, hwnd: Pointer?, minFilter: Int, maxFilter: Int, removeMsg: Int): Boolean =
            false

        override fun GetMessageW(msg: Pointer, hwnd: Pointer?, minFilter: Int, maxFilter: Int): Int {
            val id = queued.take()
            if (id == WM_QUIT) return 0
            msg.setInt(0, 0)
            msg.setLong(Native.POINTER_SIZE.toLong(), id.toLong())
            return 1
        }

        override fun TranslateMessage(msg: Pointer): Boolean {
            dispatched.add(msg.getInt(Native.POINTER_SIZE.toLong()))
            return true
        }

        override fun DispatchMessageW(msg: Pointer): Pointer? = null

        override fun PostThreadMessageW(threadId: Int, msg: Int, wParam: Long, lParam: Long): Boolean =
            queued.add(msg)

        companion object {
            const val WM_QUIT = 0x0012
            const val WM_MOUSEMOVE = 0x0200
        }
    }

    private class Running(val loop: DialStaLoop, val thread: Thread, val stopped: CountDownLatch)

    /** Start the pump on its own thread, as `TabletDial.open` does. */
    private fun start(messages: FakeUser32): Running {
        val loopRef = AtomicReference<DialStaLoop>()
        val ready = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val thread = Thread({
            val loop = DialStaLoop(messages, THREAD_ID)
            loopRef.set(loop)
            ready.countDown()
            loop.runPump { stopped.countDown() }
        }, "mobix-dial-sta-test")
        thread.isDaemon = true
        thread.start()
        assertTrue("the pump thread started", ready.await(2, TimeUnit.SECONDS))
        return Running(loopRef.get(), thread, stopped)
    }

    @Test
    fun `a task posted from another thread runs on the pump's thread and returns its answer`() {
        val messages = FakeUser32()
        val running = start(messages)
        try {
            val ranOn = AtomicReference<Thread>()
            assertEquals("the block's value comes back", 42, running.loop.invoke(-1) {
                ranOn.set(Thread.currentThread())
                42
            })
            assertSame("it ran on the pump, not on the caller", running.thread, ranOn.get())
        } finally {
            running.loop.quit()
            assertTrue("quit ends the pump", running.stopped.await(2, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `a task posted from the pump itself runs inline instead of waiting on its own queue`() {
        val messages = FakeUser32()
        val running = start(messages)
        try {
            assertEquals(7, running.loop.invoke(-1) { running.loop.invoke(-2) { 7 } })
            assertEquals(
                "the block knows it is on the pump",
                true,
                running.loop.invoke(false) { running.loop.isPumpThread() },
            )
            assertEquals("and a caller knows it is not", false, running.loop.isPumpThread())
        } finally {
            running.loop.quit()
            true
        }
    }

    @Test
    fun `window messages are translated and dispatched rather than swallowed by the task pump`() {
        val messages = FakeUser32()
        val running = start(messages)
        try {
            messages.queued.add(FakeUser32.WM_MOUSEMOVE)
            assertEquals(
                "the pump hands ordinary messages to Translate/Dispatch",
                FakeUser32.WM_MOUSEMOVE,
                messages.dispatched.poll(2, TimeUnit.SECONDS),
            )
        } finally {
            running.loop.quit()
            true
        }
    }

    @Test
    fun `after the pump stops a call answers its default instead of hanging`() {
        val messages = FakeUser32()
        val running = start(messages)
        running.loop.quit()
        assertTrue("the loop stopped", running.stopped.await(2, TimeUnit.SECONDS))

        assertEquals("no thread, no call", false, running.loop.invoke(false) { true })
        assertEquals("and quit is idempotent", Unit, running.loop.quit())
    }

    private companion object {
        const val THREAD_ID = 4242
    }
}

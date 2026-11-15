package com.mobixournal.desktop

import com.mobixournal.desktop.ink.ComHandler
import com.mobixournal.desktop.ink.comGuid
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Calls the actual native delegate table, without a connected controller or a WinRT factory. */
class TabletDialAbiTest {
    private val rotationIid = "8FE73AC9-8A36-5152-815D-03310EE8BF85"

    @Test
    fun `Windows delegate slot three dispatches the original event without changing its sender`() {
        assumeTrue(Platform.isWindows())
        var received: Pointer? = null
        val handler = ComHandler({ received = it }, {}, rotationIid)
        try {
            val sender = Memory(64).apply { clear(); setLong(0, 123456789L) }
            val args = Memory(8).apply { setDouble(0, 12.5) }
            assertEquals(0, nativeCall(handler, 3, sender, args))
            assertEquals(args, received)
            assertEquals("a delegate must never write into its event sender", 123456789L, sender.getLong(0))
        } finally {
            handler.releaseMarshaler()
        }
    }

    @Test
    fun `delegate refuses inspectable and unrelated delegate interfaces and clears their outputs`() {
        assumeTrue(Platform.isWindows())
        val handler = ComHandler({}, {}, rotationIid)
        try {
            val out = Memory(Native.POINTER_SIZE.toLong())
            assertEquals(0, nativeCall(handler, 0, comGuid(rotationIid), out))
            assertEquals(handler.objectPointer, out.getPointer(0))
            for (iid in listOf(
                "AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90", // IInspectable has a different ABI.
                "D09D9FCC-EDB8-56C0-856D-70E477A9DDF3", // ButtonClicked is not RotationChanged.
            )) {
                out.setPointer(0, handler.objectPointer)
                assertEquals(0x80004002.toInt(), nativeCall(handler, 0, comGuid(iid), out))
                assertNull(out.getPointer(0))
            }
        } finally {
            handler.releaseMarshaler()
        }
    }

    private fun nativeCall(handler: ComHandler, slot: Int, vararg args: Any): Int {
        val self = handler.objectPointer
        val function = self.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)
        return Function.getFunction(function, Function.ALT_CONVENTION).invokeInt(arrayOf(self, *args))
    }
}

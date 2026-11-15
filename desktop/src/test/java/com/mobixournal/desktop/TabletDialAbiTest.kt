package com.mobixournal.desktop

import com.mobixournal.desktop.ink.ComHandler
import com.mobixournal.desktop.ink.EmptySystemMenuItems
import com.mobixournal.desktop.ink.IID_AGILE_OBJECT
import com.mobixournal.desktop.ink.IID_INSPECTABLE
import com.mobixournal.desktop.ink.IID_ITERABLE_MENU_KIND
import com.mobixournal.desktop.ink.IID_ITERATOR_MENU_KIND
import com.mobixournal.desktop.ink.IID_UNKNOWN
import com.mobixournal.desktop.ink.Ole32Library
import com.mobixournal.desktop.ink.TabletDial
import com.mobixournal.desktop.ink.comGuid
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.EventQueue
import java.awt.Frame

/** Calls the actual native delegate and collection tables, plus the live window controller configuration. */
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
            assertEquals(0, nativeCall(handler.objectPointer, 3, sender, args))
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
            assertEquals(0, nativeCall(handler.objectPointer, 0, comGuid(rotationIid), out))
            assertEquals(handler.objectPointer, out.getPointer(0))
            for (iid in listOf(
                IID_INSPECTABLE, // IInspectable has a different ABI.
                "D09D9FCC-EDB8-56C0-856D-70E477A9DDF3", // ButtonClicked is not RotationChanged.
            )) {
                out.setPointer(0, handler.objectPointer)
                assertEquals(0x80004002.toInt(), nativeCall(handler.objectPointer, 0, comGuid(iid), out))
                assertNull(out.getPointer(0))
            }
        } finally {
            handler.releaseMarshaler()
        }
    }

    @Test
    fun `empty system menu items iterable answers inspectable and iterator slots and refuses unknown interfaces`() {
        assumeTrue(Platform.isWindows())
        val empty = EmptySystemMenuItems()
        val iterable = empty.iterablePointer
        val out = Memory(Native.POINTER_SIZE.toLong())

        for (iid in listOf(IID_UNKNOWN, IID_INSPECTABLE, IID_AGILE_OBJECT, IID_ITERABLE_MENU_KIND)) {
            out.clear()
            assertEquals(0, nativeCall(iterable, 0, comGuid(iid), out))
            assertEquals(iterable, out.getPointer(0))
        }

        // Probing IIDs such as COM's internal 2132B005-C604-4354-85BD-8F2E24181B0C must be refused so
        // SetDefaultMenuItems never calls methods outside the 7-slot IIterable vtable.
        for (iid in listOf("2132B005-C604-4354-85BD-8F2E24181B0C", IID_ITERATOR_MENU_KIND)) {
            out.setPointer(0, iterable)
            assertEquals(0x80004002.toInt(), nativeCall(iterable, 0, comGuid(iid), out))
            assertNull(out.getPointer(0))
        }

        val iidCount = Memory(4).apply { setInt(0, -1) }
        val iids = Memory(Native.POINTER_SIZE.toLong())
        assertEquals(0, nativeCall(iterable, 3, iidCount, iids))
        assertEquals(1, iidCount.getInt(0))
        val allocatedIids = iids.getPointer(0)
        assertNotNull(allocatedIids)
        val ole32 = Native.load("ole32", Ole32Library::class.java) as Ole32Library
        ole32.CoTaskMemFree(allocatedIids)

        val iteratorOut = Memory(Native.POINTER_SIZE.toLong())
        assertEquals(0, nativeCall(iterable, 6, iteratorOut))
        val iterator = iteratorOut.getPointer(0)
        assertEquals(empty.iteratorPointer, iterator)

        val hasCurrent = Memory(4).apply { setInt(0, 0x7F7F7F7F) }
        assertEquals(0, nativeCall(iterator, 7, hasCurrent))
        assertEquals("WinRT boolean false must write a zero byte", 0.toByte(), hasCurrent.getByte(0))

        val moveNext = Memory(4).apply { setInt(0, 0x7F7F7F7F) }
        assertEquals(0, nativeCall(iterator, 8, moveNext))
        assertEquals(0.toByte(), moveNext.getByte(0))

        val current = Memory(4).apply { setInt(0, 99) }
        assertEquals(0x8000000B.toInt(), nativeCall(iterator, 6, current))
    }

    @Test
    fun `opening the dial clears default tools so MobiXournal is immediately active and survives reopen`() {
        assumeTrue(Platform.isWindows())
        EventQueue.invokeAndWait {
            val frame = Frame("DialTest")
            frame.setBounds(100, 100, 240, 240)
            frame.isVisible = true
            try {
                val logs = mutableListOf<String>()
                val first = TabletDial.open(frame, onRotation = {}, onClick = {}, log = { logs += it })
                if (first != null) {
                    try {
                        assertTrue(
                            "SetDefaultMenuItems(empty) must succeed: $logs",
                            logs.any { it.contains("dial: default menu items cleared hr=0x00000000") },
                        )
                        assertTrue(
                            "MobiXournal tool must be active immediately after clearing built-in tools",
                            first.isToolActive(),
                        )
                    } finally {
                        first.close()
                    }
                }
                val second = TabletDial.open(frame, onRotation = {}, onClick = {}, log = { logs += it })
                if (second != null) {
                    try {
                        assertTrue(
                            "MobiXournal tool must stay active after a device-change reopen",
                            second.isToolActive(),
                        )
                    } finally {
                        second.close()
                    }
                }
            } finally {
                frame.dispose()
            }
        }
    }

    private fun nativeCall(self: Pointer, slot: Int, vararg args: Any): Int {
        val function = self.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)
        return Function.getFunction(function, Function.ALT_CONVENTION).invokeInt(arrayOf(self, *args))
    }
}

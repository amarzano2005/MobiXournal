package com.mobixournal.desktop

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.W32APIOptions

/**
 * The little of WinRT that the window needs: activation, `HSTRING`s, and calling a vtable slot.
 *
 * WinRT is COM with a frozen ABI, so a call is "pointer, slot, arguments" and nothing more — no runtime,
 * no projection, no dependency. The **slot numbers** are the part that cannot be guessed: a WinRT
 * interface's vtable carries `IUnknown`'s three methods **and** `IInspectable`'s three before its own, so
 * an interface's first method is slot 6. Every number the ink bridge uses comes from the machine's own
 * metadata (`Windows.UI.winmd`), which is why they live beside the call sites rather than here.
 *
 * Every apartment entry is per thread ([enter]): the ink objects are documented as **not agile**, so the
 * objects a thread activates are the objects that thread uses.
 */
internal object WinRt {

    const val S_OK = 0

    private val api: Combase by lazy { Native.load("combase", Combase::class.java, W32APIOptions.DEFAULT_OPTIONS) }

    private val entered: ThreadLocal<Boolean> = ThreadLocal.withInitial { api.RoInitialize(RO_INIT_MULTITHREADED) == S_OK }

    /** True when this thread has a COM apartment — the state every call below needs. */
    fun enter(): Boolean = entered.get()

    /** An instance of the activatable class [className], or null when this Windows does not have it. */
    fun activate(className: String): Pointer? {
        val hstring = PointerByReference()
        if (api.WindowsCreateString(WString(className), className.length, hstring) != S_OK) return null
        return try {
            val instance = PointerByReference()
            if (api.RoActivateInstance(hstring.value, instance) != S_OK) null else instance.value
        } finally {
            api.WindowsDeleteString(hstring.value)
        }
    }

    /** Calls [slot] and answers its `HRESULT`; the out-parameter form is [callOut]. */
    fun call(instance: Pointer, slot: Int, vararg args: Any?): Int = invoke(instance, slot, args.toList())

    /** Calls [slot], whose last argument is an out interface pointer, and answers that pointer. */
    fun callOut(instance: Pointer, slot: Int, vararg args: Any?): Pointer? {
        val out = PointerByReference()
        return if (invoke(instance, slot, args.toList() + out) == S_OK) out.value else null
    }

    fun release(instance: Pointer?) {
        instance ?: return
        call(instance, RELEASE_SLOT)
    }

    /** `IVectorView<T>.Size`. */
    fun size(view: Pointer): Int {
        val count = Memory(4)
        return if (call(view, VECTOR_SIZE_SLOT, count) == S_OK) count.getInt(0) else 0
    }

    /** One `HSTRING` out of a `IVectorView<HSTRING>`, copied out and the runtime's copy released. */
    fun stringAt(view: Pointer, index: Int): String? {
        val out = PointerByReference()
        if (call(view, VECTOR_GET_AT_SLOT, index, out) != S_OK) return null
        val hstring = out.value ?: return null
        return try {
            val length = Memory(4)
            val raw = api.WindowsGetStringRawBuffer(hstring, length)
            raw?.getWideString(0)?.take(length.getInt(0)).orEmpty().ifEmpty { null }
        } finally {
            api.WindowsDeleteString(hstring)
        }
    }

    /** The IIDs the object implements, in its class's own order — what a bridge's blanks are filled from. */
    fun iids(instance: Pointer): List<String> {
        val count = Memory(4)
        val array = PointerByReference()
        if (call(instance, GET_IIDS_SLOT, count, array) != S_OK) return emptyList()
        val ids = array.value ?: return emptyList()
        return try {
            (0 until count.getInt(0)).map { index -> guidAt(ids.share(index.toLong() * 16L)) }
        } finally {
            ole32.CoTaskMemFree(ids)
        }
    }

    private fun guidAt(pointer: Pointer): String {
        val data1 = pointer.getInt(0).toLong() and 0xFFFFFFFFL
        val data2 = pointer.getShort(4).toLong() and 0xFFFFL
        val data3 = pointer.getShort(6).toLong() and 0xFFFFL
        val rest = pointer.getByteArray(8, 8).joinToString("") { "%02x".format(it) }
        return "%08x-%04x-%04x-%s-%s".format(data1, data2, data3, rest.take(4), rest.drop(4))
    }

    private val ole32: Ole32 by lazy { Native.load("ole32", Ole32::class.java) }

    private interface Ole32 : Library {
        fun CoTaskMemFree(block: Pointer?)
    }

    /** `IInspectable::GetIids` — the third of the WinRT-wide six, after `Release`. */
    private const val GET_IIDS_SLOT = 3

    private fun invoke(instance: Pointer, slot: Int, args: List<Any?>): Int {
        val vtable = instance.getPointer(0) ?: return -1
        val address = vtable.getPointer(slot.toLong() * Native.POINTER_SIZE) ?: return -1
        // The vtable's own address: JNA's function pointer, called with this first, as COM wants.
        val function = Function.getFunction(address, Function.C_CONVENTION)
        return function.invokeInt((listOf(instance) + args).toTypedArray())
    }

    /** `combase.dll`: the apartment, activation, and the `HSTRING` heap. */
    private interface Combase : Library {

        fun RoInitialize(initType: Int): Int

        fun RoActivateInstance(classId: Pointer, instance: PointerByReference): Int

        fun WindowsCreateString(text: WString, length: Int, out: PointerByReference): Int

        fun WindowsGetStringRawBuffer(hstring: Pointer, length: Pointer?): Pointer?

        fun WindowsDeleteString(hstring: Pointer): Int
    }

    private const val RO_INIT_MULTITHREADED = 1

    /** `IUnknown::Release`. */
    private const val RELEASE_SLOT = 2

    /** `IVectorView<T>`: GetAt, Size — after its six WinRT-wide methods. */
    private const val VECTOR_GET_AT_SLOT = 6
    private const val VECTOR_SIZE_SLOT = 7
}

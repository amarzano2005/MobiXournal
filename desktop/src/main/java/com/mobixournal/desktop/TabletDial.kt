package com.mobixournal.desktop

import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import java.util.Locale
import kotlin.math.exp

/**
 * **The Windows wheel dial** — a rotary control read through the WinRT `RadialController`, which is the
 * only documented way a desktop app gets a dial's rotation.
 *
 * The second measurement of `desktop-tablet-dial-spike` is why this file is COM and not a message
 * handler: turning the dial produced **nothing at all** in the probe — no wheel, no scroll, no key — and
 * the Win32 pointer types have no dial in them (`PT_POINTER`, `PT_TOUCH`, `PT_PEN`, `PT_MOUSE`,
 * `PT_TOUCHPAD`, and that is the list). A dial is a *wheel* device, and Windows hands it to the app as a
 * `RadialController`, bound to a window by `IRadialControllerInterop::CreateForWindow`. Rotation then
 * arrives as a `RotationChanged` event carrying `RotationDeltaInDegrees` — in degrees, which is what
 * [dialZoomFactor] turns into a zoom step.
 *
 * Everything here is called **by vtable slot**, because a JVM has no C++ headers: the slot numbers and
 * the two GUIDs this file needs are copied from the SDK's generated
 * `windows.ui.input.h` (10.0.16299.0) and named in the constants below, so a reader can check each one
 * against its declaration instead of trusting a black box.
 *
 * Two Windows behaviours shape the design:
 *  - a custom tool must be **added to the dial's menu and selected** before the app receives rotation
 *    at all (while a built-in tool such as Volume is active, the system is the one handling the dial),
 *    so [open] appends a MobiXournal item and selects it — the reader who turns the dial and sees the
 *    window zoom has not had to find it in the menu first;
 *  - event handlers are WinRT objects. The handler this file hands over implements `IUnknown` and an
 *    `Invoke`, which is the whole ABI of WinRT's `TypedEventHandler`; its `QueryInterface` accepts any
 *    IID, because the closed-generic IID of that delegate is metadata we cannot name from a JVM — while
 *    the layout, three IUnknown slots and one `Invoke`, is fixed and public.
 *
 * No dial attached is the normal case: [open] answers `null` after logging which step said so, and the
 * window keeps the wheel and the keyboard. Nothing here is ever an exception to the user.
 */
internal interface CombaseLibrary : StdCallLibrary {

    fun RoInitialize(initType: Int): Int

    fun RoUninitialize()

    fun RoGetActivationFactory(classId: Pointer, iid: Pointer, factory: PointerByReference): Int

    fun WindowsCreateString(source: WString, length: Int, out: PointerByReference): Int

    fun WindowsDeleteString(string: Pointer): Int
}

/**
 * A GUID as the sixteen bytes `REFIID` wants: `Data1`–`Data3` little-endian, `Data4` as written.
 *
 * The literals in [TabletDial] are the SDK's own, so this parse is what makes them bytes — and
 * `TabletDialTest` checks it against a known GUID rather than trusting the byte order to luck.
 */
internal fun comGuid(text: String): Memory {
    val hex = text.replace("-", "")
    require(hex.length == 32) { "a GUID is 32 hex digits, not '$text'" }
    val bytes = ByteArray(16)
    fun byteAt(index: Int): Byte = hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    for (i in 0..3) bytes[i] = byteAt(3 - i)
    for (i in 0..1) bytes[4 + i] = byteAt(5 - i)
    for (i in 0..1) bytes[6 + i] = byteAt(7 - i)
    for (i in 8..15) bytes[i] = byteAt(i)
    return Memory(16).also { it.write(0, bytes, 0, 16) }
}

// Slots: 0–5 are IUnknown and IInspectable on every WinRT interface, so a member's slot is its position
// among the interface's own methods, counted from 6 in declaration order.
private const val SLOT_RELEASE = 2
private const val SLOT_CREATE_FOR_WINDOW = 6 // IRadialControllerInterop
private const val SLOT_GET_MENU = 6 // IRadialController
private const val SLOT_PUT_ROTATION_RESOLUTION = 8 // IRadialController
private const val SLOT_ADD_ROTATION_CHANGED = 19 // IRadialController
private const val SLOT_REMOVE_ROTATION_CHANGED = 20 // IRadialController
private const val SLOT_ADD_BUTTON_CLICKED = 21 // IRadialController
private const val SLOT_REMOVE_BUTTON_CLICKED = 22 // IRadialController
private const val SLOT_MENU_GET_ITEMS = 6 // IRadialControllerMenu
private const val SLOT_MENU_SELECT_ITEM = 10 // IRadialControllerMenu
private const val SLOT_CREATE_FROM_KNOWN_ICON = 7 // IRadialControllerMenuItemStatics
private const val SLOT_VECTOR_APPEND = 13 // IVector<T>: GetAt, get_Size, GetView, IndexOf, SetAt, InsertAt,
// RemoveAt, then Append
private const val SLOT_ROTATION_DELTA = 6 // IRadialControllerRotationChangedEventArgs

/** A WinRT object's method at [slot]: the function pointer, with `this` still to be passed. */
private fun vtableSlot(objectPointer: Pointer, slot: Int): Pointer =
    objectPointer.getPointer(0L).getPointer(slot.toLong() * Native.POINTER_SIZE)

/** Call [slot] of [objectPointer] with [args], answering the `HRESULT`. */
private fun call(objectPointer: Pointer, slot: Int, vararg args: Any?): Int =
    Function.getFunction(vtableSlot(objectPointer, slot), Function.ALT_CONVENTION)
        .invokeInt(arrayOf(objectPointer, *args))

/** `IUnknown::Release` — slot 2 of every COM object. */
private fun release(objectPointer: Pointer?) {
    if (objectPointer == null) return
    runCatching { call(objectPointer, SLOT_RELEASE) }
}

private fun hresult(value: Int): String = String.format(Locale.ROOT, "0x%08X", value)

// The four interfaces a handler has to answer by name.
private const val IID_UNKNOWN = "00000000-0000-0000-C000-000000000046"
private const val IID_INSPECTABLE = "AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90"
private const val IID_AGILE_OBJECT = "94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90"
private const val IID_MARSHAL = "00000003-0000-0000-C000-000000000046"

/** `INoMarshal`: "this object must never be marshaled" — a claim this handler cannot make, see below. */
private const val IID_NO_MARSHAL = "ECC8691B-C1DB-4DC0-855E-65F6C551AF49"

/** `IdentityUnmarshal`, COM's internal "I unmarshal objects myself" marker — refused, see below. */
private const val IID_IDENTITY_UNMARSHAL = "0000001B-0000-0000-C000-000000000046"

/**
 * The two delegate interfaces this file implements, **measured** from the radial controller rather than
 * guessed: `ITypedEventHandler<RadialController, RadialControllerRotationChangedEventArgs>` and its
 * `ButtonClicked` twin are closed generics, so their IIDs are parameterised hashes that appear in no
 * header — the probe logged them, and they are recorded here (`docs/desktop-workstation.md`).
 */
private const val IID_ROTATION_HANDLER = "8FE73AC9-8A36-5152-815D-03310EE8BF85"
private const val IID_BUTTON_HANDLER = "D09D9FCC-EDB8-56C0-856D-70E477A9DDF3"

private const val HRESULT_OK = 0
private const val HRESULT_NO_INTERFACE = 0x80004002.toInt()
private const val HRESULT_NOT_IMPLEMENTED = 0x80004001.toInt()

/** `QueryInterface(self, REFIID, void**)`. */
private fun interface ComQueryInterface : StdCallLibrary.StdCallCallback {
    fun callback(self: Pointer, iid: Pointer, out: Pointer): Int
}

/** `AddRef` and `Release`, which for a handler that never dies are counters no one reads. */
private fun interface ComRefCount : StdCallLibrary.StdCallCallback {
    fun callback(self: Pointer): Int
}

/** `IInspectable::GetIids(self, ULONG*, IID**)`. */
private fun interface ComGetIids : StdCallLibrary.StdCallCallback {
    fun callback(self: Pointer, count: Pointer, ids: Pointer): Int
}

/** `IInspectable::GetRuntimeClassName` and `GetTrustLevel`: one out parameter each. */
private fun interface ComInspectableOut : StdCallLibrary.StdCallCallback {
    fun callback(self: Pointer, out: Pointer): Int
}

/** `TypedEventHandler::Invoke(self, sender, args)` — the one method of a WinRT event handler. */
private fun interface ComInvoke : StdCallLibrary.StdCallCallback {
    fun callback(self: Pointer, sender: Pointer, args: Pointer): Int
}

/** `ole32!CoCreateFreeThreadedMarshaler` — the standard `IMarshal` for a free-threaded object. */
private interface Ole32Library : StdCallLibrary {
    fun CoCreateFreeThreadedMarshaler(outer: Pointer?, marshaler: PointerByReference): Int
}

/**
 * One WinRT event handler, as an object the JVM owns: a block of memory whose first word points at a
 * vtable.
 *
 * The vtable is the **`IInspectable` + `Invoke`** shape — seven slots — because WinRT asks its handlers
 * for `IInspectable` and calls `GetIids`/`GetTrustLevel` on the way in: a four-slot vtable answered that
 * request with a pointer to its `Invoke`, and the first real dial run died on it
 * (`Invalid memory access`, `docs/desktop-workstation.md`).
 *
 * `IMarshal` is answered by a **free-threaded marshaler** (`CoCreateFreeThreadedMarshaler`) rather than
 * by writing the interface out: registering a handler is exactly the moment WinRT marshals it, and the
 * run without one got `CO_E_NOT_SUPPORTED` (0x80004021) from both `add_RotationChanged` and
 * `add_ButtonClicked` — the object was refused, not the rotation. With the marshaler the handler is
 * genuinely free-threaded, which is also true of it: [invoke] may run on Windows' thread and the caller
 * is the one that marshals.
 *
 * Every parameter is a [Pointer] or a primitive: JNA's callback trampolines marshal those, and a
 * `ByReference` there is a shape not worth betting a window on.
 */
private class ComHandler(
    private val invoke: (args: Pointer) -> Unit,
    private val log: (String) -> Unit,
) {

    /** The object pointer WinRT is handed: `this` for every method, and its own vtable. */
    val objectPointer: Memory

    @Suppress("unused") // Holds the callbacks and the vtable alive for as long as the handler lives.
    private val kept: List<Any>

    /** The free-threaded marshaler that answers `IMarshal`, or null when ole32 refused one. */
    private val marshaler: Pointer?

    init {
        val self = Memory(Native.POINTER_SIZE.toLong())
        val unknown = comGuid(IID_UNKNOWN)
        val inspectable = comGuid(IID_INSPECTABLE)
        val agile = comGuid(IID_AGILE_OBJECT)
        val marshal = comGuid(IID_MARSHAL)
        val noMarshal = comGuid(IID_NO_MARSHAL)
        val identity = comGuid(IID_IDENTITY_UNMARSHAL)
        val delegates = listOf(comGuid(IID_ROTATION_HANDLER), comGuid(IID_BUTTON_HANDLER))
        marshaler = runCatching {
            val ole32 = Native.load("ole32", Ole32Library::class.java) as Ole32Library
            val out = PointerByReference()
            val hr = ole32.CoCreateFreeThreadedMarshaler(null, out)
            if (hr < 0) {
                log("dial: no free-threaded marshaler (${hresult(hr)})")
                null
            } else {
                out.value
            }
        }.getOrElse {
            log("dial: no free-threaded marshaler (${it.message})")
            null
        }
        val query = ComQueryInterface { pointer, iid, out ->
            log("dial: handler asked for ${iid.guidText()}")
            val answer = when {
                // The whitelist is deliberate: answering `S_OK` to an interface this table does not
                // implement is a promise to answer its methods, and the table has seven slots. Two
                // measurement runs proved the cost — `INoMarshal` answered `S_OK` (a lie: this object is
                // marshaled by the FTM below) produced `CO_E_NOT_SUPPORTED`, and `IdentityUnmarshal`
                // answered `S_OK` sent COM into slot 6 of a vtable that holds `Invoke` (an
                // `Invalid memory access` in the window procedure).
                iid.isGuid(noMarshal) || iid.isGuid(identity) -> HRESULT_NO_INTERFACE
                iid.isGuid(marshal) -> marshaler?.let { ftm ->
                    // The marshaler's own QueryInterface: the interface is its, not this table's.
                    Function.getFunction(vtableSlot(ftm, 0), Function.ALT_CONVENTION)
                        .invokeInt(arrayOf(ftm, iid, out))
                } ?: HRESULT_NO_INTERFACE
                iid.isGuid(unknown) || iid.isGuid(inspectable) || iid.isGuid(agile) -> HRESULT_OK
                delegates.any { iid.isGuid(it) } -> HRESULT_OK
                else -> HRESULT_NO_INTERFACE
            }
            if (answer == HRESULT_OK && !iid.isGuid(marshal)) out.setPointer(0L, pointer)
            answer
        }
        val addRef = ComRefCount { 2 }
        val releaseRef = ComRefCount { 1 }
        val iids = ComGetIids { _, count, _ ->
            count.setInt(0L, 0)
            HRESULT_OK
        }
        val runtimeName = ComInspectableOut { _, out ->
            out.setPointer(0L, null)
            HRESULT_NOT_IMPLEMENTED
        }
        val trust = ComInspectableOut { _, out ->
            out.setInt(0L, 0) // BaseTrust
            HRESULT_OK
        }
        val call = ComInvoke { _, _, args ->
            runCatching { invoke(args) }
            HRESULT_OK
        }
        val vtable = Memory(7L * Native.POINTER_SIZE)
        val slots = listOf<com.sun.jna.Callback>(query, addRef, releaseRef, iids, runtimeName, trust, call)
        for ((index, slot) in slots.withIndex()) {
            vtable.setPointer(index.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(slot))
        }
        self.setPointer(0L, vtable)
        objectPointer = self
        kept = slots + listOf(vtable, unknown, inspectable, agile, marshal, noMarshal, identity) + delegates
    }

    /** Give the marshaler back once the events that used it are gone. */
    fun releaseMarshaler() {
        release(marshaler)
    }
}

/** A GUID as text, for the diagnostics log: `Data1`–`Data3` big-endian, `Data4` as bytes. */
private fun Pointer.guidText(): String = buildString {
    append(String.format(Locale.ROOT, "%08X-%04X-%04X-", getInt(0), getShort(4).toInt() and 0xFFFF, getShort(6).toInt() and 0xFFFF))
    for (index in 8..15) {
        append(String.format(Locale.ROOT, "%02X", getByte(index.toLong()).toInt() and 0xFF))
        if (index == 9) append('-')
    }
}

/** `16` bytes of [iid] compared with a GUID built by [comGuid]. */
private fun Pointer.isGuid(expected: Memory): Boolean {
    for (offset in 0 until 16) {
        if (getByte(offset.toLong()) != expected.getByte(offset.toLong())) return false
    }
    return true
}

/**
 * The dial's rotation, as a zoom step: **exponential**, so a nudge at 400 % moves the same proportion as
 * one at 40 %, and one full turn is about two-fold.
 *
 * [degrees] is the signed delta Windows reports — positive for clockwise. 0.002 per degree is one turn
 * (360°) = e^0.72 ≈ 2.05×, and one degree (the resolution this file asks for) ≈ 0.2 %, which is a zoom
 * that reads as smooth rather than as steps. Clamped per event so a driver that reports a whole turn at
 * once cannot jump the page to the end of its range.
 */
internal fun dialZoomFactor(degrees: Double): Float =
    exp(degrees * 0.002).toFloat().coerceIn(0.5f, 2f)

/**
 * A live `RadialController` bound to [TabletDial.open]'s window.
 *
 * [attached] is what the status bar reports: a controller exists and this window is registered for its
 * rotation. Whether a dial is on the desk is Windows' business — rotation simply arrives when one is.
 */
internal class TabletDial private constructor(
    private val combase: CombaseLibrary,
    private val initializedApartment: Boolean,
    private val controller: Pointer,
    private val menu: Pointer,
    private val items: Pointer,
    private val item: Pointer,
    private val handlers: List<ComHandler>,
    private val rotationToken: Long,
    private val buttonToken: Long,
) : AutoCloseable {

    /** True once the rotation event is registered — the one claim this class makes. */
    val attached: Boolean = true

    override fun close() {
        runCatching { call(controller, SLOT_REMOVE_ROTATION_CHANGED, rotationToken) }
        runCatching { call(controller, SLOT_REMOVE_BUTTON_CLICKED, buttonToken) }
        handlers.forEach { it.releaseMarshaler() }
        release(item)
        release(items)
        release(menu)
        release(controller)
        // The apartment this thread opened for WinRT goes back with the reader: balanced with the
        // RoInitialize below, and only when that call is the one that created it.
        if (initializedApartment) runCatching { combase.RoUninitialize() }
    }

    companion object {

        /** `RuntimeClass_Windows_UI_Input_RadialController` — the controller's activation class. */
        private const val CLASS_CONTROLLER = "Windows.UI.Input.RadialController"

        /** `RuntimeClass_Windows_UI_Input_RadialControllerMenuItem`. */
        private const val CLASS_MENU_ITEM = "Windows.UI.Input.RadialControllerMenuItem"

        /** The item's label in the dial's menu. */
        const val MENU_LABEL = "MobiXournal"

        /** `IRadialControllerInterop`, quoted from `RadialControllerInterop.h`. */
        private const val IID_INTEROP = "1B0535C9-57AD-45C1-9D79-AD5C34360513"

        /** `IRadialController`, quoted from `windows.ui.input.h`. */
        private const val IID_CONTROLLER = "3055D1C8-DF51-43D4-B23B-0E1037467A09"

        /** `IRadialControllerMenuItemStatics`, quoted from `windows.ui.input.h`. */
        private const val IID_ITEM_STATICS = "249E0887-D842-4524-9DF8-E0D647EDC887"

        /** `RadialControllerMenuKnownIcon.Zoom` — the item's icon is a magnifier, which is what it does. */
        private const val KNOWN_ICON_ZOOM = 1

        /** Degrees between rotation events: one, the finest step Windows accepts. */
        private const val ROTATION_RESOLUTION_DEGREES = 1.0

        /** `RO_INIT_SINGLETHREADED`: the apartment a WinRT factory needs on the calling thread — an STA, because the window's own event thread is the one with a message loop that delivers the dial's events. */
        private const val RO_INIT_SINGLETHREADED = 0

        /** `RPC_E_CHANGED_MODE`: the thread is already in the *other* apartment — usable as it is. */
        private val RPC_E_CHANGED_MODE = 0x80010106.toInt()

        /**
         * Bind a controller to [window] and register its events, or answer null.
         *
         * [onRotation] receives the signed delta in degrees, [onClick] the dial's own button. Both run
         * on whichever thread Windows raises the event on — a caller with state to touch must marshal
         * to its UI thread, which is what `DesktopApp` does. [log] gets one line per step, which is how
         * a reader can tell "no dial connected" from "the activation was refused".
         */
        fun open(
            window: Window,
            onRotation: (Double) -> Unit,
            onClick: () -> Unit,
            log: (String) -> Unit,
        ): TabletDial? {
            val combase = runCatching {
                Native.load("combase", CombaseLibrary::class.java) as CombaseLibrary
            }.getOrElse {
                log("dial: combase unavailable (${it.message})")
                return null
            }
            val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull()
            if (hwnd == null) {
                log("dial: the window has no handle yet")
                return null
            }
            // WinRT activation needs an initialized apartment on the calling thread, and a plain AWT
            // thread has none: without this call every factory answers CO_E_NOTINITIALIZED
            // (0x800401F0), which is exactly what the first probe run measured.
            val apartment = combase.RoInitialize(RO_INIT_SINGLETHREADED)
            if (apartment < 0 && apartment != RPC_E_CHANGED_MODE) {
                log("dial: RoInitialize refused (${hresult(apartment)})")
                return null
            }
            val interop = activationFactory(combase, CLASS_CONTROLLER, IID_INTEROP, log) ?: return null
            val controllerRef = PointerByReference()
            val created = call(interop, SLOT_CREATE_FOR_WINDOW, hwnd, comGuid(IID_CONTROLLER), controllerRef)
            release(interop)
            val controller = controllerRef.value
            if (created < 0 || controller == null) {
                log("dial: CreateForWindow refused (${hresult(created)})")
                return null
            }
            log("dial: controller bound to the window")

            // A dial reports in ten-degree steps by default; one degree is what makes a slow turn look
            // like a zoom rather than like the ten notches of a wheel.
            val resolution = call(controller, SLOT_PUT_ROTATION_RESOLUTION, ROTATION_RESOLUTION_DEGREES)
            log("dial: rotation resolution 1°, hr=${hresult(resolution)}")

            val item = menuItem(combase, log)
            val menu = PointerByReference()
            val menuHr = call(controller, SLOT_GET_MENU, menu)
            val menuPointer = menu.value
            if (item == null || menuHr < 0 || menuPointer == null) {
                log("dial: the dial's menu is not available (${hresult(menuHr)})")
                release(item)
                release(menuPointer)
                release(controller)
                return null
            }
            val items = PointerByReference()
            val itemsHr = call(menuPointer, SLOT_MENU_GET_ITEMS, items)
            val itemsPointer = items.value
            val appended = if (itemsPointer != null) call(itemsPointer, SLOT_VECTOR_APPEND, item) else itemsHr
            log("dial: menu item appended hr=${hresult(appended)}")
            if (itemsPointer == null || appended < 0) {
                release(itemsPointer)
                release(menuPointer)
                release(item)
                release(controller)
                return null
            }

            // Ask for our own tool now, so the first turn of the dial is already a zoom. A refusal only
            // means the reader has to pick "MobiXournal" in the dial's menu once — and its HRESULT is what
            // tells "no dial on this desk" apart from "the handler was refused", which is why it is
            // logged before the events are wired.
            val selected = call(menuPointer, SLOT_MENU_SELECT_ITEM, item)
            log("dial: menu item selected hr=${hresult(selected)}")

            val rotation = ComHandler({ args -> onRotation(rotationDelta(args)) }, log)
            val button = ComHandler({ onClick() }, log)
            val rotationToken = Memory(8)
            val buttonToken = Memory(8)
            val rotationHr = call(controller, SLOT_ADD_ROTATION_CHANGED, rotation.objectPointer, rotationToken)
            val buttonHr = call(controller, SLOT_ADD_BUTTON_CLICKED, button.objectPointer, buttonToken)
            log("dial: rotation hr=${hresult(rotationHr)}, button hr=${hresult(buttonHr)}")
            if (rotationHr < 0) {
                release(itemsPointer)
                release(menuPointer)
                release(item)
                release(controller)
                return null
            }
            return TabletDial(
                combase = combase,
                initializedApartment = apartment >= 0,
                controller = controller,
                menu = menuPointer,
                items = itemsPointer,
                item = item,
                handlers = listOf(rotation, button),
                rotationToken = rotationToken.getLong(0),
                buttonToken = buttonToken.getLong(0),
            )
        }

        /** The dial's menu entry, created through the item runtime class's statics. */
        private fun menuItem(combase: CombaseLibrary, log: (String) -> Unit): Pointer? {
            val statics = activationFactory(combase, CLASS_MENU_ITEM, IID_ITEM_STATICS, log) ?: return null
            val text = hstring(combase, MENU_LABEL) ?: return null
            val out = PointerByReference()
            val hr = call(statics, SLOT_CREATE_FROM_KNOWN_ICON, text, KNOWN_ICON_ZOOM, out)
            combase.WindowsDeleteString(text)
            release(statics)
            if (hr < 0) {
                log("dial: CreateFromKnownIcon refused (${hresult(hr)})")
                return null
            }
            return out.value
        }

        private fun rotationDelta(args: Pointer): Double {
            val out = Memory(8)
            val hr = call(args, SLOT_ROTATION_DELTA, out)
            return if (hr >= 0) out.getDouble(0) else 0.0
        }

        /** `RoGetActivationFactory` for [runtimeClass], asking for the interop interface [iid]. */
        private fun activationFactory(
            combase: CombaseLibrary,
            runtimeClass: String,
            iid: String,
            log: (String) -> Unit,
        ): Pointer? {
            val classId = hstring(combase, runtimeClass)
            if (classId == null) {
                log("dial: cannot name $runtimeClass")
                return null
            }
            val out = PointerByReference()
            val hr = combase.RoGetActivationFactory(classId, comGuid(iid), out)
            combase.WindowsDeleteString(classId)
            if (hr < 0) {
                log("dial: no factory for $runtimeClass (${hresult(hr)})")
                return null
            }
            return out.value
        }

        private fun hstring(combase: CombaseLibrary, text: String): Pointer? {
            val out = PointerByReference()
            val hr = combase.WindowsCreateString(WString(text), text.length, out)
            return if (hr >= 0) out.value else null
        }
    }
}

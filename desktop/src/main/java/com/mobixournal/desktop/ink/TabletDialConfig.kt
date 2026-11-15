package com.mobixournal.desktop.ink

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

/**
 * Clears Windows' built-in radial tools (`Volume`, `Scroll`, `Zoom`, `Undo`) from the window's dial
 * context via `RadialControllerConfiguration::SetDefaultMenuItems` with an empty
 * `IIterable<RadialControllerSystemMenuItemKind>`.
 *
 * Without this step, Windows keeps its default **Volume** tool active for the window's context even
 * when `IRadialControllerMenu::SelectMenuItem` returns `S_OK`, so turning the dial moves the system
 * volume and `RadialController` raises no `RotationChanged` events for the window.
 *
 * Microsoft's contract requires `SetDefaultMenuItems` to be called **after** the app's custom item is
 * appended to `IRadialControllerMenu::Items` (clearing all built-in tools before a custom tool exists
 * restores the default set) and followed by `SelectMenuItem`.
 */
private const val CLASS_CONFIGURATION = "Windows.UI.Input.RadialControllerConfiguration"

/** `IRadialControllerConfigurationInterop`, quoted from `RadialControllerInterop.h`. */
private const val IID_CONFIGURATION_INTEROP = "787CDAAC-3186-476D-87E4-B9374A7B9970"

/** `IRadialControllerConfiguration`, quoted from `windows.ui.input.h`. */
private const val IID_CONFIGURATION = "A6B79ECB-6A52-4430-910C-56370A9D6B42"

/** `IIterable<RadialControllerSystemMenuItemKind>`, quoted from `windows.ui.input.h`. */
internal const val IID_ITERABLE_MENU_KIND = "4516010F-FD98-5E1D-BF3F-AEAF79F1F3DA"

/** `IIterator<RadialControllerSystemMenuItemKind>`, quoted from `windows.ui.input.h`. */
internal const val IID_ITERATOR_MENU_KIND = "3FBC1858-A43E-54DC-B0E2-8B098BDDACF6"

/** `IInspectable`, the root of WinRT runtime classes and generic collection projections. */
internal const val IID_INSPECTABLE = "AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90"

private const val SLOT_GET_FOR_WINDOW = 6 // IRadialControllerConfigurationInterop
private const val SLOT_SET_DEFAULT_MENU_ITEMS = 6 // IRadialControllerConfiguration

private const val HRESULT_BOUNDS = 0x8000000B.toInt()
private const val HRESULT_OUT_OF_MEMORY = 0x8007000E.toInt()

/** `IInspectable::GetIids(self, ULONG *iidCount, IID **iids)`. */
private fun interface ComGetIids : com.sun.jna.win32.StdCallLibrary.StdCallCallback {
    fun callback(self: Pointer, iidCount: Pointer, iids: Pointer): Int
}

/** `IInspectable::GetRuntimeClassName`, `GetTrustLevel`, `IIterable::First`, `IIterator::get_Current`, `get_HasCurrent`, `MoveNext`. */
private fun interface ComOutPointer : com.sun.jna.win32.StdCallLibrary.StdCallCallback {
    fun callback(self: Pointer, out: Pointer): Int
}

/** `IIterator::GetMany(self, UINT capacity, T *items, UINT *actual)`. */
private fun interface ComGetMany : com.sun.jna.win32.StdCallLibrary.StdCallCallback {
    fun callback(self: Pointer, capacity: Int, items: Pointer?, actual: Pointer): Int
}

/**
 * A zero-element `IIterable<RadialControllerSystemMenuItemKind>` and its companion
 * `IIterator<RadialControllerSystemMenuItemKind>`, laid out as native COM vtables.
 *
 * Unlike event delegates, `IIterable<T>` and `IIterator<T>` inherit from `IInspectable` (slots 0–5),
 * so `First` is at slot 6 and `get_Current`, `get_HasCurrent`, `MoveNext`, `GetMany` are at slots 6–9.
 * Both `QueryInterface` tables whitelist only `IUnknown`, `IInspectable`, `IAgileObject`, and their
 * own parameterised interface IID — refusing internal COM probing IIDs (such as
 * `2132B005-C604-4354-85BD-8F2E24181B0C`) with `E_NOINTERFACE` so `twinapi.appcore` never calls a
 * method outside this vtable.
 */
internal class EmptySystemMenuItems(private val log: (String) -> Unit = {}) {

    /** The `IIterable<RadialControllerSystemMenuItemKind>` object pointer passed to `SetDefaultMenuItems`. */
    val iterablePointer: Memory

    /** The `IIterator<RadialControllerSystemMenuItemKind>` object pointer returned by `First()`. */
    val iteratorPointer: Memory

    @Suppress("unused") // Keeps callbacks, vtables, and GUID buffers pinned for the lifetime of this instance.
    private val kept: List<Any>

    init {
        val unknown = comGuid(IID_UNKNOWN)
        val inspectable = comGuid(IID_INSPECTABLE)
        val agile = comGuid(IID_AGILE_OBJECT)
        val iterableGuid = comGuid(IID_ITERABLE_MENU_KIND)
        val iteratorGuid = comGuid(IID_ITERATOR_MENU_KIND)
        val ole32 = runCatching { Native.load("ole32", Ole32Library::class.java) as Ole32Library }.getOrNull()

        val iteratorSelf = Memory(Native.POINTER_SIZE.toLong())
        val iteratorQuery = ComQueryInterface { pointer, iid, out ->
            val ok = iid.isGuid(unknown) || iid.isGuid(inspectable) || iid.isGuid(agile) || iid.isGuid(iteratorGuid)
            if (ok) {
                out.setPointer(0L, pointer)
                HRESULT_OK
            } else {
                out.setPointer(0L, null)
                HRESULT_NO_INTERFACE
            }
        }
        val addRef = ComRefCount { 2 }
        val releaseRef = ComRefCount { 1 }
        val iteratorGetIids = getIidsCallback(ole32, iteratorGuid)
        val runtimeClassName = ComOutPointer { _, out ->
            out.setPointer(0L, null)
            HRESULT_OK
        }
        val trustLevel = ComOutPointer { _, out ->
            out.setInt(0L, 0) // BaseTrust
            HRESULT_OK
        }
        val getCurrent = ComOutPointer { _, out ->
            out.setInt(0L, 0)
            HRESULT_BOUNDS
        }
        val hasCurrent = ComOutPointer { _, out ->
            // WinRT ABI `boolean` is a single byte; `false` is `0`.
            out.setByte(0L, 0)
            HRESULT_OK
        }
        val moveNext = ComOutPointer { _, out ->
            out.setByte(0L, 0)
            HRESULT_OK
        }
        val getMany = ComGetMany { _, _, _, actual ->
            actual.setInt(0L, 0)
            HRESULT_OK
        }
        val iteratorSlots = listOf<Callback>(
            iteratorQuery,
            addRef,
            releaseRef,
            iteratorGetIids,
            runtimeClassName,
            trustLevel,
            getCurrent,
            hasCurrent,
            moveNext,
            getMany,
        )
        val iteratorVtable = buildVtable(iteratorSlots)
        iteratorSelf.setPointer(0L, iteratorVtable)
        iteratorPointer = iteratorSelf

        val iterableSelf = Memory(Native.POINTER_SIZE.toLong())
        val iterableQuery = ComQueryInterface { pointer, iid, out ->
            log("dial: iterable asked for ${iid.guidText()}")
            val ok = iid.isGuid(unknown) || iid.isGuid(inspectable) || iid.isGuid(agile) || iid.isGuid(iterableGuid)
            if (ok) {
                out.setPointer(0L, pointer)
                HRESULT_OK
            } else {
                out.setPointer(0L, null)
                HRESULT_NO_INTERFACE
            }
        }
        val iterableGetIids = getIidsCallback(ole32, iterableGuid)
        val first = ComOutPointer { _, out ->
            out.setPointer(0L, iteratorSelf)
            HRESULT_OK
        }
        val iterableSlots = listOf<Callback>(
            iterableQuery,
            addRef,
            releaseRef,
            iterableGetIids,
            runtimeClassName,
            trustLevel,
            first,
        )
        val iterableVtable = buildVtable(iterableSlots)
        iterableSelf.setPointer(0L, iterableVtable)
        iterablePointer = iterableSelf

        kept = iteratorSlots + iterableSlots + listOf(
            iteratorVtable,
            iterableVtable,
            unknown,
            inspectable,
            agile,
            iterableGuid,
            iteratorGuid,
        )
    }

    private fun getIidsCallback(ole32: Ole32Library?, interfaceGuid: Memory): ComGetIids =
        ComGetIids { _, iidCount, iids ->
            val block = ole32?.CoTaskMemAlloc(16L)
            if (block == null) {
                iidCount.setInt(0L, 0)
                iids.setPointer(0L, null)
                HRESULT_OUT_OF_MEMORY
            } else {
                block.write(0L, interfaceGuid.getByteArray(0L, 16), 0, 16)
                iidCount.setInt(0L, 1)
                iids.setPointer(0L, block)
                HRESULT_OK
            }
        }

    private fun buildVtable(slots: List<Callback>): Memory {
        val vtable = Memory(slots.size.toLong() * Native.POINTER_SIZE)
        for ((index, slot) in slots.withIndex()) {
            vtable.setPointer(index.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(slot))
        }
        return vtable
    }
}

/**
 * Remove Windows' built-in tools (`Volume`, `Scroll`, `Zoom`, `Undo`) from [hwnd]'s radial menu
 * configuration so only this window's custom **MobiXournal** item remains in the context.
 */
internal fun clearDefaultDialMenuItems(
    combase: CombaseLibrary,
    hwnd: Pointer,
    emptyItems: EmptySystemMenuItems,
    log: (String) -> Unit,
): Boolean {
    val interop = TabletDial.activationFactory(combase, CLASS_CONFIGURATION, IID_CONFIGURATION_INTEROP, log)
        ?: return false
    val configRef = PointerByReference()
    val got = call(interop, SLOT_GET_FOR_WINDOW, hwnd, comGuid(IID_CONFIGURATION), configRef)
    release(interop)
    val config = configRef.value
    if (got < 0 || config == null) {
        log("dial: GetForWindow configuration refused (${hresult(got)})")
        release(config)
        return false
    }
    val cleared = call(config, SLOT_SET_DEFAULT_MENU_ITEMS, emptyItems.iterablePointer)
    log("dial: default menu items cleared hr=${hresult(cleared)}")
    release(config)
    return cleared >= 0
}

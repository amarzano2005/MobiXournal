package com.mobixournal.desktop

import com.sun.jna.Function as JnaFunction
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.io.File

/**
 * The **shell's own file dialog**: `IFileOpenDialog` / `IFileSaveDialog`, called through JNA.
 *
 * This is the dialog Windows 11 itself uses — breadcrumbs, a search box, the places list, a real **type
 * box**, and `FOS_PICKFOLDERS` for a folder — none of which `java.awt.FileDialog` reaches, because the JDK
 * still maps that to the pre-Vista `GetOpenFileNameW`. The bindings are deliberately hand-written against
 * COM's vtable rather than another library: the module already ships JNA for the tablet, and the four
 * interfaces involved (`IFileDialog`, `IFileOpenDialog`/`IFileSaveDialog`, `IShellItem`) are a handful of
 * slots each.
 *
 * **Every failure is a `Result.failure`**, never a crash: no COM on this host, a thread in another
 * apartment, a dialog that would not show. [DesktopFilePicker] turns that into Java's dialog, so the window
 * still asks the question. A **cancel** is `success(null)` — an answer that writes nothing.
 *
 * Nothing here is unit-tested: it needs a window and a desktop session to show anything (the pure half —
 * the type rows and the container they name — is `DesktopPickerTypes`, and that *is* tested).
 */
internal object DesktopFilePickerWindows {

    /** Whether this host has the shell's dialog at all; a host that says yes may still fail, and does. */
    val available: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** A file to read. */
    fun open(title: String, types: List<PickerType>, startIn: File?): Result<File?> =
        pick(title, types, startIn, save = false, folder = false, proposed = null, initialType = 0)
            .map { answer -> answer?.let { File(it.path) } }

    /** A destination to write, with the type box that names the container. */
    fun save(
        title: String,
        types: List<PickerType>,
        startIn: File?,
        proposed: String,
        initialType: Int,
    ): Result<SaveAnswer?> =
        pick(title, types, startIn, save = true, folder = false, proposed = proposed, initialType = initialType)
            .map { answer -> answer?.let { SaveAnswer(File(it.path), it.typeIndex) } }

    /** A **folder** to write a set of files into. */
    fun folder(title: String, startIn: File?): Result<File?> =
        pick(title, emptyList(), startIn, save = false, folder = true, proposed = null, initialType = 0)
            .map { answer -> answer?.let { File(it.path) } }

    /** What the shell answered: the path it chose and the row of its type box (0 when it had none). */
    internal class Answer(val path: String, val typeIndex: Int)

    /**
     * Create the shell's dialog, hand it [types] as its type box, read the box back, and release it —
     * **without showing it**.
     *
     * This is the claim a test can check on a real host: that the COM object exists, that the vtable slots
     * this file walks are the ones it names (a wrong index does not fail politely — it calls a different
     * method), and that the `COMDLG_FILTERSPEC` array is readable by the shell. False is either "this host
     * has no shell dialog" or "the shell refused it", which is the same answer [pick] gives.
     */
    internal fun probe(types: List<PickerType>): Boolean = runCatching {
        val dialog = create(save = true) ?: return@runCatching false
        try {
            val specs = filterSpecs(types)
            check(Com.call(dialog, SLOT_SET_FILE_TYPES, types.size, specs.types) >= 0)
            check(Com.call(dialog, SLOT_SET_TITLE, wide("MobiXournal")) >= 0)
            // A round trip, because the box has no index until one is asked for: set the first row and
            // read it back, which is what `configure` does for the document's own container.
            check(Com.call(dialog, SLOT_SET_FILE_TYPE_INDEX, 1) >= 0)
            val index = Memory(4L)
            check(Com.call(dialog, SLOT_GET_FILE_TYPE_INDEX, index) >= 0)
            index.getInt(0L) == 1
        } finally {
            Com.release(dialog)
        }
    }.getOrDefault(false)

    /** One dialog's whole life: create it, say what it is for, show it, read it, release it. */
    private fun pick(
        title: String,
        types: List<PickerType>,
        startIn: File?,
        save: Boolean,
        folder: Boolean,
        proposed: String?,
        initialType: Int,
    ): Result<Answer?> = runCatching {
        val dialog = create(save) ?: error("the shell's file dialog could not be created")
        try {
            configure(dialog, title, types, startIn, save, folder, proposed, initialType)
            show(dialog, save, folder)
        } finally {
            Com.release(dialog)
        }
    }

    /** A new dialog, or null when this thread cannot host one (another apartment, no COM). */
    private fun create(save: Boolean): Pointer? {
        if (Com.ole32.CoInitializeEx(null, COINIT_APARTMENTTHREADED) < 0) return null
        val result = Memory(Native.POINTER_SIZE.toLong())
        val hr = Com.ole32.CoCreateInstance(
            (if (save) CLSID_FILE_SAVE_DIALOG else CLSID_FILE_OPEN_DIALOG).pointer,
            null,
            CLSCTX_INPROC_SERVER,
            (if (save) IID_FILE_SAVE_DIALOG else IID_FILE_OPEN_DIALOG).pointer,
            result,
        )
        if (hr < 0) return null
        return result.getPointer(0L)
    }

    /**
     * Say what the dialog is for: its title, the type box, the flags that keep it on real files, the name it
     * proposes and the folder it opens in.
     *
     * The type box is **strict**: Save As writes the container that box names, so a shell that refused the
     * rows must not go on to show a dialog with no box at all — that would silently write `.mobix`
     * whatever the document was. The other calls are best-effort (a start folder that cannot be resolved
     * simply means the dialog opens where it usually does).
     */
    private fun configure(
        dialog: Pointer,
        title: String,
        types: List<PickerType>,
        startIn: File?,
        save: Boolean,
        folder: Boolean,
        proposed: String?,
        initialType: Int,
    ) {
        Com.call(dialog, SLOT_SET_TITLE, wide(title))
        if (!folder && types.isNotEmpty()) {
            // Kept alive for the calls that read it: the shell copies the specs, but only during the call.
            val specs = filterSpecs(types)
            check(Com.call(dialog, SLOT_SET_FILE_TYPES, types.size, specs.types) >= 0) {
                "the shell refused the type box"
            }
            Com.call(dialog, SLOT_SET_FILE_TYPE_INDEX, initialType.coerceIn(0, types.lastIndex) + 1)
            if (save) Com.call(dialog, SLOT_SET_DEFAULT_EXTENSION, wide(types.first().defaultExtension))
        }
        options(dialog, save, folder)
        if (proposed != null) Com.call(dialog, SLOT_SET_FILE_NAME, wide(proposed))
        if (startIn != null) setStartFolder(dialog, startIn)
    }

    /** The flags the dialog keeps while it is open: real files only, and what its mode adds. */
    private fun options(dialog: Pointer, save: Boolean, folder: Boolean) {
        val flags = Memory(4L)
        if (Com.call(dialog, SLOT_GET_OPTIONS, flags) < 0) return
        var value = flags.getInt(0L)
        value = value or FOS_FORCEFILESYSTEM or FOS_NOCHANGEDIR or FOS_PATHMUSTEXIST
        value = if (save) value or FOS_OVERWRITEPROMPT else value or FOS_FILEMUSTEXIST
        if (folder) value = value or FOS_PICKFOLDERS
        Com.call(dialog, SLOT_SET_OPTIONS, value)
    }

    /** Open on [folder] — the document's own, so the common answer ("the same folder") costs nothing. */
    private fun setStartFolder(dialog: Pointer, folder: File) {
        if (!folder.isDirectory) return
        val result = Memory(Native.POINTER_SIZE.toLong())
        val hr = Com.shell32.SHCreateItemFromParsingName(
            wide(folder.absolutePath),
            null,
            IID_SHELL_ITEM.pointer,
            result,
        )
        if (hr < 0) return
        val item = result.getPointer(0L) ?: return
        try {
            Com.call(dialog, SLOT_SET_FOLDER, item)
        } finally {
            Com.release(item)
        }
    }

    /** Show it and read the answer: null is a cancel; a dialog that would not show is a failure. */
    private fun show(dialog: Pointer, save: Boolean, folder: Boolean): Answer? {
        val owner = runCatching { Com.user32.GetActiveWindow() }.getOrNull() ?: Pointer.NULL
        val hr = Com.call(dialog, SLOT_SHOW, owner)
        if (hr == ERROR_CANCELLED) return null
        check(hr >= 0) { "the shell's dialog did not show (0x${Integer.toHexString(hr)})" }
        val path = chosenPath(dialog) ?: return null
        return Answer(path, if (save && !folder) typeBoxIndex(dialog) else 0)
    }

    /** The path the reader chose, through `IShellItem::GetDisplayName` (the one `SIGDN_FILESYSPATH` form). */
    private fun chosenPath(dialog: Pointer): String? {
        val result = Memory(Native.POINTER_SIZE.toLong())
        if (Com.call(dialog, SLOT_GET_RESULT, result) < 0) return null
        val item = result.getPointer(0L) ?: return null
        try {
            val text = Memory(Native.POINTER_SIZE.toLong())
            if (Com.call(item, SLOT_GET_DISPLAY_NAME, SIGDN_FILESYSPATH, text) < 0) return null
            val wide = text.getPointer(0L) ?: return null
            try {
                return wide.getWideString(0L)
            } finally {
                Com.ole32.CoTaskMemFree(wide)
            }
        } finally {
            Com.release(item)
        }
    }

    /** Which row of the type box the reader left picked, zero-based (`GetFileTypeIndex` counts from one). */
    private fun typeBoxIndex(dialog: Pointer): Int {
        val index = Memory(4L)
        if (Com.call(dialog, SLOT_GET_FILE_TYPE_INDEX, index) < 0) return 0
        return (index.getInt(0L) - 1).coerceAtLeast(0)
    }
}

/**
 * The COM calls this file needs, and the vtable walk that reaches the dialog's own methods.
 *
 * Slots are `IUnknown`'s three (`QueryInterface`, `AddRef`, `Release`) followed by each interface's methods
 * in declaration order, which is why the numbers below are the interface **layout**, not a choice.
 */
private object Com {

    val ole32: Ole32 by lazy { Native.load("ole32", Ole32::class.java) }

    val shell32: Shell32 by lazy { Native.load("shell32", Shell32::class.java) }

    val user32: User32 by lazy { Native.load("user32", User32::class.java) }

    /**
     * Slot [slot] of [surface]'s vtable; a HRESULT (or `Release`'s count, which this file ignores).
     *
     * The interface pointer is the call's **first argument** — a COM method is `this` first, then its own
     * parameters, which is the one thing a hand-written vtable call has to remember and the reason the
     * probe in `DesktopFilePickerTest` crashed until it did. `C_CONVENTION` is the Microsoft x64 ABI, the
     * one COM uses on the 64-bit runtime the packaged window ships.
     */
    fun call(surface: Pointer, slot: Int, vararg args: Any?): Int {
        val vtable = surface.getPointer(0L) ?: error("a COM surface with no vtable")
        val method = vtable.getPointer(slot.toLong() * Native.POINTER_SIZE)
            ?: error("slot $slot is not on this interface")
        val call = arrayOfNulls<Any?>(args.size + 1)
        call[0] = surface
        args.copyInto(call, destinationOffset = 1)
        return JnaFunction.getFunction(method, JnaFunction.C_CONVENTION).invokeInt(call)
    }

    fun release(surface: Pointer) {
        call(surface, SLOT_RELEASE)
    }
}

/** `ole32`: the apartment, the dialog object, and the memory the shell hands back. */
internal interface Ole32 : StdCallLibrary {
    fun CoInitializeEx(reserved: Pointer?, mode: Int): Int

    fun CoCreateInstance(clsid: Pointer, outer: Pointer?, context: Int, iid: Pointer, result: Pointer): Int

    fun CoTaskMemFree(memory: Pointer?)
}

/** `shell32`: a path as an `IShellItem`, which is what `SetFolder` takes. */
internal interface Shell32 : StdCallLibrary {
    fun SHCreateItemFromParsingName(path: Pointer, bind: Pointer?, iid: Pointer, result: Pointer): Int
}

/** `user32`: the window the dialog is owned by. */
internal interface User32 : StdCallLibrary {
    fun GetActiveWindow(): Pointer?
}

/** A COM GUID — the four fields `CoCreateInstance` needs, in the layout `REFCLSID`/`REFIID` name. */
@Structure.FieldOrder("data1", "data2", "data3", "data4")
internal class Guid(
    @JvmField var data1: Int = 0,
    @JvmField var data2: Short = 0,
    @JvmField var data3: Short = 0,
    @JvmField var data4: ByteArray = ByteArray(8),
) : Structure() {

    init {
        write()
    }

    companion object {
        /** `DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7` as a [Guid]. */
        fun of(text: String): Guid {
            val parts = text.split("-")
            require(parts.size == 5) { "a GUID has five groups: $text" }
            val tail = parts[3] + parts[4]
            return Guid(
                data1 = parts[0].toLong(16).toInt(),
                data2 = parts[1].toLong(16).toShort(),
                data3 = parts[2].toLong(16).toShort(),
                data4 = ByteArray(8) { at -> tail.substring(at * 2, at * 2 + 2).toInt(16).toByte() },
            )
        }
    }
}

/** The type rows as the shell's `COMDLG_FILTERSPEC` array, and the strings it points at. */
private class FilterSpecs(val types: Memory, val strings: List<Memory>)

/** `label\0` and `*.mobix;*.xopp\0` as a `wchar_t*` the shell can read. */
private fun wide(text: String): Memory = Memory((text.length + 1).toLong() * 2).apply {
    setWideString(0L, text)
}

private fun filterSpecs(rows: List<PickerType>): FilterSpecs {
    val pointer = Native.POINTER_SIZE.toLong()
    val types = Memory(pointer * 2 * rows.size)
    val strings = mutableListOf<Memory>()
    rows.forEachIndexed { index, row ->
        val label = wide(row.label).also(strings::add)
        val spec = wide(row.extensions.joinToString(";") { filterOf(it) }).also(strings::add)
        types.setPointer(index * 2 * pointer, label)
        types.setPointer(index * 2 * pointer + pointer, spec)
    }
    return FilterSpecs(types, strings)
}

// --- the interfaces, by vtable slot ----------------------------------------------------------------
private const val SLOT_RELEASE = 2
private const val SLOT_SHOW = 3
private const val SLOT_SET_FILE_TYPES = 4
private const val SLOT_SET_FILE_TYPE_INDEX = 5
private const val SLOT_GET_FILE_TYPE_INDEX = 6
private const val SLOT_SET_OPTIONS = 9
private const val SLOT_GET_OPTIONS = 10
private const val SLOT_SET_FOLDER = 12
private const val SLOT_SET_FILE_NAME = 15
private const val SLOT_SET_TITLE = 17
private const val SLOT_GET_RESULT = 20
private const val SLOT_SET_DEFAULT_EXTENSION = 22

/** `IShellItem::GetDisplayName` — the same three `IUnknown` slots, then `BindToHandler`, `GetParent`, this. */
private const val SLOT_GET_DISPLAY_NAME = 5

// --- what the calls take ----------------------------------------------------------------------------
private const val CLSCTX_INPROC_SERVER = 0x1
private const val COINIT_APARTMENTTHREADED = 0x2
private const val FOS_OVERWRITEPROMPT = 0x2
private const val FOS_NOCHANGEDIR = 0x8
private const val FOS_PICKFOLDERS = 0x20
private const val FOS_FORCEFILESYSTEM = 0x40
private const val FOS_PATHMUSTEXIST = 0x800
private const val FOS_FILEMUSTEXIST = 0x1000

/** `SIGDN_FILESYSPATH` and `HRESULT_FROM_WIN32(ERROR_CANCELLED)`, as the signed Ints COM answers with. */
private val SIGDN_FILESYSPATH = 0x80058000.toInt()
private val ERROR_CANCELLED = 0x800704C7.toInt()

private val CLSID_FILE_OPEN_DIALOG = Guid.of("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7")
private val CLSID_FILE_SAVE_DIALOG = Guid.of("C0B4E2F3-BA21-4773-8DBA-335EC946EB8B")
private val IID_FILE_OPEN_DIALOG = Guid.of("D57C7288-D4AD-4768-BE02-9D969532D960")
private val IID_FILE_SAVE_DIALOG = Guid.of("84BCCD23-5FDE-4CDB-AEA4-AF64B83D78AB")
private val IID_SHELL_ITEM = Guid.of("43826D1E-E718-42EE-BC55-A1E261C37BFE")

package com.mobixournal.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.io.File
import java.util.Locale

/**
 * **The command line Windows wrote**, which `args` is only a damaged copy of.
 *
 * Double-clicking a notebook appends its path to the executable's line. When that path is not quoted,
 * Windows' argv splitter breaks it at every run of spaces — and **squeezes the run into one**, so a
 * folder named `09)  21-10-2026` reaches `main` as `09) 21-10-2026` and the launch names a file that
 * does not exist. Rebuilding the pieces cannot give the second space back: it was lost before the app
 * ever ran.
 *
 * The line itself is still intact, so the repair reads it from the only place that has it —
 * `kernel32!GetCommandLineW`, the wide command line Windows handed the process. No native call and no
 * recovery is needed when the path arrives whole (the ordinary case); this runs only after the
 * arguments have already failed to name a real file.
 */
private interface Kernel32 : StdCallLibrary {
    /** `LPCWSTR GetCommandLineW()` — the wide line, read as a pointer so JNA does not transcode it. */
    fun GetCommandLineW(): Pointer
}

/** The line as Windows wrote it, or null when this host cannot say (a non-Windows build, a failed load). */
internal fun rawCommandLine(): String? {
    val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    if (!os.contains("windows")) return null
    val library = runCatching { Native.load("kernel32", Kernel32::class.java) as Kernel32 }.getOrNull()
        ?: return null
    val pointer = runCatching { library.GetCommandLineW() }.getOrNull() ?: return null
    // `getWideString` is the UTF-16 read: an emoji folder name survives, where argv's own decoding or
    // a console of another code page would have replaced it.
    return runCatching { pointer.getWideString(0) }.getOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * The document named at the **end** of [raw], or null when no tail of it is a file.
 *
 * The scan runs backwards, from the last token to the first, and takes the first tail that exists on
 * disk: the document is the argument at the end of the line, so starting there is what keeps the JVM's
 * own switches (`-cp`, a jar, a main class) out of the answer — the app's classpath is full of files and
 * only the last argument is the notebook. A tail that starts inside the path (at a space in a folder or
 * file name) is not a path on its own, so it does not resolve and the scan steps back to the real start.
 *
 * Quoted tails are unquoted before the test, so both association styles answer the same: a line that is
 * `"…\\MobiXournal.exe" "C:\\…\\x.mobix"` and one that is `…\\MobiXournal.exe C:\\…\\x.mobix`.
 */
internal fun documentFromCommandLine(raw: String?): File? {
    if (raw.isNullOrBlank()) return null
    val starts = buildList {
        add(0)
        for (i in 1 until raw.length) if (raw[i - 1] == ' ' && raw[i] != ' ') add(i)
    }
    for (start in starts.asReversed()) {
        val candidate = raw.substring(start).trim().trim('"')
        if (candidate.isEmpty()) continue
        val file = File(candidate)
        if (file.isFile) return file
    }
    return null
}

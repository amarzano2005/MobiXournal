package com.mobixournal.desktop

import com.mobixournal.audio.documentAudioFiles
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.audioRef
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Where the window keeps recordings: **one bare file name each**, in `%APPDATA%\MobiXournal\audio`
 * beside `desktop.properties` (`docs/audio.md`).
 *
 * A store rather than "beside the document" because the document moves and the recording is referenced
 * by name alone (`fn`, exactly what desktop Xournal++ writes): the sidecar has to be somewhere a saver
 * can find it after the `.xopp` was opened from a USB stick and saved to the desktop. The folder is the
 * window's own, and every trip is explicit:
 *
 * - **[adoptBundled]** — a `.mobix` carries its recordings inside `assets/`, and this copies each one out
 *   under the name the strokes call it by. This is what was missing: the window used to rewrite the
 *   archive without those entries, so editing a document on the PC **deleted its audio**.
 * - **[importSidecars]** / **[exportSidecars]** — the `.xopp` half of the same rule, in and out of the
 *   document's folder.
 *
 * Names are treated as untrusted: only a bare file name is accepted, so an archive entry called
 * `../../evil.ogg` cannot write outside the store.
 */
class DesktopAudioStore(
    val folder: File = defaultFolder(),
) {

    /** The recording [name] refers to, or null when this machine does not hold it (or the name is unsafe). */
    fun file(name: String): File? = safeName(name)?.let { File(folder, it) }?.takeIf { it.isFile }

    /** The path [name] would be written to, with the store's folder created. */
    fun target(name: String): File {
        val safe = safeName(name) ?: throw IllegalArgumentException("That is not a recording file name: $name")
        folder.mkdirs()
        return File(folder, safe)
    }

    /** Copy [source] into the store as [name]; the bytes are streamed, never held whole. */
    fun adopt(name: String, source: InputStream): File? = runCatching {
        val target = target(name)
        source.use { bytes -> target.outputStream().use { out -> bytes.copyTo(out) } }
        target
    }.getOrNull()

    /**
     * Copy every recording [document]'s strokes name **out of the archive they came from**.
     *
     * A stroke names its payload twice — `bundledAudio` is the `assets/` entry, `fn` is the name
     * playback asks the store for — and both are answers to a question the file already holds, so no
     * sidecar has to sit beside a `.mobix`. An entry that is already in the store at the same length is
     * left alone: adopting is idempotent, which is what makes it safe to run on every open.
     */
    fun adoptBundled(archive: File, document: Document): Int {
        if (!archive.isFile) return 0
        val wanted = bundledRecordings(document)
        if (wanted.isEmpty()) return 0
        var adopted = 0
        runCatching {
            ZipFile(archive).use { zip ->
                for ((entryName, fileName) in wanted) {
                    val target = File(folder, fileName)
                    val entry = zip.getEntry(entryName) ?: continue
                    if (target.isFile && target.length() == entry.size) continue
                    zip.getInputStream(entry).use { bytes ->
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> bytes.copyTo(out) }
                    }
                    adopted++
                }
            }
        }.onFailure { return 0 }
        return adopted
    }

    /** Pull the `.xopp` sidecars [document] names out of [documentFolder] into the store. */
    fun importSidecars(documentFolder: File?, document: Document): Int =
        copyAll(documentAudioFiles(document)) { name ->
            documentFolder?.let { File(it, name) to folder }
        }

    /** Push every recording [document] names back out beside a `.xopp` saved as [target]. */
    fun exportSidecars(target: File, document: Document): Int {
        val folder = target.absoluteFile.parentFile ?: return 0
        return copyAll(documentAudioFiles(document)) { name -> file(name) to folder }
    }

    /**
     * Copy every recording [document] names into [folder] — the tablet's **Save separate audio**: the
     * takes leave the window's own store so another program (or another machine) can play them.
     *
     * The same copier as a `.xopp` save, so a file already there at the same length is left alone rather
     * than rewritten; the answer is how many were actually copied, which is what the capsule reports.
     */
    fun exportToFolder(folder: File, document: Document): Int {
        if (!folder.isDirectory && !folder.mkdirs()) return 0
        return copyAll(documentAudioFiles(document)) { name -> file(name) to folder }
    }

    /** The names [document] references that this store does not hold — what an open has to report. */
    fun missing(document: Document): List<String> = documentAudioFiles(document).filter { file(it) == null }

    /**
     * Copy each of [names] from its source to its destination, skipping anything already there at the
     * same length. Both directions share this so a sidecar cannot be newer on one side by accident.
     */
    private fun copyAll(names: Set<String>, destination: (String) -> Pair<File?, File>?): Int {
        var copied = 0
        for (name in names) {
            val safe = safeName(name) ?: continue
            val (source, folder) = destination(safe) ?: continue
            if (source == null || !source.isFile) continue
            val target = File(folder, safe)
            if (target.isFile && target.length() == source.length()) continue
            runCatching {
                target.parentFile?.mkdirs()
                source.inputStream().use { bytes -> target.outputStream().use { out -> bytes.copyTo(out) } }
                copied++
            }
        }
        return copied
    }

    /** The `assets/` entry each stroke's recording lives in, paired with the name playback looks it up by. */
    private fun bundledRecordings(document: Document): Map<String, String> {
        val found = LinkedHashMap<String, String>()
        document.pages.forEach { page ->
            page.layers.forEach { layer ->
                layer.elements.filterIsInstance<Stroke>().forEach { stroke ->
                    // Two shapes name the same entry: the model's own `bundledAudio`, and the older one
                    // where `fn` itself was the `assets/` path (`carriedPayloads` reads both too).
                    val entry = stroke.bundledAudio
                        ?: stroke.audioRef()?.filename?.takeIf { it.startsWith("assets/") }
                        ?: return@forEach
                    val raw = stroke.audioRef()?.filename ?: entry.substringAfterLast('/')
                    val name = safeName(bareName(raw)) ?: return@forEach
                    found.putIfAbsent(entry, name)
                }
            }
        }
        return found
    }

    companion object {

        /** The last segment of a path-like name — what a recording is actually called. */
        fun bareName(name: String): String = name.trim().substringAfterLast('/').substringAfterLast('\\')

        /** A bare file name, or null: no directories, no drive letters, no escaping the store. */
        fun safeName(name: String): String? {
            val bare = name.trim()
            if (bare.isEmpty() || bare == "." || bare == "..") return null
            if (bare.contains('/') || bare.contains('\\') || bare.contains(':')) return null
            return bare
        }

        /** `%APPDATA%\MobiXournal\audio`, the same home as the settings file (`DesktopSettings.defaultFile`). */
        fun defaultFolder(): File {
            val settings = DesktopSettings.defaultFile().absoluteFile.parentFile
            return File(settings, "audio")
        }

        /**
         * The window's one store. A single instance because the header's pill, the recorder and a save
         * must all be talking about the same folder — a second store is a second answer to "where is
         * that recording".
         */
        val shared: DesktopAudioStore by lazy { DesktopAudioStore() }
    }
}

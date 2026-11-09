package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.find

import com.mobixournal.audio.documentAudioFiles
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.audioRef
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Document-local bindings from bare recording names to immutable payload copies under `%APPDATA%\MobiXournal\audio`
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

    private val bindings = java.util.concurrent.ConcurrentHashMap<String, File>()

    /** The recording [name] refers to, or null when this machine does not hold it (or the name is unsafe). */
    fun file(name: String): File? = safeName(name)?.let(bindings::get)?.takeIf { it.isFile }

    /** The path [name] would be written to, with the store's folder created. */
    fun target(name: String): File {
        val safe = safeName(name) ?: throw IllegalArgumentException("That is not a recording file name: $name")
        folder.mkdirs()
        return File(folder, "${java.util.UUID.randomUUID()}-$safe").also { bindings[safe] = it }
    }

    /** Copy [source] into the store as [name]; the bytes are streamed, never held whole. */
    fun adopt(name: String, source: InputStream): File? = runCatching {
        val safe = safeName(name) ?: throw IllegalArgumentException("That is not a recording file name: $name")
        folder.mkdirs()
        val staged = File.createTempFile("audio-", ".writing", folder)
        try {
            source.use { input -> staged.outputStream().use { input.copyTo(it) } }
            val digest = com.mobixournal.format.mobix.MobixPayload.of(staged).sha256
            val target = File(folder, "$digest-$safe")
            if (!target.isFile) java.nio.file.Files.move(staged.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            bindings[safe] = target
            target
        } finally { staged.delete() }
    }.getOrNull()

    /**
     * Copy every recording [document]'s strokes name **out of the archive they came from**.
     *
     * A stroke names its payload twice — `bundledAudio` is the `assets/` entry, `fn` is the name
     * playback asks the store for — and both are answers to a question the file already holds, so no
     * sidecar has to sit beside a `.mobix`. An entry already adopted with the same digest is
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
                    val entry = zip.getEntry(entryName) ?: continue
                    val existing = file(fileName)
                    if (existing != null && existing.length() == entry.size &&
                        com.mobixournal.format.mobix.MobixPayload.of(existing).sha256 ==
                        com.mobixournal.format.mobix.MobixPayload.ofArchiveEntry(archive, entryName, entry.size).sha256
                    ) continue
                    val copied = adopt(fileName, zip.getInputStream(entry))
                    if (copied == null) continue
                    adopted++
                }
            }
        }.onFailure { return 0 }
        return adopted
    }

    /** Pull the `.xopp` sidecars [document] names out of [documentFolder] into the store. */
    fun importSidecars(documentFolder: File?, document: Document): Int {
        if (documentFolder == null) return 0
        return documentAudioFiles(document).count { name ->
            val safe = safeName(name) ?: return@count false
            val source = File(documentFolder, safe)
            if (!source.isFile || file(safe)?.let { sameContent(source, it) } == true) return@count false
            adopt(safe, source.inputStream()) != null
        }
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
     * The same copier as a `.xopp` save, so a file already there with identical bytes is left alone rather
     * than rewritten; the answer is how many were actually copied, which is what the capsule reports.
     */
    fun exportToFolder(folder: File, document: Document): Int {
        if (!folder.isDirectory && !folder.mkdirs()) return 0
        return copyAll(documentAudioFiles(document)) { name -> file(name) to folder }
    }

    /** The names [document] references that this store does not hold — what an open has to report. */
    fun missing(document: Document): List<String> = documentAudioFiles(document).filter { file(it) == null }

    /**
     * Copy each of [names] from its source to its destination, skipping byte-identical files already there. Both directions share this so a sidecar cannot be newer on one side by accident.
     */
    private fun copyAll(names: Set<String>, destination: (String) -> Pair<File?, File>?): Int {
        var copied = 0
        for (name in names) {
            val safe = safeName(name) ?: continue
            val (source, folder) = destination(safe) ?: continue
            if (source == null || !source.isFile) continue
            val target = File(folder, safe)
            if (target.absoluteFile == source.absoluteFile || sameContent(source, target)) continue
            runCatching {
                target.parentFile?.mkdirs()
                source.inputStream().use { bytes -> target.outputStream().use { out -> bytes.copyTo(out) } }
                copied++
            }
        }
        return copied
    }

    private fun sameContent(a: File, b: File): Boolean {
        if (!b.isFile || a.length() != b.length()) return false
        return com.mobixournal.format.mobix.MobixPayload.of(a).sha256 ==
            com.mobixournal.format.mobix.MobixPayload.of(b).sha256
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

        /** A stable private namespace: identical sidecar names in different notebooks never alias. */
        fun forDocument(origin: File): DesktopAudioStore {
            val key = java.security.MessageDigest.getInstance("SHA-256")
                .digest(com.mobixournal.desktop.shell.DesktopTabs.keyOf(origin).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return DesktopAudioStore(File(defaultFolder(), key))
        }

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

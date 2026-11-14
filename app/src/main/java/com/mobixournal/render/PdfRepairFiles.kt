package com.mobixournal.render

import android.content.res.AssetManager
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.security.MessageDigest

/**
 * **The file the tablet's renderer opens**, repaired when the file lost its font programs.
 *
 * The framework `PdfRenderer` can only be handed a file, and it cannot be told about a font — so the repair
 * of a damaged PDF ([PdfFontRepair]) has to happen before the renderer sees it, and it has to happen on a
 * copy: the document's own PDF bytes are the user's file and stay untouched, exactly as on the desktop, where
 * the repair lives in a rasteriser's private in-memory document. The copy lands in the app's cache, named
 * after the source's path and bytes, and the renderer opens that.
 *
 * A file whose fonts are all there answers itself, at the cost of one PDFBox load per distinct file (cached
 * for the life of the process by path, size and timestamp, so scrolling never pays it twice). An unreadable
 * or unsaveable file also answers itself: a document that renders with substituted letters is better than one
 * that does not open. [configure] runs at startup, before any renderer exists; it also clears copies left by a
 * previous run.
 */
object PdfRepairFiles {

    private val cache = HashMap<String, File>()
    private val lock = Any()

    private var assets: AssetManager? = null
    private var directory: File? = null

    /** Point the repair at the app's assets and its cache; called once, from the activity's startup. */
    fun configure(assets: AssetManager, directory: File) {
        this.assets = assets
        this.directory = directory
        synchronized(lock) {
            cache.clear()
            directory.listFiles()?.forEach { runCatching { it.delete() } }
        }
    }

    /**
     * The file to render [source] from: [source] itself when it names no missing font program, otherwise a
     * repaired copy in the cache.
     */
    fun renderable(source: File): File {
        val assets = assets ?: return source
        val directory = directory ?: return source
        if (!source.isFile) return source
        val key = keyOf(source)
        synchronized(lock) { cache[key]?.let { return it } }
        val renderable = runCatching { repaired(source, key, assets, directory) }.getOrNull() ?: source
        synchronized(lock) { cache[key] = renderable }
        return renderable
    }

    private fun repaired(source: File, key: String, assets: AssetManager, directory: File): File? {
        PDDocument.load(source).use { document ->
            val repaired = document.pages.sumOf { page -> PdfFontRepair.repair(document, page) { assets.program(it) } }
            if (repaired == 0 || document.numberOfPages == 0) return null
            directory.mkdirs()
            val target = File(directory, "$key.pdf")
            val writing = File(directory, "$key.part")
            document.save(writing)
            // A reader must never see a half-written copy: the rename is the publish.
            if (!writing.renameTo(target)) {
                writing.delete()
                return null
            }
            return target
        }
    }

    private fun AssetManager.program(face: PdfBundledFace): ByteArray =
        open(face.assetPath).use { it.readBytes() }

    /** Path, size and timestamp: the same identity the renderer's own source check uses. */
    private fun keyOf(source: File): String {
        val text = "${source.absolutePath}|${source.length()}|${source.lastModified()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}

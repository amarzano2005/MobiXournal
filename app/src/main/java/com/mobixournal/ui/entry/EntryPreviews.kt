package com.mobixournal.ui.entry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mobixournal.format.model.Page
import com.mobixournal.render.PageThumbnail
import com.mobixournal.render.blankDocument
import java.io.File
import java.security.MessageDigest

/**
 * The entry screen's faces: the sheet a new document will be, and a notebook's first page.
 *
 * A **card is a picture**, and the screen must not parse a document to draw one — a `.mobix` read is
 * a container walk and a render, and six of them on the frame the screen appears in is a stall (the
 * desktop learned the same lesson and paints its previews off the UI thread). On the tablet the
 * picture is therefore **written when the document is opened**, from the pages already in memory
 * ([save]), and read back here as a PNG. A notebook whose picture was never taken still gets a card:
 * it shows the empty sheet until one exists, exactly as a desktop card does before its paint lands.
 *
 * The files sit in the app's own directory, keyed by the document's URI, and are swept when the
 * document leaves the recents list ([forget]).
 */
object EntryPreviews {

    /** Wide enough for a card at tablet size, inside `PageThumbnail`'s own ceiling of 400 px. */
    private const val WIDTH_PX = 320

    private const val DIR = "entry-previews"

    /** The blank sheet the New card shows: one page of the default paper, ruled as a new sheet is. */
    fun blankSheet(): Bitmap? = runCatching {
        PageThumbnail.render(blankDocument().pages.first(), WIDTH_PX)
    }.getOrNull()

    /**
     * Write [page] as [uri]'s face. Called by whoever holds a freshly read document, on a worker: a
     * page render is milliseconds but a document can be thousands of strokes.
     */
    fun save(context: Context, uri: String, page: Page): Boolean = runCatching {
        val bitmap = PageThumbnail.render(page, WIDTH_PX) ?: return false
        val target = fileOf(context, uri)
        target.parentFile?.mkdirs()
        target.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        true
    }.getOrDefault(false)

    /** [uri]'s face, or null when none was ever written or the file has gone. */
    fun load(context: Context, uri: String): Bitmap? = runCatching {
        val file = fileOf(context, uri)
        if (!file.exists()) null else BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()

    /** Drop [uri]'s face, so a document that left the list does not leave a picture behind. */
    fun forget(context: Context, uri: String) {
        runCatching { fileOf(context, uri).delete() }
    }

    private fun fileOf(context: Context, uri: String) =
        File(File(context.filesDir, DIR).apply { mkdirs() }, keyOf(uri) + ".png")

    /** A file name for [uri]: its SHA-256, because a URI carries slashes and colons a file may not. */
    private fun keyOf(uri: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
}

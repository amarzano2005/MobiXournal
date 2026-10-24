package com.mobixournal.render

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.DragEvent
import androidx.core.content.FileProvider
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.File
import kotlin.math.ceil

/**
 * A selection leaves this canvas in **four flavours at once**, and content dropped on the page comes
 * back as a living element:
 *
 * - `application/vnd.mobixournal.element` — the elements themselves ([ElementTransfer]), which is how
 *   a copy pasted into another MobiXournal view arrives with its geometry, living entity and formula
 *   source intact;
 * - `image/svg+xml` — a vector picture of the selection, so a design tool receives editable paths;
 * - `image/png` — a rendered bitmap in the cache, handed out through the app's `FileProvider`;
 * - `text/plain` — a readable summary/LaTeX source, the fallback every application understands.
 *
 * **Incoming** drops accept those same flavours (plus an image, PDF or text file URI): text that
 * looks like CSV/TSV becomes a living table, a LaTeX-ish line a math block, prose a text box
 * ([ExternalIngest]); an image lands as an [com.mobixournal.format.model.ImageElement]; a PDF is handed
 * to the host, which knows where to append its pages.
 *
 * Android's clipboard has one type per clip in practice, so the four are declared in the
 * [ClipDescription] and carried as items; a receiver reads the one it understands. The PNG is only
 * published when a bitmap could be rendered (a real device), and its absence never fails a copy.
 */
internal fun DrawingSurfaceView.publishSelectionToSystemClipboard(elements: List<Element>) {
    val clip = selectionClipData(elements) ?: return
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(clip)
}

/**
 * The selection as one [ClipData] carrying every flavour it can. Shared by Copy and by the
 * long-press drag, so what leaves the canvas is the same whichever way it leaves.
 */
internal fun DrawingSurfaceView.selectionClipData(elements: List<Element>): ClipData? {
    if (elements.isEmpty()) return null
    val payload = ElementTransfer.encode(elements)
    val text = ElementTransfer.plainText(elements)
    val svg = selectionSvg(elements)
    val png = selectionPngUri(elements)
    val types = buildList {
        add(ElementTransfer.MIME_TYPE)
        if (svg != null) add("image/svg+xml")
        if (png != null) add("image/png")
        add("text/plain")
    }.toTypedArray()
    val clip = ClipData(ClipDescription("MobiXournal selection", types), ClipData.Item(payload))
    svg?.let { clip.addItem(ClipData.Item(it)) }
    png?.let { clip.addItem(ClipData.Item(it)) }
    clip.addItem(ClipData.Item(text))
    return clip
}

/**
 * The elements a system clipboard holds in this app's own flavour, or an empty list. The payload is
 * recognised by trying [ElementTransfer.decode] on each text item: that is stricter than trusting a
 * MIME label another app may have copied.
 */
internal fun systemClipboardElements(context: Context): List<Element> {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return emptyList()
    val clip = manager.primaryClip ?: return emptyList()
    for (i in 0 until clip.itemCount) {
        val text = clip.getItemAt(i).text?.toString() ?: continue
        val elements = ElementTransfer.decode(text)
        if (elements.isNotEmpty()) return elements
    }
    return emptyList()
}

/** The primary clip's plain text, when it has one — the seed for an external-content drop. */
internal fun systemClipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    for (i in 0 until clip.itemCount) clip.getItemAt(i).text?.let { return it.toString() }
    return null
}

/** The selection as a standalone SVG page: translated so its own box starts at a small margin. */
private fun DrawingSurfaceView.selectionSvg(elements: List<Element>): String? {
    val page = selectionPage(elements) ?: return null
    return runCatching { PageSvgWriter.write(page) }.getOrNull()
}

/** The selection rendered to a PNG in the cache and handed out through the app's FileProvider. */
private fun DrawingSurfaceView.selectionPngUri(elements: List<Element>): Uri? = runCatching {
    val page = selectionPage(elements) ?: return@runCatching null
    val scale = 2f
    val bmp = Bitmap.createBitmap(
        ceil(page.width * scale).toInt().coerceAtLeast(1),
        ceil(page.height * scale).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    Canvas(bmp).apply { drawColor(Color.WHITE) }
    PageRenderer.drawElements(Canvas(bmp), page, scale, 0f, 0f, StrokePainter(), ElementRenderer())
    val dir = File(context.cacheDir, "clipboard").apply { mkdirs() }
    val file = File(dir, "selection-${System.currentTimeMillis()}.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

/** A synthetic page holding only [elements], moved into a box with a margin. */
private fun DrawingSurfaceView.selectionPage(elements: List<Element>): Page? {
    val bounds = SelectionOps.boundsOf(elements) ?: return null
    val margin = 8.0
    val moved = elements.map { SelectionOps.translate(it, margin - bounds.left, margin - bounds.top) }
    val width = (bounds.width + margin * 2).coerceAtLeast(1.0)
    val height = (bounds.height + margin * 2).coerceAtLeast(1.0)
    return Page(
        width = width,
        height = height,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(moved)),
    )
}

/**
 * Accept a drop on the page. Returns true when the drop produced an element (or was handed to the
 * host for a PDF), false when the clip carried nothing this canvas can consume.
 */
internal fun DrawingSurfaceView.dropExternal(event: DragEvent): Boolean {
    val clip = event.clipData ?: return false
    val box = layout.pageAt(event.x + scrollX, event.y + scrollY) ?: return false
    val x = box.toPtX(event.x, scrollX)
    val y = box.toPtY(event.y, scrollY)

    for (i in 0 until clip.itemCount) {
        val item = clip.getItemAt(i)
        item.uri?.let { uri ->
            if (dropUri(uri, box.index, x, y)) return true
        }
        val text = item.text?.toString() ?: continue
        val elements = ElementTransfer.decode(text)
        if (elements.isNotEmpty()) return dropElements(box.index, x, y, elements)
        val ingested = ExternalIngest.fromText(text, x, y, colorArgb) ?: continue
        val before = doc
        val (pages, refs) = SelectionOps.addToTopLayer(doc.pages, box.index, listOf(ingested.element))
        if (refs.isEmpty()) continue
        doc = doc.copy(pages = pages)
        selection = ActiveSelection(box.index, refs)
        onSelectionChanged?.invoke(true)
        history.record(before)
        notifyHistory()
        relayout()
        render()
        return true
    }
    return false
}

/** An image or a PDF a drop/file URI hands over; the host owns whatever a PDF import means. */
private fun DrawingSurfaceView.dropUri(uri: Uri, pageIndex: Int, xPt: Double, yPt: Double): Boolean {
    val type = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
    return when {
        type.startsWith("image/") -> {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) false else {
                textEdits.insertImage(Placement(pageIndex, xPt, yPt), bytes)
                true
            }
        }
        type == "application/pdf" -> {
            val handler = onExternalPdf ?: return false
            handler(uri)
            true
        }
        type.startsWith("text/") -> {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull() ?: return false
            val ingested = ExternalIngest.fromText(text, xPt, yPt, colorArgb) ?: return false
            val before = doc
            val (pages, refs) = SelectionOps.addToTopLayer(doc.pages, pageIndex, listOf(ingested.element))
            if (refs.isEmpty()) return false
            doc = doc.copy(pages = pages)
            selection = ActiveSelection(pageIndex, refs)
            onSelectionChanged?.invoke(true)
            history.record(before)
            notifyHistory()
            relayout()
            render()
            true
        }
        else -> false
    }
}

/** Drop [elements] on [pageIndex], top-left at ([xPt], [yPt]), as one undoable edit. */
private fun DrawingSurfaceView.dropElements(pageIndex: Int, xPt: Double, yPt: Double, elements: List<Element>): Boolean {
    val bounds = SelectionOps.boundsOf(elements) ?: return false
    val placed = elements.map { SelectionOps.translate(it, xPt - bounds.left, yPt - bounds.top) }
    val before = doc
    val (pages, refs) = SelectionOps.addToTopLayer(doc.pages, pageIndex, placed)
    if (refs.isEmpty()) return false
    doc = doc.copy(pages = pages)
    selection = ActiveSelection(pageIndex, refs)
    onSelectionChanged?.invoke(true)
    history.record(before)
    notifyHistory()
    relayout()
    render()
    return true
}

/** Install the canvas's drop target. A drop is text, an element payload, an image, a PDF or a file. */
internal fun DrawingSurfaceView.installDropTarget() {
    setOnDragListener { _, event ->
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                val description = event.clipDescription ?: return@setOnDragListener false
                description.hasMimeType(ElementTransfer.MIME_TYPE) ||
                    description.hasMimeType("text/plain") ||
                    description.hasMimeType("text/*") ||
                    description.hasMimeType("image/*") ||
                    description.hasMimeType("application/pdf")
            }
            DragEvent.ACTION_DROP -> dropExternal(event)
            DragEvent.ACTION_DRAG_ENTERED -> true
            DragEvent.ACTION_DRAG_EXITED -> true
            DragEvent.ACTION_DRAG_ENDED -> true
            else -> true
        }
    }
}

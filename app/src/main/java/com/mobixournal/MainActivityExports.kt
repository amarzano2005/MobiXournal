package com.mobixournal

import android.net.Uri
import com.mobixournal.render.DocumentExportSnapshot
import java.io.File
import java.io.OutputStream

/** Encoding and provider I/O use captured document state and independent export resources. */
internal fun MainActivity.exportPdf(uri: Uri) = exportSnapshot(uri, "PDF") { snapshot, out -> snapshot.writePdf(out) }
internal fun MainActivity.exportPagePng(uri: Uri) = exportSnapshot(uri, "PNG") { snapshot, out -> snapshot.writePng(out) }
internal fun MainActivity.exportPageSvg(uri: Uri) = exportSnapshot(uri, "SVG") { snapshot, out -> snapshot.writeSvg(out) }

private fun MainActivity.exportSnapshot(
    uri: Uri, kind: String, write: (DocumentExportSnapshot, OutputStream) -> Unit,
) {
    val view = surface ?: return
    if (tabs.active?.isReadOnly == true || view.isReadOnly) {
        toast("Document is read-only")
        return
    }
    val resolver = contentResolver
    val snapshot = DocumentExportSnapshot(
        view.toDocument(), view.currentPageIndex(), view.pdfSourceFile(), view.imageSources().toMap(),
        view.backgroundResolver,
    ) { reference ->
        runCatching {
            if (reference.contains("://")) resolver.openInputStream(Uri.parse(reference))
            else File(reference).takeIf(File::isFile)?.inputStream()
        }.getOrNull()
    }
    inBackground("Exporting $kind.", { staging.writeTo(uri) { write(snapshot, it) } }) { result ->
        result.onFailure { toast("$kind export failed: ${it.message}") }
    }
}

internal fun MainActivity.suggestedPageImageName(extension: String): String =
    suggestedPdfName().removeSuffix(".pdf") + "." + extension

/** Reading an image can involve a remote provider; only placement touches the View. */
internal fun MainActivity.insertPickedImage(uri: Uri) {
    val placement = pendingImagePlacement ?: return
    pendingImagePlacement = null
    val owner = pane
    val tabId = owner.tabs.active?.id
    inBackground("Reading image.", { staging.readBytes(uri) }) { result ->
        result.onFailure { toast("Image insert failed: ${it.message}") }.onSuccess { bytes ->
            if (owner.tabs.active?.id == tabId) owner.surface?.insertImage(placement, bytes)
            else toast("Image insert cancelled: the original tab is no longer selected")
        }
    }
}

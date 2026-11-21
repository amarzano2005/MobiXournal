package com.mobixournal

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mobixournal.render.DocumentExportSnapshot
import com.mobixournal.ui.ShareFormat
import java.io.File
import java.io.OutputStream

/**
 * Encoding and provider I/O use captured document state and independent export resources.
 *
 * The PDF is written with the pages the *Export PDF* card's answer named ([pendingPdfPages]): the launcher's
 * answer arrives after the card has closed, so the notebook indices travel through the activity rather than
 * through the picker — which knows only a file name.
 */
internal fun MainActivity.exportPdf(uri: Uri) {
    val pages = pendingPdfPages
    pendingPdfPages = null
    exportSnapshot(uri, "PDF") { snapshot, out -> snapshot.writePdf(out, pages) }
}
internal fun MainActivity.exportPagePng(uri: Uri) = exportSnapshot(uri, "PNG") { snapshot, out -> snapshot.writePng(out) }
internal fun MainActivity.exportPageSvg(uri: Uri) = exportSnapshot(uri, "SVG") { snapshot, out -> snapshot.writeSvg(out) }

internal fun MainActivity.shareDocument(format: ShareFormat) {
    when (format) {
        ShareFormat.PDF -> shareSnapshot(suggestedPdfName(), "application/pdf", "Share PDF") { snapshot, out ->
            snapshot.writePdf(out, null)
        }
        ShareFormat.PNG -> shareSnapshot(suggestedPageImageName("png"), "image/png", "Share PNG") { snapshot, out ->
            snapshot.writePng(out)
        }
        ShareFormat.SVG -> shareSnapshot(suggestedPageImageName("svg"), "image/svg+xml", "Share SVG") { snapshot, out ->
            snapshot.writeSvg(out)
        }
        ShareFormat.PORTABLE -> sharePortableCopy()
    }
}

private fun MainActivity.shareSnapshot(
    fileName: String,
    mimeType: String,
    chooserTitle: String,
    write: (DocumentExportSnapshot, OutputStream) -> Unit,
) {
    val snapshot = buildExportSnapshot() ?: return
    val target = shareCacheFile(fileName)
    inBackground("Preparing $chooserTitle.", {
        target.outputStream().buffered().use { out -> write(snapshot, out) }
        target
    }) { result ->
        result.onFailure { toast("Share failed: ${it.message}") }
            .onSuccess { file -> launchShareChooser(file, mimeType, chooserTitle) }
    }
}

internal fun MainActivity.shareCacheFile(fileName: String): File {
    val dir = File(cacheDir, "shared").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return File(dir, safeName)
}

internal fun MainActivity.launchShareChooser(file: File, mimeType: String, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

private fun MainActivity.buildExportSnapshot(): DocumentExportSnapshot? {
    val view = surface ?: return null
    if (tabs.active?.isReadOnly == true || view.isReadOnly) {
        toast("Document is read-only")
        return null
    }
    val resolver = contentResolver
    return DocumentExportSnapshot(
        view.toDocument(), view.currentPageIndex(), view.pdfSourceFile(), view.imageSources().toMap(),
        view.backgroundResolver,
    ) { reference ->
        runCatching {
            if (reference.contains("://")) resolver.openInputStream(Uri.parse(reference))
            else File(reference).takeIf(File::isFile)?.inputStream()
        }.getOrNull()
    }
}

private fun MainActivity.exportSnapshot(
    uri: Uri, kind: String, write: (DocumentExportSnapshot, OutputStream) -> Unit,
) {
    val snapshot = buildExportSnapshot() ?: return
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

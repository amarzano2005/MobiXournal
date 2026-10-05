package com.mobixournal.io

/** The suffix every document this app writes carries, whichever `SaveFormat` produced the bytes. */
const val XOPP_SUFFIX = ".xopp"

/** The suffix a flattened export carries. */
const val PDF_SUFFIX = ".pdf"

/**
 * The MIME type this app gives a `.xopp` document, and registers itself to handle (see the
 * `AndroidManifest.xml` intent filters).
 *
 * A dedicated type rather than the `application/octet-stream` it used to be, so that a file we write
 * is a *document* to the rest of the system — associated with this app, offered in **Open with**,
 * and identifiable in a share sheet — instead of an untyped blob.
 *
 * It is deliberately a **vendor** type that maps to no extension, because the Storage Access
 * Framework appends an extension whenever it can map the MIME to one: `application/gzip` made it save
 * `notes.xopp.gz`, which desktop Xournal++ will not open by name. Nothing maps to `.xopp`, so the
 * name we ask for is the name we get.
 */
const val XOPP_MIME = "application/x-xopp"

/**
 * The file name to offer when a document that came in as a **PDF** is saved for the first time.
 *
 * A PDF opened from the picker or handed over by another app has no `.xopp` on disk behind it — the
 * annotations live only in the tab until they're written somewhere. Saving must never write `.xopp`
 * bytes back over the source PDF, so the first save asks for a destination, and this is the name it
 * suggests: the PDF's own name with the extension swapped, so annotations sit beside the original.
 */
fun xoppNameFor(sourceName: String): String =
    nameWithSuffix(sourceName, XOPP_SUFFIX, "Untitled$XOPP_SUFFIX")

/**
 * The file name to offer when flattening the open document to a PDF.
 *
 * An export belongs beside the document it came from, so `notes.xopp` exports as `notes.pdf` rather
 * than the generic `document.pdf` the export used to ask for. A document that has no name of its own
 * yet is called `Untitled` everywhere else in the app, so it exports as `Untitled.pdf`.
 */
fun pdfNameFor(documentName: String): String =
    nameWithSuffix(documentName, PDF_SUFFIX, "Untitled$PDF_SUFFIX")

/**
 * [name] with its extension replaced by [suffix] — the one rule behind both suggested names above.
 *
 * Only a real extension is replaced: a dot in a leading position is part of the name, not a suffix
 * (`/Downloads/.hidden` is a hidden file called `.hidden`, not a file with an empty name), and a name
 * with no dot at all just gains the suffix. A blank name falls back to [fallback].
 */
private fun nameWithSuffix(name: String, suffix: String, fallback: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return fallback
    val dot = trimmed.lastIndexOf('.')
    val stem = if (dot > 0) trimmed.substring(0, dot) else trimmed
    return stem + suffix
}

/**
 * True when [authority] belongs to a cloud or remote storage provider (e.g. Google Drive,
 * OneDrive, Dropbox, Nextcloud, Box).
 */
fun isCloudAuthority(authority: String?): Boolean {
    if (authority.isNullOrBlank()) return false
    val lower = authority.lowercase(java.util.Locale.ROOT)
    return lower.startsWith("com.google.android.apps.docs") ||
        lower.contains("drive") ||
        lower.contains("cloud") ||
        lower.contains("dropbox") ||
        lower.contains("onedrive") ||
        lower.contains("skydrive") ||
        lower.contains("box.android") ||
        lower.contains("pcloud") ||
        lower.contains("mega.privacy")
}

/**
 * True when [uri] points to a cloud or remote storage provider.
 */
fun isCloudUri(uri: android.net.Uri): Boolean = isCloudAuthority(uri.authority)


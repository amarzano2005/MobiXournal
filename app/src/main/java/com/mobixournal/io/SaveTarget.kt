package com.mobixournal.io

import com.mobixournal.format.SaveFormat
import com.mobixournal.format.mobix.MobixFormat

/** The suffix every `.xopp` this app writes carries, whichever `SaveFormat` produced the bytes. */
const val XOPP_SUFFIX = ".xopp"

/** The suffix of this app's own format — the same string the format layer names as an extension. */
const val MOBIX_SUFFIX = MobixFormat.EXTENSION

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
 * The MIME type of a `.mobix` document: the format's own declared type, spelled once in
 * [MobixFormat.MIMETYPE] and re-used here rather than copied.
 *
 * A reader must never decide by this type on disk (a SAF `content://` URI is often untyped, and two
 * apps may disagree), but a *writer* has to declare one so the file is typed for the rest of the
 * system — offered in **Open with**, identifiable in a share sheet. Like [XOPP_MIME] it maps to no
 * extension, so the Storage Access Framework does not append one to the name we ask for.
 */
const val MOBIX_MIME = MobixFormat.MIMETYPE

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
 * The file name to offer when a document is written in **this app's own format**: the source's own
 * name with `.mobix` in place of whatever it had.
 *
 * The same rule as [xoppNameFor], because it is the same act — a save that lands under a name the
 * format can be recognised by — and the one thing a `.mobix` save must never do is keep a `.xopp`
 * suffix: a file that says `.xopp` and holds a `.mobix` archive is a file the desktop is invited to
 * open and cannot.
 */
fun mobixNameFor(sourceName: String): String =
    nameWithSuffix(sourceName, MOBIX_SUFFIX, "Untitled$MOBIX_SUFFIX")

/**
 * The name a save of [documentName] in [format] should propose.
 *
 * Keeping the choice here rather than at the three call sites (plain Save, Save As, a restored
 * session) is what makes the format and the name agree: the sticky format decides the suffix, so a
 * document opened as a `.mobix` is offered as a `.mobix`.
 */
fun saveNameFor(documentName: String, format: SaveFormat): String = when (format) {
    SaveFormat.MOBIX -> mobixNameFor(documentName)
    SaveFormat.ORIGINAL, SaveFormat.ZIPPED -> xoppNameFor(documentName)
}

/** The MIME type a file written in [format] is declared as to the system's pickers. */
fun saveMimeFor(format: SaveFormat): String = when (format) {
    SaveFormat.MOBIX -> MOBIX_MIME
    SaveFormat.ORIGINAL, SaveFormat.ZIPPED -> XOPP_MIME
}

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


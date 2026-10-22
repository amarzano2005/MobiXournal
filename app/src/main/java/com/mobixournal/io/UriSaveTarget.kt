package com.mobixournal.io

import android.net.Uri
import com.mobixournal.format.model.Document
import com.mobixournal.io.DocumentEncoder.SaveTarget

/**
 * A save into a **SAF** destination — the app's destination for everything the user picks.
 *
 * It is the `content://` half of [SaveTarget]: the name is the display name the provider reports, and
 * a background reference is made portable by the resolver's own arithmetic, which relativises on the
 * URI's *document id* (`primary:Docs/notes.xopp`) rather than on a path. That arithmetic cannot leave
 * the app — it is `android.net.Uri` and `DocumentsContract` — which is exactly why the encoder takes a
 * target instead of a location, and why the desktop's save ([DocumentEncoder.FileSaveTarget]) is a
 * different implementation of the same two answers.
 *
 * An opaque provider id (a Downloads-style URI, a remote share) relativises to nothing and keeps its
 * absolute reference: the resolver answers the document unchanged, as it always has.
 */
internal class UriSaveTarget(
    private val target: Uri,
    override val name: String?,
    private val refs: DocumentReferenceResolver,
) : SaveTarget {

    override fun portable(document: Document): Document = refs.portableReference(document, target)
}

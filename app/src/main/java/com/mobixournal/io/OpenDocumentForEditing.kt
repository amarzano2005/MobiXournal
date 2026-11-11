package com.mobixournal.io

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/**
 * The document picker, asking for a **persistable read+write** grant rather than the default one-shot
 * read.
 *
 * That grant is what lets a later plain Save write straight back to the file the document came from —
 * including one on a mounted network share — and lets a restored tab still reach it after a restart. It
 * lives here rather than inside the activity because it is intent arithmetic with no host of its own.
 */
internal class OpenDocumentForEditing : ActivityResultContracts.OpenDocument() {

    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
}

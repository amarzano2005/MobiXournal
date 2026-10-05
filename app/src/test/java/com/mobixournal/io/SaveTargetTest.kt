package com.mobixournal.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The names the save/export pickers are offered. Both are the same "swap the extension" rule, which
 * is what guarantees a document always lands as a `.xopp` and an export always lands beside its
 * document — so the cases below are shared between them on purpose.
 */
class SaveTargetTest {

    // --- the document name (Save / Save As) ------------------------------------------------------

    @Test
    fun `a pdf name becomes the same name as a xopp`() {
        assertEquals("bhm_prior.xopp", xoppNameFor("bhm_prior.pdf"))
    }

    @Test
    fun `the extension match ignores case`() {
        assertEquals("Notes.xopp", xoppNameFor("Notes.PDF"))
    }

    @Test
    fun `only the last extension is replaced`() {
        assertEquals("report.2026.xopp", xoppNameFor("report.2026.pdf"))
    }

    @Test
    fun `a name with no extension just gains one`() {
        assertEquals("scan.xopp", xoppNameFor("scan"))
    }

    @Test
    fun `a leading dot is part of the name, not an extension`() {
        assertEquals(".hidden.xopp", xoppNameFor(".hidden"))
    }

    @Test
    fun `a name that is already a xopp is left alone`() {
        assertEquals("notes.xopp", xoppNameFor("notes.xopp"))
    }

    @Test
    fun `a blank name falls back to Untitled`() {
        assertEquals("Untitled.xopp", xoppNameFor(""))
        assertEquals("Untitled.xopp", xoppNameFor("   "))
    }

    @Test
    fun `a name in someone else's format is still saved as a xopp`() {
        // A typeset text file arrives as `notes.txt`, and a document opened off a share may carry
        // any extension at all: whatever the tab is called, the file written is a `.xopp`.
        assertEquals("notes.xopp", xoppNameFor("notes.txt"))
        assertEquals("notes.xopp", xoppNameFor("notes.md"))
        assertEquals("notes.xopp", xoppNameFor("notes.zip"))
    }

    // --- the PDF export name ---------------------------------------------------------------------

    @Test
    fun `a pdf export is named after its document`() {
        assertEquals("notes.pdf", pdfNameFor("notes.xopp"))
        assertEquals("bhm_prior.pdf", pdfNameFor("bhm_prior.xopp"))
    }

    @Test
    fun `a pdf export of a named document keeps the rest of the name`() {
        assertEquals("report.2026.pdf", pdfNameFor("report.2026.xopp"))
        assertEquals(".hidden.pdf", pdfNameFor(".hidden.xopp"))
    }

    @Test
    fun `a pdf export falls back when the document has no name yet`() {
        assertEquals("Untitled.pdf", pdfNameFor(""))
        assertEquals("Untitled.pdf", pdfNameFor("   "))
        assertEquals("Untitled.pdf", pdfNameFor("Untitled"))
    }

    @Test
    fun `a pdf export never doubles the suffix`() {
        // Round-tripping the suggestion must be stable, or a second export would be notes.pdf.pdf.
        assertEquals("notes.pdf", pdfNameFor(pdfNameFor("notes.xopp")))
    }

    // --- the MIME the app declares -----------------------------------------------------------------

    @Test
    fun `the document mime is a dedicated type, not a generic blob`() {
        assertEquals("application/x-xopp", XOPP_MIME)
        assertTrue("must not be generic", XOPP_MIME != "application/octet-stream")
        // SAF appends an extension for any MIME it can map one to, and would then save
        // `notes.xopp.gz` — which desktop Xournal++ won't open by name. Nothing maps `.xopp`, so
        // the MIME has to stay one the platform has no extension for.
        assertTrue("must be a vendor type", XOPP_MIME.startsWith("application/x-"))
    }

    // --- cloud authority detection -----------------------------------------------------------------

    @Test
    fun `cloud authorities are recognized`() {
        assertTrue(isCloudAuthority("com.google.android.apps.docs.storage"))
        assertTrue(isCloudAuthority("com.google.android.apps.docs.files"))
        assertTrue(isCloudAuthority("com.microsoft.skydrive.content.StorageAccessProvider"))
        assertTrue(isCloudAuthority("com.dropbox.android.provider"))
        assertTrue(isCloudAuthority("com.box.android.documents"))
        assertTrue(isCloudAuthority("com.nextcloud.client.providers.DocumentsStorageProvider"))
        assertTrue(isCloudAuthority("org.owncloud.providers.DocumentsStorageProvider"))
        assertTrue(isCloudAuthority("com.pcloud.pcloud.documents"))
        assertTrue(isCloudAuthority("mega.privacy.android.provider"))
    }

    @Test
    fun `local storage authorities are not recognized as cloud`() {
        org.junit.Assert.assertFalse(isCloudAuthority("com.android.externalstorage.documents"))
        org.junit.Assert.assertFalse(isCloudAuthority("com.android.providers.downloads.documents"))
        org.junit.Assert.assertFalse(isCloudAuthority("com.android.providers.media.documents"))
        org.junit.Assert.assertFalse(isCloudAuthority(null))
        org.junit.Assert.assertFalse(isCloudAuthority(""))
    }
}

package com.mobixournal.tabs

import android.content.ContentResolver
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TableElement
import com.mobixournal.io.DocumentIo
import com.mobixournal.io.LoadedFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verifies that `.xopp` (`ORIGINAL` and `ZIPPED`) preserves living entities across direct opens
 * (`DocumentIo.read` -> `reviveFromBridge`) and across session snapshots (`MobixTabSnapshots`),
 * including embedded `bg.pdf` payloads in `ZIPPED` snapshots.
 */
class XoppSessionRoundTripTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun io(): DocumentIo = DocumentIo(
        resolver = object : ContentResolver(null) {},
        cacheDir = temp.newFolder("cache"),
        filesDir = temp.newFolder("files"),
        appVersion = "2.4.0",
        textPdf = null,
    )

    private fun tableDoc(pdfBackground: Boolean = false): Document {
        val table = TableElement(
            left = 40.0,
            top = 80.0,
            colWidths = listOf(120.0, 120.0),
            rowHeights = listOf(60.0, 60.0),
        )
        val bg = if (pdfBackground) Background.Pdf(pageNo = 1, domain = "absolute", filename = "deck.pdf")
        else Background.Solid(style = "lined", color = 0xFFFFFFFF.toInt())
        return Document(
            title = "Notes",
            pages = listOf(Page(width = 595.0, height = 842.0, background = bg, layers = listOf(Layer(listOf(table))))),
        )
    }

    @Test
    fun `direct xopp open revives living entities for both ORIGINAL and ZIPPED`() {
        val io = io()
        val pdfBytes = "%PDF-1.4\n%fixture\n".toByteArray()
        val pdfFile = temp.newFile("deck.pdf").apply { writeBytes(pdfBytes) }

        val origEncoded = io.encode(tableDoc(), pdf = null, format = SaveFormat.ORIGINAL)
        val origLoaded = io.read(origEncoded.file, "notes.xopp", null) as LoadedFile.Doc
        assertTrue(origLoaded.document.pages[0].layers[0].elements.single() is TableElement)

        val zipEncoded = io.encode(tableDoc(pdfBackground = true), pdf = pdfFile, format = SaveFormat.ZIPPED)
        val zipLoaded = io.read(zipEncoded.file, "notes-zip.xopp", null) as LoadedFile.Doc
        assertTrue(zipLoaded.document.pages[0].layers[0].elements.single() is TableElement)
        assertNotNull(zipLoaded.pdf)
        assertArrayEquals(pdfBytes, zipLoaded.pdf!!.readBytes())
    }

    @Test
    fun `MobixTabSnapshots preserves living entities for ORIGINAL and ZIPPED including bg pdf`() {
        val io = io()
        val snapshots = MobixTabSnapshots(io)
        val pdfBytes = "%PDF-1.4\n%session-pdf\n".toByteArray()
        val pdfFile = temp.newFile("session.pdf").apply { writeBytes(pdfBytes) }

        val origTab = OpenTab("tab-orig", "Orig", tableDoc(), format = SaveFormat.ORIGINAL)
        val origSnapshot = temp.newFile("tab-orig.xopp")
        snapshots.write(origSnapshot, origTab, SnapshotSources())
        val origRestored = snapshots.read(origSnapshot, origTab)!!
        assertTrue(origRestored.document.pages[0].layers[0].elements.single() is TableElement)

        val zipTab = OpenTab("tab-zip", "Zipped", tableDoc(pdfBackground = true), format = SaveFormat.ZIPPED)
        val zipSnapshot = temp.newFile("tab-zip.xopp")
        snapshots.write(zipSnapshot, zipTab, SnapshotSources(pdf = pdfFile))
        val zipRestored = snapshots.read(zipSnapshot, zipTab)!!
        assertTrue(zipRestored.document.pages[0].layers[0].elements.single() is TableElement)
        assertNotNull(zipRestored.pdf)
        assertEquals(SaveFormat.ZIPPED, zipTab.format)
        assertArrayEquals(pdfBytes, zipRestored.pdf!!.readBytes())
    }
}

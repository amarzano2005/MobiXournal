package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopContainer
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.io.DesktopPdfIndex
import com.mobixournal.desktop.io.DesktopPdfIndexer
import com.mobixournal.desktop.io.PdfPageReferences
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.PageBackdrop
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.shell.Header

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.render.PdfOutline
import com.mobixournal.render.PdfOutlineEntry
import com.mobixournal.render.PdfTextIndex
import com.mobixournal.render.PdfWord
import java.io.File

/**
 * A desktop tab whose page sits on a PDF, with an index already in place.
 *
 * The PDF features all read the same two things — a page whose `Background.Pdf` names a sheet, and the
 * tab's `pdfIndex` — so the fixtures are built here once instead of in each test. The file on disk is a
 * placeholder: the tests that read a real PDF build one with PDFBox and go through
 * [DesktopPdfIndexer]; these tabs are about what a *loaded* index means.
 */
internal object DesktopPdfFixture {

    const val PAGE_WIDTH_PT = 595.0
    const val PAGE_HEIGHT_PT = 842.0

    /** The two-word line every selection and highlighter test crosses. */
    val LINE: List<PdfWord> = listOf(
        PdfWord("Magnetic", 50.0, 100.0, 110.0, 112.0),
        PdfWord("highlighter", 114.0, 100.0, 180.0, 112.0),
    )

    /** A tab on [pages] PDF-backed pages, each showing the PDF sheet of the same index. */
    fun tab(
        dir: File,
        pages: Int = 1,
        words: List<List<PdfWord>> = listOf(LINE),
        outline: PdfOutline = PdfOutline.EMPTY,
    ): DesktopTab {
        val model = Document(
            pages = (0 until pages).map { index ->
                Page(
                    width = PAGE_WIDTH_PT,
                    height = PAGE_HEIGHT_PT,
                    background = Background.Pdf(
                        // The first page carries the reference; every other page names its sheet only
                        // (`PdfPageReferences`), which is the shape an import writes.
                        filename = if (index == 0) "fixture.pdf" else null,
                        pageNo = index,
                        domain = "absolute",
                    ),
                    layers = listOf(Layer(emptyList())),
                )
            },
        )
        val file = File(dir, "fixture.xopp")
        val document = DesktopDocument(
            file = file,
            source = com.mobixournal.format.mobix.PageSource.of(model),
            model = model,
            container = DesktopContainer.XOPP_GZIP,
            backdrop = PageBackdrop(bundled = { null }, documentDir = dir),
        )
        val tab = DesktopTabs().open(document)
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        tab.pdfIndex = DesktopPdfIndex(PdfTextIndex(words), outline)
        return tab
    }

    /** An outline whose first chapter points at sheet 0, a section at sheet 1, and a header nowhere. */
    fun outline(): PdfOutline = PdfOutline(
        listOf(
            PdfOutlineEntry("Chapter 1", 0, depth = 0),
            PdfOutlineEntry("Section 1.1", 1, depth = 1),
            PdfOutlineEntry("Header only", PdfOutlineEntry.NO_DESTINATION, depth = 1),
        ),
    )
}

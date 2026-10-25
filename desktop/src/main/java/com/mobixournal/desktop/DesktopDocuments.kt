package com.mobixournal.desktop

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.File

/**
 * The window's **new page**: a one-sheet document, the same paper the tablet's New command opens.
 *
 * The desktop window used to start on an empty notice and could only open a file, so there was no way to
 * begin a note on the PC at all. This is the document a `+` and the startup screen stand for — A4, ruled
 * `graph`, one empty layer — and nothing else: no template, no sample ink.
 *
 * Its [File] is a **name waiting to be written**, not a file: the tab is dirty from the start, so `Ctrl+S`
 * asks where to put it and closing asks whether to keep it. The folder is the one a desktop app may write
 * without a dialog (`%USERPROFILE%\Documents\MobiXournal`), and the desktop's Save As picker rewrites it.
 */
internal object DesktopDocuments {

    /** A4 in points, the page every new sheet is born with. */
    const val PAGE_WIDTH_PT: Double = 595.276
    const val PAGE_HEIGHT_PT: Double = 841.89

    /** A fresh document, with one blank page. */
    fun blank(file: File): DesktopDocument {
        val document = Document(pages = listOf(blankPage()))
        return DesktopDocument(
            file = file,
            source = PageSource.of(document),
            model = document,
            container = DesktopContainer.XOPP_GZIP,
            backdrop = PageBackdrop(bundled = { null }, documentDir = file.parentFile),
        )
    }

    /** One A4 page ruled `graph`, with a single empty layer — the tablet's blank sheet. */
    fun blankPage(
        widthPt: Double = PAGE_WIDTH_PT,
        heightPt: Double = PAGE_HEIGHT_PT,
    ): Page = Page(
        width = widthPt,
        height = heightPt,
        background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "graph"),
        layers = listOf(Layer(emptyList())),
    )

    /**
     * A path for a new document that no open tab already uses: `Untitled 1.xopp`, `Untitled 2.xopp`, …
     * The file is not created — Save As is what writes it.
     */
    fun newPath(taken: Set<String>): File {
        val folder = File(
            File(System.getProperty("user.home"), "Documents"),
            "MobiXournal",
        )
        // A save-back stages its temp file beside the target, so the folder has to exist even though
        // the document's own file does not yet (Save As writes it there, or the user picks elsewhere).
        runCatching { folder.mkdirs() }
        var index = 1
        while (true) {
            val candidate = File(folder, "Untitled $index.xopp")
            if (DesktopTabs.keyOf(candidate) !in taken && !candidate.exists()) return candidate
            index++
        }
    }
}

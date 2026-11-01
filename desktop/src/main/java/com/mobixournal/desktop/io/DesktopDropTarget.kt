package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.shell.DesktopShellState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import java.awt.Component
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener
import java.io.File

/**
 * Supported file families when dropped onto the desktop window.
 */
enum class DroppedFileKind {
    IMAGE,
    PDF,
    DOCUMENT,
    UNSUPPORTED,
}

/**
 * Drag-and-drop ingest and file router for the desktop workstation window.
 */
object DesktopDropTarget {

    /** Classifies [file] into one of the supported dropped file families. */
    fun classify(file: File): DroppedFileKind = when (file.extension.lowercase()) {
        "png", "jpg", "jpeg", "webp", "gif" -> DroppedFileKind.IMAGE
        "pdf" -> DroppedFileKind.PDF
        "mobix", "xopp" -> DroppedFileKind.DOCUMENT
        else -> DroppedFileKind.UNSUPPORTED
    }

    /**
     * Routes dropped files to appropriate actions on [state]:
     * - Image: placed as [com.mobixournal.format.model.ImageElement] on active tab.
     * - PDF: prompts [state.askImportPdf].
     * - Document: opens as tab via [state.open].
     * - Unsupported: sets [state.failure].
     */
    fun routeDroppedFiles(
        state: DesktopShellState,
        files: List<File>,
        dropPoint: Point? = null,
    ) {
        for (file in files) {
            when (classify(file)) {
                DroppedFileKind.IMAGE -> {
                    val tab = state.active ?: run {
                        state.newDocument()
                        state.active
                    }
                    if (tab != null) {
                        val targetCenter = if (dropPoint != null && tab.viewport.zoom > 0) {
                            val (pageX, pageY) = tab.viewport.toPage(dropPoint.x.toFloat(), dropPoint.y.toFloat())
                            (pageX.toDouble()) to (pageY.toDouble())
                        } else null
                        DesktopImagePlacement.insertFromFile(state, tab, file, targetCenter)
                    }
                }
                DroppedFileKind.PDF -> {
                    state.askImportPdf(file)
                }
                DroppedFileKind.DOCUMENT -> {
                    state.open(file)
                }
                DroppedFileKind.UNSUPPORTED -> {
                    state.failure = OpenFailure(
                        title = "Unsupported file type",
                        detail = "“${file.name}” is not a supported image (PNG, JPEG, WebP, GIF), PDF, or notebook (.mobix, .xopp).",
                    )
                }
            }
        }
    }

    /**
     * Attaches an AWT [DropTarget] to [component], driving [state.isDragHovering]
     * and routing accepted drops to [routeDroppedFiles].
     */
    fun install(component: Component, state: DesktopShellState): DropTarget {
        val listener = object : DropTargetListener {
            override fun dragEnter(dtde: DropTargetDragEvent) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    state.isDragHovering = true
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun dragOver(dtde: DropTargetDragEvent) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                }
            }

            override fun dropActionChanged(dtde: DropTargetDragEvent) {}

            override fun dragExit(dte: DropTargetEvent) {
                state.isDragHovering = false
            }

            override fun drop(dtde: DropTargetDropEvent) {
                state.isDragHovering = false
                if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.rejectDrop()
                    return
                }
                dtde.acceptDrop(DnDConstants.ACTION_COPY)
                val files = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                }.getOrNull()

                if (!files.isNullOrEmpty()) {
                    routeDroppedFiles(
                        state = state,
                        files = files,
                        dropPoint = dtde.location,
                    )
                    dtde.dropComplete(true)
                } else {
                    dtde.dropComplete(false)
                }
            }
        }
        return DropTarget(component, DnDConstants.ACTION_COPY, listener, true)
    }
}

/**
 * Visual hover affordance overlay conforming to docs/interface-contract.md.
 */
@Composable
internal fun DragHoverOverlay(visible: Boolean) {
    if (!visible) return
    Box(
        Modifier
            .fillMaxSize()
            .background(DesktopTheme.desk.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .glassSurface(cornerRadius = 20.dp)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(40.dp)) {
                drawChromeGlyph(ChromeGlyph.OPEN, DesktopTheme.accent)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Drop files here",
                color = DesktopTheme.ink,
                style = DesktopTheme.title,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Images are placed on page • PDFs import backgrounds • Notebooks open in tab",
                color = DesktopTheme.inkSecondary,
                style = DesktopTheme.body,
            )
        }
    }
}

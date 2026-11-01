package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.placeLivingElement
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.DesktopTab

import com.mobixournal.format.model.ImageElement
import com.mobixournal.render.Bounds
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage

/**
 * Image element placement arithmetic and commands for Desktop.
 *
 * Implements:
 * 1. Aspect-ratio preserving bounds calculation centered within page/viewport.
 * 2. Element creation and atomic insertion into the active layer with single undo step.
 * 3. System clipboard image extraction ([DataFlavor.imageFlavor]) and paste.
 */
internal object DesktopImagePlacement {

    /**
     * Calculates the bounding box for an image of dimensions ([imageWidthPx], [imageHeightPx])
     * placed on a page of dimensions ([pageWidthPt], [pageHeightPt]) centered around ([centerX], [centerY]).
     *
     * Preserves the image's native aspect ratio while constraining it to at most 60% of the page size
     * (and at least 40 pt). Bounds are clamped so the placed element remains within page boundaries.
     */
    fun calculateBounds(
        imageWidthPx: Double,
        imageHeightPx: Double,
        pageWidthPt: Double,
        pageHeightPt: Double,
        centerX: Double = pageWidthPt / 2.0,
        centerY: Double = pageHeightPt / 2.0,
    ): Bounds {
        val safeW = imageWidthPx.coerceAtLeast(1.0)
        val safeH = imageHeightPx.coerceAtLeast(1.0)

        val maxW = (pageWidthPt * 0.6).coerceAtLeast(40.0)
        val maxH = (pageHeightPt * 0.6).coerceAtLeast(40.0)

        var targetW = safeW
        var targetH = safeH

        if (targetW > maxW || targetH > maxH) {
            val scale = minOf(maxW / targetW, maxH / targetH)
            targetW *= scale
            targetH *= scale
        }
        if (targetW < 40.0 && targetH < 40.0) {
            val scale = minOf(40.0 / targetW, 40.0 / targetH)
            targetW *= scale
            targetH *= scale
        }

        var left = centerX - targetW / 2.0
        var top = centerY - targetH / 2.0
        var right = left + targetW
        var bottom = top + targetH

        if (right > pageWidthPt) {
            val shift = right - pageWidthPt
            left = maxOf(0.0, left - shift)
            right = left + targetW
        }
        if (left < 0.0) {
            left = 0.0
            right = minOf(pageWidthPt, left + targetW)
        }
        if (bottom > pageHeightPt) {
            val shift = bottom - pageHeightPt
            top = maxOf(0.0, top - shift)
            bottom = top + targetH
        }
        if (top < 0.0) {
            top = 0.0
            bottom = minOf(pageHeightPt, top + targetH)
        }

        return Bounds(left, top, right, bottom)
    }

    /**
     * Reads image bytes from [file] and places it onto [tab]'s active page.
     */
    fun insertFromFile(
        state: DesktopShellState,
        tab: DesktopTab,
        file: File,
        targetCenter: Pair<Double, Double>? = null,
    ): Boolean {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        return insertBytes(state, tab, bytes, targetCenter)
    }

    /**
     * Decodes [bytes] to discover dimensions, computes bounds, constructs [ImageElement],
     * commits to the active layer with one undo step, and hands over to the selection tool.
     */
    fun insertBytes(
        state: DesktopShellState,
        tab: DesktopTab,
        bytes: ByteArray,
        targetCenter: Pair<Double, Double>? = null,
    ): Boolean {
        val skia = runCatching { SkiaImage.makeFromEncoded(bytes) }.getOrNull() ?: return false
        val width = skia.width.toDouble()
        val height = skia.height.toDouble()
        skia.close()

        val page = runCatching { tab.document.page(tab.page) }.getOrNull() ?: return false
        val center = targetCenter ?: tab.visiblePageCenter(page)
        val centerX = center.first
        val centerY = center.second

        val bounds = calculateBounds(
            imageWidthPx = width,
            imageHeightPx = height,
            pageWidthPt = page.width,
            pageHeightPt = page.height,
            centerX = centerX,
            centerY = centerY,
        )

        val element = ImageElement(
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
            data = bytes,
        )

        val placed = placeLivingElement(tab, element) { index -> state.edited(tab, index) }
        if (placed) {
            state.handToSelection()
        }
        return placed
    }

    /**
     * Converts an AWT [java.awt.Image] to PNG-encoded bytes.
     */
    fun imageToPngBytes(image: java.awt.Image): ByteArray? {
        val width = image.getWidth(null).coerceAtLeast(1)
        val height = image.getHeight(null).coerceAtLeast(1)
        val buffered = when (image) {
            is BufferedImage -> image
            else -> {
                val b = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val g = b.createGraphics()
                g.drawImage(image, 0, 0, null)
                g.dispose()
                b
            }
        }
        val baos = ByteArrayOutputStream()
        return if (ImageIO.write(buffered, "png", baos)) baos.toByteArray() else null
    }

    /**
     * Checks if [clipboard] carries an image ([DataFlavor.imageFlavor]), converts it to PNG bytes,
     * computes bounds centered on viewport or page, commits as an [ImageElement] with one undo step,
     * and selects it.
     */
    fun pasteSystemImage(
        tab: DesktopTab,
        clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard,
        targetCenter: Pair<Double, Double>? = null,
        onEdited: (Int) -> Unit = {},
    ): Boolean {
        val transferable = runCatching { clipboard.getContents(null) }.getOrNull() ?: return false
        if (!transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) return false
        val awtImage = runCatching {
            transferable.getTransferData(DataFlavor.imageFlavor) as? java.awt.Image
        }.getOrNull() ?: return false

        val pngBytes = imageToPngBytes(awtImage) ?: return false

        val page = runCatching { tab.document.page(tab.page) }.getOrNull() ?: return false
        val center = targetCenter ?: tab.visiblePageCenter(page)
        val centerX = center.first
        val centerY = center.second

        val bounds = calculateBounds(
            imageWidthPx = awtImage.getWidth(null).toDouble(),
            imageHeightPx = awtImage.getHeight(null).toDouble(),
            pageWidthPt = page.width,
            pageHeightPt = page.height,
            centerX = centerX,
            centerY = centerY,
        )

        val element = ImageElement(
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
            data = pngBytes,
        )

        return placeLivingElement(tab, element, onEdited)
    }
}

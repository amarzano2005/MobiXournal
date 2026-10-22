package com.mobixournal.desktop

import com.mobixournal.format.SaveFormat
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.model.Background
import com.mobixournal.io.DocumentEncoder
import com.mobixournal.render.ATTACH_DOMAIN
import com.mobixournal.render.Uncompilable
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Saving from the window — the write half of `desktop-save-and-save-as-through-the-core-encoder`.
 *
 * The bytes are the **core's** ([DocumentEncoder]): the three format arms, the `.xopp` synthesis and
 * the `.mobix` repack are one implementation, shared with the tablet. What lives here is what a
 * desktop save adds on top of it: which container a document is written as, and the atomic replace —
 * a file on a PC is written through a temp file in the same folder and moved over the original, so a
 * crash mid-write cannot leave a truncated notebook where a whole one was.
 *
 * The **sticky format** is `docs/data-path.md`'s rule and applies to both shells: a document opened as
 * a `.mobix` writes a `.mobix`, one opened as a gzip `.xopp` writes that, and one opened as a ZIP
 * package writes a package. Save As is the only place the format changes.
 */
object DesktopSaves {

    /**
     * This build's version, written into a `.mobix` as its `minAppVersion` — the version that must be
     * present to open the file safely (`docs/mobix-schema-spec.md`).
     *
     * The app reads its own from `BuildConfig`; the window has no such field, so the number lives here
     * and `DesktopSaveTest` fails the build when it drifts from `app/build.gradle.kts`'s `versionName`.
     */
    const val APP_VERSION = "1.3.0"

    /** The container a save writes for a document opened as [this]. */
    fun formatOf(container: DesktopContainer): SaveFormat = when (container) {
        DesktopContainer.XOPP_GZIP -> SaveFormat.ORIGINAL
        DesktopContainer.XOPP_ZIP -> SaveFormat.ZIPPED
        DesktopContainer.MOBIX -> SaveFormat.MOBIX
    }

    /**
     * The container Save As writes for a destination named [name], coming from a document opened as
     * [from]: the **name the user gave it** decides, which is the one place the sticky format changes
     * (`docs/data-path.md`). A name with no recognised suffix keeps the document's own container, so a
     * typo types nothing away.
     */
    fun formatFor(name: String, from: DesktopContainer): SaveFormat = when {
        name.endsWith(MobixFormat.EXTENSION, ignoreCase = true) -> SaveFormat.MOBIX
        name.endsWith(".xopp", ignoreCase = true) -> when (from) {
            // A packaged `.xopp` stays a package when it is written to another `.xopp`; a native
            // document, or a gzip one, gets the gzip form desktop Xournal++ reads everywhere.
            DesktopContainer.XOPP_ZIP -> SaveFormat.ZIPPED
            else -> SaveFormat.ORIGINAL
        }
        else -> formatOf(from)
    }

    /**
     * Write [document] as [format] into [target], atomically, and answer what a `.xopp` could not draw.
     *
     * A destination the window cannot write faithfully is **refused before anything is written**, with
     * a sentence rather than a broken file: a packaged `.xopp` carries its backgrounds *inside* the
     * package, and this window has no image or PDF store to bundle them from, so re-writing one that
     * has attachments would emit references to entries it does not write.
     *
     * This writes to a destination the document is **not** open on. Saving a document over its own file
     * has one more step on Windows — the open archive has to be released before the move — which is
     * [DesktopTab.saveBack]'s job, not this one's.
     */
    fun save(
        document: DesktopDocument,
        target: File,
        format: SaveFormat = formatOf(document.container),
    ): List<Uncompilable> = stage(document, target, format).use { staged ->
        staged.commit()
        staged.uncompiled
    }

    /** A save that has produced its bytes but has not replaced the destination yet. */
    class Staged internal constructor(
        val temp: File,
        val target: File,
        val uncompiled: List<Uncompilable>,
    ) : AutoCloseable {

        /** Move the staged file over [target] — the one step after which the document is the new one. */
        fun commit() = replace(temp, target)

        /** Drop the staged file. After a commit there is nothing left to drop. */
        override fun close() {
            temp.delete()
        }
    }

    /** Write [document] into a temp file beside [target], without touching [target]. */
    fun stage(
        document: DesktopDocument,
        target: File,
        format: SaveFormat = formatOf(document.container),
    ): Staged {
        if (format == SaveFormat.ZIPPED && hasAttachments(document)) {
            throw SaveRefused(
                "This document's background is attached inside its package, and this window cannot " +
                    "bundle a picture or a PDF it has no copy of. Save it as a .xopp or a .mobix instead.",
            )
        }
        val folder = target.absoluteFile.parentFile
            ?: throw SaveRefused("That destination has no folder to write into.")
        val temp = File.createTempFile(".${target.name}.", ".writing", folder)
        return try {
            val uncompiled = temp.outputStream().use { out ->
                DocumentEncoder.write(
                    document = document.model,
                    format = format,
                    target = DocumentEncoder.FileSaveTarget(target),
                    out = out,
                    appVersion = APP_VERSION,
                    shape = document.saveShapeFor(target),
                )
            }
            Staged(temp, target, uncompiled)
        } catch (failure: Throwable) {
            temp.delete()
            throw failure
        }
    }

    /** A save that would produce a file it could not open again, said in words the user can act on. */
    class SaveRefused(message: String) : IllegalStateException(message)

    /** Move [temp] over [target] — one rename where the filesystem allows it. */
    private fun replace(temp: File, target: File) {
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            // A share or a network drive: still one replace, just not guaranteed atomic by the OS.
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Whether any page's background is a payload the file must carry — a bundled `assets/` entry, or a
     * `domain="attach"` reference the package is expected to hold. A plain path is not an attachment:
     * the `.xopp` points at the picture beside it and the write preserves that pointer.
     */
    private fun hasAttachments(document: DesktopDocument): Boolean = document.model.pages.any { page ->
        when (val background = page.background) {
            is Background.Pdf -> background.bundledAsset != null ||
                (background.domain == ATTACH_DOMAIN && !background.filename.isNullOrEmpty())
            is Background.Pixmap -> background.bundledAsset != null ||
                (background.domain == ATTACH_DOMAIN && background.filename.isNotEmpty())
            is Background.Solid -> false
        }
    }
}

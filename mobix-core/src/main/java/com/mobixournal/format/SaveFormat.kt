package com.mobixournal.format

/**
 * How the current document is serialised to disk, and the choice that "Save As" makes sticky: once
 * you pick one, every later plain Save reuses it (see `MainActivity`).
 *
 * - [ORIGINAL] — the legacy gzip `.xopp` desktop Xournal++ writes: gzip-compressed XML, a PDF
 *   background linked by path/URI. The interchange-safe default.
 * - [ZIPPED] — the newer ZIP-package `.xopp`: a single self-contained file with the PDF embedded as
 *   an archive entry (see [XoppZip]). Portable, but see the mimetype caveat in [XoppZip].
 * - [MOBIX] — this app's own format (see `docs/mobix-format.md`): a ZIP whose `mimetype` entry
 *   declares it, holding living entities, stylus telemetry and a paged document. It shares the ZIP
 *   shape with [ZIPPED], so the type is decided by that entry and never by the suffix.
 */
enum class SaveFormat {
    /**
     * The original gzip-compressed `.xopp` format: XML wrapped in gzip, with PDF backgrounds
     * referenced by external path/URI. This is the default and most compatible format for
     * interchange with desktop Xournal++.
     */
    ORIGINAL,

    /**
     * The ZIP-package `.xopp` format: a single self-contained ZIP archive containing `content.xml`,
     * the mimetype declaration, and embedded assets (PDF backgrounds, images). More portable but
     * requires Xournal++ with ZIP support.
     */
    ZIPPED,

    /**
     * The native `.mobix` format: a ZIP archive of named parts, typed by its own `mimetype` entry
     * (`MobixFormat.MIMETYPE`), whose document is this app's model rather than a `.xopp`.
     *
     * It is the one format whose document is **not** interchangeable with desktop Xournal++ as it
     * stands — that is what the bridge is for ([SaveFormat.ORIGINAL] export of it) — so it is never
     * chosen for a document by accident: only the user's own choice or the format a document was
     * opened as puts it in play.
     */
    MOBIX,
}

# Images: import, the pixmap background, and its reference

> Read when you change image import, how a pixmap background is painted, or how its reference is resolved and stored.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Wiring an image into the open path (`render/ImageImport.kt`)

**Wiring an image into the open path (`render/ImageImport.kt`).** The `.xopp` format has exactly one
home for a picture behind a page — `<background type="pixmap">` — so an opened PNG/JPEG/WebP becomes a
**single page** carrying `Background.Pixmap(domain="absolute", filename=<source content:// URI>)` and
one empty layer. The page is sized straight from the image's pixels at the 72-dpi baseline `.xopp`
user space already uses (one pixel → one point), which keeps its aspect ratio without inventing a
physical size the file never stated. The document links the picture by location — a pixmap background
is a reference, not embedded bytes — but a copy is taken into the image store all the same
(`DocumentIo.adoptImage`), so the page keeps rendering once the staging copy is swept and the picture
can be bundled into a ZIP save. `ImageImport.pixelSize` reads the dimensions bounds-only
(`inJustDecodeBounds`), so a phone-camera photo never has to be decoded in full just to size a page;
a file that doesn't decode leaves the canvas untouched and toasts instead.

## Painting a pixmap background (`render/ImageBackgroundCache.kt`)

**Painting a pixmap background (`render/ImageBackgroundCache.kt`).** The picture itself is decoded by
the raster counterpart of `PdfPageCache`, charged to the same shared `BitmapBudget` so an image-backed
document and a PDF-backed one compete for one bound rather than two. Entries are keyed by reference
and 64 px target-width bucket and evicted **LRU**; a picture is one image per page rather than a
rasterisable page count, so there is no tiling and no prefetch ring. `request` never decodes on the
drawing thread — a miss returns the nearest cached width (or nothing) and queues the exact size on a
worker, which announces it through `onImageReady` and gets a redraw, exactly like
`PdfPageCache.onPageReady`. Decoding is two-pass: bounds first, then `inSampleSize` chosen so the
decode never lands *below* the target width (`ImageBackgroundCache.sampleSize`), then an exact scale —
so a 12-megapixel photo shown at tablet width is never materialised in full. A reference whose bytes
won't open or decode is retired, so a frame doesn't re-queue it forever.
`DrawingSurfaceView.pageBitmapFor` is the one place both caches meet: a `pdf` page asks `PdfPageCache`
and a `pixmap` page asks `ImageBackgroundCache`, and either answer rides the same `pageImage` slot
into `BackgroundRenderer.draw`.

## Resolving and storing a pixmap reference (`io/ImageStore.kt`)

**Resolving and storing a pixmap reference (`io/ImageStore.kt`, `render/PixmapBackgroundDomain.kt`).**
A `pixmap` `filename` comes in every shape a `pdf` one does, and desktop Xournal++ resolves both
through the same `getAbsoluteFilepath`, so `DocumentIo` resolves them through one `openReference`
helper: a `content://` URI, an absolute path, a relative path beside the `.xopp`, or
`domain="attach"`'s `<name>.xopp.<filename>` sibling. Whatever it can reach is **copied into
`ImageStore`** (cache dir `images/`, one never-rewritten file per copy, swept by `DocumentIo.prune`
against the pictures the live canvases decode from and additionally held to
`StorageLimits.imageCacheBytes` oldest-first, as `PdfStore` is — live copies are never evicted, so
the cap is a backstop for strays rather than the primary bound), and the copies come back on `LoadedFile.Doc` as
an `images: reference → File` map — never as a rewrite of the document. That side table is the whole
design point: the document keeps the reference it was read with, so a plain Save round-trips it
unchanged, while `DrawingSurfaceView.setImageSources` hands the map to `ImageBackgroundCache`, which
prefers the local copy and only falls back to opening the reference itself. A reference nothing could
resolve leaves that page blank and toasts (`LoadedFile.Doc.missingImage`).

Saving mirrors the PDF rules. `SaveFormat.ORIGINAL` relativises each pixmap reference against the
document's own folder where it lives there (`portablePixmapReferences`, the pixmap twin of the PDF
path) and otherwise leaves it alone. `SaveFormat.ZIPPED` bundles: `documentWithPixmapAttachments`
re-points every pixmap background at `domain="attach"`, `filename="bg-<n>.<ext>"` and hands
`XoppZip.save` the entry → file map to embed, with the extension sniffed from the picture's own bytes
(`extensionFor`) because desktop picks its image loader by suffix and a `content://` URI has none. A
background whose picture can't be reached keeps its original reference rather than becoming an attach
name with no entry behind it. `XoppZip.open` extracts every non-bookkeeping, non-`bg.pdf` entry into
the image store and returns them as `Loaded.images`, keyed by entry name — which is exactly what the
attached background's `filename` says — so a package reopens self-contained.

## Round-trip status

**Round-trip status.** `PixmapBackgroundRoundTripTest` locks in both halves: a linked reference
(absolute path, relative path, or `content://` URI) is written verbatim and the picture's bytes never
enter the file, and a bundled one survives the whole `documentWithPixmapAttachments` →
`XoppZip.save` → `XoppZip.open` path with its entry bytes and the annotations over it intact —
numbering one entry per image-backed page and leaving an unreachable picture's reference alone.

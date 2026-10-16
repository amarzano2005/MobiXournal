# The `.mobix` container and envelope specification

> Read when implementing serialization, the document envelope (manifest, canvas, pages), or backward compatibility for `.mobix`.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This file owns the **container and the document envelope**; the elements inside a page are
[mobix-elements.md](mobix-elements.md), and the motivation, the `.xopp` bridge and the app-level
policy are [mobix-format.md](mobix-format.md). The container is canonical here — do not re-draw the
tree in the other spoke.

## Container structure

`.mobix` is a ZIP archive, typed `application/vnd.mobixournal+zip`. The archive layout isolates
global metadata, per-page (or per-chunk) data, and binary assets:

```text
document.mobix
├── mimetype                     # Uncompressed ASCII: "application/vnd.mobixournal+zip"
├── manifest.json                # Document envelope & configuration (schema below)
├── canvas.json                  # Spatial geometry mode & dimensions (see infinite-canvas.md)
├── pages/                       # Paginated mode pages
│   ├── page-0001.json
│   └── page-0002.json
├── chunks/                      # Infinite mode spatial tiles (see infinite-canvas.md)
│   ├── chunk_0_0.json
│   └── chunk_-3_2.json
├── assets/                      # Binary payloads, named by the hash of their own bytes
│   ├── img_<sha256>.webp        # Embedded images (WebP/PNG/JPEG) and background pictures
│   ├── img_<sha256>.png         # Rendered LaTeX, and a bundled background PDF as img_<sha256>.pdf
│   └── aud_<sha256>.ogg         # Bundled audio, in the container it was recorded in (audio-inking-sync.md)
└── xopp-bridge/                 # Interoperability sidecar (see mobix-format.md)
    └── metadata.json            # Bridge bookkeeping: source file, synthesis hashes, timestamps
```

**Every binary is content-addressed in `assets/`**, including a page's background PDF — one rule, so a
name can never go stale and the same picture on ten pages is one entry. A fixed `background/` name was
the alternative and is wrong: a document may cite several PDFs, one per page, and a name not derived
from the content would let the last one silently replace the others.

### The rules that make it a format (not just a ZIP)

These are the parts an implementation must get right; the rest is payload. All five are enforced in
`format/mobix/MobixContainer.kt` and pinned by `MobixContainerTest`.

1. **`mimetype` is the first entry, and it is STORED.** Same reasoning as ODF: a reader that only
   wants to know what the file *is* must be able to read it from the first bytes without inflating
   anything. Every other entry is DEFLATED.
2. **A `.mobix` is told from a ZIP-package `.xopp` by that entry, not by the file name.** Both are
   ZIP archives and both begin `PK\x03\x04`, so `FileKind`'s byte sniff only reaches "it is a ZIP"
   (see [data-path.md](data-path.md)); the discriminator is the `mimetype` entry's content, and the
   `.mobix` suffix is a hint at most (a SAF `content://` URI often has no usable name). A reader
   opens the archive, reads `mimetype`, and dispatches on it. A *writer* refuses a caller-supplied
   `mimetype` entry outright: it is the container's own, and a copy could only mislabel the file.
3. **`manifest.json` is always present**, and a file without it is not a valid `.mobix` — refused on
   read and on write.
4. **Unknown entries are preserved on rewrite.** A file written by a newer app can carry entries this
   build does not understand (`chunks/`, a future `index/`); rewriting the document carries them
   through untouched, exactly like an unknown XML element in the `.xopp` path (see
   [xopp-format.md](xopp-format.md)). This is what makes "open in v2.1, save in v2.0" non-destructive.
5. **Page names are `page-%04d.json`, one-based, and the *manifest* fixes the order.** Reading a
   document never sorts file names — a page list that was reordered or spliced would come back in
   the wrong order. Names past `page-9999` keep counting (`page-10000.json`); the width is padding,
   not a limit.

---

## Manifest schema (`manifest.json`)

```json
{
  "formatVersion": 1,
  "minAppVersion": "2.0.0",
  "documentId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "canvasMode": "PAGINATED",
  "created": 1775952000000,
  "modified": 1775955600000,
  "title": "Analisi Matematica II - Appunti",
  "pages": [
    { "id": "pg_01", "entry": "pages/page-0001.json", "modified": 1775955600000, "sha256": "9f86d081…" }
  ],
  "defaultPaper": { "type": "solid", "color": "#FFFFFF00", "style": "ruled",
                    "config": "0.50,0.5,0.5,0.10,1.0,4" }
}
```

- **Required: `formatVersion`, `documentId`, `canvasMode`.** Those are the three a reader cannot
  invent — which schema this is, which document it is, and whether the parts are `pages/` or
  `chunks/`. Every other field has a documented default, so a minimal hand-written manifest opens.
- `pages[]` **is** the page order, and the bookkeeping the conflict-free merge needs
  ([sync-and-storage.md](sync-and-storage.md)): `sha256` is the digest of the page entry's *stored*
  bytes and `modified` its last-write time, so a merge can tell which side of a conflict moved. A
  page whose `entry` is absent from the archive is a corrupt file — fail the open, don't skip quietly.
- Documents in an infinite mode use `chunks[]` (same shape, entries under `chunks/`) instead of
  `pages[]`; the mode decides which list is authoritative, and the other is empty.
- `defaultPaper` is **a background object**, exactly the shape a page carries — what a new page starts
  on and what this page is on cannot drift apart. A ruling's own parameters ride verbatim in `config`,
  the comma-separated string desktop Xournal++ writes, rather than being parsed into fields and
  re-serialised into a spellings mismatch.
- Timestamps are Unix epoch milliseconds, UTC.
- **Unknown fields are preserved**: a manifest written by a newer build keeps its extra keys, values
  and nested structure across a rewrite, and they are written last, in the order they were read.

`canvas.json` — the geometry mode and its dimensions — is owned by
[infinite-canvas.md](infinite-canvas.md), which carries its schema.

---

## Page schema (`pages/page-N.json`, `chunks/chunk_x_y.json`)

```json
{
  "widthPt": 595.27559100,
  "heightPt": 841.88976400,
  "background": { "type": "solid", "color": "#FFFFFFFF", "style": "ruled",
                  "config": "0.50,0.5,0.5,0.10,1.0,4" },
  "layers": [
    { "name": "Notes", "elements": [ … ] },
    { "elements": [ … ] }
  ]
}
```

- The page's **identity is not here**: it is the `id` in the manifest's `pages[]` entry, which already
  names the file. Repeating it would give the document two places to disagree about which page this is.
- `layers[]` is z-order, first bottom, last top, and each `elements[]` is the page's own z-order —
  exactly the model's ([data-path.md](data-path.md)). `name` is optional, as in `.xopp`.
- A layer's `elements` may hold anything [mobix-elements.md](mobix-elements.md) specifies, including
  an element type this build does not model.
- `attrs` on any of these objects carries the `.xopp` attributes the bridge must not lose, the same
  channel elements use.

### Backgrounds

```json
{ "type": "solid",  "color": "#FFFFFFFF", "style": "ruled", "config": "0.50,0.5,0.5,0.10,1.0,4" }
{ "type": "pixmap", "domain": "absolute", "filename": "/home/me/scan.png", "asset": "assets/img_a1b2.png" }
{ "type": "pdf",    "domain": "absolute", "filename": "notes.pdf", "pageNo": 2,
                    "asset": "assets/img_e5f6.pdf" }
```

The `filename`/`domain`/`pageNo` reference is kept **even when the bytes are bundled**: the bundle is
what makes the file self-contained when it is shared or synced, and the reference is what the `.xopp`
bridge needs to re-emit what desktop Xournal++ expects. `asset` is optional — without the bytes the
reference stands alone, exactly as in a gzip `.xopp`. Adopting a bundled payload into the app's own
stores is the document layer's job ([data-path.md](data-path.md)).

---

## Backward and forward compatibility rules

1. **Unknown element preservation**: an element `type` this build does not model is kept whole and
   re-emitted unchanged — the rule the format is built on, and the reason a page may carry a table or
   a plot this build has no editor for ([mobix-elements.md](mobix-elements.md)).
2. **Unknown envelope fields are preserved**: the manifest, `canvas.json`, a part entry and a page have
   their unrecognised fields kept, with their values and order. Unknown *archive entries* too.
3. **Schema versioning**: `formatVersion` is incremented only for breaking structural changes. Minor
   features rely on additive fields with standard defaults. An unknown field inside a *modelled
   element* is the one thing not preserved (see the element spoke): a breaking addition is a version
   bump, not a field nobody understands.
4. **`minAppVersion` is a write-time floor, and a reader enforces it**: it is the app version that
   must be present to write this file *safely*. A reader whose own version is lower than
   `minAppVersion` refuses to open the document normally and offers a read-only open via a consent dialog
   (`ui/NewerAppConsentDialog.kt`), instead of silently dropping the entities it cannot keep. In read-only
   mode, in-canvas drawing and mutating gestures are blocked, and Save, Save As, and Export entries are
   disabled to protect document integrity; declining the prompt closes the tab and leaves nothing open.
   `formatVersion` answers "can I parse the shape of this?", `minAppVersion` answers "can I keep it
   intact?" — a higher `minAppVersion` with the same `formatVersion` is the case rule 3 alone would lose
   data on. Comparison is numeric per dotted component, and a build stamp is ignored (`1.3.0-v2` equals
   `1.3.0`).
5. **Which version writes what is recorded here**, one line per release, so a support question
   ("why won't my tablet open this file?") has an answer in the repo:

   | App version | `formatVersion` written | `minAppVersion` written |
   |---|---|---|
   | 2.0.x | 1 | `2.0.0` |

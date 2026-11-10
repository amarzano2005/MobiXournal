# The `.mobix` container and envelope specification

> Read when implementing serialization, the document envelope (manifest, canvas, pages), or backward compatibility for `.mobix`.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This file owns the **container** and the rules that follow from it; the field-by-field tables of the
envelope parts — types, requiredness, defaults, units — are [mobix-data-model.md](mobix-data-model.md)'s,
the elements inside a page are [mobix-elements.md](mobix-elements.md), and the motivation, the `.xopp`
bridge and the app-level policy are [mobix-format.md](mobix-format.md). The container is canonical here —
do not re-draw the tree in the other spoke.

The JSON below is **excerpted**: `…` marks elided content, so a block is a shape to read, not a file to
paste. Where a field's rules matter they are stated in prose under the block, not implied by the sample.

## Container structure

`.mobix` is a ZIP archive, typed `application/vnd.mobixournal+zip`. The archive layout isolates
global metadata, per-page (or per-chunk) data, and binary assets:

```text
document.mobix
├── mimetype                     # Uncompressed ASCII: "application/vnd.mobixournal+zip"
├── manifest.json                # Document envelope & configuration (schema below)
├── canvas.json                  # Spatial geometry mode & dimensions (see chunked-documents.md)
├── pages/                       # Paginated mode pages
│   ├── page-0001.json
│   └── page-0002.json
├── chunks/                      # Infinite mode spatial tiles (see chunked-documents.md)
│   ├── chunk_0_0.json
│   └── chunk_-3_2.json
├── assets/                      # Binary payloads, named by the hash of their own bytes
│   ├── img_<sha256>.webp        # Embedded images (WebP/PNG/JPEG) and background pictures
│   ├── img_<sha256>.png         # Rendered LaTeX, and a bundled background PDF as img_<sha256>.pdf
│   └── aud_<sha256>.ogg         # Bundled audio, in the container it was recorded in (audio.md)
└── xopp-bridge/                 # Interoperability sidecar (see mobix-format.md)
    └── metadata.json            # Bridge bookkeeping: source file, synthesis hashes, timestamps
```

**Every binary is content-addressed in `assets/`**, including a page's background PDF — one rule, so a
name can never go stale and the same picture on ten pages is one entry; the naming grammar, what may
reference a payload and what a missing one means are [mobix-assets.md](mobix-assets.md)'s. A fixed
`background/` name was the alternative and is wrong: a document may cite several PDFs, one per page, and
a name not derived from the content would let the last one silently replace the others.

### The container protocol, in rules a reader can be wrong about

A **reader MUST** follow these; the rest of this chapter is explanation of why each one exists. The
limits it must impose before allocating are [mobix-format.md](mobix-format.md)'s, not repeated here.

- **MUST read the type from the `mimetype` entry, not from the name or suffix.** Both a `.mobix` and a
  ZIP-package `.xopp` are ZIPs beginning `PK\x03\x04`, so a byte sniff reaches "it is a ZIP" and no
  further ([data-path.md](data-path.md)); the suffix is a hint at most, because a `content://` URI often
  has none. A file whose entry does not hold `application/vnd.mobixournal+zip` is not this format.
- **MUST NOT trust `mimetype` to be first or STORED.** A writer MUST put it first and stored, so that a
  reader asking only "what is this?" inflates nothing (the reasoning ODF uses); a reader MUST scan for
  it and inflate it anyway, because refusing a valid document written by another tool is the worse
  error. Every other entry MUST be DEFLATED by a writer.
- **MUST require `manifest.json`,** on read and on write alike: an archive without it is not a document.
- **MUST treat an entry name as an identity, not a path.** Names are `/`-separated (`pages/page-0001.json`,
  `chunks/chunk_-3_2.json`, `assets/img_<sha256>.webp`), and a reader MUST NOT normalise one: a name with
  a `..` or `.` segment, an absolute path (POSIX or `C:`), a backslash or a control character is
  **refused**, and so is a name that appears twice — two entries answering to one name make which part
  wins depend on archive order.
- **MUST take page order from `pages[]`** and tile coordinates from `chunks[]`, never from names.
  Page entries are `page-%04d.json`, one-based, and the width is padding rather than a limit: past
  `page-9999` a writer keeps counting (`page-10000.json`).
- **MUST preserve an unknown entry** through a rewrite, byte for byte. A newer writer may add `chunks/`,
  an `index/`, a field this reader has no model for; carrying it untouched is what makes "open in v2.1,
  save in v2.0" non-destructive, and it is the container's half of the retention rule in
  [mobix-elements.md](mobix-elements.md).
- **MUST distinguish absent from empty.** `mimetype` and `manifest.json` are required; `canvas.json`,
  `pages/`, `chunks/`, `assets/` and `xopp-bridge/` are optional, and a document that carries none of
  the content directories is a document with no content rather than an unreadable file.
- **MUST reject, not repair.** A corrupt container is a malformed document reported as one error with
  the underlying failure kept as its cause: an unreadable or encrypted entry, a checksum that does not
  match, a file that ends before its directory, a duplicate or unusable name, a missing `mimetype` or
  `manifest.json`. Nothing is partially read, and a rejected file is never written over.

---

The shared encoder preserves the existing manifest's creation time, default paper and unknown fields
when saving the same document identity. Modification time, title and canvas mode follow the current
write; the required app-version floor is never lowered (`ManifestSaveIntegrityTest`).

## Manifest schema (`manifest.json`)

```json
{
  "formatVersion": 1,
  "minAppVersion": "2.0.0",
  "documentId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "canvasMode": "PAGINATED",
  "created": 1775952000000,
  "modified": 1775955600000,
  "title": "Mathematical Analysis II - Notes",
  "pages": [
    { "id": "pg_01", "entry": "pages/page-0001.json", "modified": 1775955600000, "sha256": "9f86d081…" }
  ],
  "defaultPaper": { "type": "solid", "color": "#FFFFFF00", "style": "ruled",
                    "config": "0.50,0.5,0.5,0.10,1.0,4" }
}
```

The field tables, with each type, requiredness, default and unit, are
[mobix-data-model.md](mobix-data-model.md)'s. Two consequences belong here because the container is what
enforces them: a `pages[]` entry whose `entry` names nothing in the archive is a **corrupt file** — fail
the open instead of skipping quietly — and a manifest field this build does not model is preserved on
rewrite, which is compatibility rule 2 below at the envelope's level.

`canvas.json` — the geometry mode and its dimensions — is owned by
[chunked-documents.md](chunked-documents.md), which carries its schema.

---

### Declared capabilities

`formatVersion` answers "can I parse this shape?" and `minAppVersion` answers "can this app keep the
file intact?" — both in MobiXournal's own version numbers. What a file needs *understood* is declared
instead, in `requiredCapabilities` and `optionalCapabilities`: [mobix-capabilities.md](mobix-capabilities.md)
owns that contract — the identifier's grammar, the superset rule, what each list means, the four
outcomes with their precedence, and the union a merge or paste declares.

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

The page's fields and defaults are [mobix-data-model.md](mobix-data-model.md)'s; the shape above is here
because the page is what the container's entry names point at. Two things a reader will get wrong
without being told: a layer's `elements` may hold anything [mobix-elements.md](mobix-elements.md)
specifies, including a type this build has no model for, and `attrs` on any of these objects carries the
`.xopp` attributes the bridge must not lose, the same channel elements use.

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

## Publishing v1 for an outside implementer

The `.mobix` format is only open if someone who has never seen this repository can write a reader from
what is published. Three decisions make that the case, and none of them is a second copy of the format:

- **The pages above are the specification.** `mobix-schema-spec.md` (container, manifest, canvas, page),
  `mobix-elements.md` (rules and geometry), `mobix-living-entities.md` (living families),
  `mobix-format.md` (limits and the capability table) and `xopp-bridge.md` (the bridge and its losses)
  are written for an implementer, not for a contributor: a rule that an outside reader must follow is
  stated as a rule, and code names appear only where they help locate the rule's tests. A parallel
  spec document would be a fork of the one thing this project cannot afford to describe twice.
- **The machine-checkable half lives in [`spec/`](../spec/README.md).** That directory carries the
  reading order, the [conformance checklist](../spec/CONFORMANCE.md) a third party runs row by row, and
  — from `mobix-spec-schema` — JSON Schema Draft 2020-12 documents for the manifest, the canvas, a page
  and each known element family, kept honest by a drift guard. The schemas state where they stop: a
  digest of bytes in another entry, a cross-entry reference, and geometry that is the sum of fields are
  checks only code can make.
- **The artefacts are meant to be reusable.** The `spec/` documents and the golden fixture corpus are to
  be dedicated to the public domain (CC0-1.0) so an independent reader can embed them without taking on
  this application's licence; the application source stays GPL-2.0-or-later, and `NOTICE` records the
  split. `mobix-spec-licence` lands the licence files and the `NOTICE` row together, so the promise is
  never stated in prose before it is stated in the tree.

The child tasks split the remaining prose by protocol area (`mobix-spec-container`,
`mobix-spec-data-model`, `mobix-spec-elements`, `mobix-spec-assets`, `mobix-spec-capabilities`), the
artefacts by kind (`mobix-spec-schema`, `mobix-spec-conformance`, `mobix-spec-licence`), and the proof by
`mobix-spec-conformance`: a checklist row with no sample behind it is a claim, not a check.

## Backward and forward compatibility rules

1. **Unknown element preservation**: an element `type` this build does not model is kept whole and
   re-emitted unchanged — the rule the format is built on, and the reason a page may carry a table or
   a plot this build has no editor for ([mobix-elements.md](mobix-elements.md)).
2. **Unknown envelope fields are preserved**: the manifest, `canvas.json`, a part entry and a page have
   their unrecognised fields kept, with their values and order. Unknown *archive entries* too.
3. **Schema versioning**: `formatVersion` is incremented only for breaking structural changes. Minor
   features rely on additive fields with standard defaults, and a field this build did not read is
   carried in its element's `unknown` channel and written back (see the element spoke), so an additive
   field needs neither a version bump nor a refusal. A breaking change — one where the *meaning* of an
existing value changes — is still a `formatVersion` bump.
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

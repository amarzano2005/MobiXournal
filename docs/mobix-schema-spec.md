# The `.mobix` container and envelope specification

> Read when implementing serialization, the document envelope (manifest, canvas, pages), or backward compatibility for `.mobix`.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This file owns the **container and the document envelope**; the elements inside a page are
[mobix-elements.md](mobix-elements.md), and the motivation, the `.xopp` bridge and the app-level
policy are [mobix-format.md](mobix-format.md). The container is canonical here — do not re-draw the
tree in the other spoke.

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

- **Required: `formatVersion`, `documentId`, `canvasMode`.** Those are the three a reader cannot
  invent — which schema this is, which document it is, and whether the parts are `pages/` or
  `chunks/`. Every other field has a documented default, so a minimal hand-written manifest opens.
- `pages[]` **is** the page order, and the bookkeeping a merge needs
  ([mobix-format.md](mobix-format.md) → *Merging two copies is library code*): `sha256` is the digest of the page entry's *stored*
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
[chunked-documents.md](chunked-documents.md), which carries its schema.

---

### Declared capabilities

`formatVersion` answers "can I parse this shape?" and `minAppVersion` answers "can this app keep the
file intact?" — both in MobiXournal's own version numbers. The third question has no home unless the file
answers it: *which features must a reader understand to keep this document?* An independent writer
cannot express that in app versions, and bumping `formatVersion` for a feature no v1 reader needs to
know about would break every v1 reader for no structural reason. The manifest therefore declares
capabilities.

```json
{
  "requiredCapabilities": ["org.mobixournal/stem.connector:1"],
  "optionalCapabilities": ["org.mobixournal/paper.isometric:2"]
}
```

- **The identifier** is `<namespace>/<feature>:<version>`: a reverse-DNS namespace the declaring writer
owns, a lower-case dot-or-dash feature name, and a positive integer version. The namespace keeps two
writers from colliding on one word, and is not a URL — nothing is fetched.
- **A version promises the shape, and a later version is a superset** of the one before it for the same
feature. A reader that handles `…:2` therefore satisfies `…:1`; a reader handling only `…:1` does not
satisfy `…:2`. Comparison never crosses two feature names.
- **Both lists are additive and optional in the file**: absent means empty, and their order is kept as
written, because a writer's own priority is not this format's to sort.
- **`requiredCapabilities` is a write floor**: a reader that does not satisfy one may not write the
document. **`optionalCapabilities` is a display hint**: not implementing one is allowed, and the construct
it names must then be preserved verbatim rather than edited.

**Who validates.** The manifest codec checks the grammar (identifier shape, positive version, no
duplicates) and preserves both lists in order; it decides nothing about the running app. The reader gate
at open compares them with the capabilities this build declares and produces one verdict, which the UI
only presents. A malformed identifier is a corrupt manifest and fails the open, like a malformed
`canvasMode`.

**The four outcomes**, in precedence order — the first that applies wins:

| Outcome | When | What the document allows |
|---|---|---|
| **refuse-write** | a required capability this build cannot satisfy *and* cannot preserve, so its elements would be lost | the pages may be looked at, and nothing may be written back |
| **read-only display** | `minAppVersion` above this build, or a required capability whose constructs are all displayable but not representable | every page shown; edits, Save and Save As disabled |
| **preserved-but-not-drawn** | an optional capability this build does not implement but can keep whole | editing elsewhere allowed; the affected elements carried through untouched |
| **full edit** | every required capability satisfied and no optional construct affected | edited and saved normally |

Two precedence rules are stated because they are easy to get backwards: an **unknown required
capability outranks a permissive `minAppVersion`** (the file says it needs something, so a version number
cannot talk it out of that), and **`minAppVersion` outranks the optional list** (a reader that cannot keep
the document intact at all cannot honestly claim to preserve the parts it does not draw). Among several
unknown required capabilities the strictest outcome wins; refusing to write is never weakened by another
capability being understood.

`preserved-but-not-drawn` depends on the retention rule in [mobix-elements.md](mobix-elements.md) — a
build that drops a field it did not read cannot claim to have preserved an optional construct. That rule
holds for every family the format models: an unread field on a known type is carried, and an element
type with no model at all is kept whole, so a construct this build cannot draw can still be handed back
untouched. A capability whose construct has **no model and no preservation channel** — one the reader
would have to interpret to keep — is the case that stays **read-only display**.

`minAppVersion` stays the legacy MobiXournal guard, still enforced and still compared numerically per
dotted component. **Editability backwards is not promised**: a newer file opened by an older build may be
shown read-only or preserved verbatim, never silently trimmed.

**Per-type versioning.** An element type does not carry its own `schemaVersion`. Two version numbers for
one shape are two answers to one question, and the capability list already says which shapes a reader
must understand; a legacy type with no declared capability is a v1 type by definition. `formatVersion`
remains the structural gate, and a change no additive field can express still bumps it.

**Cross-cutting.** A merge result declares the union of both sides' required and optional sets. A copy or
paste into another document raises the destination's required set to include the capabilities of the
elements it now holds, or is refused: a document that cannot keep an element must not accept it and then
trim it.

The manifest half is implemented (`MobixCapability`, and the two lists in `MobixManifest`), and the
gate's decision lives in `MobixCapabilities.evaluate`; fixtures 13-17 in the golden corpus are one file
per outcome and are driven by `MobixCapabilityFixturesTest`. Still to build: the gate's shell wiring and
notice (`mobix-capability-reader-gate`, `mobix-capability-warning-surface`) and the merge/paste union
(`mobix-capability-merge-copy`).

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

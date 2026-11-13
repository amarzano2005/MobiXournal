# `.mobix` assets

> Read when you read or write an `assets/` entry — a picture, a bundled PDF or a recording — or when one
> is named and missing and you have to decide whether that is an empty element or a corrupt document.

Every binary a `.mobix` carries lives in `assets/` and nowhere else: a JSON part names one, and the
archive holds the bytes under a name it derives from them. That is the whole design, and it buys three
things at once — the same picture referenced twice is stored once, a name cannot go stale, and a file
that **bundles** its payloads travels without the folders it came from. (Not every payload is bundled:
the next two sections say which ones may stay a reference, and *Is it inside the file?* names the
consequence.) The container layout is
[mobix-schema-spec.md](mobix-schema-spec.md)'s; this chapter is the `assets/` contract an outside reader
or writer implements.

## The name is the content

An entry is named `assets/<prefix>_<sha256>.<suffix>`:

| Part | What it is |
|---|---|
| `prefix` | the kind, and the only thing that tells the two apart: `img` for a picture, `aud` for a recording |
| `sha256` | the **lowercase** hex digest, 64 characters, of the bytes *as they are stored in the entry* |
| `suffix` | the container the bytes arrived in, letters and digits only, no dot (`webp`, `png`, `jpeg`, `pdf`, `ogg`, `wav`, `m4a`) |

Two consequences follow, and both are rules rather than preferences:

- **The digest is taken over the stored payload, not over the decoded picture or the audio.** A reader
  verifies an entry by hashing what the archive gives it and comparing with the name; that check is only
  possible because the name is the payload's own hash, and it is why a payload edited or damaged after
  the write is refused instead of drawn.
- **Nothing is transcoded on the way in, so the suffix tells the truth.** A PNG stays a PNG and a WAV
  stays a WAV: re-encoding the user's picture to save bytes would change the pixels they pasted. A
  recording keeps the codec it was made in — Ogg/Opus is the default in a `.mobix`, while a document
  whose format was `.xopp` when the recording started bundles its WAV ([audio.md](audio.md)).

Deduplication is not a separate feature with bookkeeping of its own: two payloads with the same bytes
have the same digest, so they have the same name and there is one entry. Fixture 03 in the golden corpus
is the worked case — one picture behind an `image` and a `texImage` on the first page and another
`image` on the second, and one entry in the archive for all three.

The default suffix per kind lives in the code's mirror of this vocabulary (`MobixFormat.assetEntry`,
`MobixFormat.AssetKind`), which also enforces the grammar: 64 lowercase hex characters after the prefix,
then a dot and a non-empty suffix of letters and digits.

## What may point at a payload

| Part | Field | Optional |
|---|---|---|
| `image` element | `asset` | no — a picture with no bytes is not a picture |
| `texImage` element | `asset` | yes — the LaTeX alone is still a formula |
| `pixmap` / `pdf` background | `asset` | yes — the file reference stands alone |
| `stroke` recording | `audioRef.assetId` | yes — a stroke may carry no audio, or name it only as a sidecar file |

A background keeps `filename`/`domain`/`pageNo` **even when its bytes are bundled**, and the `.xopp`
bridge re-emits that reference: the bundled entry and the file reference are two names for one picture,
and renaming one never renames the other. A recording has the same two names — the entry under its hash
and the sidecar file the bridge writes when the document leaves as `.xopp` ([audio.md](audio.md)).

An element may also name an asset through its `.xopp` `fn` attribute, the shape this app wrote before it
kept the two apart; a name in `assets/` shape is a bundled entry whichever field carries it.

## A reader's obligations

- **Resolve the exact entry name.** No suffix guessing and no normalisation: a name the archive does not
  hold is not there, and "the same hash in another container" is a different entry.
- **Verify the digest before the bytes are used**, as part of reading: an entry's name is a claim about
  its content, and a payload that does not hash to it is a corrupt document.
- **A named payload the archive does not carry is a corrupt document** — one error naming the failing
  entry, and no element built out of nothing. A blank picture is the one answer that is wrong, because
  it turns a damaged file into a document the user believes is intact.
- **An absent `asset` field is not that case.** A `texImage` or a background may reference a file without
  bundling it; that is a reference with no bytes to resolve, which the field tables allow, and it is a
  different thing from a name whose entry is missing.
- The size budget imposed before anything is allocated is [mobix-format.md](mobix-format.md)'s.

## A writer's obligations

- **Name an entry from the hash of the bytes you wrote**, never from a counter, a timestamp or the file
  it came from: two writers producing the same picture then converge on the same entry without
  coordinating, and no name can go stale.
- **Write an entry for every payload a part names.** A name no entry satisfies is a corrupt document, so
  a writer that cannot supply the bytes reports that to the user instead of writing the reference anyway.
- **Carry forward what you did not write.** A Save As builds a fresh archive from the model, which keeps
  the names it was read with, so the rewrite streams the still-referenced entries out of the original
  (`carriedPayloads`) rather than orphaning them. An entry this build has no model for at all is kept by
  the container's own preserve-unknown rule ([mobix-schema-spec.md](mobix-schema-spec.md)).

## Is it inside the file?

A `.mobix` may legally reference bytes it does not carry — a background's `filename`, a recording's `fn`
sidecar — so "is this file complete?" is a question about each reference, not about the file.
`PortabilityAudit` answers it for an opened document: it walks the pages through the page surface, one
at a time and each page once, and classifies every reference a background or an element makes.

| Status | Meaning | Raised by |
|---|---|---|
| `EMBEDDED` | the bytes are inside the document | a bundled `assets/` entry present in the archive; a picture element's own bytes |
| `REFERENCED_ABSENT` | a legal reference with no bytes in the archive | a `filename` with no `asset`, a stroke or text box's `fn` sidecar |
| `UNRECOVERABLE` | no caller can make these bytes part of the document | an `assets/` name whose entry is gone (corrupt); a reference that leaves the document's folder — an absolute path, a URI, a `..` segment |

The audit **opens no payload**: presence is answered from the archive's central directory, because a
report about a file must not inflate the file it is reporting on. That is also its limit, stated rather
than hidden — a name in `assets/` shape is trusted to claim its digest here, so damaged bytes read as
`EMBEDDED`; verifying them is the separate, off-line validator's question. An element family this build
preserves verbatim is opaque, so a reference inside one is not classified.

Each entry carries the kind of payload (`image`, `texImage`, `pixmap`, `pdf`, `audio`), the reference as
the document writes it, and the page and element it was found on; a stable key per reference lets a
caller group by reference instead of by page. A font name is **not** a reference: nothing in a `.mobix`
carries font bytes, so `TextElement.font` is a rendering hint ([mobix-elements.md](mobix-elements.md)),
and `FONT` exists in the vocabulary only for the day a face becomes a payload kind.

## A validator, not only reader behaviour

A reader enforces these rules on the bytes it happens to touch, so a damaged payload nobody has drawn
looks fine. `MobixAssetValidator` asks the same rules of the whole archive, without opening a document:

| Rule id | Question | Severity |
|---|---|---|
| `NAME_GRAMMAR` | is the entry `assets/<prefix>_<64 lowercase hex>.<suffix>`? | error |
| `UNKNOWN_PREFIX` | does the prefix name a kind the vocabulary defines? | error |
| `DIGEST_MISMATCH` | do the bytes hash to the digest their name claims? | error |
| `MISSING_ENTRY` | does every name a part references have an entry? | error |
| `ORPHAN_ENTRY` | is there an entry no part names? | warning — legal under the preserve-unknown rule |
| `CONTAINER_LIMIT` | did a size or count cap stop the walk? | error, and the limits travel with the answer |
| `UNREADABLE_PAGE` | could a page be decoded at all? | error |

An entry that breaks a grammar or digest rule is not reported as an orphan as well: one fault, one
finding. A picture element's entry is matched **by digest** rather than by name, because the model keeps
the bytes and not the container they arrived in — a payload written under a different suffix is still
that picture. The validator writes nothing, and its signatures take an entry source or a `File`, so the
independent CLI reuses this code instead of a second implementation of the same rules.

## A portable copy

When the bytes are still reachable, the file can be made portable: `PortableCopy` writes a copy that
embeds every absent payload it can read — a background's file, a recording's sidecar — and leaves the
ones it cannot as references. Three properties make it trustworthy rather than merely written:

- it produces a **normal `.mobix` container**, never a second container type: the embedded bytes are
  ordinary `assets/` entries, so anything that opens a notebook opens the copy — only the extension is
  its own (`.pmobix`), because the file is an **export and never a document to keep editing**;
- it **re-opens what it wrote** and verifies that every entry hashes to the name it carries, deleting a
  copy that fails — a broken "portable" file is worse than none;
- it **says what it could not do**: the result lists what was embedded and what is still only a
  reference, so a copy never claims a portability it does not have.

Nothing is downloaded, and the document it read is untouched. Both shells reach it as the `.pmobix`
answer of the card the menu's **Export As** chip raises, beside **PNG** and **SVG**. Each asks for the
destination where it asks for every save, resolves a reference the way its own renderer does — the
document's folder for a background, the audio store for a recording — and leaves the open tab on its own
file. The tablet's copy is written to a cache file and staged onto the picked URI afterwards, so a failed
write leaves nothing half-made in the user's folder.

Because the container is a `.mobix`, the suffix is an alias at the door rather than a third format: a
portable copy opens with the same readers as the notebook it came from — the desktop Open row lists
`*.pmobix`, and Android's extension fallback registers the suffix for a tap — because the read dispatch
is on the archive's `mimetype`, never on the name ([data-path.md](data-path.md)). **Save As** still
offers only the two containers a document lives in.

## Where the rule is checked

`MobixContainerTest` holds the missing-payload case at page level: a page whose `image` names an entry the
archive does not carry comes back as the container's malformed-document error rather than as a blank
element. Fixture 03 is the positive case, and `MobixV1FixturesTest` checks that its names are the hashes
of their own bytes and that they survive a save. `DesktopFilePickerTest` keeps `*.pmobix` in the window's
open row, `ManifestIntentFilterTest` keeps its Android registration and `InstallerFileAssociationsTest`
the Windows installer's, so the copy can always be opened again.

# The `.mobix` command-line reader

> Read when you inspect or script a `.mobix` outside the app, or change `tools/mobix-cli`.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

`tools/mobix-cli/` is a **second, independent reader** of the published format: Python 3, the
standard library only, and no import of `com.mobixournal.*`. It is not a second engine — it reads the
envelope, validates it and lists what the archive holds, while the elements inside a page stay the
app's business.
That independence is the point: when this reader and `mobix-core` disagree about a file, the
disagreement is a finding about the format or about one of the two implementations, not a bug they
share because they share code.

It is not part of the Android or desktop build, so it needs no JDK, Gradle, SDK or device.

## What the commands answer

```sh
cd tools/mobix-cli
python3 -m mobix_cli inspect   FILE [--json]
python3 -m mobix_cli list      FILE [--json]
python3 -m mobix_cli validate  FILE [--json]
python3 -m mobix_cli extract   FILE --dest DIR [--entry NAME ... | --all] [--force] [--json]
python3 -m mobix_cli export-json FILE
pipx install ./tools/mobix-cli     # the same commands as `mobix-cli`
```

`inspect` answers **what document is this**: the type from the `mimetype` entry's bytes, the format
version, the document id, the creator when the `.xopp` bridge sidecar records one, the canvas mode
and page size (the sheet's size, the first page's own size, or a chunk's tile), how many pages or
chunks it carries, how many archive entries and bytes it holds, and the two declared capability
lists. It also **checks** what it reports: every part is read, compared with the digest the manifest
records for it and parsed within the 64-level JSON cap, and every asset is hashed against the digest
its own name claims. The `integrity` block says how many of each were actually inspected, so the
report cannot imply more than it did.

`list` answers **what is in the archive**: pages and chunks in manifest order, each with its entry
name, size and the digest the manifest records, and assets by name with their size and the digest
their name claims. It reads the central directory and the envelope only — no page and no payload is
inflated — so a digest there is the file's claim, not a verification. `inspect` is the command that
checks; `list` is the cheap listing a script can run over a folder.

`validate` answers **is this document legal**: it walks the conformance rules and reports every one
it finds broken instead of stopping at the first, because a file can fail three rules and the person
fixing it wants all three. Each finding names the **row of the conformance checklist** it decides and
the rule's own name (`row-17 CONTAINER_LIMIT`, `row-14 DIGEST_MISMATCH`, `row-04 PART_DIGEST`), so
the output, [`spec/CONFORMANCE.md`](../spec/CONFORMANCE.md) and the chapters behind it can be read
side by side. It checks the container (type, required entries, names, caps, encryption, compression
method), the envelope (required fields, part lists and modes, `canvas.json` agreement), every part's
recorded digest, every asset's name and digest, every `asset`/`assetId` reference, and the JSON depth
cap. When the optional `jsonschema` package is installed it also validates the manifest, canvas, pages
and chunks against `spec/schema/`; the report says `schema: checked` or `schema: unavailable`, so a
clean result never implies a schema pass that did not run.

`extract` answers **put these entries on disk, and nowhere else**. The name written is the archive's
own entry name, never a name read out of the document's JSON; the resolved path has to stay under
`--dest`, including through a symbolic link, and a target that already exists needs `--force`. An entry
is hashed against the digest the document records for it before anything appears, the write lands
through a temporary sibling file, and the caps are the format's own — 256 MiB per entry and 1 GiB in
total. `--entry` is repeatable; `--all` takes every file entry.

`export-json` answers **what does this document contain, in a form I can diff**: the manifest and
canvas as carried, a per-page summary with its size and its elements' ids and families, and the
assets by name, size, digest and kind. Layers and elements keep document order, assets are sorted by
name, and nothing is decoded — a picture is not rasterised and a recording is not played. Two copies
of one unchanged document therefore print byte-identical dumps, which is what makes the command
useful in a review.

`--json` prints one object and nothing else, so a caller never parses prose. Without it the same
fields are laid out for a person (`export-json` is always the JSON dump).

## The three exit codes

| Code | Meaning |
|---|---|
| 0 | the archive is a document this reader accepts, or `validate` found nothing |
| 1 | a document it **must refuse** (the reason on stderr), or `validate` reported findings |
| 2 | a path it **cannot open** as an archive, with the reason on stderr |

The split is deliberate: "this is not a ZIP" and "this is a valid ZIP that is not a valid `.mobix`"
are different answers, and a script that sweeps a folder needs to tell them apart. A refusal names
the entry and the rule it broke; a `validate` finding names its row, severity and location.

## What it refuses, and what it leaves to the app

The rules are the format's own, not this tool's opinions; each has one authoritative home and the
code follows it rather than restating it.

- **The container**: the type is read from `mimetype`'s bytes (never from the suffix); `manifest.json`
  is required; duplicate, `.`/`..`-segment, absolute, backslashed and control-character entry names
  are refused rather than normalised; the caps are 20 000 entries, 256 MiB per entry, 1 GiB of payload
  and 1000× expansion ([mobix-schema-spec.md](mobix-schema-spec.md),
  [mobix-format.md](mobix-format.md)).
- **The envelope**: `formatVersion`, `documentId` and `canvasMode` are required, an unknown format
  version or canvas mode is refused, `pages` and `chunks` are exclusive with each other and with the
  mode, a part naming an entry the archive lacks is refused, `canvas.json` may not disagree with the
  manifest, and a JSON part may not nest past the depth cap
  ([mobix-data-model.md](mobix-data-model.md)).
- **Capabilities**: an identifier that does not parse, or that appears twice in one list, is a corrupt
  declaration ([mobix-capabilities.md](mobix-capabilities.md)). A *well-formed* required capability
  this reader does not implement is **reported, not refused**: the contract's outcome there is
  refuse-write, and this tool never writes.
- **Assets**: an entry under `assets/` must be named by the digest of its bytes
  ([mobix-assets.md](mobix-assets.md)); `inspect` also refuses bytes that do not hash to that name.
- **`minAppVersion`** is not a refusal: a higher floor means an older *app* may not save the file, and
  a read-only inspection is exactly what this tool does.

What it does **not** do: decode elements, render anything, verify geometry that is the sum of other
fields, follow a reference from one element to another, or write a file. Those are the rows of
[the conformance checklist](../spec/CONFORMANCE.md) that stay a full reader's job; the rows this
tool's own tests decide are a subset, and the checklist says which.

## Where it lives and how it is tested

`mobix_cli/container.py` is the single boundary that touches `zipfile`; `jsonbytes.py` holds the
depth-capped decoder; `validate.py` is the rule engine and the optional schema layer; `extract.py`
owns path safety and the atomic write; `export.py` builds the stable dump; `views.py` builds the
`inspect`/`list` objects and the human reports; `cli.py` is argparse and the exit codes.
`pyproject.toml` makes it installable, and `README.md` is the quickstart.

```sh
python3 -m unittest discover tools/mobix-cli/tests
```

No network and no dependency outside the standard library. The suite reads the committed golden and
hostile corpora under `mobix-core/src/test/resources/fixtures/`, plus archives it builds in a
temporary directory so each envelope refusal has a sample of its own. It also runs
`python3 -m mobix_cli` once from the package directory, because that is how the tool is started from
a checkout.

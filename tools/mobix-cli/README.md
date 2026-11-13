# mobix-cli

A reader and a minimal writer for `.mobix` notebooks, written in Python with the standard library only.
It lives outside the application's code on purpose: it is a second implementation of the published
format, so a disagreement with the app is a finding rather than a shared bug. It does not import
`com.mobixournal.*`, does not read the application's sources, and never touches the network.

## Install

Python 3.9 or newer, nothing else. Either install the command:

```sh
pipx install ./tools/mobix-cli    # or: pip install --user ./tools/mobix-cli
mobix-cli inspect notebook.mobix
```

or run the module from its own directory, with no install at all:

```sh
cd tools/mobix-cli
python3 -m mobix_cli inspect ../../mobix-core/src/test/resources/fixtures/mobix-v1/01-minimal-single-page.mobix
```

## Commands

- `inspect FILE [--json]` — type, format version, document id, creator (when the `.xopp` bridge
  sidecar records one), canvas mode and page size, page/chunk count, entry count and bytes, declared
  capabilities. It also verifies every part's recorded digest and every asset's own name, and parses
  each part within the format's 64-level JSON cap.
- `list FILE [--json]` — pages and chunks in manifest order, and assets by name, with size and digest.
  It reads the archive's central directory only: no part and no asset is inflated, so a digest is
  reported as the file claims it, not verified. `inspect` is the command that checks.
- `validate FILE [--json]` — every conformance rule, one finding per line with the checklist row,
  severity and location. It also validates against `spec/schema/` when the optional `jsonschema`
  package is installed, and says so in the report.
- `extract FILE --dest DIR [--entry NAME … | --all] [--force] [--json]` — write chosen entries under
  a destination, refusing a name that could escape it (including through a symlink) and verifying
  each recorded digest before the file appears.
- `export-json FILE` — one stable dump (manifest, canvas, page element ids and families, assets by
  name and size) so two documents can be diffed.
- `new OUT --title T [--force] [--json]` — a one-page document with one stroke, one image and its
  bundled PNG. It produces exactly those two families and says which ones it only preserves.
- `retitle FILE --title T [--out FILE] [--force] [--json]` — changes `manifest.json` and copies every
  other entry byte for byte, including entries and keys this tool has no model for.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | the archive is a readable document |
| 1 | a document this reader must refuse (not this type, no manifest, an unsafe entry name, a cap exceeded, a corrupt part, a digest that does not match) |
| 2 | a path that cannot be opened as an archive (missing, unreadable, not a ZIP) |

The reason is printed on stderr, naming the entry and the rule. `validate` answers 1 for a report with
findings, so a script can use the exit code without parsing prose.

## The `--json` shapes

`--json` prints exactly one object to stdout; `export-json` is always the JSON dump. The keys are stable
and additive, so a script may ignore what it does not know:

- **`inspect`** — `path`, `mimetype`, `formatVersion`, `documentId`, `creator`, `canvasMode`, `pages`,
  `chunks`, `entries`, `fileBytes`, `payloadBytes`, `canvas`, `pageSize`, `requiredCapabilities`,
  `optionalCapabilities`, `integrity` (what was verified).
- **`list`** — `path`, `mimetype`, `formatVersion`, `documentId`, `canvasMode`, `pages` and `chunks`
  (`index`, `id`, `entry`, `size`, `digest`), `assets` (`name`, `size`, `digest`).
- **`validate`** — `path`, `ok`, `entries`, `schema` (`checked`, `unavailable` or `not-found`),
  `findings` (`rule` such as `row-17`, `name`, `severity`, `location`, `message`).
- **`extract`** — `path`, `dest`, `written` (`entry`, `file`, `bytes`, `digest`).
- **`new`/`retitle`** — `path`, `title`, `produced`, `preserved`, `rewritten`, `unknownEntries`,
  `assets`.
- **`export-json`** — `format` (`mobix-document/1`), `manifest`, `canvas`, the envelope fields, `pages`
  (each with `size` and its elements' `layer`, `id`, `family`) and `assets` (`name`, `size`, `digest`,
  `kind`).

## What it does not do

No rendering: it never rasterises a page, an image or a recording, and `export-json` names assets
instead of decoding them. No STEM semantics: a plot, table, circuit or formula is reported by family,
never evaluated. No repair and no conversion: it does not rewrite a broken archive, and `retitle`
changes one field rather than converting a `.xopp` or a newer format version. `extract` never downloads
anything — it opens the local archive it was given and writes only under `--dest`.

## Stack, licence and why

**Python 3, standard library only.** The point of this tool is independence: a Kotlin or Gradle import
would let one compiler and one classpath hide a disagreement, and the format's rules are already
published as prose and schemas. Python is the shortest path to a second reader that runs on a machine
with no JDK, which is also why CI's `cli` job installs nothing else. Rust is the project default for a
web server; if this tool ever grows a server surface it would be written in Rust as *another*
implementation, not as a rewrite of this one.

**Licence split.** The tool's source is **GPL-2.0-or-later**, like the rest of this repository. The
specification under `spec/` and the golden fixture corpus stay **CC0-1.0**, so a third party may
implement from the spec — and check against the specimens — without touching this code. `NOTICE`
records the split.

## Tests

```sh
python3 -m unittest discover tools/mobix-cli/tests
python3 scripts/check-mobix-cli.py    # CI's cli job: corpora, differential, writer round-trip
```

The suite reads the committed golden and hostile corpora under
`mobix-core/src/test/resources/fixtures/`, plus small archives it builds in a temporary directory.
The differential is the part that keeps two implementations honest: `spec/corpus-verdicts.csv` records
what `mobix-core` decides for every corpus file, `CorpusVerdictsTest` keeps that table equal to the
Kotlin code, and the script fails when this reader disagrees.

The rules this tool enforces are the application's own, and their authoritative homes are
`docs/mobix-schema-spec.md`, `docs/mobix-data-model.md`, `docs/mobix-capabilities.md`,
`docs/mobix-assets.md` and `docs/mobix-format.md`; the outside reader's checklist is
`spec/CONFORMANCE.md`.

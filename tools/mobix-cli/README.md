# mobix-cli

A read-only reader for `.mobix` notebooks, written in Python with the standard library only. It
lives outside the application's code on purpose: it is a second implementation of the published
format, so a disagreement with the app is a finding rather than a shared bug.

```sh
cd tools/mobix-cli
python3 -m mobix_cli inspect ../../mobix-core/src/test/resources/fixtures/mobix-v1/01-minimal-single-page.mobix
python3 -m mobix_cli list    FILE --json
pipx install .            # or: pipx install ./tools/mobix-cli
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

The reason is printed on stderr, naming the entry and the rule.

## Tests

```sh
python3 -m unittest discover tools/mobix-cli/tests
```

No network and no dependency outside the standard library. The suite reads the committed golden and
hostile corpora under `mobix-core/src/test/resources/fixtures/`, plus small archives it builds in a
temporary directory for the envelope refusals.

The rules this tool enforces are the application's own, and their authoritative homes are
`docs/mobix-schema-spec.md`, `docs/mobix-data-model.md`, `docs/mobix-capabilities.md`,
`docs/mobix-assets.md` and `docs/mobix-format.md`; the outside reader's checklist is
`spec/CONFORMANCE.md`.

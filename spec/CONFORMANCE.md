# `.mobix` v1 conformance checklist

> Read when you want to prove a reader or writer you built conforms, row by row, instead of trusting a
> successful open on one file.

Each row is a rule an implementation can be wrong about while still producing something that looks like
a document. **The smallest sample** is the document to run the row against — the committed corpus where
one exists, otherwise the test file that builds the case, so the sample is still a file you can read.
**Expected** is what a conforming implementation does with that document: accept it, or refuse it for
the reason named. A refusal for a different reason is a failure of the row, not a pass.

## How to run it

1. **Use the corpus, not a file of your own.** `mobix-core/src/test/resources/fixtures/mobix-v1/` holds
   eighteen documents, one per compatibility construct; `mobix-core/src/test/resources/fixtures/
   mobix-hostile/` holds nine, one per threat. Read the corpus READMEs for what each file is.
2. **Open the row's sample and assert the expected outcome.** For a rejected row, assert the rule the
   message names, not merely that something failed.
3. **For an accepted row, save it to a new file and reopen that.** Compare what the row's chapter says
   must survive: an unknown entry byte for byte, an unknown field with its value and order, a construct
   the capability table marks preserved. The corpus shows the case; the save-and-reopen is what proves
   your writer did not lose it.
4. **Validate the envelope first if you can** (row 38): the schemas in [`schema/`](schema/) answer the
   field-level rows mechanically with a stock Draft 2020-12 validator, so a failure there is found before
   any code runs.
5. **Finish on a file you produced yourself** — one document written by your own implementation, run
   through every accepted row. A reader that only handles this repository's bytes has been tested against
   one writer, and that is the writer it was built to agree with.

The rows that a schema cannot answer are the ones whose sample is a test file: a digest of bytes kept in
another entry, a reference from one entry to another, and geometry that is the sum of other fields.

| # | A conforming reader must | The smallest sample | Expected | Rule stated in |
|---|---|---|---|---|
| 1 | identify the type from the `mimetype` entry's bytes, not from the file name or suffix | `MobixContainerTest.kt` (a ZIP-package `.xopp`) | rejected — not this format | `mobix-schema-spec.md`, container protocol |
| 2 | refuse an archive whose `mimetype` is not `application/vnd.mobixournal+zip` | `MobixContainerTest.kt` (a ZIP declaring no type) | rejected — declares no type | container protocol |
| 3 | refuse an archive without `manifest.json` | `MobixContainerTest.kt` (mimetype only) | rejected — no manifest | container protocol |
| 4 | read `pages[]`/`chunks[]` in manifest order, never by entry name | `02-multi-page-order.mobix` | accepted, pages in manifest order | `mobix-schema-spec.md`, manifest |
| 5 | take a part's identity from `id`, and its bytes from `entry` | `02-multi-page-order.mobix` | accepted, ids kept across a save | manifest |
| 6 | fill a documented default for an absent optional field, and refuse a missing required one | `01-minimal-single-page.mobix`; `MobixManifestTest.kt` (a copy missing `documentId`) | accepted with defaults; rejected without a required field | manifest, canvas |
| 7 | accept a document that carries neither `pages` nor `chunks` as having no content | `01-minimal-single-page.mobix` | accepted, one empty page | manifest |
| 8 | keep an unknown manifest, page, part or `canvas.json` field, value and order included | `06-unknown-envelope-fields.mobix` | accepted, unknown fields unchanged | compatibility rule 2 |
| 9 | keep an unknown archive entry byte for byte on a rewrite | `05-unknown-entry.mobix` | accepted, both entries identical after a save | container rule 4 |
| 10 | preserve an element `type` it has no model for, whole, and write it back | `07-unknown-element-type.mobix` | accepted, element preserved | `mobix-elements.md`, rule 1 |
| 11 | preserve a field of a known type it did not read, at the level it was written | `08-unknown-top-level-field.mobix`, `09-unknown-nested-fields.mobix` | accepted, every extra key back in place | `mobix-elements.md`, retention rule |
| 12 | refuse a value it would misread, and keep the element whole rather than the page failing | `11-unknown-vocabulary-values.mobix` | accepted, three elements preserved | `mobix-elements.md`, vocabulary |
| 13 | draw an unknown background as a plain sheet and write the object back unchanged | `12-unknown-background-type.mobix` | accepted, `gradient` written back with its `type` | `mobix-format.md`, capability table |
| 14 | resolve an `asset` by the entry named, and treat a missing one as a corrupt document rather than a blank element | `03-bundled-assets.mobix`; `MobixContainerTest.kt` (a page naming an entry it lacks) | accepted; rejected — asset missing | `mobix-assets.md` |
| 15 | read a chunked document as tiles at their own coordinates, without inventing pages | `10-chunked-document.mobix` | accepted, only the occupied tiles | `chunked-documents.md` |
| 16 | compare declared capabilities as the contract states, and refuse to write a file whose required set it does not satisfy | `13-required-capability-unknown.mobix`, `15-capability-higher-version.mobix`, `14-optional-capability-unknown.mobix` | refuse-write; refuse-write; accepted, preserved | `mobix-capabilities.md` |
| 17 | impose the limits `mobix-format.md` states before allocating from a hostile file | `mobix-hostile/01-too-many-entries.mobix`, `02-deflate-bomb.mobix`, `03-deep-nesting.mobix` | rejected, each for its own limit | `mobix-format.md`, what a reader must refuse |
| 18 | refuse a duplicate, `..`/`.`-segment, absolute, backslashed or control-character entry name rather than normalising it | `mobix-hostile/04-duplicate-entry.mobix`, `05-traversal-entry.mobix`; `MobixContainerTest.kt` (a dot segment) | rejected | container protocol |
| 19 | treat `mimetype` and `manifest.json` as required and every other entry as optional, distinguishing absent from empty | `01-minimal-single-page.mobix`; `MobixContainerTest.kt` (mimetype only) | accepted; rejected | container protocol |
| 20 | report a corrupt container as one malformed-document error carrying the underlying failure as its cause, never a partial read | `mobix-hostile/07-encrypted-entry.mobix`, `08-truncated-directory.mobix`, `06-wrong-digest.mobix` | rejected, one error with a cause | container protocol |
| 21 | require `formatVersion`, `documentId` and `canvasMode`, and fill the documented default for every other manifest field | `01-minimal-single-page.mobix`; `MobixManifestTest.kt` | accepted with defaults; rejected without a required field | `mobix-data-model.md`, manifest |
| 22 | read page order from `pages[]`, tile set from `chunks[]`, and treat the two lists as exclusive with `canvasMode` | `02-multi-page-order.mobix`, `10-chunked-document.mobix`; `MobixManifestTest.kt` (both lists) | accepted; rejected — both lists | data model, cross-field rule 3 |
| 23 | read lengths as points, times as UTC epoch milliseconds, colours as `#RRGGBBAA`, and a non-positive `chunkSizePt` as its default | `01-minimal-single-page.mobix`, `10-chunked-document.mobix` | accepted, units read as stated | data model, units |
| 24 | model a `stroke` with its tool, its style, its per-point widths and its telemetry, and refuse one whose tool or style has no name here | `11-unknown-vocabulary-values.mobix` (`FOUNTAIN_PEN`); `07-unknown-element-type.mobix` | accepted, the unknown stroke preserved | `mobix-element-fields.md`, stroke |
| 25 | place a `text` by its anchor and never invent a box for it | `08-unknown-top-level-field.mobix` | accepted, no `bounds` invented | element fields, text |
| 26 | resolve `image` and `texImage` through `assets/`, treat a missing payload as a corrupt document, and read a formula with no rendering as still a formula | `03-bundled-assets.mobix`; `MobixElementCodecTest.kt` (a `texImage` with no bytes) | accepted, `texImage` still drawn from `latex`; rejected when a named asset is absent | `mobix-assets.md`, element fields |
| 27 | rebuild a `table`'s box from `colWidths`/`rowHeights`, read a short row as empty cells, and keep an unreadable `align` by preserving the table whole | `09-unknown-nested-fields.mobix`; `11-unknown-vocabulary-values.mobix` (`JUSTIFY`) | accepted, box rebuilt; accepted, table preserved | element fields, table |
| 28 | draw a `plot` from its curves and range, and keep `summaryText` as the cache it is | `09-unknown-nested-fields.mobix` | accepted, curve extras kept | element fields, plot |
| 29 | recompute a `parametricShape`'s bounds from its vertices, and accept a `shape` name this build does not know | `MobixElementExampleTest.kt` (a shape with a foreign name) | accepted, bounds recomputed | element fields, parametricShape |
| 30 | place a `circuit` by `position` then `rotation`, with its pins relative to the position and before the rotation | `MobixElementExampleTest.kt` (a rotated component with two pins) | accepted, pins land on the rotated positions | element fields, circuit |
| 31 | typeset a `mathBlock` from its `source` alone, in the `flavor` it names | `MathBlockCodecTest.kt` (a Typst block) | accepted, no rendered bytes read | element fields, mathBlock |
| 32 | model every family's documented example without falling back to verbatim preservation | `18-every-family.mobix` | accepted, no `RawElement` for a known family | `mobix-element-fields.md` |
| 33 | parse an identifier against the grammar, and fail the open on one that does not parse instead of reading it as an unknown feature | `17-third-party-writer.mobix`; `MobixCapabilityTest.kt` (a malformed identifier) | accepted; rejected — corrupt declaration | `mobix-capabilities.md`, the identifier's grammar |
| 34 | satisfy a capability only from the same namespace and feature at an equal or higher version, never across two features | `15-capability-higher-version.mobix`; `MobixCapabilityTest.kt` | refuse-write — `:1` does not satisfy `:2` | `mobix-capabilities.md`, a later version is a superset |
| 35 | keep both lists in written order with one entry per capability, an absent list meaning empty | `16-capability-additive-order.mobix` | accepted, order and spelling unchanged | `mobix-capabilities.md`, required is a floor |
| 36 | decide the four outcomes in precedence order, an unsatisfied requirement above a permissive `minAppVersion` and the floor above the optional list | `13-…`, `14-…`, `15-…` and `01-…` | refuse-write, preserved, refuse-write and full edit — in that precedence | `mobix-capabilities.md`, four outcomes |
| 37 | declare the union on a merge or paste, one entry per feature at the higher version, the base's order first | `MobixMergeManifestPartsTest.kt`, `MobixCapabilityCopyTest.kt` | accepted, the merged manifest states the union | `mobix-capabilities.md`, merge and paste |
| 38 | validate `manifest.json`, `canvas.json`, a page and a chunk against the published schemas before trusting a field table read | every file in both corpora; `MobixSchemaGuardTest.kt` | accepted, no schema violation | [`schema/`](schema/) |

A row that fails is a defect in the implementation, not a matter of taste: the chapters state each rule
once, and the fixtures are files rather than prose so that a disagreement is reproducible. The evidence
that this repository's own reader passes every row is the set of tests the sample column names.

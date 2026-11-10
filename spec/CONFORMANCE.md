# `.mobix` v1 conformance checklist

> Read when you want to prove a reader or writer you built conforms, row by row, instead of trusting a
> successful open on one file.

Each row is a rule an implementation can be wrong about while still producing something that looks like
a document. The chapter column names where the rule is stated; the evidence column names what a reader
of this repository uses to prove it. Run every row against every file in the golden corpus
(`mobix-core/src/test/resources/fixtures/mobix-v1/`) and against one file you produced yourself.

| # | A conforming reader must | Chapter | Evidence here |
|---|---|---|---|
| 1 | identify the type from the `mimetype` entry's bytes, not from the file name or suffix | `mobix-schema-spec.md`, container protocol | `MobixContainerTest` (`aZipPackageXoppIsNotAMobix`, `aPlainZipIsRejectedForDeclaringNoType`) |
| 2 | refuse an archive whose `mimetype` is not `application/vnd.mobixournal+zip` | container protocol | `MobixContainerTest`; fixture 17 |
| 3 | refuse an archive without `manifest.json` | container protocol | `MobixContainerTest` (`anArchiveWithoutAManifestIsNotADocument`) |
| 4 | read `pages[]`/`chunks[]` in manifest order, never by entry name | `mobix-schema-spec.md`, manifest | `MobixV1FixturesTest` fixture 02 |
| 5 | take a part's identity from `id`, and its bytes from `entry` | manifest | fixture 02 |
| 6 | fill a documented default for an absent optional field, and refuse a missing required one | manifest, canvas | `MobixManifestTest` |
| 7 | accept a document that carries neither `pages` nor `chunks` as having no content | manifest | `MobixManifestTest` fixture 01 |
| 8 | keep an unknown manifest, page, part or `canvas.json` field, value and order included | compatibility rule 2 | fixtures 06 |
| 9 | keep an unknown archive entry byte for byte on a rewrite | container rule 4 | fixture 05 |
| 10 | preserve an element `type` it has no model for, whole, and write it back | `mobix-elements.md`, rule 1 | fixture 07 |
| 11 | preserve a field of a known type it did not read, at the level it was written | `mobix-elements.md`, retention rule | fixtures 08, 09 |
| 12 | refuse a value it would misread, and keep the element whole rather than the page failing | `mobix-elements.md`, vocabulary | fixture 11 |
| 13 | draw an unknown background as a plain sheet and write the object back unchanged | `mobix-format.md`, capability table | fixture 12 |
| 14 | resolve an `asset` by the entry named, and treat a missing one as a corrupt document rather than a blank element | `mobix-assets.md` | `MobixContainerTest` (`anImageWhoseAssetTheArchiveDoesNotCarryIsACorruptDocument`); fixture 03 |
| 15 | read a chunked document as tiles at their own coordinates, without inventing pages | `docs/chunked-documents.md` | fixture 10 |
| 16 | compare declared capabilities as the contract states, and refuse to write a file whose required set it does not satisfy | `mobix-schema-spec.md`, declared capabilities | fixtures 13-17 |
| 17 | impose the limits `mobix-format.md` states before allocating from a hostile file | `mobix-format.md`, what a reader must refuse | `MobixHostileCorpusTest` over `fixtures/mobix-hostile/` |
| 18 | refuse a duplicate, `..`/`.`-segment, absolute, backslashed or control-character entry name rather than normalising it | container protocol | `MobixArchiveSafetyTest`, `MobixContainerTest` |
| 19 | treat `mimetype` and `manifest.json` as required and every other entry as optional, distinguishing absent from empty | container protocol | `MobixManifestTest`; fixture 01 |
| 20 | report a corrupt container as one malformed-document error carrying the underlying failure as its cause, never a partial read | container protocol | `MobixArchiveSafetyTest` (encrypted entry, bad CRC, truncated file) |
| 21 | require `formatVersion`, `documentId` and `canvasMode`, and fill the documented default for every other manifest field | `mobix-data-model.md`, manifest | `MobixManifestTest`; fixture 01 |
| 22 | read page order from `pages[]`, tile set from `chunks[]`, and treat the two lists as exclusive with `canvasMode` | data model, cross-field rule 3 | `MobixManifestTest`; fixtures 02, 10 |
| 23 | read lengths as points, times as UTC epoch milliseconds, colours as `#RRGGBBAA`, and a non-positive `chunkSizePt` as its default | data model, units and `canvas.json` | `MobixManifestTest`; fixtures 01, 10 |
| 24 | model a `stroke` with its tool, its style, its per-point widths and its telemetry, and refuse one whose tool or style has no name here | `mobix-element-fields.md`, stroke | `MobixElementCodecTest`; fixture 07 |
| 25 | place a `text` by its anchor and never invent a box for it | element fields, text | `MobixElementCodecTest` (`aTextElementCarriesItsFontAndContent`) |
| 26 | resolve `image` and `texImage` through `assets/`, treat a missing payload as a corrupt document, and read a formula with no rendering as still a formula | `mobix-assets.md`, element fields | `MobixElementCodecTest` (`anImageWhoseAssetIsMissingIsRefused`, `aFormulaWithNoRenderingIsStillAFormula`); fixture 03 |
| 27 | rebuild a `table`'s box from `colWidths`/`rowHeights`, read a short row as empty cells, and keep an unreadable `align` by preserving the table whole | element fields, table | `MobixLivingCodecTest` |
| 28 | draw a `plot` from its curves and range, and keep `summaryText` as the cache it is | element fields, plot | `MobixLivingCodecTest` |
| 29 | recompute a `parametricShape`'s bounds from its vertices, and accept a `shape` name this build does not know | element fields, parametricShape | `MobixLivingCodecTest` |
| 30 | place a `circuit` by `position` then `rotation`, with its pins relative to the position and before the rotation | element fields, circuit | `MobixLivingCodecTest` |
| 31 | typeset a `mathBlock` from its `source` alone, in the `flavor` it names | element fields, mathBlock | `MathBlockCodecTest` |
| 32 | model every family's documented example without falling back to verbatim preservation | `mobix-element-fields.md` | `MobixElementExampleTest` |

A row that fails is a defect in the implementation, not a matter of taste: the chapters state each rule
once, and the fixtures are files rather than prose so that a disagreement is reproducible.

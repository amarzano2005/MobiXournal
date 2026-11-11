# The `.mobix` v1 specification — entry point

> Read when you are implementing a `.mobix` reader or writer outside this repository, or checking
> whether a document you were handed is a conforming one.

This directory holds the **implementer-facing** half of the format: the order in which the normative
chapters are read, the conformance checklist a third-party reader runs, and — as the child tasks land —
a JSON Schema layer beside them. The prose itself is not duplicated here.

## The normative sources, in reading order

1. [`docs/mobix-schema-spec.md`](../docs/mobix-schema-spec.md) — the container (`mimetype`, ZIP rules,
   unknown entries), `manifest.json`, `canvas.json`, a page and a chunk, and the compatibility rules.
2. [`docs/mobix-elements.md`](../docs/mobix-elements.md) — the four rules every element obeys, the
   geometry elements, and the vocabulary a reader must interpret.
3. [`docs/mobix-living-entities.md`](../docs/mobix-living-entities.md) — table, plot, parametric shape,
   circuit and math block.
4. [`docs/mobix-format.md`](../docs/mobix-format.md) — what a reader must refuse, and the capability
   table that says which construct survives an open and save.
5. [`docs/xopp-bridge.md`](../docs/xopp-bridge.md) — the `.xopp` bridge, and the losses it documents.

Those pages are written to be read by someone who has never seen this codebase: where a rule matters to
an outside implementation it is stated as a rule, not as a description of a Kotlin class. A second copy
of the format here would be a fork, and a fork is a document that will disagree with the reader it
describes.

## The schemas

[`schema/`](schema/) holds the machine-checkable mirror of the codecs, Draft 2020-12 and self-contained
per file: [`manifest.schema.json`](schema/manifest.schema.json), [`canvas.schema.json`](schema/canvas.schema.json),
[`page.schema.json`](schema/page.schema.json), [`chunk.schema.json`](schema/chunk.schema.json) and one
document per element family in [`schema/elements/`](schema/elements/). Each declares the required fields,
the documented defaults as `default` annotations rather than validation, the enums the format owns, and
`additionalProperties: true` wherever the retention rule applies, so an additive field never makes a
conforming document invalid.

Every file's `$comment` says where it stops: a digest of bytes held in another entry, a reference from
one entry to another, and geometry that is the sum of other fields are checks only code can make.
`MobixSchemaGuardTest` runs in `:mobix-core:test`: it validates the whole golden corpus against these
files, and it re-encodes one fully populated element per family and compares the keys the writer emits
with the properties a schema declares, so a field added on either side alone fails the build. It also
fails when a modelled family has no schema file, no sample or no element in
[`18-every-family.mobix`](../mobix-core/src/test/resources/fixtures/mobix-v1/README.md), which is what
keeps a new `type` from shipping as code alone.

## Where the machine-checkable part lives

A prose rule is necessary and not sufficient: the schema layer beside this file will describe
`manifest.json`, `canvas.json`, a page and each known element family as JSON Schema Draft 2020-12, and a
guard test in the repository keeps those documents in step with the codecs. The schemas deliberately
**stop** at three things, and say so in the file: a digest of bytes stored in another entry, a reference
from one entry to another (`asset`, `entry`), and geometry that is the sum of other fields.

## How a third party checks an implementation

[`CONFORMANCE.md`](CONFORMANCE.md) is the checklist, one row per rule a reader can be wrong about. The
short form: open every file in the golden corpus, run the conformance rows against the document you
produced from each, then open and re-save one file written by this app and compare the constructs the
capability table says must survive.

## Licence — public domain, on purpose

Everything in this directory ([`README.md`](README.md), [`CONFORMANCE.md`](CONFORMANCE.md), the JSON
Schema documents when they land, and the [`LICENSE`](LICENSE) beside them) and the golden fixture
corpus in `mobix-core/src/test/resources/fixtures/mobix-v1/` are dedicated to the public domain under
**CC0 1.0 Universal** — SPDX `CC0-1.0`. The application's source stays **GPL-2.0-or-later**, and
[`NOTICE`](../NOTICE) records the split.

The reason is the reader this directory exists for. A specification an implementer cannot copy into a
wiki, a test suite or a non-GPL product without first answering a licence question is a specification
that gets re-read and mis-remembered instead of used. CC0 makes the text and the fixture files
themselves freely embeddable, with no attribution obligation to satisfy and no compatibility question
to answer first.

The choice is also compatible with the repository as a whole, which matters because these files ship
inside it. A public-domain work may be redistributed inside a GPL-licensed collection without changing
that collection's terms: the GPL's copyleft attaches to the program and its derivative works, while
these artefacts are data the program ships beside. Bundling them here does not relicense the
application, and embedding them elsewhere does not put anyone under the GPL. That is why the split is
by file rather than by feature — the specification and its samples are CC0, the code that reads them
is GPL.

## What this is not

It is not a second architecture index, it does not describe the app's policies (which format a document
is saved in, how the shells present an outcome), and it is not a place for a rule that the normative
chapters already state. Those live in the pages above and in the guide.

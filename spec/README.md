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

## What this is not

It is not a second architecture index, it does not describe the app's policies (which format a document
is saved in, how the shells present an outcome), and it is not a place for a rule that the normative
chapters already state. Those live in the pages above and in the guide.

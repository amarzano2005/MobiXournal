# Declared capabilities

> Read when you write or read `requiredCapabilities` and `optionalCapabilities` in a `.mobix` manifest, or
> when a reader you built has to decide what it may do with a file that declares them.

## Three questions, three fields

A `.mobix` answers three separate questions, and each has its own field:

| Question | Field | Whose version numbers |
|---|---|---|
| can I parse this shape? | `formatVersion` | the format's |
| can this app keep the file intact? | `minAppVersion` | MobiXournal's |
| which features must a reader understand to keep this document? | `requiredCapabilities` / `optionalCapabilities` | the writer's own |

The third has no home unless the file answers it. An independent writer cannot express "my connector
element has to be understood" in app versions, and bumping `formatVersion` for a feature no v1 reader
needs to know about would break every v1 reader with no structural reason. So the manifest declares
capabilities instead:

```json
{
  "requiredCapabilities": ["org.mobixournal/stem.connector:1"],
  "optionalCapabilities": ["org.mobixournal/paper.isometric:2"]
}
```

## The identifier's grammar

An identifier is `<namespace>/<feature>:<version>`, and each part earns its place:

- **`namespace`** — a reverse-DNS name the declaring writer owns (`org.mobixournal`), lowercase, digits,
  internal dashes, at least two dot-separated labels. It exists so two writers cannot collide on one
  word, and it names *who* owns the feature's meaning. It is deliberately **not a URL**: nothing is
  fetched, so a capability never becomes a network dependency or a privacy leak.
- **`feature`** — lowercase alphanumerics separated by single dots or dashes (`stem.connector`). It is
  the construct's name inside that namespace; dots group a family, so `audio.opus` and `audio.flac` are
  two features rather than one with a parameter.
- **`version`** — a positive integer. It is a promise about the *shape* of the feature, not a release
  number of the writing app.

A value outside that grammar is a **corrupt declaration**, not a capability from the future: a reader
cannot tell a typo from an unknown feature, and reading a typo as "unknown" would silently weaken the
file's own requirement. The manifest codec therefore refuses both lists when one entry does not parse,
and the open fails naming the offending text — the same way a malformed `canvasMode` fails it. The
canonical spelling is what a save writes back, field for field.

## A later version is a superset

A version promises the shape, so `…:2` contains everything `…:1` promised. That gives the whole
comparison rule, which has exactly two clauses:

- A reader that handles `…:2` **satisfies** `…:1`; a reader that handles only `…:1` does **not** satisfy
  `…:2`. Satisfaction is `declared version >= wanted version`.
- Comparison never crosses a feature **or** a namespace: `audio.flac:9` does not satisfy `audio.opus:2`,
  and no version of `com.example/audio.opus` satisfies `org.mobixournal/audio.opus`, however high.

There is no negotiation and no fallback list: a capability is satisfied or it is not.

## Required is a floor, optional is a hint

- **`requiredCapabilities` is a write floor.** A reader that does not satisfy one may not write the
  document — it may show what it can, and nothing else.
- **`optionalCapabilities` is a display hint.** Not implementing one is allowed; the construct it names
  must then be preserved verbatim rather than edited.
- **Both lists are optional in the file and additive.** Absent means empty, an entry appears once, and
  the order is kept exactly as written: a writer's own priority is not this format's to sort.

Both rules are read from the same verdict, computed once at open from the manifest and the capabilities
the reader declares. A notice and a disabled action are two readings of one answer; two comparisons in
two places are how a window ends up offering a save its gate already refused.

## Four outcomes, in the order they are decided

The first that applies wins, and the order is the contract:

| # | Outcome | When | What the document allows |
|---|---|---|---|
| 1 | **refuse-write** | a required capability this reader does not satisfy | the pages may be looked at, and nothing may be written back |
| 2 | **read-only display** | `minAppVersion` above this reader | every page shown; edits, Save and Save As disabled |
| 3 | **preserved-but-not-drawn** | an optional capability this reader does not implement but can keep whole | editing elsewhere allowed; the affected elements carried through untouched |
| 4 | **full edit** | every required capability satisfied, no optional construct affected | edited and saved normally |

Two precedence rules are stated because they are the ones that get reversed:

- **An unknown required capability outranks a permissive `minAppVersion`.** The file says it needs
  something; a version number cannot talk it out of that.
- **`minAppVersion` outranks the optional list.** A reader that cannot keep the document intact at all
  cannot honestly claim to preserve the parts it does not draw.

Among several unsatisfied requirements the verdict names all of them, in the writer's order, so the
message a user reads is the file's own list rather than one arbitrary entry. Refusing to write is never
weakened by another capability being understood.

v1 has no fifth answer for "a required construct I can draw but not write": a required entry is a claim
about writing, so it refuses the write. **Read-only is therefore `minAppVersion`'s outcome here**, and a
writer that wants its file shown but never written by older builds says so by raising that floor.

## What makes preservation honest

`preserved-but-not-drawn` exists only because the retention rule in [mobix-elements.md](mobix-elements.md)
makes it true: a field a reader did not read is carried, and an element type it has no model for is kept
whole and written back. A capability is a claim that a construct survives a save it did not understand,
so the two must agree — a build that drops what it did not parse cannot declare the third outcome.

The consequence is a limit on which capabilities may be *optional* rather than required: one whose
construct has **no model and no preservation channel** — something the reader would have to interpret in
order to keep — cannot be promised as preserved, and its writer must declare it required instead.

`minAppVersion` stays the legacy MobiXournal guard, compared numerically per dotted component.
**Editability backwards is not promised**: a newer file opened by an older build may be shown read-only
or preserved verbatim, never silently trimmed.

## Merge and paste declare the union

A merge result declares the union of both sides' required and optional sets, and a copy or paste into
another document raises the destination's required set to include the capabilities of the elements it now
holds — or is refused. A document that cannot keep an element must not accept it and then trim it.

Both halves follow one rule (`unionCapabilities`): **one entry per feature, at the higher version, the
base's order first**. A document holding both `…:1` and `…:2` must state the stronger claim, and the
surviving entry keeps the position its feature first appeared at. The raised set lives on the document
and is written by the save, so the file the user ends up with says what it holds and the next older
reader knows not to trim it.

## Where these rules are checked

`MobixCapabilityTest` is the grammar's evidence and the comparison rule's: the canonical spelling parses
field for field, a later version satisfies an earlier one, and nothing crosses a feature or a namespace.
Fixtures 13-17 in the golden corpus are one file per outcome, driven by `MobixCapabilityFixturesTest`;
the space between them — including both precedence rules — is the matrix of `MobixCapabilityGateTest`.
What each shell does with the verdict is the shells' own business: the tablet asks before it disables
writing, and the window opens a read-only document's pages in a tab with every write turned off.

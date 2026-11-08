# The developer guide: build it, test it, change it

> Read when you are about to change the code, run the suite, or review a change someone else made.
> For the task: [the guide router](guide-index.md), [what works and what is not there](guide-limitations.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This is the route in for someone who has the repository and not the history. It is a page about **how to
work here**, so it owns no mechanism: every step below links the page that owns the thing you are about to
touch.

## The first hour

1. **[codebase.md](codebase.md)** — what the project is, what it is built on, and the prior art it came from.
2. **[repo-layout.md](repo-layout.md)** — the three modules and the packages inside them.
3. **[architecture.md](architecture.md)** — the index of every spoke. Open the one your task touches; it is
   the whole reason this corpus is split into spokes rather than one long document.
4. **[tools.md](tools.md)** — the front door for building, running and releasing. The commands live there,
   not here.

## Build, test, run

The commands are [tools.md](tools.md)'s, and which suite runs where is [testing.md](testing.md)'s. Three
facts are worth carrying in your head before you run anything:

- **The JVM suites need no device.** They are where the codecs, the geometry and the guards live, and a
  full run is a few minutes.
- **The instrumented suite is compiled, not run, in CI**, because no runner there has a device — so an
  `androidTest` that stops compiling is still a broken claim, and `CiCoverageTest` fails on it.
- **The emulator loop** — boot, install, drive, screenshot, `androidTest` — is
  [tools-emulator.md](tools-emulator.md)'s.

## The rules the build will hold you to

Most of these fail a test rather than a review, which is the point: a rule a machine can check is not a
matter of taste.

- **Size.** [SourceStructureTest](testing.md) ratchets file and function length, and `AGENTS.md` states the
  numbers and why they exist. The remedy is a split, never a raised ceiling without a written reason.
- **One fact, one home.** If you find the same rule in two pages, move it to its owner and leave a link —
  and if the two can drift apart unnoticed, add a line to `DocClaimsTest`
  ([testing.md](testing.md) lists what each guard is for).
- **A UI change** is checked against [interface-contract.md](interface-contract.md), and the declaration it
  needs is [design-workflow.md](design-workflow.md)'s.
- **A document-format change** is bounded by the contract in [xopp-bridge.md](xopp-bridge.md) and the
  coverage ratchet that enforces it, described in [testing.md](testing.md).

## Writing it down

Documentation is part of the work, not a follow-up: the page and the code land in the same commit.
[writing-docs.md](writing-docs.md) owns how a page is written and reviewed — the reader model, the phases,
the Definition of Done and the status vocabulary. A capability's state is stated in that vocabulary, and
where each word may be spent is [guide-limitations.md](guide-limitations.md)'s.

**Adding a topic** is five steps, in [architecture.md](architecture.md) under *Adding a spoke*: a new page,
a row in the index, a reader line, a link from the pages that need it, and — if a guide page leans on it —
the `For the task` line that names that page back.

## Shipping a change

Releases are cut by CI from a tag, never by hand, and the procedure is *Cutting a release* in
[tools.md](tools.md). What has shipped, and on which shell, is
[the release index](releases/index.md)'s.

## Where things go

| If your question is | its owner is |
|---|---|
| what the project is, and its lineage | [codebase.md](codebase.md) |
| where a file lives | [repo-layout.md](repo-layout.md) |
| how the system works | [architecture.md](architecture.md) |
| how to build, run or release it | [tools.md](tools.md) |
| what the suites and guards enforce | [testing.md](testing.md) |
| what a user sees, and what is missing | [the guide](guide-index.md) |
| how to report a bug or send a patch | [CONTRIBUTING.md](../CONTRIBUTING.md) |

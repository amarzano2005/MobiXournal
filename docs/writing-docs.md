# Writing a doc a person can use

> Read when you write, rewrite or review documentation — a file under `docs/`, `README.md`, a release
> note, or the words the app shows when something fails.
> For the task: [What works, and what is not there](guide-limitations.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md). Which file owns
which fact is the map in [AGENTS.md](../AGENTS.md)._

## Documentation is an interface

A document is an interface between the software and the person trying to use it, and an interface that
needs its implementation explained before it can be operated has failed. So a page is organised around
a reader's question — what can I do, how do I do it, what will happen, what can go wrong — and never
around the shape of the code.

Changing a heading, turning paragraphs into bullets and copying text between files are not
documentation work. Keep the technical facts; question the structure that carries them.

## Eight questions before the first line

1. Who is going to read this?
2. Why are they reading it?
3. What are they trying to accomplish?
4. What do they already know?
5. What do they need to know?
6. What is the simplest true way to say it?
7. What could they misunderstand?
8. Where should they go next?

A paragraph that answers none of these belongs in another document, or in none.

## The five audiences, and who serves them

| Reader | What they arrive with | Where the answer lives |
|---|---|---|
| **New users** | "What is this, is it for me, how do I start?" | `README.md` — the first screen, the features, how to get the app |
| **Xournal++ users** | "My notebooks — can I open them, and can I go back?" | `README.md`, then [`xopp-bridge.md`](xopp-bridge.md) for what survives and what does not |
| **Regular users** | "How do I do this one thing?" | `README.md` for the shape of a task; the owning spoke only when the answer is technical |
| **Developers** | "How does it work inside?" | the spokes, entered from [`architecture.md`](architecture.md) |
| **Contributors** | "How do I build, test and land a change?" | [AGENTS.md](../AGENTS.md), [`tools.md`](tools.md), [`testing.md`](testing.md) |

One document does not serve two rows. A README section written for a developer is a section a user has
to skip, and a spoke written for a user cannot state a limitation precisely enough.

## Organise by task, not by subsystem

The order of a user-facing page is the order of the work, not the order of the modules:

```text
wrong:  Canvas renderer · Layer subsystem · Document serialisation · Stroke processing
right:  Getting started · Working with documents · Writing and drawing
        Organising notes with layers · Annotating a PDF · Importing and exporting
```

Internal structure belongs in the reference spokes, where a developer arrives already knowing what they
are looking for.

## Sentences a newcomer can follow

- Plain words first; a term earns its place by being explained where it first appears.
- Active voice, short sentences, one idea in each.
- A heading says what the reader will learn, not what the section contains.
- A comparison becomes a table; a sequence becomes a numbered list; everything else stays prose.
- An example beats an abstract definition whenever the two could be confused.
- A screenshot is taken when the reader must recognise a control, and not as decoration.
- A limitation is stated in the same place as the capability it limits.

## One home, and a pointer to it

Each fact lives in one file, and the map in [AGENTS.md](../AGENTS.md) says which. Every other page
summarises it in a sentence and links to it. `README.md` stays the entry point — what the app is, how to
get it, what it does, where to read more — and never becomes the manual, the reference and the changelog
at once.

Before editing a file, decide what it owns, what it must not repeat, and which reader it serves. A file
that answers a second audience's questions has become two documents sharing a name.

## A change and its documents

A change that moves what a reader can see, do or rely on carries its documentation **in the same
commit**, never in a follow-up. A feature whose page still describes the old behaviour is a bug with no
failing test, and a removal whose page still promises the thing is worse than either.

The rule is one sentence; what it costs you is [doc-governance.md](doc-governance.md)'s matrix — a new
entity, a new module, a changed rendering path, one shell only, a removal. Two obligations do not move
with it: a capability's state is restated only in [guide-limitations.md](guide-limitations.md), and item
10 of the Definition of Done below is what "in the same commit" means in practice.

The other half of the rule matters as much: **do not touch a page the change does not affect**. A doc
edited to satisfy a form is a page whose next reader re-checks what never changed, which is the cost
this rule exists to remove.

## Verify a claim before you write it

Inspect the source, the platform implementation, the tests and the release notes before describing a
behaviour. Then say which of these is true:

- implemented and wired end to end into what the user runs;
- implemented in the shared library with no caller yet;
- experimental — present behind a switch, or unreproduced on a device;
- planned, with the task that will make it real;
- absent.

Never conclude that Windows does it because Android does, that a visible control supports an action, or
that an element which renders can also be edited, selected, deleted and written back. Displaying is not
editing, editing is not saving, and saving is not round-tripping.

### What the evidence behind a claim is

A statement about behaviour also says which kind of check stands behind it, because the five are not
equally strong and only one of them is about a person actually doing the thing.

| Class | Means | What it can support |
|---|---|---|
| **Verified on Android** | the workflow was driven on a device or an emulator, and the run was written down | a procedure a tablet reader follows |
| **Verified on Windows** | the same, on the window, with the build named | a procedure a Windows reader follows |
| **Verified through automated tests** | a named test asserts it and passes | the behaviour under test |
| **Verified through source-code inspection only** | the code was read and settles the question | what the code does — never that someone can do it |
| **Not verified** | nothing available could confirm it | an open question, written as one |

Source inspection is the class a mechanism claim needs and the one a workflow claim may never borrow: a
control that exists in a composable is not a control a reader can find, and a code path is not a step they
can follow. A page that describes an interaction nothing in this repository exercised says so, and the
support matrix is where that word is carried by name ([guide-limitations.md](guide-limitations.md)).

## The six status words

When a capability's state is stated, it is stated with one of these words — and the platform is named
whenever the answer differs between them.

| Word | Means |
|---|---|
| **Supported** | Verified end to end in the shell named, with a test or a device run behind it |
| **Partially supported** | Real but incomplete: one shell, one format, one path, or library-only code |
| **Experimental** | Present but not yet trusted — behind a switch, or not reproduced on real hardware |
| **Planned** | Not there; `TODO.toml` holds the work that will add it |
| **Not supported** | Deliberately absent, with the reason stated |
| **Not verified** | Believed to work and unconfirmed; say so rather than implying a test exists |

The wiring words **live** and **library only** stay what [`architecture.md`](architecture.md) makes them
— where a behaviour is called from — and are a different axis from what a reader may rely on. A
library-only engine is *partially supported* while its code is real and tested.

## Two formats: state what survives

`.mobix` and `.xopp` are not interchangeable, so a sentence about them names the step it means: the file
is recognised, it parses, it draws, an element can be edited, an element is preserved, the export keeps
it, a round trip returns the same document, and a byte comparison is equal. "Works with Xournal++" is
not one of those steps.

For each element a reader cares about — stroke, text, image, PDF background, layer, formula, table, plot,
circuit — say what happens on import, on editing and on export, and point at the test that proves it.
The full contract is [`xopp-bridge.md`](xopp-bridge.md)'s; do not answer it a second time. Notebooks are
valuable data, and a migration risk stated plainly is worth more than a fluent promise.

## Say which platform, every time

Android and Windows differ. A procedure that differs gets two procedures, a capability that differs gets
a table, and a shell that lacks something says so instead of describing the other one. Never present an
incomplete implementation as done, and never let a step assume a control the reader cannot see.

## Fewer documents, better answers

The goal is the effort a reader spends finding and trusting an answer, not the number of words produced.
One guide beats five overlapping ones; a clear explanation beats repeated paraphrases; a limitation beats
an ambiguous promise. Outdated, duplicated or misleading text is deleted, after its still-true content
has a home elsewhere.

## Phase 1 — Research

Read the code, the tests, the existing docs and the release history before explaining anything. A claim
that cannot be traced to one of them is not written down.

## Phase 2 — Name the reader

Decide which of the five audiences the page serves, and what that reader must be able to do afterwards.

## Phase 3 — Design the information architecture

List what to keep, rewrite, move, merge, delete and add before writing a sentence. A fact that moves
leaves a pointer behind.

## Phase 4 — Draft for the reader

Write from the reader's question, not from the original text. Reuse a sentence only when it is already
the clearest one.

## Phase 5 — Verify every claim

Check each statement against the implementation or an authoritative record, and label what could not be
checked as **Not verified**.

## Phase 6 — Read it as a newcomer

Can someone tell what the app does, find what they need, follow the steps without guessing, and see the
limits and the risks? Anything that fails is rewritten, not explained around.

## Phase 7 — Validate the mechanics

Links, anchors, images, commands, examples, and the consistency of platform and version claims. The
mechanical half is checked by `DocsGuardTest` and `DocClaimsTest`, whose inventory is
[`testing.md`](testing.md).

## The Definition of Done

A documentation task is finished when:

1. the page has a stated audience and an obvious purpose;
2. its structure follows the reader's work, not the code's;
3. every technical claim was checked, and the unchecked ones say so;
4. it can be understood without opening a source file;
5. its instructions can be followed without guessing;
6. platform differences and limitations are visible;
7. nothing is duplicated from another owner, and the pointer to that owner is there;
8. its links, examples and commands resolve;
9. a first-time reader can complete the task it describes;
10. the `README.md` bullet or owning spoke is updated in the same commit;
11. the build's doc guards pass.

## What the build enforces, and what only review can

- **`DocsGuardTest`** — the shape: an opening title and reader line on every doc, a working index, no
  broken link or anchor, the 300-line budget, one title and one section per subject, no sentence in two
  files, and every code file, doc and task id a doc names existing.
- **`DocClaimsTest`** — the statements: sentences that were false once, a compatibility claim dressed in
  adjectives instead of evidence, the desktop's Settings list, the test classes a doc names, and this
  standard's own vocabulary, phases and Definition of Done.
- **Review** — audience fit, task order, comprehension, and whether a risk is stated plainly. No test can
  read a page for a newcomer; the checklist above is what a reviewer holds.

## When two rules want different answers

Choose the reader over the original text, clarity over brevity, accuracy over a better-sounding claim,
and the developer reference over the front door. When the behaviour is unknown, investigate; when it is
missing, say so; when information is absent, name the gap instead of filling it.

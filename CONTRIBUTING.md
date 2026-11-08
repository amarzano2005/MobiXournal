# Contributing to MobiXournal

MobiXournal is an independent, unofficial app: it continues Brian Monroe's NeXopp under its own name and
is not affiliated with the Xournal++ project ([codebase.md](docs/codebase.md) owns that history). Bug
reports and patches are welcome, and the fastest way to have either one acted on is to include what the
sections below ask for.

## Report a problem

Five things make a report actionable, and a report without them usually costs a round trip:

1. **The platform** — Android (with the device and Android version) or Windows 11.
2. **The version** — the release you installed; [the release index](docs/releases/index.md) dates them, so
   "the current one" is not enough.
3. **What you did** — the steps, in order, starting from opening a document.
4. **What happened** — including whether it happens every time.
5. **The message, verbatim.** Error text is written to be acted on; a paraphrase cannot be.

## Before you open an issue

Three pages answer most reports without one: [troubleshooting](docs/guide-troubleshooting.md) for a failure
and its next action, [the FAQ](docs/guide-faq.md) for the questions that look like bugs, and
[what works and what is not there](docs/guide-limitations.md) for the capabilities that are stated rather
than missing by accident.

## Change the code

- **Take the build and test front door from [tools.md](docs/tools.md)** rather than inventing a command;
  [testing.md](docs/testing.md) says which suite runs where.
- **Expect the guards to review you first.** They are listed with what each one protects in
  [testing.md](docs/testing.md); the size ratchet, the interface contract and the language rules are steps
  of the change, not a later cleanup.
- **A user-facing change** is checked against [interface-contract.md](docs/interface-contract.md), and
  records the design declaration [design-workflow.md](docs/design-workflow.md) describes.
- **A patch that changes a document format** has to keep the contract in
  [xopp-bridge.md](docs/xopp-bridge.md): `.xopp` files round-trip without loss, and the tests say so.

## Change the documentation

Docs land in the same commit as the code, and four checks decide whether a doc change is done. The first
three now fail the build, so a green run answers them; [writing-docs.md](docs/writing-docs.md) owns the
rest of the standard.

1. **The page opens like the rest.** An `# H1`, then a `> Read when …` line naming its reader — the line
   is how the person it was written for recognises that it is theirs.
2. **The fact has one home.** If it is already stated somewhere, move it to its owner and leave a link;
   two copies drift apart, and a sentence pasted into two pages fails `DocsGuardTest`.
3. **A status word is spent once.** `Supported`, `Partially supported`, `Experimental`, `Planned`,
   `Not supported` and `Not verified` go against a capability only on
   [guide-limitations.md](docs/guide-limitations.md); every other page links it.
4. **A guide page is registered and points back.** It is listed in [the router](docs/guide-index.md),
   closes on the spoke that owns the how, and that spoke names it in a `For the task` line.

## Open a pull request

The template asks the eight questions a reviewer would otherwise have to ask in the thread: which
documented behaviour the change moves, whether the capability matrix is still right, whether a page
names a file, module or version that no longer exists. Answer them in the description —
[doc-governance.md](docs/doc-governance.md) is the matrix that says which pages a given change touches,
and the doc guards run in CI and locally (`:app:testDebugUnitTest --tests "com.mobixournal.guards.*"`).

## Licence and credit

The licence is [LICENSE](LICENSE) and the third-party notices are [NOTICE](NOTICE). MobiXournal reads and writes Xournal++'s `.xopp` format and continues the NeXopp lineage: the
branding and the credit are [codebase.md](docs/codebase.md)'s to state, and the app is not endorsed by the
Xournal++ authors.

The developer entry point — build, tests, guards, releases — is
[the developer guide](docs/developer-guide.md).

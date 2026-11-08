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

Docs land in the same commit as the code. [writing-docs.md](docs/writing-docs.md) owns the reader model,
the phases and the Definition of Done; the one rule worth repeating here is that a fact has **one** home, so
a second statement of it is a bug in the docs. A capability's state is stated in the vocabulary
[writing-docs.md](docs/writing-docs.md) defines and [guide-limitations.md](docs/guide-limitations.md)
spends.

## Licence and credit

The licence is [LICENSE](LICENSE) and the third-party notices are [NOTICE](NOTICE). MobiXournal reads and writes Xournal++'s `.xopp` format and continues the NeXopp lineage: the
branding and the credit are [codebase.md](docs/codebase.md)'s to state, and the app is not endorsed by the
Xournal++ authors.

The developer entry point — build, tests, guards, releases — is
[the developer guide](docs/developer-guide.md).

<!--
Documentation is part of the change (`docs/writing-docs.md`), and `docs/doc-governance.md` says which
pages a given change touches. Answer the questions; delete the ones this change does not reach.
-->

## What changes, and why

<!-- The behaviour, the module and the reader it affects. One paragraph is enough. -->

## Documentation

- [ ] I reviewed the README and updated it if this change affects product-facing features, platform support, installation, distribution, screenshots or file-format capabilities — technical facts stay in their owning doc, and the README summarises and links
- [ ] The capability matrix (`docs/guide-limitations.md`) still says the truth
- [ ] Every page still describes the behaviour it quotes
- [ ] A format, API or schema change reached the spoke that owns it
- [ ] A new or removed module, package or dependency reached `docs/architecture.md` and `docs/repo-layout.md`
- [ ] The links, anchors and paths of the pages I touched resolve
- [ ] A release note is queued, or the change rides in a release that already has one
- [ ] No page was edited that this change does not affect

## Checks

- [ ] `./gradlew :app:testDebugUnitTest --tests "com.mobixournal.guards.*"` (or the container/host loop in `docs/tools.md`)
- [ ] The suite of the module I changed

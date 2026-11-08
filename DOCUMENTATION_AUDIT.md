# MobiXournal documentation audit

**Assignment:** an architecture audit of the MobiXournal documentation — the corpus read as one information system, not as a folder of Markdown files.

**Scope read:** `README.md`, `AGENTS.md`, the **49** spoke documents directly under `docs/`, the **5** release notes under `docs/releases/`, plus the evidence each of them claims: the Kotlin sources in `app/`, `mobix-core/` and `desktop/`, the guard suites in `app/src/test/java/com/mobixournal/guards/`, the Gradle build, and the work archive `FINISHED.toml`.

**Mode:** read-only. No existing file was modified and no proposed change was implemented; this report is the only artefact of the assignment.

**State audited:** `main` at MobiXournal 2.0.0 (Android, `versionCode 14`) with Desktop 1.0.1 (Windows 11), documentation standard `docs/writing-docs.md` in force, `TODO.toml` empty (no active work).

## How this report is written

Every claim below is traceable to a file and, where the claim is about behaviour, to the code that settles it. Severity is graded by **user harm**, not by tidiness:

| Severity | Meaning |
|---|---|
| **Critical** | A reader acting on the sentence gets a wrong result: a feature they think exists, a file operation they think is safe, a difference they think does not exist. |
| **High** | The reader cannot complete a task without guessing, or loses trust in the corpus as a whole. |
| **Medium** | The information exists but is in the wrong place, split, or written for someone else. |
| **Low** | Editorial: tone, title, rhythm, duplication that costs reading time but not correctness. |

Audience codes used throughout the inventory: **A1** new user (never installed it), **A2** Xournal++ user evaluating a replacement, **A3** regular user (owns a notebook, wants a task done), **A4** developer (works on this codebase), **A5** contributor (external: bug report, patch, translation of a doc).

## Contents

1. Executive summary
2. Audience analysis
3. Existing documentation inventory
4. Detailed findings
5. Technical accuracy concerns
6. Information architecture problems
7. Proposed documentation structure
8. Prioritized action plan
9. Unverified claims requiring investigation

---

## 1. Executive summary

MobiXournal is an unofficial, stylus-first note-taking and STEM drafting app for **Android 8.0+** and **Windows 11**, built as one `mobix-core` plus two shells (an Android editor and a Compose Multiplatform desktop window). It began as a continuation of NeXopp, itself a companion for Xournal++, and it keeps two document formats alive at once: its own `.mobix` container and `.xopp` interchange with a permanent, tested round-trip contract.

Its documentation is, by engineering standards, unusually good. The corpus has a single owner per fact, a reader line at the top of every document, mechanical guards that fail the build when a doc drifts, and a written standard (`docs/writing-docs.md`) that was added days before this audit. There are 54 Markdown files, all of them structurally valid, all of them reachable from one index, none of them over the 300-line budget. Nothing here is abandoned or contradictory by accident.

And yet **the corpus does not document the product**. Of 49 spokes, 49 are written for the person changing the code: each opens with *"Read when you change…"* and proceeds through class names, caches, staging files and measurements. There is **no page a user can read to do a user's job** — install it, make a notebook, annotate a PDF, decide whether their `.xopp` survives the trip, find out why a save was refused. That job is currently done by a 123-line `README.md` written in a marketing register, and by five release notes.

Five findings matter more than the rest:

1. **No user-facing documentation exists at all** (F-01, Critical). Four of the five audiences in the project's own standard are unserved by any page.
2. **Three statements in the corpus are false about shipped behaviour** (F-03, F-04, F-05, Critical): the `.mobix` table says data tables have no editor; the architecture index and the bridge doc say the merge engine has no caller; the README and the 2.0.0 release notes say Typst formulas render as crisp vectors. Each sends a reader to a wrong conclusion — build a feature that exists, assume sync cannot happen, or choose the app for a typesetter it does not have.
3. **Status truth is scattered and duplicated in two vocabularies** (F-08, High): a capability's state is stated in README bullets, in a `.mobix` feature table using the retired words *Live / Model only / Live & verified*, and in past-tense release notes. The six-word vocabulary the standard now mandates is used by the standard alone.
4. **The map is a subsystem list, not a task list** (F-10, High): every route into the corpus goes through `docs/architecture.md`, whose entries are named after modules ("The chrome: editor screen, rail and dock"), not after what a person wants to do.
5. **The technical reference has begun to rot where it is most trusted** (F-06, F-07, High): a measured table in `docs/codebase.md` is wrong in 8 of 9 rows, and `docs/repo-layout.md` promises "one line per file" while omitting 140 Kotlin files and pins Gradle 8.9 against an 8.14.5 wrapper.

The fix is not a rewrite. It is an **addition plus a correction pass**: a task-oriented layer for A1–A3 that the existing guard set can hold (new audience, same mechanics), one owned status page, an accuracy sweep over about a dozen sentences, and the merge-or-split decisions that seven documents sitting at 295–300 lines are already asking for. Section 7 proposes exactly that; Section 8 orders it by user harm.

## 2. Audience analysis

The project's own standard (`docs/writing-docs.md`) names five audiences. This audit kept them and measured the corpus against each, because two of them (A1, A2) currently have no entry point that is not a developer document.

### A1 — The new user who has just downloaded it

**Wants to accomplish:** open the app or the installer, draw something, save it, find the file again, and decide whether it is worth keeping.
**Questions they arrive with:** *Which download is mine, the APK or the MSI? Does it need an account, a cloud, a licence? Will it run on my tablet with a passive pen? Where do my notes live — can I move them to Drive? Why does Windows warn me about the installer?*
**Information they need:** two install paths with their real prerequisites (Android 8.0+, JDK never needed for the MSI); a five-minute first notebook; the two formats explained in **consequences** ("what stays editable"), not in container terms; where files are stored and what "atomic staged write" means for a Google Drive folder; a statement that the desktop MSI is unsigned unless the project was built with a certificate.
**Where they get confused today:** the README's *Try It* table is the only onboarding, and it hands them `scripts/release-apk.sh`, `MOBIX_SIGN_*` and `:desktop:createDistributable` within one screen. The first bullets they read are "sticky format policy" and "atomic staged writes" — two internal decisions wearing user clothes. There is no screenshot annotation, no labelled UI, no "your first stroke" walkthrough. A user who wants to know what the tools are called has to open a 300-line developer chat about the rail.
**Coverage:** none. `README.md` is the closest artefact and is written for an evaluator with a build toolchain.

### A2 — The Xournal++ user evaluating or migrating

**Wants to accomplish:** open an existing `.xopp` library, keep working, and hand files back to desktop Xournal++ without losing ink.
**Questions:** *Will my notebooks open unchanged? What happens to my audio sidecars and my PDF backgrounds? If I edit here and then in Xournal++, which version wins? Does "lossless" include my layers, my page labels, my LaTeX? Can I keep using `.xopp` or am I being pushed to `.mobix`? Is the app affiliated with Xournal++?*
**Information they need:** the compatibility contract in user language; what the metadata revival does when a file was edited elsewhere; the two documented refusals (a packaged `.xopp` whose background is inside the package cannot be saved by the desktop window; a chunked `.mobix` opens as pages and the app offers no unbounded canvas); the affiliation disclaimer.
**Where they get confused today:** the only honest statement of the contract is `docs/xopp-bridge.md` (235 lines of synthesis, revival and SHA-256), and the only user-visible claim is the word "losslessly" in a README bullet. Nothing tells them that opening a `.xopp` keeps saving as `.xopp` — which is the single most reassuring fact this product has — except a developer sentence about "sticky format policy". And nothing warns them about the one case the desktop window refuses.
**Coverage:** none for A2; the material exists but is written for A4.

### A3 — The regular user with a job to finish

**Wants to accomplish:** one specific task, under time pressure, on one of two platforms: annotate a PDF, insert a plot with a shaded integral, snap a bad circle, record audio and jump back to a stroke, search handwriting, move a page, change a pen preset, recover after a crash.
**Questions:** *Where is that button? What is the shortcut? Why is this greyed out? Why did the save fail and is my work gone? Which of these two menus has the setting I need? Is this different on Windows?*
**Information they need:** task pages named after the task, with the UI path and the shortcut on both shells; a shortcut reference per platform; the failure messages verbatim, with what to do; a per-platform capability matrix (a tablet has an audio rail slot, a window records through its own pill; some things exist on one side only).
**Where they get confused today:** the knowledge is present but encoded for maintainers — `docs/desktop-documents.md` explains the crash snapshot under the heading *"Unsaved edits: the dot, the question, and a snapshot"*, which a user will never search for; `docs/desktop-settings.md` lists all eleven Settings sections but as a store-key reference; `docs/ui-modernization.md` has the shortcut tables. Every one of these is a good page for A4 and an unfindable page for A3.
**Coverage:** none that a non-developer can use. This is the largest single gap in the corpus and the reason the product's most polished behaviours (recovery, refusal messages, magnetic highlighter) are effectively undocumented.

### A4 — The developer working on this repository

**Wants to accomplish:** change one subsystem without breaking another, and know why it is the way it is.
**Questions:** *Where does this live? What is the invariant? Which test pins it? Is this live, library-only, or aspirational? What breaks in the other shell if I change it here?*
**Information they need:** the hub-and-spoke map, one owner per fact, the guards, the measured budgets, the decisions with their dates, the exact files.
**Where they get confused today:** they are the audience the corpus was built for, and it mostly works — the reader line, the single owner and the guards are genuinely strong. The friction is accuracy drift (F-03–F-08) and the fact that a fact's *status* must be reconstructed from prose rather than read from a status word.
**Coverage:** excellent breadth, moderate depth-per-topic, and a rot problem in the two reference tables.

### A5 — The contributor outside the repository

**Wants to accomplish:** file a report that can be acted on, or land a small patch, and know what will be refused.
**Questions:** *Which version and platform am I on? What will you need from me? Where do I put a doc change? Why did CI fail on a doc rule?*
**Information they need:** how to report (version, platform, file that failed to open), the conventions a patch must satisfy (one home per fact, the six status words, the interface contract, the guards), and the licence/attribution position (GPL-2.0-or-later inherited from NeXopp, with `NOTICE`).
**Where they get confused today:** `AGENTS.md` is an agent-and-maintainer contract that happens to also be the only statement of conventions; there is no `CONTRIBUTING`-style page, and no issue template is described anywhere in the docs. A first-time contributor meets the 300-line budget and the `_One spoke_` line without a page telling them those exist and why.
**Coverage:** partial, and misplaced: the conventions live in a document addressed to AI agents.

### What the gap costs, concretely

The corpus is written for A4 and reads as evidence that the product's own behaviours are undocumented from the outside. A user who cannot find the words *recovery*, *refused save* or *magnetic highlighter* will not discover them by browsing file names such as `desktop-documents.md` or `pdf.md`. The 2.0.0 release notes are, today, the best user documentation the project has — which means the best documentation of a feature is frozen at the moment it shipped, written in past tense, and never updated again.

---

## 3. Existing documentation inventory

Every document is listed once, with the audience it actually serves (not the one its title suggests), its strengths, its gaps, and whether its home is right. Line counts are exact. "A4 only" is the corpus's default state and is not repeated as a strength.

### 3.1 Formats and the bridge

| Document | Lines | Audience | Purpose today | Strengths | Weaknesses, gaps, duplication | Home |
|---|---|---|---|---|---|---|
| [`mobix-format.md`](docs/mobix-format.md) | 179 | A2, A4 | Why `.mobix` exists, what it adds over `.xopp`, the per-document format policy | The motivation section is genuinely persuasive; the status column is an attempt at honesty; 24 links | Status column uses the retired vocabulary and one row is false about tables (F-04); no user-language page for the format policy | Keep as the format's owner; fix the table, split the policy into A2 language |
| [`mobix-schema-spec.md`](docs/mobix-schema-spec.md) | 176 | A4 | Container, manifest, page schema, compatibility rules | Precise, code-derived, states what a reader refuses | No complete example envelope; versioning rules stated but not shown as a worked migration | Keep |
| [`mobix-elements.md`](docs/mobix-elements.md) | 144 | A4 | The four rules every element obeys, and the geometry elements | The four rules are the corpus's best invariant statement | No worked round-trip example; a reader must assemble the element model from three documents | Keep |
| [`mobix-living-entities.md`](docs/mobix-living-entities.md) | 268 | A4 | The five living families on disk, per family | Accurate and current — including the table editor that `mobix-format.md` denies | Near the ceiling; the table-editor description contradicts the format table, and nothing reconciles them | Keep; it is the truth, `mobix-format.md` must follow it |
| [`xopp-format.md`](docs/xopp-format.md) | 270 | A2, A4 | The `.xopp` on-disk schema, authoritative | Code-derived mapping, honest about what the format cannot hold | No minimal `.xopp` sample to compare against; 270 lines of schema with no index | Keep |
| [`xopp-bridge.md`](docs/xopp-bridge.md) | 235 | A2, A4 | Synthesis, revival, the metadata block, the compatibility contract | The contract is explicit, testable and points at its guards; the refusal list is excellent | "Nothing in the app calls it" about the merge engine is false (F-03); the contract is written for implementers, not for a migrating user | Keep as owner; fix the false claim; add an A2 summary at the top |
| [`mobix-streaming-api.md`](docs/mobix-streaming-api.md) | 92 | A4 | The four-verb contract a second shell opens documents with | Small, precise, proves itself with the desktop test | No code sample; no statement of what a third-party caller may rely on outside this repo | Keep |

### 3.2 Documents, data and the session

| Document | Lines | Audience | Purpose today | Strengths | Weaknesses, gaps, duplication | Home |
|---|---|---|---|---|---|---|
| [`data-path.md`](docs/data-path.md) | 168 | A4 | Sniffing, reading, encoding and writing a document's bytes | One path, no forks between shells | A single `## Data path` section: 168 lines with no headings — the hardest doc to skim in the corpus; a reader looking for "where does a save stage" must read all of it | Keep; break into headings |
| [`document-session.md`](docs/document-session.md) | 242 | A4 | Where an open document is held: remote staging, tabs, split view | Explains the three-way relationship honestly | Remote staging (SSHFS/FTP/cloud) reads as a supported workflow with no user page and no limitations list | Keep; add the A3 page for remote folders |
| [`chunked-documents.md`](docs/chunked-documents.md) | 127 | A4 | The storage and I/O engine of a chunked board | Clear ownership statement; links into merge and format | Overlaps `infinite-canvas.md` (geometry and export) — two homes for one topic | Merge target: absorb `infinite-canvas.md` |
| [`infinite-canvas.md`](docs/infinite-canvas.md) | 87 | A4 | Geometry modes and export slicing of chunked content | Short and specific | Second half of a topic whose first half is `chunked-documents.md`; a reader cannot tell which to open | Merge into `chunked-documents.md` |
| [`text-and-markdown.md`](docs/text-and-markdown.md) | 206 | A4 | Text/markdown import, the paginator, generated PDFs | Complete on a fiddly path | No user page: importing a markdown file and getting a paginated PDF is a feature nobody can discover from the docs | Keep; add the user task |
| [`images.md`](docs/images.md) | 72 | A4 | Image import, pixmap background, reference storage | Small, own topic, states round-trip status | `Round-trip status` is the pattern the whole corpus should use and only this doc has | Keep; use it as the template for status sections |
| [`pdf.md`](docs/pdf.md) | 299 | A2, A3, A4 | Everything PDF: backgrounds, export, fonts, text, outline | Deep and accurate; the magnetic highlighter and text selection are well explained | Seven topics in 299 lines, one of them a retired feature kept as history; a user cannot find "copy text from a PDF" here | **Split** (backgrounds/fonts/references vs text/outline/export) |

### 3.3 The canvas and the chrome (Android)

| Document | Lines | Audience | Purpose today | Strengths | Weaknesses, gaps, duplication | Home |
|---|---|---|---|---|---|---|
| [`chrome.md`](docs/chrome.md) | 245 | A4 | The editor screen, the rail, the secondary toolbar, the floating dock's material | Concrete, with the reasons for each rule (the rail never cuts a slot) | The frame around the page lives in `chrome-frame.md` — one subject, two files; the user's mental model ("where is the pen?") is absent | Keep as owner; absorb `chrome-frame.md` |
| [`chrome-frame.md`](docs/chrome-frame.md) | 113 | A4 | Bands around the page, saved toolbar order, the empty document | Small and focused | Same subject as `chrome.md`; split by accident of budget | Merge into `chrome.md` |
| [`chrome-settings.md`](docs/chrome-settings.md) | 136 | A4 | The Settings shell: two shapes, search, prefs behind them | Explains phone vs tablet layout honestly | A user looking for "where is the setting for X" will not open a file about layout; no shortcut table here | Keep as engineering owner; the A3 settings page belongs in the guide |
| [`pages-and-paper.md`](docs/pages-and-paper.md) | 167 | A3, A4 | Paper and ruling, templates, bookmarks, page list | Bookmark rationale (`.xopp` has no page label) is a good explanation | Written from the model outward; page reorder/duplicate/copy are described in `pdf.md` and here without a single task page | Keep |
| [`ui-modernization.md`](docs/ui-modernization.md) | 244 | A4 | Why the UI left Material 3, design principles, ergonomics, tokens, shell details | The "why" of the visual identity is genuinely readable; shortcut tables live here | Overlaps `interface-contract.md` (prescriptive rules) and `colours.md` (tokens): three homes for one design system, told three times | Keep as rationale; point at the contract for rules |
| [`interface-contract.md`](docs/interface-contract.md) | 300 | A4 | What any new UI element must satisfy: material, colour, geometry, glyphs, states, motion, parity | The corpus's strongest prescriptive document; it is what makes the two shells look like one product | At the ceiling exactly, with an appendix ("Air at a rounded edge") doing a second job | Keep; move the appendix into `colours.md` or `chrome.md` |
| [`colours.md`](docs/colours.md) | 180 | A4 | The palette's provenance, the 24-swatch ceiling, theme roles | Provenance against desktop Xournal++ is well argued | Tokens are also specified in `ui-modernization.md` and `interface-contract.md` — a colour change has three places to visit | Keep as palette owner; other two link only |

### 3.4 Tools, input and search (Android)

| Document | Lines | Audience | Purpose today | Strengths | Weaknesses, gaps, duplication | Home |
|---|---|---|---|---|---|---|
| [`drawing-tools.md`](docs/drawing-tools.md) | 291 | A4 | What each tool does to the page: shapes, eraser, layers, hold-to-snap, the plotter, zoom | Broad and concrete; the plotter and hold-to-snap are explained with their arithmetic | Four subjects in one file, two of them owned elsewhere: hold-to-snap's behaviour is `shape-perfection-engine.md`'s, the plot engine's is `math-engine.md`'s; near the ceiling | **Split**: canvas tools (shapes, eraser, layers, zoom) vs placement (plotter, hold-to-snap pointer) |
| [`element-authoring.md`](docs/element-authoring.md) | 129 | A4 | Placement of text, LaTeX, images, plots, tables, circuits, and vertical space | The window-vs-tablet parity detail is excellent | A grab-bag by construction: three unrelated sections (authoring, living placement, vertical space) under one title; the title promises six tools and the reader gets a list | Move `## Vertical space` to `drawing-tools.md`; keep the rest |
| [`selection.md`](docs/selection.md) | 300 | A4 | Marquee, lasso, handles, transforms, align/distribute, region copy, the window's inspector lane | Complete and precise on a genuinely hard feature | Exactly at the ceiling; four clip flavours and the inspector lane crowd out what a user would search for ("how do I copy part of a page?") | **Split**: selection and transforms vs transfer/clipboard/region |
| [`gestures.md`](docs/gestures.md) | 213 | A4 | Gesture priority, wheel, palm rejection, pressure, smoothing, momentum, taps, telemetry | The "one move, one gesture" priority list is the clearest rule in the corpus | Overlaps `stylus.md` on "one pointer layer, two shells"; telemetry section duplicates a `.mobix` capability row | Keep |
| [`stylus.md`](docs/stylus.md) | 236 | A4 | Tool detection, barrel button, diagnostics, graphics tablets, onboarding | Measured rather than guessed (the pen diagnostics rationale) | Same overlap with `gestures.md`; first-launch onboarding is a user feature with no user page | Keep |
| [`search.md`](docs/search.md) | 253 | A3, A4 | What a query searches, the two engines, the index, the model choice | Strong: states what is measured and what is cached | A user cannot learn from it that search exists, what it finds on each platform, or that the ML Kit model may need a download | Keep; add the A3 task page |
| [`shape-perfection-engine.md`](docs/shape-perfection-engine.md) | 143 | A4 | Dwell trigger, tremor filter, recognised classes, rejections | Has the corpus's only `## Status` section that states what is *not* recognised and why | The behaviour is also told in `drawing-tools.md`; the split is by file, not by question | Keep as behaviour owner; `drawing-tools.md` links only |

### 3.5 Audio

| Document | Lines | Audience | Purpose today | Strengths | Weaknesses, gaps, duplication | Home |
|---|---|---|---|---|---|---|
| [`audio.md`](docs/audio.md) | 161 | A4 | Recording, replay, `.xopp` sidecars, the window's audio | Explains the format-dependent recorder choice (Opus vs WAV) clearly | Exactly **one** link: the least connected document in the corpus, though it owns a headline feature; the karaoke half is a second file | Keep as owner; absorb `audio-inking-sync.md` |
| [`audio-inking-sync.md`](docs/audio-inking-sync.md) | 234 | A4 | Stroke-synchronised replay, bundled audio, scrubbing | Detailed, with the telemetry mapping | Opens with a history of Xournal++ sidecars; its vocabulary ("karaoke", "sub-stroke") is invisible to a user looking for "replay my recording"; second half of one topic | Merge into `audio.md` (trim history to fit 300) |

### 3.6 The desktop window

| Document | Lines | Audience | Purpose today | Strengths | Weaknesses, gaps, duplication | Home |
|---|---|---|---|---|---|---|
| [`desktop-workstation.md`](docs/desktop-workstation.md) | 300 | A4 | The window: shell, strips, how it is tested, decisions | The best entry point for the desktop half; 19 links; decisions dated | At the ceiling; "How it is tested" duplicates `desktop-testing.md` and `testing.md` | Keep as owner; drop the test section to a link |
| [`desktop-ui-layout.md`](docs/desktop-ui-layout.md) | 286 | A3, A4 | Panes, strips, the shortcut table | The shortcut table is the single most useful user artefact in the corpus — buried in a developer doc | Overlaps `desktop-workstation.md` (strips) and `desktop-settings.md` (shortcuts live in both); title says layout, content says reference | Keep the engineering half; **move the shortcut table** into the guide and link it |
| [`desktop-settings.md`](docs/desktop-settings.md) | 299 | A4 | Eleven Settings sections and the stored key behind each | Exhaustive, and the guard derives the list from `DesktopSettingSection` | At the ceiling; a reference to store keys, not a page a user can use; a user cannot find "backup my settings" from its title | Keep; the A3 settings page belongs in the guide |
| [`desktop-documents.md`](docs/desktop-documents.md) | 73 | A3, A4 | Open/save failures, the dirty dot, the close question, crash recovery | The corpus's best failure documentation — verbatim messages, byte-identical proof, the frozen-window case | Titled and framed for a developer; a user who needs it will never find it under this name | Keep; **extract the user-facing half** into the guide's troubleshooting page |
| [`desktop-editing.md`](docs/desktop-editing.md) | 203 | A4 | What a drag does: figures, placement, selection, PDF text, images, search | Consistent with the tablet's semantics and says where they differ | Long; the parity statements with `app/` are the value and are easy to miss | Keep |
| [`desktop-pen-feel.md`](docs/desktop-pen-feel.md) | 110 | A4 | Active area, hand-pan, the machine's share | Narrow and honest; the Xournal++ comparison is measured | A4-only by nature: "the pen feels" is what a user searches for, and this is not it | Keep |
| [`desktop-tablet.md`](docs/desktop-tablet.md) | 300 | A4 | Wintab, Windows Ink, the dial, the packet path | Device-level accuracy that no user doc could carry | At the ceiling; the dial and tablet setup a user needs ("does my tablet work?") is absent | Keep |
| [`desktop-testing.md`](docs/desktop-testing.md) | 64 | A4 | How the desktop suite runs and what its renders prove | Concrete about pixels and budgets | Duplicates the desktop half of `testing.md` and the *"How it is tested"* section of `desktop-workstation.md`: three homes for one fact | Merge into `testing.md` or keep only as the linked detail of one of the two |

### 3.7 Rendering and mathematics

| Document | Lines | Audience | Purpose today | Strengths | Weaknesses, gaps, duplication | Home |
|---|---|---|---|---|---|---|
| [`rendering.md`](docs/rendering.md) | 195 | A4 | The paint pipeline, the frame watchdog, rasterisation, SVG/PNG export | Ties a performance claim to a record and a rule book | No user-facing consequence stated (when does a page look different?), which would be one sentence | Keep |
| [`math-engine.md`](docs/math-engine.md) | 119 | A4 | Parsing, numerical calculus, advanced curves, parameter sliders | The calculus section is precise about what is refused (three or more curves) | Polar curves are presented as an engine feature without saying they are not placeable from the plot tool (F-11); overlaps `drawing-tools.md` (the plotter) and `mobix-living-entities.md` (the stored plot) with no boundary statement | Keep as engine owner; add the placement boundary |

### 3.8 Engineering process and meta

| Document | Lines | Audience | Purpose today | Strengths | Weaknesses, gaps, duplication | Home |
|---|---|---|---|---|---|---|
| [`architecture.md`](docs/architecture.md) | 118 | A4 | The index: one entry per spoke, plus what the guards enforce | Does its job: 53 links, and it is the reason a 54-file corpus is navigable at all | The "what is installed" block says there is **no sync** because the merge engine has no caller — false (F-03) | Keep as the internal index; a second, task-oriented index is missing |
| [`code-structure.md`](docs/code-structure.md) | 87 | A4 | Where new code belongs: size rules, the window's packages, the canvas ratchet | Ratchets with numbers, all guard-backed | Overlaps `repo-layout.md` on the package map (two lists to keep in step) | Keep |
| [`repo-layout.md`](docs/repo-layout.md) | 292 | A4 | "Every module, package and file", one line per file | The most useful map for a newcomer to the tree | The promise is false: 140 Kotlin files of `app/src/main` and `mobix-core/src/main` are unlisted, the `design/` package (13 files) and `ui/entry/` are absent; Gradle is pinned "8.9" against an 8.14.5 wrapper (F-06) | Keep as module/package map; **stop promising one line per file** |
| [`codebase.md`](docs/codebase.md) | 160 | A4, A5 | What it is built on, prior art, NeXopp lineage, branding | The lineage and licence story is careful and useful | Holds a "measured" performance table dated 2026-10-22 that is wrong in 8 of 9 rows (F-07); the comparison counts (287/21/33/283) are dated 2026-07-30 and unverifiable by a reader | Keep the provenance; **delete the stale measurements** |
| [`stack.md`](docs/stack.md) | 152 | A4 | The pinned stack and the reason each choice exists | Every row is a decision with a why; nothing is decorative | Two rows per dependency (pinned vs decision) make a long scan; no "last reviewed" date per row | Keep |
| [`testing.md`](docs/testing.md) | 182 | A4 | What the unit tests cover, what the build enforces, what CI runs | The guard inventory is the corpus's strongest mechanical asset | The desktop suite is described here *and* in `desktop-testing.md` *and* in `desktop-workstation.md`; CI coverage statement will drift silently | Keep as the single owner; the other two link only |
| [`tools.md`](docs/tools.md) | 300 | A4 | Build, deploy, emulator, release, the running-window probe, the design skill | The release procedure is complete and current | At the ceiling and a grab-bag: a build pipeline reference, a device-harness index, a UI/UX skill advert and a release procedure in one file | **Split**: build & release vs running-window probes & skills |
| [`tools-emulator.md`](docs/tools-emulator.md) | 148 | A4 | Boot, install, drive and instrument the emulator | Complete device loop in one place | Special-cased as "not part of the architecture index", though it is an ordinary topic with an ordinary owner (`tools.md`); Emoji-free dev doc, fine | Register it normally under `tools.md`; drop the special case |
| [`writing-docs.md`](docs/writing-docs.md) | 202 | A4, A5 | The standard: audiences, IA, status words, seven phases, Definition of Done | The best thing in the corpus: it states the reader model the rest of the docs do not yet follow | Not yet reflected in the corpus it governs (the six status words appear nowhere else); its Definition of Done has no artefact a reviewer can check off | Keep; this audit's Section 7 is its missing companion |

### 3.9 Release notes

| Document | Lines | Audience | Purpose today | Strengths | Weaknesses, gaps, duplication | Home |
|---|---|---|---|---|---|---|
| [`v2.0.0.md`](docs/releases/v2.0.0.md) | 62 | A1, A2, A3 | The joint Android 2.0.0 / Desktop 1.0.0 milestone | The only user-voiced page in the project: screenshots, download table, features in consequence language | Two products in one page; repeats the Typst over-claim (F-05); no "fixed in this version" section; freezes truth at release time | Keep as the anchor; add an index |
| [`desktop-v1.0.1.md`](docs/releases/desktop-v1.0.1.md) | 70 | A3 | Desktop 1.0.1: zoom, scrolling, status band, installer | Well-scoped, user-voiced, says what changed in behaviour | No date, no download table, no version scheme note; README never says 1.0.1 is the current desktop build | Keep |
| [`v1.3.0.md`](docs/releases/v1.3.0.md) | 42 | A3 | Guides, STEM suite, PDF, templates, gestures | Scannable | No date; no link to the features' docs; no index entry | Keep |
| [`v1.2.0.md`](docs/releases/v1.2.0.md) | 35 | A3 | Interface and settings refinements, selection, reliability | Short | No date, no download table, no compatibility note (does 1.2 open 1.3 files?) | Keep |
| [`v1.1.0.md`](docs/releases/v1.1.0.md) | 87 | A3 | The modern interface default, STEM tools, search | Substantial | **No H1** — the only document in the corpus that does not open with a title (F-12); sections are emoji headings only | Fix structure; add H1 |

### What the inventory adds up to

- **54 files, one audience.** 49 spokes open with "Read when you change…"; no page is addressed to a user completing a task, and no page states the platform a reader is on at its top.
- **Eight documents sit at 291–300 lines** (`pdf` 299, `selection` 300, `interface-contract` 300, `desktop-workstation` 300, `desktop-settings` 299, `desktop-tablet` 300, `tools` 300, `drawing-tools` 291). The ceiling is not protecting them any more; it is forcing merges that should be splits and splits that should be merges.
- **Link density is uneven.** `architecture.md` has 53 links; `audio.md`, `images.md` and `desktop-documents.md` have one. Those three are topics a reader would most want to reach from elsewhere.
- **Duplication by split, not by copy:** audio/audio-inking-sync, chrome/chrome-frame, chunked-documents/infinite-canvas, testing/desktop-testing/desktop-workstation, ui-modernization/interface-contract/colours. The guards cannot see this kind of duplication — it is not a copied sentence.

---

## 4. Detailed findings

Each finding states what the corpus says, the evidence that settles it, why the difference harms a reader, and the smallest fix. Findings are ordered by severity, then by user harm.

### F-01 — There is no user documentation (Critical)

**What exists:** 54 Markdown files. Twenty-eight open with "Read when you change…", the rest with equivalents ("Read when you need to know how the system works internally", "Read before adding anything to the interface"). The only pages addressed to a user are `README.md` and `docs/releases/`.

**Evidence:** the reader line of every spoke in Section 3; `README.md`'s first substantive bullets ("sticky format policy", "atomic staged writes") describe decisions, not actions; `AGENTS.md`'s own map routes "How a user runs/uses it" to `README.md`, a 123-line file whose Build section is half its length.

**Why it hurts:** a person who installs the APK has no page that says how to create a notebook, what the tools are, how to save to Drive, what happens to a `.xopp`, or why a save was refused. They will use the product as a demo of its bullets. The features the project is proudest of — crash recovery, magnetic highlighter, audio-tap replay, the refusal messages that protect their file — are documented only as engineering decisions, findable by file name if you already know the system.

**Fix:** a task-oriented layer for A1–A3 (Section 7), starting with install, first notebook, `.xopp` expectations, PDF/audio tasks, troubleshooting. It needs no new fact: every page is extracted from an existing spoke, with the reader changed.

### F-02 — The README promises conversion the desktop window refuses (High)

**What the corpus says:** "**Save As** converts cleanly in either direction" ([README.md:46](README.md#L46)) and "Universal Import & Export" ([README.md:47](README.md#L47)).

**Evidence:** the desktop window refuses to save a **packaged `.xopp` whose background lives inside the package** — it has no image/PDF store to bundle one back out of — and says so with the two ways round it ([desktop-documents.md:20](docs/desktop-documents.md#L20)). The refusal is safe (the file stays byte-identical), but it exists, and the README does not mention it.

**Why it hurts:** a user annotating a self-contained `.xopp` with an embedded PDF on Windows gets a refused save at the moment they trust the app with the file. The docs already contain the correct sentence; the front door contradicts it. A user reads "cleanly in either direction" as a guarantee and the app as broken.

**Fix:** one clause in the README's format bullet naming the exception and pointing at the guide's troubleshooting page; keep the refusal text as the authoritative copy.

### F-03 — The merge engine is described as having no caller; it runs on every `.mobix` open (Critical)

**What the corpus says:** "There is no sync: the merge engine is library code with no caller" ([architecture.md:14](docs/architecture.md#L14)); "nothing in the app calls it, there is no sync UI, and no account" ([xopp-bridge.md:107](docs/xopp-bridge.md#L107)).

**Evidence:** `DocumentIo.checkForConflictSiblingAndMerge` is invoked on the `.mobix` open path ([DocumentIo.kt:261](app/src/main/java/com/mobixournal/io/DocumentIo.kt#L261)) and calls `MobixMergeEngine.merge` ([DocumentIo.kt:335](app/src/main/java/com/mobixournal/io/DocumentIo.kt#L335)). The result is stored as `LoadedFile.conflictNotification` ([DocumentIo.kt:281](app/src/main/java/com/mobixournal/io/DocumentIo.kt#L281)) and a test asserts the notification's wording ([DocumentIoNewerAppTest.kt:256](app/src/test/java/com/mobixournal/io/DocumentIoNewerAppTest.kt#L256)).

**Why it hurts:** three readers are misled at once. A contributor planning conflict handling is told the code is dead and may duplicate it or delete it. A user reading the architecture believes a stale copy cannot be merged into their notebook — while it already is, silently. And the real gap — the notification is produced but no screen shows it — is invisible, because the corpus says there is nothing to show. This is the most consequential sentence in the corpus: it is false about an operation that rewrites a user's document.

**Fix:** correct both sentences to "a three-way merge runs when a conflict sibling is opened; its report is not yet surfaced in the UI", record that gap as a follow-up, and let `DocClaimsTest` pin the corrected wording.

### F-04 — Data tables are declared editor-less while their editor ships (Critical)

**What the corpus says:** the `.mobix` capability table calls data tables **"Model only** — codec and ink are live; the table tool still draws a static grid and no editor exists" ([mobix-format.md:42](docs/mobix-format.md#L42)).

**Evidence:** the fixed cell editor is live — `TableCellEditorDialog` is rendered from the editor regions ([EditorRegions.kt:1700](app/src/main/java/com/mobixournal/ui/EditorRegions.kt#L1700)), with its state in `EditorUiState` ([EditorUiState.kt:251](app/src/main/java/com/mobixournal/ui/EditorUiState.kt#L251)) and its own test class. It is documented as shipping in [mobix-living-entities.md:114](docs/mobix-living-entities.md#L114) and announced in the 2.0.0 notes ("in-place cell editing").

**Why it hurts:** a contributor reads the table and rebuilds a feature that exists, or scopes it as "model only" in a task. A user or evaluator reading the format's own honesty column concludes the flagship STEM table is a mock. Worse, the corpus contradicts itself in three places, so a reader no longer knows which file to trust — the exact failure mode a single-owner model is meant to prevent.

**Fix:** one row, one word (Supported), pointing at `mobix-living-entities.md`; and a `DocClaimsTest` line that fails if the phrase "no editor exists" returns.

### F-05 — Typst is advertised as a typesetter; it is a preserved flavour (Critical)

**What the corpus says:** "re-editable LaTeX / Typst math formulas rendered as crisp vectors" ([README.md:62](README.md#L62)) and the same sentence in the 2.0.0 notes ([v2.0.0.md:39](docs/releases/v2.0.0.md#L39)).

**Evidence:** `MathFlavor.TYPST` is a **source-preservation** switch: the flavour is stored and marked on the `.xopp` `teximage` so a reader never guesses the language ([Living.kt:46](mobix-core/src/main/java/com/mobixournal/format/model/Living.kt#L46), [MathBlockInk.kt:22](mobix-core/src/main/java/com/mobixournal/render/MathBlockInk.kt#L22)). Typesetting runs through `LatexParser`/`LatexLayout` in every path (canvas, `.xopp`, PDF, SVG); the project's own record states there is no KaTeX or Typst binary in the tree and that the tablet typesets with the same LaTeX layout engine (`FINISHED.toml`, `desktop-math-block-authoring`). A Typst source such as `sum_(i=1)^n i` is therefore parsed as LaTeX.

**Why it hurts:** this is a purchasing decision. A physics or maths user who writes Typst reads the README (or the release notes, the pages that rank in search) and installs the app for a feature that will render their formula wrongly or as literal text. The failure appears after they have typed their first formula, which is the worst possible moment.

**Fix:** either qualify the sentence ("LaTeX is typeset; Typst sources are preserved and round-trip, rendered by the LaTeX engine") or implement a real Typst path. Until one of those lands, the claim must not stand in user-facing copy — and it should be in `DocClaimsTest` either way.

### F-06 — The repository map promises more than it delivers, and pins the wrong Gradle (High)

**What the corpus says:** "The repository layout: every module, package and file" with "one line per file" ([repo-layout.md:1](docs/repo-layout.md#L1)), and "Gradle wrapper (pinned 8.9)" ([repo-layout.md:30](docs/repo-layout.md#L30)).

**Evidence:** the wrapper declares `gradle-8.14.5-bin.zip` (`gradle/wrapper/gradle-wrapper.properties`). Of the Kotlin sources under `app/src/main` and `mobix-core/src/main` (about 140 files), most are not named; the whole `mobix-core/.../design/` package (13 files) and `app/.../ui/entry/` are absent from the map.

**Why it hurts:** a newcomer uses this page to find things — that is its entire purpose — and silently misses a third of the tree, then edits the wrong place. The wrong Gradle pin is worse: someone reproducing a build failure from the doc's version chases a nonexistent toolchain difference.

**Fix:** rename the promise to "every module and package, and the files that carry a rule", add the missing packages, fix the wrapper version, and delete the exhaustive file list rather than maintain it.

### F-07 — A "measured" table is wrong in 8 of 9 rows (High)

**What the corpus says:** `codebase.md`'s comparison table is captioned "Measured on 2026-10-22" ([codebase.md:118](docs/codebase.md#L118) onward).

**Evidence:** against the current tree, `XoppSynthesis` 183 vs 179, `PageSvgWriter` 252 vs 229, `EditHistory` 111 vs 69, `DrawingGuide` 403 vs 375, `DocumentIo` 540 vs 666, `DrawingSurfaceInput` 482 vs 581, `DrawingSurfaceStrokes` 506 vs 683, `DrawingSurfaceSelection` 409 vs 341; only `ElementEdits` (152) still matches.

**Why it hurts:** a table that says "measured" is read as ground truth, and a developer uses it to judge complexity and risk ("DocumentIo is a 540-line file"). Every wrong number is a small argument that the docs are not maintained — which is how a corpus loses the trust that makes people read it at all. Two weeks of drift produced this; the table cannot be kept honest by hand.

**Fix:** delete the row counts. If the numbers are wanted, generate them in `DocsGuardTest` from `git ls-files` or drop the table; keep the narrative decisions, which do not rot.

### F-08 — Status is stated in three vocabularies and owned by no page (High)

**What the corpus says:** `mobix-format.md`'s status column uses **Live**, **Model only**, **Stored & verified**, **Live & verified** ([mobix-format.md:38](docs/mobix-format.md#L38)); `AGENTS.md` and every spoke use **live** / **library only**; `docs/writing-docs.md` mandates six different words — **Supported, Partially supported, Experimental, Planned, Not supported, Not verified** — and requires "no adjectives".

**Evidence:** the six-word vocabulary appears in `writing-docs.md` and in no other document. `README.md` states capabilities in marketing prose, release notes in past tense, the format table in the retired words, the code in truth.

**Why it hurts:** the user's central question is "does this thing work, here, now?". Today they must triangulate four sources written in four registers, and the answer changes with the file they happen to open. A contributor adding a feature has no single place to update, so the next drift is designed in.

**Fix:** one owned status page (`docs/guide/limitations.md`) as a matrix of capability × platform × status word × evidence link, with the format table's column pointing at it instead of restating it.

### F-09 — The failure messages are documented where only a developer will look (High)

**What the corpus says:** `desktop-documents.md` contains the exact sentences a user sees — the PDF-family refusal, the stale-window case, the memory case, the save refusal, the recovery flow ([desktop-documents.md:10](docs/desktop-documents.md#L10)–[73](docs/desktop-documents.md#L73)).

**Evidence:** the file's reader line says "Read when you change what the `desktop/` window does with a file"; its headings are *Opening a document, and what a failed open says*, *Saving, and what a refused save says*, *Unsaved edits: the dot, the question, and a snapshot*. Nothing in `README.md` mentions recovery, refusal or the stale window.

**Why it hurts:** these are the moments a user is most frightened and most in need of the docs. The content is already written, in the right words, and shelved under a name no user will search. The single highest-value documentation move in this repository is roughly 40 lines extracted from a file that already exists.

**Fix:** a troubleshooting page in the user guide that quotes the messages verbatim and links to `desktop-documents.md` for the mechanism; the developer doc stays as the mechanism's owner.

### F-10 — Every route into the corpus goes through a subsystem index (High)

**What the corpus says:** `docs/architecture.md` is "the index" and `AGENTS.md` routes "Any other spoke" there; `README.md`'s Documentation Map lists 14 targets, all engineering topics ([README.md:96](README.md#L96)).

**Evidence:** 39 of the 49 spokes are not linked from `README.md`. The only task-like entry points are `README.md`'s feature bullets, which link to nothing. Titles are subsystem names ("The chrome: editor screen, rail and dock", "The data path: opening, reading and saving"), so a reader cannot predict contents from the file list either.

**Why it hurts:** navigation assumes the reader knows the system's decomposition. A user who wants to "annotate a PDF" does not know that PDF lives inside `pdf.md` under seven headings, that text selection is a section there, or that the magnetic highlighter is a subsection of the same file. The corpus is navigable by the people who wrote it, which is the definition of an internal wiki rather than documentation.

**Fix:** a second index organised by task and platform (Section 7), with the subsystem index kept as the developer's map. Two indexes, two audiences, one guard covering both.

### F-11 — Polar plots are presented as available; the plot tool only places Cartesian curves (Medium)

**What the corpus says:** `math-engine.md` states that "the engine supports" parametric curves and polar plots ([math-engine.md:93](docs/math-engine.md#L93)), and its reader line invites a reader implementing "polar plots".

**Evidence:** the placement path places `y = f(x)` only — [MultiFunctionPlotter.kt:113](mobix-core/src/main/java/com/mobixournal/render/MultiFunctionPlotter.kt#L113) says so explicitly, and [FunctionAnalysis.kt:20](mobix-core/src/main/java/com/mobixournal/render/FunctionAnalysis.kt#L20) repeats it. Polar/parametric survive, draw and export from the model ([PlotInk.kt:278](mobix-core/src/main/java/com/mobixournal/render/PlotInk.kt#L278)) and are exercised by round-trip tests.

**Why it hurts:** a reader concludes they can offer a polar plot from the UI, plans a task around it, and discovers the gap at implementation time. The correct statement is small and useful ("cannot be placed from the Axes tool; supported on disk, in drawing and in export"), and it is the difference between a status and a promise.

**Fix:** qualify the section with one sentence stating the placement boundary; `PlotInk`'s own comment is the model to copy.

### F-12 — Release notes have no index, no consistent shape, and one has no title (Medium)

**What the corpus says:** five files under `docs/releases/`, each a standalone changelog. `README.md` links the directory ([README.md:114](README.md#L114)).

**Evidence:** `v1.1.0.md` opens with an emoji heading and **no H1** — the only document in the corpus without a title. Only `v2.0.0.md` carries a date; only `v2.0.0.md` carries a download table. The current desktop build is **1.0.1**, a fact stated only inside `desktop-v1.0.1.md`; `README.md` says `MobiXournal-<version>.msi` without saying which version is current.

**Why it hurts:** a user asking "what changed since I installed it?" or "can 1.0.1 open my 2.0.0 file?" has no index, no dates to compare, and no compatibility statement. Support conversations start with version discovery instead of the problem.

**Fix:** `docs/releases/index.md` (version, platform, date, one-line summary, link) and a five-line template applied to the two older notes; a sentence in `README.md` naming the current versions.

### F-13 — Reference prose carries anecdote, so the invariant is hard to find (Medium)

**What the corpus does:** decisions and incidents are narrated inline, in the present tense, inside reference sections. Example: the staging-name rationale in `desktop-documents.md` follows a save refusal through Windows' 260-character limit and a deep Italian folder path ([desktop-documents.md:33](docs/desktop-documents.md#L33)); `chrome.md`, `pdf.md` and `drawing-tools.md` do the same.

**Evidence:** the pattern is deliberate — `AGENTS.md` asks for decisions recorded in the owning spoke — but it has no marker. A reader cannot tell "the rule" from "the incident that produced the rule".

**Why it hurts:** newcomers must reverse-engineer invariants from stories, and the stories are what push eight documents into the 300-line ceiling. The material is valuable; its lack of a shape is not.

**Fix:** a two-part convention in each affected section — the rule first (imperative, one or two sentences), then a `Decision` paragraph in past tense with its date. This is a `writing-docs.md` addition, not a rewrite.

### F-14 — The corpus's own vocabulary is undefined (Medium)

**What it uses without definition:** *living element / living entity*, *glass family*, *rail*, *dock*, *band*, *sheet*, *slot*, *sticky format*, *sidecar*, *chunked board*, *dwell / hold-to-snap*, *barrel*, *dial*, *ring*, *inspector lane*, *strand*, *wash*, *karaoke*.

**Evidence:** no glossary exists; each term is explained, if at all, mid-sentence in a different document. `images.md` alone uses *pixmap background* and *reference* as if settled terms.

**Why it hurts:** search fails for these words (a user searching "snap circle" does not meet *dwell*; one searching "menu on the pen" does not meet *ring*), and a new contributor cannot ask a question in the project's own vocabulary. For A2 the mismatch is worse: Xournal++ calls the same objects *layers*, *tools*, *page background*, so the reader cannot map one vocabulary onto the other.

**Fix:** `docs/guide/glossary.md`, one table: term, what it is, the Xournal++ or everyday word it replaces, and the owning doc.

### F-15 — There is no contributor entry point (Medium)

**What the corpus says:** conventions, guards, commit rules, host profiles and the documentation standard all live in `AGENTS.md`, addressed to AI agents and maintainers; `README.md` links it as "Development Workflow & Conventions" ([README.md:102](README.md#L102)).

**Evidence:** no page describes how to report a bug (version, platform, the file that failed, the message seen), what a patch must satisfy, or why CI fails on a doc rule. The `LICENSE`/`NOTICE` position (GPL-2.0-or-later from NeXopp) is only in `codebase.md`.

**Why it hurts:** an external contributor's first interaction is a bug report with no required fields — and the project's ability to fix open/save failures depends entirely on those fields. This is a support-cost problem as much as a documentation one.

**Fix:** `CONTRIBUTING.md` at the root: report template (five fields), patch expectations (the guards, the doc standard, the interface contract), licence and credit.

### F-16 — The front door is dense enough to read as a wall (Low)

**What the corpus does:** `README.md` is 123 lines and **1 182 words** — within budget — but its feature bullets run 35–55 words each, in one unbroken list per section, and mix three readers (evaluator, builder, developer) in a single scroll.

**Why it hurts:** the reader who most needs the front door — A1 — skims it and retains three phrases. The information is right; the rhythm is not. Nothing here is inaccurate, which is exactly why it is cheap to fix.

**Fix:** keep the budget, cut the register: one sentence per bullet, maximum 20 words, and move build instructions behind a single line pointing at the guide.

---

## 5. Technical accuracy concerns

Consolidated register of statements checked against the tree. "Verdict" is what a reader would conclude, not what the author meant.

| # | Claim | Source | What the code/build says | Verdict |
|---|---|---|---|---|
| A1 | Data tables: "no editor exists" | [mobix-format.md:42](docs/mobix-format.md#L42) | `TableCellEditorDialog` live ([EditorRegions.kt:1700](app/src/main/java/com/mobixournal/ui/EditorRegions.kt#L1700)) | **False** (F-04) |
| A2 | Merge engine "no caller" / "nothing calls it" | [architecture.md:14](docs/architecture.md#L14), [xopp-bridge.md:107](docs/xopp-bridge.md#L107) | Called on every `.mobix` open ([DocumentIo.kt:261](app/src/main/java/com/mobixournal/io/DocumentIo.kt#L261), [:335](app/src/main/java/com/mobixournal/io/DocumentIo.kt#L335)) | **False** (F-03) |
| A3 | Typst formulas "rendered as crisp vectors" | [README.md:62](README.md#L62), [v2.0.0.md:39](docs/releases/v2.0.0.md#L39) | Flavour preserved; typeset by `LatexParser`/`LatexLayout` | **Over-claim** (F-05) |
| A4 | "Save As converts cleanly in either direction" | [README.md:46](README.md#L46) | Desktop refuses a packaged `.xopp` with an embedded background ([desktop-documents.md:20](docs/desktop-documents.md#L20)) | **Incomplete** (F-02) |
| A5 | "Gradle wrapper (pinned 8.9)" | [repo-layout.md:30](docs/repo-layout.md#L30) | `gradle-8.14.5-bin.zip` | **False** (F-06) |
| A6 | "one line per file" for the whole tree | [repo-layout.md:1](docs/repo-layout.md#L1) | ~140 Kotlin files unlisted, two packages absent | **False promise** (F-06) |
| A7 | Nine file sizes "measured 2026-10-22" | [codebase.md:118](docs/codebase.md#L118) | 8 of 9 wrong (Section 4, F-07) | **Stale** (F-07) |
| A8 | "The engine supports … polar plots" | [math-engine.md:93](docs/math-engine.md#L93) | Draw/export/round-trip yes; placeable no ([MultiFunctionPlotter.kt:113](mobix-core/src/main/java/com/mobixournal/render/MultiFunctionPlotter.kt#L113)) | **Needs qualification** (F-11) |
| A9 | ".xopp … losslessly" | [README.md:10](README.md#L10), [AGENTS.md](AGENTS.md) | Contract is lossless for modelled content; anything undrawable is **reported** rather than dropped ([xopp-bridge.md](docs/xopp-bridge.md)) | **Defensible, overstated** — one clause would make it exact |
| A10 | Perf numbers: replays "0.0–0.2 ms", "120/144 Hz", "~15 MB an hour", "40 ms open", "millisecond incremental saves" | [v2.0.0.md](docs/releases/v2.0.0.md), [desktop-tablet.md](docs/desktop-tablet.md), [audio.md:15](docs/audio.md#L15) | Measurements exist in tests/records but are machine-specific and undated in user copy | **Unverified for a reader** (Section 9) |
| A11 | Handwriting search "offline Kotlin fallback" | [README.md:70](README.md#L70) | ML Kit's recogniser uses `RemoteModelManager` + `DownloadConditions` ([MlKitInkEngine.kt:5](app/src/main/java/com/mobixournal/render/MlKitInkEngine.kt#L5)); the model may need a first download | **Needs nuance** (Section 9) |
| A12 | MSI "signed when a certificate is configured" | [README.md:38](README.md#L38) | True: `desktop-package.ps1` signs only with `MOBIX_SIGN_*`; CI ships unsigned and says so | **Accurate** — but the user-visible consequence (SmartScreen) belongs in the guide |

What this table shows as a pattern: the corpus is accurate about **mechanisms** and unreliable about **states and consequences**. Mechanisms are what its authors write daily; states are what readers need.

---

## 6. Information architecture problems

### 6.1 How readers actually search, and where they land

| The reader's query | Where the corpus sends them today | Where it should land |
|---|---|---|
| "recover my notebook after a crash" | nothing; `desktop-documents.md` (developer title) | a troubleshooting page, with the `%APPDATA%\MobiXournal\recovery` path and what "(recovered)" means |
| "typst" | [README.md:62](README.md#L62) — an over-claim | a limitations entry stating what is preserved and what is typeset |
| "merge conflict / sync" | [architecture.md:14](docs/architecture.md#L14) — "there is no sync" | "what happens when two copies exist", with the current UI gap stated |
| "edit a table" | [mobix-format.md:42](docs/mobix-format.md#L42) — "no editor exists" | a task page: double-tap the table, edit cells, one undo step |
| "keyboard shortcuts" | [desktop-ui-layout.md:208](docs/desktop-ui-layout.md#L208) (a layout doc) | a shortcut reference, linked from Settings |
| "why was my save refused" | [desktop-documents.md:20](docs/desktop-documents.md#L20) (mechanism doc) | troubleshooting, quoting the message |
| "does my tablet work on Windows" | [desktop-tablet.md](docs/desktop-tablet.md) (300 lines of Wintab) | a "graphics tablets" section in the guide plus the supported-device statement |
| "what changed in the update" | five disconnected files, four undated | a release index with dates and platform |

Seven of eight queries land in a document whose title does not contain the query's words. That is the single diagnostic of this audit: **the corpus is organised by how it is built, and searched by what it does.**

### 6.2 Structural problems

**P1 — One dimension of navigation.** `docs/architecture.md` indexes subsystems; `AGENTS.md` and `README.md` both funnel into it. There is no task dimension, so "I want to…" must be translated into "which module owns this?" before a page can be found.

**P2 — The audience model is declared, not implemented.** `writing-docs.md` names five audiences and requires one home per fact. The corpus implements A4 (and half of A5, through `AGENTS.md`). The standard and the corpus disagree about what the corpus is for — the highest-priority mismatch in this report.

**P3 — Platform is encoded in file prefixes, not in the information.** `desktop-*` means Windows; Android has no prefix at all, so it reads as the default. A Windows user must learn that `pdf.md` covers "PDF backgrounds" on Android but that the window's PDF text work is partly in `desktop-editing.md`. There is no page that answers "is this different on the other shell?" in one table.

**P4 — Topic boundaries are set by the line budget.** Eight docs sit at 291–300 lines (Section 3). The budget is a good rule, but it is now producing the wrong artefacts: `audio` is split at a seam a user cannot see, `chrome` at an arbitrary one, `infinite-canvas` separated from its own storage engine, while `pdf` holds seven topics and `tools` holds four.

**P5 — Status has no home.** Four sources (README, format table, release notes, code), three vocabularies (F-08), no page whose job is to say what works where. Every capability answer costs a tree-wide search.

**P6 — The topology is a star with no trails.** README → architecture → spoke. Spokes link back to the index and to each other by mechanism, never by task; there is no "next step", no "if you want X, also read Y", no release index, no glossary. A reader who lands on the right page still has to invent the path.

**P7 — Insider vocabulary is load-bearing.** 
Navigation labels (*chrome*, *spoke*, *strand*, *wash*, *glass family*) are the project's private shorthand, and no page translates them — nor maps them onto the words an Xournal++ user already owns (F-14).

**P8 — The best user documentation is frozen at release time.** The release notes are the only user-voiced pages, and they are past tense and never updated. Everything shipped after 2.0.0 exists for the user only in code.

**P9 — History and invariant share a paragraph.** Decision narratives are valuable to A4 and opaque to everyone else; without a marker the reader cannot tell a rule from the story that produced it (F-13).

### 6.3 The changes this implies, by name

**Split** — `pdf.md` (backgrounds/fonts/references vs text/outline/export); `selection.md` (transforms vs transfer/clipboard/region); `tools.md` (build & release vs probes & skills); `drawing-tools.md` (canvas tools vs plotter/placement).

**Merge** — `audio-inking-sync.md` → `audio.md`; `chrome-frame.md` → `chrome.md`; `infinite-canvas.md` → `chunked-documents.md`; `desktop-testing.md` → `testing.md` (with `desktop-workstation.md` linking); the shortcut table of `desktop-ui-layout.md` → the guide.

**Move** — the user-facing halves of `desktop-documents.md`, `desktop-settings.md`, `desktop-ui-layout.md`, `ui-modernization.md`, `search.md`, `pdf.md`, `pages-and-paper.md` into the guide layer (spokes keep the mechanism and gain a pointer); `tools-emulator.md` into the normal index under `tools.md`.

**Remove** — the file-size table in `codebase.md`; the exhaustive file list in `repo-layout.md`; the retired "Vector PDF pages" section of `pdf.md` (keep three lines of history); every legacy status word (`Live`, `Model only`, `Stored & verified`) once the status page exists.

**Missing** — the guide layer itself (13 pages, Section 7.3); `CONTRIBUTING.md`; `docs/releases/index.md`; a glossary; a platform capability matrix; `docs/guide/limitations.md` as the single status owner.

**Missing links** — `README.md` → guide index and release index; guide pages → their owning spoke ("the mechanism is here"); every spoke → its user page, in the reader block; `architecture.md` → the guide layer as a second index rather than the only one.

---

## 7. Proposed documentation structure

### 7.1 Principles

1. **Two layers, one fact.** A user page says what to do and what it means; a spoke says how it works and why. The fact lives in the spoke; the user page links to it. Nothing is copied, so the guard against copied sentences keeps working.
2. **Titles are tasks.** A guide page is named after the job (`annotate-a-pdf.md`), and its H1 is the job. Spoke titles stay technical — they serve A4.
3. **Platform is a first-class axis** in the guide: one page per install path, a table wherever behaviour differs, no page that silently means one shell.
4. **Status is owned once** by `docs/guide/limitations.md`, in the six words of `writing-docs.md`, and other pages link to it.
5. **The existing mechanics survive.** Reader line, one home per fact, ≤300 lines, no copied sentences, `architecture.md` registration — all still apply to both layers.

### 7.2 The target tree

```
README.md                        front door: what it is, two install paths, 2 screenshots, links to the guide
CONTRIBUTING.md                  NEW — report template, patch rules, licence/credit
docs/
  guide/                         NEW — the user layer (A1, A2, A3)
    index.md                     how to find what you need (task list, platform split)
    install-android.md           APK, Android 8.0+, storage, file managers
    install-windows.md           MSI, unsigned/SmartScreen, associations, uninstall
    first-notebook.md            create, ink, save, reopen; where the file goes
    formats.md                   .mobix vs .xopp in consequences; Save As; what survives
    xournalpp.md                 A2: what carries over, what does not, editing elsewhere
    inking-and-gestures.md       pen, palm, snapping, selection, undo
    stem-tools.md                plots, tables, circuits, chemistry, math, guides, dimensions
    pdf-and-audio.md             annotate a PDF; record, replay, export audio
    search-and-organise.md       search, pages, layers, tabs, split view
    settings-and-shortcuts.md    both shells' settings and the shortcut table
    troubleshooting.md           every message, quoted; recovery; stale window; refused saves
    limitations.md               THE status matrix: capability × platform × six-word status
    glossary.md                  term → meaning → Xournal++ word → owning spoke
  formats/                       (moved) mobix-format, mobix-schema-spec, mobix-elements,
                                 mobix-living-entities, xopp-format, xopp-bridge, mobix-streaming-api,
                                 chunked-documents (absorbing infinite-canvas)
  android/                       (moved) chrome (absorbing chrome-frame), chrome-settings,
                                 interface-contract, ui-modernization, colours, pages-and-paper,
                                 drawing-tools, element-authoring, selection, gestures, stylus,
                                 shape-perfection-engine, search, audio (absorbing audio-inking-sync)
  desktop/                       (moved) desktop-workstation, desktop-ui-layout, desktop-settings,
                                 desktop-documents, desktop-editing, desktop-pen-feel, desktop-tablet
  internals/                     data-path, document-session, rendering, pdf (split), images,
                                 text-and-markdown, math-engine, code-structure, repo-layout,
                                 codebase, stack, testing, tools, tools-emulator, architecture
  releases/                      index.md (NEW) + v1.1.0 … v2.0.0, desktop-v1.0.1
  writing-docs.md
```

**A note on the folders.** The move to `guide/`, `formats/`, `android/`, `desktop/` is the right shape but the **last** step, not the first: `DocsGuardTest` currently assumes a flat `docs/` (its index check walks direct children, the 300-line budget and the title/anchor rules follow file names). Phase 1 (Section 8, P1) delivers every user-facing benefit with the flat tree intact: `docs/guide-*.md` files registered in `architecture.md`. The folder split lands when the guard is taught to walk recursively, in one commit, with no content change.

### 7.3 The new documents

| Document | Audience | Purpose | Scope (what it owns) | Relationship |
|---|---|---|---|---|
| `docs/guide/index.md` | A1–A3 | The task index: "what do you want to do?" | Routing only — no facts | Replaces `architecture.md` as the first stop for a user; `architecture.md` stays the developer index |
| `install-android.md` | A1 | Get the APK running | Version requirements, install, storage/permissions, first launch | Links `chrome.md` for what the screen is |
| `install-windows.md` | A1 | Get the MSI installed and files associated | SmartScreen/unsigned note, associations, uninstall, `.exe` folder alternative | Links `desktop-workstation.md`, `tools.md` |
| `first-notebook.md` | A1 | One page written and saved correctly | Create, ink, save, reopen, format choice in everyday words | Links `formats.md`, `inking-and-gestures.md` |
| `formats.md` | A1–A3 | Choose and convert formats without fear | Sticky policy, Save As, what stays editable, the desktop refusal | Owns the consequence language; links `mobix-format.md`, `xopp-bridge.md`, `troubleshooting.md` |
| `xournalpp.md` | A2 | Decide and migrate | What opens unchanged, metadata revival, editing elsewhere, audio sidecars, affiliation | The A2 rendering of `xopp-bridge.md`; links it |
| `inking-and-gestures.md` | A3 | Write well | Pens, pressure, palm, snapping, eraser, selection, undo | Links `stylus.md`, `gestures.md`, `shape-perfection-engine.md` |
| `stem-tools.md` | A3 | Use the STEM tools | Plot, table, circuit, chemistry, math, guides, dimensions; what each is not | Links `drawing-tools.md`, `element-authoring.md`, `math-engine.md`, `limitations.md` |
| `pdf-and-audio.md` | A3 | Two headline jobs | Import/annotate PDF, text selection, outline; record/replay/export audio | Links `pdf.md`, `audio.md` |
| `search-and-organise.md` | A3 | Find and rearrange | Search per platform, pages, layers, tabs, split view | Links `search.md`, `pages-and-paper.md`, `document-session.md` |
| `settings-and-shortcuts.md` | A3 | Change a setting, learn a key | Both shells' settings map + the full shortcut table | Owns the user-facing key table; `desktop-settings.md` keeps store keys |
| `troubleshooting.md` | A1–A3 | Recover from a failed open/save | Every message verbatim, crash recovery, stale window, refused save, where logs are | Extracted from `desktop-documents.md`; links it for mechanism |
| `limitations.md` | A1–A3, A4 | Answer "does it work here?" | Capability × platform × six-word status × evidence link | The only status owner; the `.mobix` table's status column points here |
| `glossary.md` | A1–A5 | Decode the vocabulary | Term, meaning, Xournal++ equivalent, owning doc | Cross-cutting; no facts of its own |
| `docs/releases/index.md` | A1–A3 | Version history | Version, platform, date, one line, link | Sits above the five notes |
| `CONTRIBUTING.md` | A5 | Report or patch | Five-field report, patch rules, guards, licence/credit | Links `writing-docs.md`, `testing.md`, `code-structure.md` |

### 7.4 What happens to the existing documents

Nothing is deleted and no mechanism is rewritten. Each spoke keeps its body and gains (a) a pointer in its reader block — *"the task page for this is guide/…"* — and (b) the status correction from Section 5 where it makes a claim. The four splits and four merges of Section 6.3 are content-preserving moves with trims, each fitting the 300-line budget. `codebase.md` and `repo-layout.md` lose their rotting tables and keep their narrative, which is the part that cannot be regenerated.

### 7.5 The navigation model after the change

Three entry points, each with one job:

- **`README.md`** — should I use this, and how do I install it? Two links onward: the guide, and the release index.
- **`docs/guide/index.md`** — I have a job; take me to the page. Platform is the first split, vocabulary is the glossary.
- **`docs/architecture.md`** — I am changing the code; take me to the owner. Unchanged in role.

Cross-links run both ways and are declared, not implied: a guide page ends with *"How it works: [spoke]"*, a spoke's reader block ends with *"For the task, see: [guide page]"*. That single convention is what turns a star into a map.

### 7.6 What the guards gain

- `DocsGuardTest`: `docsOfRecord()` extended to `docs/guide/` when the folders land; the reader-line rule restated for guide pages (a task line rather than a change line); `architecture.md` must list guide pages too; a rule that every guide page links to at least one spoke and vice versa.
- `DocClaimsTest`: the corrected sentences of F-03, F-04, F-05 pinned as text (the pattern it already uses for `read-only`); the six status words present in `limitations.md`; no guide page older than its claimed version.
- `SourceStructureTest`: unchanged.
- Root files: `CONTRIBUTING.md` must be named by `README.md`, or the root-orphan rule will fail the build.

**This report is itself a root file.** `DOCUMENTATION_AUDIT.md` sits at the repository root, where `everyFileAtTheRootIsReferenced` requires a file to be named by a doc, a test or a script. The rule currently passes for a reason worth stating: the guard concatenates the text of every reference file — this one included — so the report naming itself satisfies it. That is a loophole, not an endorsement. When the audit is merged it should be named from `AGENTS.md` as the audit record, or moved under `docs/` when Phase 1 starts; if it stays at the root, it belongs in `rootInfrastructure` with the decision written down, which is the rule the guard itself asks for.

### 7.7 Two journeys, before and after

**A Windows user with a PDF.** Today: `README.md` says "Smart PDF Annotation" and links nothing; `pdf.md` opens with a retired feature and never mentions the window's text selection until section four; the magnetic highlighter is a subsection of a 299-line file about backgrounds. After: `guide/index.md` → *Annotate a PDF* → `pdf-and-audio.md`, which says import, select text with `Shift+S`, snap the highlighter, browse the outline, then links `pdf.md` for the mechanism.

**An Xournal++ user with a `.xopp` library.** Today: they read "losslessly" in a bullet and hope. After: `guide/xournalpp.md` states that the file opens, keeps saving as `.xopp`, that living elements export as ink with metadata and revive unless edited elsewhere, that audio sidecars and PDF backgrounds come along, and the one case the desktop window refuses — then links the contract for anyone who wants the digest rules. The same facts, addressed to the person they affect.

---

## 8. Prioritized action plan

Ordered by user harm. **P0 is not documentation work**: three sentences currently tell readers something false, and one of them concerns an operation that rewrites a user's notebook. P1 changes who the corpus serves. P2 repairs the structure that P1 exposes. P3 is polish and should never delay P0–P2.

### P0 — Correct what is false (hours, not days)

| ID | Action | Fixes | Where | Acceptance |
|---|---|---|---|---|
| A1 | Rewrite the "no caller" sentences as "a three-way merge runs when a conflict sibling is opened; its report is not yet surfaced", and add the merge-on-open fact where a user reads about saving | F-03 | [architecture.md:14](docs/architecture.md#L14), [xopp-bridge.md:107](docs/xopp-bridge.md#L107), `docs/data-path.md` | No document says "no caller"/"nothing calls it"; `DocClaimsTest` pins the new wording |
| A2 | Change the data-table row to **Supported**, pointing at `mobix-living-entities.md` | F-04 | [mobix-format.md:42](docs/mobix-format.md#L42) | The phrase "no editor exists" is gone; `DocClaimsTest` fails if it returns |
| A3 | Qualify Typst in both places: LaTeX is typeset; Typst sources are preserved, marked and rendered by the LaTeX engine | F-05 | [README.md:62](README.md#L62), [v2.0.0.md:39](docs/releases/v2.0.0.md#L39) | No user-facing page claims a Typst typesetter; the limitation appears in `limitations.md` once it exists |
| A4 | Add the desktop exception to the README's conversion bullet | F-02 | [README.md:46](README.md#L46) | The bullet names the packaged-`.xopp` case and links troubleshooting |

P0 is one commit of four small edits plus two guard lines, and it removes every statement in the corpus that would make a careful reader act wrongly.

### P1 — Build the user layer (the real work)

| ID | Action | Fixes | Where | Acceptance |
|---|---|---|---|---|
| B1 | Write `docs/guide-limitations.md`: capability × platform × the six status words × evidence link | F-08, F-10 | new | It is the only page that states status; the `.mobix` table's status column links to it; a reader can answer "does it work on Windows?" in one page |
| B2 | Write `docs/guide-troubleshooting.md` from the user-facing half of `desktop-documents.md`, quoting every message verbatim | F-09 | new, extracted | Open-failure, save-refusal, recovery, stale-window each have a section with the message and the next action |
| B3 | Write `docs/guide-install-android.md`, `docs/guide-install-windows.md`, `docs/guide-first-notebook.md` | F-01 | new | A person who has never seen the app can install, write a page and reopen the file using only these three |
| B4 | Write `docs/guide-formats.md` and `docs/guide-xournalpp.md` (A2) | F-01, F-02, A9 | new | The A2 page answers opens/changes/deletes/edits-elsewhere/audio/PDF/affiliation without leaving the page |
| B5 | Write `docs/guide-index.md` and register every guide page in `architecture.md` (flat names until the folder move) | F-10, P6 | new | Every task in Section 6.1 is reachable in two clicks from `README.md` |
| B6 | Write `docs/guide-glossary.md` | F-14 | new | Every coined term in Section 4 F-14 has a row with its Xournal++ equivalent |

Sequence inside P1: **B1 → B2 → B3/B4 → B5 → B6**. B1 first because it fixes the vocabulary every other page will use; B2 first in user value; B5 last because it routes pages that must already exist.

### P2 — Repair the structure

| ID | Action | Fixes | Where | Acceptance |
|---|---|---|---|---|
| C1 | The four splits and four merges of Section 6.3, one commit each, byte-for-byte content moves plus trims | P4 | `pdf`, `selection`, `tools`, `drawing-tools`; `audio`, `chrome`, `chunked-documents`, `testing` | No doc over 300 lines; every fact keeps exactly one home; no reader line lost |
| C2 | `docs/releases/index.md`, a five-line template, a date and an H1 for every note, current versions stated in `README.md` | F-12 | `docs/releases/`, `README.md` | A reader can name the current Android and Windows versions and the date of each release from one page |
| C3 | Adopt the two-way pointer convention: a guide page ends with "How it works", a spoke's reader block ends with "For the task" | P6 | all guide pages + the ~20 spokes whose tasks exist | Every guide page links at least one spoke and vice versa; `DocsGuardTest` can enforce it |
| C4 | Write `CONTRIBUTING.md` (five-field report, patch rules, licence/credit) and link it from `README.md` | F-15 | root, `README.md` | The root-orphan rule is satisfied by the README link; a first-time reporter has a template |
| C5 | Delete the rotting tables (`codebase.md` sizes, `repo-layout.md` file list), fix the Gradle pin, qualify polar, register `tools-emulator.md` normally, move `desktop-testing.md` into `testing.md` | F-06, F-07, F-11, P6 | `codebase.md`, `repo-layout.md`, `math-engine.md`, `tools.md`, `testing.md` | No doc states a measurement it cannot regenerate; no "one line per file" promise remains |
| C6 | Teach the guards the guide layer (recursion, task line, cross-link rule, status words) | P1 | `DocsGuardTest`, `DocClaimsTest` | A doc that violates the new layer fails the build, not the review |

### P3 — Editorial

| ID | Action | Fixes | Where |
|---|---|---|---|
| D1 | Retitle and re-cut the front door to one-sentence bullets; move build behind one line | F-16 | `README.md` |
| D2 | Adopt the rule-then-`Decision` convention for incident prose | F-13 | `writing-docs.md` + the eight ceiling docs |
| D3 | Give `desktop-ui-layout.md`'s shortcut table a user home and leave a pointer | P4 | guide + `desktop-ui-layout.md` |
| D4 | Move to `docs/guide/`, `formats/`, `android/`, `desktop/`, `internals/` in one commit with no content change | 7.2 | all |

### Why this order

A user who reads the corpus today can be told a feature does not exist (P0-A2), that a file is safe when it is being merged (P0-A1), and that a formula will typeset (P0-A3). Those are the only statements in this report with a wrong-outcome cost measured in minutes. Nothing else on the list can hurt someone who has not yet found the page — and after P1, they can.

---

## 9. Unverified claims requiring investigation

Claims this audit could not settle from the tree. Each is either a number that needs a fresh measurement, or a behaviour whose condition is environmental. None is known to be false; all are currently stated as if measured forever.

| # | Claim | Why it cannot be verified here | How to verify | Where it should land |
|---|---|---|---|---|
| U1 | Page replay 0.0–0.2 ms; tab switch under 200 ms; canvas part under 50 ms; blank page 0.4 ms / written 0.8 / dense 3 | Measured in `desktop` tests on the owner's hardware (AGENTS.md Profile D); no dated report is committed | Re-run `:desktop:test` on Profile D and publish the printed budgets with date and machine | `desktop-workstation.md` (mechanism) + release notes (only if re-measured) |
| U2 | "120/144 Hz" rendering | Depends on the monitor and the Windows refresh setting; AGENTS.md notes Profile D needs its 144 Hz target raised first | Measure on the 21:9 display at 144 Hz, or state "up to the display's refresh rate" | `desktop-tablet.md`, release notes |
| U3 | Opus audio "~15 MB an hour" | Stated in `audio.md` and the README; depends on content and encoder settings | Record 10 minutes of a real lecture on the tablet and compute the rate | `audio.md` (owner); README links it |
| U4 | "Opens in ~40 ms" / "incremental saves in milliseconds" | Single-machine observations in decision prose | Run the 40-page `.mobix` open and one-page incremental save on both profiles, record device and date | `desktop-documents.md`, `mobix-format.md` |
| U5 | "330 changes" across 303+43+32+16+5+5 in v2.0.0 | Sums to 404, not 330 — likely a different counting rule (tasks vs entries); unverifiable without the release script's definition | Either recompute with `todo.py stats` for the release window or drop the number | `v2.0.0.md` |
| U6 | NeXopp comparison: 287 shared files, 21 added, 33 removed, 283 touched | Dated 2026-07-30; the tree has changed since; a reader cannot reproduce it | Re-run the file-by-file comparison and date it, or remove the counts | `codebase.md` |
| U7 | Handwriting search works offline | ML Kit's digital-ink recogniser is driven through `RemoteModelManager` + `DownloadConditions` ([MlKitInkEngine.kt:5](app/src/main/java/com/mobixournal/render/MlKitInkEngine.kt#L5)); whether the model ships or needs a first download is not stated | Test a fresh install with no network: search a handwritten word, capture the log | `search.md`, `limitations.md` |
| U8 | "Dimension arrows" auto-measure in millimetres | `DimensionLabel`/`ShapeInk` exist; the interaction (drag a dimension, what it measures) is documented nowhere | Drive the tool on the emulator, record the steps | `drawing-tools.md`, guide `stem-tools.md` |
| U9 | Android 8.0+ (API 26) install base | Stated in README and the notes; this audit did not read `minSdk` in `app/build.gradle.kts` | Read `minSdk`; state it once in `limitations.md` | `install-android.md` |
| U10 | Desktop 1.0.1 opening documents written by 2.0.0 (and vice versa) | No compatibility statement exists for the version pair | Open a 2.0.0 `.mobix`/`.xopp` in the shipped 1.0.1 MSI; record the result | `releases/index.md`, `limitations.md` |
| U11 | "Losslessly" for `.xopp` | True for modelled content; the bridge reports what it cannot draw rather than dropping it ([xopp-bridge.md](docs/xopp-bridge.md)) — the absolute word hides the qualifier | Decide the one clause that states the qualifier, then use it everywhere | `README.md`, `formats.md`, `xopp-bridge.md` |
| U12 | MSI signing on the published release | The script signs only with `MOBIX_SIGN_*`; CI states it ships unsigned without a certificate | Check the signature of the published `MobiXournal-1.0.1.msi` (`signtool verify` / file properties) | `install-windows.md` |

Two of these (U1, U2) are the ones a reader is most likely to quote back at the project, because they appear in release notes as achievements. Either publish them with a machine and a date, or soften them to what is true on any machine ("blocking work is proportional to the new stroke").

---

## Closing note

This corpus is closer to being excellent than its gaps suggest. The single-owner model, the reader line, the guards, the six status words and the seven-phase standard are all in place; what is missing is a layer for the people the product is for, and an accuracy pass over the dozen sentences where mechanism was written with the confidence of state. The work in Section 8 is additive and guard-compatible: nothing in Section 7 requires abandoning the way this repository documents itself — only extending it to readers who never open a `.kt` file.

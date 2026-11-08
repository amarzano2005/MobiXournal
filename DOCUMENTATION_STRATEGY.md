# MobiXournal documentation strategy and implementation plan

**Input:** [`DOCUMENTATION_AUDIT.md`](DOCUMENTATION_AUDIT.md), re-validated against the tree on 2026-11-08.
**Constraints honoured here:** no existing document was modified; no capability is described that the code does not have; every technical statement below was re-checked against source, build files or measurements taken for this plan; user-facing pages are planned as task pages, not feature inventories.

**What this document is:** the plan that turns the audit into work — an information architecture, a page-by-page blueprint, a phased roadmap, and the decisions that need a human before they can be written.

**This plan's own file:** `DOCUMENTATION_STRATEGY.md`, at the repository root beside `DOCUMENTATION_AUDIT.md`. It is a planning record, not product documentation; §6.6 decides where both records live once the plan is accepted.

## 1. Executive summary

MobiXournal has a documentation corpus built for its maintainers: 49 spokes, each opening with what to read when changing the code, one owner per fact, guards that fail the build on drift. It has no documentation for the people who install and use the product, and three sentences in it are false about shipped behaviour.

This plan adds the missing layer and repairs the false statements, without discarding what works:

- **A user layer of 15 task pages** (`docs/guide/`) that never restate a mechanism: each page says what to do and links the spoke that owns how it works.
- **One owner for status** (`docs/guide/limitations.md`), in the six words the project already mandates, replacing four scattered sources and two retired vocabularies.
- **A migration and compatibility page** for Xournal++ users, which is the product's strongest differentiator and its least documented story.
- **A developer entry point** (`docs/developer-guide.md`) that routes to the conventions now buried in an agent-facing file, and a `CONTRIBUTING.md` for people outside the repository.
- **Four merges and four splits** decided by topic rather than by the 300-line budget, and one folder move — last, because it is the only structural change the guards cannot absorb today.
- **A guard extension** so the new layer is enforced mechanically instead of by memory.

Net effect: **54 → 72 Markdown files** (18 new pages, 4 merges, 4 splits), with no fact duplicated and no existing spoke rewritten beyond the accuracy corrections.

**Effort:** 10–14 person-days for one writer who knows the codebase, of which Phase 0 (the false statements) is half a day and the highest-value Phase 1 is two days. **Sequence:** accuracy → trust (limitations, troubleshooting, install) → tasks → structure → hardening.

**How success is measured** (all checkable, none of them vibes):

1. A person who has never seen the app can install it, write a page, save it and reopen it using only `README.md` and the guide — the acceptance walk in §5.
2. Every realistic support question in §4.1 has exactly one page that answers it, reachable in two clicks from the README.
3. No page states a capability without a status word from `docs/writing-docs.md`, and the status words exist in exactly one file.
4. No document names a fact it does not own; the copied-sentence and title guards stay green.
5. The four false statements of Phase 0 are pinned by `DocClaimsTest`, so they cannot return silently.

---

## 2. Validated audit findings

### 2.1 Method

Every audit finding was re-checked in three ways: the cited line was re-read in the working tree; any behavioural claim was traced to the code that executes it; any number was recomputed here rather than trusted. Verdicts:

- **Confirmed** — reproduced exactly, evidence re-pinned to the current line.
- **Corrected** — the finding stands but its evidence was imprecise (wrong line, understated count).
- **Retracted** — not reproducible; the audit's claim is withdrawn.

Severity is the audit's, re-judged by user harm. **Impact class** is the consequence for a real reader: **I1** blocks onboarding, **I2** leads to a wrong decision or data-safety fear, **I3** wastes work or support time, **I4** erodes trust in the corpus, **I5** reading friction. **Effort** is XS (<2 h), S (half a day), M (1–2 days), L (3–5 days), measured for one writer with codebase knowledge.

### 2.2 The register

| ID | Finding | Verdict | Re-checked evidence | Sev. | Impact | Effort | Depends on |
|---|---|---|---|---|---|---|---|
| F-01 | No user-facing documentation exists | Confirmed | 49 of 49 spokes carry a change-oriented reader line; only `README.md` and `docs/releases/` address a user | Critical | I1 | L | Phase 0 (so pages do not repeat false claims) |
| F-02 | README promises conversion the window refuses | Confirmed | [README.md:46](README.md#L46) "converts cleanly in either direction" vs the packaged-`.xopp` refusal at [desktop-documents.md:29](docs/desktop-documents.md#L29) | High | I2 | XS | Troubleshooting page (link target) |
| F-03 | Merge engine described as having no caller | Confirmed | [architecture.md:16](docs/architecture.md#L16), [xopp-bridge.md:108](docs/xopp-bridge.md#L108) vs the call at [DocumentIo.kt:261](app/src/main/java/com/mobixournal/io/DocumentIo.kt#L261) into `MobixMergeEngine.merge` at [:335](app/src/main/java/com/mobixournal/io/DocumentIo.kt#L335) | Critical | I2 | XS | A product decision on surfacing the notification (§6) |
| F-04 | Data tables declared editor-less | Confirmed | [mobix-format.md:42](docs/mobix-format.md#L42) "no editor exists" vs the live dialog at [EditorRegions.kt:1700](app/src/main/java/com/mobixournal/ui/EditorRegions.kt#L1700) | Critical | I3 | XS | None |
| F-05 | Typst advertised as a typesetter | Confirmed | [README.md:62](README.md#L62) and [v2.0.0.md:39](docs/releases/v2.0.0.md#L39); `MathFlavor.TYPST` is source preservation, typeset through the LaTeX layout engine | Critical | I2 | XS (qualify) or L (implement) | Human decision (§6.1) |
| F-06 | Repository map promises more than it delivers; wrapper pinned wrong | Corrected | Wrapper is `gradle-8.14.5-bin.zip` (doc: "pinned 8.9"). Coverage is worse than the audit said: **702** Kotlin files under `app/src/main` (293), `mobix-core/src/main` (168) and `desktop/src/main` (241); **266** basenames appear anywhere in the 292-line doc | High | I3, I4 | M | None |
| F-07 | A "measured" table is stale | Confirmed | Re-measured: doc 179/229/69/375/666/581/683/341/152 vs actual **183/252/111/403/540/482/506/409/152** — 8 of 9 drift, `ElementEdits` matches | High | I4 | S | Decision: delete or generate (§6.5) |
| F-08 | Status lives in three vocabularies, owned by nobody | Confirmed | Six mandated words appear only in `writing-docs.md`; [mobix-format.md:38](docs/mobix-format.md#L38) uses Live / Model only / Stored & verified; README and release notes use prose | High | I2, I4 | M | Status decisions per capability (owner sign-off) |
| F-09 | Failure messages documented only for developers | Confirmed | The verbatim copy sits in [desktop-documents.md:27](docs/desktop-documents.md#L27) onward, under a change-oriented reader line | High | I1 | S | None — content exists |
| F-10 | Every route goes through a subsystem index | Confirmed | 39 of 49 spokes are not linked from `README.md`; `architecture.md` is the only index | High | I1 | M | Phase 1 pages must exist first |
| F-11 | Polar plots presented as placeable | Confirmed | [math-engine.md:93](docs/math-engine.md#L93) vs [MultiFunctionPlotter.kt:113](mobix-core/src/main/java/com/mobixournal/render/MultiFunctionPlotter.kt#L113) "places `y = f(x)` and nothing else" | Medium | I3 | XS | None |
| F-12 | Releases have no index, no shape, one no title | Corrected | `grep -c '^# '` gives **0** for `v1.1.0.md`; five standalone files, no index; **3 of 5 carry no date** (only `v2.0.0.md` 2026-11-06 and `desktop-v1.0.1.md` 2026-11-07 do) | Medium | I1, I3 | S | None |
| F-13 | Reference prose carries undifferentiated anecdote | Confirmed | Present in `desktop-documents.md`, `chrome.md`, `pdf.md`, `drawing-tools.md`; no marker separates rule from incident | Medium | I5 | M | A `writing-docs.md` convention |
| F-14 | The corpus's own vocabulary is undefined | Confirmed | No glossary; ~18 coined terms in active use | Medium | I1, I5 | M | Glossary needs the status page's vocabulary |
| F-15 | No contributor entry point | Confirmed | Conventions only in `AGENTS.md` (agent-addressed); no report template | Medium | I3 | S | None |
| F-16 | Front door is dense (1 182 words, 35–55-word bullets) | Confirmed | Measured on the current file | Low | I5 | S | Phase 3 rewrite |

### 2.3 What validation changed

Three things are different from the audit, and the plan uses the corrected versions:

1. **F-06's count was understated.** The audit said ~140 unlisted Kotlin files; measured, it is **702 files, of which 266 names appear**, and the desktop module — 241 files — is described only as a module paragraph. The finding is stronger, not weaker.
2. **Three citations were off by 1–2 lines** and are re-pinned above: `architecture.md` 14 → **16**, `xopp-bridge.md` 107 → **108**, `desktop-documents.md` 20 → **29** (the refusal paragraph), `README.md` 70 → **69** (offline fallback). Behavioural claims were unaffected; a reader following the old anchors would land beside the sentence.
3. **One arithmetic inconsistency was found while validating:** `v2.0.0.md` states "330 changes" and, in the same sentence, 303 + 43 + 32 + 16 + 5 + 5 = **404**. Either the total or the breakdown is wrong. It is recorded as a Phase 0 correction, not as a documentation failure — release notes are generated from the work archive, so the fix belongs to the generator.
4. **The audit's date statement was too generous to the corpus.** It said only `v2.0.0.md` carries a release date; measured, **two of five** do (`v2.0.0.md`, `desktop-v1.0.1.md`), so three releases cannot be placed on a timeline. F-12 stands with that correction.

**Retracted: none.** Every finding reproduced. The unverified-claims list (audit §9) is deliberately *not* used to justify any page: a claim that cannot be verified today is not a reason to write, it is a reason to measure or soften.

### 2.4 What the register implies

- **Four items are Phase-0 work** (F-02, F-03, F-04, F-05 + the release arithmetic): each is a sentence, each is XS, and each currently sends a reader to a wrong action. No page should be written before them, because the new pages would inherit the errors.
- **The user layer is one project, not eighteen** (F-01, F-09, F-10, F-14, F-16): the pages share a vocabulary, an index and a link convention; writing them separately produces the same drift the corpus already has.
- **Two items need a decision before writing** (F-03's notification, F-06/F-07's tables, F-08's status words): they are questions for the owner, collected in §6.
- **The structural work is last** (F-06, F-13, splits/merges/folders): it is the only work that can be deferred without leaving a reader without an answer.

---

## 3. Proposed information architecture

### 3.1 Principles

1. **Two layers, one fact.** A guide page says what to do and what it means for the user; a spoke says how it works and why. The mechanism lives only in the spoke; the guide links it. Nothing is copied, so the copied-sentence guard keeps working.
2. **Titles are the reader's words.** In the guide a title is the job ("Annotate a PDF"); in the spokes it stays the subject ("The paint pipeline and page rasterisation"). Two layers, two naming systems, no shared headings.
3. **Platform is an axis, not a prefix.** Install pages are per platform, every task page states where behaviour differs, and no page silently means one shell.
4. **Status is owned once.** `docs/guide/limitations.md` is the only page that states whether a capability works, in the six words already mandated by `docs/writing-docs.md`; every other page links it.
5. **The existing mechanics survive.** Reader line, one owner per fact, ≤300 lines, no copied sentences, registration in an index — all apply to the new layer. The change is additive, not a replacement.

### 3.2 The layers

```
                    ┌──────────────────────────────┐
   A1  A2  A3  ───▶ │  README.md  →  docs/guide/   │  what to do
                    └──────────────┬───────────────┘
                                   │ "How it works"
                    ┌──────────────▼───────────────┐
   A4  A5  ────────▶│  developer-guide.md  →  docs/ │  how and why it works
                    └──────────────┬───────────────┘
                                   │ one owner per fact
                    ┌──────────────▼───────────────┐
   A4      ────────▶│  architecture.md (the index)  │  where the owner is
                    └──────────────────────────────┘
```

Three entry points, each with one job: the README sells and installs; the guide routes by task; the developer guide routes by subsystem. The architecture index stays the developer's map and stops being the only map.

### 3.3 Target tree

```
README.md                     rewrite: what it is · 2 install paths · 2 screenshots · guide + releases
CONTRIBUTING.md               NEW  report template, patch rules, licence/credit
docs/
  guide/                      NEW layer (A1, A2, A3) — 15 pages
    index.md                  task index, platform-split, glossary link
    install-android.md
    install-windows.md
    first-notebook.md
    formats-and-conversion.md
    xournalpp-migration.md
    inking-and-gestures.md
    stem-tools.md
    pdf-and-audio.md
    search-and-organise.md
    settings-and-shortcuts.md
    troubleshooting.md
    faq.md
    limitations.md            the only status owner
    glossary.md
  releases/
    index.md                  NEW  version · platform · date · one line · link
    v1.1.0.md … v2.0.0.md, desktop-v1.0.1.md   (kept; structure fixed)
  developer-guide.md          NEW  routes A4/A5 to conventions, guards, pipelines
  architecture.md             kept; gains the guide-layer pointer and the three corrected sentences
  writing-docs.md             kept; gains the rule-then-Decision convention (§6.2)
  formats/                    MOVED  mobix-format · mobix-schema-spec · mobix-elements ·
                              mobix-living-entities · xopp-format · xopp-bridge ·
                              mobix-streaming-api · chunked-documents (+ infinite-canvas merged)
  android/                    MOVED  chrome (+ chrome-frame merged) · chrome-settings ·
                              interface-contract · ui-modernization · colours · pages-and-paper ·
                              drawing-tools · element-authoring · selection · gestures · stylus ·
                              search · audio (+ audio-inking-sync merged) · shape-perfection-engine
  desktop/                    MOVED  desktop-workstation · desktop-ui-layout · desktop-settings ·
                              desktop-documents · desktop-editing · desktop-pen-feel ·
                              desktop-tablet
  internals/                  MOVED  data-path · document-session · rendering · pdf (split) ·
                              images · text-and-markdown · math-engine · code-structure ·
                              repo-layout · codebase · stack · testing (+ desktop-testing merged) ·
                              tools · tools-emulator
```

File arithmetic: **54 today** (49 spokes + 5 releases). **+18 new** (15 guide pages, `CONTRIBUTING.md`, `releases/index.md`, `developer-guide.md`), **−4 merged**, **+4 from splits** = **72**. The current root audit stays outside this count; it is a record, not product documentation (see §6.6).

### 3.4 Navigation rules (each mechanically checkable)

| Rule | Statement | Enforced by |
|---|---|---|
| N1 | Every guide page opens with its audience and the platform(s) it covers, then the questions it answers in order | A reader-line check for the guide layer |
| N2 | Every guide page ends with **How it works**, linking at least one spoke | `DocsGuardTest` (new rule) |
| N3 | Every spoke whose task has a page ends its reader block with **For the task**, linking it | `DocsGuardTest` (link presence) |
| N4 | Every page is listed in `docs/guide/index.md` or `docs/architecture.md` — no page reachable only from another page's body | Existing index check, extended |
| N5 | No guide page states a capability without a status word; the words are defined and listed only in `limitations.md` | `DocClaimsTest` (allowed vocabulary + single owner) |

### 3.5 Naming conventions

- Guide: `verb-or-object-in-reader-language.md` — `first-notebook.md`, `formats-and-conversion.md`, `pdf-and-audio.md`. No subsystem names, no numbers, no `desktop-` prefix; platform appears in the title only where the page is platform-specific (`install-android.md`).
- Hubs always `index.md`; the developer entry point is a single page (`developer-guide.md`) so it can be linked from GitHub's file list.
- Spokes keep their current names: they are stable anchors inside the codebase's vocabulary and renaming them buys nothing for a reader.

### 3.6 The one structural constraint, and why the move is last

`DocsGuardTest` currently treats `docs/` as flat: the index check walks direct children, and the 300-line, title, anchor and copied-sentence rules follow file names. A folder move therefore changes the guard in the same commit — which is exactly the kind of mixed change that makes a failure hard to attribute. The plan therefore ships **Phase 1 with flat names** (`docs/guide-*.md`), then moves files in **Phase 3, one commit with no content change**, then repoints the guard.

---

## 4. Complete documentation blueprint

Every proposed page below is specified the same way: who it is for, the job it does, the questions it must answer, its section outline, and the pages it must link. The outline is a contract — a page that grows a section outside it belongs in a different page.

**A note on facts:** no page below introduces a capability. Each is assembled from an existing spoke, with the reader changed; where a fact is currently unverified (audit §9) the page either omits it or links the measurement, and never states a number it cannot reproduce.

### 4.1 `README.md` (rewrite)

**Audience:** A1 first, A2 second, A5 briefly.
**Purpose:** in one screen, let a newcomer decide whether this is their tool, and get it running.
**Must answer:** What is it, and on what platforms? Which file do I download? Does it need an account, a cloud or a JDK? What happens to my Xournal++ files? Where do I go next?
**Outline:** 1. One-sentence identity and the two platforms. 2. One screenshot per platform, labelled. 3. *Get it running*: two rows (Android APK, Windows MSI) with requirements inline. 4. *Two formats, one document*: three sentences in consequences, no container internals. 5. *What it is good at*: five bullets, ≤20 words each, no numbers that need a date. 6. *Documentation*: guide, troubleshooting, releases. 7. *Build from source*: one line pointing at the developer guide. 8. Licence and credits.
**Links:** `docs/guide/index.md`, `docs/guide/install-android.md`, `docs/guide/install-windows.md`, `docs/guide/formats-and-conversion.md`, `docs/releases/index.md`, `docs/developer-guide.md`, `CONTRIBUTING.md`.
**Budgets:** ≤160 lines, ≤1 400 words (existing guard), no paragraph over 1 200 characters.

### 4.2 `CONTRIBUTING.md` (new)

**Audience:** A5.
**Purpose:** make a report actionable and a patch landable on the first attempt.
**Must answer:** What do I include in a bug report? Which version am I on and where do I read it? What must a patch satisfy before review? Why did CI fail on a documentation rule? What licence am I contributing under?
**Outline:** 1. Report a problem: five fields (platform, version, what you did, what happened, the message verbatim) and where the app shows its version. 2. Before you open an issue: the troubleshooting page. 3. Change the code: build/test front door links, the guards that will fail, the 700/100-line rules, the interface contract. 4. Change the docs: the writing standard, one owner per fact, the status vocabulary. 5. Licence, credits, and the NeXopp/Xournal++ lineage.
**Links:** `docs/guide/troubleshooting.md`, `docs/developer-guide.md`, `docs/writing-docs.md`, `docs/interface-contract.md`, `docs/codebase.md`, `NOTICE`, `LICENSE`.

### 4.3 `docs/guide/index.md` (new)

**Audience:** A1, A2, A3.
**Purpose:** the task router — the page a user opens instead of the architecture index.
**Must answer:** I want to do X; where is the page? Is this page for Android or Windows? I do not know the word the app uses; where is the glossary?
**Outline:** 1. First time here: three links (install Android, install Windows, what the app is). 2. *I want to…*: a table of jobs grouped by intent (write, annotate, record, find, configure, recover), each row naming platform differences. 3. *Coming from Xournal++*: migration page. 4. *Something is wrong*: troubleshooting, FAQ. 5. *What works where*: limitations. 6. *Words*: glossary. 7. The developer layer, for people who arrived by mistake.
**Links:** all 14 other guide pages, `docs/architecture.md`, `docs/developer-guide.md`.
**Rule:** it contains no facts — only routing. It is the only guide page allowed to be a list.

### 4.4 `docs/guide/install-android.md` (new)

**Audience:** A1 on Android.
**Purpose:** from download to first stroke, with the requirements stated before the download.
**Must answer:** Which Android version do I need (8.0+, API 26)? Where is the APK and is it safe? Will my pen work — active or passive? Where do files go, and can I use Drive/Nextcloud? Why does installing over an older build keep my data? What if I already have a `.xopp`?
**Outline:** 1. Requirements. 2. Install from the release page; the debug APK note (same `applicationId`, upgrades in place). 3. First launch: what you see, one labelled screenshot. 4. Where documents live: storage access, cloud folders, staged writes in plain words. 5. Opening an existing `.xopp`. 6. Uninstall and what it leaves behind.
**Links:** `install-windows.md` (other platform), `first-notebook.md`, `formats-and-conversion.md`, `xournalpp-migration.md`, `troubleshooting.md`.

### 4.5 `docs/guide/install-windows.md` (new)

**Audience:** A1 on Windows 11.
**Purpose:** install the MSI without being frightened by SmartScreen, and know what it did to the system.
**Must answer:** Does it need Java? What does the installer change (per-user, `%LOCALAPPDATA%`, file associations, desktop shortcut)? Why is it unsigned, and what does Windows say? How do I uninstall, and what stays behind? Is there a portable build? Which desktop version is this?
**Outline:** 1. Requirements (Windows 11 x64, no JDK). 2. The MSI: what it installs, the two association checkboxes. 3. The unsigned-installer paragraph: why, what SmartScreen shows, what to do. 4. The folder build as the alternative. 5. First launch and the two panes. 6. Uninstall and leftovers (settings under `%APPDATA%`). 7. Version statement linking the release index.
**Links:** `docs/releases/index.md`, `first-notebook.md`, `settings-and-shortcuts.md`, `desktop`-spoke for the window anatomy, `troubleshooting.md`.

### 4.6 `docs/guide/first-notebook.md` (new)

**Audience:** A1, A3.
**Purpose:** produce and reopen a correct document in five minutes — the walk the whole guide is judged by.
**Must answer:** How do I create a notebook and pick a paper? How do I write, erase and undo? How do I save, and where did it go? What format did I just create, and does it matter now? How do I reopen it tomorrow?
**Outline:** 1. New document, paper choice. 2. Ink, eraser, undo — the three interactions that must work before anything else. 3. Save and Save As, in user language: the document keeps its format. 4. Reopen, and what "recent" means. 5. Where to go next: the three most likely second tasks.
**Links:** `formats-and-conversion.md`, `inking-and-gestures.md`, `stem-tools.md`, `docs/pages-and-paper.md` (mechanism).

### 4.7 `docs/guide/formats-and-conversion.md` (new)

**Audience:** A1, A2, A3.
**Purpose:** remove format anxiety — the single highest-friction concept in the product.
**Must answer:** What is the difference between `.mobix` and `.xopp` in consequences? Which one does my document use? What happens if I use Save As? What survives conversion (editable elements, audio, PDF backgrounds) and what becomes ink? What is refused, and why is that safe?
**Outline:** 1. The rule in one paragraph: a document remembers its own format. 2. `.xopp`: interchange, opens unchanged, saves back as `.xopp`. 3. `.mobix`: native, keeps living elements, telemetry, bundled audio. 4. Save As: what converts, what becomes ink. 5. The one documented refusal (packaged `.xopp` with an embedded background, opened in the desktop window) with the two ways round it. 6. What never converts silently. 7. Pointers to the contract page for readers who want the digest rules.
**Links:** `xournalpp-migration.md`, `troubleshooting.md`, `limitations.md`, `docs/mobix-format.md`, `docs/xopp-bridge.md` (mechanism).

### 4.8 `docs/guide/xournalpp-migration.md` (new)

**Audience:** A2.
**Purpose:** let a Xournal++ user decide in two minutes, and migrate without surprises.
**Must answer:** Is this affiliated with Xournal++? Will my notebooks open? Will they still open in Xournal++ afterwards? What happens if I edit in both? Do my audio sidecars and PDF backgrounds come along? Can I keep working in `.xopp` forever? What is different in the ink?
**Outline:** 1. Affiliation and lineage, plainly. 2. Open a `.xopp`: what carries over. 3. Round-trip: living elements become standard ink with metadata; revival rules, and what "edited elsewhere" means for them. 4. Audio sidecars and PDF backgrounds. 5. Working in both directions day to day. 6. What deliberately differs (e.g. drawn width follows desktop's pressure filter; app offers no unbounded canvas). 7. When to switch format and when not to.
**Links:** `formats-and-conversion.md`, `limitations.md`, `docs/xopp-bridge.md`, `docs/xopp-format.md`, `docs/codebase.md` (lineage).

### 4.9 `docs/guide/inking-and-gestures.md` (new)

**Audience:** A3.
**Purpose:** make the pen behave as the user expects, and explain the interactions that are not obvious from a toolbar.
**Must answer:** How is stroke width decided, and can I change it? What does palm rejection cover, and what does it not? What does the pen's barrel button do? What is the ring menu and when does it appear? Why did my circle become a perfect circle — and how do I stop that? How do I move a page's content down?
**Outline:** 1. Pens: active vs passive, pressure, tilt (what is used and what is not). 2. Shortcuts and the ring. 3. Hold-to-snap and continuous recognition; how to cancel it. 4. Highlighter behaviour over text. 5. Selecting, moving, resizing; vertical space. 6. Undo granularity: one gesture, one step. 7. Where each behaviour is explained for developers.
**Links:** `settings-and-shortcuts.md`, `docs/stylus.md`, `docs/gestures.md`, `docs/shape-perfection-engine.md`, `docs/selection.md`.

### 4.10 `docs/guide/stem-tools.md` (new)

**Audience:** A3, the reason many users are here.
**Purpose:** one page per STEM object, in the order a person reaches for them, each ending with what the object is *not*.
**Must answer:** How do I place a plot, and can I edit its formula afterwards? How do I edit a table cell? Can I place a parametric or polar curve? How do I draw a circuit and join wires? Chemistry bonds? Where is the formula tool, and which language is typeset? Do the guides snap, and to what?
**Outline:** 1. The common model: a placed object stays editable; one placement is one undo step. 2. Functions: plot, analysis, sliders, shaded integral; placement boundary for parametric/polar stated here. 3. Tables: place, edit cells, header row. 4. Circuits and gates: parts, pins, snapping. 5. Chemistry and dimensioning. 6. Formulas: LaTeX typeset, Typst preserved (status word, link). 7. Drafting guides and paper snapping.
**Links:** `limitations.md`, `docs/drawing-tools.md`, `docs/element-authoring.md`, `docs/math-engine.md`, `docs/mobix-living-entities.md`.

### 4.11 `docs/guide/pdf-and-audio.md` (new)

**Audience:** A3.
**Purpose:** the two headline workflows, each with the window's and the tablet's answer.
**Must answer:** How do I import a PDF, all of it or a page range? Can I copy text out of it? What does the highlighter do differently on PDF text? Where is the PDF's outline? How do I record while writing, and jump back to a stroke? Can I export the audio? What happens to my recording in a `.xopp`?
**Outline:** 1. PDF as a page background; import and append. 2. Text selection and copy. 3. Magnetic highlighter. 4. Contents/outline navigation. 5. Recording: per format, per platform. 6. Replay by stroke, speed control, scrubbing. 7. Audio export and what a `.xopp` gets instead. 8. Known limits.
**Links:** `formats-and-conversion.md`, `docs/pdf.md`, `docs/audio.md`, `docs/desktop-editing.md` (the window's half).

### 4.12 `docs/guide/search-and-organise.md` (new)

**Audience:** A3.
**Purpose:** find things in a long notebook and rearrange a document without fear.
**Must answer:** What can I search — typed text, handwriting, formulas, PDF text? Does it work offline? Why did a handwritten word not match? How do I reorder, duplicate or copy pages? What is a bookmark? How do tabs and split view relate?
**Outline:** 1. What a query searches, per platform. 2. Recognition engines and their status (download/caching stated as status, not promise). 3. Pages: add, remove, reorder, duplicate, copy across tabs. 4. Bookmarks and the page list. 5. Tabs and split view, including the live mirror. 6. Layers, briefly.
**Links:** `limitations.md`, `docs/search.md`, `docs/pages-and-paper.md`, `docs/document-session.md`.

### 4.13 `docs/guide/settings-and-shortcuts.md` (new)

**Audience:** A3.
**Purpose:** the only user-facing settings and keys reference, for both shells.
**Must answer:** Where is setting X? What does it change? What is the shortcut for tool Y? How do I back up or move my settings? How do I reset the pen curve?
**Outline:** 1. Opening Settings on each shell and how search works. 2. Section-by-section table: what a user changes and what it affects. 3. The shortcut table (moved out of `desktop-ui-layout.md`), one column per shell. 4. Pen presets and calibration. 5. Backup and restore.
**Links:** `docs/desktop-settings.md` (store keys), `docs/chrome-settings.md`, `docs/ui-modernization.md` (shortcut source until moved), `docs/desktop-ui-layout.md`.

### 4.14 `docs/guide/troubleshooting.md` (new)

**Audience:** A1–A3, at the moment of failure.
**Purpose:** every failure the product can report, verbatim, with the next action.
**Must answer:** The window says it is stale — what now? My save was refused; is my file safe? Where did the crash snapshot go, and what does "(recovered)" mean? The file will not open and the message names a PDF/ZIP/text — why? I edited in Xournal++ and my plot is now ink — why? Where do I report this?
**Outline:** 1. Open failures: the three sentences and their causes. 2. Save failures: refusal, I/O, stale window; the staged-file guarantee in plain words. 3. Recovery: where the snapshot is, what happens on save/discard, what is deleted. 4. Files that are not documents. 5. Editing elsewhere and revival. 6. Collecting evidence for a report. 7. If nothing here helps, report it.
**Links:** `CONTRIBUTING.md`, `formats-and-conversion.md`, `docs/desktop-documents.md` (mechanism), `docs/data-path.md`.

### 4.15 `docs/guide/faq.md` (new)

**Audience:** A1, A2.
**Purpose:** the twelve questions that are not tasks, answered in two sentences each, each pointing at the owner.
**Must answer:** Is it free, and under what licence? Is it affiliated with Xournal++? Does it need an account or the internet? Where are my files? Can I sync or merge two copies? Is there an iOS/macOS version? Does it work with a mouse? Why are there two version numbers? Can I open a `.xopp` in Xournal++ after saving here? What is a "living" element?
**Outline:** twelve questions, alphabetical, each: the answer, then *More:* link. No new facts — each answer is a sentence plus a pointer.
**Links:** `limitations.md`, `formats-and-conversion.md`, `xournalpp-migration.md`, `docs/codebase.md` (licence/lineage).
**Rule:** the FAQ must not be the only home of any fact; if an answer exists nowhere else, it belongs in a task page first.

### 4.16 `docs/guide/limitations.md` (new) — the status owner

**Audience:** A1–A4.
**Purpose:** answer "does this work, here, now?" once, for the whole product.
**Must answer:** Is capability X supported on Android / Windows? Is it partial, experimental, planned or unsupported? What is the evidence? What is unverified?
**Outline:** 1. How to read the six status words (link to the standard, one line each). 2. The matrix: capability | Android | Windows | evidence link. 3. Known gaps with a reason (no sync UI; no unbounded canvas creation; no Typst typesetter; parametric/polar not placeable). 4. Platform differences that are deliberate. 5. What "not verified" means and where the list lives.
**Links:** `docs/writing-docs.md` (vocabulary), every spoke that owns a row, `troubleshooting.md`.
**Rule:** any other page that needs a status word states it by linking here, never by restating the matrix.

### 4.17 `docs/guide/glossary.md` (new)

**Audience:** A1–A5.
**Purpose:** decode the product's vocabulary and map it onto the words a Xournal++ user already owns.
**Must answer:** What is a living element? What is the rail / dock / band / sheet? What is a sidecar? What is a chunked document? What does "hold to snap" mean? What does Xournal++ call the same thing?
**Outline:** one table: term | what it is (one sentence) | Xournal++ or everyday equivalent | owning page. Grouped: document and formats, canvas and chrome, pen and input, STEM objects, engineering terms (last group marked A4).
**Links:** the owner of each term; `limitations.md`.

### 4.18 `docs/releases/index.md` (new)

**Audience:** A1–A3.
**Purpose:** the version timeline the corpus does not have.
**Must answer:** What is the current Android version and the current Windows version? When was each released? What changed? Where do I download it?
**Outline:** 1. Current versions, two lines. 2. Table: version | platform | date | one-line summary | link. 3. Compatibility statement between Android and desktop versions (status word if unknown). 4. Note that notes older than the template carry only the information they were written with.
**Links:** the five note files, `limitations.md`, `install-android.md`, `install-windows.md`.

### 4.19 `docs/developer-guide.md` (new)

**Audience:** A4, A5.
**Purpose:** the front door for someone who is going to change the code, so `AGENTS.md` can stay an agent contract.
**Must answer:** How do I build and test? Where do conventions live? Which guard will fail if I break a rule? Where is the map of the tree? What must a UI change satisfy? How do I write a doc here?
**Outline:** 1. Build and test front doors (three commands, per host profile). 2. The five maps: architecture index, repo layout, code structure, testing, stack. 3. The rules the build enforces (guards, budgets). 4. Working conventions (commits, tasks, docs in the same commit). 5. Writing a doc. 6. Where the machine-specific profiles live.
**Links:** `AGENTS.md`, `docs/architecture.md`, `docs/code-structure.md`, `docs/repo-layout.md`, `docs/testing.md`, `docs/tools.md`, `docs/writing-docs.md`, `docs/interface-contract.md`, `docs/stack.md`.

### 4.20 `docs/architecture.md` (kept, three corrections and one link)

**Audience:** A4.
**Purpose:** unchanged — the index of owners.
**Must answer:** which spoke owns this topic.
**Changes (not a rewrite):** correct the merge sentence to "a three-way merge runs when a conflict sibling is opened; the report is not yet surfaced"; add one line pointing at the guide layer as the other index; register the guide pages so the index check stays total; add the *For the task* link rule to its spoke template.
**Links:** unchanged plus `docs/guide/index.md`.

### 4.21 The reference layer (existing spokes, regrouped)

**Audience:** A4, plus A2 for the format pages.
**Purpose:** remain the authoritative home of mechanism, unchanged in content.
**What changes:** folder grouping only (`formats/`, `android/`, `desktop/`, `internals/`), the four merges and four splits of §5, and the reader-block second line that points at the guide page. No mechanism is rewritten; no new reference page is proposed, because the 49 spokes already cover the system and adding a layer of summaries would create the second home the corpus forbids.

---

## 5. Prioritized implementation roadmap

Five phases, each independently shippable and revertable. Phase 0 is half a day and must land first; everything after it is additive.

### Phase 0 — Truth (0.5 day, blocks everything)

| Item | Work | Fixes | Effort |
|---|---|---|---|
| TR-01 | Rewrite the merge sentences: runs on conflict-sibling open, report not surfaced | F-03 | XS |
| TR-02 | Correct the data-table row to the status word and point at the owner | F-04 | XS |
| TR-03 | Qualify Typst in the README and the 2.0.0 note (LaTeX typeset; Typst preserved and marked) | F-05 | XS |
| TR-04 | Add the packaged-`.xopp` exception to the README's conversion promise | F-02 | XS |
| TR-05 | Qualify polar/parametric placement in the math page | F-11 | XS |
| TR-06 | Fix the release arithmetic (330 vs 404) at the generator, or drop the total | §2.3 | S |
| TR-07 | Pin TR-01…TR-05 wording in `DocClaimsTest` | all | S |

**Exit criteria:** no document states a false capability; the five corrected sentences are guarded; `DocsGuardTest` + `DocClaimsTest` green; no guide page exists yet.

### Phase 1 — Trust and entry (2–3 days)

| Item | Work | Fixes | Effort | Depends on |
|---|---|---|---|---|
| TR-10 | `guide-limitations.md` (status matrix; positions read from the tree, owner may overrule a cell) | F-08 | M | §6.3 positions |
| TR-11 | `troubleshooting.md` extracted from the window's failure copy | F-09 | S | TR-10 |
| TR-12 | `install-android.md`, `install-windows.md` | F-01 | M | TR-10 |
| TR-13 | `guide-index.md` (flat, like its pages; the folder move is TR-36) with the entry rows | F-10 | S | TR-10…TR-12 |
| TR-14 | README: replace *Try It* with the two guide links; leave the rest for Phase 3 | F-01, F-10 | XS | TR-12 |
| TR-15 | Extend `DocsGuardTest` for the guide layer (registration, task line, cross-link) | N1–N4 | M | TR-10…TR-13 |

**Exit criteria:** a newcomer installs from the README, and any failure message they meet has a page; the status page answers "does it work here?" in one screen.

### Phase 2 — Tasks (4–5 days)

| Item | Work | Fixes | Effort |
|---|---|---|---|
| TR-20 | `first-notebook.md` | F-01 | M |
| TR-21 | `formats-and-conversion.md` + `xournalpp-migration.md` | F-01, F-02, A9 | M |
| TR-22 | `inking-and-gestures.md`, `stem-tools.md` | F-01, F-11 | L |
| TR-23 | `pdf-and-audio.md`, `search-and-organise.md` | F-01 | L |
| TR-24 | `settings-and-shortcuts.md` (move the shortcut table) | P4 (audit) | M |
| TR-25 | `faq.md`, `glossary.md` | F-14 | M |
| TR-26 | Each completed spoke gains its **For the task** line | N3 | S |

**Exit criteria:** every realistic query in audit §6.1 lands on a page within two clicks; each guide page links at least one owner; nothing in the guide restates mechanism.

### Phase 3 — Structure (3–4 days)

| Item | Work | Fixes | Effort |
|---|---|---|---|
| TR-30 | Merge `audio-inking-sync` → `audio`, `chrome-frame` → `chrome`, `infinite-canvas` → `chunked-documents`, `desktop-testing` → `testing` | P4 | M |
| TR-31 | Split `pdf`, `selection`, `tools`, `drawing-tools` | P4 | L |
| TR-32 | `releases/index.md` + template + dates + the missing H1 | F-12 | S |
| TR-33 | `developer-guide.md`, `CONTRIBUTING.md`, register `tools-emulator` normally | F-15 | M |
| TR-34 | Delete the rotting tables; fix the wrapper pin; keep the narrative | F-06, F-07 | S |
| TR-35 | README rewrite (≤160 lines, ≤1 400 words, ≤20-word bullets) | F-16 | S |
| TR-36 | Folder move + recursive guard + link check, one commit | §3.6 | M |

**Exit criteria:** no page over 300 lines; no page states a measurement it cannot regenerate; the folder move changes no content; every page is registered and linked.

### Phase 4 — Hardening (1–2 days)

| Item | Work | Effort |
|---|---|---|
| TR-40 | Guard the status vocabulary (words only in `limitations.md`; other pages link) | M |
| TR-41 | A docs check in the PR/commit checklist: reader line, one home, status word, guide link | S |
| TR-42 | Refresh screenshots (Profile D for the 21:9 window; tablet for Android), dated | S |
| TR-43 | Review cadence: status matrix and release index revisited at every release | XS |

**Could be done in Phase 0 if the owner wants a smaller first step:** TR-32 (release index) is independent of the guide and immediately useful. It is placed in Phase 3 only to keep Phase 0 a pure truth pass.

### Critical path and sequencing rules

1. **No guide page before Phase 0.** Writing a user page from a false sentence copies the falsehood into a second home, and the copied-sentence guard cannot catch a paraphrase.
2. **No folder move before the guard is taught.** A move plus a logic change in one commit is unattributable when it fails.
3. **No status word before its decision.** `limitations.md` cannot invent `Partially supported`; rows come from §6.3's rulings.
4. **No split before its merge.** Merging first reduces the page count and gives the splits a cleaner seam.

---

## 6. Risks, uncertainties and decisions that need a human

### 6.1 Typst: qualify or implement (blocks TR-03)

Two honest options. **Qualify** (XS): state that LaTeX is typeset and Typst sources are preserved and marked, in the README, the 2.0.0 note and `limitations.md`. **Implement** (L, and it is a product change): add a real Typst path or remove the flavour switch until one exists. The strategy recommends qualifying now and deciding the product question separately — a documentation plan must not promise a typesetter.

### 6.2 The merge notification: document the gap or close it (blocks TR-01 wording)

A three-way merge runs on conflict-sibling open and produces a message nothing displays. The docs can say "runs; report not surfaced" (honest, XS) or the product can surface it (a UI task, outside this plan). Writing the first without recording the second turns a documentation fix into a permanent excuse; the plan assumes the sentence **and** a follow-up issue.

### 6.3 Status rows need an owner's ruling (blocks TR-10)

Seven capabilities cannot be classified from the tree alone, because "supported" is a product judgement:

| Capability | Question for the owner |
|---|---|
| Handwriting search offline | Does a fresh install recognise handwriting with no network, or does the model download first? |
| Parametric and polar plots | Supported on disk and in drawing, not placeable from the plot tool: `Partially supported`? |
| Typst | `Partially supported` (preserved, not typeset) or `Experimental`? |
| Chunked documents | Opens them, cannot create them: `Partially supported`? |
| Sync / two copies | Merge runs, no UI: `Not supported` for sync, with a note? |
| Desktop MSI signing | `Not verified` for the published artifact until checked. |
| Stylus tilt/azimuth | Captured and stored, does not drive width: `Supported` for storage, `Not supported` for drawing? |

Positions were taken from the tree on 2026-11-08 and shipped in `guide-limitations.md`: six rows follow
the code and its guards, the MSI signature is **Not verified** because no artifact was checked, and each
cell names the evidence, so the owner's product judgement can overrule a cell without a rewrite.

### 6.4 The folder move is a one-way door for links

Forty-nine spokes live at `docs/*.md`; GitHub URLs, `FINISHED.toml` descriptions, commit messages and the guards all name them. Moving them breaks external anchors silently. Mitigation: move in one commit, add a link checker to the guard first, keep the old paths in a redirect table in `architecture.md` for one release, and accept that external deep links to old files will 404 (GitHub offers no redirect). The alternative — never moving — is acceptable too: the layers can stay flat with prefixes. **This is a decision, not a technical necessity.**

### 6.5 Rotting tables: generate or delete

`codebase.md`'s sizes and `repo-layout.md`'s file list both rot. Generate them from `git ls-files` inside a guard (one source of truth, fails when stale) or delete them and keep the narrative. Recommendation: delete the size table (it answers a question nobody asks) and reduce the layout page to modules and packages (the question newcomers do ask). Hand-maintained numbers must not survive this plan.

### 6.6 Where the audit lives

The audit is a root file. The root-orphan rule accepts it only because the file names itself — a loophole. Options: link it from `AGENTS.md` as the audit record, move it under `docs/` (it then needs a reader line and index registration), or delete it once the plan is accepted. Recommendation: keep it as the record, linked from `AGENTS.md`, and never treat it as user documentation.

### 6.7 Screenshots are a hardware dependency

The README and release notes use two real screenshots. New pages need labelled captures: the window at 21:9 on Profile D (where 144 Hz must first be enabled to represent the claim), the tablet in its normal orientation. If the hardware is unavailable, pages ship without screenshots rather than with mockups — a mockup is an invented capability.

### 6.8 Version drift between guide and release

A user guide is only true for a version. Mitigation: each guide page states the versions it describes (Android 2.0.0 / Desktop 1.0.1 at the time of writing), `releases/index.md` is the single place that changes, and the status matrix is reviewed at every release as part of TR-43. Risk accepted: prose can still lag a release by days; the guard cannot check meaning.

### 6.9 Effort and quality risks

- Estimates assume the writer knows the codebase; an outside writer doubles them, and the guide would need a technical reviewer per page.
- The largest quality risk is a guide that becomes a feature inventory. The structural defence is the outline contract in §4 (sections are fixed) plus `limitations.md` as the only capability table.
- The second risk is a guide that duplicates mechanism because the writer could not find the owner. The *How it works* rule (N2) makes the owner visible on every page.

### 6.10 Deliberately out of scope

No documentation site or theme, no generated API reference, no video, no localization (the product is English-only by policy), no changelog automation beyond `releases/index.md`, and no rewrite of the 49 spokes beyond the Phase 0 corrections and the merges/splits.

### 6.11 Open questions

1. Typst: qualify now, or schedule the typesetter? (§6.1)
2. Merge notification: document the gap, or ship the surface in the same release? (§6.2)
3. The seven status rulings of §6.3 — who signs them, and against which test evidence?
4. Folder move now or keep the flat tree? (§6.4)
5. Generate or delete the two rotting tables? (§6.5)
6. Does the audit stay in the repository? (§6.6)
7. Is the desktop MSI in the current release signed? (audit U12)
8. Which desktop version is "current" for the guide — 1.0.1, or does 2.0.x follow? (audit U10)

---

## 7. Why this structure is better, audience by audience

**A1 — the new user.** Today their only entry is a README that asks them to run `scripts/release-apk.sh` inside the first screen. After Phase 1 they read two download rows, open one install page written for their platform, and reach `first-notebook.md` in one more click — with the requirements (Android 8.0+, no JDK on Windows) stated before the download rather than after a failure. The install pages exist separately because the two installers fail differently: an APK has no SmartScreen, and an MSI has no file manager. One page serving both would have to explain neither.

**A2 — the Xournal++ user.** Today they get the word "losslessly" and a 235-line document about SHA-256 digests. After Phase 2 they get `xournalpp-migration.md`, which answers the five questions that decide the switch (opens unchanged, saves back as `.xopp`, elements revive unless edited elsewhere, sidecars and backgrounds come along, affiliation is none) and only then links the contract. This audience is the product's best acquisition channel and currently its worst-served one; the page is also the only place where the desktop refusal can be explained without sounding like a defect.

**A3 — the regular user with a job.** Today they must translate "annotate a PDF" into "open `pdf.md`, find the section on selection". After Phase 2 the guide index lists jobs, each page is titled with the job, and the page ends by naming the owner. The platform axis is explicit on every page that differs, so a tablet user is never sent to a window feature and vice versa. The two pages that change the most are `troubleshooting.md` (failure text, verbatim, with the next action) and `limitations.md` (one screen instead of four sources in three vocabularies).

**A4 — the developer.** Today they have a strong corpus with three false sentences and two rotting tables, and no way to tell status from prose. After Phase 0 the corpus stops lying; after Phase 3 the ceiling-driven artefacts are split by topic again; `developer-guide.md` gives a newcomer the five maps in one page instead of sending them into an agent-facing contract. Their reading cost goes **down**, because the guide absorbs the "what does the user see" context that currently lives in their reference pages as anecdote (F-13).

**A5 — the contributor.** Today the first interaction is an issue with no required fields, and the conventions are in a file addressed to AI agents. After Phase 3 `CONTRIBUTING.md` states the five report fields, the guards a patch will meet, and the licence position. `developer-guide.md` is the human-readable version of the same route, so `AGENTS.md` can stay what it is: a precise contract for agents, not the only onboarding a person gets.

**Why the two-layer split, rather than one bigger guide.** Every fact in this product has a mechanism behind it, and mechanisms change faster than tasks. A single layer would either freeze mechanism in user prose (drift, F-07) or leak class names into task pages (the failure the audit found everywhere). Two layers let each change at its own rate: a task page changes when the user's job changes, a spoke when the code does, and the pointer between them is the only thing that must stay true — which is exactly what a link check can enforce.

**Why status gets its own page.** Status is the one fact every audience asks for and no mechanism owns. Giving it a page with a guard-backed vocabulary turns four drifting sources into one reviewable table, and makes "is this supported?" a link instead of a research task.

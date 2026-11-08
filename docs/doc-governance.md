# Documentation governance: the rule, the matrix and the checks

> Read when a change you are making could invalidate a page, when you review a pull request, or when a
> page and the code have started to disagree.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md). How a page is
written is [writing-docs.md](writing-docs.md)'s; this page is who updates what, and what the build holds
to account._

Governance is three rules and the machinery that keeps them true. It is deliberately small: a process
that costs more than the inconsistency it prevents is the inconsistency, moved.

1. **A change carries its documentation**, in the same commit. [writing-docs.md](writing-docs.md) states
   the rule and its Definition of Done counts it.
2. **A fact has one home.** The map in [AGENTS.md](../AGENTS.md) names the owner of each category, and
   every other page links it. A second copy is a page that will disagree with the first.
3. **A disagreement is caught by the build, not by a reader.** What can be read out of the tree is
   asserted; what cannot is on the review checklist below.

## Who owns which fact

The categories, and the one page each is answered from. Where a category is not listed here, the map in
[AGENTS.md](../AGENTS.md) is the authority — it covers work in flight, the front doors and the tooling.

| Question | Owner |
|---|---|
| Is a capability there, and how far? | [guide-limitations.md](guide-limitations.md) — the only page that grades one |
| How does the system work inside? | [architecture.md](architecture.md), one spoke per topic |
| What does a document contain? | [mobix-format.md](mobix-format.md), [mobix-schema-spec.md](mobix-schema-spec.md), [mobix-elements.md](mobix-elements.md), [mobix-living-entities.md](mobix-living-entities.md) |
| What survives a `.xopp` round trip? | [xopp-bridge.md](xopp-bridge.md) |
| Where does code live, and which modules exist? | [repo-layout.md](repo-layout.md) |
| How is a page written? | [writing-docs.md](writing-docs.md) |
| How do I get the app, and use it? | [README.md](../README.md), then [the guide](guide-index.md) |
| What did a version ship? | [the release index](releases/index.md), one note per version |
| Who updates what, and what does the build check? | this page |

## What a change invalidates

Read the row your change lands in, before you write the commit message. The list is the **minimum**: a
change that spans two areas reads two rows, and a page no row names is a page the change does not
affect — do not touch it to look thorough.

| Area you changed | Pages to re-read |
|---|---|
| the `mobix-core` model, its codecs or the container | [mobix-format.md](mobix-format.md), [mobix-schema-spec.md](mobix-schema-spec.md), [data-path.md](data-path.md), [xopp-format.md](xopp-format.md) |
| a `.mobix` element or a living entity | [mobix-elements.md](mobix-elements.md), [mobix-living-entities.md](mobix-living-entities.md), [guide-stem-tools.md](guide-stem-tools.md) |
| the `.xopp` bridge or the per-document format policy | [xopp-bridge.md](xopp-bridge.md), [guide-formats-and-conversion.md](guide-formats-and-conversion.md), [guide-xournalpp-migration.md](guide-xournalpp-migration.md) |
| the math engine or the plot tool | [math-engine.md](math-engine.md), [function-plots.md](function-plots.md), [guide-stem-tools.md](guide-stem-tools.md) |
| the renderer, a PDF path or an export | [rendering.md](rendering.md), [pdf.md](pdf.md), [images.md](images.md), [text-and-markdown.md](text-and-markdown.md) |
| opening, saving or the session cache | [data-path.md](data-path.md), [document-session.md](document-session.md), [chunked-documents.md](chunked-documents.md) |
| the Android chrome, Settings or the tool strips | [chrome.md](chrome.md), [chrome-settings.md](chrome-settings.md), [ui-modernization.md](ui-modernization.md), [interface-contract.md](interface-contract.md) |
| the window under `desktop/` | [desktop-workstation.md](desktop-workstation.md), [desktop-documents.md](desktop-documents.md), [desktop-editing.md](desktop-editing.md), [desktop-settings.md](desktop-settings.md) |
| a pen, a tablet or a gesture | [stylus.md](stylus.md), [gestures.md](gestures.md), [desktop-tablet.md](desktop-tablet.md), [guide-inking-and-gestures.md](guide-inking-and-gestures.md) |
| recording, replay or karaoke | [audio.md](audio.md), [guide-pdf-and-audio.md](guide-pdf-and-audio.md) |
| search or recognition | [search.md](search.md), [guide-search-and-organise.md](guide-search-and-organise.md) |
| a Gradle module, a package, a dependency or a CI job | [repo-layout.md](repo-layout.md), [architecture.md](architecture.md), [testing.md](testing.md), [tools.md](tools.md) |
| a new user-facing feature | [README.md](../README.md), [the guide index](guide-index.md) and the page for that job, then [guide-limitations.md](guide-limitations.md) if it differs by shell or stops part-way |
| a removed or withdrawn feature | its guide page, [guide-limitations.md](guide-limitations.md), the format page if the file loses a field, and the release note that records the removal |
| anything that ships | [the release index](releases/index.md) — the note is drafted by the release command, never by hand |

Two of those rows are not judgement calls. Only the matrix may move a capability's word, and a removal
that leaves the promise standing is the one error a reader cannot forgive.

## When the README has to change

[README.md](../README.md) is the product-facing front door: what the app is, who it is for, how to get it and
where the truth lives. It summarises and links; it owns no technical fact. A change in any row below triggers a
README read, and the third column names the pages that own the detail.

| Change | README review | Also re-read |
|---|---|---|
| a new user-facing feature | **required** — the capability list and where it sits in the product story | the guide page for that job, [guide-limitations.md](guide-limitations.md) |
| a removed or changed feature | **required** — a promise left standing is the one error a reader cannot forgive | the guide page, [guide-limitations.md](guide-limitations.md), the format page if the file loses a field |
| Android- or Windows-only behaviour | **required** — the platform tables, and any claim of parity | [guide-limitations.md](guide-limitations.md), the shell's own spoke |
| installation or first-run steps | **required** — the two install lists | [guide-install-android.md](guide-install-android.md), [guide-install-windows.md](guide-install-windows.md), [tools.md](tools.md) |
| a release, a version or the distribution | **required** — the downloads it names | [releases/index.md](releases/index.md), the release note |
| a file format, the format policy or import/export | **required** — the two-format table and any refusal it states | [mobix-format.md](mobix-format.md), [xopp-bridge.md](xopp-bridge.md), [guide-formats-and-conversion.md](guide-formats-and-conversion.md) |
| a major UI change | **required when a screenshot or the product story is affected** | [interface-contract.md](interface-contract.md), the chrome spoke |
| internal refactoring with no user-facing effect | **not required** — an untouched README is the correct outcome | the spoke that owns the mechanism |

Two rules keep the table from becoming busywork: the README changes because a reader's answer moved, never to
tick a box; and a fact is corrected at its owner first, with the README's sentence following it.

## The change, in four steps

1. **Find the row above** and open the pages it names. Two rows means two readings; no row means the
   change is internal and its documentation is the comment beside it.
2. **Edit the owner first**, then the pages that link it. A page that quotes a rule is updated because
   the owner changed, not because the page was edited.
3. **Add a row** when the change is a capability that differs by shell, stops part-way or sits behind a
   switch — [guide-limitations.md](guide-limitations.md) has the rule for that page.
4. **Answer the pull-request checklist**, which is these four steps as eight questions:
   [.github/PULL_REQUEST_TEMPLATE.md](../.github/PULL_REQUEST_TEMPLATE.md). A pull request that leaves a
   box unticked is incomplete rather than fast.

## The periodic sweep

At every release, and once a quarter besides, one person walks this list. Nothing is deleted on sight: a
page nobody links may still be the only place a fact lives, so the finding is reported first and the
owner of that area decides.

| Finding | How it shows up | Remedy |
|---|---|---|
| a page nothing links | the guards keep every page reachable, so this is a page reachable only from one that is itself unreachable | register it in the index, merge it, or retire it after its content has moved |
| the same fact twice | a sentence pasted into two pages fails the build; a paraphrase does not | move the fact to its owner and leave the sentence that links it |
| a page older than the behaviour it quotes | a reader reporting the difference, or `DocClaimsTest` catching a sentence that was false once | correct the page, and add the claim to `DocClaimsTest` if it can recur |
| a feature documented and no longer built | a matrix row or a guide step describing a control nobody can find | the row changes word, or the page loses the step; the release note says what left |
| a module documented and removed | [repo-layout.md](repo-layout.md) names a module `settings.gradle.kts` no longer includes | the guard fails first, and the page follows |
| a reference to an old API | a code file a page names that no longer exists | the guard fails on the path; the page is rewritten against the new one |
| a difference between the shells nobody wrote down | a reader on one shell following the other's step | a row in the matrix, and the page split by platform |
| a measurement, or a download page, that has rotted | the weekly link report, or a reader | re-run the benchmark, or open the URL by hand and update it only once it has moved |

### The README walk, monthly

Once a month, and again before a release is cut, one person reads the README as a stranger and answers six
questions. What it produces is a report: nothing here edits a page on a reader's behalf, and a finding is
corrected at its owner first.

| Question | What "wrong" looks like | How it is answered |
|---|---|---|
| does the positioning still describe the app | a promise inherited from a previous release | read against [guide-index.md](guide-index.md) |
| do the screenshots show the current UI | a control that moved, a page style that changed | open the build and compare |
| are both platforms described as they are today | a Windows step a tablet reader cannot follow | [guide-limitations.md](guide-limitations.md) |
| do the install steps match what is published | a file name, a gate, or an unsigned-installer note that expired | the release page, opened by hand |
| do `.mobix`, `.xopp` and the STEM list still match the app | a partial capability sold as whole, or one described in the past tense | [guide-limitations.md](guide-limitations.md), the format pages |
| does every link to authoritative documentation still land | a moved page, a retired anchor | the guards for the tree, the weekly report for the URLs |

The mechanical half of that list is not on the walk: links, anchors, images, assets, artefact names and the
version numbers are checked on every pull request by `DocsGuardTest`, and the URLs that leave the tree are
swept by `.github/workflows/link-check.yml` into a report. The walk is what a build cannot do — decide whether
the words still describe the product.

## Measurements and links away from the tree

Two facts outlive the repository: a number measured on someone's machine, and a document at the other end
of a URL. Neither is answerable by the guards above, and both are handled without making a pull request
depend on something outside it.

- **A measurement carries its conditions.** Every benchmark report opens with the block
  [benchmarks.md](benchmarks.md) specifies — machine, JDK, Kotlin, Gradle, workload, warm-up, iterations,
  unit and date — and carries **Not recorded** where the run could not establish a field. A figure quoted in
  a page links the report it came from; one whose report says *Not recorded* is described as historical
  rather than as comparable, and refreshing it means re-running the benchmark, never editing the digits.
- **External links are swept on a schedule, not in review.** A relative link, an anchor and the file it
  names are mandatory checks above, and they fail the pull request. A URL on another host is checked by
  `.github/workflows/link-check.yml` every week and on demand, and what it produces is a report rather
  than a gate: a 429, a 403, a timeout or a page that refuses automated clients is a **manual
  verification** item, and a failed request is never by itself a reason to change a link. A URL is
  replaced after a person has opened it and found the target moved.

## Running the checks locally

The documentation guards are two JUnit classes in the app's unit-test source set, so they need no device
and no network:

```sh
# any host with a JDK and the Android SDK in reach
scripts/host-build.sh :app:testDebugUnitTest \
    --tests "com.mobixournal.guards.DocsGuardTest" \
    --tests "com.mobixournal.guards.DocClaimsTest"

# the container host: the same check inside the shared toolchain
scripts/build.sh :app:testDebugUnitTest --tests "com.mobixournal.guards.*"
```

A failure names the file and the rule in the assertion's message. What each guard protects is
[testing.md](testing.md)'s inventory, and the build front doors are [tools.md](tools.md)'s.

## In CI, and in review

`.github/workflows/build.yml` runs the doc guards as their own step on every pull request and every push
to `main`, before the full suite, so a broken link or an unindexed page fails under a step named for it.

The rest is review: [the checklist](../.github/PULL_REQUEST_TEMPLATE.md) is the questions a reviewer
would otherwise have to ask in the thread, [CONTRIBUTING.md](../CONTRIBUTING.md) is where a report or a
patch starts, and [writing-docs.md](writing-docs.md) is how a page is judged once it exists.

## Where these rules live

The rule is stated once in [writing-docs.md](writing-docs.md), the owners in [AGENTS.md](../AGENTS.md)
and [architecture.md](architecture.md), the checks in [testing.md](testing.md) and the code under
`app/src/test/java/com/mobixournal/guards/`. This page adds only what none of them answers: which pages a
given change touches, and what happens to a page that has stopped being true.

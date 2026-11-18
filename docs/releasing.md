# Releasing — freezing a version, tagging it, and publishing both installers

> Read when you cut a release, move either version line, or change `.github/workflows/build.yml`.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

Authoritative home for **how a version is frozen, tagged and published**. The toolchain around it — the
container build, WiX and the MSI, the signing secrets — belongs to [tools.md](tools.md); this page owns the
order, and the two version lines.

## Cutting a release

Releases are produced by CI, never by hand. Pushing an annotated tag `vX.Y.Z` runs
`.github/workflows/build.yml`: one job builds the signed APK (`scripts/release-apk.sh`), one the MSI, and
a **third that needs both** creates the GitHub Release with the two installers attached and its notes read
from `docs/releases/vX.Y.Z.md` (`body_path`) — so a release cannot appear with half its downloads.

**A version's contents are the *pending* set in `FINISHED.toml`** — every finished task that does
not carry a `release` stamp — so what the next release ships is a fact about the archive, not a
recollection. Freezing the version is therefore a command:

```sh
scripts/todo.sh list --finished --unreleased                       # what the next version will ship
scripts/todo.sh release --version X.Y.Z --bump-gradle \
    --desktop-version A.B.C                                       # freeze, notes, both version lines
```

`release` stamps every pending task with that version (so the next release starts from an empty
pending set) and writes `docs/releases/vX.Y.Z.md` grouped by category. It refuses to overwrite a
changelog that already exists (`--force` to mean it), refuses a version that is not newer than the
last recorded one, and refuses to run with nothing pending. `--dry-run` prints the draft and
changes nothing; `--no-changelog` stamps without writing a file; `--through YYYY-MM-DD` is the
one-shot backfill for a release that predates the field. Implementation: `scripts/todo/release.py`;
regression check: `python3 scripts/todo/test_release.py`.

1. **Run the release command** (above). `--bump-gradle` sets `versionName` and increments the monotonic
   `versionCode` in `app/build.gradle.kts`; `--desktop-version` moves the window's own line, refusing one
   older than the number already there. Either way `BuildVersions.DESKTOP` then follows `packageVersion`,
   and the command names **every file it wrote**, since one is easy to leave out of the commit.
2. **Curate `docs/releases/vX.Y.Z.md`.** The generated file is a *draft*: group the bullets,
   reword them, add the highlights a raw list cannot show. The file name must match the tag
   exactly.
3. **Revisit the pages that carry the version.** The status matrix in
   [`guide-limitations.md`](guide-limitations.md) opens by naming the builds it describes, so that line
   moves with the release, and a row whose evidence changed during it is re-checked rather than left
   standing on an older test. [`releases/index.md`](releases/index.md) then takes the new version into its
   two current-version lines and its table, so the newest note is reachable without reading the folder.
   Neither is generated: the command knows which tasks shipped, not which capability changed.
4. **Commit and push `main`**, then let the tooling make the tag:

   ```sh
   git push origin main
   scripts/todo.sh tag --version X.Y.Z          # verify-release, then the annotated tag and its push
   ```

   `tag` runs the same checks CI runs and only then creates and pushes the tag, so the tag cannot be made
   around them; `--no-push` stops after creating it. It refuses a dirty tree, a tag that already exists
   (moving one is a decision, not a command), a `HEAD` that is not `main`, and a version the Gradle line
   does not carry. Regression check: `python3 scripts/todo/test_tag.py`.

5. **Watch the workflow.** The release appears on GitHub with `MobiXournal-<android>.apk` and
   `MobiXournal-<desktop>.msi` attached, titled with **both** numbers — `scripts/todo/todo.py release-name`
   reads them from the two Gradle files, so the page cannot name one shell's version as the whole story.

The workflow enforces the freeze rather than trusting it: every run lints both task files and runs
the CLI's own regression checks (`test_store.py`, `test_done.py`, `test_release.py`, `test_tag.py`), and a
`v*` tag also runs `scripts/todo/todo.py verify-release --version <tag>`. That fails when the archive does
not record the version as released, when `docs/releases/vX.Y.Z.md` is missing, when `versionName` does not
match the tag, and when the tree does not carry the freeze: uncommitted changes, a tag off `HEAD`, or
the window's two numbers disagreeing. Run it after the commit and **before** the tag — 2.3.0 was tagged
with `BuildVersions.kt` unstaged, so the tagged tree still named 1.2.0 and the MSI never built.

- **The changelog is generated, then curated.** `todo.sh release` guarantees the *collection* is
  complete and correctly scoped to the version; the curation (grouping, wording, highlights) is
  still a human pass on `docs/releases/vX.Y.Z.md`.
- **Notes are curated, so `generate_release_notes` is off.** GitHub's auto-generated commit
  list would never see this file.
- **A tag without its notes file fails the release step** — deliberately: the workflow's
  `body_path` finds nothing and errors, rather than silently publishing an empty body.

### Rehearsing the release job

The job that publishes runs on a tag and only on a tag, so its assumptions were first tested by a release.
A manual run of the workflow (`workflow_dispatch`) now finishes with the `rehearsal` job: it needs the two
builds, downloads the artifacts they produced, and runs `scripts/rehearse-release.sh assets`. That checks
the two file names against the Gradle lines and the note the body reads, and prints the title the release
would carry. It publishes nothing.

## Two versions, one product

The tablet app and the Windows window are **two artifacts with two version lines**. The app restarted
its numbering at **2.0.0**; the window starts at **1.0.0**.
Neither number is derived from the other, and neither is copied into a doc: each lives in the Gradle
file of the artifact that carries it (`versionName`/`versionCode` in `app/build.gradle.kts`,
`packageVersion` in `desktop/build.gradle.kts`). Read and check both with:

```sh
scripts/todo.sh versions
```

It prints the two values and fails when either is not a shape a release can use. The window's number
is stricter on purpose: Compose writes `packageVersion` into the MSI's version resource, which Windows
reads as a *number*, so a suffix such as `1.0.0-rc1` fails at packaging — the check says so in a second
instead.

`release --bump-gradle` owns the **app** line, because its `versionCode` has to move with it and only
the archive knows what is pending; `--desktop-version` moves the window's, in the Gradle file that names
its MSI. That MSI carries the window's number into the same release under the app's tag; `verify-release`
compares a tag with the app's `versionName`, so a window that needs notes of its own gets a tag of its own
— `desktop-vX.Y.Z`, outside the `v*` filter and with `docs/releases/desktop-vX.Y.Z.md` — and
`todo.sh versions` checks both numbers.

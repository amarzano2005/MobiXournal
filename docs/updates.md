# Updates — the release both shells check, and what Update now installs

> Read when you change how a shell learns about a newer build, what its About row reports, or what its install command does.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## The release channel both shells read

**The project's GitHub Releases are the only source.** One release is published per version, and it carries
both artifacts: the tablet's APK and the window's MSI. The shells read the same document —
`api.github.com/repos/amarzano2005/MobiXournal/releases/latest` — so there is no second place a version can
be announced, and nothing has to be uploaded for one of them.

**Two artifacts, two versions, one tag.** The tag names the tablet's build (`v2.0.0`), while the window's
number is the one its own asset name carries (`MobiXournal-1.0.1.msi`). Each shell therefore reads its
version from its own artifact — the tag for Android, the asset's name for Windows — and compares it with the
build in hand. The asset names themselves are `docs/tools.md`'s (*Desktop installer*); this page depends on
them rather than restating them.

**A draft or a prerelease is not an update.** The API answers with the latest published release, and a build
being prepared must not be offered as one; a release without the shell's own installer is not an update
either. Anything unreadable — no connection, a non-200, a body that is not the release document — reads as
*no update*: a check that could not be made never becomes an invitation to install something unknown.

## When the check runs, and what it says

- **At launch**, once per process, silently: the answer only surfaces when it is *newer*.
- **From About**, on request: *Check for updates* runs the same check and reports the result either way —
  the version already in hand, the newer one, or the failure.
- Nothing is sent about the reader or the document. It is a GET of a public page, and no identifier of the
  machine, the account or the open notebook travels with it.

Both shells keep the result in one state object per shell (`AppUpdates.kt`, `DesktopUpdates.kt`), because the
offer outlives the page that shows it: the launch check may find a release while the reader is writing.

## The popup and its two answers

When a newer build exists, a card names it and the build in hand, and offers the release's own page as
**See what changed** — the notes are read where they were written, not summarised in the window.
Its two answers are the reader's:

- **Remind me later** closes the card. The offer stays on the About page, so the reader can come back to it
  without the check being made again.
- **Update now** downloads that shell's artifact and hands it to the platform's installer. The download
  reports its progress, and a failure says so instead of leaving the button pressed and silent.

## What Update now installs

**Android.** The APK is streamed into `cacheDir/updates/`, which only the app's `FileProvider` exposes, and
handed to the system installer through a read grant. `REQUEST_INSTALL_PACKAGES` lets the app *ask*; Android
still shows its own prompt the first time, and the reader answers it. Nothing is installed silently.

**Windows.** The MSI is streamed beside the system's temporary files and started with `msiexec`, the per-user
package `docs/tools.md` builds. The window does **not** close itself while the installer is up: the MSI
replaces files the running window holds, and quitting with an unsaved tab is the reader's decision, so the
card says what to do and the person finishes it.

An update replaces the application, never a document: both installers keep the app's own data (the settings
file, the recovery snapshots and the notebooks themselves) where they were.

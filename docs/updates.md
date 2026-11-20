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
either. A readable document that names no newer build is *no update*, and a body that is not the release
document is read the same way: a check never turns itself into an invitation to install something unknown.

**A refusal is not the end of the check.** `403` is about the API, not about the release: both shells then
read the same release through two public pages that carry no quota — `releases.atom` names the newest tag
and that release's asset list names the files (`UpdateFallback`), so the tablet still finds its APK and the
window still reads its own number from the MSI's name. The API stays primary because only it says whether a
release is a **draft or a prerelease**: the feed lists what was published, so the fallback is the second
door and not the first. Only when both doors fail does the check report a failure.

**A check that could not be made says so.** A non-200 is a failure, not an "already the latest": GitHub
answers `403` to an anonymous request once the calling IP has spent its sixty calls an hour, and a shared
address reaches that without anyone doing anything unusual. Reading the refusal as *no update* told a reader
on an older build that there was nothing to install while a newer release was published, so both shells
report the failure where they report every other answer: on About's own line under its command, and in the
tablet's notification when the reader asked for the check.

## When the check runs, and what it says

- **At launch**, once per process, silently: the answer only surfaces when it is *newer*.
- **From About**, on request: an explicit **Check for updates** button sits under the build number on the
  tablet (`AboutSection.kt`) and the window's row is a command row (`AboutSettings.kt`) — and it reports the
  result either way: the version already in hand, the newer one, or the failure. That answer is a
  **persistent line under the command** on both shells — the tablet's notification and About's own sentence
  in the window, which has no floating toast: the panel that asked the question is the panel that answers
  it, and the sentence stays until the next check. The command's own note is static help, never the answer.
- Nothing is sent about the reader or the document. It is a GET of a public page, and no identifier of the
  machine, the account or the open notebook travels with it.

Both shells keep the result in one state object per shell (`AppUpdates.kt`, `DesktopUpdates.kt`), because the
offer outlives the page that shows it: the launch check may find a release while the reader is writing.

## The popup and its states

When a newer build exists, a card names the build and the one in hand — *MobiXournal 2.3.0*, *Update available
· you have 2.2.0* — and, while the offer is on the table, opens the release's own page: the notes are read
where they were written, not summarised in the window (`What's changed in 2.3.0` on the tablet, *See what
changed* in the window). The card is a **floating surface** on both shells — the tablet's `FloatingDialog`,
the window's `glassSurface` — so it arrives and leaves on the glass spring instead of being an opaque dialog
over the page, and `ColorContractGuardTest` fails the build if the tablet's goes back to one
([`interface-contract.md`](interface-contract.md) owns that rule).

**The words and the action live once, in `mobix-core`.** `UpdateCardCopy.kt` owns the sentence for every
phase, the failure sentences and the action each phase offers (`UpdateAction`); a dialog only performs that
action and prints its label, so a button cannot say one thing and do another. Both shells read the same copy
and differ only in their material — the tablet's `Button`/`TextButton` on the floating card, the window's
`PanelButton` on its opaque popup — and in the noun each platform owns: *Android's installer* and *Windows'
installer*. A shell that cannot remember a skip is not offered one (`canSkip`), and the tablet's card is at
most 420 dp wide, so a landscape slate reads a card rather than a panel with an empty half.

| Phase | What the card says | The answers, in both shells |
|---|---|---|
| Available | the build, and the one in hand | **Update now** (filled), *Remind me later*, *Skip this version* (the tablet), the notes |
| Downloading | *Downloading the update* and a bar: `42% · 12.4 MB of 45.0 MB`; an indeterminate bar and no percentage when the server declared no length | **Cancel** — a filled answer on the tablet, an ordinary one in the window |
| Downloaded | *Update downloaded*, and the sentence the platform's installer left | **Install** — asks that installer again for the artifact in hand — and *Later* |
| Failed, no bytes | the offer, with the failure's own sentence | **Try again** (a fresh download), *Remind me later*, *Skip this version* (the tablet), the notes |
| Failed, bytes in hand | *Update downloaded*, with the installer's refusal | **Install** again, *Later* |

**A download cannot be hidden.** While one runs the tablet's card ignores an outside tap and Back, and the
window's card is a modal whose scrim already takes the click, because a transfer that continues with no
surface is one the reader cannot see; **Cancel** stops it, deletes the half artifact and puts the offer back,
and a cancel that lands after the bytes are complete does nothing. Closing the response body is what
interrupts the blocked read in the window, and the cancelled attempt's own file name keeps it from touching
the next one. Progress is a bar and a number — never a sentence and a disabled button saying the same thing
twice — and a response without `Content-Length` says only that it is downloading, with an indeterminate
bar, instead of printing a percentage it does not have.

**Skip this version is the one answer that outlives the launch.** The version string is kept in the app's
own preferences (`mobixournal_updates`, its own file like the bookmark and recents stores), and the launch
check stops raising the card for it — the release is still recorded, so About's command reopens the offer
whenever the reader asks. A newer release is a different string, so it is offered again; nothing has to
clear the entry.

**The window's card is the same card in another material.** It reads the shared copy, so its phases,
sentences and answers are the tablet's; it keeps the downloaded MSI, so **Install** starts `msiexec` again
instead of fetching it twice, and **Try again** re-downloads after a transfer that stopped. **Skip this
version is the tablet's, for now**: the window remembers no skipped build, so it does not offer an answer it
could not honour and its launch check repeats the offer on every start. The standing answers of an offer
are:

- **Remind me later** closes the card. The offer stays on the About page, so the reader can come back to it
  without the check being made again.
- **Skip this version** (the tablet) puts the build away for good: no card at the next launch, and the same
  offer one tap away in About.
- **Update now** downloads that shell's artifact and hands it to the platform's installer. A failure says
  so instead of leaving the button pressed and silent.

## What Update now installs

**Android.** The APK is streamed into `cacheDir/updates/`, which only the app's `FileProvider` exposes, and
handed to the system installer through a read grant. `REQUEST_INSTALL_PACKAGES` lets the app *ask*; Android
still shows its own prompt the first time, and the reader answers it. Nothing is installed silently.

**Windows.** The MSI is streamed beside the system's temporary files and started with `msiexec`, the per-user
package `docs/tools.md` builds. The package stays there until the next download, so **Install** starts that
same `msiexec` again without transferring it a second time. The window's own HTTP client lives in `java.net.http`, which the packaged app
image must list as a **jlink module** (`desktop/build.gradle.kts`): the runtime jpackage builds from the
classpath does not carry it, and a window shipped without it died at startup while every test passed on the
full JDK. The window does **not** close itself while the installer is up: the MSI
replaces files the running window holds, and quitting with an unsaved tab is the reader's decision, so the
card says what to do and the person finishes it.

An update replaces the application, never a document: both installers keep the app's own data (the settings
file, the recovery snapshots and the notebooks themselves) where they were.

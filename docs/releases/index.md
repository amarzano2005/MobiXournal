# The releases, newest first

> Read when you want to know which version is current, what a version changed, or which note belongs to
> the shell you installed.
> For the task of explaining why the two shells number separately, guide-faq.md is the page that
> answers it for a reader.

## What is current

- **Android:** MobiXournal **2.1.0**, released 2026-11-09.
- **Windows:** MobiXournal Desktop **1.1.0**, released 2026-11-09.

The two shells are built and released separately and therefore number separately. A note names the shell
it belongs to, and a release note for one shell does not describe the other.

| Version | Shell | Released | What it was | The note |
|---|---|---|---|---|
| **2.1.0** | Android | 2026-11-09 | The update check, document work off the frame, interruption-safe saves | [v2.1.0.md](v2.1.0.md) |
| **1.1.0** | Windows | 2026-11-09 | The window's update check, settings by workspace, saves staged off the UI | [v2.1.0.md](v2.1.0.md) |
| **1.1.0** | Android | 2026-10-07 | New tools, a new interface, on-device handwriting search | [v1.1.0.md](v1.1.0.md) |
| **1.2.0** | Android | 2026-10-09 | Eighteen enhancements across UI, stylus, selection and settings | [v1.2.0.md](v1.2.0.md) |
| **1.3.0** | Android | 2026-10-12 | STEM and drafting tools, guides, PDF outline navigation, hold-to-snap | [v1.3.0.md](v1.3.0.md) |
| **2.0.0** | Android | 2026-11-06 | The 2.0 line, with the first Windows window released beside it | [v2.0.0.md](v2.0.0.md) |
| **1.0.0** | Windows | 2026-11-06 | The window's first release, shipped with Android 2.0.0 | [v2.0.0.md](v2.0.0.md) |
| **1.0.1** | Windows | 2026-11-07 | The window's first follow-up | [desktop-v1.0.1.md](desktop-v1.0.1.md) |

A notebook is not tied to a version: `.mobix` and `.xopp` are the same formats in both shells, so a
document written by one opens in the other. Whether a particular feature works in the build you installed
is [the status page](../guide-limitations.md)'s question.

## What the older notes do and do not say

The first three notes were written before this index existed, so they carry only what their authors wrote
down: no `Released` line, no per-category counts, and no promise that the list of changes is exhaustive.
Their dates here are the days their files entered the repository. The 2.0.0 note and everything after it
follow the shape below.

## A note's shape

A new note is a new file in this folder, linked by a row in the table above:

```markdown
# MobiXournal vX.Y.Z (Android) & Desktop vX.Y.Z (Windows 11)

Released YYYY-MM-DD · one line saying what the release is for.

## Added / Changed / Fixed / Removed

- What changed, for a reader, not for the history.
```

The file name is the version for the tablet (`vX.Y.Z.md`) and `desktop-vX.Y.Z.md` for a window-only
release. The file is written by the release command, not by hand — [tools.md](../tools.md) owns that
procedure — and the GitHub Release reads its body from this file, so the note is the announcement rather
than a summary of one.

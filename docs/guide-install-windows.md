# Installing MobiXournal on Windows 11

> Read when you are installing the desktop window from the MSI, wondering what it changed on your PC, or
> deciding whether to use the installer or the folder build.

_A page of the user guide; the internal map is [architecture.md](architecture.md)._

## What you need

- **Windows 11, 64-bit.**
- **No Java.** The installer carries its own runtime; nothing has to be installed first.
- A graphics tablet is optional. The window reads Wintab and Windows Ink pen streams when a tablet is
  present and works with a mouse when it is not.

## Get the installer

Download `MobiXournal-<version>.msi` from the project's [GitHub Releases](https://github.com/amarzano2005/MobiXournal/releases)
page. The current desktop build is **1.0.1**; the MSI is named after the version it contains, so the file
you download states it.

## What the installer does

It is a **per-user** install: no administrator prompt, and the program lands in your own profile under
`%LOCALAPPDATA%` rather than in `Program Files`.

During setup it offers two optional extras, both on by default:

- associate `.mobix` and `.xopp` files with MobiXournal, so double-clicking a notebook opens the window;
- put a shortcut on the desktop.

Both can be declined, and associations can be changed later in Windows' own *Default apps* settings.

### The installer is not signed

The MSI is signed only when it was built with a code-signing certificate configured; a build without one
is left unsigned on purpose rather than signed with a fake identity. An unsigned installer makes Windows
show **"unknown publisher"** and SmartScreen may ask you to confirm before running it. If that matters to
you, check the file you downloaded against the release page you took it from before running it.

## The folder build, without an installer

If you would rather not install anything, build the standalone folder and run the executable inside it:

```powershell
.\gradlew.bat :desktop:createDistributable
```

The window is then at `desktop/build/compose/binaries/main/app/MobiXournal/MobiXournal.exe`. It carries
the same program with no associations and no shortcut, and it updates only when you rebuild it.

## First launch

The window opens at **100 %**: the sheet's width fills the window, as on the tablet. Documents are opened
from the header, from Explorer (if you took the associations), or by dragging a file onto the window.

## Where things are kept

- **Your notebooks** are the files you opened or saved — anywhere, including a synced folder. A save is
  written beside the target and then moved into place, so a cloud client never uploads half a notebook.
- **Crash recovery** lives in `%APPDATA%\MobiXournal\recovery\`, one snapshot per unsaved notebook. It is
  deleted when that notebook is saved, discarded or closed.

## Uninstall

Uninstall from *Settings → Apps → Installed apps*. The uninstaller removes the program; your notebooks
are untouched because they are files you chose. Snapshots in `%APPDATA%\MobiXournal\recovery\` are worth a
look before you remove the folder, since a notebook that was never saved exists only there.

## Related pages on Windows

- [Installing on Android](guide-install-android.md) — the other platform
- [When something goes wrong](guide-troubleshooting.md) — every message the window can show
- [`docs/desktop-workstation.md`](desktop-workstation.md) — what the window is, for a developer

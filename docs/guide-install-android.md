# Installing MobiXournal on Android

> Read when you are putting the app on an Android tablet or phone for the first time, or wondering where
> a notebook you saved actually went.

_A page of the user guide; the internal map is [architecture.md](architecture.md)._

## What you need

- **Android 8.0 or newer** (API 26).
- A tablet or phone. A stylus makes it a notebook; a finger works for everything else.
- No account, no subscription, and no internet connection to open or save a document.

## Get the file

Download `app-release.apk` from the project's [GitHub Releases](https://github.com/amarzano2005/MobiXournal/releases)
page. The current Android build is **2.0.0** (`versionCode 14`).

If you build it yourself instead, `scripts/release-apk.sh` produces the release APK, and
`.\gradlew.bat assembleDebug` produces a debug one. The debug build installs over the release build —
same application id, no variant suffix — so installing it does not create a second app or lose your
settings.

## Install it

Open the downloaded APK. Android will ask you to allow installs from the app you downloaded it with; you
can turn that permission back off afterwards. The installer shows the app's name and the permissions it
asks for; nothing here needs special access beyond storage you choose through the system file picker.

If a previous version is installed, the new one replaces it in place: settings, recent documents and
anything the app keeps for a crash survive the upgrade.

## Where your documents live

A notebook is a **file you chose**, saved through Android's own file picker. That has three consequences
worth knowing on the first day:

- It lives wherever you put it — internal storage, an SD card, or a folder synced by Google Drive,
  Nextcloud, OneDrive or WebDAV.
- Saves into a cloud folder are written to a temporary file and then moved into place, so a sync client
  that uploads while you work never sees a half-written notebook.
- The app does not keep a private copy of your notebook. If you move the file with a file manager, the
  app's recent list is the only thing that needs updating.

## Opening a notebook you already have

Both `.mobix` and `.xopp` open directly. A Xournal++ `.xopp` keeps saving as `.xopp` unless you use
**Save As** to convert it, so a file can go back to a desktop Xournal++ without a migration step. The
full rules — what stays editable, what becomes ink, what never converts silently — belong to
[mobix-format.md](mobix-format.md) and [xopp-bridge.md](xopp-bridge.md).

## Uninstall

Uninstalling removes the app, not your notebooks: they are ordinary files where you saved them. Files
the app kept for a crash live in the app's own storage and go with it.

## Related pages on Android

- [Installing on Windows 11](guide-install-windows.md) — the other platform
- [When something goes wrong](guide-troubleshooting.md) — refused opens, failed saves, recovery
- [`docs/tools.md`](tools.md) — the build pipelines, for a developer

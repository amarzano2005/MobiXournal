# The emulator harness: boot, install, drive, and `androidTest`

> Read when you boot the emulator, install a build on it, drive its UI, or run the instrumented suite.

This spoke owns the device loop — starting the emulator, installing a build on it, seeing and
driving the screen, and running `androidTest` — on the `/data/android` container host and on a host
SDK. Compiling the APK itself is [tools.md](tools.md)'s.

## Android emulator

Running the APK on a device/emulator also goes through `/data/android` — a headless Android 14
emulator you drive over `adb`, plus physical devices on the tailnet (see its `config.yaml`).
**This emulator is the standard way to test the app: don't stop at "it compiles."** Every
change with a runtime surface must be installed and exercised on the emulator, not just built.

- **Where it lives:** `/data/android/` — `docker-compose.yml` (the emulator container) and
  the emulator driver scripts (`emulator.sh`), alongside `adb-targets.sh`
  which lists every target across the two isolated adb worlds (host adb → physical devices,
  container adb → emulator). Full details in that directory's `README.md`; don't duplicate them here.
- **How to run it:** `emulator.sh` dispatches `status | up | boot-wait | down | install <apk> |
  launch <pkg> | screenshot [png] | ui | logcat | shell | adb` — so `emulator.sh up` to boot, then
  `emulator.sh install <apk>`, `emulator.sh launch com.mobixournal.v2`, `emulator.sh screenshot <png>`,
  `emulator.sh ui`, and `emulator.sh logcat` for logs. Run `adb-targets.sh` to see which devices and
  the emulator are reachable. Physical devices install via the host adb:
  `adb -s <ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk`.
- **Testing a change on the emulator (the expected loop):** after a green
  `scripts/build.sh`, install the fresh APK and actually drive it:
  1. `emulator.sh install app/build/outputs/apk/debug/app-debug.apk` then
     `emulator.sh launch com.mobixournal.v2`.
  2. **Take screenshots** (`emulator.sh screenshot <png>`) and look at them to confirm the UI
     rendered as intended — this is how you *see* the change, not infer it.
  3. **Read the error logs** — `adb logcat` (filter to the app) — to catch crashes, stack
     traces, and warnings the build can't surface.
  4. **Simulate touch / stylus input** — finger presses, taps, and swipes over `adb` (e.g.
     `adb shell input tap <x> <y>` / `input swipe …`, or `emulator.sh ui`) to exercise
     drawing, tool selection, open/save, and other interactions end-to-end.
  Report what the screenshots and logs actually showed; a change isn't verified until it's
  been run this way on the emulator.
- **Promoting to physical devices — not our job.** Emulator verification is where
  our loop ends. Deployment tooling or manual installation handles moving the built APK to
  physical devices as needed. Don't `adb install` to those devices as a routine step. (Manual `adb -s <ip>:5555 install
  -r …` still works if you ever need a one-off, but it isn't part of the standard flow.)
- **Running the instrumented (`androidTest`) suite** (this container path only — on a host the
  Gradle task documented under *Host fallback* below is canonical): use `scripts/connected-test.sh`
  (wrapper over the toolchain's `connected-test.sh`), **not**
  Gradle's `connectedDebugAndroidTest`. Gradle's task starts an adb server inside the throwaway
  build container — a different adb world than the emulator — so it dies with "No connected
  devices!". The wrapper instead builds the app + `androidTest` APKs in the builder, reads the
  instrumentation component from the test APK's manifest, then installs and runs it through the
  emulator container's own adb; its exit status is the test result (non-zero if any test fails).
  - `scripts/connected-test.sh` — build + run the whole `SmokeTest` suite on the emulator.
  - `scripts/connected-test.sh -e class com.mobixournal.SmokeTest` — extra args pass through to
    `am instrument` (class/method/size filters, etc.).
- **Gotchas:** the emulator needs host KVM (`/dev/kvm`, VT-x enabled in BIOS).

### Host fallback — Android Studio's own emulator

Where `/data/android` isn't present, the SDK's own emulator is the same loop, driven by the SDK's
`adb` instead of `emulator.sh`:

```sh
# The SDK this checkout builds with — `host-build.sh --list` prints the path it picked
SDK=$(scripts/host-build.sh --list | sed -n 's/^host-build: SDK //p')
"$SDK/emulator/emulator" -avd Pixel_Tablet &   # `.exe` under Git Bash; or start it from Android Studio
"$SDK/platform-tools/adb" wait-for-device      # then poll sys.boot_completed for 1
"$SDK/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
"$SDK/platform-tools/adb" shell am start -n com.mobixournal.v2/com.mobixournal.MainActivity
"$SDK/platform-tools/adb" exec-out screencap -p > shot.png   # and: logcat, input tap/swipe
```

**The instrumented suite runs on this host too.** Gradle's `connectedDebugAndroidTest` is only
broken *inside the container*, where its adb is a different world from the emulator's; on a host the
SDK's adb **is** the emulator's adb, so the ordinary task works — and unlike `scripts/connected-test.sh`
it needs no container at all:

```sh
# Whole androidTest suite on the attached emulator/device
scripts/host-build.sh connectedDebugAndroidTest

# One class, with the runner argument AGP expects
scripts/host-build.sh -Pandroid.testInstrumentationRunnerArguments.class=com.mobixournal.StylusInputTest \
    connectedDebugAndroidTest
```

That is how the stylus paths are exercised where `adb shell input` cannot reach (`StylusInputTest`
injects real `MotionEvent`s carrying tool type, button state and per-sample pressure).

**Capturing the pen-tip HUD.** `adb shell input` cannot inject a stylus barrel key on the AVD (key
code 308 arrives as 0), so the ring is opened from an instrumented test instead:
`RadialHudScreenshotTest` starts the editor, binds the barrel to the HUD, opens the ring with a
barrel **key-down** and writes the composited screen to `Pictures/` — `radial-hud.png` (the ring over
a stroke) and `radial-hud-commit.png` (a flick committed, the rail following). Run it with the class
filter above, then pull the file it printed (`adb pull /sdcard/Pictures/radial-hud.png`, with
`MSYS_NO_PATHCONV=1` in Git Bash so the remote path is not rewritten as a Windows one).

**On a host this is the canonical path, and it runs everything.** Measured: 34 tests across all seven
`androidTest` classes — `BitmapCacheDeadlockTest`, `MouseWheelInputTest`, `SelectionInputTest` (3),
`SmokeTest` (2), `StylusInputTest` (19), `io.UriStagingTest` (3), `ui.SettingsStoreTest` (3) — in
about half a minute on a warm `Pixel_Tablet` AVD (55 s on a booting one). So `scripts/connected-test.sh` is **not** needed on a host, not even for
`SmokeTest`: its reason for existing (Gradle's task starting an adb inside the throwaway *build*
container, a different adb world from the emulator's) does not apply where the host's own SDK adb
reaches the device. Keep it for the `/data/android` pipeline, where that mismatch is real.

- **Check the accelerator first:** `emulator -accel-check` reports whether WHPX (Windows) / KVM is
  usable; without it an x86_64 image won't boot in reasonable time.
- **A fresh boot comes up with the screen dozing and the notification shade focused.** Screenshots
  taken then are black: `adb shell input keyevent KEYCODE_WAKEUP` (and `82` to dismiss the lock
  screen) first, then confirm with `adb shell dumpsys window | grep mCurrentFocus`.
- **A finger only draws when the app's *Finger draws* setting is on** — otherwise it pans, which
  looks exactly like "my input did nothing". To draw as a **pen**, name the source:
  `adb shell input stylus swipe 500 450 2100 1250 700` delivers `TOOL_TYPE_STYLUS`, which the canvas
  inks whatever the finger setting says (`input` lists `stylus` among its sources; check
  `adb shell input` for the list this image has).
- **Find a control's coordinates instead of guessing them:** `adb shell uiautomator dump` then
  `adb pull` gives every button's `content-desc` and bounds (`Tool: Pen`, `Drawing guides`, …).
- **Hand a document to the app without driving the picker:** `adb shell am start -a
  android.intent.action.VIEW -d file://<path> -t application/vnd.mobixournal+zip` opens it straight
  into a tab (the manifest declares both `content` and `file` schemes for that type). The path must be
  one *the app* can read: it asks for no storage permission, so `/sdcard/Download/…` fails silently —
  a toast, no tab. `adb push x.mobix /data/local/tmp/` then
  `adb shell run-as com.mobixournal.v2 cp /data/local/tmp/x.mobix files/x.mobix` puts it in the app's
  own sandbox, and `file:///data/data/com.mobixournal.v2/files/x.mobix` opens every time.
- **Fixtures for an emulator pass:** the documents a runtime pass needs are written by a JVM test
  rather than by hand, so the device fixture and the unit-test fixture cannot drift apart:
  `./gradlew testDebugUnitTest --tests com.mobixournal.io.DeviceFixturesTest` writes
  `app/build/device-fixtures/paged-200.mobix` (200 pages, a **bundled picture** on page 2, a graph page
  3) and `board-2d.mobix` (an `INFINITE_2D` board at cells `(0, 0)`, `(1, 0)` and `(-1, 0)`, so a
  signed chunk name is exercised). Push them into `/sdcard/Download/` and open them from the picker;
  a fixture whose `minAppVersion` is newer than the installed build opens **read-only**, so the writer
  pins it to the app's own version.
- **Verify colours and geometry from the screenshot, not by eye:** crop and sample it (PIL/`magick`),
  e.g. compare a pop-up's fill with the toolbar's. Two pixels that look alike are one `assert` apart.
  A wrapped `.mobix` is a ZIP: `zipfile` plus the JSON inside confirms what a save actually wrote —
  which chunk gained a stroke, whether an `assets/` entry survived, whether `pages/` is still empty.
- **Windows/Git Bash mangles `/sdcard/…` paths** into `C:/Program Files/Git/sdcard/…`: prefix the
  command with `MSYS_NO_PATHCONV=1`, or write the remote path as `//sdcard/…`.
- **`adb devices` showing `unauthorized` usually means the AVD resumed from a snapshot**, not a bad
  key: the guest's `adb_keys` belongs to the adb of the session that saved the snapshot, so the boot
  authorization never runs. Cold-boot it (`emulator … -no-snapshot-load`, no `-wipe-data` needed) and
  the host's key is injected again — measured: 150 s to `device` on a Pixel_Tablet image, where the
  same AVD from a snapshot stayed `unauthorized` indefinitely.
- **To check both themes of a visual change**, flip the guest rather than rebuilding:
  `adb shell cmd uimode night yes|no` — chrome colours and any theme-derived edge change with it.
- **A change is only verified when the screenshot shows it.** Diff the "before" and "after" captures
  of the same screen with PIL/`magick` and read the pixels at the edge; that is how a pop-up rim was
  measured as a 2 px step from `(238,237,244)` to `(173,173,180)` in the light theme and from
  `(30,31,37)` to `(89,90,96)` in the dark one, and how an elevated menu was shown to paint *inside*
  its own top edge instead of casting a shadow.

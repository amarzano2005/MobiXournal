# Host profiles and toolchain selection

> Read when starting work on a different machine, choosing a toolchain, or diagnosing a host-specific failure.

The owner works across **multiple machines** (a Linux container host equipped with `/data/android`,
a Windows 11 laptop and a Windows 11 desktop PC). **Never assume which machine a session is running
on** — check the OS/shell, the host name (`$env:COMPUTERNAME`) and whether `/data/android` exists at
the start of a task, then follow the matching profile below:

| Profile | How to recognise it | Build & test front door | Emulator & `androidTest` |
|---|---|---|---|
| **A. Linux `/data/android` container host** (Primary standard when present) | Linux shell (`bash`/`sh`) and `/data/android/build.sh` exists | `scripts/build.sh` (runs inside `android-builder:local` via Docker, Podman fallback) | `/data/android` headless Android 14 container via `emulator.sh`; run `androidTest` via `scripts/connected-test.sh` |
| **B. Windows 11 laptop (`i7-12650H`)** | Windows (`pwsh`), user `amarz`, no `/data/android` | `.\gradlew.bat` directly in `pwsh`, or `& "C:\Program Files\Git\bin\bash.exe" scripts/host-build.sh` | Local SDK `Pixel_Tablet` AVD (WHPX); run `androidTest` via `.\gradlew.bat connectedDebugAndroidTest` |
| **D. Windows 11 desktop PC (`ANTONIOFISSO-PC`, `i9-12900KF`)** | Windows (`pwsh`), host name `ANTONIOFISSO-PC`, 3440×1440 monitor, no `/data/android` | same as B | same as B (SDK and `Pixel_Tablet` AVD present) |
| **C. Other Linux / macOS / Windows hosts** (without `/data/android`) | `/data/android` absent | `scripts/host-build.sh` (auto-discovers JDK 17–24 preferring 21 + Android SDK) or `./gradlew` | Host SDK `emulator` + `adb`; run `androidTest` via `scripts/host-build.sh connectedDebugAndroidTest` |

### Profile A — Linux `/data/android` container host
- **Containerized build & test**: Always use `scripts/build.sh` (which defaults to both unit-test
  suites plus `assembleDebug`) and `scripts/todo.sh <command>`. Do not rely on host-installed JDK/SDK
  when `/data/android` is available.
- **Containerized emulator**: Drive the emulator via `emulator.sh` (`up`, `install`, `launch
  com.mobixournal`, `screenshot`, `ui`, `logcat`) and run instrumented tests via
  `scripts/connected-test.sh` (never plain `connectedDebugAndroidTest` inside the throwaway builder
  container, which lives in a separate `adb` world).
- Full contract and gotchas live in `docs/tools.md`.

### Profile B and D — the Windows 11 machines
Both run Windows 11 `10.0.26300` in `pwsh` with the same toolchain paths (below); they differ in
hardware. **Profile D is the machine to verify desktop-app work on**: it has the 21:9 monitor, which
Profile B lacks. Both Windows machines carry the same **Gaomon WH851** tablet — the pen they use on
the Android tablet as well.

#### Profile D — Windows 11 desktop PC (`ANTONIOFISSO-PC`)
- **Hardware**: 12th Gen Intel Core **i9-12900KF** (16 cores, 24 threads), **32 GB RAM**, NVIDIA
  **GeForce RTX 3080** (a discrete GPU, so a running AVD does not share system RAM with the display).
- **Display**: one **3440×1440 (21:9)** monitor; Windows reports **120 Hz** for it, so the 144 Hz
  target of `docs/desktop-workstation.md` needs the refresh rate raised in Windows display settings
  (or a check that the monitor supports it) before it can be measured here.
- **Tablet**: **Gaomon WH851** — reaches Compose as a mouse (pressure `1.0`, no tilt); its pen's own
  stream is **Wintab** (`docs/desktop-tablet.md` owns the readers). Express keys are the vendor's.
- **Python trap**: `python` on `PATH` resolves to the Microsoft Store stub in `WindowsApps` and prints
  a Store prompt instead of running. The real interpreter is
  `C:\Users\amarz\AppData\Local\Python\bin\python.exe` (3.14), e.g.
  `& 'C:\Users\amarz\AppData\Local\Python\bin\python.exe' scripts/todo/todo.py stats`.
- **JDKs**: `~\.jdks` holds `jbr-21.0.11` (use this) and `temurin-25.0.3`; the default `java` on `PATH`
  is **JDK 27**, which breaks Gradle — set `JAVA_HOME` as under Profile B.
- Everything else (SDK, `Pixel_Tablet` AVD, `.\gradlew.bat`, Git Bash for `.sh`) is as under Profile B.
  With 32 GB the RAM pressure is far lower, but still run **one Gradle command at a time**.

#### Profile B — Windows 11 laptop (`i7-12650H` / 16 GB RAM)
When the session is running on this Windows 11 workstation (`10.0.26300`, `pwsh`):
- **Hardware & resource budget**:
  - **CPU**: 12th Gen Intel Core i7-12650H (10 cores: 6P + 4E, **16 logical processors**).
  - **RAM**: **16 GB physical RAM** (~15.7 GB usable; ~8–9 GB typically free alongside OS/IDE).
  - **GPU & Virtualization**: Intel UHD Graphics (integrated, shared system RAM); **WHPX (`10.0.26300`)**
    active for x86_64 Android emulation.
  - **RAM protection rule**: `gradle.properties` pins `-Xmx2048m` and `org.gradle.caching=true`.
    **Never launch concurrent Gradle commands in parallel tool calls** — a second Gradle + Kotlin daemon
    alongside a running `Pixel_Tablet` AVD (which shares system RAM via Intel UHD Graphics) will cause
    swap thrashing. Run targeted JVM tests (`--tests <Class>`) during iteration before the full suite.
- **Toolchain paths & Windows shell gotchas**:
  - **No `/data/android`, Docker, Podman, or local `xournalpp` binary** on this machine (verify `.xopp`
    desktop compatibility via JVM round-trip, `XoppSynthesis`, and `MobixMetaRevival` tests).
  - **Bash stub trap (`WindowsApps\bash.exe`)**: Plain `bash` in `PATH` resolves to the non-functional
    WSL stub. To run `.sh` scripts (`scripts/host-build.sh`, `scripts/todo.sh`), explicitly call
    **Git Bash**: `& "C:\Program Files\Git\bin\bash.exe" scripts/host-build.sh ...` — or bypass bash
    overhead entirely in `pwsh` by calling `.\gradlew.bat` and `python scripts/todo/todo.py <cmd>` directly.
  - **JDK**: JetBrains Runtime **JDK 21** at `C:\Users\amarz\.jdks\jbr-21.0.11` (picked automatically by
    `scripts/host-build.sh`), plus Eclipse Temurin OpenJDK 17 (`17.0.20.1`) on `PATH`. **A JDK 27 does
    not work for Gradle here:** the machine's default JVM (e.g. `C:\Program Files\Java\jdk-27`) makes the
    Kotlin DSL fail at configuration with a bare `IllegalArgumentException: 27` from
    `JavaVersion.parse`, so export `JAVA_HOME='C:\Users\amarz\.jdks\jbr-21.0.11'` before any
    `./gradlew.bat` or `scripts/host-build.sh` run.
  - **Android SDK & AVD**: SDK at `C:\Users\amarz\AppData\Local\Android\Sdk` (`local.properties`),
    `adb.exe` in `platform-tools\`, `emulator.exe` in `emulator\` with AVD **`Pixel_Tablet`**.
  - **Emulator & `androidTest` on this host**: Use `.\gradlew.bat connectedDebugAndroidTest` (with
    `-Pandroid.testInstrumentationRunnerArguments.class=...` for single classes) — do **not** use
    `scripts/connected-test.sh` here. See `docs/tools-emulator.md` (*Host fallback — Android Studio's own
    emulator*) for `-no-snapshot-load`, `KEYCODE_WAKEUP`, and `run-as com.mobixournal`.

Build and packaging commands belong to [tools.md](tools.md); device operation belongs to
[tools-emulator.md](tools-emulator.md). Detect the current host again instead of treating an old task
description as a hardware inventory.

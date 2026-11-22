"""Cross-platform Android emulator control wrapper for host SDKs and /data/android."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time

DEFAULT_AVD = "Pixel_Tablet"
DEFAULT_COMPONENT = "com.mobixournal/com.mobixournal.MainActivity"
DEFAULT_TIMEOUT = 180


def _repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def _unescape_gradle_path(raw: str) -> str:
    return raw.replace("\\\\", "\\").replace("\\:", ":")


def _is_android_sdk(path: Path) -> bool:
    if not path.is_dir():
        return False
    adb_name = "adb.exe" if os.name == "nt" else "adb"
    return (path / "platform-tools" / adb_name).is_file() or (path / "platforms").is_dir()


def _safe_home() -> Path | None:
    try:
        return Path.home()
    except RuntimeError:
        return None


def discover_android_sdk(repo: Path) -> Path:
    """Locate the Android SDK root from env, local.properties, or standard install paths."""
    candidates: list[Path] = []
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(var):
            candidates.append(Path(os.environ[var]))
    local_props = repo / "local.properties"
    if local_props.is_file():
        for line in local_props.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.startswith("sdk.dir="):
                candidates.append(Path(_unescape_gradle_path(line.split("=", 1)[1].strip())))
                break
    local_app = os.environ.get("LOCALAPPDATA")
    if local_app:
        candidates.append(Path(local_app) / "Android" / "Sdk")
    home = _safe_home()
    if home is not None:
        candidates.extend([
            home / "AppData" / "Local" / "Android" / "Sdk",
            home / "Android" / "Sdk",
            home / "Android" / "sdk",
            home / "Library" / "Android" / "sdk",
        ])
    for cand in candidates:
        if _is_android_sdk(cand):
            return cand
    raise RuntimeError("No Android SDK found; set ANDROID_HOME or sdk.dir in local.properties.")


def _sdk_tool(sdk: Path, subdir: str, name: str) -> str:
    suffix = ".exe" if os.name == "nt" else ""
    tool = sdk / subdir / f"{name}{suffix}"
    if not tool.is_file():
        raise RuntimeError(f"Expected SDK tool not found: {tool}")
    return str(tool)


def read_app_version(repo: Path) -> str:
    """Read versionName from app/build.gradle.kts."""
    gradle_file = repo / "app" / "build.gradle.kts"
    if not gradle_file.is_file():
        raise RuntimeError(f"Missing {gradle_file}")
    text = gradle_file.read_text(encoding="utf-8", errors="replace")
    match = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', text, re.MULTILINE)
    if not match:
        raise RuntimeError(f"versionName not found in {gradle_file}")
    return match.group(1)


def default_release_apk(repo: Path, must_exist: bool = True) -> Path:
    """Return the signed release APK path MobiXournal-<version>.apk (never app-debug.apk)."""
    version = read_app_version(repo)
    apk = repo / "app" / "build" / "outputs" / "apk" / "release" / f"MobiXournal-{version}.apk"
    if must_exist and not apk.is_file():
        raise RuntimeError(
            f"Signed release APK not found at {apk}. "
            "Build it first with scripts/verify.cmd --build-apk (or scripts/release-apk.sh)."
        )
    return apk


def _container_emulator_sh() -> Path | None:
    if os.name == "nt":
        return None
    toolchain = Path(os.environ.get("ANDROID_TOOLCHAIN", "/data/android"))
    script = toolchain / "emulator.sh"
    return script if os.access(script, os.X_OK) else None


def _run(cmd: list[str], capture: bool = False, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        cmd,
        capture_output=capture,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=check,
    )


def _list_avds(emulator_exe: str) -> list[str]:
    proc = _run([emulator_exe, "-list-avds"], capture=True, check=False)
    if proc.returncode != 0:
        return []
    return [line.strip() for line in proc.stdout.splitlines() if line.strip()]


def _has_online_device(adb_exe: str) -> bool:
    proc = _run([adb_exe, "devices"], capture=True, check=False)
    if proc.returncode != 0:
        return False
    for line in proc.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            return True
    return False


def _boot_wait(adb_exe: str, timeout: int) -> None:
    _run([adb_exe, "wait-for-device"])
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        proc = _run([adb_exe, "shell", "getprop", "sys.boot_completed"], capture=True, check=False)
        if proc.returncode == 0 and proc.stdout.strip() == "1":
            return
        time.sleep(2)
    raise RuntimeError(f"Emulator did not finish booting within {timeout}s.")


def _wake_and_unlock(adb_exe: str) -> None:
    _run([adb_exe, "shell", "input", "keyevent", "KEYCODE_WAKEUP"], check=False)
    _run([adb_exe, "shell", "input", "keyevent", "82"], check=False)


def _cmd_host(args: argparse.Namespace, repo: Path) -> int:
    sdk = discover_android_sdk(repo)
    adb_exe = _sdk_tool(sdk, "platform-tools", "adb")

    if args.command == "status":
        devices = _run([adb_exe, "devices", "-l"], capture=True, check=False)
        if devices.stdout:
            print(devices.stdout.rstrip())
        if devices.stderr:
            print(devices.stderr.rstrip(), file=sys.stderr)
        emu_exe = _sdk_tool(sdk, "emulator", "emulator")
        avds = _list_avds(emu_exe)
        print("AVDs: " + (", ".join(avds) if avds else "(none)"))
        return devices.returncode

    if args.command == "up":
        if not _has_online_device(adb_exe):
            emu_exe = _sdk_tool(sdk, "emulator", "emulator")
            emu_cmd = [emu_exe, "-avd", args.avd]
            if not args.snapshot:
                emu_cmd.append("-no-snapshot-load")
            creationflags = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) if os.name == "nt" else 0
            subprocess.Popen(
                emu_cmd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                stdin=subprocess.DEVNULL,
                creationflags=creationflags,
                start_new_session=(os.name != "nt"),
            )
        _boot_wait(adb_exe, args.timeout)
        _wake_and_unlock(adb_exe)
        return 0

    if args.command == "boot-wait":
        _boot_wait(adb_exe, args.timeout)
        return 0

    if args.command == "down":
        proc = _run([adb_exe, "emu", "kill"], check=False)
        return proc.returncode

    if args.command == "install":
        apk = Path(args.apk) if args.apk else default_release_apk(repo, must_exist=True)
        if not apk.is_file():
            raise RuntimeError(f"APK not found: {apk}")
        return _run([adb_exe, "install", "-r", str(apk)], check=False).returncode

    if args.command == "launch":
        return _run([adb_exe, "shell", "am", "start", "-n", args.component], check=False).returncode

    if args.command == "screenshot":
        out = Path(args.png)
        out.parent.mkdir(parents=True, exist_ok=True)
        proc = subprocess.run([adb_exe, "exec-out", "screencap", "-p"], capture_output=True, check=False)
        if proc.returncode != 0:
            if proc.stderr:
                sys.stderr.buffer.write(proc.stderr)
            return proc.returncode
        out.write_bytes(proc.stdout)
        print(f"Saved screenshot: {out} ({len(proc.stdout)} bytes)")
        return 0

    if args.command == "ui":
        remote = "/sdcard/window_dump.xml"
        dump = _run([adb_exe, "shell", "uiautomator", "dump", remote], check=False)
        if dump.returncode != 0:
            return dump.returncode
        if args.out_xml:
            out = Path(args.out_xml)
            out.parent.mkdir(parents=True, exist_ok=True)
            return _run([adb_exe, "pull", remote, str(out)], check=False).returncode
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "window_dump.xml"
            pull = _run([adb_exe, "pull", remote, str(target)], check=False)
            if pull.returncode != 0:
                return pull.returncode
            print(target.read_text(encoding="utf-8", errors="replace"))
        return 0

    if args.command == "logcat":
        if args.clear:
            return _run([adb_exe, "logcat", "-c"], check=False).returncode
        return _run(
            [adb_exe, "logcat", "-d", "-t", str(args.lines), "AndroidRuntime:E", "com.mobixournal:V", "*:S"],
            check=False,
        ).returncode

    if args.command == "shell":
        return _run([adb_exe, "shell", *args.args], check=False).returncode

    if args.command == "adb":
        return _run([adb_exe, *args.args], check=False).returncode

    raise RuntimeError(f"Unknown command: {args.command}")


def _delegate_container(script: Path, argv: list[str], repo: Path) -> int:
    if argv and argv[0] == "install" and len(argv) == 1:
        apk = default_release_apk(repo, must_exist=True)
        argv = ["install", str(apk)]
    return subprocess.run([str(script), *argv], check=False).returncode


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="emulator",
        description="Cross-platform Android emulator harness (host SDK and /data/android).",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("status", help="List connected devices and available AVDs.")

    up_p = sub.add_parser("up", help="Boot the AVD (cold boot by default) and wake the screen.")
    up_p.add_argument("--avd", default=DEFAULT_AVD, help=f"AVD name (default: {DEFAULT_AVD}).")
    up_p.add_argument("--snapshot", action="store_true", help="Allow snapshot resume instead of -no-snapshot-load.")
    up_p.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT, help="Boot wait timeout in seconds.")

    bw_p = sub.add_parser("boot-wait", help="Wait for sys.boot_completed=1.")
    bw_p.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT, help="Boot wait timeout in seconds.")

    sub.add_parser("down", help="Stop the running emulator.")

    inst_p = sub.add_parser("install", help="Install an APK (defaults to signed MobiXournal-<version>.apk).")
    inst_p.add_argument("apk", nargs="?", help="Optional APK path; defaults to signed release APK.")

    launch_p = sub.add_parser("launch", help="Launch the app activity.")
    launch_p.add_argument("component", nargs="?", default=DEFAULT_COMPONENT, help="Component to launch.")

    shot_p = sub.add_parser("screenshot", help="Capture screen PNG via adb exec-out screencap -p.")
    shot_p.add_argument("png", help="Destination PNG path.")

    ui_p = sub.add_parser("ui", help="Dump UI hierarchy XML via uiautomator.")
    ui_p.add_argument("out_xml", nargs="?", help="Optional destination file; prints to stdout if omitted.")

    log_p = sub.add_parser("logcat", help="Inspect or clear MobiXournal/AndroidRuntime logcat output.")
    log_p.add_argument("--clear", action="store_true", help="Clear logcat buffer.")
    log_p.add_argument("--lines", type=int, default=120, help="Trailing lines to dump (default: 120).")

    sh_p = sub.add_parser("shell", help="Run adb shell <args...>.")
    sh_p.add_argument("args", nargs=argparse.REMAINDER, help="Arguments passed to adb shell.")

    adb_p = sub.add_parser("adb", help="Run raw adb <args...>.")
    adb_p.add_argument("args", nargs=argparse.REMAINDER, help="Arguments passed to adb.")

    return parser


def main(argv: list[str] | None = None) -> int:
    raw_argv = sys.argv[1:] if argv is None else argv
    repo = _repo_root()
    container_sh = _container_emulator_sh()
    if container_sh is not None:
        return _delegate_container(container_sh, raw_argv, repo)
    args = _build_parser().parse_args(raw_argv)
    try:
        return _cmd_host(args, repo)
    except RuntimeError as exc:
        print(f"emulator: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

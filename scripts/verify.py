"""Sequential task-closing verification runner for Windows and POSIX."""
from __future__ import annotations

import argparse
import getpass
import json
import os
from pathlib import Path
import platform
import re
import shutil
import socket
import subprocess
import sys

PREFERRED_JDKS = (21, 17, 22, 20, 23, 19, 18, 24)
GUARD_CLASSES = (
    "com.mobixournal.guards.AgentInstructionsTest",
    "com.mobixournal.guards.CanvasFluidityGuardTest",
    "com.mobixournal.guards.ChromeGeometryGuardTest",
    "com.mobixournal.guards.CiCoverageTest",
    "com.mobixournal.guards.ColorContractGuardTest",
    "com.mobixournal.guards.DocClaimsTest",
    "com.mobixournal.guards.DocsGuardTest",
    "com.mobixournal.guards.InstallerFileAssociationsTest",
    "com.mobixournal.guards.ManifestIntentFilterTest",
    "com.mobixournal.guards.PackageStructureTest",
    "com.mobixournal.guards.SettingsBackupGuardTest",
    "com.mobixournal.guards.SettingsCopyParityTest",
    "com.mobixournal.guards.SettingsWriteGuardTest",
    "com.mobixournal.guards.SourceStructureTest",
    "com.mobixournal.guards.SystemBarsGuardTest",
    "com.mobixournal.guards.TypeContractGuardTest",
    "com.mobixournal.guards.XoppExportCoverageTest",
    "com.mobixournal.ui.ToolbarMenuGuardTest",
)
ANDROID_UI_EXTRA_CLASSES = (
    "com.mobixournal.ui.ShapeFeedbackTest",
    "com.mobixournal.ui.ToolGlyphCoverageTest",
    "com.mobixournal.ui.SettingsGlyphParityTest",
)
DESKTOP_UI_GUARD_CLASSES = (
    "com.mobixournal.desktop.DesktopThemeTest",
    "com.mobixournal.desktop.DesktopDialogContractTest",
    "com.mobixournal.desktop.DesktopTextFeedbackContractTest",
    "com.mobixournal.desktop.GlassPopupGuardTest",
    "com.mobixournal.desktop.DesktopPopupMaterialTest",
    "com.mobixournal.desktop.DesktopSettingsGlyphTest",
    "com.mobixournal.desktop.DesktopSettingsStyleTest",
    "com.mobixournal.desktop.DesktopShapeFeedbackTest",
    "com.mobixournal.desktop.DismissGuardTest",
    "com.mobixournal.desktop.ShortcutTableTest",
    "com.mobixournal.desktop.ChromeMaterialRenderTest",
    "com.mobixournal.desktop.LightMaterialTest",
    "com.mobixournal.desktop.ToolStripsTest",
)


def _repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def _probe_jdk_major(jdk_root: Path) -> int | None:
    """Return the JDK major version for jdk_root, or None if unusable."""
    java_exe = jdk_root / "bin" / ("java.exe" if os.name == "nt" else "java")
    if not java_exe.is_file():
        return None
    proc = subprocess.run(
        [str(java_exe), "-version"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    match = re.search(r'"(\d+)(?:\.[^"]*)?"', f"{proc.stdout}\n{proc.stderr}")
    return int(match.group(1)) if match else None


def _windows_jdk_candidates() -> list[Path]:
    candidates: list[Path] = []
    for var in ("HOST_JDK", "JAVA_HOME"):
        if os.environ.get(var):
            candidates.append(Path(os.environ[var]))
    jdks_dir = Path.home() / ".jdks"
    if jdks_dir.is_dir():
        candidates.extend(sorted(jdks_dir.iterdir()))
    local_app = os.environ.get("LOCALAPPDATA")
    if local_app:
        candidates.append(Path(local_app) / "Programs" / "Android Studio" / "jbr")
    prog_files = Path(os.environ.get("ProgramFiles", r"C:\Program Files"))
    candidates.append(prog_files / "Android" / "Android Studio" / "jbr")
    java_dir = prog_files / "Java"
    if java_dir.is_dir():
        candidates.extend(sorted(java_dir.iterdir()))
    return candidates


def _select_windows_jdk() -> str:
    """Select an installed JDK in {17..24}, preferring JDK 21."""
    best: tuple[int, Path] | None = None
    for idx, root in enumerate(_windows_jdk_candidates()):
        major = _probe_jdk_major(root)
        if major not in PREFERRED_JDKS:
            continue
        if idx == 0 and os.environ.get("HOST_JDK"):
            return str(root)
        rank = PREFERRED_JDKS.index(major)
        if best is None or rank < best[0]:
            best = (rank, root)
    if best is None:
        raise RuntimeError(
            "No supported JDK (17-24, preferring 21) found; set JAVA_HOME or HOST_JDK."
        )
    return str(best[1])


def _posix_jdk_candidates() -> list[Path]:
    candidates: list[Path] = []
    for var in ("HOST_JDK", "JAVA_HOME"):
        if os.environ.get(var):
            candidates.append(Path(os.environ[var]))
    for root in (
        Path.home() / ".jdks",
        Path.home() / ".sdkman" / "candidates" / "java",
        Path.home() / ".gradle" / "jdks",
        Path("/usr/lib/jvm"),
        Path("/opt/java"),
    ):
        if root.is_dir():
            candidates.extend(sorted(root.iterdir()))
    java_on_path = shutil.which("java")
    if java_on_path:
        candidates.append(Path(java_on_path).resolve().parent.parent)
    return candidates


def _select_posix_jdk() -> str | None:
    best: tuple[int, Path] | None = None
    for root in _posix_jdk_candidates():
        major = _probe_jdk_major(root)
        if major not in PREFERRED_JDKS:
            continue
        rank = PREFERRED_JDKS.index(major)
        if best is None or rank < best[0]:
            best = (rank, root)
    return str(best[1]) if best else None


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


def _discover_android_sdk(repo: Path) -> str | None:
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
            return str(cand)
    return None


def _find_git_bash() -> str | None:
    """Locate a non-WindowsApps bash.exe on Windows."""
    candidates: list[str] = []
    if os.environ.get("MOBIX_BASH"):
        candidates.append(os.environ["MOBIX_BASH"])
    prog_files = Path(os.environ.get("ProgramFiles", r"C:\Program Files"))
    candidates.extend([
        str(prog_files / "Git" / "bin" / "bash.exe"),
        str(prog_files / "Git" / "usr" / "bin" / "bash.exe"),
    ])
    on_path = shutil.which("bash")
    if on_path:
        candidates.append(on_path)
    for cand in candidates:
        if "windowsapps" in cand.lower():
            continue
        if Path(cand).is_file():
            return cand
    return None


def _list_avds(sdk_path: str | None) -> list[str]:
    if not sdk_path:
        return []
    emu = Path(sdk_path) / "emulator" / ("emulator.exe" if os.name == "nt" else "emulator")
    if not emu.is_file():
        return []
    try:
        proc = subprocess.run(
            [str(emu), "-list-avds"],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=5,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return []
    if proc.returncode != 0:
        return []
    return [line.strip() for line in proc.stdout.splitlines() if line.strip()]


def _git_hooks_path(repo: Path) -> str | None:
    try:
        proc = subprocess.run(
            ["git", "config", "--get", "core.hooksPath"],
            cwd=repo,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=5,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    if proc.returncode != 0:
        return None
    val = proc.stdout.strip()
    return val or None


def _detect_host_env(repo: Path) -> dict[str, object]:
    """Inspect host profile, toolchain paths, AVDs, and git hooks without running builds."""
    toolchain = Path(os.environ.get("ANDROID_TOOLCHAIN", "/data/android"))
    has_container = os.name != "nt" and os.access(toolchain / "build.sh", os.X_OK)
    hostname = os.environ.get("COMPUTERNAME") or socket.gethostname()
    user = os.environ.get("USERNAME") or getpass.getuser()
    if has_container:
        profile = "A"
    elif os.name == "nt" and hostname.upper() == "ANTONIOFISSO-PC":
        profile = "D"
    elif os.name == "nt" and user.lower() == "amarz":
        profile = "B"
    else:
        profile = "C"

    jdk: str | None = None
    jdk_major: int | None = None
    try:
        jdk = _select_windows_jdk() if os.name == "nt" else _select_posix_jdk()
        if jdk:
            jdk_major = _probe_jdk_major(Path(jdk))
    except RuntimeError:
        jdk = None

    sdk = _discover_android_sdk(repo)
    hooks_path = _git_hooks_path(repo)
    return {
        "os": platform.system(),
        "hostname": hostname,
        "user": user,
        "profile": profile,
        "container_toolchain": str(toolchain) if has_container else None,
        "python": sys.executable,
        "python_version": platform.python_version(),
        "jdk": jdk,
        "jdk_major": jdk_major,
        "android_sdk": sdk,
        "avds": _list_avds(sdk),
        "git_bash": _find_git_bash() if os.name == "nt" else shutil.which("sh"),
        "git_hooks_path": hooks_path,
        "hooks_configured": hooks_path == ".githooks",
    }


def _format_host_env(info: dict[str, object]) -> str:
    avds = info["avds"]
    avd_text = ", ".join(avds) if isinstance(avds, list) and avds else "(none)"
    return "\n".join([
        f"Host profile: {info['profile']} ({info['os']} / {info['hostname']} / {info['user']})",
        f"Container toolchain: {info['container_toolchain'] or 'absent'}",
        f"Python: {info['python']} ({info['python_version']})",
        f"JDK: {info['jdk'] or 'not found'} (major={info['jdk_major']})",
        f"Android SDK: {info['android_sdk'] or 'not found'}",
        f"AVDs: {avd_text}",
        f"Shell runner: {info['git_bash'] or 'not found'}",
        f"Git hooksPath: {info['git_hooks_path'] or 'unset'} (configured={info['hooks_configured']})",
    ])


def _run_step(cmd: list[str], cwd: Path, env: dict[str, str]) -> None:
    print(f"==> {' '.join(cmd)}", flush=True)
    proc = subprocess.run(cmd, cwd=cwd, env=env, check=False)
    if proc.returncode != 0:
        raise SystemExit(proc.returncode)


def _gradle_env(repo: Path, env: dict[str, str]) -> dict[str, str]:
    if os.name == "nt":
        java_home = env.get("JAVA_HOME")
        if not java_home or _probe_jdk_major(Path(java_home)) not in PREFERRED_JDKS:
            java_home = _select_windows_jdk()
        run_env = {**env, "JAVA_HOME": java_home}
    else:
        run_env = dict(env)
    sdk = _discover_android_sdk(repo)
    if sdk:
        run_env.setdefault("ANDROID_HOME", sdk)
        run_env.setdefault("ANDROID_SDK_ROOT", sdk)
    return run_env


def _run_gradle(repo: Path, gradle_args: list[str], env: dict[str, str]) -> None:
    """Run one Gradle command using the platform runner sequentially."""
    run_env = _gradle_env(repo, env)
    if os.name == "nt":
        cmd = ["cmd.exe", "/c", str(repo / "gradlew.bat"), *gradle_args]
    else:
        toolchain = Path(os.environ.get("ANDROID_TOOLCHAIN", "/data/android"))
        runner = "build.sh" if os.access(toolchain / "build.sh", os.X_OK) else "host-build.sh"
        cmd = [str(repo / "scripts" / runner), *gradle_args]
    _run_step(cmd, repo, run_env)


def _run_release_apk(repo: Path, env: dict[str, str]) -> None:
    """Build and verify the signed release APK via scripts/release-apk.sh."""
    run_env = _gradle_env(repo, env)
    script = str(repo / "scripts" / "release-apk.sh")
    if os.name == "nt":
        bash_exe = _find_git_bash()
        if not bash_exe:
            raise RuntimeError("Git Bash not found; install Git for Windows or set MOBIX_BASH.")
        cmd = [bash_exe, script]
    else:
        cmd = ["sh", script]
    _run_step(cmd, repo, run_env)


def _parse_args(argv: list[str]) -> tuple[argparse.Namespace, list[str]]:
    parser = argparse.ArgumentParser(
        prog="verify",
        description="Run sequential MobiXournal validation, guard suites, tests, and packaging.",
    )
    parser.add_argument("--env", action="store_true", help="Print detected host profile, JDK, SDK, Git Bash, AVDs, and git hook status, then exit.")
    parser.add_argument("--json", action="store_true", help="Format --env output as JSON.")
    parser.add_argument("--full", action="store_true", help="Run all 3 module test suites and androidTest compile sequentially.")
    parser.add_argument("--ui-guards", action="store_true", help="Run full mobile and desktop UI guard suites.")
    parser.add_argument("--build-apk", action="store_true", help="Build the signed release APK via scripts/release-apk.sh (when rebuild=true).")
    parser.add_argument("--todo-tests", action="store_true", help="Run scripts/todo/test_*.py and scripts/test-*.py suites.")
    parser.add_argument("--skip-distributable", action="store_true", help="Skip :desktop:createDistributable.")
    if "--" in argv:
        split = argv.index("--")
        ns, extra = parser.parse_known_args(argv[:split])
        return ns, [*extra, *argv[split + 1 :]]
    return parser.parse_known_args(argv)


def _tests_args(classes: tuple[str, ...]) -> list[str]:
    return [arg for cls in classes for arg in ("--tests", cls)]


def main(argv: list[str] | None = None) -> int:
    """Execute the sequential task verification workflow."""
    args, gradle_args = _parse_args(sys.argv[1:] if argv is None else argv)
    repo = _repo_root()
    if args.env:
        info = _detect_host_env(repo)
        print(json.dumps(info, indent=2) if args.json else _format_host_env(info))
        return 0
    env = {**os.environ, "PYTHONUTF8": "1"}
    _run_step([sys.executable, str(repo / "scripts" / "todo" / "todo.py"), "validate"], repo, env)
    if args.todo_tests:
        todo_tests = sorted((repo / "scripts" / "todo").glob("test_*.py"))
        script_tests = sorted((repo / "scripts").glob("test-*.py"))
        for test_file in [*todo_tests, *script_tests]:
            _run_step([sys.executable, str(test_file)], repo, env)
    if gradle_args:
        _run_gradle(repo, gradle_args, env)
    if args.full:
        for task in (
            ":mobix-core:test",
            ":app:testDebugUnitTest",
            ":app:compileDebugAndroidTestKotlin",
            ":desktop:test",
        ):
            _run_gradle(repo, [task], env)
    else:
        app_guards = GUARD_CLASSES + (ANDROID_UI_EXTRA_CLASSES if args.ui_guards else ())
        _run_gradle(repo, [":app:testDebugUnitTest", *_tests_args(app_guards)], env)
    if args.ui_guards and not args.full:
        _run_gradle(repo, [":desktop:test", *_tests_args(DESKTOP_UI_GUARD_CLASSES)], env)
    if args.build_apk:
        _run_release_apk(repo, env)
    if not args.skip_distributable:
        _run_gradle(repo, [":desktop:createDistributable"], env)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

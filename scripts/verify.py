"""Sequential task-closing verification runner for Windows and POSIX."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

PREFERRED_JDKS = (21, 17, 22, 20, 23, 19, 18, 24)
GUARD_CLASSES = (
    "com.mobixournal.guards.DocsGuardTest",
    "com.mobixournal.guards.DocClaimsTest",
    "com.mobixournal.guards.SourceStructureTest",
    "com.mobixournal.guards.AgentInstructionsTest",
    "com.mobixournal.guards.CiCoverageTest",
    "com.mobixournal.guards.ColorContractGuardTest",
    "com.mobixournal.guards.ChromeGeometryGuardTest",
    "com.mobixournal.guards.TypeContractGuardTest",
    "com.mobixournal.guards.SystemBarsGuardTest",
    "com.mobixournal.guards.SettingsCopyParityTest",
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


def _run_step(cmd: list[str], cwd: Path, env: dict[str, str]) -> None:
    print(f"==> {' '.join(cmd)}", flush=True)
    proc = subprocess.run(cmd, cwd=cwd, env=env, check=False)
    if proc.returncode != 0:
        raise SystemExit(proc.returncode)


def _run_gradle(repo: Path, gradle_args: list[str], env: dict[str, str]) -> None:
    """Run one Gradle command using the platform runner sequentially."""
    if os.name == "nt":
        java_home = env.get("JAVA_HOME")
        if not java_home or _probe_jdk_major(Path(java_home)) not in PREFERRED_JDKS:
            java_home = _select_windows_jdk()
        run_env = {**env, "JAVA_HOME": java_home}
        cmd = ["cmd.exe", "/c", str(repo / "gradlew.bat"), *gradle_args]
    else:
        run_env = env
        toolchain = Path(os.environ.get("ANDROID_TOOLCHAIN", "/data/android"))
        runner = "build.sh" if os.access(toolchain / "build.sh", os.X_OK) else "host-build.sh"
        cmd = [str(repo / "scripts" / runner), *gradle_args]
    _run_step(cmd, repo, run_env)


def _parse_args(argv: list[str]) -> tuple[argparse.Namespace, list[str]]:
    parser = argparse.ArgumentParser(
        prog="verify",
        description="Run sequential MobiXournal validation, guard suites, tests, and packaging.",
    )
    parser.add_argument("--full", action="store_true", help="Run all 3 module test suites sequentially.")
    parser.add_argument("--ui-guards", action="store_true", help="Run full mobile and desktop UI guard suites.")
    parser.add_argument("--build-apk", action="store_true", help="Run :app:assembleDebug (when rebuild=true).")
    parser.add_argument("--todo-tests", action="store_true", help="Run scripts/todo/test_*.py suites.")
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
    env = {**os.environ, "PYTHONUTF8": "1"}
    _run_step([sys.executable, str(repo / "scripts" / "todo" / "todo.py"), "validate"], repo, env)
    if args.todo_tests:
        for test_file in sorted((repo / "scripts" / "todo").glob("test_*.py")):
            _run_step([sys.executable, str(test_file)], repo, env)
    if gradle_args:
        _run_gradle(repo, gradle_args, env)
    if args.full:
        for suite in (":mobix-core:test", ":app:testDebugUnitTest", ":desktop:test"):
            _run_gradle(repo, [suite], env)
    app_guards = GUARD_CLASSES + (ANDROID_UI_EXTRA_CLASSES if args.ui_guards else ())
    _run_gradle(repo, [":app:testDebugUnitTest", *_tests_args(app_guards)], env)
    if args.ui_guards and not args.full:
        _run_gradle(repo, [":desktop:test", *_tests_args(DESKTOP_UI_GUARD_CLASSES)], env)
    if args.build_apk:
        _run_gradle(repo, [":app:assembleDebug"], env)
    if not args.skip_distributable:
        _run_gradle(repo, [":desktop:createDistributable"], env)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

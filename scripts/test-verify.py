#!/usr/bin/env python3
"""Tests for scripts/verify.py guard coverage, signed APK routing, and --env probe."""
from __future__ import annotations

import contextlib
import io
import json
from pathlib import Path
import sys
import unittest
from unittest import mock

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "scripts"))

import verify  # noqa: E402


class VerifyScriptTest(unittest.TestCase):
    def test_guard_classes_include_every_guard_test_in_repo(self):
        guards_dir = REPO_ROOT / "app" / "src" / "test" / "java" / "com" / "mobixournal" / "guards"
        expected = {f"com.mobixournal.guards.{p.stem}" for p in guards_dir.glob("*Test.kt")}
        self.assertTrue(expected, "Expected at least one guard test file")
        missing = sorted(expected - set(verify.GUARD_CLASSES))
        self.assertEqual([], missing, f"GUARD_CLASSES is missing guard tests: {missing}")

    def test_build_apk_invokes_release_apk_never_assemble_debug(self):
        gradle_calls: list[list[str]] = []
        release_calls: list[Path] = []
        with (
            mock.patch.object(verify, "_run_step"),
            mock.patch.object(verify, "_run_gradle", side_effect=lambda _r, args, _e: gradle_calls.append(args)),
            mock.patch.object(verify, "_run_release_apk", side_effect=lambda repo, _e: release_calls.append(repo)),
        ):
            rc = verify.main(["--build-apk", "--skip-distributable"])
        self.assertEqual(0, rc)
        self.assertEqual([REPO_ROOT], release_calls)
        flattened = [arg for call in gradle_calls for arg in call]
        self.assertNotIn(":app:assembleDebug", flattened)

    def test_full_runs_all_suites_and_android_test_compile_without_double_app_run(self):
        gradle_calls: list[list[str]] = []
        with (
            mock.patch.object(verify, "_run_step"),
            mock.patch.object(verify, "_run_gradle", side_effect=lambda _r, args, _e: gradle_calls.append(args)),
        ):
            rc = verify.main(["--full", "--ui-guards", "--skip-distributable"])
        self.assertEqual(0, rc)
        self.assertEqual(
            [
                [":mobix-core:test"],
                [":app:testDebugUnitTest"],
                [":app:compileDebugAndroidTestKotlin"],
                [":desktop:test"],
            ],
            gradle_calls,
        )

    def test_ui_guards_without_full_runs_android_and_desktop_guard_classes(self):
        gradle_calls: list[list[str]] = []
        with (
            mock.patch.object(verify, "_run_step"),
            mock.patch.object(verify, "_run_gradle", side_effect=lambda _r, args, _e: gradle_calls.append(args)),
        ):
            rc = verify.main(["--ui-guards", "--skip-distributable"])
        self.assertEqual(0, rc)
        self.assertEqual(2, len(gradle_calls))
        self.assertEqual(":app:testDebugUnitTest", gradle_calls[0][0])
        for cls in (*verify.GUARD_CLASSES, *verify.ANDROID_UI_EXTRA_CLASSES):
            self.assertIn(cls, gradle_calls[0])
        self.assertEqual(":desktop:test", gradle_calls[1][0])
        for cls in verify.DESKTOP_UI_GUARD_CLASSES:
            self.assertIn(cls, gradle_calls[1])

    def test_env_json_runs_no_gradle_and_reports_expected_keys(self):
        buf = io.StringIO()
        with (
            mock.patch.object(verify, "_run_step", side_effect=AssertionError("unexpected step")),
            mock.patch.object(verify, "_run_gradle", side_effect=AssertionError("unexpected gradle")),
            contextlib.redirect_stdout(buf),
        ):
            rc = verify.main(["--env", "--json"])
        self.assertEqual(0, rc)
        payload = json.loads(buf.getvalue())
        for key in ("profile", "os", "python", "git_hooks_path", "hooks_configured"):
            self.assertIn(key, payload)
        self.assertIn(payload["profile"], {"A", "B", "C", "D"})


if __name__ == "__main__":
    raise SystemExit(unittest.main())

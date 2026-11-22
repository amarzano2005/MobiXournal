#!/usr/bin/env python3
"""Tests for scripts/emulator.py signed APK defaults, SDK discovery, and host commands."""
from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "scripts"))

import emulator  # noqa: E402


class EmulatorScriptTest(unittest.TestCase):
    def test_default_release_apk_reads_version_and_never_uses_debug_apk(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "app").mkdir()
            (root / "app" / "build.gradle.kts").write_text(
                'android {\n    defaultConfig {\n        versionName = "2.4.0"\n    }\n}\n',
                encoding="utf-8",
            )
            with self.assertRaises(RuntimeError) as ctx:
                emulator.default_release_apk(root, must_exist=True)
            msg = str(ctx.exception)
            self.assertIn("MobiXournal-2.4.0.apk", msg)
            self.assertNotIn("app-debug.apk", msg)

            apk = root / "app" / "build" / "outputs" / "apk" / "release" / "MobiXournal-2.4.0.apk"
            apk.parent.mkdir(parents=True, exist_ok=True)
            apk.write_bytes(b"apk")
            self.assertEqual(apk, emulator.default_release_apk(root, must_exist=True))

    def test_discover_android_sdk_reads_escaped_local_properties(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            sdk = root / "FakeSdk"
            (sdk / "platforms").mkdir(parents=True)
            escaped = str(sdk).replace("\\", "\\\\").replace(":", "\\:")
            (root / "local.properties").write_text(f"sdk.dir={escaped}\n", encoding="utf-8")
            with mock.patch.dict(os.environ, {}, clear=True):
                self.assertEqual(sdk, emulator.discover_android_sdk(root))

    def test_up_install_launch_and_screenshot_commands(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            sdk = root / "Sdk"
            (sdk / "platform-tools").mkdir(parents=True)
            (sdk / "emulator").mkdir(parents=True)
            adb_name = "adb.exe" if os.name == "nt" else "adb"
            emu_name = "emulator.exe" if os.name == "nt" else "emulator"
            adb_exe = sdk / "platform-tools" / adb_name
            emu_exe = sdk / "emulator" / emu_name
            adb_exe.write_text("", encoding="utf-8")
            emu_exe.write_text("", encoding="utf-8")

            (root / "app").mkdir()
            (root / "app" / "build.gradle.kts").write_text('versionName = "2.4.0"\n', encoding="utf-8")
            apk = root / "app" / "build" / "outputs" / "apk" / "release" / "MobiXournal-2.4.0.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"signed-apk")

            calls: list[list[str]] = []
            popen_calls: list[list[str]] = []

            def fake_run(cmd, **_kwargs):
                calls.append(cmd)
                if "devices" in cmd and "-l" not in cmd:
                    return subprocess.CompletedProcess(cmd, 0, "List of devices attached\n", "")
                if "getprop" in cmd:
                    return subprocess.CompletedProcess(cmd, 0, "1\n", "")
                if "screencap" in cmd:
                    return subprocess.CompletedProcess(cmd, 0, b"\x89PNG\r\n\x1a\n", b"")
                return subprocess.CompletedProcess(cmd, 0, "", "")

            shot = root / "out" / "shot.png"
            with (
                mock.patch.object(emulator, "_repo_root", return_value=root),
                mock.patch.object(emulator, "_container_emulator_sh", return_value=None),
                mock.patch.object(emulator, "discover_android_sdk", return_value=sdk),
                mock.patch("subprocess.run", side_effect=fake_run),
                mock.patch("subprocess.Popen", side_effect=lambda cmd, **_kw: popen_calls.append(cmd)),
            ):
                self.assertEqual(0, emulator.main(["up"]))
                self.assertEqual(0, emulator.main(["install"]))
                self.assertEqual(0, emulator.main(["launch"]))
                self.assertEqual(0, emulator.main(["screenshot", str(shot)]))

            self.assertEqual([[str(emu_exe), "-avd", "Pixel_Tablet", "-no-snapshot-load"]], popen_calls)
            self.assertIn([str(adb_exe), "install", "-r", str(apk)], calls)
            self.assertIn([str(adb_exe), "shell", "am", "start", "-n", emulator.DEFAULT_COMPONENT], calls)
            self.assertEqual(b"\x89PNG\r\n\x1a\n", shot.read_bytes())


if __name__ == "__main__":
    raise SystemExit(unittest.main())

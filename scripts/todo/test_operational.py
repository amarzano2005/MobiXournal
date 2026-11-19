#!/usr/bin/env python3
"""Regression checks for active-task operational consistency; no dependencies."""

import argparse
import contextlib
import datetime
import io
import os
import tempfile
import unittest

import operational
import store
import todo


def task(**overrides):
    entry = dict(
        id="check-example",
        title="Check the codec",
        description=(
            "Verify round-trip encoding in "
            "mobix-core/src/main/java/com/mobixournal/format/mobix/MobixElementCodec.kt; "
            "run MobixElementCodecTest."
        ),
        status="active",
        category="test",
        level="task",
    )
    entry.update(overrides)
    return entry


class OperationalTest(unittest.TestCase):
    def test_obsolete_package_is_actionable(self):
        errors = operational.problems(task(description="Launch com.mobixournal.v2."))
        self.assertTrue(any("use com.mobixournal" in error for error in errors))
        self.assertEqual([], operational.problems(task(description="Launch com.mobixournal.")))

    def test_historical_package_mentions_do_not_allow_later_current_commands(self):
        history = "Historical note: com.mobixournal.v2 was the old identifier. "
        self.assertEqual([], operational.problems(task(
            description=history + "Launch com.mobixournal.")))
        self.assertTrue(operational.problems(task(
            description=history + "Launch com.mobixournal.v2.")))
        self.assertTrue(operational.problems(task(description=(
            "Historical note: an identifier changed. Launch com.mobixournal.v2."))))

    def test_present_host_claims_are_rejected(self):
        for claim in ("this host has no adb or AVD", "this machine has no AVD",
                      "no adb and no AVD on this machine", "No adb or AVD on this host"):
            with self.subTest(claim=claim):
                self.assertTrue(operational.problems(task(description=claim)))

    def test_explicit_history_does_not_claim_current_host(self):
        for note in ("Historical observation (2026-11-11): this host has no adb or AVD.",
                     "On 2026-11-11, this machine has no AVD.",
                     "The previous host has no adb or AVD."):
            with self.subTest(note=note):
                self.assertEqual([], operational.problems(task(description=note)))
        self.assertTrue(operational.problems(task(description=(
            "Historical note: this host has no adb. This machine has no AVD."))))

    def test_device_test_requires_true_flag_with_false_default(self):
        for flags in ({}, {"emulator_debug": False}):
            with self.subTest(flags=flags):
                self.assertTrue(operational.problems(task(
                    title="Run the parametric plot pass on a device", **flags)))
        self.assertEqual([], operational.problems(task(
            title="Run the plot pass on the emulator", emulator_debug=True, rebuild=False)))

    def test_feature_and_nonruntime_tests_are_not_inferred(self):
        self.assertEqual([], operational.problems(task(category="feature", emulator_debug=False,
            title="Review device controls", description="No emulator pass is required.")))
        self.assertEqual([], operational.problems(task(title="Document emulator instructions")))
        self.assertEqual([], operational.problems(task(title="Run device research", level="scope")))
        self.assertEqual([], operational.problems(task(title="Test emulator configuration parsing")))
        self.assertEqual([], operational.problems(task(title="Test the device parser on the JVM")))
        self.assertTrue(operational.problems(task(title="Run parser tests on the emulator")))

    def test_description_apk_install_and_evidence_requires_emulator_debug(self):
        desc = "Install the debug APK, open a sheet, and capture screenshots with logcat clean."
        self.assertTrue(operational.problems(task(category="feature", title="Check feature", description=desc)))
        self.assertEqual(
            [],
            operational.problems(task(category="feature", title="Check feature", description=desc, emulator_debug=True)),
        )

    def test_validate_checks_active_tasks_but_preserves_archive_history(self):
        with tempfile.TemporaryDirectory() as root:
            historical = task(id="historical-example", status="finished",
                completed=datetime.date(2026, 11, 11),
                title="Run the emulator pass", description="this host has no adb; com.mobixournal.v2")
            store.save_finished(dict(meta={}, tasks=[historical]), os.path.join(root, "FINISHED.toml"))
            store.save_todo(dict(meta={}, tasks=[task()]), os.path.join(root, "TODO.toml"))
            with contextlib.redirect_stdout(io.StringIO()):
                todo.cmd_validate(argparse.Namespace(), root=root)
            store.save_todo(dict(meta={}, tasks=[task(title="Run the emulator pass")]),
                            os.path.join(root, "TODO.toml"))
            output = io.StringIO()
            with contextlib.redirect_stdout(output), self.assertRaises(SystemExit) as result:
                todo.cmd_validate(argparse.Namespace(), root=root)
            self.assertEqual(1, result.exception.code)
            self.assertIn("TODO [check-example]", output.getvalue())
            self.assertIn("--emulator-debug", output.getvalue())


if __name__ == "__main__":
    unittest.main()

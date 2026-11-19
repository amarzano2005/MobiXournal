#!/usr/bin/env python3
"""Regression tests for `todo brief` and `todo doctor` (`doctor.py`)."""

from __future__ import annotations

import argparse
import contextlib
import datetime
import io
import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import doctor  # noqa: E402
import store  # noqa: E402
import todo  # noqa: E402


def _write_lines(path, count, line="line\n"):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(line * count)


def _git(cwd, *args, env=None):
    res = subprocess.run(
        ["git", *args], cwd=cwd, env=env, capture_output=True, text=True, check=True,
    )
    return res.stdout


class DoctorTest(unittest.TestCase):
    def test_brief_includes_shipped_deps_ceilings_and_verification(self):
        with tempfile.TemporaryDirectory() as tmp:
            doc_path = os.path.join(tmp, "docs", "big.md")
            src_path = os.path.join(tmp, "app", "src", "main", "java", "Big.kt")
            _write_lines(doc_path, 285)
            _write_lines(src_path, 610)

            done_dep = {
                "id": "dep-done",
                "title": "Done prerequisite",
                "description": "Shipped Helper.kt with unit coverage.",
                "status": "finished",
                "level": "task",
                "category": "feature",
                "completed": datetime.date(2026, 11, 12),
                "design": "none: pure helper",
            }
            active_task = {
                "id": "work-1",
                "title": "Wire UI change",
                "description": "Update app/src/main/java/Big.kt and docs/big.md; verify with BigTest.",
                "status": "active",
                "level": "task",
                "category": "feature",
                "urgency": "high",
                "order": 100,
                "created": datetime.date(2026, 11, 10),
                "depends_on": ["dep-done"],
                "targets": ["app/src/main/java/Big.kt"],
                "rebuild": True,
                "emulator_debug": True,
                "tags": [],
            }

            payload = doctor.brief_task(active_task, [active_task], [done_dep], root=tmp)
            self.assertEqual("DONE", payload["depends_on"][0]["state"])
            self.assertIn("Shipped Helper.kt", payload["depends_on"][0]["summary"])
            by_path = {f["path"]: f for f in payload["referenced_files"]}
            self.assertIn("DocsGuardTest", by_path["docs/big.md"]["warning"])
            self.assertIn("SourceStructureTest", by_path["app/src/main/java/Big.kt"]["warning"])
            self.assertTrue(any("--build-apk" in v for v in payload["verification"]))
            self.assertFalse(any("--ui-guards" in v for v in payload["verification"]))
            self.assertTrue(any("docs/tools-emulator.md" in v for v in payload["verification"]))

            ui_task = dict(active_task, id="work-ui", tags=["ui"])
            ui_payload = doctor.brief_task(ui_task, [ui_task], [done_dep], root=tmp)
            self.assertTrue(any("--ui-guards --build-apk" in v for v in ui_payload["verification"]))

            ui_doc_task = {
                "id": "work-ui-doc",
                "title": "Align layout doc",
                "description": "Update docs/desktop-ui-layout.md and verify with DesktopThemeTest.",
                "status": "active",
                "level": "task",
                "category": "docs",
                "urgency": "normal",
                "order": 110,
                "created": datetime.date(2026, 11, 10),
                "tags": [],
                "rebuild": False,
                "emulator_debug": False,
            }
            ui_doc_payload = doctor.brief_task(ui_doc_task, [ui_doc_task], [], root=tmp)
            self.assertTrue(any("--ui-guards" in v for v in ui_doc_payload["verification"]))

            rendered = doctor.format_brief(payload)
            self.assertIn("[DONE 2026-11-12] dep-done", rendered)
            self.assertIn("WARN:", rendered)

    def test_diagnose_detects_missing_ceilings_git_staleness_and_wip(self):
        with tempfile.TemporaryDirectory() as tmp:
            _write_lines(os.path.join(tmp, "docs", "tight.md"), 290)
            _write_lines(os.path.join(tmp, "desktop", "src", "main", "java", "Fat.kt"), 620)

            # Initialize a real git repo and commit a file with a newer timestamp than created.
            _git(tmp, "init")
            _git(tmp, "config", "user.name", "Test")
            _git(tmp, "config", "user.email", "test@example.com")
            _git(tmp, "add", ".")
            env = os.environ.copy()
            env["GIT_AUTHOR_DATE"] = "2026-11-18T12:00:00Z"
            env["GIT_COMMITTER_DATE"] = "2026-11-18T12:00:00Z"
            _git(tmp, "commit", "-m", "init", env=env)

            tasks = [
                {
                    "id": "t-stale-missing",
                    "title": "Edit Fat.kt and docs/tight.md",
                    "description": (
                        "Update desktop/src/main/java/Fat.kt, desktop/src/main/java/Gone.kt, "
                        "new desktop/src/main/java/CreatedFresh.kt, and docs/tight.md; verify with FatTest."
                    ),
                    "status": "active",
                    "level": "task",
                    "category": "feature",
                    "urgency": "high",
                    "order": 10,
                    "created": datetime.date(2026, 11, 10),
                    "tags": [],
                    "rebuild": False,
                    "emulator_debug": False,
                },
            ]
            # Add 15 more ready tasks to trigger WIP advisory (>15 ready).
            for i in range(15):
                tasks.append({
                    "id": f"t-extra-{i}",
                    "title": f"Extra {i}",
                    "description": "Update docs/tight.md and verify with DocsGuardTest.",
                    "status": "active",
                    "level": "task",
                    "category": "chore",
                    "urgency": "normal",
                    "order": 20 + i,
                    "created": datetime.date(2026, 11, 19),
                    "tags": [],
                    "rebuild": False,
                    "emulator_debug": False,
                })

            diag = doctor.diagnose_plan(tasks, [], root=tmp, include_all=False)
            self.assertEqual(1, len(diag["stale_tasks"]))
            self.assertEqual("t-stale-missing", diag["stale_tasks"][0]["id"])
            missing_paths = [m["path"] for m in diag["missing_paths"]]
            self.assertEqual(["desktop/src/main/java/Gone.kt"], missing_paths)
            self.assertEqual("docs/tight.md", diag["doc_ceilings"][0]["path"])
            self.assertEqual(16, diag["doc_ceilings"][0]["task_count"])
            self.assertEqual("desktop/src/main/java/Fat.kt", diag["source_ceilings"][0]["path"])
            self.assertIsNotNone(diag["wip_health"]["advisory"])

            # Marking reviewed=2026-11-19 clears staleness on t-stale-missing.
            tasks[0]["reviewed"] = datetime.date(2026, 11, 19)
            diag2 = doctor.diagnose_plan(tasks, [], root=tmp, include_all=False)
            self.assertEqual([], diag2["stale_tasks"])

    def test_cli_brief_and_doctor_strict_exit_codes(self):
        with tempfile.TemporaryDirectory() as tmp:
            _write_lines(os.path.join(tmp, "docs", "ok.md"), 10)
            active = [{
                "id": "good-1",
                "title": "Good task",
                "description": "Edit docs/ok.md and verify with DocsGuardTest.",
                "status": "active",
                "level": "task",
                "category": "docs",
                "urgency": "normal",
                "order": 10,
                "created": datetime.date(2026, 11, 10),
                "tags": [],
                "rebuild": False,
                "emulator_debug": False,
            }]
            store.save_todo({"meta": {"title": "t"}, "tasks": active}, os.path.join(tmp, "TODO.toml"))
            store.save_finished({"meta": {"title": "f"}, "tasks": []}, os.path.join(tmp, "FINISHED.toml"))

            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                todo.cmd_brief(argparse.Namespace(id="good-1", json=False), root=tmp)
                todo.cmd_doctor(argparse.Namespace(all=False, strict=True, json=False), root=tmp)
            self.assertIn("[good-1] Good task", buf.getvalue())
            self.assertIn("Doctor (ready=1): stale=0 missing=0", buf.getvalue())

            active[0]["description"] = "Edit docs/missing.md and verify with DocsGuardTest."
            store.save_todo({"meta": {"title": "t"}, "tasks": active}, os.path.join(tmp, "TODO.toml"))
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(SystemExit) as res:
                todo.cmd_doctor(argparse.Namespace(all=False, strict=True, json=False), root=tmp)
            self.assertEqual(1, res.exception.code)


if __name__ == "__main__":
    unittest.main()

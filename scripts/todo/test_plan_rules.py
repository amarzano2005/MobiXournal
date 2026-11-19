#!/usr/bin/env python3
"""Regression tests for stringent task/scope/epic rules and DAG planning."""

from __future__ import annotations

import argparse
import contextlib
import datetime
import io
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import plan_rules  # noqa: E402
import store  # noqa: E402
import todo  # noqa: E402


def epic(eid="ep-1", order=10, urgency="high", **extra):
    out = {
        "id": eid,
        "title": "Epic: cohesive feature track",
        "description": (
            "Mission: keep the feature track cohesive across core and shells. "
            "Completion means every child task is verified end to end and documented."
        ),
        "status": "active",
        "level": "epic",
        "category": "feature",
        "urgency": urgency,
        "order": order,
        "created": datetime.date(2026, 11, 10),
        "tags": [],
        "rebuild": False,
        "emulator_debug": False,
    }
    out.update(extra)
    return out


def scope(sid="sc-1", order=20, urgency="normal", **extra):
    out = {
        "id": sid,
        "title": "Investigate and scope design options",
        "description": (
            "Investigate FormatCodec.kt and decide the schema boundary for the next step, "
            "then add concrete child tasks and close this scope."
        ),
        "status": "active",
        "level": "scope",
        "category": "feature",
        "urgency": urgency,
        "order": order,
        "created": datetime.date(2026, 11, 10),
        "tags": [],
        "rebuild": False,
        "emulator_debug": False,
    }
    out.update(extra)
    return out


def task(tid="t-1", order=100, urgency="high", **extra):
    out = {
        "id": tid,
        "title": "Implement concrete model rule",
        "description": (
            "Update mobix-core/src/main/java/com/mobixournal/format/mobix/MobixElementCodec.kt "
            "with the explicit rule; verify with MobixElementCodecTest."
        ),
        "status": "active",
        "level": "task",
        "category": "feature",
        "urgency": urgency,
        "order": order,
        "created": datetime.date(2026, 11, 10),
        "tags": [],
        "rebuild": False,
        "emulator_debug": False,
    }
    out.update(extra)
    return out


def finished(fid="done-1", category="feature"):
    return {
        "id": fid,
        "title": "Completed prerequisite",
        "description": "Shipped.",
        "status": "finished",
        "level": "task",
        "category": category,
        "completed": datetime.date(2026, 11, 10),
        "design": "none: core-only change",
    }


class PlanRulesTest(unittest.TestCase):
    def test_valid_dag_passes(self):
        active = [
            epic("ep-1", order=10),
            task("t-1", order=100, epic="ep-1", depends_on=["done-1"]),
            task("t-2", order=110, urgency="normal", epic="ep-1", depends_on=["t-1"]),
        ]
        self.assertEqual([], plan_rules.validate_plan(active, [finished("done-1")]))

    def test_dangling_self_and_cycle_dependencies_fail(self):
        errs = plan_rules.validate_plan([task("t-1", depends_on=["missing"])], [])
        self.assertTrue(any("unknown id" in e for e in errs))
        errs = plan_rules.validate_plan([task("t-1", depends_on=["t-1"])], [])
        self.assertTrue(any("itself" in e for e in errs))
        errs = plan_rules.validate_plan([
            task("a", order=100, depends_on=["b"]),
            task("b", order=200, depends_on=["a"]),
        ], [])
        self.assertTrue(any("cycle in depends_on" in e for e in errs))

    def test_order_and_urgency_inversions_fail(self):
        errs = plan_rules.validate_plan([
            task("a", order=200, urgency="high"),
            task("b", order=100, urgency="high", depends_on=["a"]),
        ], [])
        self.assertTrue(any("strictly greater" in e for e in errs))
        errs = plan_rules.validate_plan([
            task("a", order=100, urgency="normal"),
            task("b", order=200, urgency="high", depends_on=["a"]),
        ], [])
        self.assertTrue(any("urgency" in e for e in errs))

    def test_task_cannot_depend_on_epic(self):
        active = [epic("ep-1", order=10), task("t-1", order=100, epic="ep-1", depends_on=["ep-1"])]
        errs = plan_rules.validate_plan(active, [])
        self.assertTrue(any("cannot depend_on epic" in e for e in errs))

    def test_scope_downstream_gating_and_blocked_honesty(self):
        active = [
            scope("sc-1", order=100, urgency="normal"),
            task("t-1", order=200, urgency="normal", status="active", depends_on=["sc-1"]),
        ]
        errs = plan_rules.validate_plan(active, [])
        self.assertTrue(any("must be 'blocked'" in e for e in errs))
        active[1]["status"] = "blocked"
        self.assertEqual([], plan_rules.validate_plan(active, []))
        errs = plan_rules.validate_plan([task("b-1", status="blocked", depends_on=["done-1"])], [finished("done-1")])
        self.assertTrue(any("no unfinished dependencies" in e for e in errs))

    def test_epic_and_scope_limits(self):
        errs = plan_rules.validate_plan([epic("ep-zombie", order=10)], [])
        self.assertTrue(any("no active children" in e for e in errs))
        self.assertEqual([], plan_rules.validate_plan([epic("ep-res", order=10, tags=["research"])], []))
        errs = plan_rules.validate_plan([epic("ep-rb", order=10, tags=["research"], rebuild=True)], [])
        self.assertTrue(any("rebuild = false" in e for e in errs))
        errs = plan_rules.validate_plan([scope("sc-rb", order=10, emulator_debug=True)], [])
        self.assertTrue(any("emulator_debug = false" in e for e in errs))

    def test_task_readiness_limits(self):
        errs = plan_rules.validate_plan([task("t-imp", description=(
            "Update mobix-core/src/main/java/Foo.kt. Decide whether to persist markers or keep them transient. "
            "Verify with FooTest."
        ))], [])
        self.assertTrue(any("open design/exploration imperative" in e for e in errs))
        # Non-imperative sentence phrasing is accepted:
        self.assertEqual([], plan_rules.validate_plan([task("t-ok", description=(
            "The audit recorded what mobix-core/src/main/java/Foo.kt does; PinRefs decide connectivity. "
            "Verify that FooTest passes."
        ))], []))
        errs = plan_rules.validate_plan([task("t-no-anchor", description=(
            "Implement the bounded evaluator with zero globals and explicit error cases for every bad input; "
            "verify everything with unit tests."
        ))], [])
        self.assertTrue(any("file/path/module anchor" in e for e in errs))
        errs = plan_rules.validate_plan([task("t-no-verify", description=(
            "Update mobix-core/src/main/java/Foo.kt to store fields cleanly in the document model without changing older files."
        ))], [])
        self.assertTrue(any("explicit verification" in e for e in errs))
        errs = plan_rules.validate_plan([task("t-short", description="Fix Foo.kt; test.")], [])
        self.assertTrue(any("description length" in e for e in errs))
        errs = plan_rules.validate_plan([task("t-title", title="x" * 125)], [])
        self.assertTrue(any("title length" in e for e in errs))

    def test_standalone_docs_and_android_rebuild_flags(self):
        errs = plan_rules.validate_plan([
            task("t-code", order=100, category="feature"),
            task("t-doc", order=200, category="docs", depends_on=["t-code"], description=(
                "Document the schema in docs/mobix-format.md and verify with DocsGuardTest and DocClaimsTest."
            )),
        ], [])
        self.assertTrue(any("standalone docs task" in e for e in errs))
        errs = plan_rules.validate_plan([task("t-and", rebuild=False, description=(
            "Wire the button on both shells in app/src/main/java/Foo.kt and verify with FooTest."
        ))], [])
        self.assertTrue(any("rebuild = false" in e for e in errs))

    def test_encoding_and_host_neutrality(self):
        for bad in (
            "Show Polar r(?) in app/src/main/java/Foo.kt and verify with FooTest.",
            "Compute R\ufffd in mobix-core/src/main/java/Foo.kt and verify with FooTest.",
            "Install on Pixel_Tablet AVD using docs/tools-emulator.md and verify screenshots.",
            "Install the APK and follow docs/tools.md's host fallback; check screenshots with logcat clean.",
            "Update mobix-core/Foo.kt and verify with python3 scripts/todo/todo.py validate.",
        ):
            with self.subTest(bad=bad):
                self.assertTrue(plan_rules.validate_plan([task(rebuild=True, emulator_debug=True, description=bad)], []))

    def test_repo_paths_and_gradle_test_modules(self):
        with tempfile.TemporaryDirectory() as tmp:
            os.makedirs(os.path.join(tmp, "docs"), exist_ok=True)
            os.makedirs(os.path.join(tmp, "scripts"), exist_ok=True)
            os.makedirs(os.path.join(tmp, "app", "src", "test", "java"), exist_ok=True)
            with open(os.path.join(tmp, "docs", "mobix-format.md"), "w", encoding="utf-8") as f:
                f.write("# Format\n")
            with open(os.path.join(tmp, "scripts", "todo.sh"), "w", encoding="utf-8") as f:
                f.write("#!/bin/sh\n")
            with open(os.path.join(tmp, "app", "src", "test", "java", "XoppSynthesisTest.kt"), "w", encoding="utf-8") as f:
                f.write("class XoppSynthesisTest\n")

            bad_doc = task(description="Update docs/mobix-spec-v1 and run scripts/todo.sh; verify with XoppSynthesisTest.")
            self.assertTrue(any("docs/mobix-spec-v1" in e for e in plan_rules.validate_plan([bad_doc], [], root=tmp)))

            wrong_mod = task(description=(
                "Verify export in docs/mobix-format.md via ./gradlew :mobix-core:test --tests '*XoppSynthesisTest*'."
            ))
            errs = plan_rules.validate_plan([wrong_mod], [], root=tmp)
            self.assertTrue(any("found in app" in e for e in errs))

            good = task(rebuild=True, description=(
                "Verify export in docs/mobix-format.md and new NewExportTest via "
                "./gradlew :app:testDebugUnitTest --tests '*XoppSynthesisTest*' and scripts/todo.sh."
            ))
            self.assertEqual([], plan_rules.validate_plan([good], [], root=tmp))

    def test_list_ready_and_plan_commands(self):
        with tempfile.TemporaryDirectory() as tmp:
            active = [
                epic("ep-1", order=10),
                task("t-ready", order=100, epic="ep-1", depends_on=["done-1"]),
                task("t-wait", order=110, epic="ep-1", depends_on=["t-ready"]),
                scope("sc-1", order=120, epic="ep-1"),
                task("t-blocked", order=130, status="blocked", epic="ep-1", depends_on=["sc-1"]),
            ]
            store.save_todo({"meta": {"title": "t"}, "tasks": active}, os.path.join(tmp, "TODO.toml"))
            store.save_finished({"meta": {"title": "f"}, "tasks": [finished("done-1")]}, os.path.join(tmp, "FINISHED.toml"))

            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                todo.cmd_list(
                    argparse.Namespace(finished=False, unreleased=False, ready=True, epic=None,
                                       status=None, category=None, level=None, json=False),
                    root=tmp,
                )
            out = buf.getvalue()
            self.assertIn("[t-ready]", out)
            self.assertIn("[sc-1]", out)
            self.assertNotIn("[t-wait]", out)
            self.assertNotIn("[t-blocked]", out)
            self.assertNotIn("[ep-1]", out)

            pbuf = io.StringIO()
            with contextlib.redirect_stdout(pbuf):
                todo.cmd_plan(argparse.Namespace(epic=None, json=False), root=tmp)
            pout = pbuf.getvalue()
            self.assertIn("Plan: 3 tasks, 1 scopes, 1 epics | ready=2 waiting=1 blocked=1", pout)
            self.assertIn("[WAIT: t-ready]", pout)
            self.assertIn("[BLOCKED: sc-1]", pout)


if __name__ == "__main__":
    unittest.main()

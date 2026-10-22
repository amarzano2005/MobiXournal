#!/usr/bin/env python3
"""Regression checks for ``done`` (the archive half of the task files).

There is no test runner for the scripts directory, so this is a plain,
dependency-free script like ``test_store.py``: run it and it prints one line per
check and exits non-zero on any failure.

    python3 scripts/todo/test_done.py

It guards one promise: **`FINISHED.toml` records what a task delivered, not the
plan it was picked up from.** The archive is what a later reader (and the
changelog's `release` step) treats as history, so a finished entry that still
says "will move the save point" is a stale plan wearing a completion date. The
summary is therefore required, and a missing one must leave both files exactly
as they were rather than archiving the plan by omission.
"""

from __future__ import annotations

import argparse
import datetime
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import store  # noqa: E402  (path is set above, module lives beside this file)
import todo  # noqa: E402

_FAILURES = []

PLAN = ("Will give each tab a save point in its history, add a dot to the strip and ask "
        "before closing; the snapshot will live beside the settings file.")


def check(condition, label):
    status = "ok  " if condition else "FAIL"
    print(f"{status} {label}")
    if not condition:
        _FAILURES.append(label)


def scratch(tmp, active=(), finished=()):
    """Write a TODO.toml / FINISHED.toml pair into *tmp* and hand back the paths."""
    todo_path = os.path.join(tmp, "TODO.toml")
    fin_path = os.path.join(tmp, "FINISHED.toml")
    store.save_todo({"meta": {"title": "test"}, "tasks": list(active)}, todo_path)
    store.save_finished({"meta": {"title": "test"}, "tasks": list(finished)}, fin_path)
    return todo_path, fin_path


def active(task_id="t", **extra):
    task = {
        "id": task_id,
        "title": "Ask before closing unsaved work",
        "description": PLAN,
        "status": "active",
        "level": "task",
        "category": "feature",
        "urgency": "normal",
        "order": 1200,
        "created": datetime.date(2026, 10, 20),
        "tags": ["desktop"],
        "rebuild": False,
        "emulator_debug": False,
    }
    task.update(extra)
    return task


def args(task_id="t", shipped=None, shipped_file=None):
    return argparse.Namespace(id=task_id, shipped=shipped, shipped_file=shipped_file,
                              date=None)


def shipped_read(tmp):
    return store.load(os.path.join(tmp, "FINISHED.toml"))["tasks"]


def todo_read(tmp):
    return store.load(os.path.join(tmp, "TODO.toml"))["tasks"]


def main():
    summary = ("Desktop: dirty state, close confirmation and crash safety. The save point is the "
               "history's state id, compared on open and after every save; the strip dots a dirty "
               "tab; closing a tab or the window asks Save / Discard / Cancel, the X included; a "
               "dirty tab keeps a .mobix snapshot that the next start adopts. 7 lifecycle tests.")

    # The everyday case: the archive holds the delivery, with the plan gone.
    with tempfile.TemporaryDirectory() as tmp:
        scratch(tmp, active=[active()])
        todo.cmd_done(args(shipped=summary), root=tmp)
        archived = shipped_read(tmp)
        check(len(archived) == 1, "the task reaches the archive")
        entry = archived[0]
        check(entry["description"] == summary, "the archived description is the shipped summary")
        check(PLAN not in entry["description"], "and the plan is not in the archive")
        check(entry["status"] == "finished", "the entry is stamped finished")
        check(entry["completed"] == datetime.date.today(), "and dated today")
        check(todo_read(tmp) == [], "the task left TODO.toml")
        check(not any(key in entry for key in ("order", "urgency", "rebuild", "emulator_debug")),
              "the active-only fields are dropped")

    # Newest first: the archive's documented order survives the insert.
    with tempfile.TemporaryDirectory() as tmp:
        older = dict(active("older"), status="finished", description="An older delivery.")
        scratch(tmp, active=[active("new")], finished=[older])
        todo.cmd_done(args("new", shipped="A newer delivery."), root=tmp)
        check([t["id"] for t in shipped_read(tmp)] == ["new", "older"],
              "the newest delivery is first")

    # No summary: refuse, and leave both files untouched (the plan is not archived by omission).
    with tempfile.TemporaryDirectory() as tmp:
        todo_path, fin_path = scratch(tmp, active=[active()])
        before = (open(todo_path, encoding="utf-8").read(), open(fin_path, encoding="utf-8").read())
        refused = False
        try:
            todo.cmd_done(args(), root=tmp)
        except SystemExit as exc:
            refused = exc.code not in (0, None)
        after = (open(todo_path, encoding="utf-8").read(), open(fin_path, encoding="utf-8").read())
        check(refused, "a missing summary is refused")
        check(before == after, "and nothing is moved or rewritten")

    # A whitespace-only summary is not a summary.
    with tempfile.TemporaryDirectory() as tmp:
        scratch(tmp, active=[active()])
        refused = False
        try:
            todo.cmd_done(args(shipped="   \n\t "), root=tmp)
        except SystemExit as exc:
            refused = exc.code not in (0, None)
        check(refused, "an empty summary is refused")
        check(len(todo_read(tmp)) == 1, "and the task stays active")

    # Long summaries come from a file, and reach the archive as one line.
    with tempfile.TemporaryDirectory() as tmp:
        scratch(tmp, active=[active()])
        path = os.path.join(tmp, "summary.txt")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("First paragraph of the delivery.\n\n  Second paragraph, indented.\n")
        todo.cmd_done(args(shipped_file=path), root=tmp)
        entry = shipped_read(tmp)[0]
        check(entry["description"] == "First paragraph of the delivery. Second paragraph, indented.",
              "--shipped-file is read and folded to one line")

    # Quotes and backslashes survive the TOML write: the archive keeps the words as given.
    with tempfile.TemporaryDirectory() as tmp:
        scratch(tmp, active=[active()])
        tricky = 'The panel asks "Save \\ Discard \\ Cancel" and the X cannot bypass it.'
        todo.cmd_done(args(shipped=tricky), root=tmp)
        check(shipped_read(tmp)[0]["description"] == tricky,
              "quotes and backslashes round-trip through the archive")

    # Two sources for one field is a mistake, not a precedence question.
    with tempfile.TemporaryDirectory() as tmp:
        scratch(tmp, active=[active()])
        path = os.path.join(tmp, "summary.txt")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("From the file.")
        refused = False
        try:
            todo.cmd_done(args(shipped="From the flag.", shipped_file=path), root=tmp)
        except SystemExit as exc:
            refused = exc.code not in (0, None)
        check(refused, "passing both summary sources is refused")

    print()
    if _FAILURES:
        print(f"{len(_FAILURES)} check(s) failed")
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())

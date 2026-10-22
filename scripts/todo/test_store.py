#!/usr/bin/env python3
"""Regression checks for the task store (``store.py``).

There is no test runner for the scripts directory, so this is a plain,
dependency-free script like ``test_release.py``: run it and it prints one line
per check and exits non-zero on any failure.

    python3 scripts/todo/test_store.py

It guards the one layout promise ``TODO.toml`` makes — that the file reads in
`order` sequence — because `add` appends and `edit --order` moves a single entry,
which without the sort left a plan that no longer matched the file it lives in.
"""

from __future__ import annotations

import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import store  # noqa: E402  (path is set above, module lives beside this file)

_FAILURES = []


def check(condition, label):
    status = "ok  " if condition else "FAIL"
    print(f"{status} {label}")
    if not condition:
        _FAILURES.append(label)


def task(task_id, order=None, title=None):
    out = {"id": task_id, "title": title or task_id, "category": "chore",
           "status": "active", "level": "task"}
    if order is not None:
        out["order"] = order
    return out


def round_trip(tasks):
    """Save *tasks* to a temp file and read the ids back in file order."""
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "TODO.toml")
        store.save_todo({"meta": {"title": "t"}, "tasks": list(tasks)}, path)
        return [t["id"] for t in store.load(path)["tasks"]]


def main():
    # Appending out of order is the everyday case: `add` puts the new task last.
    check(
        round_trip([task("late", 900), task("early", 100), task("middle", 500)])
        == ["early", "middle", "late"],
        "writing sorts the tasks by order",
    )

    # `edit <id> --order N` rewrites one order; the entry moves with it.
    check(
        round_trip([task("a", 10), task("b", 20), task("c", 30), task("moved", 25)])
        == ["a", "b", "moved", "c"],
        "an edited order moves its entry into place",
    )

    # Two tasks on the same order keep a stable, readable sequence.
    check(
        round_trip([task("zebra", 100), task("alpha", 100), task("beta", 100)])
        == ["alpha", "beta", "zebra"],
        "a tie is broken by id, so the order is stable",
    )

    # A task with no order at all sorts last instead of crashing the write.
    check(
        round_trip([task("unordered"), task("ordered", 700)])
        == ["ordered", "unordered"],
        "a missing order sorts after every recorded one",
    )

    # The sort is in place, so a caller that keeps using the document sees the
    # same sequence the file now holds (release stamps read it back).
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "TODO.toml")
        doc = {"meta": {}, "tasks": [task("b", 200), task("a", 100)]}
        store.save_todo(doc, path)
        check([t["id"] for t in doc["tasks"]] == ["a", "b"],
              "the caller's document is left in file order")

    # Every other field survives a save; only the sequence changes.
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "TODO.toml")
        store.save_todo({"meta": {}, "tasks": [
            {"id": "keep", "title": "Keep me", "description": "a long description",
             "status": "active", "level": "scope", "category": "refactor",
             "urgency": "high", "order": 5, "created": "2026-10-22", "tags": ["desktop"],
             "rebuild": False, "emulator_debug": False},
        ]}, path)
        back = store.load(path)["tasks"][0]
        check(back.get("urgency") == "high" and back.get("level") == "scope"
              and back.get("tags") == ["desktop"] and back.get("rebuild") is False
              and back.get("created") == "2026-10-22",
              "sorting changes no field but the sequence")

    print()
    if _FAILURES:
        print(f"{len(_FAILURES)} check(s) failed")
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())

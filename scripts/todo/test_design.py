#!/usr/bin/env python3
"""Regression checks for the design declaration (`todo.py done --design`).

There is no test runner for the scripts directory, so this is a plain, dependency-free script like
``test_done.py``: run it and it prints one line per check, exiting non-zero on any failure.

    python3 scripts/todo/test_design.py

It guards one promise: **a change is closed only by declaring what it verified against the interface
contract.** The contract's checklist used to be a paragraph nobody had to answer, which is why a change
that never opened it closed exactly like one that had. The declaration makes the question unskippable,
so the checks here are about the gate's edges: an answer-less axis, a single axis, an unknown word, a
declaration that names no surface and gives no reason — each must be refused, and a refusal must leave
the two task files exactly as they were.
"""

from __future__ import annotations

import argparse
import datetime
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import design  # noqa: E402  (path is set above, module lives beside this file)
import store  # noqa: E402
import todo  # noqa: E402

_FAILURES = []

# Two answers, in the contract's words: the smallest declaration that is a check rather than a note.
GOOD = ("material: capsule in the ribbon's lane; "
        "states: hover and focus added, the other three unchanged")

CITED = "Interaction/Focus States"

CORPUS = ("No,Category,Issue,Platform,Description,Do,Don't,"
          "Code Example Good,Code Example Bad,Severity\n"
          "1,Interaction,Focus States,All,A control needs a visible ring,"
          "Draw the ring,Skip it,ring-2,none,High\n")


def check(condition, label):
    status = "ok  " if condition else "FAIL"
    print(f"{status} {label}")
    if not condition:
        _FAILURES.append(label)


def corpus_root(tmp):
    """A checkout that has the skill vendored, with one rule in it."""
    path = os.path.join(tmp, design.CORPUS)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(CORPUS)
    return tmp


def bare_root(tmp):
    """A checkout without the skill: a citation cannot be checked there, so it is not judged."""
    return tmp


def scratch(tmp, active=(), finished=()):
    todo_path = os.path.join(tmp, "TODO.toml")
    fin_path = os.path.join(tmp, "FINISHED.toml")
    store.save_todo({"meta": {"title": "test"}, "tasks": list(active)}, todo_path)
    store.save_finished({"meta": {"title": "test"}, "tasks": list(finished)}, fin_path)
    return todo_path, fin_path


def active(task_id="t", **extra):
    task = {
        "id": task_id,
        "title": "Give the ribbon's new slot its material",
        "description": "Add a capsule slot for the audio tool to the ribbon.",
        "status": "active",
        "level": "task",
        "category": "feature",
        "urgency": "normal",
        "order": 1200,
        "created": datetime.date(2026, 11, 4),
        "tags": ["ui"],
        "rebuild": False,
        "emulator_debug": False,
    }
    task.update(extra)
    return task


def args(task_id="t", shipped="What shipped.", design_text=GOOD, design_file=None):
    return argparse.Namespace(id=task_id, shipped=shipped, shipped_file=None,
                              design=design_text, design_file=design_file, date=None)


def refused(callable_, *a, **kw):
    try:
        callable_(*a, **kw)
    except SystemExit as exc:
        return exc.code not in (0, None)
    return False


def read_pair(tmp):
    return (open(os.path.join(tmp, "TODO.toml"), encoding="utf-8").read(),
            open(os.path.join(tmp, "FINISHED.toml"), encoding="utf-8").read())


def main():
    # --- the format --------------------------------------------------------

    bad = design.problems("", root=None)
    check(bad and "empty" in bad[0], "an empty declaration is refused")

    check(design.problems("   \n\t ", root=None), "a whitespace-only declaration is refused")

    check(design.problems(GOOD, root=None) == [], "two answered axes pass")

    one = design.problems("material: capsule in the lane", root=None)
    check(any("at least 2 axes" in p for p in one), "one answered axis is refused as too few")

    unknown = design.problems("material: capsule; colourr: untouched", root=None)
    check(any("unknown axis" in p for p in unknown), "an unknown axis is refused")
    check(any("tokens" in p for p in unknown), "and its message carries the vocabulary")

    empty_note = design.problems("material: capsule; states:", root=None)
    check(any("has no answer" in p for p in empty_note), "an axis with no answer is refused")

    no_colon = design.problems("material capsule; states hover", root=None)
    check(any("needs the form" in p for p in no_colon), "a clause without `axis:` is refused")

    folded = design.problems("material:   a capsule in the lane  ;  states:  hover", root=None)
    check(folded == [], "loose spacing around the separators is accepted")
    check(design.fold("  a  b\n c ") == "a b c", "the fold is single spacing, single line")
    check(design.normalise("material: capsule\n\nstates: hover") == "material: capsule; states: hover",
          "a line break separates two clauses instead of welding them")

    # --- the escape for work with no interface surface ----------------------

    check(design.problems("none: documentation only", root=None) == [],
          "`none:` with a reason passes")

    short = design.problems("none: docs only", root=None)
    check(any("needs the reason" in p for p in short), "`none:` without a real reason is refused")

    mixed = design.problems("none: documentation only; material: unchanged", root=None)
    check(any("whole declaration" in p for p in mixed), "`none:` cannot sit beside answered axes")

    # --- citations of the corpus -------------------------------------------

    with tempfile.TemporaryDirectory() as tmp:
        root = corpus_root(tmp)
        check(design.problems(GOOD + f"; rule: {CITED}", root=root) == [],
              "a cited rule that exists passes")
        missing = design.problems(GOOD + "; rule: Interaction/Contrast Ratios", root=root)
        check(any("Interaction/Contrast Ratios" in p for p in missing),
              "a cited rule that does not exist is refused by name")
        check(design.problems(GOOD + "; rule: focus states", root=root) != [],
              "a citation is matched on its `Category/Issue` id, not on a guess")

    with tempfile.TemporaryDirectory() as tmp:
        root = bare_root(tmp)
        check(design.problems(GOOD + f"; rule: {CITED}", root=root) == [],
              "without the skill vendored a citation is skipped, not failed")

    with tempfile.TemporaryDirectory() as tmp:
        root = corpus_root(tmp)
        empty_citation = design.problems(GOOD + "; rule: see the checklist", root=root)
        check(any("<Category>/<Issue>" in p for p in empty_citation),
              "`rule:` with no id is refused")

    # --- closing a task ----------------------------------------------------

    with tempfile.TemporaryDirectory() as tmp:
        scratch(tmp)
        before = read_pair(tmp)
        check(refused(todo.cmd_done, args(design_text=None), root=tmp),
              "`done` without a declaration is refused")
        check(read_pair(tmp) == before, "and nothing is moved or rewritten")

    with tempfile.TemporaryDirectory() as tmp:
        scratch(tmp, active=[active()])
        check(refused(todo.cmd_done, args(design_text="material: capsule"), root=tmp),
              "`done` with a bad declaration is refused")
        check(store.load(os.path.join(tmp, "TODO.toml"))["tasks"] != [],
              "and the task stays active")

    with tempfile.TemporaryDirectory() as tmp:
        scratch(tmp, active=[active()])
        todo.cmd_done(args(), root=tmp)
        entry = store.load(os.path.join(tmp, "FINISHED.toml"))["tasks"][0]
        check(entry.get("design") == GOOD, "the declaration is archived beside what shipped")
        check(entry["description"] == "What shipped.", "and the shipped summary is untouched")

    with tempfile.TemporaryDirectory() as tmp:
        scratch(tmp, active=[active()])
        todo.cmd_done(args(design_text="none: documentation only"), root=tmp)
        entry = store.load(os.path.join(tmp, "FINISHED.toml"))["tasks"][0]
        check(entry.get("design") == "none: documentation only",
              "a `none:` declaration is archived with its reason")

    with tempfile.TemporaryDirectory() as tmp:
        scratch(tmp, active=[active()])
        path = os.path.join(tmp, "design.txt")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("material:  capsule\n\n  states: hover\n")
        todo.cmd_done(args(design_text=None, design_file=path), root=tmp)
        entry = store.load(os.path.join(tmp, "FINISHED.toml"))["tasks"][0]
        check(entry.get("design") == "material: capsule; states: hover",
              "--design-file is read one clause per line and folded to one line")

    with tempfile.TemporaryDirectory() as tmp:
        scratch(tmp, active=[active()])
        path = os.path.join(tmp, "design.txt")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(GOOD)
        check(refused(todo.cmd_done, args(design_file=path), root=tmp),
              "passing both declaration sources is refused")

    with tempfile.TemporaryDirectory() as tmp:
        scratch(tmp, active=[active()])
        tricky = 'material: capsule; states: hover - the "picked" slot; tests: DesktopShapeFeedbackTest'
        todo.cmd_done(args(design_text=tricky), root=tmp)
        entry = store.load(os.path.join(tmp, "FINISHED.toml"))["tasks"][0]
        check(entry.get("design") == tricky, "quotes round-trip through the archive")

    # --- a hand-edited archive is caught -----------------------------------

    with tempfile.TemporaryDirectory() as tmp:
        good = dict(active("good"), status="finished", description="Shipped.",
                    completed=datetime.date(2026, 11, 4), design=GOOD)
        bad_entry = dict(active("bad"), status="finished", description="Shipped.",
                         completed=datetime.date(2026, 11, 4), design="colourr: unchanged")
        scratch(tmp, finished=[good, bad_entry])
        check(refused(todo.cmd_validate, argparse.Namespace(), root=tmp),
              "`validate` fails on an archived declaration that names no axis")

    with tempfile.TemporaryDirectory() as tmp:
        good = dict(active("good"), status="finished", description="Shipped.",
                    completed=datetime.date(2026, 11, 4), design=GOOD)
        legacy = dict(active("legacy"), status="finished", description="Shipped before the gate.",
                      completed=datetime.date(2026, 10, 1))
        scratch(tmp, finished=[good, legacy])
        check(not refused(todo.cmd_validate, argparse.Namespace(), root=tmp),
              "an entry archived before the gate is not retroactively refused")

    print()
    if _FAILURES:
        print(f"{len(_FAILURES)} check(s) failed")
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())

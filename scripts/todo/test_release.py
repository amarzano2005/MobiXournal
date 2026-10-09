#!/usr/bin/env python3
"""Regression checks for the release tooling (``release.py``).

There is no test runner for the scripts directory, so this is a plain,
dependency-free script: run it and it prints one line per check and exits
non-zero on any failure.

    python3 scripts/todo/test_release.py

It covers what the release automation must not get wrong — that the *pending*
set is exactly the unstamped tasks (the whole point of the version separation),
that the changelog renders grouped and readable, that a curated changelog is
never clobbered, and that the Gradle bump keeps the file's line endings.
"""

from __future__ import annotations

import datetime
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import release  # noqa: E402  (path is set above, module lives beside this file

_FAILURES = []


def check(condition, label):
    status = "ok  " if condition else "FAIL"
    print(f"{status} {label}")
    if not condition:
        _FAILURES.append(label)


def task(task_id, category, title, description="", completed="2026-10-05",
         release_version=None):
    out = {
        "id": task_id,
        "category": category,
        "title": title,
        "description": description,
        "completed": datetime.date.fromisoformat(completed),
    }
    if release_version:
        out["release"] = release_version
    return out


# --- versions -------------------------------------------------------------

def test_versions():
    check(release.is_version("1.2.0"), "is_version accepts X.Y.Z")
    check(not release.is_version("1.2"), "is_version rejects X.Y")
    check(not release.is_version("v1.2.0"), "is_version rejects a leading v")
    check(not release.is_version(None), "is_version rejects None")
    check(release.version_key("1.10.0") > release.version_key("1.9.0"),
          "version_key orders numerically, not lexically")


def test_latest_version():
    doc = {"meta": {}, "tasks": [
        task("a", "feature", "A", release_version="1.0.0"),
        task("b", "feature", "B", release_version="1.1.0"),
        task("c", "feature", "C"),
    ]}
    check(release.latest_version(doc) == "1.1.0",
          "latest_version reads the newest task stamp")
    doc["meta"]["latest_release"] = "1.2.0"
    check(release.latest_version(doc) == "1.2.0",
          "latest_version also reads the meta stamp")
    check(release.latest_version({"meta": {}, "tasks": []}) is None,
          "latest_version is None on an unreleased archive")


# --- the pending set is the version separation ----------------------------

def test_pending_tasks():
    tasks = [
        task("shipped", "feature", "Shipped", completed="2026-10-05",
             release_version="1.1.0"),
        task("old", "feature", "Old", completed="2026-10-05"),
        task("new", "bug", "New", completed="2026-10-09"),
    ]
    pending = release.pending_tasks(tasks)
    check([t["id"] for t in pending] == ["old", "new"],
          "pending_tasks keeps only the unstamped tasks, in archive order")
    through = release.pending_tasks(tasks, datetime.date(2026, 10, 7))
    check([t["id"] for t in through] == ["old"],
          "pending_tasks --through narrows to tasks completed by a date")
    check(release.pending_tasks([t for t in tasks if t["id"] == "shipped"]) == [],
          "pending_tasks is empty once everything is stamped")


# --- changelog rendering --------------------------------------------------

def test_summary():
    check(
        release.summary("2026-10-09", [
            task("a", "feature", "A"), task("b", "feature", "B"),
            task("c", "bug", "C"), task("d", "refactor", "D"),
        ]) == "Released 2026-10-09 · 4 changes: 2 features, 1 fix, 1 refactor.",
        "summary counts per category with singular nouns where they belong",
    )
    check(release.summary("2026-10-09", [task("a", "bug", "A")])
          == "Released 2026-10-09 · 1 change: 1 fix.",
          "summary says '1 change', not '1 changes'")
    check(release.summary("2026-10-09", []) == "Released 2026-10-09 · 0 changes: no changes.",
          "summary is empty-safe")


def test_bullet():
    short = task("a", "feature", "Add a thing", "It does the thing. And more.")
    check(release.bullet(short) == "**Add a thing** — It does the thing.",
          "bullet is title plus the description's first sentence, one final period")
    long_desc = "Start " + ("word " * 80) + "ends."
    clipped = release.bullet(task("b", "feature", "Long", long_desc))
    check(clipped.endswith("…") and "…." not in clipped,
          "a long gist is clipped with a single ellipsis, never '….'")
    no_desc = release.bullet(task("c", "feature", "Bare"))
    check(no_desc == "**Bare**.", "a task with no description is just its title")


def test_render_changelog():
    tasks = [
        task("f", "feature", "Feature one", "Gist one."),
        task("b", "bug", "Fix one", "Gist two."),
        task("r", "refactor", "Refactor one", "Gist three."),
    ]
    text = release.render_changelog("1.2.0", "2026-10-09", tasks, previous="1.1.0")
    check(text.startswith("# MobiXournal v1.2.0\n"), "changelog leads with the version heading")
    check(text.index("## ✨ Features") < text.index("## 🐛 Fixes")
          < text.index("## ♻️ Refactoring"),
          "changelog sections follow the category order")
    check("- **Fix one** — Gist two." in text, "every task becomes a bullet")
    check("compare/v1.1.0...v1.2.0" in text, "the compare link uses the previous version")
    check("docs/tools.md#cutting-a-release" in text, "the draft points at the release procedure")
    check("1.2.0" in text, "the draft names the version it belongs to")


# --- filesystem edges -----------------------------------------------------

def test_write_changelog_refuses_clobber():
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "v1.2.0.md")
        release.write_changelog(path, "first")
        with open(path, encoding="utf-8") as fh:
            check(fh.read() == "first", "write_changelog writes the text as UTF-8")
        raised = False
        try:
            release.write_changelog(path, "second")
        except SystemExit:
            raised = True
        check(raised, "write_changelog refuses to clobber a curated changelog")
        release.write_changelog(path, "second", force=True)
        with open(path, encoding="utf-8") as fh:
            check(fh.read() == "second", "write_changelog --force overwrites")


def test_write_changelog_keeps_unicode():
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "v1.2.0.md")
        release.write_changelog(path, "## ✨ Features\n")
        with open(path, "rb") as fh:
            raw = fh.read()
        check(raw.decode("utf-8") == "## ✨ Features\n",
              "changelog is written UTF-8, emoji and all")


GRADLE_SAMPLE = (
    "android {\r\n"
    "    defaultConfig {\r\n"
    '        versionCode = 3\r\n'
    '        versionName = "1.1.0"\r\n'
    "    }\r\n"
    "}\r\n"
)


def test_bump_gradle():
    with tempfile.TemporaryDirectory() as tmp:
        os.makedirs(os.path.join(tmp, "app"))
        path = os.path.join(tmp, "app", "build.gradle.kts")
        with open(path, "w", encoding="utf-8", newline="") as fh:
            fh.write(GRADLE_SAMPLE)
        old_name, old_code, new_code = release.bump_gradle(tmp, "1.2.0")
        check((old_name, old_code, new_code) == ("1.1.0", 3, 4),
              "bump_gradle reports the old name and the incremented counter")
        with open(path, encoding="utf-8", newline="") as fh:
            text = fh.read()
        check('versionName = "1.2.0"' in text, "bump_gradle sets versionName")
        check("versionCode = 4" in text, "bump_gradle increments versionCode")
        check(text.count("\r\n") == text.count("\n"),
              "bump_gradle leaves the file's CRLF line endings intact")


def main():
    test_versions()
    test_latest_version()
    test_pending_tasks()
    test_summary()
    test_bullet()
    test_render_changelog()
    test_write_changelog_refuses_clobber()
    test_write_changelog_keeps_unicode()
    test_bump_gradle()
    if _FAILURES:
        print(f"\n{len(_FAILURES)} check(s) failed")
        return 1
    print("\nall release-tooling checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())

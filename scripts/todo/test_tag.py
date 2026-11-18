#!/usr/bin/env python3
"""Regression checks for ``tag`` — the command that makes a release tag, or refuses.

    python3 scripts/todo/test_tag.py

A tag is the one step that publishes, and 2.3.0 was tagged by hand from a tree whose freeze was not
committed: the tag captured 1.2.0's window, its suite failed and the MSI never built. `tag` now runs the
same checks CI runs and only then creates the tag, so this file drives it against real git repositories
in a temporary directory: a frozen tree is tagged, and an uncommitted tree, a side branch, an existing
tag and a version that was never frozen are all refused with the sentence a reader needs.
"""

from __future__ import annotations

import argparse
import contextlib
import datetime
import io
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import store  # noqa: E402  (path is set above, module lives beside this file)
import todo  # noqa: E402

_FAILURES = []

VERSION = "9.9.9"
DESKTOP = "1.3.0"


def check(condition, label):
    status = "ok  " if condition else "FAIL"
    print(f"{status} {label}")
    if not condition:
        _FAILURES.append(label)


def git(root, *args, capture=False):
    """git in *root*, with an identity of its own so the caller's config cannot change the outcome."""
    done = subprocess.run(
        ["git", "-c", "user.email=test@example.com", "-c", "user.name=Test", *args],
        cwd=root, capture_output=True, text=True, timeout=60,
    )
    if done.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {done.stderr.strip()}")
    return done.stdout if capture else ""


def write(root, relative, text):
    path = os.path.join(root, *relative.split("/"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)


def finished_task():
    return {
        "id": "frozen-task",
        "title": "Ship the thing",
        "description": "The thing ships.",
        "status": "finished",
        "level": "task",
        "category": "feature",
        "completed": datetime.date(2026, 11, 18),
        "release": VERSION,
        "shipped": "The thing shipped, with its tests and its docs.",
        "design": "none: nothing on the interface moved",
    }


def repo(tmp):
    """A committed checkout where VERSION was frozen: both Gradle lines, the archive and the note."""
    git(tmp, "init", "-q")
    # The command refuses a tag made off main, so the fixture's default branch has to be `main` whatever
    # `init.defaultBranch` says on this machine.
    git(tmp, "symbolic-ref", "HEAD", "refs/heads/main")
    write(tmp, "app/build.gradle.kts", f'versionName = "{VERSION}"\nversionCode = 17\n')
    write(tmp, "desktop/build.gradle.kts", f'packageVersion = "{DESKTOP}"\n')
    write(
        tmp,
        "mobix-core/src/main/java/com/mobixournal/design/BuildVersions.kt",
        f'package com.mobixournal.design\n\nobject BuildVersions {{\n    const val DESKTOP = "{DESKTOP}"\n}}\n',
    )
    write(tmp, f"docs/releases/v{VERSION}.md", f"# MobiXournal v{VERSION}\n")
    store.save_todo({"meta": {"title": "test"}, "tasks": []}, os.path.join(tmp, "TODO.toml"))
    store.save_finished(
        {"meta": {"title": "test", "latest_release": VERSION}, "tasks": [finished_task()]},
        os.path.join(tmp, "FINISHED.toml"),
    )
    git(tmp, "add", "-A")
    git(tmp, "commit", "-q", "-m", "freeze v9.9.9")


def tag(root, version=VERSION):
    """Run the command the CLI runs, from inside *root*; its exit status and everything it said."""
    out, err = io.StringIO(), io.StringIO()
    code = 0
    cwd = os.getcwd()
    try:
        os.chdir(root)
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            todo.cmd_tag(argparse.Namespace(version=version, no_push=True))
    except SystemExit as exc:
        # `sys.exit("sentence")` only prints when nothing catches it, so the sentence the reader would
        # see is the exception's own code.
        if isinstance(exc.code, int) or exc.code is None:
            code = exc.code if isinstance(exc.code, int) else 1
        else:
            code = 1
            out.write(str(exc.code) + "\n")
    finally:
        os.chdir(cwd)
    return code, out.getvalue() + err.getvalue()


def main():
    # The everyday case: a frozen, committed tree gets its tag, at HEAD, and nothing is pushed.
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        repo(tmp)
        code, said = tag(tmp)
        check(code == 0, "a frozen tree is tagged")
        check(git(tmp, "tag", "--list", capture=True).split() == ["v" + VERSION],
              "the tag carries the version")
        target = git(tmp, "rev-list", "-n1", "v" + VERSION, capture=True).strip()
        head = git(tmp, "rev-parse", "HEAD", capture=True).strip()
        check(target == head, "the tag points at HEAD, not at an older commit")
        check("push it with: git push origin v" + VERSION in said,
              "--no-push says how to publish it")

    # A tag that already exists is never moved by this command.
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        repo(tmp)
        tag(tmp)
        code, said = tag(tmp)
        check(code != 0 and "already exists" in said, "an existing tag is refused, not moved")

    # The mistake 2.3.0 made: the freeze is on disk but not in the commit the tag would name.
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        repo(tmp)
        with open(os.path.join(tmp, "desktop", "build.gradle.kts"), "a", encoding="utf-8") as fh:
            fh.write("\n")
        code, said = tag(tmp)
        check(code != 0 and "uncommitted changes" in said,
              "an uncommitted tree is refused, with the files named")
        check(git(tmp, "tag", "--list", capture=True).strip() == "", "no tag is left behind")

    # A version that was never frozen cannot be tagged at all.
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        repo(tmp)
        code, said = tag(tmp, version="9.9.8")
        check(code != 0 and "has versionName" in said,
              "a version the Gradle line does not carry is refused")

    # A tag publishes what main carries, so a side branch is not a release branch.
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        repo(tmp)
        git(tmp, "checkout", "-q", "-b", "side")
        code, said = tag(tmp)
        check(code != 0 and "not main" in said, "a tag off main is refused")

    if _FAILURES:
        print(f"\n{len(_FAILURES)} check(s) failed")
        return 1
    print("\nall checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())

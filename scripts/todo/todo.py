#!/usr/bin/env python3
"""Command-line front door to the TOML task list (TODO.toml / FINISHED.toml).

Run ``python3 todo.py <command> --help`` for any command. Typical use:

    todo.py list                      # active tasks, most urgent first
    todo.py stats                     # totals by status / category / urgency
    todo.py add --title "..." --description "..." --category feature
    todo.py edit <id> --urgency high
    todo.py done <id>                 # move a task into the archive
    todo.py list --finished --unreleased   # what the next version will ship
    todo.py release --version 1.2.0   # freeze a release + draft its changelog
    todo.py validate                  # lint both files

All logic lives in store.py / tomlio.py / release.py; this file is just argument
parsing and presentation.
"""

from __future__ import annotations

import argparse
import datetime
import json
import os
import re
import sys

import release
import store


# --- helpers ---------------------------------------------------------------

def _today():
    return datetime.date.today()


def _load_todo():
    path = store.todo_path()
    return path, store.load(path)


def _load_finished():
    path = store.finished_path()
    return path, store.load(path)


def _print_task(task, indent=""):
    urg = task.get("urgency", "-")
    cat = task.get("category", "-")
    status = task.get("status", "-")
    print(f"{indent}[{task.get('id','?')}] {task.get('title','')}")
    lvl = task.get("level", store.DEFAULT_LEVEL)
    meta = f"{indent}    level={lvl} status={status} category={cat} urgency={urg}"
    if "order" in task:
        meta += f" order={task['order']}"
    if "completed" in task:
        meta += f" completed={task['completed']}"
    if "release" in task:
        meta += f" release={task['release']}"
    if "rebuild" in task:
        meta += f" rebuild={'yes' if task['rebuild'] else 'no'}"
    if "emulator_debug" in task:
        meta += f" emulator-debug={'yes' if task['emulator_debug'] else 'no'}"
    print(meta)


# --- commands --------------------------------------------------------------

def cmd_list(args):
    _, doc = (_load_finished() if args.finished else _load_todo())
    tasks = doc["tasks"]
    if args.unreleased:
        if not args.finished:
            sys.exit("--unreleased filters the archive; pass --finished too")
        tasks = release.pending_tasks(tasks)
    if args.status:
        tasks = [t for t in tasks if t.get("status") == args.status]
    if args.category:
        tasks = [t for t in tasks if t.get("category") == args.category]
    if args.level:
        tasks = [t for t in tasks
                 if t.get("level", store.DEFAULT_LEVEL) == args.level]
    if not args.finished:
        tasks = store.sort_active(tasks)
    if args.json:
        print(json.dumps(tasks, default=str, indent=2))
        return
    if not tasks:
        print("(no matching tasks)")
        return
    for task in tasks:
        _print_task(task)


def cmd_show(args):
    for loader in (_load_todo, _load_finished):
        _, doc = loader()
        task = store.find(doc["tasks"], args.id)
        if task:
            if args.json:
                print(json.dumps(task, default=str, indent=2))
            else:
                _print_task(task)
                print(f"\n{task.get('description','')}")
            return
    sys.exit(f"no task with id {args.id!r}")


def cmd_stats(args):
    _, todo = _load_todo()
    _, done = _load_finished()
    active = todo["tasks"]
    payload = {
        "active_total": len(active),
        "finished_total": len(done["tasks"]),
        "active_by_status": store.counts_by(active, "status"),
        "active_by_category": store.counts_by(active, "category"),
        "active_by_urgency": store.counts_by(active, "urgency"),
        "active_by_level": store.counts_by(active, "level"),
        "finished_by_category": store.counts_by(done["tasks"], "category"),
        "finished_unreleased": len(release.pending_tasks(done["tasks"])),
        "finished_by_release": store.counts_by(
            [t for t in done["tasks"] if t.get("release")], "release"
        ),
        "latest_release": release.latest_version(done),
    }
    if args.json:
        print(json.dumps(payload, indent=2))
        return
    print(f"active tasks:   {payload['active_total']}")
    print(f"finished tasks: {payload['finished_total']}")
    for label, key in (
        ("active by status", "active_by_status"),
        ("active by category", "active_by_category"),
        ("active by urgency", "active_by_urgency"),
        ("active by level", "active_by_level"),
        ("finished by category", "finished_by_category"),
    ):
        print(f"\n{label}:")
        for name, n in payload[key].items():
            print(f"  {name:12} {n}")
    print("\nreleases:")
    print(f"  latest       {payload['latest_release'] or '(none recorded)'}")
    print(f"  unreleased   {payload['finished_unreleased']}")
    for version, n in payload["finished_by_release"].items():
        print(f"  in v{version:<9} {n}")


def cmd_count(args):
    _, doc = (_load_finished() if args.finished else _load_todo())
    tasks = doc["tasks"]
    if args.by:
        for name, n in store.counts_by(tasks, args.by).items():
            print(f"{name:14} {n}")
    else:
        print(len(tasks))


def cmd_add(args):
    path, doc = _load_todo()
    _, done = _load_finished()
    existing = [t.get("id") for t in doc["tasks"] + done["tasks"]]
    task = {
        "id": args.id or store.slugify(args.title, existing),
        "title": args.title,
        "description": args.description,
        "status": args.status,
        "level": args.level,
        "category": args.category,
        "urgency": args.urgency,
        "order": store.next_order(doc["tasks"]),
        "created": _today(),
        "tags": args.tag or [],
        "rebuild": args.rebuild,
        "emulator_debug": args.emulator_debug,
    }
    doc["tasks"].append(task)
    store.save_todo(doc, path)
    print(f"added [{task['id']}] {task['title']}")


def cmd_edit(args):
    path, doc = _load_todo()
    task = store.find(doc["tasks"], args.id)
    if not task:
        sys.exit(f"no active task with id {args.id!r}")
    for field in ("title", "description", "status", "level", "category", "urgency",
                  "order", "rebuild", "emulator_debug"):
        value = getattr(args, field)
        if value is not None:
            task[field] = value
    if args.add_tag:
        task.setdefault("tags", [])
        for tag in args.add_tag:
            if tag not in task["tags"]:
                task["tags"].append(tag)
    store.save_todo(doc, path)
    print(f"updated [{task['id']}]")


def cmd_done(args):
    todo_path, todo = _load_todo()
    fin_path, done = _load_finished()
    task = store.find(todo["tasks"], args.id)
    if not task:
        sys.exit(f"no active task with id {args.id!r}")
    todo["tasks"] = [t for t in todo["tasks"] if t.get("id") != args.id]
    task["status"] = store.STATUS_FINISHED
    task["completed"] = (
        datetime.date.fromisoformat(args.date) if args.date else _today()
    )
    for stray in ("order", "urgency", "rebuild", "emulator_debug"):
        task.pop(stray, None)
    done["tasks"].insert(0, task)  # newest first
    store.save_todo(todo, todo_path)
    store.save_finished(done, fin_path)
    print(f"finished [{task['id']}] on {task['completed']}")


def cmd_remove(args):
    path, doc = _load_todo()
    task = store.find(doc["tasks"], args.id)
    if not task:
        sys.exit(f"no active task with id {args.id!r}")
    doc["tasks"] = [t for t in doc["tasks"] if t.get("id") != args.id]
    store.save_todo(doc, path)
    why = f" ({args.reason})" if args.reason else ""
    print(f"removed [{args.id}]{why}")


def cmd_release(args):
    root = store.find_repo_root()
    fin_path, done = _load_finished()
    version = args.version.strip()
    if not release.is_version(version):
        sys.exit(f"not a X.Y.Z version: {version!r}")
    through = datetime.date.fromisoformat(args.through) if args.through else None
    pending = release.pending_tasks(done["tasks"], through)
    if not pending:
        sys.exit("nothing pending — every finished task already carries a release")
    previous = release.latest_version(done)
    if previous and release.version_key(version) <= release.version_key(previous):
        sys.exit(f"v{version} is not newer than the last released v{previous}")
    date = datetime.date.fromisoformat(args.date)
    changelog = release.render_changelog(version, date, pending, previous)
    target = release.changelog_path(root, version)
    rel_target = os.path.relpath(target, root)

    if args.dry_run:
        print(changelog)
        print(
            f"(dry run: {len(pending)} task(s) would be stamped {version}; "
            f"{rel_target} would be written)"
        )
        return

    if not args.no_changelog:
        release.write_changelog(target, changelog, force=args.force)
    for task in pending:
        task["release"] = version
    done["meta"]["latest_release"] = version
    store.save_finished(done, fin_path)

    print(f"released v{version} on {date}: stamped {len(pending)} task(s)")
    if not args.no_changelog:
        print(f"  changelog draft: {rel_target}")
    if args.bump_gradle:
        old_name, old_code, new_code = release.bump_gradle(root, version)
        print(
            f"  app/build.gradle.kts: versionName {old_name} -> {version}, "
            f"versionCode {old_code} -> {new_code}"
        )
    remaining = len(release.pending_tasks(store.load(fin_path)["tasks"]))
    print(f"  pending after this release: {remaining}")
    if not args.no_changelog:
        print("  next: curate the draft, commit, then push the v" + version + " tag")


def cmd_verify_release(args):
    """Check that *version* was actually frozen by `release` before it is tagged.

    The CI workflow runs this on a ``vX.Y.Z`` tag so a version can never ship
    with an unfrozen archive: the tag must match ``versionName``, the changelog
    must exist, and the archive must record the version as released. Work
    finished after the freeze is fine — it belongs to the next version.
    """
    root = store.find_repo_root()
    version = args.version.strip().lstrip("v")
    problems = []
    _, done = _load_finished()
    recorded = release.latest_version(done)
    if recorded != version:
        problems.append(
            f"FINISHED.toml records {recorded or 'no release'} as the latest, not {version} — "
            f"run `scripts/todo.sh release --version {version}` before tagging"
        )
    if not os.path.exists(release.changelog_path(root, version)):
        problems.append(
            f"docs/releases/v{version}.md is missing — the release step reads it as "
            "the Release body and its absence fails the publish"
        )
    gradle_name, _ = release.gradle_version(root)
    if gradle_name != version:
        problems.append(
            f"app/build.gradle.kts has versionName {gradle_name!r}, not {version!r} — "
            "re-run the release with --bump-gradle"
        )
    if problems:
        print("\n".join(f"- {p}" for p in problems))
        sys.exit(f"release v{version} was not frozen cleanly")
    pending = len(release.pending_tasks(done["tasks"]))
    print(f"v{version} frozen: valid, changelog present, versionName matches")
    print(f"  pending for the next release: {pending}")


def cmd_validate(args):
    problems = []
    seen = {}
    for label, (_, doc), finished in (
        ("TODO", _load_todo(), False),
        ("FINISHED", _load_finished(), True),
    ):
        for task in doc["tasks"]:
            problems += _validate_task(label, task, finished, seen)
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    print("ok — both files validate")


def _validate_task(label, task, finished, seen):
    out = []
    tid = task.get("id")
    where = f"{label} [{tid or '?'}]"
    for field in ("id", "title", "description", "status"):
        if not task.get(field):
            out.append(f"{where}: missing {field}")
    if tid in seen:
        out.append(f"{where}: duplicate id (also in {seen[tid]})")
    elif tid:
        seen[tid] = label
    if "level" in task and task["level"] not in store.LEVELS:
        out.append(f"{where}: unknown level {task['level']!r}")
    if "category" in task and task["category"] not in store.CATEGORIES:
        out.append(f"{where}: unknown category {task['category']!r}")
    if finished:
        if task.get("status") != store.STATUS_FINISHED:
            out.append(f"{where}: archive task not marked finished")
        if not task.get("completed"):
            out.append(f"{where}: missing completed date")
        shipped = task.get("release")
        if shipped is not None and not release.is_version(shipped):
            out.append(f"{where}: release must be an X.Y.Z version, got {shipped!r}")
    else:
        if "release" in task:
            out.append(f"{where}: release is archive-only (set by `todo.py release`)")
        if task.get("status") not in store.STATUSES_ACTIVE:
            out.append(f"{where}: status not one of {store.STATUSES_ACTIVE}")
        if task.get("urgency") and task["urgency"] not in store.URGENCIES:
            out.append(f"{where}: unknown urgency {task['urgency']!r}")
        for flag in ("rebuild", "emulator_debug"):
            if flag in task and not isinstance(task[flag], bool):
                out.append(f"{where}: {flag} must be true or false")
    return out


# --- migrate (one-shot markdown/rough-TOML cleanup) ------------------------

def _norm(text):
    return re.sub(r"\s+", " ", text or "").strip()


def _strip_date(text):
    return re.sub(r"^\d{4}-\d{2}-\d{2}\s*[—–-]\s*", "", text).strip()


def _strip_md(text):
    return text.replace("**", "").replace("`", "").strip()


def _guess_category(text):
    """Best-effort category from a task's title. Deliberately keyed on the
    title, not the body — words like "fixed palette" or "long document" in a
    description otherwise misclassify a feature as a bug/docs task."""
    low = text.lower()
    if re.search(r"\b(fix|fixed|bug)\b", low):
        return "bug"
    if re.search(r"\b(test|smoke|drift)\b", low):
        return "test"
    if re.search(r"document(ed|ation)|readme|agents\.md|scaffold|pinned|survey"
                 r"|toolchain|harness|architecture", low):
        return "docs"
    return "feature"


def _clean_completed(task):
    if task.get("date"):
        return datetime.date.fromisoformat(str(task["date"]))
    m = re.search(r"(\d{4}-\d{2}-\d{2})", (task.get("title", "") + " "
                                           + task.get("description", "")))
    return datetime.date.fromisoformat(m.group(1)) if m else None


def _clean_title(task):
    raw = _strip_md(_strip_date(_norm(task.get("title", ""))))
    if raw and raw.endswith(".") and len(raw) <= 120:
        return raw
    desc = _strip_md(_strip_date(_norm(task.get("description", ""))))
    first = re.split(r"(?<=\.)\s", desc, maxsplit=1)[0]
    return (first[:100]).strip() or raw or "(untitled)"


def _clean_description(task):
    desc = _strip_date(_norm(task.get("description", "")))
    desc = re.sub(r"^\*\*.*?\*\*\s*", "", desc)  # drop repeated bold title
    return desc.replace("**", "").strip()


def cmd_migrate(args):
    created = datetime.date.fromisoformat(args.created)
    _migrate_todo(created)
    _migrate_finished()
    print("migrated TODO.toml and FINISHED.toml to schema v"
          f"{store.SCHEMA_VERSION}; run `todo.py validate`")


def _migrate_todo(created):
    root = store.find_repo_root()
    raw = tomlio_load(store.todo_path(root))
    meta = dict(raw.get("todo", {}))
    meta.pop("source_of_truth", None)
    meta["kind"] = "todo"
    meta["archive"] = "FINISHED.toml"
    rules = dict(raw.get("rules", {}))
    rules["finish_work"] = ("When a task is fully finished (built, tested, "
                            "documented), run `todo.py done <id>` to move it to "
                            "FINISHED.toml.")
    meta["rules"] = rules
    tasks = []
    for i, old in enumerate(raw.get("tasks", []), start=1):
        text = old.get("title", "") + " " + old.get("description", "")
        tasks.append({
            "id": old["id"],
            "title": _strip_md(_norm(old.get("title", ""))),
            "description": _norm(old.get("description", "")),
            "status": "active",
            "category": _guess_category(text),
            "urgency": "medium",
            "order": i * 10,
            "created": created,
            "tags": [],
        })
    store.save_todo({"meta": meta, "tasks": tasks}, store.todo_path(root))
    dropped = len(raw.get("notes", []))
    if dropped:
        print(f"  todo: dropped {dropped} informational note(s) "
              "(already captured in FINISHED.toml)")


def _migrate_finished():
    root = store.find_repo_root()
    raw = tomlio_load(store.finished_path(root))
    meta = dict(raw.get("finished", {}))
    meta.pop("source_markdown", None)
    meta.pop("append_only", None)
    meta["kind"] = "finished"
    rules = dict(raw.get("rules", {}))
    rules["finish_work"] = ("Tasks arrive here via `todo.py done <id>`; newest "
                            "first, append-only.")
    meta["rules"] = rules
    tasks = []
    for old in raw.get("tasks", []):
        tasks.append({
            "id": old["id"],
            "title": _clean_title(old),
            "description": _clean_description(old),
            "status": store.STATUS_FINISHED,
            "category": _guess_category(_clean_title(old)),
            "completed": _clean_completed(old),
        })
    store.save_finished({"meta": meta, "tasks": tasks},
                        store.finished_path(root))


def tomlio_load(path):
    import tomlio
    return tomlio.load(path)


# --- argument parser -------------------------------------------------------

def build_parser():
    p = argparse.ArgumentParser(description="TOML task-list manager")
    sub = p.add_subparsers(dest="command", required=True)

    ls = sub.add_parser("list", help="list tasks")
    ls.add_argument("--finished", action="store_true", help="list the archive")
    ls.add_argument("--unreleased", action="store_true",
                    help="archive only: the finished tasks the next version will ship")
    ls.add_argument("--status")
    ls.add_argument("--category")
    ls.add_argument("--level", choices=store.LEVELS)
    ls.add_argument("--json", action="store_true")
    ls.set_defaults(func=cmd_list)

    sh = sub.add_parser("show", help="show one task by id")
    sh.add_argument("id")
    sh.add_argument("--json", action="store_true")
    sh.set_defaults(func=cmd_show)

    st = sub.add_parser("stats", help="summary counts")
    st.add_argument("--json", action="store_true")
    st.set_defaults(func=cmd_stats)

    ct = sub.add_parser("count", help="count tasks, optionally grouped")
    ct.add_argument("--finished", action="store_true")
    ct.add_argument("--by", choices=("status", "category", "urgency", "level"))
    ct.set_defaults(func=cmd_count)

    ad = sub.add_parser("add", help="add an active task")
    ad.add_argument("--title", required=True)
    ad.add_argument("--description", required=True)
    ad.add_argument("--category", choices=store.CATEGORIES, default="feature")
    ad.add_argument("--level", choices=store.LEVELS, default=store.DEFAULT_LEVEL,
                    help="task (atomic) | scope (investigate, then add tasks) | "
                         "epic (large; break into scope/task children)")
    ad.add_argument("--urgency", choices=store.URGENCIES, default="normal")
    ad.add_argument("--status", choices=store.STATUSES_ACTIVE, default="active")
    ad.add_argument("--id", help="explicit id (default: slug of title)")
    ad.add_argument("--tag", action="append")
    ad.add_argument("--rebuild", action=argparse.BooleanOptionalAction, default=True,
                    help="rebuild the Android app for this task (default: yes)")
    ad.add_argument("--emulator-debug", action=argparse.BooleanOptionalAction,
                    default=False,
                    help="run the full emulator verify loop for this task (default: no)")
    ad.set_defaults(func=cmd_add)

    ed = sub.add_parser("edit", help="edit an active task")
    ed.add_argument("id")
    ed.add_argument("--title")
    ed.add_argument("--description")
    ed.add_argument("--status", choices=store.STATUSES_ACTIVE)
    ed.add_argument("--category", choices=store.CATEGORIES)
    ed.add_argument("--level", choices=store.LEVELS)
    ed.add_argument("--urgency", choices=store.URGENCIES)
    ed.add_argument("--order", type=int)
    ed.add_argument("--add-tag", action="append")
    ed.add_argument("--rebuild", action=argparse.BooleanOptionalAction, default=None,
                    help="set whether this task rebuilds the Android app")
    ed.add_argument("--emulator-debug", action=argparse.BooleanOptionalAction,
                    default=None,
                    help="set whether this task runs the full emulator verify loop")
    ed.set_defaults(func=cmd_edit)

    dn = sub.add_parser("done", help="move a task to the archive")
    dn.add_argument("id")
    dn.add_argument("--date", help="completion date YYYY-MM-DD (default: today)")
    dn.set_defaults(func=cmd_done)

    rm = sub.add_parser("remove", help="drop an active task")
    rm.add_argument("id")
    rm.add_argument("--reason")
    rm.set_defaults(func=cmd_remove)

    rl = sub.add_parser("release",
                        help="freeze a release: stamp the pending archive tasks "
                             "and draft their changelog")
    rl.add_argument("--version", required=True,
                    help="release version, X.Y.Z (matches the git tag without its v)")
    rl.add_argument("--date", default=datetime.date.today().isoformat(),
                    help="release date for the changelog (YYYY-MM-DD, default: today)")
    rl.add_argument("--through",
                    help="backfill only: stamp pending tasks completed on or before "
                         "this date (YYYY-MM-DD)")
    rl.add_argument("--no-changelog", action="store_true",
                    help="stamp the tasks without writing docs/releases/vX.Y.Z.md")
    rl.add_argument("--bump-gradle", action="store_true",
                    help="also set versionName and increment versionCode in "
                         "app/build.gradle.kts")
    rl.add_argument("--force", action="store_true",
                    help="overwrite an existing changelog file")
    rl.add_argument("--dry-run", action="store_true",
                    help="print the changelog and the plan, change nothing")
    rl.set_defaults(func=cmd_release)

    vr = sub.add_parser("verify-release",
                        help="check a version was frozen before it is tagged"
                             " (run by CI on a v* tag)")
    vr.add_argument("--version", required=True,
                    help="release version, X.Y.Z or the vX.Y.Z tag")
    vr.set_defaults(func=cmd_verify_release)

    va = sub.add_parser("validate", help="lint both task files")
    va.set_defaults(func=cmd_validate)

    mg = sub.add_parser("migrate", help="one-shot: clean rough TOML to schema v1")
    mg.add_argument("--created", default=datetime.date.today().isoformat(),
                    help="created date for active tasks (YYYY-MM-DD)")
    mg.set_defaults(func=cmd_migrate)

    return p


def main(argv=None):
    # A release changelog carries emoji, and on a non-UTF-8 console (Windows cp1252) echoing it
    # would crash the command. The files are always written UTF-8; only the console needs the
    # unencodable handful replaced rather than fatal.
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(errors="replace")
        except (AttributeError, ValueError):  # not a TextIOWrapper, or already detached
            pass
    args = build_parser().parse_args(argv)
    args.func(args)


if __name__ == "__main__":
    main()

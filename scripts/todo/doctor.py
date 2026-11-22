"""Context briefing (`todo brief`) and backlog drift diagnostics (`todo doctor`).

Dependency-free Python stdlib helpers that help agents start a task without
exploratory tool-call churn and catch stale paths, doc ceilings, and WIP bloat.
"""

from __future__ import annotations

import datetime
import os
import re
import subprocess

import store

DOC_WARN_LINES = 280
DOC_BUDGET_LINES = 300
SOURCE_WARN_LINES = 600
SOURCE_BUDGET_LINES = 700
WIP_READY_LIMIT = 15
WIP_ACTIVE_TASK_LIMIT = 30

_REPO_PATH_RE = re.compile(r"\b((?:app|mobix-core|desktop|docs|scripts)/[A-Za-z0-9_./-]+)")
_EXT_PATH_RE = re.compile(
    r"\b((?:app|mobix-core|desktop|docs|scripts)/[A-Za-z0-9_./-]+\.(?:kt|kts|java|py|sh|cmd|ps1|md|yml))"
)
_DOC_MD_RE = re.compile(r"\b(docs/[A-Za-z0-9_./-]+\.md)\b")
_SRC_MAIN_KT_RE = re.compile(r"\b((?:app|mobix-core|desktop)/src/main/[A-Za-z0-9_./-]+\.kt)\b")
_NEW_PREFIX_RE = re.compile(r"\b(?:new|create)(?:\s+(?:file|spoke))?\s+[`'\"]?$", re.IGNORECASE)
_UI_PATH_PREFIXES = (
    "app/src/main/java/com/mobixournal/ui/",
    "desktop/src/main/java/com/mobixournal/desktop/",
)
_UI_DOCS = frozenset({
    "docs/interface-contract.md",
    "docs/ui-modernization.md",
    "docs/chrome.md",
    "docs/desktop-ui-layout.md",
    "docs/desktop-workstation.md",
    "docs/desktop-settings.md",
})


def _parse_date(value):
    if type(value) is datetime.date:
        return value
    if isinstance(value, str):
        try:
            return datetime.date.fromisoformat(value)
        except ValueError:
            return None
    return None


def _line_count(path):
    with open(path, encoding="utf-8", errors="replace") as fh:
        return sum(1 for _ in fh)


def _existing_paths_for(task, root):
    seen = set()
    ordered = []
    candidates = list(task.get("targets") or [])
    desc = task.get("description") or ""
    for match in _REPO_PATH_RE.finditer(desc):
        candidates.append(match.group(1).rstrip("."))
    for ref in candidates:
        clean = ref.replace("\\", "/").rstrip("/")
        if not clean or "*" in clean or "..." in clean or clean in seen:
            continue
        full = os.path.join(root, clean) if root else None
        if full and os.path.isfile(full):
            seen.add(clean)
            ordered.append((clean, full))
    return ordered


def _file_warning(rel_path, lines):
    if rel_path.startswith("docs/") and rel_path.endswith(".md") and lines >= DOC_WARN_LINES:
        return f"near/at {DOC_BUDGET_LINES}-line DocsGuardTest cap (plan spoke split)"
    if "/src/main/" in rel_path and rel_path.endswith(".kt") and lines >= SOURCE_WARN_LINES:
        return f"near/over {SOURCE_BUDGET_LINES}-line SourceStructureTest cap"
    return None


def _is_ui_task(task):
    if "ui" in (task.get("tags") or []):
        return True
    candidates = list(task.get("targets") or [])
    desc = task.get("description") or ""
    candidates.extend(m.group(1).rstrip(".") for m in _REPO_PATH_RE.finditer(desc))
    for ref in candidates:
        clean = ref.replace("\\", "/").rstrip("/")
        if clean in _UI_DOCS or any(clean.startswith(prefix) for prefix in _UI_PATH_PREFIXES):
            return True
    return False


def _suggested_verification(task):
    ui_flag = " --ui-guards" if _is_ui_task(task) else ""
    build_flag = " --build-apk" if task.get("rebuild") else ""
    steps = [
        f"Windows: scripts/verify.cmd{ui_flag}{build_flag} [-- <gradle-args>]",
        f"Linux/macOS: scripts/verify.sh{ui_flag}{build_flag} [-- <gradle-args>]",
    ]
    if task.get("emulator_debug"):
        steps.append(
            "Emulator pass required (emulator_debug = true): use scripts/emulator.cmd (Windows) or "
            "scripts/emulator.sh (POSIX) per docs/tools-emulator.md"
        )
    return steps


def brief_task(task, active_tasks, finished_tasks, root=None):
    """Build a structured context packet for starting *task*."""
    active_by_id = {t["id"]: t for t in active_tasks if t.get("id")}
    fin_by_id = {t["id"]: t for t in finished_tasks if t.get("id")}
    deps_resolved = []
    for dep in (task.get("depends_on") or []):
        if dep in fin_by_id:
            ft = fin_by_id[dep]
            shipped = " ".join((ft.get("description") or "").split())
            if len(shipped) > 220:
                shipped = shipped[:217] + "..."
            deps_resolved.append({
                "id": dep, "state": "DONE", "completed": str(ft.get("completed") or ""), "summary": shipped,
            })
        elif dep in active_by_id:
            at = active_by_id[dep]
            deps_resolved.append({
                "id": dep, "state": f"ACTIVE ({at.get('status')})", "title": at.get("title", ""),
            })
        else:
            deps_resolved.append({"id": dep, "state": "MISSING"})

    files = []
    for rel, full in _existing_paths_for(task, root):
        lines = _line_count(full)
        entry = {"path": rel, "lines": lines}
        warn = _file_warning(rel, lines)
        if warn:
            entry["warning"] = warn
        files.append(entry)

    return {
        "id": task.get("id"),
        "title": task.get("title"),
        "level": task.get("level", store.DEFAULT_LEVEL),
        "status": task.get("status"),
        "category": task.get("category"),
        "urgency": task.get("urgency"),
        "order": task.get("order"),
        "epic": task.get("epic"),
        "created": str(task.get("created") or ""),
        "reviewed": str(task["reviewed"]) if task.get("reviewed") else None,
        "rebuild": bool(task.get("rebuild", True)),
        "emulator_debug": bool(task.get("emulator_debug", False)),
        "tags": list(task.get("tags") or []),
        "targets": list(task.get("targets") or []),
        "depends_on": deps_resolved,
        "referenced_files": files,
        "verification": _suggested_verification(task),
        "description": task.get("description", ""),
    }


def format_brief(payload):
    """Render *payload* from ``brief_task`` as concise human/agent text."""
    lines = [
        f"[{payload['id']}] {payload['title']}",
        f"  level={payload['level']} status={payload['status']} category={payload['category']} "
        f"urgency={payload['urgency']} order={payload['order']} rebuild={payload['rebuild']} "
        f"emulator_debug={payload['emulator_debug']}",
    ]
    if payload.get("epic"):
        lines.append(f"  epic={payload['epic']}")
    rev = f" reviewed={payload['reviewed']}" if payload.get("reviewed") else ""
    lines.append(f"  created={payload['created']}{rev}")
    if payload["depends_on"]:
        lines.append("\nDependencies:")
        for dep in payload["depends_on"]:
            if dep["state"] == "DONE":
                lines.append(f"  [DONE {dep['completed']}] {dep['id']}: {dep['summary']}")
            elif dep["state"] == "MISSING":
                lines.append(f"  [MISSING] {dep['id']}")
            else:
                lines.append(f"  [{dep['state']}] {dep['id']}: {dep['title']}")
    if payload["referenced_files"]:
        lines.append("\nExisting referenced files:")
        for item in payload["referenced_files"]:
            note = f"  [WARN: {item['warning']}]" if item.get("warning") else ""
            lines.append(f"  - {item['path']} ({item['lines']} lines){note}")
    lines.append("\nDescription:\n" + payload["description"])
    lines.append("\nVerification:")
    for step in payload["verification"]:
        lines.append(f"  - {step}")
    return "\n".join(lines)


def _git_latest_change(root, rel_paths):
    try:
        res = subprocess.run(
            ["git", "log", "-n", "1", "--format=%cs", "--name-only", "--", *rel_paths],
            cwd=root,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=15,
        )
    except (OSError, subprocess.SubprocessError):
        return None, []
    if res.returncode != 0 or not res.stdout.strip():
        return None, []
    out_lines = [line.strip() for line in res.stdout.splitlines() if line.strip()]
    commit_date = _parse_date(out_lines[0])
    touched = [line for line in out_lines[1:] if line in rel_paths]
    return commit_date, touched or rel_paths


def _ready_items(active_tasks, finished_ids):
    return [
        t for t in active_tasks
        if t.get("level", store.DEFAULT_LEVEL) != "epic"
        and t.get("status") != "blocked"
        and all(d in finished_ids for d in (t.get("depends_on") or []))
    ]


def _scan_missing_paths(inspected, root):
    out = []
    for task in inspected:
        tid = task.get("id", "?")
        for tgt in (task.get("targets") or []):
            if not os.path.exists(os.path.join(root, tgt)):
                out.append({"id": tid, "path": tgt, "source": "targets"})
        desc = task.get("description") or ""
        for match in _EXT_PATH_RE.finditer(desc):
            ref = match.group(1).rstrip(".")
            if "*" in ref or "..." in ref:
                continue
            prefix = desc[max(0, match.start() - 32):match.start()]
            if _NEW_PREFIX_RE.search(prefix):
                continue
            if not os.path.exists(os.path.join(root, ref)):
                out.append({"id": tid, "path": ref, "source": "description"})
    return out


def _scan_ceilings(active_tasks, inspected, root):
    doc_counts = {}
    for task in active_tasks:
        text = (task.get("title") or "") + "\n" + (task.get("description") or "")
        for ref in set(_DOC_MD_RE.findall(text)):
            doc_counts[ref] = doc_counts.get(ref, 0) + 1
    doc_ceilings = []
    for ref, count in sorted(doc_counts.items()):
        full = os.path.join(root, ref)
        if os.path.isfile(full):
            lines = _line_count(full)
            if lines >= DOC_WARN_LINES:
                doc_ceilings.append({"path": ref, "lines": lines, "budget": DOC_BUDGET_LINES, "task_count": count})
    src_seen = {}
    for task in inspected:
        paths = list(task.get("targets") or []) + _SRC_MAIN_KT_RE.findall(task.get("description") or "")
        for ref in paths:
            if not ref.endswith(".kt") or "/src/main/" not in ref:
                continue
            full = os.path.join(root, ref)
            if os.path.isfile(full) and ref not in src_seen:
                lines = _line_count(full)
                if lines >= SOURCE_WARN_LINES:
                    src_seen[ref] = {"path": ref, "lines": lines, "budget": SOURCE_BUDGET_LINES}
    return doc_ceilings, sorted(src_seen.values(), key=lambda x: (-x["lines"], x["path"]))


def diagnose_plan(active_tasks, finished_tasks, root=None, include_all=False):
    """Run backlog drift, missing path, file ceiling, and WIP diagnostics."""
    finished_ids = {t.get("id") for t in finished_tasks if t.get("id")}
    ready = _ready_items(active_tasks, finished_ids)
    inspected = [t for t in active_tasks if t.get("level") != "epic"] if include_all else ready

    stale = []
    has_git = bool(root and os.path.exists(os.path.join(root, ".git")))
    if root and has_git:
        for task in inspected:
            base = _parse_date(task.get("reviewed")) or _parse_date(task.get("created"))
            files = [rel for rel, _ in _existing_paths_for(task, root)]
            if not base or not files:
                continue
            latest, touched = _git_latest_change(root, files)
            if latest and latest > base:
                stale.append({
                    "id": task.get("id"),
                    "baseline_date": str(base),
                    "latest_change_date": str(latest),
                    "files": touched,
                })

    missing = _scan_missing_paths(inspected, root) if root else []
    doc_ceilings, source_ceilings = _scan_ceilings(active_tasks, inspected, root) if root else ([], [])

    non_epic_tasks = [t for t in active_tasks if t.get("level", store.DEFAULT_LEVEL) == "task"]
    blocked = [t for t in active_tasks if t.get("status") == "blocked"]
    scopes = [t for t in active_tasks if t.get("level") == "scope"]
    epics = [t for t in active_tasks if t.get("level") == "epic"]
    non_blocked_tasks = sum(1 for t in non_epic_tasks if t.get("status") != "blocked")
    advisory = None
    if len(ready) > WIP_READY_LIMIT or non_blocked_tasks > WIP_ACTIVE_TASK_LIMIT:
        advisory = (
            f"Rolling-wave WIP advisory: {len(ready)} ready items and {non_blocked_tasks} unblocked "
            f"tasks (target <= {WIP_READY_LIMIT} ready / <= {WIP_ACTIVE_TASK_LIMIT} active); "
            "keep distant future tracks as `scope` and expand 1-2 tracks Just-in-Time."
        )

    return {
        "inspected_scope": "all" if include_all else "ready",
        "inspected_count": len(inspected),
        "stale_tasks": stale,
        "missing_paths": missing,
        "doc_ceilings": doc_ceilings,
        "source_ceilings": source_ceilings,
        "wip_health": {
            "ready_count": len(ready),
            "active_tasks": len(non_epic_tasks),
            "blocked_count": len(blocked),
            "scopes_count": len(scopes),
            "epics_count": len(epics),
            "advisory": advisory,
        },
    }


def format_diagnosis(payload):
    """Render *payload* from ``diagnose_plan`` as readable text."""
    wip = payload["wip_health"]
    out = [
        f"Doctor ({payload['inspected_scope']}={payload['inspected_count']}): "
        f"stale={len(payload['stale_tasks'])} missing={len(payload['missing_paths'])} "
        f"doc_ceilings={len(payload['doc_ceilings'])} source_ceilings={len(payload['source_ceilings'])}",
        f"WIP: ready={wip['ready_count']} tasks={wip['active_tasks']} blocked={wip['blocked_count']} "
        f"scopes={wip['scopes_count']} epics={wip['epics_count']}",
    ]
    if wip["advisory"]:
        out.append(f"  [ADVISORY] {wip['advisory']}")
    if payload["stale_tasks"]:
        out.append("\nStale tasks (referenced files changed after created/reviewed):")
        for item in payload["stale_tasks"]:
            out.append(
                f"  - [{item['id']}] base={item['baseline_date']} latest={item['latest_change_date']} "
                f"files={', '.join(item['files'])}"
            )
    if payload["missing_paths"]:
        out.append("\nMissing referenced paths:")
        for item in payload["missing_paths"]:
            out.append(f"  - [{item['id']}] ({item['source']}) {item['path']}")
    if payload["doc_ceilings"]:
        out.append(f"\nDocs near/at {DOC_BUDGET_LINES}-line DocsGuardTest cap:")
        for item in payload["doc_ceilings"]:
            out.append(f"  - {item['path']}: {item['lines']}/{item['budget']} lines (cited by {item['task_count']} tasks)")
    if payload["source_ceilings"]:
        out.append(f"\nKotlin sources near/over {SOURCE_BUDGET_LINES}-line SourceStructureTest cap:")
        for item in payload["source_ceilings"]:
            out.append(f"  - {item['path']}: {item['lines']}/{item['budget']} lines")
    return "\n".join(out)

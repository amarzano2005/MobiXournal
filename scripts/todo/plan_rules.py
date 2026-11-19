"""Stringent plan-coherence, DAG, and small-model readiness rules for active tasks.

Historical entries in FINISHED.toml are never retroactively rejected here.
Live repository path and Gradle test-module checks run when ``root`` points at
a checkout containing ``docs/``, keeping isolated temp-dir tests clean.
"""

from __future__ import annotations

import os
import re

import store

MAX_TITLE_LEN = 120
MIN_TASK_DESC = 80
MIN_CONTAINER_DESC = 120
MAX_DESC_LEN = 2600

_OPEN_DECISION_RE = re.compile(
    r"^\s*(?:decide(?:\s+(?:whether|where|how|that|and|with|against))?"
    r"|pick\s+one\b"
    r"|audit\s+(?:before|the\b|[A-Z]\w+)"
    r"|investigate\b|explore\b|figure\s+out\b|check\s+whether\b)",
    re.IGNORECASE,
)
_FILE_ANCHOR_RE = re.compile(
    r"\b[\w./-]+\.(?:kt|kts|java|py|sh|md|yml|yaml|json|xml|toml)\b"
    r"|\b(?:mobix-core|app|desktop|scripts|docs|spec|tools)/"
)
_VERIFY_RE = re.compile(
    r"\b(?:verify|tests?|assert|gradlew|scripts/build\.sh|scripts/todo\.sh"
    r"|screenshot|logcat|re-measure)\b",
    re.IGNORECASE,
)
_EPIC_MISSION_RE = re.compile(r"\b(?:mission|goal)\b", re.IGNORECASE)
_EPIC_DONE_RE = re.compile(r"\b(?:completion|done|rule)\b", re.IGNORECASE)
_SCOPE_WORK_RE = re.compile(
    r"\b(?:decide|audit|investigate|assess|evaluate|feasibility"
    r"|break(?:s|ing)?\s+(?:out|into)|child\s+tasks|deliverable"
    r"|go/no-go|record\s+the\s+decision)\b",
    re.IGNORECASE,
)
_ANDROID_CODE_RE = re.compile(
    r"(?:app/src/main/|both shells|\bon android\b|android and the window"
    r"|android and desktop|install the (?:debug )?apk|android offers|android owns)",
    re.IGNORECASE,
)
_MOJIBAKE_THETA_RE = re.compile(
    r"\br\(\?\)|theta/\?|atan\(\?\)|cos\s*\?|sin\s*\?|\?\s*min/\?\s*max|the \? fields"
)
_HOST_AVD_RE = re.compile(r"\bPixel_Tablet\b")
_OLD_EMU_DOC_RE = re.compile(
    r"docs/tools\.md.{0,40}host fallback", re.IGNORECASE | re.DOTALL
)
_BARE_TODO_PY_RE = re.compile(r"\bpython3?\s+scripts/todo/todo\.py\b")
_REPO_DOC_REF_RE = re.compile(r"\b(docs/[A-Za-z0-9_./-]+)")
_REPO_SCRIPT_REF_RE = re.compile(r"\b(scripts/[A-Za-z0-9_./-]+)")
_NEW_PREFIX_RE = re.compile(r"\b(?:new|create)(?:\s+(?:file|spoke))?\s+$", re.IGNORECASE)
_GRADLE_TEST_TASK_RE = re.compile(
    r":(mobix-core|app|desktop):(?:test|testDebugUnitTest)\b"
    r"((?:\s+--tests\s+(?:'[^']+'|\"[^\"]+\"|[^\s`\"')]+))+)"
)
_SINGLE_TEST_ARG_RE = re.compile(r"--tests\s+(?:'([^']+)'|\"([^\"]+)\"|([^\s`\"')]+))")


def _index_test_files(root):
    modules = {}
    for mod in ("mobix-core", "app", "desktop"):
        test_dir = os.path.join(root, mod, "src", "test")
        stems = set()
        if os.path.isdir(test_dir):
            for dirpath, _, filenames in os.walk(test_dir):
                for name in filenames:
                    if name.endswith(".kt"):
                        stems.add(name[:-3])
                    elif name.endswith(".java"):
                        stems.add(name[:-5])
        modules[mod] = stems
    return modules


def _check_fields_and_graph(active_tasks, active_by_id, all_by_id):
    errors = []
    for task in active_tasks:
        tid = task.get("id", "?")
        where = f"TODO [{tid}]"
        level = task.get("level", store.DEFAULT_LEVEL)
        epic = task.get("epic")
        if epic is not None:
            if not isinstance(epic, str) or not epic.strip():
                errors.append(f"{where}: epic must be a non-empty task id string")
            elif epic == tid:
                errors.append(f"{where}: epic cannot reference itself")
            else:
                target = active_by_id.get(epic)
                if not target or target.get("level", store.DEFAULT_LEVEL) != "epic":
                    errors.append(f"{where}: epic {epic!r} must reference an active epic")
        deps = task.get("depends_on")
        if deps is None:
            continue
        if not isinstance(deps, list) or any(not isinstance(d, str) or not d.strip() for d in deps):
            errors.append(f"{where}: depends_on must be a list of non-empty strings")
            continue
        if len(set(deps)) != len(deps):
            errors.append(f"{where}: depends_on contains duplicate ids")
        for dep in deps:
            if dep == tid:
                errors.append(f"{where}: depends_on cannot reference itself")
            elif dep not in all_by_id:
                errors.append(f"{where}: depends_on references unknown id {dep!r}")
            elif level == "task" and all_by_id[dep].get("level", store.DEFAULT_LEVEL) == "epic":
                errors.append(f"{where}: a task cannot depend_on epic {dep!r}")
    return errors


def _detect_cycles(active_tasks, active_by_id):
    errors = []
    state = {}  # 0=unvisited, 1=visiting, 2=done
    reported = set()

    def dfs(node_id, trail):
        state[node_id] = 1
        deps = active_by_id[node_id].get("depends_on") or []
        if isinstance(deps, list):
            for dep in deps:
                if dep not in active_by_id:
                    continue
                if state.get(dep) == 1:
                    idx = trail.index(dep)
                    cycle = " -> ".join(trail[idx:] + [dep])
                    sig = tuple(sorted(trail[idx:]))
                    if sig not in reported:
                        reported.add(sig)
                        errors.append(f"TODO [{node_id}]: cycle in depends_on: {cycle}")
                elif state.get(dep, 0) == 0:
                    dfs(dep, trail + [dep])
        state[node_id] = 2

    for task in active_tasks:
        tid = task.get("id")
        if tid and tid in active_by_id and state.get(tid, 0) == 0:
            dfs(tid, [tid])
    return errors


def _transitively_depends_on_scope(tid, active_by_id):
    seen = set()
    stack = list(active_by_id[tid].get("depends_on") or [])
    while stack:
        cur = stack.pop()
        if cur in seen or cur not in active_by_id:
            continue
        seen.add(cur)
        target = active_by_id[cur]
        if target.get("level", store.DEFAULT_LEVEL) == "scope":
            return cur
        deps = target.get("depends_on") or []
        if isinstance(deps, list):
            stack.extend(deps)
    return None


def _check_ordering_and_blocking(active_tasks, active_by_id, finished_ids):
    errors = []
    for task in active_tasks:
        tid = task.get("id", "?")
        where = f"TODO [{tid}]"
        level = task.get("level", store.DEFAULT_LEVEL)
        deps = task.get("depends_on") if isinstance(task.get("depends_on"), list) else []
        for dep in deps:
            if dep not in active_by_id:
                continue
            prereq = active_by_id[dep]
            if prereq.get("order", 1_000_000) >= task.get("order", 1_000_000):
                errors.append(
                    f"{where}: order {task.get('order')} must be strictly greater than "
                    f"prerequisite {dep} (order {prereq.get('order')})"
                )
            if store.urgency_rank(prereq) < store.urgency_rank(task):
                errors.append(
                    f"{where}: urgency {task.get('urgency')!r} cannot exceed "
                    f"prerequisite {dep} ({prereq.get('urgency')!r})"
                )
        if level == "task":
            scope_id = _transitively_depends_on_scope(tid, active_by_id)
            if scope_id and task.get("status") != "blocked":
                errors.append(
                    f"{where}: depends downstream of active scope {scope_id!r}, "
                    "so status must be 'blocked'"
                )
        if task.get("status") == "blocked":
            active_deps = [d for d in deps if d in active_by_id]
            if not active_deps:
                note = "all deps finished" if any(d in finished_ids for d in deps) else "depends_on empty"
                errors.append(
                    f"{where}: blocked item has no unfinished dependencies ({note}) — "
                    "unblock it or record depends_on"
                )
    return errors


def _check_epic_and_scope(task, active_tasks):
    errors = []
    tid = task.get("id", "?")
    where = f"TODO [{tid}]"
    level = task.get("level", store.DEFAULT_LEVEL)
    desc = (task.get("description") or "").strip()
    if task.get("rebuild", True):
        errors.append(f"{where}: {level} must have rebuild = false")
    if task.get("emulator_debug", False):
        errors.append(f"{where}: {level} must have emulator_debug = false")
    if not (MIN_CONTAINER_DESC <= len(desc) <= MAX_DESC_LEN):
        errors.append(
            f"{where}: {level} description length {len(desc)} outside "
            f"{MIN_CONTAINER_DESC}..{MAX_DESC_LEN}"
        )
    if level == "epic":
        children = {t.get("id") for t in active_tasks if t.get("epic") == tid}
        deps = set(task.get("depends_on") or [])
        if children & deps:
            errors.append(f"{where}: epic cannot depend_on its own children ({sorted(children & deps)})")
        if "research" not in (task.get("tags") or []) and not children:
            errors.append(f"{where}: non-research epic has no active children (epic == {tid!r})")
        if not (_EPIC_MISSION_RE.search(desc) and _EPIC_DONE_RE.search(desc)):
            errors.append(f"{where}: epic description must state mission/goal and completion/rule criteria")
    else:
        if not _SCOPE_WORK_RE.search(desc):
            errors.append(f"{where}: scope description must state what it investigates/decides and produces")
    return errors


def _check_task_readiness(task, all_by_id):
    errors = []
    tid = task.get("id", "?")
    where = f"TODO [{tid}]"
    title = (task.get("title") or "").strip()
    desc = (task.get("description") or "").strip()
    if len(title) > MAX_TITLE_LEN:
        errors.append(f"{where}: title length {len(title)} exceeds {MAX_TITLE_LEN}")
    if not (MIN_TASK_DESC <= len(desc) <= MAX_DESC_LEN):
        errors.append(f"{where}: description length {len(desc)} outside {MIN_TASK_DESC}..{MAX_DESC_LEN}")
    for sentence in re.split(r"(?<=[.!?])\s+|\n+", title + "\n" + desc):
        match = _OPEN_DECISION_RE.match(sentence)
        if match:
            errors.append(
                f"{where}: open design/exploration imperative {match.group(0).strip()!r} belongs in a scope, not a task"
            )
            break
    has_anchor = bool(_FILE_ANCHOR_RE.search(desc)) or (
        bool(task.get("emulator_debug")) and "com.mobixournal" in desc and "docs/tools-emulator.md" in desc
    )
    if not has_anchor:
        errors.append(f"{where}: task must cite a concrete file/path/module anchor")
    if not _VERIFY_RE.search(desc):
        errors.append(f"{where}: task must state an explicit verification step")
    cat = task.get("category")
    if cat == "docs":
        if task.get("rebuild", True) or task.get("emulator_debug", False):
            errors.append(f"{where}: docs task must have rebuild = false and emulator_debug = false")
        for dep in (task.get("depends_on") or []):
            dep_task = all_by_id.get(dep)
            if dep_task and dep_task.get("category") in ("feature", "bug", "refactor"):
                errors.append(f"{where}: standalone docs task cannot follow code task {dep!r}")
    elif not task.get("rebuild", True) and _ANDROID_CODE_RE.search(desc):
        errors.append(f"{where}: Android/both-shell task has rebuild = false")
    return errors


def _check_text_and_repo_refs(task, root, test_index):
    errors = []
    tid = task.get("id", "?")
    where = f"TODO [{tid}]"
    text = (task.get("title") or "") + "\n" + (task.get("description") or "")
    if "\ufffd" in text:
        errors.append(f"{where}: contains corrupted Unicode replacement character U+FFFD")
    if _MOJIBAKE_THETA_RE.search(text):
        errors.append(f"{where}: contains corrupted theta placeholder (? instead of θ)")
    if _HOST_AVD_RE.search(text):
        errors.append(f"{where}: hardcodes local AVD name; cite docs/host-profiles.md instead")
    if _OLD_EMU_DOC_RE.search(text):
        errors.append(f"{where}: cites docs/tools.md for emulator host fallback; use docs/tools-emulator.md")
    if _BARE_TODO_PY_RE.search(text):
        errors.append(f"{where}: cites bare python scripts/todo/todo.py; use scripts/todo.sh")
    if not root or not os.path.isdir(os.path.join(root, "docs")):
        return errors
    for regex in (_REPO_DOC_REF_RE, _REPO_SCRIPT_REF_RE):
        for match in regex.finditer(text):
            ref = match.group(1).rstrip(".")
            prefix = text[max(0, match.start() - 32):match.start()]
            if _NEW_PREFIX_RE.search(prefix):
                continue
            full = os.path.join(root, ref)
            if ref.endswith("/"):
                if not os.path.isdir(full):
                    errors.append(f"{where}: missing referenced directory {ref!r}")
            elif not (os.path.isfile(full) or os.path.isfile(full + ".md") or os.path.isdir(full)):
                errors.append(f"{where}: missing referenced path {ref!r}")
    for match in _GRADLE_TEST_TASK_RE.finditer(text):
        module, args_blob = match.group(1), match.group(2)
        for arg_m in _SINGLE_TEST_ARG_RE.finditer(args_blob):
            raw = next(g for g in arg_m.groups() if g is not None)
            token = raw.strip("*").split(".")[-1]
            if not token or token in test_index[module] or any(token in s for s in test_index[module]):
                continue
            other = [m for m, stems in test_index.items() if m != module and any(token in s for s in stems)]
            if other:
                errors.append(
                    f"{where}: Gradle selector {raw!r} under :{module}: matches 0 tests there "
                    f"(found in {', '.join(other)})"
                )
    return errors


def validate_plan(active_tasks, finished_tasks, root=None):
    """Return all plan-coherence, DAG, readiness, and path errors for active tasks."""
    active_by_id = {t["id"]: t for t in active_tasks if t.get("id")}
    all_by_id = {t["id"]: t for t in finished_tasks if t.get("id")}
    all_by_id.update(active_by_id)
    finished_ids = {t["id"] for t in finished_tasks if t.get("id")}

    errors = _check_fields_and_graph(active_tasks, active_by_id, all_by_id)
    errors += _detect_cycles(active_tasks, active_by_id)
    errors += _check_ordering_and_blocking(active_tasks, active_by_id, finished_ids)

    test_index = _index_test_files(root) if root and os.path.isdir(os.path.join(root, "docs")) else {}
    for task in active_tasks:
        level = task.get("level", store.DEFAULT_LEVEL)
        if level in ("epic", "scope"):
            errors += _check_epic_and_scope(task, active_tasks)
        elif level == "task":
            errors += _check_task_readiness(task, all_by_id)
        errors += _check_text_and_repo_refs(task, root, test_index)
    return errors

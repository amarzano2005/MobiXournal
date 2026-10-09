"""Freeze a release: separate the changes that shipped in a version from the
ones still pending, and draft that version's changelog from FINISHED.toml.

A finished task carries a ``release`` field once it has shipped. **Absent means
"pending the next version"**, which is the whole point: the pending set is what
the next changelog is drawn from, so it must contain exactly the work done since
the last tag and nothing already released.

``todo.py release --version X.Y.Z`` therefore:

1. selects every pending task (newest-first, as the archive holds them),
2. writes a draft ``docs/releases/vX.Y.Z.md`` grouped by category,
3. stamps those tasks with ``release = "X.Y.Z"`` and records ``latest_release``
   in the archive's meta — so the *next* release starts from an empty pending
   set, and the archive itself is the per-version separation,
4. optionally bumps ``versionName``/``versionCode`` in ``app/build.gradle.kts``.

The written changelog is a **draft**: it is never clobbered silently (see
:func:`write_changelog`) and a human curates it before the tag is pushed. Kept
out of ``todo.py`` so the command stays presentation-only and this logic —
selection, rendering, the version bump — is the only place that knows it.
"""

from __future__ import annotations

import datetime
import os
import re

import store

#: Where the per-version changelogs live, relative to the repo root.
CHANGELOG_DIR = ("docs", "releases")

#: One changelog section per category, in the order a release note reads best.
#: A category the archive holds that isn't listed falls into :data:`OTHER_SECTION`.
CATEGORY_SECTIONS = (
    ("feature", "✨ Features"),
    ("bug", "🐛 Fixes"),
    ("refactor", "♻️ Refactoring"),
    ("test", "🧪 Tests"),
    ("docs", "📚 Documentation"),
    ("chore", "🔧 Maintenance"),
)
OTHER_SECTION = "📦 Other"

#: Singular/plural nouns for the summary line, so "1 fix" never reads "1 fixes".
_CATEGORY_NOUNS = {
    "feature": ("feature", "features"),
    "bug": ("fix", "fixes"),
    "refactor": ("refactor", "refactors"),
    "test": ("test", "tests"),
    "docs": ("doc update", "doc updates"),
    "chore": ("chore", "chores"),
}

_VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")

_GRADLE_VERSION_CODE_RE = re.compile(r"^(\s*versionCode\s*=\s*)(\d+)(\s*)$", re.M)
_GRADLE_VERSION_NAME_RE = re.compile(r'^(\s*versionName\s*=\s*")([^"]*)("\s*)$', re.M)


# --- version helpers -------------------------------------------------------

def is_version(text):
    """True when *text* looks like a ``X.Y.Z`` release version."""
    return bool(isinstance(text, str) and _VERSION_RE.match(text.strip()))


def version_key(text):
    """Sort key for a version string; :func:`is_version` must have passed."""
    return tuple(int(part) for part in text.strip().split("."))


def latest_version(doc):
    """The newest released version recorded in a FINISHED document, or None.

    Reads both sources — the meta's ``latest_release`` (written by a release)
    and the ``release`` stamp on each task — so a hand-stamped archive still
    reports its newest version.
    """
    versions = {t.get("release") for t in doc["tasks"] if t.get("release")}
    meta_version = doc.get("meta", {}).get("latest_release")
    if meta_version:
        versions.add(meta_version)
    clean = [v for v in versions if is_version(v)]
    return max(clean, key=version_key) if clean else None


# --- selection -------------------------------------------------------------

def pending_tasks(tasks, through=None):
    """The finished tasks not yet shipped, in the archive's own order.

    *through* (a :class:`datetime.date`) narrows the set to tasks completed on or
    before that date. It exists for the one-shot backfill of a release that
    predates the ``release`` field, never for the normal path.
    """
    return [
        task for task in tasks
        if not task.get("release")
        and (through is None or _completed_on_or_before(task, through))
    ]


def _completed_on_or_before(task, through):
    raw = task.get("completed")
    if isinstance(raw, datetime.datetime):
        return raw.date() <= through
    if isinstance(raw, datetime.date):
        return raw <= through
    if isinstance(raw, str):
        try:
            return datetime.date.fromisoformat(raw) <= through
        except ValueError:
            return False
    return False


# --- changelog -------------------------------------------------------------

def changelog_path(root, version):
    """Absolute path of the changelog for *version* (its name must match the tag)."""
    return os.path.join(root, *CHANGELOG_DIR, f"v{version}.md")


def render_changelog(version, date, tasks, previous=None):
    """Render the draft changelog for *version* from its *tasks*.

    Grouped by category with one bullet per task, plus a summary line and — when
    the previous version is known — the compare link the earlier notes carry.
    """
    lines = [f"# MobiXournal v{version}", "", summary(date, tasks), ""]
    for category, heading in CATEGORY_SECTIONS:
        group = [t for t in tasks if t.get("category") == category]
        if group:
            lines += [f"## {heading}", ""]
            lines += [f"- {bullet(t)}" for t in group]
            lines.append("")
    other = [t for t in tasks if t.get("category") not in dict(CATEGORY_SECTIONS)]
    if other:
        lines += [f"## {OTHER_SECTION}", ""]
        lines += [f"- {bullet(t)}" for t in other]
        lines.append("")
    if previous:
        lines += [
            "---",
            "",
            f"**Full diff**: [`v{previous}...v{version}`]"
            f"(https://github.com/amarzano2005/MobiXournal/compare/v{previous}...v{version})",
            "",
        ]
    lines.append(_draft_note(version))
    return "\n".join(lines)


def summary(date, tasks):
    """One-line summary: the release date and how many changes of each kind."""
    counts = {}
    for task in tasks:
        counts[task.get("category")] = counts.get(task.get("category"), 0) + 1
    parts = []
    for category, _ in CATEGORY_SECTIONS:
        count = counts.pop(category, 0)
        if count:
            parts.append(f"{count} {_noun(category, count)}")
    other = sum(counts.values())
    if other:
        parts.append(f"{other} other")
    detail = ", ".join(parts) if parts else "no changes"
    label = "change" if len(tasks) == 1 else "changes"
    return f"Released {date} · {len(tasks)} {label}: {detail}."


def _noun(category, count):
    singular, plural = _CATEGORY_NOUNS.get(category, (category or "change", "changes"))
    return singular if count == 1 else plural


def bullet(task):
    """One changelog entry: the task's title, then the gist of its description.

    The title is the *what*, the first sentence of the description the *why*, so
    a draft reads as prose without anyone having to re-read the archive.
    """
    title = (task.get("title") or "").strip().rstrip(".")
    gist = _gist(task.get("description"))
    if not gist:
        return f"**{title}**."
    if gist.endswith(("…", "!", "?")):
        return f"**{title}** — {gist}"
    return f"**{title}** — {gist}."


def _gist(text, limit=200):
    """A task's first sentence, shortened at a clause boundary when it runs long.

    Cutting mid-word reads as broken prose, so a long sentence is clipped at the
    last ``;``/``,``/dash/space before the limit instead.
    """
    text = re.sub(r"\s+", " ", text or "").strip()
    if not text:
        return ""
    sentence = re.search(r"(?<=\.)\s", text)
    if sentence:
        text = text[:sentence.start()]
    text = text.rstrip(".")
    if len(text) <= limit:
        return text
    clipped = text[:limit]
    for separator in ("; ", ", ", " — ", " - ", " "):
        index = clipped.rfind(separator)
        if index >= limit // 2:
            return clipped[:index].rstrip(" ,;-") + "…"
    return clipped.rstrip() + "…"


def _draft_note(version):
    # An HTML comment, so it vanishes from the rendered release notes while still
    # telling whoever opens the file that it is a starting point, not the final word.
    return (
        "<!-- Draft generated from FINISHED.toml by "
        f"`scripts/todo.sh release --version {version}`.\n"
        "     Curate it — group, reword, add the highlights — then it is published\n"
        "     verbatim as the GitHub Release body (see docs/tools.md#cutting-a-release). -->\n"
    )


def write_changelog(path, text, force=False):
    """Write a draft changelog, refusing to clobber a curated one unless *force*."""
    if os.path.exists(path) and not force:
        raise SystemExit(
            f"{path} already exists — curate it, or re-run with --force to overwrite"
        )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    return path


# --- version bump ----------------------------------------------------------

def gradle_path(root):
    return os.path.join(root, "app", "build.gradle.kts")


def bump_gradle(root, version):
    """Point ``app/build.gradle.kts`` at *version*.

    ``versionName`` becomes *version* and ``versionCode`` is incremented — a
    monotonic counter, not derived from the name (see ``docs/tools.md``). Returns
    ``(old_name, old_code, new_code)``. Read and written with ``newline=""`` so
    the file's existing line endings survive the edit untouched.
    """
    path = gradle_path(root)
    with open(path, encoding="utf-8", newline="") as fh:
        text = fh.read()
    code = _GRADLE_VERSION_CODE_RE.search(text)
    name = _GRADLE_VERSION_NAME_RE.search(text)
    if not code or not name:
        raise SystemExit(f"cannot find versionCode/versionName in {path}")
    old_name, old_code = name.group(2), int(code.group(2))
    new_code = old_code + 1
    text = _GRADLE_VERSION_CODE_RE.sub(
        lambda m: f"{m.group(1)}{new_code}{m.group(3)}", text, count=1,
    )
    text = _GRADLE_VERSION_NAME_RE.sub(
        lambda m: f"{m.group(1)}{version}{m.group(3)}", text, count=1,
    )
    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)
    return old_name, old_code, new_code


def gradle_version(root):
    """The ``(versionName, versionCode)`` currently in ``app/build.gradle.kts``."""
    with open(gradle_path(root), encoding="utf-8") as fh:
        text = fh.read()
    code = _GRADLE_VERSION_CODE_RE.search(text)
    name = _GRADLE_VERSION_NAME_RE.search(text)
    if not code or not name:
        return None, None
    return name.group(2), int(code.group(2))

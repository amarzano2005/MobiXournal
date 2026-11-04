"""The design declaration a change to the interface must carry into the archive.

`docs/interface-contract.md` is the law for what a new interface element must satisfy, and
`scripts/design.sh` is the corpus a change is checked against. Neither could make anyone **say** what
was checked: the contract's checklist was a paragraph nobody had to answer, so a change closed without
ever opening it left exactly the same trace as one that had been through all nine lines.

This module is the answering half. `todo.py done` refuses to archive a task without a declaration, and
the declaration is written in the contract's own words — the axes below are its checklist:

    material: capsule, standing in the ribbon's lane
    states: hover and focus added, the other three unchanged

Two answers is the floor (a single axis is a note, not a check), an axis naming no answer is refused,
and an unknown axis is refused with the vocabulary rather than silently ignored. A `rule:
<Category>/<Issue>` clause cites the corpus, and every id in it must exist in `ux-guidelines.csv`, so a
citation is **checked** where the skill is vendored and skipped where it is not — the gate must not
depend on a folder someone may delete. `none: why` is the way out for a task with no interface surface,
and it keeps the reason in the archive beside what shipped.

It cannot read a screenshot. What it can do is make the question unskippable and keep the answer, which
is the most an archive can hold.
"""

from __future__ import annotations

import csv
import io
import os

# The contract's checklist, one word per line of it (`docs/interface-contract.md`, *Checklist*).
AXES = ("material", "tokens", "glyph", "states", "copy", "keyboard", "parity", "docs", "tests")

# Clause keys that are not axes: a citation of the corpus, and the escape for work with no UI surface.
_CITATION_KEYS = ("rule", "rules")
_NONE_KEYS = ("none", "not-ui")

MIN_AXES = 2
MIN_NONE_REASON = 12
MAX_DECLARATION = 1200

# The corpus `scripts/design.sh --domain ux` answers from; owned by `docs/tools.md` (*UI/UX Pro Max*).
CORPUS = os.path.join("skills", "ui-ux-pro-max", "data", "ux-guidelines.csv")

_HERE = os.path.dirname(os.path.abspath(__file__))


def fold(text):
    """One line, the way the task files store every free-text field."""
    return " ".join((text or "").split())


def normalise(text):
    """One line, with a line break treated as a clause break.

    A declaration read from `--design-file` is naturally written one clause per line, while the same
    thing on the command line is one line of `;`-separated clauses. Folding first would weld two
    clauses into one (`material: capsule states: hover`), which parses as a single axis and is refused
    for the wrong reason, so the breaks are turned into the separator before the fold.
    """
    raw = (text or "").replace("\r\n", "\n").replace("\r", "\n")
    if "\n" in raw:
        lines = (line.strip().rstrip(";").strip() for line in raw.split("\n"))
        raw = "; ".join(line for line in lines if line)
    return fold(raw)


def corpus_path(root=None):
    """Where the vendored corpus would be: *root* (a checkout) or this module's own repo."""
    base = root or os.path.abspath(os.path.join(_HERE, "..", ".."))
    return os.path.join(base, CORPUS)


def _corpus_ids(root=None):
    """The `Category/Issue` ids the corpus publishes, or ``None`` when it is not vendored.

    ``None`` is not an error: the skill is a vendored third-party corpus, so a checkout without it is a
    checkout where a citation cannot be verified — and skipping verification is the honest answer, not
    refusing every declaration from then on.
    """
    path = corpus_path(root)
    if not os.path.isfile(path):
        return None
    try:
        with io.open(path, encoding="utf-8", newline="") as fh:
            rows = list(csv.DictReader(fh))
    except (OSError, csv.Error, UnicodeDecodeError):
        return None
    ids = set()
    for row in rows:
        category = fold(row.get("Category"))
        issue = fold(row.get("Issue"))
        if category and issue:
            ids.add(f"{category}/{issue}".casefold())
    return ids or None


def _citations(note):
    """The ids a `rule:` clause names, in the corpus' own `Category/Issue` spelling."""
    out = []
    for part in note.split(","):
        cited = fold(part).strip("`").strip()
        category, sep, issue = cited.partition("/")
        if sep and fold(category) and fold(issue):
            out.append(f"{fold(category)}/{fold(issue)}")
    return out


def problems(text, root=None):
    """Everything wrong with a declaration; an empty list means it says what a closer has to say."""
    declaration = normalise(text)
    if not declaration:
        return [
            "the declaration is empty: name the axes you verified, or say "
            "`none: why this task has no interface surface`"
        ]
    if len(declaration) > MAX_DECLARATION:
        return [f"the declaration is {len(declaration)} characters: keep it under {MAX_DECLARATION}"]

    clauses = [part.strip() for part in declaration.split(";") if part.strip()]
    if ":" in clauses[0] and clauses[0].partition(":")[0].strip().lower() in _NONE_KEYS:
        return _none_problems(clauses)

    out = []
    answered = []
    for clause in clauses:
        key, sep, note = clause.partition(":")
        key = key.strip().strip("`").strip().lower()
        if not sep:
            out.append(
                f"`{clause}` needs the form `axis: what you verified` "
                f"(axes: {', '.join(AXES)}, or `none: why`)"
            )
            continue
        if not fold(note):
            out.append(f"`{key}` has no answer: say what you verified, not just the axis")
        elif key in _CITATION_KEYS:
            out += _citation_problems(note, root)
        elif key in AXES:
            if key not in answered:
                answered.append(key)
        else:
            out.append(
                f"unknown axis `{key}` (the vocabulary is {', '.join(AXES)}, "
                f"plus `rule:` and `none:`)"
            )
    if len(answered) < MIN_AXES:
        out.append(
            f"answer at least {MIN_AXES} axes of the contract's checklist "
            f"({', '.join(AXES)}); answered: {', '.join(answered) or 'none'}"
        )
    return out


def _none_problems(clauses):
    """`none: why` — the whole declaration, because a task either has a surface or it does not."""
    out = []
    if len(clauses) > 1:
        out.append(
            "`none:` answers the whole declaration, so drop the other clauses: "
            + "; ".join(clauses[1:])
        )
    reason = fold(clauses[0].partition(":")[2])
    if len(reason) < MIN_NONE_REASON:
        out.append(
            f"`none:` needs the reason ({MIN_NONE_REASON}+ characters) this task has no interface "
            "surface: a reader months later is the one it is for"
        )
    return out


def _citation_problems(note, root):
    cited = _citations(note)
    if not cited:
        return [
            "`rule:` needs at least one `<Category>/<Issue>` id from the corpus, "
            "e.g. `rule: Interaction/Focus States`"
        ]
    known = _corpus_ids(root)
    if known is None:  # not vendored here: nothing to check against, so nothing is claimed
        return []
    return [
        f"`rule: {one}` is not in {CORPUS}: cite an id that `scripts/design.sh --domain ux \"...\"` "
        "actually returned"
        for one in cited
        if one.casefold() not in known
    ]

"""The design declaration a change to the interface must carry into the archive.

`docs/interface-contract.md` is the law for what a new interface element must satisfy. Neither could make
anyone **say** what was checked: the contract's checklist was a paragraph nobody had to answer, so a change
closed without ever opening it left exactly the same trace as one that had been through all nine lines.

This module is the answering half. `todo.py done` refuses to archive a task without a declaration, and
the declaration is written in the contract's own words — the axes below are its checklist:

    material: capsule, standing in the ribbon's lane
    states: hover and focus added, the other three unchanged

Two answers is the floor (a single axis is a note, not a check), an axis naming no answer is refused,
and an unknown axis is refused with the vocabulary rather than silently ignored. `none: why` is the way
out for a task with no interface surface, and it keeps the reason in the archive beside what shipped.

It cannot read a screenshot. What it can do is make the question unskippable and keep the answer, which
is the most an archive can hold.
"""

from __future__ import annotations

# The contract's checklist, one word per line of it (`docs/interface-contract.md`, *Checklist*).
AXES = ("material", "tokens", "glyph", "states", "copy", "keyboard", "parity", "docs", "tests")

# Clause key for work with no UI surface.
_NONE_KEYS = ("none", "not-ui")

MIN_AXES = 2
MIN_NONE_REASON = 12
MAX_DECLARATION = 1200


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


def problems(text, root=None):
    """Everything wrong with a declaration; an empty list means it says what a closer has to say."""
    del root
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
        elif key in AXES:
            if key not in answered:
                answered.append(key)
        else:
            out.append(
                f"unknown axis `{key}` (the vocabulary is {', '.join(AXES)}, plus `none:`)"
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

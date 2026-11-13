#!/usr/bin/env python3
"""Check the independent `.mobix` reader against the specification and against `mobix-core`.

This is the script the `cli` job in `.github/workflows/build.yml` runs. It needs Python 3 and nothing
else — no Gradle, no JDK, no Android SDK — because the point of `tools/mobix-cli/` is that it is a
second implementation: a disagreement with the app must be a finding here rather than a shared bug.

It answers four questions, in order:

1. **The golden corpus is clean.** Every file in `fixtures/mobix-v1/` validates with no finding.
2. **Each hostile file is refused for its own reason.** `fixtures/mobix-hostile/` carries one file per
   threat; each must exit 1 naming its expected conformance row, except the truncated directory, which
   cannot even be opened (exit 2), and `00-smallest`, the legal neighbour, which must pass.
3. **The two implementations agree on the verdict.** `spec/corpus-verdicts.csv` records what
   `mobix-core` decides for every corpus file (`CorpusVerdictsTest` keeps that table honest); the CLI's
   answer must be the same one, so a rule added on one side alone fails here.
4. **The writer round-trips.** `new` writes a document that validates, and `retitle` leaves a document
   that still validates.

Failure output is the finding lines, not the archive. Usage:

    python3 scripts/check-mobix-cli.py
"""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CLI_DIR = ROOT / "tools" / "mobix-cli"
GOLDEN = ROOT / "mobix-core" / "src" / "test" / "resources" / "fixtures" / "mobix-v1"
HOSTILE = ROOT / "mobix-core" / "src" / "test" / "resources" / "fixtures" / "mobix-hostile"
VERDICTS = ROOT / "spec" / "corpus-verdicts.csv"

# One threat per file, each with the conformance row its refusal must name. `docs/mobix-format.md`
# owns the limits these rows come from; the hostile corpus README says what each file is.
EXPECTED_ROW = {
    "01-too-many-entries.mobix": "row-17",
    "02-deflate-bomb.mobix": "row-17",
    "03-deep-nesting.mobix": "row-17",
    "04-duplicate-entry.mobix": "row-18",
    "05-traversal-entry.mobix": "row-18",
    "06-wrong-digest.mobix": "row-04",
    "07-encrypted-entry.mobix": "row-20",
}

# The file cut before its central directory is not a document at all: the command reports it as a path
# it cannot open (exit 2), not as a document with a finding.
CANNOT_OPEN = {"08-truncated-directory.mobix"}

LEGAL_NEIGHBOUR = "00-smallest.mobix"


def run_cli(*arguments: object) -> subprocess.CompletedProcess[str]:
    """Run the CLI from its own directory, so it is the module a user runs and not a test import."""
    return subprocess.run(
        [sys.executable, "-m", "mobix_cli", *(str(argument) for argument in arguments)],
        cwd=CLI_DIR,
        capture_output=True,
        text=True,
        timeout=120,
    )


def findings_of(completed: subprocess.CompletedProcess[str]) -> list[dict]:
    try:
        report = json.loads(completed.stdout)
    except json.JSONDecodeError:
        return []
    return report.get("findings", []) if isinstance(report, dict) else []


def finding_lines(findings: list[dict], limit: int = 4) -> list[str]:
    return [
        f"    {item.get('rule', '?')}: {item.get('message', '')}"
        for item in findings[:limit]
        if isinstance(item, dict)
    ]


def verdict_of(path: Path) -> str:
    """What this reader decides: a clean report, or anything that stops it being accepted."""
    return "clean" if run_cli("validate", path, "--json").returncode == 0 else "refused"


def check_golden(problems: list[str]) -> None:
    for path in sorted(GOLDEN.glob("*.mobix")):
        completed = run_cli("validate", path, "--json")
        if completed.returncode != 0:
            problems.append(
                f"{path.name}: the golden corpus must validate clean, exited {completed.returncode}"
            )
            problems.extend(finding_lines(findings_of(completed)))


def check_hostile(problems: list[str]) -> None:
    for path in sorted(HOSTILE.glob("*.mobix")):
        if path.name == LEGAL_NEIGHBOUR:
            completed = run_cli("validate", path, "--json")
            if completed.returncode != 0:
                problems.append(f"{path.name}: the legal neighbour must be accepted")
                problems.extend(finding_lines(findings_of(completed)))
            continue

        completed = run_cli("validate", path, "--json")
        if path.name in CANNOT_OPEN:
            if completed.returncode != 2:
                problems.append(
                    f"{path.name}: expected exit 2 (not an archive this reader can open), "
                    f"got {completed.returncode}"
                )
            continue

        expected = EXPECTED_ROW[path.name]
        rows = {finding.get("rule") for finding in findings_of(completed)}
        if completed.returncode != 1 or expected not in rows:
            problems.append(
                f"{path.name}: expected exit 1 naming {expected}, "
                f"got exit {completed.returncode} with {sorted(row for row in rows if row)}"
            )
            problems.extend(finding_lines(findings_of(completed)))


def recorded_verdicts() -> dict[str, str]:
    table: dict[str, str] = {}
    for raw in VERDICTS.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        name, _, verdict = line.partition(",")
        table[name.strip()] = verdict.strip()
    return table


def check_differential(problems: list[str]) -> int:
    recorded = recorded_verdicts()
    files = {
        path.name: path
        for directory in (GOLDEN, HOSTILE)
        for path in sorted(directory.glob("*.mobix"))
    }

    for name in sorted(set(recorded) - set(files)):
        problems.append(f"{name}: spec/corpus-verdicts.csv names a file this repository does not carry")
    for name in sorted(set(files) - set(recorded)):
        problems.append(f"{name}: the verdict table does not name this corpus file")

    for name in sorted(set(recorded) & set(files)):
        decided = verdict_of(files[name])
        if decided != recorded[name]:
            problems.append(
                f"{name}: mobix-core records {recorded[name]}, the CLI says {decided}"
            )
    return len(files)


def check_writer(problems: list[str]) -> None:
    with tempfile.TemporaryDirectory() as temp:
        document = Path(temp) / "ci-round-trip.mobix"

        created = run_cli("new", document, "--title", "CI round-trip")
        if created.returncode != 0:
            problems.append(f"new: exited {created.returncode}: {created.stderr.strip()}")
            return

        validated = run_cli("validate", document, "--json")
        if validated.returncode != 0:
            problems.append("new: the document it wrote does not validate")
            problems.extend(finding_lines(findings_of(validated)))

        renamed = run_cli("retitle", document, "--title", "CI round-trip, renamed")
        if renamed.returncode != 0:
            problems.append(f"retitle: exited {renamed.returncode}: {renamed.stderr.strip()}")
            return

        validated = run_cli("validate", document, "--json")
        if validated.returncode != 0:
            problems.append("retitle: the rewritten document does not validate")
            problems.extend(finding_lines(findings_of(validated)))


def main() -> int:
    problems: list[str] = []
    check_golden(problems)
    check_hostile(problems)
    corpus_size = check_differential(problems)
    check_writer(problems)

    if problems:
        print("mobix-cli check failed:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1

    print(
        f"ok — {corpus_size} corpus files agree with mobix-core, "
        "the hostile corpus is refused for its own rows, and the writer round-trips"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

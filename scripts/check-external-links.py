#!/usr/bin/env python3
"""Check the external URLs the documentation points at.

The mandatory link checks live in `DocsGuardTest`: every relative link, `#anchor` and named file must
resolve, and a broken one fails the build. This script is the other half — the URLs on other hosts — and it
is deliberately **not** part of that gate, because a 429, a bot-blocking page or a flaky server says more
about the network than about the documentation.

What it does:

  * collects every `http(s)` URL from the prose of the docs (fenced code blocks skipped, since a URL there
    is an example rather than a reference);
  * asks each one, once, with a HEAD and a GET fallback;
  * classifies the answer, and separates "the page moved" and "we were not allowed to look" from "gone".

What it never does: edit a document. A URL is changed by a person who has opened it, which is the rule
`docs/doc-governance.md` states. The report is written under `build/reports/` so it is never committed.

    python scripts/check-external-links.py
    python scripts/check-external-links.py --fail-on-broken   # for a human, not for CI

Exit status is 0 unless `--fail-on-broken` is given, so a scheduled run reports instead of failing.
"""

from __future__ import annotations

import argparse
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

#: The prose that may carry a reference. Release notes are included: a download link there is a URL a
#: reader follows.
SOURCES = ["README.md", "AGENTS.md", "CONTRIBUTING.md"]
DIRECTORIES = ["docs"]

#: A URL, stopping at the characters that end one in Markdown prose.
URL = re.compile(r"https?://[^\s<>()\[\]`\"'|]+")

#: Punctuation that a sentence puts after a URL rather than in it.
TRAILING = ".,;:!?*_"

#: Hosts whose answer is not evidence about the document: badges are generated per request.
IGNORED_HOSTS = ("img.shields.io", "localhost", "127.0.0.1", "example.com", "example.org")

#: Statuses that mean "not looked at", not "gone". Each is a manual verification item.
MANUAL = {401, 403, 405, 407, 429, 451, 503}

USER_AGENT = "MobiXournal-doc-link-check/1.0 (+https://github.com/amarzano2005/MobiXournal)"


def markdown_files() -> list[Path]:
    files = [ROOT / name for name in SOURCES if (ROOT / name).is_file()]
    for directory in DIRECTORIES:
        base = ROOT / directory
        if base.is_dir():
            files.extend(sorted(base.rglob("*.md")))
    return files


def urls_in(path: Path) -> list[str]:
    """The URLs in one file's prose, in order, with duplicates kept so the source is attributable."""
    found: list[str] = []
    fenced = False
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.lstrip().startswith("```"):
            fenced = not fenced
            continue
        if fenced:
            continue
        for match in URL.finditer(line):
            found.append(match.group(0).rstrip(TRAILING))
    return found


def ask(url: str, timeout: float) -> tuple[str, str, str]:
    """(status, verdict, detail) for one URL. Never raises: a failure is an answer here."""
    request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
            final = response.geturl()
    except urllib.error.HTTPError as error:
        status = error.code
        final = url
        # Plenty of servers refuse HEAD and answer GET; ask again before calling it a refusal.
        if status in (403, 405, 501):
            try:
                with urllib.request.urlopen(
                    urllib.request.Request(url, headers={"User-Agent": USER_AGENT}),
                    timeout=timeout,
                ) as response:
                    status, final = response.status, response.geturl()
            except urllib.error.HTTPError as second:
                status, final = second.code, url
            except Exception:  # noqa: BLE001 - any transport failure is a manual check, not a verdict
                return "no response", "manual verification", "HEAD refused, GET failed"
    except Exception as error:  # noqa: BLE001 - see above: the network is not the document
        return "no response", "manual verification", type(error).__name__

    if status in MANUAL:
        return str(status), "manual verification", "the host refused or throttled an automated client"
    if status >= 400:
        return str(status), "broken", "the page is gone or errors"
    if final.rstrip("/") != url.rstrip("/"):
        return str(status), "moved", f"redirects to {final}"
    return str(status), "ok", ""


def main() -> int:
    parser = argparse.ArgumentParser(description="Check the documentation's external URLs.")
    parser.add_argument("--report", default="build/reports/external-links.md",
                        help="where the Markdown report is written (default: build/reports/external-links.md)")
    parser.add_argument("--timeout", type=float, default=10.0, help="seconds per request (default: 10)")
    parser.add_argument("--fail-on-broken", action="store_true",
                        help="exit 1 when a URL is broken; off by default so a schedule reports only")
    args = parser.parse_args()

    rows: list[tuple[str, str, str, str, str]] = []
    seen: set[tuple[str, str]] = set()
    for path in markdown_files():
        for url in urls_in(path):
            host = url.split("/")[2] if "//" in url else ""
            if any(ignored in host for ignored in IGNORED_HOSTS):
                continue
            key = (path.relative_to(ROOT).as_posix(), url)
            if key in seen:
                continue
            seen.add(key)
            status, verdict, detail = ask(url, args.timeout)
            rows.append((key[0], url, status, verdict, detail))
            print(f"{verdict:20} {status:14} {url}")

    checked = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    counts = {verdict: sum(1 for row in rows if row[3] == verdict)
              for verdict in ("ok", "moved", "broken", "manual verification")}

    lines = [
        "# External link report",
        "",
        f"Checked {len(rows)} URLs on {checked}.",
        "",
        "| Verdict | Count |",
        "|---|---|",
        *[f"| {verdict} | {count} |" for verdict, count in counts.items()],
        "",
        "**Manual verification** is not a failure: a 403, a 429, a timeout or a page that refuses an",
        "automated client says nothing certain about the document. **Broken** is a finding — open it",
        "yourself before changing anything, because a URL is the citation, not a redirect to be tidied.",
        "",
        "| Source | URL | Status | Verdict | Detail |",
        "|---|---|---|---|---|",
    ]
    for source, url, status, verdict, detail in rows:
        lines.append(f"| `{source}` | {url} | {status} | {verdict} | {detail} |")

    report = ROOT / args.report
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"\nreport: {report.relative_to(ROOT).as_posix()}")

    if args.fail_on_broken and counts["broken"]:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

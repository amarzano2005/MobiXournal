"""The two failure codes, over the hostile corpus and small archives built for one rule each."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

from cli_support import HOSTILE, PACKAGE_DIR, run

MIMETYPE = "application/vnd.mobixournal+zip"


def hostile(name: str) -> Path:
    return HOSTILE / name


def write_archive(path: Path, entries: dict[str, bytes], compression=zipfile.ZIP_DEFLATED) -> Path:
    with zipfile.ZipFile(path, "w", compression) as archive:
        for name, data in entries.items():
            archive.writestr(name, data)
    return path


def manifest(**overrides) -> bytes:
    base = {
        "formatVersion": 1,
        "documentId": "doc-test",
        "canvasMode": "PAGINATED",
        "pages": [{"id": "pg_1", "entry": "pages/page-0001.json"}],
    }
    base.update(overrides)
    return json.dumps(base).encode("utf-8")


class CannotOpenTest(unittest.TestCase):
    def test_a_missing_file_is_exit_2(self) -> None:
        code, _, err = run("inspect", "/definitely/not/here.mobix")

        self.assertEqual(code, 2)
        self.assertIn("cannot open", err)

    def test_a_text_file_is_exit_2(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "note.mobix"
            path.write_text("not a zip", encoding="utf-8")

            code, _, err = run("inspect", str(path))

        self.assertEqual(code, 2)
        self.assertIn("cannot open", err)

    def test_a_truncated_archive_is_exit_2(self) -> None:
        code, _, err = run("list", str(hostile("08-truncated-directory.mobix")))

        self.assertEqual(code, 2, err)


class HostileCorpusTest(unittest.TestCase):
    def test_the_legal_neighbour_is_readable(self) -> None:
        for command in ("inspect", "list"):
            with self.subTest(command=command):
                code, _, err = run(command, str(hostile("00-smallest.mobix")))
                self.assertEqual(code, 0, err)

    def test_too_many_entries_is_refused(self) -> None:
        code, _, err = run("list", str(hostile("01-too-many-entries.mobix")))

        self.assertEqual(code, 1)
        self.assertIn("20000", err)

    def test_deflate_bomb_is_refused_by_expansion(self) -> None:
        code, _, err = run("inspect", str(hostile("02-deflate-bomb.mobix")))

        self.assertEqual(code, 1)
        self.assertIn("expands", err)

    def test_deep_nesting_is_refused_when_parts_are_read(self) -> None:
        code, _, err = run("inspect", str(hostile("03-deep-nesting.mobix")))

        self.assertEqual(code, 1)
        self.assertIn("nests JSON deeper", err)

    def test_duplicate_entry_is_refused(self) -> None:
        code, _, err = run("list", str(hostile("04-duplicate-entry.mobix")))

        self.assertEqual(code, 1)
        self.assertIn("appears twice", err)

    def test_traversal_entry_is_refused(self) -> None:
        code, _, err = run("list", str(hostile("05-traversal-entry.mobix")))

        self.assertEqual(code, 1)
        self.assertIn("'..'", err)

    def test_wrong_digest_is_refused_when_digests_are_checked(self) -> None:
        code, _, err = run("inspect", str(hostile("06-wrong-digest.mobix")))

        self.assertEqual(code, 1)
        self.assertIn("hashes to", err)

    def test_encrypted_entry_is_refused(self) -> None:
        code, _, err = run("list", str(hostile("07-encrypted-entry.mobix")))

        self.assertEqual(code, 1)
        self.assertIn("encrypted", err)


class EnvelopeRefusalTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temp = tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.temp = Path(self._temp.name)
        self.page = b'{"widthPt":100.0,"heightPt":100.0,"layers":[]}'

    def build(self, entries: dict[str, bytes], **kwargs) -> Path:
        return write_archive(self.temp / "case.mobix", entries, **kwargs)

    def assert_refused(self, path: Path, reason: str) -> None:
        for command in ("inspect", "list"):
            with self.subTest(command=command):
                code, _, err = run(command, str(path))
                self.assertEqual(code, 1, err)
                self.assertIn(reason, err)

    def test_missing_mimetype_is_refused(self) -> None:
        path = self.build({"manifest.json": manifest()})
        self.assert_refused(path, "not a .mobix document")

    def test_wrong_mimetype_is_refused(self) -> None:
        path = self.build({"mimetype": b"application/zip", "manifest.json": manifest()})
        self.assert_refused(path, "not a .mobix document")

    def test_missing_manifest_is_refused(self) -> None:
        path = self.build({"mimetype": MIMETYPE.encode()})
        self.assert_refused(path, "no 'manifest.json'")

    def test_missing_required_manifest_field_is_refused(self) -> None:
        path = self.build(
            {
                "mimetype": MIMETYPE.encode(),
                "manifest.json": json.dumps(
                    {"documentId": "x", "canvasMode": "PAGINATED"}
                ).encode(),
            }
        )
        self.assert_refused(path, "formatVersion")

    def test_unknown_format_version_is_refused(self) -> None:
        path = self.build(
            {
                "mimetype": MIMETYPE.encode(),
                "manifest.json": manifest(formatVersion=2),
            }
        )
        self.assert_refused(path, "formatVersion 2")

    def test_unknown_canvas_mode_is_refused(self) -> None:
        path = self.build(
            {
                "mimetype": MIMETYPE.encode(),
                "manifest.json": manifest(canvasMode="SPIRAL"),
            }
        )
        self.assert_refused(path, "canvasMode")

    def test_both_part_lists_is_refused(self) -> None:
        path = self.build(
            {
                "mimetype": MIMETYPE.encode(),
                "manifest.json": manifest(chunks=[{"id": "c", "entry": "chunks/c.json"}]),
                "pages/page-0001.json": self.page,
                "chunks/c.json": self.page,
            }
        )
        self.assert_refused(path, "both 'pages' and 'chunks'")

    def test_part_naming_a_missing_entry_is_refused(self) -> None:
        path = self.build(
            {
                "mimetype": MIMETYPE.encode(),
                "manifest.json": manifest(pages=[{"id": "pg_1", "entry": "pages/gone.json"}]),
            }
        )
        self.assert_refused(path, "which the archive does not hold")

    def test_malformed_capability_is_refused(self) -> None:
        path = self.build(
            {
                "mimetype": MIMETYPE.encode(),
                "manifest.json": manifest(requiredCapabilities=["not a capability"]),
                "pages/page-0001.json": self.page,
            }
        )
        self.assert_refused(path, "capability")

    def test_canvas_mode_disagreement_is_refused(self) -> None:
        path = self.build(
            {
                "mimetype": MIMETYPE.encode(),
                "manifest.json": manifest(),
                "canvas.json": json.dumps({"mode": "INFINITE_2D"}).encode(),
                "pages/page-0001.json": self.page,
            }
        )
        self.assert_refused(path, "canvas.json says mode")

    def test_unsafe_entry_name_is_refused(self) -> None:
        path = self.build(
            {
                "mimetype": MIMETYPE.encode(),
                "manifest.json": manifest(),
                "pages/page-0001.json": self.page,
                "../escape.json": b"x",
            }
        )
        self.assert_refused(path, "'..'")

    def test_malformed_asset_name_is_refused(self) -> None:
        path = self.build(
            {
                "mimetype": MIMETYPE.encode(),
                "manifest.json": manifest(),
                "pages/page-0001.json": self.page,
                "assets/picture.png": b"x",
            }
        )
        self.assert_refused(path, "asset entry")


class RunnableTest(unittest.TestCase):
    def test_python_dash_m_runs_from_the_package_directory(self) -> None:
        fixture = "../../mobix-core/src/test/resources/fixtures/mobix-v1/01-minimal-single-page.mobix"
        completed = subprocess.run(
            [sys.executable, "-m", "mobix_cli", "inspect", fixture, "--json"],
            cwd=PACKAGE_DIR,
            capture_output=True,
            text=True,
            check=False,
        )

        self.assertEqual(completed.returncode, 0, completed.stderr)
        view = json.loads(completed.stdout)
        self.assertEqual(view["pages"], 1)


if __name__ == "__main__":
    unittest.main()

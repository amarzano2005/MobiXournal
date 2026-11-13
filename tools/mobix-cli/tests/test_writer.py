"""`new` and `retitle`: a clean document, a title-only rewrite, and determinism both ways."""

from __future__ import annotations

import json
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

from cli_support import GOLDEN, run, run_json


def fixture(name: str) -> Path:
    return GOLDEN / name


def read_entries(path: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(path) as archive:
        return {
            info.filename: archive.read(info)
            for info in archive.infolist()
            if not info.filename.endswith("/")
        }


class NewTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temp = tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.temp = Path(self._temp.name)
        self.out = self.temp / "created.mobix"

    def create(self, *extra: str, out: Path | None = None, title: str = "CLI acceptance"):
        return run(
            "new",
            str(out or self.out),
            "--title",
            title,
            *extra,
        )

    def test_creates_a_document_the_validator_accepts(self) -> None:
        code, _, err = self.create()

        self.assertEqual(code, 0, err)
        self.assertEqual(run("validate", str(self.out))[0], 0)

    def test_it_reports_the_families_it_produces_and_only_preserves(self) -> None:
        _, out, _ = self.create()

        self.assertIn("produced families: stroke, image", out)
        self.assertIn("preserved families (never rewritten)", out)
        for family in ("text", "texImage", "table", "plot", "parametricShape", "circuit", "mathBlock"):
            self.assertIn(family, out)

    def test_the_dump_shows_one_page_with_a_stroke_an_image_and_one_asset(self) -> None:
        self.assertEqual(self.create()[0], 0)
        code, out, err = run("export-json", str(self.out))
        dump = json.loads(out)

        self.assertEqual(code, 0, err)
        self.assertEqual(len(dump["pages"]), 1)
        self.assertEqual(
            {element["family"] for element in dump["pages"][0]["elements"]},
            {"stroke", "image"},
        )
        self.assertEqual(len(dump["assets"]), 1)
        asset = dump["assets"][0]
        self.assertEqual(asset["kind"], "image")
        self.assertTrue(asset["name"].startswith("assets/img_"))
        self.assertEqual(asset["digest"], asset["name"].split("_", 1)[1].split(".", 1)[0])

    def test_the_same_title_writes_the_same_bytes(self) -> None:
        first = self.temp / "first.mobix"
        second = self.temp / "second.mobix"

        self.assertEqual(self.create(out=first)[0], 0)
        self.assertEqual(self.create(out=second)[0], 0)
        self.assertEqual(first.read_bytes(), second.read_bytes())

    def test_a_different_title_is_a_different_document(self) -> None:
        other = self.temp / "other.mobix"

        self.assertEqual(self.create()[0], 0)
        self.assertEqual(self.create(out=other, title="Another")[0], 0)
        self.assertNotEqual(self.out.read_bytes(), other.read_bytes())
        self.assertNotEqual(
            json.loads(read_entries(other)["manifest.json"])["documentId"],
            json.loads(read_entries(self.out)["manifest.json"])["documentId"],
        )

    def test_an_existing_file_needs_force(self) -> None:
        self.out.write_bytes(b"keep me")

        code, _, err = self.create()

        self.assertEqual(code, 1)
        self.assertIn("--force", err)
        self.assertEqual(self.out.read_bytes(), b"keep me")
        self.assertEqual(self.create("--force")[0], 0)


class RetitleTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temp = tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.temp = Path(self._temp.name)

    def test_the_title_changes_and_every_other_entry_does_not(self) -> None:
        source = self.temp / "preserved.mobix"
        shutil.copyfile(fixture("05-unknown-entry.mobix"), source)
        before = read_entries(source)

        code, out, err = run("retitle", str(source), "--title", "A new title")

        self.assertEqual(code, 0, err)
        after = read_entries(source)
        self.assertEqual(set(after), set(before))
        self.assertEqual(json.loads(after["manifest.json"])["title"], "A new title")
        for name in before:
            if name != "manifest.json":
                self.assertEqual(after[name], before[name], name)
        self.assertIn("future/thing.bin", out)
        self.assertIn("index/search.idx", out)

    def test_the_rewritten_document_validates_clean(self) -> None:
        source = self.temp / "validated.mobix"
        shutil.copyfile(fixture("03-bundled-assets.mobix"), source)

        self.assertEqual(run("retitle", str(source), "--title", "Rinominato")[0], 0)

        self.assertEqual(run("validate", str(source))[0], 0)

    def test_unknown_envelope_fields_are_kept(self) -> None:
        source = self.temp / "unknown.mobix"
        shutil.copyfile(fixture("06-unknown-envelope-fields.mobix"), source)
        before = json.loads(read_entries(source)["manifest.json"])

        self.assertEqual(run("retitle", str(source), "--title", "Kept")[0], 0)

        after = json.loads(read_entries(source)["manifest.json"])
        self.assertEqual(set(after), set(before))
        self.assertEqual(after["title"], "Kept")
        for key in before:
            if key != "title":
                self.assertEqual(after[key], before[key], key)

    def test_an_unmodified_copy_produces_the_same_bytes(self) -> None:
        first = self.temp / "one.mobix"
        second = self.temp / "two.mobix"
        shutil.copyfile(fixture("01-minimal-single-page.mobix"), first)
        shutil.copyfile(fixture("01-minimal-single-page.mobix"), second)

        self.assertEqual(run("retitle", str(first), "--title", "Stesso")[0], 0)
        self.assertEqual(run("retitle", str(second), "--title", "Stesso")[0], 0)

        self.assertEqual(first.read_bytes(), second.read_bytes())

    def test_a_different_output_needs_force_but_in_place_does_not(self) -> None:
        source = self.temp / "source.mobix"
        destination = self.temp / "destination.mobix"
        shutil.copyfile(fixture("01-minimal-single-page.mobix"), source)
        destination.write_bytes(b"keep me")

        code, _, err = run(
            "retitle", str(source), "--title", "X", "--out", str(destination)
        )

        self.assertEqual(code, 1)
        self.assertIn("--force", err)
        self.assertEqual(destination.read_bytes(), b"keep me")
        self.assertEqual(
            run("retitle", str(source), "--title", "X", "--out", str(destination), "--force")[0],
            0,
        )
        self.assertEqual(
            json.loads(read_entries(destination)["manifest.json"])["title"], "X"
        )

    def test_a_file_mobixournal_saved_validates_clean(self) -> None:
        """The acceptance pair's first half: the app's own output, read by the CLI."""

        code, out, err = run("validate", str(fixture("18-every-family.mobix")))

        self.assertEqual(code, 0, err)
        self.assertIn("clean", out.lower())


if __name__ == "__main__":
    unittest.main()

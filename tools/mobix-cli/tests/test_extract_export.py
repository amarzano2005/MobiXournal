"""`extract` and `export-json`: path safety, caps, overwrite and a dump that diffs cleanly."""

from __future__ import annotations

import hashlib
import json
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

from cli_support import GOLDEN, run, run_json

MIMETYPE = "application/vnd.mobixournal+zip"
PAGE = "pages/page-0001.json"


def fixture(name: str) -> Path:
    return GOLDEN / name


def page_bytes() -> bytes:
    return json.dumps(
        {
            "widthPt": 100.0,
            "heightPt": 120.0,
            "layers": [{"elements": []}],
        },
        separators=(",", ":"),
    ).encode("utf-8")


def entries(page: bytes, *, digest: str = "") -> dict[str, bytes]:
    manifest = {
        "formatVersion": 1,
        "documentId": "doc-extract",
        "canvasMode": "PAGINATED",
        "pages": [{"id": "pg_1", "entry": PAGE, "sha256": digest}],
    }
    return {
        "mimetype": MIMETYPE.encode("ascii"),
        "manifest.json": json.dumps(manifest, separators=(",", ":")).encode(),
        PAGE: page,
    }


def write_archive(path: Path, files: dict[str, bytes]) -> Path:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in files.items():
            archive.writestr(name, data, compress_type=zipfile.ZIP_STORED if name == "mimetype" else zipfile.ZIP_DEFLATED)
    return path


class ExtractTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temp = tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.temp = Path(self._temp.name)
        self.dest = self.temp / "out"

    def test_one_page_is_written_with_the_archives_own_bytes(self) -> None:
        source = fixture("02-multi-page-order.mobix")
        code, view, err = run_json(
            "extract", str(source), "--dest", str(self.dest), "--entry", "pages/page-0003.json"
        )

        self.assertEqual(code, 0, err)
        written = view["written"][0]
        self.assertEqual(written["entry"], "pages/page-0003.json")
        target = self.dest / "pages" / "page-0003.json"
        self.assertTrue(target.is_file())
        with zipfile.ZipFile(source) as archive:
            self.assertEqual(target.read_bytes(), archive.read("pages/page-0003.json"))
        self.assertEqual(written["bytes"], target.stat().st_size)

    def test_one_asset_is_written_and_its_digest_is_verified(self) -> None:
        source = fixture("03-bundled-assets.mobix")
        with zipfile.ZipFile(source) as archive:
            asset = next(name for name in archive.namelist() if name.startswith("assets/img_"))

        code, view, err = run_json(
            "extract", str(source), "--dest", str(self.dest), "--entry", asset
        )

        self.assertEqual(code, 0, err)
        self.assertEqual(view["written"][0]["digest"], asset.split("_", 1)[1].split(".", 1)[0])

    def test_all_writes_every_file_entry(self) -> None:
        source = fixture("01-minimal-single-page.mobix")
        with zipfile.ZipFile(source) as archive:
            expected = [info.filename for info in archive.infolist() if not info.filename.endswith("/")]

        code, view, err = run_json("extract", str(source), "--dest", str(self.dest), "--all")

        self.assertEqual(code, 0, err)
        self.assertEqual([item["entry"] for item in view["written"]], expected)
        for name in expected:
            self.assertTrue((self.dest / name).is_file(), name)
        self.assertEqual(list(self.dest.rglob("*.part")), [])

    def test_no_selection_is_refused(self) -> None:
        code, _, err = run("extract", str(fixture("01-minimal-single-page.mobix")), "--dest", str(self.dest))

        self.assertEqual(code, 1)
        self.assertIn("no entries chosen", err)

    def test_an_entry_the_archive_lacks_is_refused(self) -> None:
        code, _, err = run(
            "extract",
            str(fixture("01-minimal-single-page.mobix")),
            "--dest",
            str(self.dest),
            "--entry",
            "pages/nope.json",
        )

        self.assertEqual(code, 1)
        self.assertIn("does not hold", err)

    def test_an_existing_target_needs_force(self) -> None:
        source = fixture("01-minimal-single-page.mobix")
        arguments = ("extract", str(source), "--dest", str(self.dest), "--entry", PAGE)

        self.assertEqual(run(*arguments)[0], 0)
        code, _, err = run(*arguments)
        self.assertEqual(code, 1)
        self.assertIn("--force", err)
        self.assertEqual(run(*arguments, "--force")[0], 0)

    def test_an_entry_over_the_cap_is_refused_and_leaves_nothing(self) -> None:
        source = fixture("01-minimal-single-page.mobix")
        with mock.patch("mobix_cli.extract.MAX_ENTRY_BYTES", 10):
            code, _, err = run("extract", str(source), "--dest", str(self.dest), "--entry", PAGE)

        self.assertEqual(code, 1)
        self.assertIn("cap", err)
        self.assertEqual(list(self.dest.rglob("*")), [])

    def test_a_traversal_entry_writes_nothing_outside(self) -> None:
        source = write_archive(self.temp / "traversal.mobix", entries(page_bytes()) | {"../../escape.txt": b"x"})
        outside = self.temp / "escape.txt"

        code, _, err = run("extract", str(source), "--dest", str(self.dest), "--all")

        self.assertEqual(code, 1, err)
        self.assertIn("'..'", err)
        self.assertFalse(outside.exists())
        self.assertFalse((self.temp.parent / "escape.txt").exists())

    def test_a_symlinked_target_is_refused(self) -> None:
        source = fixture("01-minimal-single-page.mobix")
        outside = self.temp / "outside"
        outside.mkdir()
        self.dest.mkdir()
        (self.dest / "pages").symlink_to(outside, target_is_directory=True)

        code, _, err = run("extract", str(source), "--dest", str(self.dest), "--entry", PAGE)

        self.assertEqual(code, 1, err)
        self.assertIn("symbolic link", err)
        self.assertEqual(list(outside.iterdir()), [])


class ExportJsonTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temp = tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.temp = Path(self._temp.name)

    def dump(self, path: Path) -> tuple[int, str, str]:
        return run("export-json", str(path))

    def test_the_dump_is_stable_across_two_copies_of_one_document(self) -> None:
        source = fixture("01-minimal-single-page.mobix")
        copy = self.temp / "copy.mobix"
        shutil.copyfile(source, copy)

        first, second = self.dump(source), self.dump(copy)

        self.assertEqual(first[0], 0, first[2])
        self.assertEqual(second[0], 0, second[2])
        self.assertEqual(first[1], second[1])

    def test_the_manifest_is_the_documents_own_object(self) -> None:
        source = fixture("06-unknown-envelope-fields.mobix")
        with zipfile.ZipFile(source) as archive:
            manifest = json.loads(archive.read("manifest.json"))

        code, out, err = self.dump(source)
        dump = json.loads(out)

        self.assertEqual(code, 0, err)
        self.assertEqual(dump["manifest"], manifest)
        self.assertEqual(dump["pages"][0]["entry"], manifest["pages"][0]["entry"])

    def test_element_ids_and_families_are_summarised(self) -> None:
        code, out, err = self.dump(fixture("18-every-family.mobix"))
        dump = json.loads(out)

        self.assertEqual(code, 0, err)
        families = {element["family"] for element in dump["pages"][0]["elements"]}
        self.assertTrue(
            {"stroke", "text", "image", "texImage", "table", "plot",
             "parametricShape", "circuit", "mathBlock"} <= families,
            families,
        )
        for element in dump["pages"][0]["elements"]:
            self.assertEqual(set(element), {"layer", "id", "family"})

    def test_an_unknown_element_family_is_named_not_dropped(self) -> None:
        code, out, err = self.dump(fixture("07-unknown-element-type.mobix"))

        self.assertEqual(code, 0, err)
        families = {element["family"] for element in json.loads(out)["pages"][0]["elements"]}
        self.assertIn("3dPlotElement", families)

    def test_assets_are_listed_by_name_and_size_only(self) -> None:
        code, out, err = self.dump(fixture("03-bundled-assets.mobix"))
        dump = json.loads(out)

        self.assertEqual(code, 0, err)
        self.assertEqual(len(dump["assets"]), 3)
        for asset in dump["assets"]:
            self.assertEqual(set(asset), {"name", "size", "digest", "kind"})
            self.assertIn(asset["kind"], ("image", "audio"))
            self.assertGreater(asset["size"], 0)
        self.assertEqual(
            [asset["name"] for asset in dump["assets"]],
            sorted(asset["name"] for asset in dump["assets"]),
        )

    def test_a_missing_file_is_exit_2(self) -> None:
        code, _, err = self.dump(Path("/definitely/not/here.mobix"))

        self.assertEqual(code, 2)
        self.assertIn("cannot open", err)

    def test_a_corrupt_part_is_refused(self) -> None:
        source = write_archive(
            self.temp / "bad.mobix",
            entries(b"not json", digest=hashlib.sha256(b"not json").hexdigest()),
        )

        code, _, err = self.dump(source)

        self.assertEqual(code, 1)
        self.assertIn("refused", err)


if __name__ == "__main__":
    unittest.main()

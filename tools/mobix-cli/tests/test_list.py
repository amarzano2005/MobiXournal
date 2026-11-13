"""`list` over the committed corpus: manifest order, central-directory sizes, names and digests."""

from __future__ import annotations

import unittest

from cli_support import GOLDEN, run, run_json


def fixture(name: str):
    return GOLDEN / name


class ListPagesTest(unittest.TestCase):
    def test_pages_follow_the_manifest_not_the_entry_names(self) -> None:
        code, view, err = run_json("list", str(fixture("02-multi-page-order.mobix")))

        self.assertEqual(code, 0, err)
        self.assertEqual([page["index"] for page in view["pages"]], [1, 2, 3])
        self.assertEqual(
            [page["id"] for page in view["pages"]], ["pg_7", "pg_2", "pg_9"]
        )
        self.assertEqual(
            [page["entry"] for page in view["pages"]],
            ["pages/page-0003.json", "pages/page-0001.json", "pages/page-0002.json"],
        )
        for page in view["pages"]:
            self.assertEqual(len(page["digest"]), 64)
            self.assertGreater(page["size"], 0)

    def test_a_minimal_document_lists_one_page_and_no_assets(self) -> None:
        view = run_json("list", str(fixture("01-minimal-single-page.mobix")))[1]

        self.assertEqual(len(view["pages"]), 1)
        self.assertEqual(view["pages"][0]["entry"], "pages/page-0001.json")
        self.assertEqual(view["chunks"], [])
        self.assertEqual(view["assets"], [])

    def test_human_report_lists_the_index_and_the_digest(self) -> None:
        code, out, err = run("list", str(fixture("02-multi-page-order.mobix")))

        self.assertEqual(code, 0, err)
        self.assertIn("pages (3)", out)
        self.assertIn("pages/page-0003.json", out)
        self.assertIn("sha256:", out)


class ListChunksAndAssetsTest(unittest.TestCase):
    def test_chunks_are_listed_by_manifest_index(self) -> None:
        view = run_json("list", str(fixture("10-chunked-document.mobix")))[1]

        self.assertEqual(view["pages"], [])
        self.assertEqual(
            [chunk["entry"] for chunk in view["chunks"]],
            ["chunks/chunk_0_0.json", "chunks/chunk_-1_2.json"],
        )
        self.assertEqual([chunk["index"] for chunk in view["chunks"]], [1, 2])

    def test_assets_carry_the_digest_their_name_claims(self) -> None:
        view = run_json("list", str(fixture("03-bundled-assets.mobix")))[1]

        self.assertEqual(len(view["assets"]), 3)
        for asset in view["assets"]:
            prefix, rest = asset["name"].split("/")[1].split("_", 1)
            self.assertIn(prefix, ("img", "aud"))
            self.assertEqual(asset["digest"], rest.split(".", 1)[0])
            self.assertEqual(len(asset["digest"]), 64)


class ListIsAListingTest(unittest.TestCase):
    def test_list_does_not_inflate_parts_so_a_wrong_digest_is_only_reported(self) -> None:
        path = "mobix-core/src/test/resources/fixtures/mobix-hostile/06-wrong-digest.mobix"

        code, view, err = run_json("list", str(path))

        self.assertEqual(code, 0, err)
        self.assertEqual(len(view["pages"]), 1)
        self.assertEqual(len(view["pages"][0]["digest"]), 64)


if __name__ == "__main__":
    unittest.main()

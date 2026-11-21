from __future__ import annotations

import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "check-doc-quality.sh"


class DocQualityCommandTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        destination = self.root / "scripts/docs/check-doc-quality.sh"
        destination.parent.mkdir(parents=True)
        shutil.copy2(SCRIPT, destination)
        (self.root / "docs").mkdir()
        self.git("init", "--quiet")
        self.git("-c", "user.name=Doc Test", "-c", "user.email=doc@example.invalid",
                 "commit", "--quiet", "--allow-empty", "-m", "initial")

    def git(self, *arguments):
        return subprocess.run(
            ["git", *arguments], cwd=self.root, check=True,
            capture_output=True, text=True,
        )

    def write(self, relative, text):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def check(self):
        result = subprocess.run(
            ["bash", "scripts/docs/check-doc-quality.sh"], cwd=self.root,
            capture_output=True, text=True, timeout=15,
        )
        reports = list((self.root / "target/doc-quality").glob("doc-quality-*.md"))
        self.assertEqual(len(reports), 1, result.stderr)
        return result, reports[0].read_text(encoding="utf-8")

    def test_current_design_directory_passes(self):
        self.write("docs/README.md", "[设计](design/current.md)\n参考 docs/design/current.md\n")
        self.write("docs/design/current.md", "# 当前设计\n")
        result, report = self.check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("**过时路径引用**: 0", report)

    def test_legacy_design_reference_fails_with_current_destination(self):
        self.write("docs/README.md", "[旧设计](/docs/design-docs/old.md)\n")
        result, report = self.check()
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("**过时路径引用**: 1", report)
        self.assertIn("`docs/design-docs/` → `docs/design/`", report)

    def test_fixed_commit_links_and_historical_path_mentions_are_not_current_links(self):
        self.write("docs/history.md", "旧目录为 `docs/design-docs/`。\n"
                   "[原文](https://example.invalid/repo/blob/abc/docs/design-docs/old.md)\n")
        result, report = self.check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("**过时路径引用**: 0", report)

    def test_frozen_handbook_and_generated_dependencies_are_excluded(self):
        for directory in ("handbook", "node_modules", "target"):
            self.write(f"docs/{directory}/old.md", "docs/design-docs/old.md /.worktrees/old/\n")
        self.write("docs/current.md", "# 当前正文\n")
        result, report = self.check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("**总文档数**: 1", report)

    def test_empty_document_set_does_not_divide_by_zero(self):
        result, report = self.check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("**总文档数**: 0", report)

    def test_deleted_recent_documents_do_not_count_as_current_updates(self):
        self.write("docs/design/deleted.md", "# 旧正文\n")
        self.git("add", "docs/design/deleted.md")
        self.git("-c", "user.name=Doc Test", "-c", "user.email=doc@example.invalid",
                 "commit", "--quiet", "-m", "add document")
        (self.root / "docs/design/deleted.md").unlink()
        self.write("docs/current.md", "# 未提交正文\n")
        result, report = self.check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("**30 天内更新**: 0", report)
        self.assertIn("**超过 30 天**: 1", report)


if __name__ == "__main__":
    unittest.main()

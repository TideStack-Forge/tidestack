from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import link_check as checker


def write(root: Path, relative: str, text: str) -> Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path


class GithubSlugTest(unittest.TestCase):
    def test_numbered_and_dotted_headings(self):
        self.assertEqual(checker.github_slug("## 1. 分层约束"), "1-分层约束")
        self.assertEqual(
            checker.github_slug("### 3.4 人员档案表单方案的设计动机与边界"),
            "34-人员档案表单方案的设计动机与边界",
        )

    def test_cjk_punctuation_is_dropped(self):
        self.assertEqual(
            checker.github_slug("## 2. 同源 Populate（JOIN 策略）"),
            "2-同源-populatejoin-策略",
        )

    def test_underscore_is_preserved_and_backticks_are_stripped(self):
        self.assertEqual(
            checker.github_slug("## 四、P1：FULL_PROCESS 首次待办查询不创建本地任务绑定"),
            "四p1full_process-首次待办查询不创建本地任务绑定",
        )
        self.assertEqual(
            checker.github_slug("## `preflight-backend-release` 阶段"), "preflight-backend-release-阶段"
        )

    def test_collect_headings_returns_all_levels(self):
        text = "# 标题\n\n正文\n\n## 1. 分层约束\n\n### 1.1 层级定义\n"
        self.assertEqual(checker.collect_headings(text), {"标题", "1-分层约束", "11-层级定义"})


class LineAnchorTest(unittest.TestCase):
    def test_line_anchor_forms(self):
        self.assertTrue(checker.is_line_anchor("L12"))
        self.assertTrue(checker.is_line_anchor("L12-L20"))
        self.assertFalse(checker.is_line_anchor("12"))
        self.assertFalse(checker.is_line_anchor("l12"))
        self.assertFalse(checker.is_line_anchor("分层约束"))


class CheckTreeTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        write(self.root, "docs/target.md", "# 目标页\n\n## 1. 分层约束\n\n第一行\n第二行\n")
        write(
            self.root,
            "docs/source.md",
            "\n".join(
                [
                    "# 源页",
                    "",
                    "## 1. 分层约束",
                    "",
                    "- 有效相对链接：[目标](target.md)",
                    "- 有效跨文件锚点：[目标 §1](target.md#1-分层约束)",
                    "- 有效同文件锚点：[本页 §1](#1-分层约束)",
                    "- 有效根路径链接：[目标](/docs/target.md)",
                    "- 有效行锚点：[目标 L3](target.md#L3)",
                    "- 失效目标：[不存在](missing.md)",
                    "- 失效跨文件锚点：[目标 §2](target.md#2-不存在的小节)",
                    "- 失效同文件锚点：[本页 §9](#9-没有这一节)",
                    "- 越界行锚点：[目标 L999](target.md#L999)",
                    "- 外部链接忽略：[官网](https://example.com/none)",
                    "",
                ]
            ),
        )

    def tearDown(self):
        self._tmp.cleanup()

    def test_only_broken_entries_are_reported(self):
        _, issues = checker.run(self.root)
        reported = sorted((issue.kind, issue.link) for issue in issues)
        self.assertEqual(
            reported,
            [
                (checker.ANCHOR, "#9-没有这一节"),
                (checker.ANCHOR, "target.md#2-不存在的小节"),
                (checker.ANCHOR, "target.md#L999"),
                (checker.MISSING, "missing.md"),
            ],
        )

    def test_every_markdown_file_is_checked_once(self):
        checked, _ = checker.run(self.root)
        self.assertEqual(checked, 2)

    def test_excluded_workspace_dirs_are_not_scanned(self):
        write(self.root, "work/scratch.md", "[坏链](nowhere.md)\n")
        write(self.root, "tmp/testing-evidence/export/README.md", "[坏链](nowhere.md)\n")
        checked, issues = checker.run(self.root)
        self.assertEqual(checked, 2)
        self.assertFalse([issue for issue in issues if issue.source.startswith("work/")])
        self.assertFalse([issue for issue in issues if issue.source.startswith("tmp/")])
        self.assertNotIn("nowhere.md", [issue.link for issue in issues])


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Markdown 链接与锚点检查（check-links.sh 的实现）。

文档检查不进 CI：本脚本由本地按需运行，或由发版前预检 scripts/ci/preflight-backend-release.sh 调用。

一次扫描同时校验两件事：
1. 链接目标存在（相对路径按所在文件目录解析，`/` 开头按仓库根解析）；
2. 锚点有效：`.md` 目标按 GitHub/GitLab 标题 slug 校验，`#L12` 形式按目标文件行数校验。

用法：
  python3 scripts/docs/link_check.py [仓库根]

测试：
  python3 -m unittest discover -s scripts/docs/tests -p 'test_*.py'

退出码：0 = 全部通过；1 = 存在失效链接或锚点。
"""

from __future__ import annotations

import re
import sys
from collections import namedtuple
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

# 扫描面只覆盖产品文档（docs/ 与各模块的 *.md）。以下目录刻意排除：
#   .codestable/ .omo/ .sisyphus/ .firecrawl/ work/ tmp/ —— 工具与工作区产物，不是文档体系的一部分
#   docs/handbook/                                —— 独立文档站点，链接按站点路由解析（/docs/... 等），不是文件路径
EXCLUDED_PATH_PARTS = (
    "/.git/",
    "/node_modules/",
    "/target/",
    "/.worktrees/",
    "/.codestable/",
    "/.omo/",
    "/.sisyphus/",
    "/.firecrawl/",
    "/work/",
    "/tmp/",
    "/docs/handbook/",
)

LINK_PATTERN = re.compile(r"\[[^]]*\]\(([^)]+)\)")
HEADING_PATTERN = re.compile(r"^(#{1,6})\s+(.*)$")
LINE_ANCHOR_PATTERN = re.compile(r"^L\d+(-L\d+)?$")
EXTERNAL_PREFIXES = ("http://", "https://", "mailto:", "tel:", "data:", "javascript:")

Issue = namedtuple("Issue", "kind source link detail")
MISSING = "missing"
ANCHOR = "anchor"


def github_slug(heading: str) -> str:
    """标题锚点：小写、去格式符与标点、空格转连字符；下划线按 GitHub 规则保留。"""
    text = re.sub(r"[`*]", "", heading.strip().lower())
    text = re.sub(r"[^\w\u4e00-\u9fff \-]", "", text)
    return text.strip().replace(" ", "-")


def collect_headings(text: str) -> set[str]:
    anchors = set()
    for line in text.splitlines():
        match = HEADING_PATTERN.match(line)
        if match:
            anchors.add(github_slug(match.group(2)))
    return anchors


def is_line_anchor(anchor: str) -> bool:
    return bool(LINE_ANCHOR_PATTERN.match(anchor))


def iter_links(text: str):
    for match in LINK_PATTERN.finditer(text):
        yield match.group(1)


def markdown_files(root: Path):
    for path in sorted(root.rglob("*.md")):
        marker = "/" + path.relative_to(root).as_posix()
        if any(part in marker for part in EXCLUDED_PATH_PARTS):
            continue
        yield path


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def _relative(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return str(path)


def _resolve(source: Path, body: str, root: Path) -> Path:
    if body.startswith("/"):
        return root / body.lstrip("/")
    return source.parent / body


def check_file(path: Path, root: Path, anchor_cache: dict) -> list[Issue]:
    issues: list[Issue] = []
    source = _relative(path, root)
    text = _read(path)
    for raw in sorted(set(iter_links(text))):
        if not raw or raw.startswith(EXTERNAL_PREFIXES):
            continue
        body, _, anchor = raw.partition("#")
        if body:
            target = _resolve(path, body, root)
            if not target.exists():
                issues.append(Issue(MISSING, source, raw, ""))
                continue
        else:
            target = path
        if not anchor:
            continue
        if is_line_anchor(anchor):
            line_count = len(_read(target).splitlines())
            first_line = int(anchor[1:].split("-")[0])
            if first_line > line_count:
                issues.append(
                    Issue(ANCHOR, source, raw, f"{_relative(target, root)} 只有 {line_count} 行")
                )
            continue
        if target.suffix.lower() != ".md":
            continue
        if target not in anchor_cache:
            anchor_cache[target] = collect_headings(_read(target))
        if anchor not in anchor_cache[target]:
            issues.append(
                Issue(ANCHOR, source, raw, f"{_relative(target, root)} 无标题锚点 #{anchor}")
            )
    return issues


def run(root: Path) -> tuple[int, list[Issue]]:
    checked = 0
    issues: list[Issue] = []
    anchor_cache: dict = {}
    for path in markdown_files(root):
        checked += 1
        issues.extend(check_file(path, root, anchor_cache))
    return checked, issues


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else REPO_ROOT
    print(f"Checking markdown links in {root}")

    checked, issues = run(root)

    for issue in issues:
        if issue.kind == MISSING:
            print(f"BROKEN: {issue.source} -> {issue.link}")
        else:
            print(f"BROKEN ANCHOR: {issue.source} -> {issue.link}（{issue.detail}）")

    print()
    print(f"Checked {checked} markdown files")

    if issues:
        missing = sum(1 for issue in issues if issue.kind == MISSING)
        anchors = len(issues) - missing
        print(f"Found {len(issues)} broken links（缺失目标 {missing} 处，锚点失效 {anchors} 处）")
        return 1

    print("No broken links found")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

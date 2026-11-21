#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: export-data-model.sh --manifest <path> --target <path> --mode dry-run|sync [--dry-run]

Creates a local public data-model tree from the manifest. This command never
pushes, publishes, merges, releases, deploys, or contacts a remote.
USAGE
}

manifest=""
target=""
mode="dry-run"
dry_run_flag="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --manifest)
      manifest="${2:-}"
      shift 2
      ;;
    --target)
      target="${2:-}"
      shift 2
      ;;
    --mode)
      mode="${2:-}"
      shift 2
      ;;
    --dry-run)
      dry_run_flag="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$manifest" || -z "$target" ]]; then
  usage >&2
  exit 2
fi

if [[ "$mode" != "dry-run" && "$mode" != "sync" ]]; then
  echo "--mode must be dry-run or sync" >&2
  exit 2
fi

python3 - "$manifest" "$target" "$mode" "$dry_run_flag" <<'PY'
import datetime as _dt
import hashlib
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path


MANIFEST = Path(sys.argv[1]).resolve()
TARGET = Path(sys.argv[2]).resolve()
MODE = sys.argv[3]
DRY_RUN_FLAG = sys.argv[4] == "true"
ROOT = Path.cwd().resolve()


def fail(message, code=1):
    print(message, file=sys.stderr)
    sys.exit(code)


def run(args, cwd=None, check=False, env=None):
    proc_env = os.environ.copy()
    if env:
        proc_env.update(env)
    proc = subprocess.run(
        args,
        cwd=str(cwd or ROOT),
        env=proc_env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check and proc.returncode != 0:
        fail(proc.stdout + proc.stderr)
    return proc


def scalar(value):
    value = value.strip()
    if not value:
        return ""
    if value == "[]":
        return []
    if value.startswith("[") and value.endswith("]"):
        body = value[1:-1].strip()
        if not body:
            return []
        return [scalar(part) for part in body.split(",")]
    if (value.startswith('"') and value.endswith('"')) or (value.startswith("'") and value.endswith("'")):
        return value[1:-1]
    return value


def parse_manifest(path):
    sections = {}
    current = None
    current_item = None
    last_key = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip(" "))
        text = raw.strip()
        if indent == 0 and text.endswith(":"):
            current = text[:-1]
            sections.setdefault(current, [])
            current_item = None
            last_key = None
            continue
        if current is None:
            continue
        if indent == 2 and text.startswith("- "):
            body = text[2:]
            item = {}
            if ":" in body:
                key, value = body.split(":", 1)
                item[key.strip()] = scalar(value)
                last_key = key.strip()
            else:
                item["value"] = scalar(body)
                last_key = "value"
            sections[current].append(item)
            current_item = item
            continue
        if current_item is not None and indent >= 4:
            if text.startswith("- "):
                current_item.setdefault(last_key, [])
                if isinstance(current_item[last_key], list):
                    current_item[last_key].append(scalar(text[2:]))
                continue
            if ":" in text:
                key, value = text.split(":", 1)
                key = key.strip()
                current_item[key] = scalar(value)
                last_key = key
    return sections


def safe_reset_target(target):
    if str(target) in {"/", "/tmp", "/private/tmp", str(Path.home())}:
        fail(f"Refusing unsafe target: {target}")
    if (target / ".git").exists():
        fail(f"Refusing to replace a Git repository: {target}; export to a separate candidate directory")
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)


def ignore_public(dir_name, entries):
    ignored = {
        ".git",
        ".codestable",
        ".worktrees",
        ".ai-workflows",
        ".sisyphus",
        ".gstack",
        ".idea",
        ".m2",
        "target",
        "tmp",
        "logs",
        "log",
        "node_modules",
        "testing-evidence",
        "__pycache__",
    }
    return {entry for entry in entries if entry in ignored or entry.endswith(".pyc") or entry == ".flattened-pom.xml"}


def copy_path(rel):
    src = ROOT / rel
    dst = TARGET / rel
    if not src.exists():
        return False
    dst.parent.mkdir(parents=True, exist_ok=True)
    if src.is_dir():
        shutil.copytree(src, dst, ignore=ignore_public)
    else:
        shutil.copy2(src, dst)
    return True


def replace_modules(rel, modules):
    path = TARGET / rel
    if not path.exists():
        fail(f"Cannot rewrite missing POM: {rel}")
    text = path.read_text(encoding="utf-8")
    module_xml = "\n".join(f"        <module>{module}</module>" for module in modules)
    replacement = f"<modules>\n{module_xml}\n    </modules>"
    text, count = re.subn(r"<modules>.*?</modules>", replacement, text, flags=re.S)
    if count != 1:
        fail(f"Expected one <modules> block in {rel}, found {count}")
    path.write_text(text, encoding="utf-8")


def retain_bom_internal_artifacts(rel, allowed_artifacts):
    path = TARGET / rel
    if not path.exists():
        fail(f"Cannot rewrite missing BOM: {rel}")
    text = path.read_text(encoding="utf-8")

    def keep_or_remove(match):
        artifact_id = match.group(1)
        return match.group(0) if artifact_id in allowed_artifacts else ""

    text = re.sub(
        r"\s*<dependency>\s*<groupId>com\.ouroboros</groupId>\s*<artifactId>([^<]+)</artifactId>.*?</dependency>",
        keep_or_remove,
        text,
        flags=re.S,
    )
    path.write_text(text, encoding="utf-8")


def remove_build_outputs(root):
    names = {"target", "tmp", "logs", "log", "__pycache__"}
    for current, dirs, files in os.walk(root, topdown=False):
        current_path = Path(current)
        for name in list(dirs):
            if name in names:
                shutil.rmtree(current_path / name, ignore_errors=True)
        for name in files:
            if name == ".flattened-pom.xml" or name.endswith(".pyc"):
                try:
                    (current_path / name).unlink()
                except FileNotFoundError:
                    pass


def write_text(rel, content):
    path = TARGET / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def markdown_list(values):
    return "\n".join(f"- `{value}`" for value in values) or "- none"


def scrub_public_report(text):
    scrubbed = str(text)
    replacements = [
        (str(TARGET), "$PUBLIC_TREE"),
        (str(ROOT), "$INTERNAL_REPO"),
        (str(Path.home()), "$HOME"),
    ]
    try:
        replacements.append((str(MANIFEST.relative_to(ROOT)), "internal export manifest"))
    except ValueError:
        replacements.append((str(MANIFEST), "internal export manifest"))
    for needle, replacement in replacements:
        if needle:
            scrubbed = scrubbed.replace(needle, replacement)
    return scrubbed


def markdown_table(rows, headers):
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join(["---"] * len(headers)) + " |",
    ]
    for row in rows:
        lines.append("| " + " | ".join(row) + " |")
    return "\n".join(lines)


def iter_public_files(root):
    ignored_dirs = {".git", "target", "tmp", "logs", "log", "node_modules", "__pycache__"}
    ignored_suffixes = {".class", ".jar", ".zip", ".gz", ".png", ".jpg", ".jpeg", ".gif", ".ico"}
    for current, dirs, files in os.walk(root):
        dirs[:] = [name for name in dirs if name not in ignored_dirs]
        current_path = Path(current)
        for file_name in files:
            if any(file_name.endswith(suffix) for suffix in ignored_suffixes):
                continue
            yield current_path / file_name


def scan_paths(paths, patterns):
    matches = []
    for rel in paths:
        path = TARGET / rel
        candidates = []
        if path.is_dir():
            candidates = list(iter_public_files(path))
        elif path.exists():
            candidates = [path]
        for file_path in candidates:
            text = file_path.read_text(encoding="utf-8", errors="ignore")
            for pattern in patterns:
                if pattern in text:
                    matches.append(f"{file_path.relative_to(TARGET)}: {pattern}")
    return matches


def scan_public_dependency_violations(root, allowed_artifacts):
    matches = []
    for pom_path in root.rglob("pom.xml"):
        if any(part in {".git", "target"} for part in pom_path.parts):
            continue
        text = pom_path.read_text(encoding="utf-8", errors="ignore")
        for dependency in re.finditer(r"<dependency>.*?</dependency>", text, flags=re.S):
            block = dependency.group(0)
            group = re.search(r"<groupId>\s*com\.ouroboros\s*</groupId>", block)
            artifact = re.search(r"<artifactId>\s*([^<]+)\s*</artifactId>", block)
            if group and artifact and artifact.group(1) not in allowed_artifacts:
                matches.append(f"{pom_path.relative_to(root)}: com.ouroboros:{artifact.group(1)}")
    return matches


def resolve_mockito_agent():
    local_repo = Path.home() / ".m2/repository/org/mockito/mockito-core"
    local_agents = list(local_repo.glob("*/mockito-core-*.jar"))
    if local_agents:
        def version_key(path):
            parts = re.split(r"[^0-9]+", path.parent.name)
            return tuple(int(part) for part in parts if part.isdigit())
        return str(sorted(local_agents, key=version_key)[-1])

    classpath_file = TARGET / "target/public-test-classpath.txt"
    classpath_file.parent.mkdir(parents=True, exist_ok=True)
    command = [
        "mvn",
        "-q",
        "-Ppublic",
        "-pl",
        "core/ouroboros-data/ouroboros-data-core",
        "-am",
        "-DincludeScope=test",
        f"-Dmdep.outputFile={classpath_file}",
        "dependency:build-classpath",
    ]
    proc = run(command, cwd=TARGET)
    if proc.returncode != 0:
        fail("Unable to resolve public test classpath:\n" + proc.stdout + proc.stderr)
    entries = classpath_file.read_text(encoding="utf-8").split(os.pathsep)
    agents = [
        entry for entry in entries
        if "/org/mockito/mockito-core/" in entry and entry.endswith(".jar")
    ]
    if not agents:
        fail("Unable to locate mockito-core jar in public test classpath")
    return agents[-1]


if not MANIFEST.exists():
    fail(f"Manifest not found: {MANIFEST}")

source_revision = run(["git", "rev-parse", "--verify", "HEAD"], cwd=ROOT)
if source_revision.returncode != 0 and MODE == "sync":
    fail("A source commit is required before synchronizing repositories")
internal_ref = source_revision.stdout.strip() if source_revision.returncode == 0 else "unborn-working-tree"

manifest_text = MANIFEST.read_bytes()
manifest_hash = hashlib.sha256(manifest_text).hexdigest()
public_manifest_label = "internal export manifest (not copied)"
public_tree_label = "public-tree-local-fixture"
manifest = parse_manifest(MANIFEST)
included_items = [item for item in manifest.get("included_modules", []) if item.get("path")]
included = [item["path"] for item in included_items]
included_ids = [item["id"] for item in included_items if item.get("id")]
split_sources = manifest.get("split_source_modules", [])

safe_reset_target(TARGET)

support_paths = [
    ".gitignore",
    "pom.xml",
    "docs/examples/automation-checks/checkstyle.xml",
    "docs/examples/automation-checks/pmd-ruleset.xml",
    "bom",
    "core/pom.xml",
    "core/ouroboros-data/pom.xml",
    "libs/pom.xml",
]

copied = []
missing = []
support_modules = manifest.get("support_modules", [])
support_inputs = [
    item["path"] + "/pom.xml" if item.get("aggregator_only") == "true" else item["path"]
    for item in support_modules
]
for rel in support_paths + support_inputs + included:
    if rel in copied:
        continue
    if copy_path(rel):
        copied.append(rel)
    else:
        missing.append(rel)

if missing:
    fail("Missing required export paths:\n" + "\n".join(missing))

ignore_path = TARGET / ".gitignore"
ignore_path.write_text(
    "\n".join(line for line in ignore_path.read_text().splitlines() if ".codestable" not in line) + "\n",
    encoding="utf-8",
)

replace_modules("pom.xml", ["bom", "libs", "core"])
replace_modules("libs/pom.xml", ["util/ouroboros-util", "common"])
replace_modules("libs/common/pom.xml", [
    Path(item["path"]).name for item in support_modules
    if Path(item["path"]).parent == Path("libs/common")
])
replace_modules("core/pom.xml", ["ouroboros-data"])

data_modules = [Path(path).name for path in included]
replace_modules("core/ouroboros-data/pom.xml", data_modules)
public_support_artifacts = {
    "ouroboros-bom",
}.union(item["artifact_id"] for item in support_modules)
allowed_public_artifacts = public_support_artifacts.union(
    item.get("artifact_id", Path(item["path"]).name) for item in included_items
)
retain_bom_internal_artifacts(
    "bom/pom.xml",
    allowed_public_artifacts,
)
public_dependency_violations = scan_public_dependency_violations(TARGET, allowed_public_artifacts)

public_build_modules = [path for path in included if (TARGET / path / "pom.xml").exists()]
public_build_selector = ",".join(public_build_modules)
public_validation_command_text = "mvn -Ppublic -pl core/ouroboros-data -am test"
public_module_build_command_text = f"mvn -Ppublic -pl {public_build_selector} -am -DskipTests package"

module_rows = [
    [
        item.get("id", ""),
        item.get("path", ""),
        f"`com.ouroboros:{item.get('artifact_id', Path(item['path']).name)}:${{project.version}}`",
    ]
    for item in included_items
]
module_table = markdown_table(module_rows, ["Module", "Path", "Draft coordinate"])

readme = f"""# Ouroboros Data Model

This repository is a local public export fixture for the Ouroboros dynamic data
model. It is generated from the internal monorepo by the manifest-scoped export
workflow and contains synthetic Git history only.

- Export manifest: `{public_manifest_label}`
- Manifest SHA-256: `{manifest_hash}`
- Mode: `{MODE}`
- License: Apache License 2.0; see `LICENSE`.
- Non-automatic actions: no remote push, merge, publish, release, deploy, or promotion.

## Public Profile

Run the public profile smoke test and manifest-selected module package build:

```bash
{public_validation_command_text}
{public_module_build_command_text}
```

The public profile is a build and verification surface, not a publishing
promise. Maven coordinates below are draft coordinates for review.

{module_table}

## Documentation

- Public API entry: [`docs/public-api.md`](docs/public-api.md)
- Contribution guide: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Adapter integration guide: [`docs/adapter-integration-guide.md`](docs/adapter-integration-guide.md)
- Minimal example: [`docs/examples/minimal-data-model.md`](docs/examples/minimal-data-model.md)
- Data module overview: [`core/ouroboros-data/README.md`](core/ouroboros-data/README.md)

## Contribution Policy

External contributions are accepted with DCO sign-off. CLA is not required for
the public export fixture generated by this workflow.

## Primary Key Boundary

`data-pkgen-coding` is part of the first public profile. It uses the
`DataExpressionEvaluator` port from `ouroboros-data-core`; concrete expression
engines, DB-backed sequence behavior, script wrappers and snowflake runtime
adapters remain external adapter concerns.

## Release Gates

`release-gate-report.md` records the local license, sensitive-content, public
API, CI-template, and primary-key documentation gates produced by the export.
"""
write_text("README.md", readme)
write_text("README.en.md", readme)
readme = f"""# Ouroboros Data Model

本仓库是 Ouroboros 动态数据模型的公开投影。它由内部 monorepo 按 manifest 作用域导出，公开仓库历史从首次开源发布重新开始，不携带内部 Git 历史。

English documentation: [`README.en.md`](README.en.md)

- 导出 manifest：`{public_manifest_label}`
- Manifest SHA-256：`{manifest_hash}`
- 导出模式：`{MODE}`
- 开源协议：Apache License 2.0，见 `LICENSE`
- 非自动动作：本导出流程不会自动执行 remote push、merge、publish、release、deploy 或 promotion

## 公开构建面

运行 public profile 冒烟测试和 manifest 选中模块的打包验证：

```bash
{public_validation_command_text}
{public_module_build_command_text}
```

public profile 是构建和校验边界，不代表 Maven 发布承诺。下方坐标仅作为公开评审草案。

{module_table}

## 文档

- 公开 API 入口：[`docs/public-api.md`](docs/public-api.md) / [`docs/public-api.en.md`](docs/public-api.en.md)
- 贡献指南：[`CONTRIBUTING.md`](CONTRIBUTING.md) / [`CONTRIBUTING.en.md`](CONTRIBUTING.en.md)
- 适配器接入指南：[`docs/adapter-integration-guide.md`](docs/adapter-integration-guide.md) / [`docs/adapter-integration-guide.en.md`](docs/adapter-integration-guide.en.md)
- 最小示例：[`docs/examples/minimal-data-model.md`](docs/examples/minimal-data-model.md) / [`docs/examples/minimal-data-model.en.md`](docs/examples/minimal-data-model.en.md)
- 数据模块总览：[`core/ouroboros-data/README.md`](core/ouroboros-data/README.md) / [`core/ouroboros-data/README.en.md`](core/ouroboros-data/README.en.md)

## 贡献策略

外部贡献需要 DCO sign-off。当前公开投影流程不要求 CLA。

## 主键边界

`data-pkgen-coding` 属于首批公开 profile。它通过 `ouroboros-data-core` 中的 `DataExpressionEvaluator` 端口接入表达式能力；具体表达式引擎、数据库序列、脚本包装和雪花算法运行时适配器都留在外部 adapter 中。

## 发布门禁

`release-gate-report.md` 记录本地开源协议、敏感内容、公开 API、CI 模板和主键文档门禁结果。
"""
write_text("README.md", readme)

license_text = """                                 Apache License
                           Version 2.0, January 2004
                        https://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. Do not include
      the brackets! The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] TideStack

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
"""
write_text("LICENSE", license_text)

notice = """Ouroboros Data Model
Copyright (c) TideStack.

This product includes software developed by the Ouroboros project.
"""
write_text("NOTICE", notice)

contributing = """# Contributing to Ouroboros Data Model

Thank you for improving the public data model project. This repository is a
public projection of the internal Ouroboros monorepo, so maintainers import
accepted changes back through the manifest-scoped sync workflow.

## Development Baseline

- Java 21 is required.
- Run `mvn -Ppublic -pl core/ouroboros-data -am test` before submitting a change.
- Keep public modules free of platform imports such as security, lifecycle,
  script, application, or internal runtime glue.
- Add or update tests for behavioral changes.

## Public Boundary Rules

- Use `DataExpressionEvaluator` from `ouroboros-data-core` for expression needs;
  do not depend on a concrete expression engine from a platform module.
- Keep DB-backed sequence implementations, snowflake adapters and platform
  lifecycle bridges outside `data-pkgen-coding`.
- Keep runtime/application adapters out of core, SQL, typed, builder and test
  support modules.
- Do not add generated files, local build output, private metadata or internal
  repository paths.

## Pull Request Checklist

- [ ] The change is limited to public modules or public documentation.
- [ ] `mvn -Ppublic -pl core/ouroboros-data -am test` passes.
- [ ] No internal platform imports were added.
- [ ] Contributions are signed off with DCO.
- [ ] Public documentation was updated for API or adapter behavior changes.
"""
write_text("CONTRIBUTING.md", contributing)
write_text("CONTRIBUTING.en.md", contributing)
contributing = """# 参与贡献 Ouroboros Data Model

感谢你改进公开数据模型项目。本仓库是内部 Ouroboros monorepo 的公开投影，维护者会通过 manifest 作用域同步流程把被接受的变更回流到内部仓库。

English documentation: [`CONTRIBUTING.en.md`](CONTRIBUTING.en.md)

## 开发基线

- 需要 Java 21。
- 提交前运行 `mvn -Ppublic -pl core/ouroboros-data -am test`。
- 公开模块不得引入 security、lifecycle、script、application 或内部 runtime glue 等平台依赖。
- 行为变更必须新增或更新测试。

## 公开边界规则

- 表达式能力通过 `ouroboros-data-core` 的 `DataExpressionEvaluator` 端口接入，不依赖具体平台表达式引擎。
- DB 序列实现、雪花算法适配器和平台生命周期桥接不得放入 `data-pkgen-coding`。
- runtime/application adapter 不进入 core、SQL、typed、builder 和 test-support 公开模块。
- 不提交生成产物、本地构建输出、私有元数据或内部仓库路径。

## Pull Request 检查清单

- [ ] 变更只触及公开模块或公开文档。
- [ ] `mvn -Ppublic -pl core/ouroboros-data -am test` 通过。
- [ ] 没有新增内部平台 import。
- [ ] 提交带有 DCO sign-off。
- [ ] API 或 adapter 行为变化已更新公开文档。
"""
write_text("CONTRIBUTING.md", contributing)

public_api = f"""# Public API Entry

This page is the public documentation entry for the dynamic data model export.
It intentionally describes only the manifest-selected public surface.

## Included Modules

{module_table}

## API Surface

- `ouroboros-data-core` owns model metadata, statements, data station contracts,
  expression ports, migration contracts, and generic primary-key SPI.
- `ouroboros-data-typed-core` owns typed annotations/model contracts.
- `ouroboros-data-typed-meta-processor` owns typed metadata annotation
  processing.
- `ouroboros-data-sql` owns SQL query and mutation execution and explicitly
  composes `ouroboros-data-sql-migration` to preserve existing migration APIs.
- `ouroboros-data-sql-migration` owns schema-changing migration implementation
  and does not depend back on the SQL query module.
- `ouroboros-data-pkgen-coding` owns the coding primary-key factory and uses
  the expression port defined by `ouroboros-data-core`.
- `ouroboros-data-builders` is deprecated; use query facades in data-core and
  model metadata APIs. `ouroboros-data-test-support` provides public test helpers.

## Deferred Or Runtime-Owned Surface

- `data-pkgen-coding` is public, but DB-backed sequence behavior remains
  runtime-owned and must be provided by an adapter through `CodingSequencer`.
- Snowflake-specific concrete algorithms remain runtime-owned. If a public
  snowflake contract becomes necessary, it should be introduced as a dedicated
  interface-only module rather than folded into coding primary-key contracts.
- Typed model runtime has its own public/deferred decision path and is not
  described as part of util or exception support.

## Build

```bash
{public_validation_command_text}
{public_module_build_command_text}
```
"""
write_text("docs/public-api.md", public_api)
write_text("docs/public-api.en.md", public_api)
public_api = f"""# 公开 API 入口

本页是动态数据模型公开投影的 API 文档入口，只描述 manifest 选中的公开边界。

English documentation: [`public-api.en.md`](public-api.en.md)

## 包含模块

{module_table}

## API 边界

- `ouroboros-data-core` 负责模型元数据、statement、data station contract、表达式端口、迁移 contract 和通用主键 SPI。
- `ouroboros-data-typed-core` 负责 typed annotation 与模型 contract。
- `ouroboros-data-typed-meta-processor` 负责 typed metadata 的 annotation processing。
- `ouroboros-data-sql` 负责 SQL 查询与写入执行，并显式组合 `ouroboros-data-sql-migration` 以保留既有迁移 API。
- `ouroboros-data-sql-migration` 负责 schema-changing migration 实现，不反向依赖 SQL 查询模块。
- `ouroboros-data-pkgen-coding` 负责编码主键工厂，并使用 `ouroboros-data-core` 定义的表达式端口。
- `ouroboros-data-builders` 已弃用；新代码使用 data-core 查询门面与模型元数据 API。`ouroboros-data-test-support` 提供公开测试辅助能力。

## 暂缓或 runtime-owned 边界

- `data-pkgen-coding` 是公开模块，但 DB 序列状态仍由 runtime adapter 通过 `CodingSequencer` 提供。
- 雪花算法的具体实现仍由 runtime 持有；如果未来需要公开 contract，应作为独立 interface-only 模块引入。
- typed model runtime 有独立的公开/暂缓决策路径，不属于 util 或 exception support 范围。

## 构建

```bash
{public_validation_command_text}
{public_module_build_command_text}
```
"""
write_text("docs/public-api.md", public_api)

adapter_guide = """# Adapter Integration Guide

Ouroboros Data Model keeps the public core platform-neutral. Runtime behavior
is supplied by adapters through public contracts instead of internal platform
dependencies.

## Adapter Points

| Adapter point | Public contract | Required when | Integration approach |
| --- | --- | --- | --- |
| Expression evaluation | `com.ouroboros.data.expression.DataExpressionEvaluator` | Default values, coding primary keys or plugins evaluate expressions. | Provide an SPI implementation in `META-INF/services/com.ouroboros.data.expression.DataExpressionEvaluator`. Keep the expression engine dependency in the adapter module. |
| Data station/runtime | `com.ouroboros.data.model.DataStation` and related model contracts | Applications need to execute model CRUD/query behavior. | Build a runtime module that wires a station implementation to storage, transactions and application lifecycle outside the public core. |
| SQL migration | `com.ouroboros.data.migration.SqlMigrationService` | Applications need schema changes from data model metadata. | Depend on `ouroboros-data-sql-migration` and invoke migration services from the application's deployment/runtime layer. |
| Coding primary-key sequence | `com.ouroboros.data.pkgenerator.CodingSequencer` | `ouroboros-data-pkgen-coding` templates use `<0001>` or named sequences. | Provide a sequencer backed by your storage or sequence service and inject it into `FixLengthCodingGeneratorFactory`. |
| Typed metadata generation | `ouroboros-data-typed-core` annotations plus `ouroboros-data-typed-meta-processor` | Projects want compile-time generation of data model metadata from typed Java classes. | Add the processor to annotation processing and commit generated metadata only when it is part of your public source policy. |
| Test fixtures | `ouroboros-data-test-support` | Public adapters need repeatable tests without platform runtime glue. | Use public fixtures and H2/Spring test support; do not import internal monorepo test modules. |

## Expression Adapter

Implement `DataExpressionEvaluator` when an application needs a concrete
expression language. The public modules only know the port:

```java
public final class MyExpressionEvaluator implements DataExpressionEvaluator {
  @Override
  public Either<Throwable, Object> evaluate(String expression, Map<String, Object> context) {
    // Delegate to the expression engine owned by the adapter.
  }
}
```

Register the implementation:

```text
META-INF/services/com.ouroboros.data.expression.DataExpressionEvaluator
```

## Coding Primary-Key Adapter

`ouroboros-data-pkgen-coding` parses templates and delegates sequence state to
`CodingSequencer`. Keep persistence, distributed locks and tenant-specific
sequence policies in your adapter.

## Runtime Boundary

Public modules should not depend on platform lifecycle, security, script or app
modules. Put those concerns in an adapter that consumes public contracts and can
be tested independently.
"""
write_text("docs/adapter-integration-guide.md", adapter_guide)
write_text("docs/adapter-integration-guide.en.md", adapter_guide)
adapter_guide = """# Adapter 接入指南

Ouroboros Data Model 的公开 core 保持平台中立。运行时行为通过公开 contract 由 adapter 提供，而不是依赖内部平台模块。

English documentation: [`adapter-integration-guide.en.md`](adapter-integration-guide.en.md)

## Adapter 点位

| Adapter 点位 | 公开 contract | 何时需要 | 接入方式 |
| --- | --- | --- | --- |
| 表达式求值 | `com.ouroboros.data.expression.DataExpressionEvaluator` | 默认值、编码主键或插件需要计算表达式。 | 在 `META-INF/services/com.ouroboros.data.expression.DataExpressionEvaluator` 注册 SPI 实现，把具体表达式引擎留在 adapter 模块。 |
| Data station/runtime | `com.ouroboros.data.model.DataStation` 及相关模型 contract | 应用需要执行模型 CRUD/query 行为。 | 在公开 core 外构建 runtime 模块，把 station 实现接到存储、事务和应用生命周期。 |
| SQL migration | `com.ouroboros.data.migration.SqlMigrationService` | 应用需要根据数据模型元数据变更 schema。 | 依赖 `ouroboros-data-sql-migration`，由应用部署/runtime 层调用迁移服务。 |
| 编码主键序列 | `com.ouroboros.data.pkgenerator.CodingSequencer` | `ouroboros-data-pkgen-coding` 模板使用 `<0001>` 或命名序列。 | 提供基于存储或序列服务的 sequencer，并注入 `FixLengthCodingGeneratorFactory`。 |
| Typed metadata 生成 | `ouroboros-data-typed-core` annotation 与 `ouroboros-data-typed-meta-processor` | 项目希望从 typed Java class 编译期生成数据模型元数据。 | 配置 annotation processor；仅在公开源码策略要求时提交生成元数据。 |
| 测试 fixture | `ouroboros-data-test-support` | 公开 adapter 需要不依赖平台 runtime glue 的可重复测试。 | 使用公开 fixture 与 H2/Spring 测试支撑，不导入内部 monorepo 测试模块。 |

## 表达式 Adapter

应用需要具体表达式语言时，实现 `DataExpressionEvaluator`。公开模块只依赖端口：

```java
public final class MyExpressionEvaluator implements DataExpressionEvaluator {
  @Override
  public Either<Throwable, Object> evaluate(String expression, Map<String, Object> context) {
    // Delegate to the expression engine owned by the adapter.
  }
}
```

注册文件：

```text
META-INF/services/com.ouroboros.data.expression.DataExpressionEvaluator
```

## 编码主键 Adapter

`ouroboros-data-pkgen-coding` 负责解析模板，并把序列状态委托给 `CodingSequencer`。持久化、分布式锁和租户级序列策略应保留在 adapter 中。

## Runtime 边界

公开模块不依赖平台生命周期、安全、脚本或 app 模块。相关能力应放在消费公开 contract 的 adapter 中，并可独立测试。
"""
write_text("docs/adapter-integration-guide.md", adapter_guide)

example = """# Minimal Dynamic Data Model Example

This example is intentionally configuration-level so it can stay stable while
the public API surface is finalized.

```yaml
dataModel:
  name: sample_user
  station: sql
  table: sample_user
  fields:
    - name: id
      valueType: string
      primaryKey: true
    - name: display_name
      valueType: string
query:
  select:
    - id
    - display_name
  from: sample_user
  where:
    display_name: Ada
```

Use `ouroboros-data-core` for model metadata and contracts, and add a public
runtime adapter only after the runtime split decision marks it as public.
"""
write_text("docs/examples/minimal-data-model.md", example)
write_text("docs/examples/minimal-data-model.en.md", example)
example = """# 最小动态数据模型示例

这个示例刻意保持在配置层，便于在公开 API 边界继续稳定前保持可读。

English documentation: [`minimal-data-model.en.md`](minimal-data-model.en.md)

```yaml
dataModel:
  name: sample_user
  station: sql
  table: sample_user
  fields:
    - name: id
      valueType: string
      primaryKey: true
    - name: display_name
      valueType: string
query:
  select:
    - id
    - display_name
  from: sample_user
  where:
    display_name: Ada
```

使用 `ouroboros-data-core` 承载模型元数据和 contract。runtime adapter 只有在 runtime split 决策进入公开范围后才应加入公开构建面。
"""
write_text("docs/examples/minimal-data-model.md", example)

data_readme = f"""# Ouroboros Data Public Modules

This directory contains the public dynamic data model build surface selected by
the export manifest.

## Build

```bash
{public_validation_command_text}
{public_module_build_command_text}
```

## Module Boundary

- `ouroboros-data-core`: public contracts, metadata, statements, ports, and
  platform-neutral behavior.
- `ouroboros-data-sql`: SQL query and mutation execution.
- `ouroboros-data-sql-migration`: schema migration implementation and
  `SqlMigrationService` tool surface, consumed explicitly by SQL/test support
  while keeping schema-changing code isolated.
- `ouroboros-data-typed-core`: typed annotations and model contracts.
- `ouroboros-data-typed-meta-processor`: typed metadata annotation processor.
- `ouroboros-data-pkgen-coding`: coding primary-key template parser and factory
  backed by the `DataExpressionEvaluator` and `CodingSequencer` public ports.
- `ouroboros-data-builders`: deprecated builder helpers; use data-core APIs.
- `ouroboros-data-test-support`: public test fixtures and helpers.

Runtime-backed sequence implementations, script wrappers and snowflake adapters
are outside the public coding module and must be supplied by adapters.
"""
write_text("core/ouroboros-data/README.md", data_readme)
write_text("core/ouroboros-data/README.en.md", data_readme)
data_readme = f"""# Ouroboros Data 公开模块

本目录包含 export manifest 选中的动态数据模型公开构建面。

English documentation: [`README.en.md`](README.en.md)

## 构建

```bash
{public_validation_command_text}
{public_module_build_command_text}
```

## 模块边界

- `ouroboros-data-core`：公开 contract、元数据、statement、port 与平台中立行为。
- `ouroboros-data-sql`：SQL 查询和写入执行。
- `ouroboros-data-sql-migration`：schema migration 实现与 `SqlMigrationService` tool surface，由 SQL/test support 显式消费，同时隔离 schema-changing 代码。
- `ouroboros-data-typed-core`：typed annotation 与模型 contract。
- `ouroboros-data-typed-meta-processor`：typed metadata annotation processor。
- `ouroboros-data-pkgen-coding`：编码主键模板解析与 factory，依赖 `DataExpressionEvaluator` 和 `CodingSequencer` 公开端口。
- `ouroboros-data-builders`：已弃用的 builder 辅助能力，新代码使用 data-core API。
- `ouroboros-data-test-support`：公开测试 fixture 与辅助能力。

runtime-backed 序列实现、脚本包装和雪花算法 adapter 不属于公开 coding 模块，必须由 adapter 提供。
"""
write_text("core/ouroboros-data/README.md", data_readme)

ci = f"""name: Public CI

on:
  push:
  pull_request:

jobs:
  public-profile:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven
      - name: Resolve Mockito javaagent
        run: |
          mkdir -p target
          mvn -q -Ppublic -pl core/ouroboros-data/ouroboros-data-core -am -DincludeScope=test -Dmdep.outputFile=target/public-test-classpath.txt dependency:build-classpath
          python3 - <<'PY'
          import os
          entries = open("target/public-test-classpath.txt", encoding="utf-8").read().split(os.pathsep)
          agents = [entry for entry in entries if "/org/mockito/mockito-core/" in entry and entry.endswith(".jar")]
          if not agents:
              raise SystemExit("mockito-core jar not found")
          with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as env:
              env.write("JDK_JAVA_OPTIONS=-javaagent:" + agents[-1] + "\\n")
          PY
      - name: Run public profile smoke test
        run: {public_validation_command_text}
      - name: Package manifest-selected public modules
        run: {public_module_build_command_text}
"""
write_text(".github/workflows/public-ci.yml", ci)

validation_command = ["mvn", "-Ppublic", "-pl", "core/ouroboros-data", "-am", "test"]
module_build_command = ["mvn", "-Ppublic", "-pl", public_build_selector, "-am", "-DskipTests", "package"]
mockito_agent = resolve_mockito_agent()
build_env = {
    "JDK_JAVA_OPTIONS": (os.environ.get("JDK_JAVA_OPTIONS", "") + f" -javaagent:{mockito_agent}").strip()
}
validation_build = run(validation_command, cwd=TARGET, env=build_env)
module_build = run(module_build_command, cwd=TARGET, env=build_env)
build_passed = validation_build.returncode == 0 and module_build.returncode == 0
remove_build_outputs(TARGET)

pkgen_path = TARGET / "core/ouroboros-data/ouroboros-data-pkgen-coding"
pkgen_projected = pkgen_path.exists()
pkgen_forbidden = []
if pkgen_projected:
    patterns = [
        "DBCodingSequencer",
        "CodeGenELAndScriptContext",
        "SnowflakePrimaryKeyGeneratorFactory",
        "Snowflakes",
        "common-expression",
        "com.ouroboros.expression",
        "com.querydsl",
        "com.ouroboros.data.pkgenerator.model",
    ]
    for current, _, files in os.walk(pkgen_path):
        for file_name in files:
            if not (file_name.endswith(".java") or file_name == "pom.xml"):
                continue
            file_path = Path(current) / file_name
            text = file_path.read_text(encoding="utf-8", errors="ignore")
            for pattern in patterns:
                if pattern in text:
                    pkgen_forbidden.append(f"{file_path.relative_to(TARGET)}: {pattern}")

secret_patterns = [
    ("private-key", re.compile(r"BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY")),
    ("aws-access-key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("assigned-sensitive-value", re.compile(r"(?im)^\s*(secret|password|token)\s*[:=]\s*['\"]?[A-Za-z0-9_./+=-]{8,}['\"]?\s*$")),
]
secret_matches = []
for public_file in iter_public_files(TARGET):
    text = public_file.read_text(encoding="utf-8", errors="ignore")
    for label, pattern in secret_patterns:
        if pattern.search(text):
            secret_matches.append(f"{public_file.relative_to(TARGET)}: {label}")

public_docs_forbidden = scan_paths(
    ["README.md", "docs", "core/ouroboros-data"],
    ["DBCodingSequencer", "Snowflakes", "SnowflakePrimaryKeyGeneratorFactory"],
)
public_profile_files = [
    ".github/workflows/public-ci.yml",
    "README.md",
    "README.en.md",
    "LICENSE",
    "NOTICE",
    "CONTRIBUTING.md",
    "CONTRIBUTING.en.md",
    "docs/public-api.md",
    "docs/public-api.en.md",
    "docs/adapter-integration-guide.md",
    "docs/adapter-integration-guide.en.md",
    "docs/examples/minimal-data-model.md",
    "docs/examples/minimal-data-model.en.md",
    "core/ouroboros-data/README.md",
    "core/ouroboros-data/README.en.md",
]
missing_public_profile_files = [rel for rel in public_profile_files if not (TARGET / rel).exists()]
release_gate_passed = not secret_matches and not public_docs_forbidden and not missing_public_profile_files

release_report = f"""# Public Release Gate Report

## Summary

- Status: {'passed' if release_gate_passed else 'failed'}
- Generated at: `{_dt.datetime.now(_dt.timezone.utc).isoformat()}`
- Apache-2.0 LICENSE present: `{str((TARGET / 'LICENSE').exists()).lower()}`
- NOTICE present: `{str((TARGET / 'NOTICE').exists()).lower()}`
- CONTRIBUTING present: `{str((TARGET / 'CONTRIBUTING.md').exists()).lower()}`
- English CONTRIBUTING present: `{str((TARGET / 'CONTRIBUTING.en.md').exists()).lower()}`
- Public CI template present: `{str((TARGET / '.github/workflows/public-ci.yml').exists()).lower()}`
- Public API docs present: `{str((TARGET / 'docs/public-api.md').exists()).lower()}`
- English public API docs present: `{str((TARGET / 'docs/public-api.en.md').exists()).lower()}`
- Adapter integration guide present: `{str((TARGET / 'docs/adapter-integration-guide.md').exists()).lower()}`
- English adapter integration guide present: `{str((TARGET / 'docs/adapter-integration-guide.en.md').exists()).lower()}`
- Minimal example present: `{str((TARGET / 'docs/examples/minimal-data-model.md').exists()).lower()}`
- English minimal example present: `{str((TARGET / 'docs/examples/minimal-data-model.en.md').exists()).lower()}`

## Owner Decision Points

- Final repository owner: pending.
- Final license choice: pending.
- Maven publishing coordinates: draft only.
- Remote publication, merge, release, deploy, and promotion: not authorized.

## Sensitive Content Scan

- Matches: `{len(secret_matches)}`

{markdown_list(secret_matches)}

## Public Documentation Guard

- Forbidden runtime implementation mentions: `{len(public_docs_forbidden)}`

{markdown_list(public_docs_forbidden)}

## Missing Public Profile Files

{markdown_list(missing_public_profile_files)}
"""
write_text("release-gate-report.md", release_report)

generated_at = _dt.datetime.now(_dt.timezone.utc).isoformat()
report_title = "Public Forward Sync Dry Run Report" if MODE == "sync" else "Public Export Dry Run Report"
projection_commit_message = "Sync public data model projection" if MODE == "sync" else "Initial public data model dry-run export"
state_commit_message = "Record public data model sync state" if MODE == "sync" else "Record public export dry-run state"
mockito_agent_report = scrub_public_report(mockito_agent)
validation_stdout_tail = scrub_public_report(validation_build.stdout[-3000:])
validation_stderr_tail = scrub_public_report(validation_build.stderr[-3000:])
module_stdout_tail = scrub_public_report(module_build.stdout[-3000:])
module_stderr_tail = scrub_public_report(module_build.stderr[-3000:])

report = f"""# {report_title}

## Summary

- Status: {'passed' if build_passed and not public_dependency_violations and not pkgen_forbidden and release_gate_passed else 'failed'}
- Generated at: `{generated_at}`
- Mode: `{MODE}`
- Dry run flag: `{DRY_RUN_FLAG}`
- Source ref recorded in public tree: `false`
- Manifest: `{public_manifest_label}`
- Manifest SHA-256: `{manifest_hash}`
- Target: `{public_tree_label}`
- Remote actions: none

## Forward Sync Policy

- Policy: `manifest-scoped-forward-sync`
- Public history policy: `orphan-from-first-public-release`
- Internal commit messages copied: no
- Projection commit message: `{projection_commit_message}`
- Target branch action: dry-run only
- Remote push / merge / publish / release / deploy: not performed

## Included Modules

{markdown_list(included)}

## Public Build Inputs

{markdown_list([path for path in copied if path not in included])}

## Public Dependency Guard

- Result: `{'passed' if not public_dependency_violations else 'failed'}`
- Allowed public artifacts:

{markdown_list(sorted(allowed_public_artifacts))}

- Out-of-scope internal dependencies:

{markdown_list(public_dependency_violations)}

## Public Profile Build

- Mockito javaagent: `{mockito_agent_report}`
- Result: `{'passed' if build_passed else 'failed'}`

### Public profile smoke test

- Command: `{' '.join(validation_command)}`
- Exit code: `{validation_build.returncode}`

#### stdout tail

```text
{validation_stdout_tail}
```

#### stderr tail

```text
{validation_stderr_tail}
```

### Manifest module package build

- Command: `{' '.join(module_build_command)}`
- Exit code: `{module_build.returncode}`

#### stdout tail

```text
{module_stdout_tail}
```

#### stderr tail

```text
{module_stderr_tail}
```

## History Policy

- Internal `.git` copied: no
- Public history: generated locally by this script after projection
- Internal `.codestable` copied: no

## Pkgen Projection Guard

- `data-pkgen-coding` projected: `{str(pkgen_projected).lower()}`
- Forbidden matches: `{len(pkgen_forbidden)}`

{markdown_list(pkgen_forbidden)}

## Public Metadata, CI, And Release Gates

- README generated: `{str((TARGET / 'README.md').exists()).lower()}`
- English README generated: `{str((TARGET / 'README.en.md').exists()).lower()}`
- Apache-2.0 LICENSE generated: `{str((TARGET / 'LICENSE').exists()).lower()}`
- NOTICE generated: `{str((TARGET / 'NOTICE').exists()).lower()}`
- CONTRIBUTING generated: `{str((TARGET / 'CONTRIBUTING.md').exists()).lower()}`
- English CONTRIBUTING generated: `{str((TARGET / 'CONTRIBUTING.en.md').exists()).lower()}`
- Public CI template generated: `{str((TARGET / '.github/workflows/public-ci.yml').exists()).lower()}`
- API docs entry generated: `{str((TARGET / 'docs/public-api.md').exists()).lower()}`
- English API docs entry generated: `{str((TARGET / 'docs/public-api.en.md').exists()).lower()}`
- Adapter integration guide generated: `{str((TARGET / 'docs/adapter-integration-guide.md').exists()).lower()}`
- English adapter integration guide generated: `{str((TARGET / 'docs/adapter-integration-guide.en.md').exists()).lower()}`
- Minimal example generated: `{str((TARGET / 'docs/examples/minimal-data-model.md').exists()).lower()}`
- English minimal example generated: `{str((TARGET / 'docs/examples/minimal-data-model.en.md').exists()).lower()}`
- Release gate report: `release-gate-report.md`
- Release gate result: `{'passed' if release_gate_passed else 'failed'}`
- Sensitive content matches: `{len(secret_matches)}`
- Forbidden public docs matches: `{len(public_docs_forbidden)}`

## Cleanup

- Build output directories removed after build: yes
- Ignored private/runtime directories: `.git`, `.codestable`, `target`, `tmp`, logs, node_modules, AI worktrees
"""
write_text("export-report.md", report)

if build_passed and not public_dependency_violations and not pkgen_forbidden and release_gate_passed:
    run(["git", "init", "-q", "-b", "master"], cwd=TARGET, check=True)
    if MODE == "sync":
        git_user_name = os.environ.get("PUBLIC_GIT_USER_NAME", "TideStack Open Source Maintainers")
        git_user_email = os.environ.get("PUBLIC_GIT_USER_EMAIL", "opensource@tidestack.org")
    else:
        git_user_name = "Ouroboros Public Export Dry Run"
        git_user_email = "public-export-dry-run@example.invalid"
    run(["git", "config", "user.name", git_user_name], cwd=TARGET, check=True)
    run(["git", "config", "user.email", git_user_email], cwd=TARGET, check=True)
    run(["git", "add", "."], cwd=TARGET, check=True)
    run(["git", "commit", "-q", "-m", projection_commit_message], cwd=TARGET, check=True)
    public_ref = run(["git", "rev-parse", "HEAD"], cwd=TARGET, check=True).stdout.strip()
else:
    public_ref = ""

state = f"""schema_version: 1
roadmap: dynamic-data-model-open-source
mode: {MODE}
status: {'passed' if build_passed and not public_dependency_violations and not pkgen_forbidden and release_gate_passed else 'failed'}
generated_at: "{generated_at}"
manifest: internal-export-manifest
manifest_sha256: {manifest_hash}
target: {public_tree_label}
source_ref_recorded_in_public_tree: false
public_ref_recorded_in_public_tree: false
sync:
  policy: manifest-scoped-forward-sync
  dry_run: {str(DRY_RUN_FLAG or MODE == 'sync').lower()}
  history_policy: orphan-from-first-public-release
  internal_commit_messages_copied: false
  projection_commit_message: "{projection_commit_message}"
  remote_actions_performed: false
included_modules:
"""
for module_id in included_ids:
    state += f"  - {module_id}\n"
state += f"""checks:
  build_passed: {str(build_passed).lower()}
  public_dependency_violations: {len(public_dependency_violations)}
  internal_git_history_copied: false
  codestable_copied: false
  pkgen_forbidden_matches: {len(pkgen_forbidden)}
  release_gate_passed: {str(release_gate_passed).lower()}
  sensitive_content_matches: {len(secret_matches)}
  public_docs_forbidden_matches: {len(public_docs_forbidden)}
non_automatic_actions:
  remote_push: false
  merge: false
  publish: false
  release: false
  deploy: false
"""
write_text("public-sync-state.yaml", state)

if public_ref:
    run(["git", "add", "public-sync-state.yaml", "export-report.md"], cwd=TARGET, check=True)
    run(["git", "commit", "-q", "-m", state_commit_message], cwd=TARGET, check=True)

print(f"public export target: {TARGET}")
print(f"manifest sha256: {manifest_hash}")
print(f"internal ref: {internal_ref}")
print(f"public ref: {public_ref or 'not-created'}")
print(f"sync mode: {MODE}")
print(f"build: {'passed' if build_passed else 'failed'}")
print(f"public dependency violations: {len(public_dependency_violations)}")
print(f"pkgen forbidden matches: {len(pkgen_forbidden)}")
print(f"release gate: {'passed' if release_gate_passed else 'failed'}")

if not build_passed or public_dependency_violations or pkgen_forbidden or not release_gate_passed:
    sys.exit(1)
PY

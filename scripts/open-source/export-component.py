#!/usr/bin/env python3
"""Export a manifest-defined distribution from the monorepo worktree."""

import argparse
import copy
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

REPO_ROOT = Path(__file__).resolve().parents[2]
MANIFEST = REPO_ROOT / "scripts/open-source/manifests/components.json"
EXCLUDED = {".git", ".codestable", "target", "dist", "node_modules", "__pycache__", ".DS_Store"}


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def source_files(root: Path) -> list[Path]:
    return sorted(
        path for path in root.rglob("*")
        if path.is_file() and not EXCLUDED.intersection(path.relative_to(root).parts)
    )


def copy_path(source: Path, target: Path) -> None:
    if source.is_symlink():
        raise ValueError(f"Symlink is outside the export contract: {source}")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def read_pom(path: Path) -> ET.Element:
    root = ET.parse(path).getroot()
    for node in root.iter():
        node.tag = node.tag.rsplit("}", 1)[-1]
    return root


def write_pom(path: Path, root: ET.Element) -> None:
    root.set("xmlns", "http://maven.apache.org/POM/4.0.0")
    ET.indent(root, space="    ")
    ET.ElementTree(root).write(path, encoding="UTF-8", xml_declaration=True)


def element(parent: ET.Element, tag: str, text: str) -> ET.Element:
    child = ET.SubElement(parent, tag)
    child.text = text
    return child


def prepare_script_build(snapshot: Path) -> None:
    """Derive a closed reactor and dependency versions from the canonical POMs."""
    source = read_pom(REPO_ROOT / "pom.xml")
    bom = read_pom(REPO_ROOT / "bom/pom.xml")
    root = ET.Element("project")
    for tag, value in (
        ("modelVersion", "4.0.0"), ("groupId", "com.ouroboros"),
        ("artifactId", "tidestack-script-parent"),
        ("version", source.findtext("version")), ("packaging", "pom"),
        ("name", "TideStack Script Distribution"),
    ):
        element(root, tag, value)
    modules = ET.SubElement(root, "modules")
    for module in ("libs/util/ouroboros-util", "libs/common"):
        element(modules, "module", module)
    properties = ET.SubElement(root, "properties")
    for name in ("spring-boot.version", "junit5.engine.version", "junit5.platform.version",
                 "ouroboros.version", "project.build.sourceEncoding"):
        element(properties, name, bom.findtext(f"properties/{name}"))
    element(properties, "maven.compiler.release", "21")
    management = ET.SubElement(ET.SubElement(root, "dependencyManagement"), "dependencies")
    for group, artifact, version in (
        ("org.springframework.boot", "spring-boot-dependencies", "${spring-boot.version}"),
        ("org.junit", "junit-bom", "${junit5.engine.version}"),
    ):
        dependency = ET.SubElement(management, "dependency")
        for tag, value in (("groupId", group), ("artifactId", artifact),
                           ("version", version), ("type", "pom"), ("scope", "import")):
            element(dependency, tag, value)

    poms = [read_pom(path) for path in snapshot.rglob("pom.xml")]
    coordinates = {
        (dep.findtext("groupId"), dep.findtext("artifactId"))
        for pom in poms for dep in pom.findall("dependencies/dependency")
    }
    # networknt 3 uses Jackson 3; retain the platform's transitive alignment.
    coordinates.update({("tools.jackson.core", "jackson-core"),
                        ("tools.jackson.core", "jackson-databind"),
                        ("com.fasterxml.jackson.core", "jackson-annotations")})
    for dependency in bom.findall("dependencyManagement/dependencies/dependency"):
        key = (dependency.findtext("groupId"), dependency.findtext("artifactId"))
        if key in coordinates:
            management.append(copy.deepcopy(dependency))

    plugins = ET.SubElement(ET.SubElement(root, "build"), "plugins")
    for plugin in source.findall("build/pluginManagement/plugins/plugin"):
        if plugin.findtext("artifactId") in {
            "maven-compiler-plugin", "maven-surefire-plugin", "gmavenplus-plugin"
        }:
            plugins.append(copy.deepcopy(plugin))
    write_pom(snapshot / "pom.xml", root)

    for relative in ("libs/common/pom.xml", "libs/util/ouroboros-util/pom.xml"):
        path = snapshot / relative
        pom = read_pom(path)
        pom.find("parent/artifactId").text = "tidestack-script-parent"
        repositories = pom.find("repositories")
        if repositories is not None:
            pom.remove(repositories)
        if relative == "libs/common/pom.xml":
            modules = pom.find("modules")
            modules.clear()
            for name in ("common-code-intelligence", "common-script", "common-expression", "common-template"):
                element(modules, "module", name)
        write_pom(path, pom)


def prepare_maven_projection(snapshot: Path) -> None:
    """Keep the original Maven hierarchy, restricted to exported modules."""
    for path in sorted(snapshot.rglob("pom.xml")):
        pom = read_pom(path)
        modules = pom.find("modules")
        if modules is not None:
            for module in list(modules):
                if not (path.parent / module.text / "pom.xml").exists():
                    modules.remove(module)
        for tag in ("repositories", "pluginRepositories", "distributionManagement"):
            child = pom.find(tag)
            if child is not None:
                pom.remove(child)
        write_pom(path, pom)


def prepare_schema_projection(snapshot: Path) -> None:
    index_path = snapshot / "index.json"
    index = json.loads(index_path.read_text())
    for entry in index["entries"]:
        entry["path"] = Path(entry["path"]).name
    index_path.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n")
    for name in ("README.md", "README.en.md"):
        path = snapshot / name
        path.write_text(path.read_text().replace("-s metadata-schema/", "-s "))


def build_snapshot(component: str, snapshot: Path) -> dict:
    config = json.loads(MANIFEST.read_text())[component]
    source_root = REPO_ROOT / config.get("source_root", ".")
    for relative in config["paths"]:
        source = REPO_ROOT / relative
        if not source.exists():
            raise ValueError(f"Missing export input: {source}")
        paths = source_files(source) if source.is_dir() else [source]
        for path in paths:
            copy_path(path, snapshot / path.relative_to(source_root))
    if "overlay" in config:
        overlay = REPO_ROOT / config["overlay"]
        for path in source_files(overlay):
            copy_path(path, snapshot / path.relative_to(overlay))
    if "license" in config:
        copy_path(REPO_ROOT / config["license"], snapshot / "LICENSE")
    if component == "script":
        prepare_script_build(snapshot)
    elif component == "approval-workflow":
        prepare_maven_projection(snapshot)
    elif component == "metadata-schema":
        prepare_schema_projection(snapshot)
    return config


def differences(snapshot: Path, target: Path) -> list[str]:
    expected = {path.relative_to(snapshot) for path in source_files(snapshot)}
    actual = {Path(name) for name in git(
        target, "ls-files", "--cached", "--others", "--exclude-standard", "-z"
    ).split("\0") if name}
    changed = []
    for relative in sorted(expected | actual):
        left, right = snapshot / relative, target / relative
        if relative not in expected or not right.is_file() or right.is_symlink():
            changed.append(str(relative))
        elif (left.read_bytes() != right.read_bytes()
              or (left.stat().st_mode & 0o111) != (right.stat().st_mode & 0o111)):
            changed.append(str(relative))
    return changed


def initialize_target(target: Path, component: str) -> None:
    if target.exists() and any(target.iterdir()):
        raise ValueError(f"Initial export requires an empty destination: {target}")
    target.mkdir(parents=True, exist_ok=True)
    git(target, "init", "-b", "master")
    git(target, "config", "tidestack.exportComponent", component)


def verify_target(target: Path, component: str) -> None:
    if not (target / ".git").is_dir():
        raise ValueError(f"Not an exported repository: {target}")
    if git(target, "config", "--get", "tidestack.exportComponent") != component:
        raise ValueError(f"Destination belongs to another component: {target}")


def apply_snapshot(snapshot: Path, target: Path) -> None:
    expected = {path.relative_to(snapshot) for path in source_files(snapshot)}
    previous = {Path(name) for name in git(target, "ls-files", "-z").split("\0") if name}
    for relative in previous - expected:
        (target / relative).unlink()
    for relative in sorted(expected):
        copy_path(snapshot / relative, target / relative)
    git(target, "add", "--all")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--component", required=True, choices=tuple(json.loads(MANIFEST.read_text())))
    parser.add_argument("--target", required=True, type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="Compare without changing the destination")
    mode.add_argument("--update", action="store_true", help="Update an exported, clean destination")
    args = parser.parse_args()
    target = args.target.expanduser().resolve()
    if target == REPO_ROOT or target in REPO_ROOT.parents or REPO_ROOT in target.parents:
        raise ValueError("Destination must be outside the source monorepo")
    with tempfile.TemporaryDirectory(prefix="tidestack-component-") as temporary:
        snapshot = Path(temporary)
        config = build_snapshot(args.component, snapshot)
        if args.check or args.update:
            verify_target(target, args.component)
            changed = differences(snapshot, target)
            if args.check:
                print(json.dumps({"repository": config["repository"], "differences": changed}, indent=2))
                return 1 if changed else 0
            if git(target, "status", "--porcelain", "--untracked-files=all"):
                raise ValueError("Destination has local changes; commit or preserve them before updating")
        else:
            initialize_target(target, args.component)
        apply_snapshot(snapshot, target)
        print(f"Exported {config['repository']} to {target}; changes staged, no commit or push")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f"Export failed: {error}", file=sys.stderr)
        sys.exit(2)

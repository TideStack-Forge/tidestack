#!/usr/bin/env python3
"""Generate contract/capability snapshots and portable skills from maintained sources."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys


ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = Path("metadata-schema")
HANDBOOK_SCHEMAS = Path("docs/handbook/docs/reference/jsonschema/metadata")
IDE_SCHEMAS = Path("ide-plugins/common/metadata-editor-schema/schemas")
METADATA = Path("skills/tidestack-metadata-definition")
CAPABILITIES = Path("docs/handbook/static/capabilities")
ADVISOR = Path("skills/tidestack-howto")
TEMPLATE = Path("project-templates/templates/backend.extension.spring-autoconfigure/1.0.0")
TARGETS = (
    TEMPLATE / "tree/.agents/skills",
    Path("maven-archetypes/ouroboros-module-archetype/src/main/resources/archetype-resources/.agents/skills"),
)
LEGACY = {
    "tidestack",
    "ouroboros-app-module-definition", "ouroboros-menu-definition", "ouroboros-authority-definition",
    "ouroboros-ui-model-definition", "ouroboros-ui-schema-definition", "ouroboros-configuration-definition",
    "ouroboros-resource-bundle-definition", "ouroboros-metadata-migration", "ouroboros-start-web",
}


def encoded(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()


def digest(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def schema_outputs(root: Path) -> dict[Path, bytes]:
    outputs = {}
    entries = []
    for source in sorted((root / SCHEMAS).glob("*.schema.json")):
        original = source.read_bytes()
        json.loads(original)
        outputs[HANDBOOK_SCHEMAS / source.name] = original
        outputs[IDE_SCHEMAS / source.name] = original
        outputs[METADATA / "references" / source.name] = original
        entry = {"schema": source.name, "source": str(source.relative_to(root)),
                 "sourceSha256": digest(original), "distributedSha256": digest(original)}
        entries.append(entry)
    if not entries:
        raise ValueError("No source schemas found")
    index = json.loads((root / SCHEMAS / "index.json").read_bytes())
    indexed = [item["schema"] for item in index["entries"]] + index.get("supportingSchemas", [])
    if len(index["entries"]) != index["count"] or len(indexed) != len(set(indexed)) or set(indexed) != {item["schema"] for item in entries}:
        raise ValueError("Public schema index does not match the maintained schemas")
    handbook_index = {**index, "entries": [{**item, "path": "docs/reference/jsonschema/metadata/" + item["schema"]} for item in index["entries"]]}
    outputs[HANDBOOK_SCHEMAS / "index.json"] = encoded(handbook_index)
    outputs[IDE_SCHEMAS / "index.json"] = encoded(index)
    provenance = {"source": str(SCHEMAS), "count": len(index["entries"]),
                  "schemaCount": len(entries), "entries": entries}
    outputs[METADATA / "references/schema-sources.json"] = encoded(provenance)
    fixtures = (root / "testing/common/fixtures/data/metadata-schema-conformance/cases.json").read_bytes()
    outputs[Path("docs/handbook/scripts/fixtures/metadata-schema-conformance.json")] = fixtures
    outputs[Path("ide-plugins/common/metadata-editor-schema/tests/fixtures/metadata-schema-conformance.json")] = fixtures
    return outputs


def capability_outputs(root: Path) -> dict[Path, bytes]:
    outputs = {}
    entries = []
    for name in ("index.json", "catalog.json", "catalog.schema.json"):
        source = CAPABILITIES / name
        content = (root / source).read_bytes()
        json.loads(content)
        outputs[ADVISOR / "references" / name] = content
        entries.append({"source": str(source), "sha256": digest(content)})
    catalog = json.loads(outputs[ADVISOR / "references/catalog.json"])
    outputs[ADVISOR / "references/capability-sources.json"] = encoded({
        "baseline": "dev-next@4b525ad61456db1f80f09235765acd93f136b472",
        "source": catalog["source"],
        "count": len(catalog["capabilities"]),
        "entries": entries,
    })
    return outputs


def portable_files(root: Path) -> dict[Path, Path]:
    files = {}
    for skill in sorted((root / "skills").iterdir()):
        if not (skill / "SKILL.md").is_file():
            continue
        for source in sorted(skill.rglob("*")):
            relative = source.relative_to(root / "skills")
            if source.is_file() and not any(p in {"__pycache__", "tests"} for p in relative.parts) and source.suffix != ".pyc":
                files[relative] = source
    return files


def synchronize(root: Path, check: bool) -> list[str]:
    differences = []
    overrides = {**schema_outputs(root), **capability_outputs(root)}
    for relative, content in overrides.items():
        target = root / relative
        if not target.is_file() or target.read_bytes() != content:
            differences.append(str(relative))
            if not check:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(content)

    # In check mode, use the expected snapshots even when the checked-in snapshots are stale.
    files = portable_files(root)
    files.update({p.relative_to("skills"): root / p for p in overrides if p.parts[0] == "skills"})
    names = {p.parts[0] for p in files}
    for directory in TARGETS:
        target_root = root / directory
        existing = {p.relative_to(target_root): p for p in target_root.rglob("*") if p.is_file()
                    and "__pycache__" not in p.parts and p.suffix != ".pyc"}
        unknown = {p.parts[0] for p in existing} - names - LEGACY
        if unknown:
            raise ValueError(f"Unmanaged template skill directories at {directory}: {sorted(unknown)}")
        for relative, source in files.items():
            content = overrides[source.relative_to(root)] if source.relative_to(root) in overrides else source.read_bytes()
            target = target_root / relative
            if not target.is_file() or target.read_bytes() != content:
                differences.append(str(directory / relative))
                if not check:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(content)
                    if source.exists():
                        shutil.copymode(source, target)
        for relative, path in existing.items():
            if relative not in files:
                differences.append(str(directory / relative))
                if not check:
                    path.unlink()
        if not check:
            for directory_path in sorted(target_root.rglob("*"), reverse=True):
                if directory_path.is_dir() and not any(directory_path.iterdir()):
                    directory_path.rmdir()

    manifest_path = root / TEMPLATE / "template.json"
    manifest = json.loads(manifest_path.read_text())
    operations = [op for op in manifest["operations"] if not op.get("target", "").startswith(".agents/skills/")]
    operations.extend({"kind": "copy", "source": "tree/.agents/skills/" + p.as_posix(),
                       "target": ".agents/skills/" + p.as_posix()} for p in sorted(files))
    if manifest["operations"] != operations:
        differences.append(str(TEMPLATE / "template.json"))
        if not check:
            manifest["operations"] = operations
            manifest_path.write_bytes(encoded(manifest))
    return differences


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Report drift without modifying files")
    args = parser.parse_args()
    try:
        differences = synchronize(ROOT, args.check)
    except (OSError, ValueError) as error:
        print(f"Skill distribution failed: {error}", file=sys.stderr)
        return 2
    if differences and args.check:
        print("Skill/schema distribution drift:\n" + "\n".join(differences), file=sys.stderr)
        return 1
    print(f"Skill distribution {'checked' if args.check else 'synchronized'}: {len(differences)} changed files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

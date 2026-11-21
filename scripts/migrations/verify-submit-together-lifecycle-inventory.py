#!/usr/bin/env python3
"""Verify submitTogether lifecycle inventory before template cutover.

The verifier is intentionally independent from model discovery. It validates the
machine-readable inventory emitted by the runtime inventory runner and applies
the lifecycle migration gate rules from the approved design.
"""

from __future__ import annotations

import argparse
import json
import sys
import tempfile
from pathlib import Path
from typing import Any

try:
    import yaml  # type: ignore
except ImportError:  # pragma: no cover - exercised only on minimal Python envs
    yaml = None


EMPTY_TREE_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
SECURITY_CATEGORIES = {
    "permission",
    "security",
    "integrity",
    "access-control",
    "reference-integrity",
}
BUSINESS_CATEGORIES = {
    "business-flow",
    "business-script",
    "custom-flow",
    "custom-script",
}
PERFORMANCE_CATEGORIES = {
    "data-plugin-fallback",
    "single-item-fallback",
    "performance-fallback",
}
RESOLVED = {"migrated", "resolved", "batch-aware"}
BEHAVIOR_ACCEPTED = {"accepted-behavior-removal", "accepted-business-lifecycle-removal"}
PERFORMANCE_ACCEPTED = {"accepted-performance-fallback"}
UNRESOLVED = {"", "pending", "unresolved", "todo", "none", "null"}


def load_document(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    if path.suffix.lower() == ".json":
        return json.loads(text)
    if yaml is not None:
        loaded = yaml.safe_load(text)
        return loaded or {}
    return parse_minimal_yaml(text)


def parse_minimal_yaml(text: str) -> dict[str, Any]:
    result: dict[str, Any] = {}
    current_section: str | None = None
    current_entry: str | None = None
    current_issue: str | None = None

    for raw in text.splitlines():
        line = raw.rstrip()
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = len(line) - len(line.lstrip(" "))
        if indent == 0 and stripped.endswith(":"):
            current_section = stripped[:-1]
            result.setdefault(current_section, {})
            current_entry = None
            current_issue = None
            continue
        if current_section != "entries":
            continue
        if indent == 2 and stripped.endswith(":"):
            current_entry = stripped[:-1]
            result["entries"].setdefault(current_entry, {})
            current_issue = None
            continue
        if indent == 4 and stripped == "issues:":
            if current_entry is not None:
                result["entries"].setdefault(current_entry, {}).setdefault("issues", {})
            continue
        if indent == 6 and stripped.endswith(":"):
            current_issue = stripped[:-1]
            if current_entry is not None:
                result["entries"][current_entry].setdefault("issues", {}).setdefault(current_issue, {})
            continue
        if indent >= 8 and ":" in stripped and current_entry is not None and current_issue is not None:
            key, value = stripped.split(":", 1)
            result["entries"][current_entry]["issues"][current_issue][key.strip()] = clean_scalar(value.strip())
    return result


def clean_scalar(value: str) -> str:
    return value.strip().strip("'\"")


def build_resolution_lookup(resolution: dict[str, Any] | None) -> dict[str, dict[str, Any]]:
    lookup: dict[str, dict[str, Any]] = {}
    if not resolution:
      return lookup

    issues = resolution.get("issues")
    if isinstance(issues, dict):
        for issue_id, payload in issues.items():
            if isinstance(payload, dict):
                lookup[str(issue_id)] = payload

    entries = resolution.get("entries")
    if isinstance(entries, dict):
        for entry_id, entry_payload in entries.items():
            if not isinstance(entry_payload, dict):
                continue
            entry_issues = entry_payload.get("issues")
            if isinstance(entry_issues, dict):
                for issue_id, payload in entry_issues.items():
                    if isinstance(payload, dict):
                        lookup[f"{entry_id}:{issue_id}"] = payload
                        lookup[str(issue_id)] = payload

    resolutions = resolution.get("resolutions")
    if isinstance(resolutions, list):
        for payload in resolutions:
            if not isinstance(payload, dict):
                continue
            issue_id = payload.get("issueId") or payload.get("issue_id") or payload.get("id")
            entry_id = payload.get("entryId") or payload.get("entry_id")
            if issue_id:
                lookup[str(issue_id)] = payload
                if entry_id:
                    lookup[f"{entry_id}:{issue_id}"] = payload
    return lookup


def issue_value(issue: dict[str, Any], resolution: dict[str, Any] | None, key: str) -> Any:
    if key == "resolution" and resolution and resolution.get(key) not in (None, ""):
        return resolution.get(key)
    value = issue.get(key)
    if value not in (None, ""):
        return value
    if resolution:
        return resolution.get(key)
    return value


def verify_inventory(inventory: dict[str, Any], resolution: dict[str, Any] | None = None) -> list[str]:
    errors: list[str] = []
    if not inventory.get("schemaVersion"):
        errors.append("schemaVersion is required")
    if inventory.get("sourceComplete") is not True:
        errors.append("sourceComplete must be true before cutover")

    source_ledger = inventory.get("sourceLedger")
    if not isinstance(source_ledger, list) or not source_ledger:
        errors.append("sourceLedger must contain repository/appHome/provider sources")
    else:
        for index, source in enumerate(source_ledger):
            if not isinstance(source, dict):
                errors.append(f"sourceLedger[{index}] must be an object")
                continue
            if source.get("sourceComplete") is False or source.get("enumerable") is False:
                name = source.get("name") or source.get("type") or index
                errors.append(f"sourceLedger[{name}] is incomplete")
            if source.get("type") == "repository":
                repository_hash = source.get("repositoryHash")
                scanned_file_count = source.get("scannedFileCount")
                if repository_hash == EMPTY_TREE_SHA256 or scanned_file_count == 0:
                    errors.append("sourceLedger[repository] has empty scan evidence")

    entries = inventory.get("entries")
    if not isinstance(entries, list):
        errors.append("entries must be a list")
        return errors

    resolution_lookup = build_resolution_lookup(resolution)
    for entry in entries:
        if not isinstance(entry, dict):
            errors.append("entry must be an object")
            continue
        entry_id = str(entry.get("id") or entry.get("relationPath") or "<unknown>")
        issues = entry.get("issues", [])
        if not isinstance(issues, list):
            errors.append(f"{entry_id}: issues must be a list")
            continue
        for issue in issues:
            if not isinstance(issue, dict):
                errors.append(f"{entry_id}: issue must be an object")
                continue
            issue_id = str(issue.get("id") or issue.get("category") or "<unknown>")
            resolved = resolution_lookup.get(f"{entry_id}:{issue_id}") or resolution_lookup.get(issue_id)
            category = str(issue_value(issue, resolved, "category") or "").lower()
            severity = str(issue_value(issue, resolved, "severity") or "blocking").lower()
            lifecycle = str(issue_value(issue, resolved, "resolution") or "").lower()
            approval_ref = str(issue_value(issue, resolved, "approvalRef") or "").strip()

            if lifecycle in RESOLVED:
                continue
            if lifecycle in UNRESOLVED:
                errors.append(f"{entry_id}:{issue_id} is unresolved")
                continue
            if category in SECURITY_CATEGORIES:
                errors.append(f"{entry_id}:{issue_id} is {category} and must be migrated")
                continue
            if category in BUSINESS_CATEGORIES:
                if lifecycle in BEHAVIOR_ACCEPTED and approval_ref:
                    continue
                errors.append(f"{entry_id}:{issue_id} needs migrated or accepted behavior removal approvalRef")
                continue
            if category in PERFORMANCE_CATEGORIES:
                if lifecycle in PERFORMANCE_ACCEPTED and approval_ref:
                    continue
                errors.append(f"{entry_id}:{issue_id} needs batch-aware or performance fallback approvalRef")
                continue
            if severity in {"blocking", "important"}:
                errors.append(f"{entry_id}:{issue_id} has unsupported resolution: {lifecycle}")
    return errors


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def run_self_test() -> int:
    with tempfile.TemporaryDirectory() as temp_dir:
        temp = Path(temp_dir)
        base = {
            "schemaVersion": "submit-together-lifecycle-inventory/v1",
            "sourceComplete": True,
            "sourceLedger": [{"type": "repository", "sourceComplete": True}],
            "entries": [],
        }
        valid = dict(base)
        valid["entries"] = [{
            "id": "customer.contacts",
            "issues": [{
                "id": "fallback-contact-audit",
                "category": "data-plugin-fallback",
                "severity": "important",
                "resolution": "accepted-performance-fallback",
                "approvalRef": "owner-approval-2026-07-31",
            }],
        }]
        valid_path = temp / "valid.json"
        write_json(valid_path, valid)
        if verify_inventory(load_document(valid_path)):
            print("self-test valid case failed", file=sys.stderr)
            return 1

        incomplete = dict(base)
        incomplete["sourceComplete"] = False
        if not verify_inventory(incomplete):
            print("self-test sourceComplete=false should fail", file=sys.stderr)
            return 1

        empty_repository = dict(base)
        empty_repository["sourceLedger"] = [{
            "type": "repository",
            "sourceComplete": True,
            "repositoryHash": EMPTY_TREE_SHA256,
            "scannedFileCount": 0,
        }]
        if not verify_inventory(empty_repository):
            print("self-test empty repository scan should fail", file=sys.stderr)
            return 1

        security = dict(base)
        security["entries"] = [{
            "id": "customer.contacts",
            "issues": [{
                "id": "access-control",
                "category": "security",
                "severity": "blocking",
                "resolution": "accepted-behavior-removal",
                "approvalRef": "owner-approval-2026-07-31",
            }],
        }]
        if not verify_inventory(security):
            print("self-test security acceptance should fail", file=sys.stderr)
            return 1

        business = dict(base)
        business["entries"] = [{
            "id": "customer.contacts",
            "issues": [{
                "id": "custom-after-script",
                "category": "business-script",
                "severity": "important",
                "resolution": "accepted-behavior-removal",
            }],
        }]
        if not verify_inventory(business):
            print("self-test missing approvalRef should fail", file=sys.stderr)
            return 1
    print("self-test passed")
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--inventory")
    parser.add_argument("--resolution", "--resolutions", dest="resolution")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)

    if args.self_test:
        return run_self_test()
    if not args.inventory:
        parser.error("--inventory is required unless --self-test is used")

    inventory = load_document(Path(args.inventory))
    resolution = load_document(Path(args.resolution)) if args.resolution else None
    errors = verify_inventory(inventory, resolution)
    if args.json:
        print(json.dumps({"ok": not errors, "errors": errors}, ensure_ascii=False, indent=2))
    elif errors:
        for error in errors:
            print(error, file=sys.stderr)
    else:
        print("inventory verification passed")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

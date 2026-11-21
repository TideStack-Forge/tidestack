#!/usr/bin/env python3
from __future__ import annotations

import datetime as dt
import json
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Any


EXCLUDED_PARTS = {"node_modules", "target", "dist", ".worktrees", "ide-plugins", "tmp"}
POLICY_PATH = Path("scripts/ci/dependency-policy.yaml")
ACTIVITI6_ARTIFACT = "ouroboros-approval-workflow-activiti6"
JUNIT4_IMPORT_RULE = r"(?m)^\s*import\s+org\.junit\.(?!jupiter(?:\.|;)|platform(?:\.|;))"
JUNIT4_COORDINATE = ("junit", "junit")
JUNIT_VINTAGE_COORDINATE = ("org.junit.vintage", "junit-vintage-engine")


class InputError(Exception):
    pass


@dataclass(frozen=True)
class Diagnostic:
    code: str
    group: str
    path: str
    usage: str
    policy_id: str
    reason: str

    def line(self) -> str:
        return (
            f"{self.code} group={self.group} path={self.path} usage={self.usage} "
            f"policy={self.policy_id} reason={self.reason}"
        )


def _strip_comment(line: str) -> str:
    in_quote = False
    quote = ""
    for i, ch in enumerate(line):
        if ch in {"'", '"'} and (i == 0 or line[i - 1] != "\\"):
            if not in_quote:
                in_quote = True
                quote = ch
            elif quote == ch:
                in_quote = False
        if ch == "#" and not in_quote:
            return line[:i].rstrip()
    return line.rstrip()


def _scalar(value: str) -> Any:
    value = value.strip()
    if value == "[]":
        return []
    if value == "{}":
        return {}
    if value in {"true", "false"}:
        return value == "true"
    if value in {"null", "Null", "NULL", "~"}:
        return None
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    if re.fullmatch(r"-?\d+", value):
        return int(value)
    return value


def _split_key_value(text: str) -> tuple[str, str | None]:
    key, sep, value = text.partition(":")
    if not sep:
        raise InputError(f"invalid yaml line: {text}")
    key = key.strip()
    if not key:
        raise InputError(f"empty yaml key: {text}")
    value = value.strip()
    return key, value if value else None


def _prepare_yaml(text: str) -> list[tuple[int, str]]:
    rows: list[tuple[int, str]] = []
    for raw in text.splitlines():
        line = _strip_comment(raw)
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip(" "))
        rows.append((indent, line.strip()))
    return rows


def _parse_block(rows: list[tuple[int, str]], index: int, indent: int) -> tuple[Any, int]:
    if index >= len(rows):
        return {}, index
    current_indent, current = rows[index]
    if current_indent < indent:
        return {}, index
    if current_indent != indent:
        raise InputError(f"unexpected yaml indent: {current}")
    if current.startswith("- "):
        return _parse_list(rows, index, indent)
    return _parse_mapping(rows, index, indent)


def _parse_mapping(rows: list[tuple[int, str]], index: int, indent: int) -> tuple[dict[str, Any], int]:
    out: dict[str, Any] = {}
    while index < len(rows):
        row_indent, text = rows[index]
        if row_indent < indent:
            break
        if row_indent != indent or text.startswith("- "):
            break
        key, value = _split_key_value(text)
        index += 1
        if value is None:
            if index < len(rows) and (rows[index][0] > row_indent or (rows[index][0] == row_indent and rows[index][1].startswith("- "))):
                child, index = _parse_block(rows, index, rows[index][0])
                out[key] = child
            else:
                out[key] = {}
        else:
            out[key] = _scalar(value)
    return out, index


def _parse_list(rows: list[tuple[int, str]], index: int, indent: int) -> tuple[list[Any], int]:
    out: list[Any] = []
    while index < len(rows):
        row_indent, text = rows[index]
        if row_indent < indent:
            break
        if row_indent != indent or not text.startswith("- "):
            break
        rest = text[2:].strip()
        index += 1
        if not rest:
            if index < len(rows) and rows[index][0] > row_indent:
                child, index = _parse_block(rows, index, rows[index][0])
                out.append(child)
            else:
                out.append(None)
            continue
        if ":" in rest and not rest.startswith(("'", '"')):
            key, value = _split_key_value(rest)
            item: dict[str, Any] = {}
            if value is None:
                if index < len(rows) and rows[index][0] > row_indent:
                    child, index = _parse_block(rows, index, rows[index][0])
                    item[key] = child
                else:
                    item[key] = {}
            else:
                item[key] = _scalar(value)
            if index < len(rows) and rows[index][0] > row_indent:
                more, index = _parse_mapping(rows, index, rows[index][0])
                item.update(more)
            out.append(item)
        else:
            out.append(_scalar(rest))
    return out, index


def load_yaml_subset(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise InputError(f"policy file not found: {path.as_posix()}")
    rows = _prepare_yaml(path.read_text(encoding="utf-8"))
    if not rows:
        raise InputError(f"empty policy file: {path.as_posix()}")
    data, index = _parse_block(rows, 0, rows[0][0])
    if index != len(rows) or not isinstance(data, dict):
        raise InputError(f"could not parse policy yaml: {path.as_posix()}")
    return data


def relpath(root: Path, path: Path) -> str:
    return path.relative_to(root).as_posix()


def excluded(path: str) -> bool:
    p = Path(path)
    return path.startswith("micro-app/spreadsheet/") or bool(EXCLUDED_PARTS.intersection(p.parts))


def strict_date(value: Any) -> dt.date:
    if not isinstance(value, str):
        raise InputError(f"invalid date: {value!r}")
    try:
        parsed = dt.date.fromisoformat(value)
    except ValueError as exc:
        raise InputError(f"invalid date: {value!r}") from exc
    if parsed.isoformat() != value:
        raise InputError(f"invalid date: {value!r}")
    return parsed


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child_text(node: ET.Element, name: str, default: str = "") -> str:
    for child in list(node):
        if local_name(child.tag) == name:
            return (child.text or default).strip()
    return default


def child(node: ET.Element, name: str) -> ET.Element | None:
    for item in list(node):
        if local_name(item.tag) == name:
            return item
    return None


def direct_dependencies(pom: Path) -> list[dict[str, str]]:
    try:
        root = ET.parse(pom).getroot()
    except ET.ParseError as exc:
        raise InputError(f"invalid pom xml: {pom.as_posix()}") from exc
    deps = child(root, "dependencies")
    if deps is None:
        return []
    out = []
    for dep in list(deps):
        if local_name(dep.tag) != "dependency":
            continue
        out.append(
            {
                "group_id": child_text(dep, "groupId"),
                "artifact_id": child_text(dep, "artifactId"),
                "version": child_text(dep, "version"),
                "scope": child_text(dep, "scope", "compile") or "compile",
            }
        )
    return out


def dependency_management_dependencies(pom: Path) -> list[dict[str, str]]:
    try:
        root = ET.parse(pom).getroot()
    except ET.ParseError as exc:
        raise InputError(f"invalid pom xml: {pom.as_posix()}") from exc
    management = child(root, "dependencyManagement")
    if management is None:
        return []
    deps = child(management, "dependencies")
    if deps is None:
        return []
    out = []
    for dep in list(deps):
        if local_name(dep.tag) != "dependency":
            continue
        out.append(
            {
                "group_id": child_text(dep, "groupId"),
                "artifact_id": child_text(dep, "artifactId"),
                "version": child_text(dep, "version"),
                "scope": child_text(dep, "scope", "compile") or "compile",
            }
        )
    return out


def local_parent_pom(root: Path, pom: Path) -> Path | None:
    try:
        project = ET.parse(pom).getroot()
    except ET.ParseError as exc:
        raise InputError(f"invalid pom xml: {pom.as_posix()}") from exc
    parent = child(project, "parent")
    if parent is None:
        return None
    relative = child(parent, "relativePath")
    relative_path = "../pom.xml" if relative is None else (relative.text or "").strip()
    if not relative_path:
        return None
    candidate = (pom.parent / relative_path).resolve()
    try:
        candidate.relative_to(root)
    except ValueError:
        return None
    return candidate if candidate.is_file() else None


def modules_in(pom: Path) -> set[str]:
    try:
        root = ET.parse(pom).getroot()
    except ET.ParseError as exc:
        raise InputError(f"invalid pom xml: {pom.as_posix()}") from exc
    modules = child(root, "modules")
    if modules is None:
        return set()
    return {((item.text or "").strip()) for item in list(modules) if local_name(item.tag) == "module"}


def maven_usage(scope: str) -> str:
    return "test" if scope == "test" else "runtime"


def path_matches(pattern: str, path: str) -> bool:
    if pattern.endswith("/**"):
        prefix = pattern[:-3]
        return path == prefix or path.startswith(prefix + "/")
    return path == pattern


def coordinate_matches(coordinate: dict[str, Any], dep: dict[str, str]) -> bool:
    match = coordinate.get("match") or {}
    return (
        match.get("group_id") == dep.get("group_id")
        and (match.get("artifact_id") in {"*", dep.get("artifact_id")})
    )


def coordinate_id_maps(policy: dict[str, Any]) -> dict[str, dict[str, Any]]:
    out = {}
    for group in policy.get("dependency_groups") or []:
        coords = group.get("coordinates") or {}
        for bucket in ("canonical", "forbidden"):
            for coord in coords.get(bucket) or []:
                out[coord.get("id")] = coord
    return out


def group_scope_patterns(group: dict[str, Any]) -> list[str]:
    patterns: list[str] = []
    for boundary in group.get("boundaries") or []:
        path_match = boundary.get("path_match") or {}
        if path_match.get("path"):
            patterns.append(str(path_match["path"]))
        if path_match.get("path_glob"):
            patterns.append(str(path_match["path_glob"]))
    for ex in group.get("exceptions") or []:
        if ex.get("path_glob"):
            patterns.append(str(ex["path_glob"]))
    required = group.get("required_states") or {}
    for fact in required.get("runtime_facts") or []:
        if fact.get("path"):
            patterns.append(str(fact["path"]))
    return sorted(set(patterns))


def in_group_scope(group: dict[str, Any], path: str) -> bool:
    patterns = group_scope_patterns(group)
    if not patterns:
        return True
    return any(path_matches(pattern, path) for pattern in patterns)


def group_by_id(policy: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {group.get("id"): group for group in policy.get("dependency_groups") or []}


def exception_applies(
    ex: dict[str, Any],
    path: str,
    usage: str,
    waiver_type: str,
    waiver_id: str,
    today: dt.date,
) -> bool:
    try:
        if strict_date(ex.get("expires_on")) < today:
            return False
    except InputError:
        return False
    waiver = ex.get("waives") or {}
    if waiver.get("type") != waiver_type:
        return False
    if waiver_type == "coordinate" and waiver.get("coordinate_id") != waiver_id:
        return False
    if waiver_type == "state" and waiver.get("state_id") != waiver_id:
        return False
    return usage in (ex.get("usage_scopes") or []) and path_matches(str(ex.get("path_glob") or ""), path)


def matching_exception(
    group: dict[str, Any],
    path: str,
    usage: str,
    waiver_type: str,
    waiver_id: str,
    today: dt.date,
) -> dict[str, Any] | None:
    for ex in group.get("exceptions") or []:
        if exception_applies(ex, path, usage, waiver_type, waiver_id, today):
            return ex
    return None


def validate_evidence_references(root: Path, policy: dict[str, Any]) -> None:
    seen = set()
    for evidence in policy.get("evidence") or []:
        evidence_id = evidence.get("id")
        if not evidence_id or evidence_id in seen:
            raise InputError(f"duplicate or missing evidence id: {evidence_id}")
        seen.add(evidence_id)
        for artifact in evidence.get("artifacts") or []:
            rel = artifact.get("path")
            if not isinstance(rel, str) or excluded(rel):
                raise InputError(f"invalid evidence path: {rel}")
            path = root / rel
            if not path.is_file():
                raise InputError(f"missing evidence artifact: {rel}")


def scan_maven(root: Path) -> list[tuple[str, dict[str, str]]]:
    out = []
    for pom in sorted(root.rglob("pom.xml")):
        rel = relpath(root, pom)
        if excluded(rel):
            continue
        for dep in direct_dependencies(pom):
            out.append((rel, dep))
    return out


def scan_maven_management(root: Path) -> list[tuple[str, dict[str, str]]]:
    out = []
    for pom in sorted(root.rglob("pom.xml")):
        rel = relpath(root, pom)
        if excluded(rel):
            continue
        for dep in dependency_management_dependencies(pom):
            out.append((rel, dep))
    return out


def scan_package_json(root: Path) -> list[tuple[str, dict[str, Any]]]:
    out = []
    for manifest in sorted(root.rglob("package.json")):
        rel = relpath(root, manifest)
        if excluded(rel):
            continue
        try:
            data = json.loads(manifest.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise InputError(f"invalid package json: {rel}") from exc
        out.append((rel, data))
    return out


def validate_exceptions(
    root: Path,
    policy: dict[str, Any],
    maven: list[tuple[str, dict[str, str]]],
    today: dt.date,
) -> list[Diagnostic]:
    diagnostics = []
    coords = coordinate_id_maps(policy)
    for group in policy.get("dependency_groups") or []:
        group_id = group.get("id", "<unknown>")
        for ex in group.get("exceptions") or []:
            ex_id = str(ex.get("id") or "<unknown>")
            path_glob = str(ex.get("path_glob") or "")
            if path_glob.endswith("/**"):
                diagnostics.append(
                    Diagnostic("POLICY_EXCEPTION_EXPANDED", group_id, path_glob, "*", ex_id, "exception uses directory-wide waiver")
                )
            try:
                if strict_date(ex.get("expires_on")) < today:
                    diagnostics.append(
                        Diagnostic("POLICY_EXCEPTION_EXPIRED", group_id, path_glob, "*", ex_id, "exception is expired")
                    )
            except InputError as exc:
                raise InputError(f"{ex_id}: {exc}") from exc
            condition = ex.get("tightening_condition") or {}
            ctype = condition.get("type")
            if ctype == "artifact-exists":
                target = str(condition.get("path") or "")
                if (root / target).exists():
                    diagnostics.append(
                        Diagnostic("POLICY_EXCEPTION_TIGHTENING_MET", group_id, target, "*", ex_id, "tightening artifact now exists")
                    )
            elif ctype == "coordinate-absent":
                coordinate = coords.get(condition.get("coordinate_id"))
                usage = str(condition.get("usage_scope") or "")
                target_glob = str(condition.get("path_glob") or "")
                present = False
                if coordinate:
                    for dep_path, dep in maven:
                        if path_matches(target_glob, dep_path) and maven_usage(dep.get("scope", "")) == usage and coordinate_matches(coordinate, dep):
                            present = True
                            break
                if not present:
                    diagnostics.append(
                        Diagnostic("POLICY_EXCEPTION_TIGHTENING_MET", group_id, target_glob, usage, ex_id, "waived coordinate is absent")
                    )
    return diagnostics


def check_forbidden_maven(
    policy: dict[str, Any],
    maven: list[tuple[str, dict[str, str]]],
    today: dt.date,
) -> list[Diagnostic]:
    diagnostics = []
    for group in policy.get("dependency_groups") or []:
        group_id = group.get("id", "<unknown>")
        for coord in (group.get("coordinates") or {}).get("forbidden") or []:
            coord_id = str(coord.get("id") or "<unknown>")
            usage_scopes = set(coord.get("usage_scopes") or [])
            if coord.get("ecosystem") != "maven":
                continue
            for path, dep in maven:
                if not in_group_scope(group, path):
                    continue
                usage = maven_usage(dep.get("scope", ""))
                if usage not in usage_scopes or not coordinate_matches(coord, dep):
                    continue
                if matching_exception(group, path, usage, "coordinate", coord_id, today):
                    continue
                diagnostics.append(
                    Diagnostic("POLICY_FORBIDDEN_COORDINATE", group_id, path, usage, coord_id, f"{dep['group_id']}:{dep['artifact_id']}")
                )
    return diagnostics


def check_frontend(policy: dict[str, Any], root: Path, manifests: list[tuple[str, dict[str, Any]]], today: dt.date) -> list[Diagnostic]:
    group = group_by_id(policy).get("frontend-tooling")
    if not group:
        return []
    diagnostics = []
    actual = {path: data for path, data in manifests}
    required = group.get("required_states") or {}
    non_install = {item.get("path") for item in required.get("non_install_manifests") or []}
    for path, data in actual.items():
        if path in non_install:
            continue
        if not data.get("scripts"):
            continue
        lock = Path(path).parent / "package-lock.json"
        if not (root / lock).is_file() and not matching_exception(group, path, "build-tooling", "state", "missing-lockfile", today):
            diagnostics.append(
                Diagnostic("POLICY_FRONTEND_MISSING_LOCK", "frontend-tooling", path, "build-tooling", "missing-lockfile", "install root lacks sibling package-lock.json")
            )
    for cohort in required.get("tooling_cohorts") or []:
        package_names = set(cohort.get("package_names") or [])
        expected = sorted(
            (obs.get("manifest"), obs.get("package_name"), obs.get("specifier"))
            for obs in cohort.get("observations") or []
        )
        observed = []
        for path, data in actual.items():
            if path in non_install:
                continue
            for bucket in ("dependencies", "devDependencies"):
                for name, spec in (data.get(bucket) or {}).items():
                    if name in package_names:
                        observed.append((path, name, spec))
        if sorted(observed) != expected:
            diagnostics.append(
                Diagnostic("POLICY_FRONTEND_COHORT_DRIFT", "frontend-tooling", ",".join(sorted({x[0] for x in observed}) or ["<none>"]), "build-tooling", str(cohort.get("id")), "tooling cohort observations differ from ledger")
            )
    return diagnostics


def check_approval(root: Path) -> list[Diagnostic]:
    diagnostics = []
    runtime_paths = [
        "core/ouroboros-approval-workflow/ouroboros-approval-workflow-runtime/pom.xml",
        "core/ouroboros-approval-workflow/ouroboros-approval-workflow-qb-spring-boot-starter/pom.xml",
    ]
    for rel in runtime_paths:
        path = root / rel
        if not path.exists():
            continue
        for dep in direct_dependencies(path):
            if dep.get("artifact_id") == ACTIVITI6_ARTIFACT:
                diagnostics.append(
                    Diagnostic("POLICY_APPROVAL_DEFAULT_BOUNDARY", "approval-workflow-engine", rel, "runtime", ACTIVITI6_ARTIFACT, "Activiti6 entered default runtime/starter")
                )
    parent = root / "core/ouroboros-approval-workflow/pom.xml"
    if parent.exists() and ACTIVITI6_ARTIFACT in modules_in(parent):
        diagnostics.append(
            Diagnostic("POLICY_APPROVAL_DEFAULT_BOUNDARY", "approval-workflow-engine", relpath(root, parent), "runtime", ACTIVITI6_ARTIFACT, "Activiti6 entered default reactor modules")
        )
    return diagnostics


def dep_is(dep: dict[str, str], coordinate: tuple[str, str]) -> bool:
    return dep.get("group_id") == coordinate[0] and dep.get("artifact_id") == coordinate[1]


def nearest_module_pom(root: Path, source: Path) -> Path | None:
    current = source.parent
    while True:
        pom = current / "pom.xml"
        if pom.is_file():
            return pom
        if current == root or current.parent == current:
            return None
        current = current.parent


def junit4_sources(root: Path, import_rule: str) -> list[str]:
    pattern = re.compile(import_rule)
    sources = []
    for source in sorted(root.rglob("*.java")):
        rel = relpath(root, source)
        if excluded(rel) or "/src/test/" not in rel:
            continue
        if pattern.search(source.read_text(encoding="utf-8")):
            sources.append(rel)
    return sources


def effective_vintage_owner(root: Path, source: str, vintage_poms: set[str]) -> str | None:
    pom = nearest_module_pom(root, root / source)
    seen = set()
    while pom is not None:
        rel = relpath(root, pom)
        if rel in seen:
            raise InputError(f"cyclic local parent chain near {rel}")
        seen.add(rel)
        if rel in vintage_poms:
            return rel
        pom = local_parent_pom(root, pom)
    return None


def mapping_counts(mapping: dict[str, int]) -> list[dict[str, Any]]:
    return [
        {"declaration_pom": path, "effective_source_count": count}
        for path, count in sorted(mapping.items())
    ]


def check_junit(policy: dict[str, Any], root: Path, maven: list[tuple[str, dict[str, str]]], management: list[tuple[str, dict[str, str]]], today: dt.date) -> list[Diagnostic]:
    group = group_by_id(policy).get("junit-compatibility")
    if not group:
        return []
    diagnostics = []
    required = group.get("required_states") or {}
    source_state = required.get("source_snapshot") or {}
    vintage_state = required.get("vintage_declarations") or {}
    coverage_state = required.get("effective_coverage") or {}
    import_rule = str(source_state.get("import_rule") or JUNIT4_IMPORT_RULE)

    direct_vintage = sorted(
        path for path, dep in maven if maven_usage(dep.get("scope", "")) == "test" and dep_is(dep, JUNIT_VINTAGE_COORDINATE)
    )
    management_vintage = sorted(path for path, dep in management if dep_is(dep, JUNIT_VINTAGE_COORDINATE))
    direct_junit = sorted(path for path, dep in maven if maven_usage(dep.get("scope", "")) == "test" and dep_is(dep, JUNIT4_COORDINATE))
    runtime_junit = sorted(
        path
        for path, dep in maven
        if maven_usage(dep.get("scope", "")) != "test" and (dep_is(dep, JUNIT4_COORDINATE) or dep_is(dep, JUNIT_VINTAGE_COORDINATE))
    )
    sources = junit4_sources(root, import_rule)

    if source_state.get("file_count") is not None and len(sources) != source_state.get("file_count"):
        diagnostics.append(
            Diagnostic("POLICY_JUNIT_SOURCE_COUNT_DRIFT", "junit-compatibility", "<sources>", "test", "source_snapshot", f"expected {source_state.get('file_count')} JUnit 4 sources, found {len(sources)}")
        )
    if vintage_state.get("direct_count") is not None and len(direct_vintage) != vintage_state.get("direct_count"):
        diagnostics.append(
            Diagnostic("POLICY_JUNIT_VINTAGE_DECLARATION_DRIFT", "junit-compatibility", "<poms>", "test", "vintage_declarations", f"expected {vintage_state.get('direct_count')} direct Vintage declarations, found {len(direct_vintage)}")
        )
    if vintage_state.get("management_count") is not None and len(management_vintage) != vintage_state.get("management_count"):
        diagnostics.append(
            Diagnostic("POLICY_JUNIT_VINTAGE_MANAGEMENT_DRIFT", "junit-compatibility", "<poms>", "test", "vintage_declarations", f"expected {vintage_state.get('management_count')} managed Vintage declarations, found {len(management_vintage)}")
        )
    if sorted(vintage_state.get("management_paths") or []) != management_vintage:
        diagnostics.append(
            Diagnostic("POLICY_JUNIT_VINTAGE_MANAGEMENT_DRIFT", "junit-compatibility", ",".join(management_vintage or ["<none>"]), "test", "vintage_declarations", "managed Vintage paths differ from ledger")
        )
    if direct_junit != sorted(m.get("declaration_pom") for m in required.get("direct_junit_mappings") or []):
        diagnostics.append(
            Diagnostic("POLICY_JUNIT_DIRECT_JUNIT_DRIFT", "junit-compatibility", ",".join(direct_junit or ["<none>"]), "test", "direct_junit_mappings", "direct junit:junit declarations differ from ledger")
        )
    for path in runtime_junit:
        diagnostics.append(
            Diagnostic("POLICY_JUNIT_RUNTIME_COORDINATE", "junit-compatibility", path, "runtime", "junit-runtime", "JUnit 4 or Vintage entered runtime scope")
        )

    actual_mapping: dict[str, int] = {}
    uncovered = []
    for source in sources:
        owner = effective_vintage_owner(root, source, set(direct_vintage))
        if owner is None:
            uncovered.append(source)
        else:
            actual_mapping[owner] = actual_mapping.get(owner, 0) + 1
    expected_mapping = sorted(
        (
            {"declaration_pom": m.get("declaration_pom"), "effective_source_count": m.get("effective_source_count")}
            for m in required.get("vintage_effective_mappings") or []
        ),
        key=lambda item: str(item.get("declaration_pom") or ""),
    )
    actual_mapping_rows = mapping_counts(actual_mapping)
    if expected_mapping and actual_mapping_rows != expected_mapping:
        diagnostics.append(
            Diagnostic("POLICY_JUNIT_EFFECTIVE_MAPPING_DRIFT", "junit-compatibility", "<mappings>", "test", "vintage_effective_mappings", "effective Vintage owner mapping differs from ledger")
        )
    if coverage_state.get("covered_source_count") is not None and (len(sources) - len(uncovered)) != coverage_state.get("covered_source_count"):
        diagnostics.append(
            Diagnostic("POLICY_JUNIT_COVERAGE_DRIFT", "junit-compatibility", "<sources>", "test", "effective_coverage", f"expected {coverage_state.get('covered_source_count')} covered sources, found {len(sources) - len(uncovered)}")
        )
    if coverage_state.get("uncovered_source_count") is not None and len(uncovered) != coverage_state.get("uncovered_source_count"):
        diagnostics.append(
            Diagnostic("POLICY_JUNIT_UNCOVERED_SOURCE", "junit-compatibility", ",".join(uncovered[:5] or ["<none>"]), "test", "effective_coverage", f"{len(uncovered)} JUnit 4 source files have no effective Vintage owner")
        )

    for mapping in required.get("vintage_effective_mappings") or []:
        path = str(mapping.get("declaration_pom") or "")
        if mapping.get("effective_source_count") == 0 and not matching_exception(group, path, "test", "state", f"unused-vintage:{path}", today):
            diagnostics.append(
                Diagnostic("POLICY_JUNIT_UNUSED_VINTAGE", "junit-compatibility", path, "test", "unused-vintage", "Vintage declaration owns no JUnit 4 source")
            )
    return diagnostics


def verify(root: Path, policy: dict[str, Any] | None = None) -> tuple[int, list[Diagnostic], dict[str, int]]:
    root = root.resolve()
    today = dt.datetime.now(dt.timezone.utc).date()
    if policy is None:
        policy = load_yaml_subset(root / POLICY_PATH)
    if policy.get("schema_version") != 1:
        raise InputError("dependency policy schema_version must be 1")
    validate_evidence_references(root, policy)
    maven = scan_maven(root)
    management = scan_maven_management(root)
    manifests = scan_package_json(root)
    diagnostics = []
    diagnostics.extend(validate_exceptions(root, policy, maven, today))
    diagnostics.extend(check_forbidden_maven(policy, maven, today))
    diagnostics.extend(check_frontend(policy, root, manifests, today))
    diagnostics.extend(check_approval(root))
    diagnostics.extend(check_junit(policy, root, maven, management, today))
    diagnostics = sorted(set(diagnostics), key=lambda item: (item.code, item.group, item.path, item.policy_id, item.reason))
    stats = {
        "groups": len(policy.get("dependency_groups") or []),
        "poms": len({path for path, _ in maven}),
        "package_json": len(manifests),
        "diagnostics": len(diagnostics),
    }
    return (1 if diagnostics else 0), diagnostics, stats


def main() -> int:
    try:
        code, diagnostics, stats = verify(Path.cwd())
    except InputError as exc:
        print(f"VERIFIER_INPUT_ERROR group=policy path={POLICY_PATH.as_posix()} usage=input policy=schema reason={exc}", file=sys.stderr)
        return 2
    if diagnostics:
        for diagnostic in diagnostics:
            print(diagnostic.line())
        return code
    print(
        "dependency-policy: PASS "
        f"groups={stats['groups']} poms={stats['poms']} package_json={stats['package_json']} diagnostics=0"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

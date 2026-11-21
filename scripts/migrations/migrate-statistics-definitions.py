#!/usr/bin/env python3
"""Convert an exported statistics directory into a new, reviewable directory."""
import argparse
import copy
import json
import re
import shutil
import sys
import tempfile
from pathlib import Path

NAME = re.compile(r"[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*\Z")
REMOVED_METADATA = {"appName", "module", "owner", "createdBy", "updatedBy", "publishedAt"}
# Skip SQL literals, quoted identifiers, comments and PostgreSQL casts.
SQL_TOKEN = re.compile(
    r"'(?:''|[^'])*'|\"(?:\"\"|[^\"])*\"|`(?:``|[^`])*`|"
    r"\$\$(?:[\s\S]*?)\$\$|--[^\n]*|/\*[\s\S]*?\*/|::|:([A-Za-z_][A-Za-z0-9_]*)"
)


def convert_sql(sql):
    return SQL_TOKEN.sub(lambda match: "${" + match[1] + "}" if match[1] else match[0], sql)


def identity(value):
    if not isinstance(value, str) or not NAME.fullmatch(value):
        raise ValueError("name must use dot-separated letters, digits, underscores or hyphens")
    return value.lower()


def convert_dataset(original):
    result = copy.deepcopy(original)
    identity(result.get("name"))
    legacy = any(field in result.get("queryDefinition", {}) for field in ("genericSql", "variants", "datasourceName"))
    status = result.pop("status", "draft" if legacy else None)
    if status is not None:
        if status not in ("draft", "validated", "published", "deprecated"):
            raise ValueError("unknown lifecycle status")
        result.setdefault("isEnabled", status == "published")
    for field in REMOVED_METADATA:
        result.pop(field, None)
    query = result.get("queryDefinition", {})
    if query.get("type") != "sql":
        raise ValueError("convert non-SQL queryDefinition to an explicit SQL Dataset first")
    if query.get("datasourceTags"):
        raise ValueError("resolve datasourceTags to one application datasource binding first")
    query.pop("datasourceTags", None)
    if "datasourceName" in query:
        old_source = query.pop("datasourceName") or "default"
        if "datasource" in query and query["datasource"] != old_source:
            raise ValueError("conflicting datasource and datasourceName")
        query["datasource"] = old_source
    query.setdefault("datasource", "default")
    if "genericSql" in query:
        old_sql = query.pop("genericSql")
        if query.get("sql") and query["sql"] != old_sql:
            raise ValueError("conflicting sql and genericSql")
        query["sql"] = old_sql
    dialects = query.setdefault("dialects", [])
    for variant in query.pop("variants", []) or []:
        if variant.get("datasourceTags") or variant.get("dialectVersionRange"):
            raise ValueError("resolve SQL variant tags/version constraints before converting")
        if not variant.get("dialect") or not variant.get("sqlText"):
            raise ValueError("SQL variant requires one dialect and SQL text")
        dialects.append({"dialectName": variant["dialect"], "sql": variant["sqlText"]})
    seen = set()
    for dialect in dialects:
        name = identity(dialect.get("dialectName"))
        if name in seen:
            raise ValueError("select one SQL candidate for each dialect before converting")
        seen.add(name)
        dialect["sql"] = convert_sql(dialect["sql"])
    if query.get("sql"):
        query["sql"] = convert_sql(query["sql"])
    if not query.get("sql") and not dialects:
        raise ValueError("Dataset requires SQL or a dialect SQL candidate")
    return result


def convert_configs(rows, connection_map):
    references = {key.lower(): value for key, value in connection_map.items()}
    converted = {}
    for row in rows:
        app = identity(row.get("appName"))
        dataset = identity(row.get("datasetName"))
        key = app + "/" + dataset
        connection = row.get("testConnection")
        if row.get("jdbcConnection"):
            if key not in references:
                raise ValueError(key + " requires a connection reference after registering its JDBC connection")
            connection = references[key]
        if connection and connection.get("kind") not in ("application", "platform", "runtime"):
            raise ValueError(key + " has an invalid connection reference kind")
        if connection and connection.get("kind") == "platform" and not connection.get("platformDataSourceName"):
            raise ValueError(key + " requires platformDataSourceName")
        if connection and connection.get("kind") == "runtime" and not connection.get("runtimeId"):
            raise ValueError(key + " requires runtimeId")
        if connection and set(connection) - {"kind", "platformDataSourceName", "envName", "runtimeId"}:
            raise ValueError(key + " connection contains fields outside the reference contract")
        config = {
            "appName": app, "datasetName": dataset,
            "datasource": row.get("datasource") or row.get("testDatasourceName") or "default",
            "parameters": row.get("parameters", row.get("testParameters", {})),
            "testConnection": connection or {"kind": "application"},
        }
        if key in converted and converted[key] != config:
            raise ValueError(key + " has conflicting historical dev-config/test-fixture rows")
        converted[key] = config
    return list(converted.values())


def plan(source, configs, connection_map):
    files = {}
    identities = set()
    errors = []
    for path in sorted(source.rglob("*.json")):
        relative = path.relative_to(source)
        try:
            if path.is_symlink():
                raise ValueError("symbolic links require an explicit source export")
            value = json.loads(path.read_text())
            if path.name.endswith(".dataset.json"):
                value = convert_dataset(value)
            elif path.name.endswith(".chart.json"):
                identity(value.get("name"))
            else:
                raise ValueError("convert legacy QueryView to a composite-view business model, ReportView to Chart, or Dashboard to a dashboard business model first")
            key = (str(relative.parent).lower(), identity(value.get("name")))
            if key in identities:
                raise ValueError("case-insensitive definition identity conflicts in the same source directory")
            identities.add(key)
            files[str(relative)] = value
        except (ValueError, TypeError, KeyError) as error:
            errors.append(str(relative) + ": " + str(error))
    if configs is not None:
        try:
            files["dev-configs.json"] = convert_configs(configs, connection_map)
        except (ValueError, TypeError, KeyError) as error:
            errors.append("dev-configs: " + str(error))
    return files, errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="exported statistics-reporting directory")
    parser.add_argument("--output", type=Path, required=True, help="new output directory")
    parser.add_argument("--config-input", type=Path, help="JSON array exported from dev-config and/or test-fixture storage")
    parser.add_argument("--connection-map", type=Path, help="JSON map keyed by appName/datasetName with platform/runtime references")
    parser.add_argument("--apply", action="store_true", help="write the validated conversion to the new output directory")
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve()
    if not source.is_dir() or output.exists() or output.is_relative_to(source):
        parser.error("source must exist and output must be a new directory outside source")
    configs = json.loads(args.config_input.read_text()) if args.config_input else None
    connections = json.loads(args.connection_map.read_text()) if args.connection_map else {}
    files, errors = plan(source, configs, connections)
    if errors:
        print(json.dumps({"errors": errors}, ensure_ascii=False, indent=2))
        return 1
    print(json.dumps({"files": list(files), "applied": args.apply}, ensure_ascii=False, indent=2))
    if args.apply:
        output.parent.mkdir(parents=True, exist_ok=True)
        staging = Path(tempfile.mkdtemp(prefix=".statistics-conversion-", dir=output.parent))
        try:
            for relative, value in files.items():
                target = staging / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
            staging.rename(output)
        finally:
            if staging.exists():
                shutil.rmtree(staging)
    return 0


if __name__ == "__main__":
    sys.exit(main())

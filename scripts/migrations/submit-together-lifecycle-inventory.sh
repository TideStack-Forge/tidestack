#!/usr/bin/env bash
set -euo pipefail

MODE="pre-cutover"
APP_ARTIFACT=""
APP_HOME=""
REPOSITORY_ROOT="$(pwd)"
OUTPUT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="$2"
      shift 2
      ;;
    --app-artifact|--app-jar)
      APP_ARTIFACT="$2"
      shift 2
      ;;
    --app-home)
      APP_HOME="$2"
      shift 2
      ;;
    --repository-root)
      REPOSITORY_ROOT="$2"
      shift 2
      ;;
    --output)
      OUTPUT="$2"
      shift 2
      ;;
    *)
      echo "unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$APP_ARTIFACT" || -z "$APP_HOME" || -z "$REPOSITORY_ROOT" || -z "$OUTPUT" ]]; then
  echo "--app-artifact/--app-jar, --app-home, --repository-root and --output are required" >&2
  exit 2
fi

if [[ ! -f "$APP_ARTIFACT" ]]; then
  echo "app artifact does not exist: $APP_ARTIFACT" >&2
  exit 2
fi

if [[ ! -d "$APP_HOME" ]]; then
  echo "appHome does not exist or is not a directory: $APP_HOME" >&2
  exit 2
fi

if [[ ! -d "$REPOSITORY_ROOT" ]]; then
  echo "repository root does not exist or is not a directory: $REPOSITORY_ROOT" >&2
  exit 2
fi

python3 - "$MODE" "$APP_ARTIFACT" "$APP_HOME" "$REPOSITORY_ROOT" "$OUTPUT" <<'PY'
import hashlib
import json
import os
import re
import sys
import zipfile
from pathlib import Path

mode = sys.argv[1]
artifact = Path(sys.argv[2]).resolve()
app_home = Path(sys.argv[3]).resolve()
repository_root = Path(sys.argv[4]).resolve()
output = Path(sys.argv[5])

PROVIDER_SERVICES = [
    {
        "kind": "business-model-meta-provider",
        "service": "com.ouroboros.businessmodel.BusinessModelMetaProvider",
        "effectiveRule": "BusinessModelMetaCenter: sorted map merge, reversed first-present lookup",
    },
    {
        "kind": "flow-model-meta-provider",
        "service": "com.ouroboros.logicflow.model.FlowModelMetaProvider",
        "effectiveRule": "FlowModelMetaCenter: sorted map merge, reversed first enabled lookup",
    },
    {
        "kind": "flow-model-provider",
        "service": "com.ouroboros.logicflow.model.FlowModelProvider",
        "effectiveRule": "FlowModelCenter: reversed provider order, first enabled model wins",
    },
    {
        "kind": "data-model-meta-provider",
        "service": "com.ouroboros.data.model.DataModelMetaProvider",
        "effectiveRule": "DataModelMetaCenter: sorted map merge, reversed first-present lookup",
    },
]

TYPED_SOURCES = [
    {
        "kind": "typed-core-annotation-contract",
        "path": "core/ouroboros-data/ouroboros-data-typed-core/src/main/java/com/ouroboros/data/annotation",
    },
    {
        "kind": "typed-runtime-provider",
        "path": "core/ouroboros-data/ouroboros-data-typed-runtime/src/main/java/com/ouroboros/data/typed/runtime/SpringTypedModelMetaProvider.java",
    },
    {
        "kind": "typed-model-decorator-spi",
        "path": "core/ouroboros-data/ouroboros-data-plugins/src/main/resources/META-INF/services/com.ouroboros.data.model.TypedModelAnnotationDecorator",
    },
    {
        "kind": "typed-field-decorator-spi",
        "path": "core/ouroboros-data/ouroboros-data-plugins/src/main/resources/META-INF/services/com.ouroboros.data.model.TypedFieldAnnotationDecorator",
    },
]

SUBMIT_TOGETHER_PATTERN = re.compile(r'"submitTogether"\s*:\s*true')
EMPTY_TREE_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
IGNORED_PATH_PARTS = {
    ".git",
    ".worktrees",
    ".ai-workflows",
    ".sisyphus",
    ".aider-desk",
    ".gstack",
    "build-layer",
    "common-layer",
    "node_modules",
    "template-layer",
    "target",
    "__pycache__",
}

SCAN_STATS: dict[str, dict[str, object]] = {}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def iter_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for current, dir_names, file_names in os.walk(root):
        dir_names[:] = sorted(
            name
            for name in dir_names
            if name not in IGNORED_PATH_PARTS
        )
        current_path = Path(current)
        for file_name in sorted(file_names):
            path = current_path / file_name
            if path.is_file() and not ignored(root, path):
                files.append(path)
    return files


def tree_hash(path: Path, stats_key: str, require_non_empty: bool = False) -> str:
    digest = hashlib.sha256()
    files = iter_files(path)
    SCAN_STATS[stats_key] = {
        "scannedFileCount": len(files),
        "sourceComplete": bool(files) or not require_non_empty,
    }
    if require_non_empty and not files:
        return EMPTY_TREE_SHA256
    for child in files:
        digest.update(str(child.relative_to(path)).encode("utf-8"))
        digest.update(b"\0")
        digest.update(sha256_file(child).encode("ascii"))
        digest.update(b"\0")
    return digest.hexdigest()


def ignored(root: Path, path: Path) -> bool:
    try:
        parts = path.relative_to(root).parts
    except ValueError:
        parts = path.parts
    return any(part in IGNORED_PATH_PARTS for part in parts)


def read_service_file(path: Path) -> list[str]:
    providers: list[str] = []
    if not path.exists():
        return providers
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        providers.append(line)
    return providers


def repository_service_descriptors(service: str) -> list[dict[str, object]]:
    descriptors: list[dict[str, object]] = []
    service_suffix = Path("META-INF/services") / service
    suffix_parts = service_suffix.parts
    for service_file in iter_files(repository_root):
        if service_file.parts[-len(suffix_parts):] != suffix_parts:
            continue
        providers = read_service_file(service_file)
        descriptors.append({
            "source": str(service_file),
            "providers": providers,
            "count": len(providers),
        })
    return descriptors


def artifact_service_descriptor(service: str) -> dict[str, object]:
    service_name = "META-INF/services/" + service
    if not zipfile.is_zipfile(artifact):
        return {
            "source": str(artifact),
            "archiveReadable": False,
            "providers": [],
            "count": 0,
        }
    with zipfile.ZipFile(artifact) as archive:
        if service_name not in archive.namelist():
            return {
                "source": str(artifact),
                "archiveReadable": True,
                "providers": [],
                "count": 0,
            }
        text = archive.read(service_name).decode("utf-8")
    providers = [
        line.strip()
        for line in text.splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]
    return {
        "source": str(artifact),
        "archiveReadable": True,
        "providers": providers,
        "count": len(providers),
    }


def app_home_service_descriptor(service: str) -> list[dict[str, object]]:
    descriptors: list[dict[str, object]] = []
    service_suffix = "META-INF/services/" + service
    for service_file in sorted(app_home.rglob(service_suffix)):
        providers = read_service_file(service_file)
        descriptors.append({
            "source": str(service_file),
            "providers": providers,
            "count": len(providers),
        })
    return descriptors


def source_ledger() -> list[dict[str, object]]:
    app_home_hash = tree_hash(app_home, "appHome")
    repository_hash = tree_hash(repository_root, "repository", require_non_empty=True)
    app_home_stats = SCAN_STATS["appHome"]
    repository_stats = SCAN_STATS["repository"]
    ledger: list[dict[str, object]] = [
        {
            "type": "application-artifact",
            "source": str(artifact),
            "artifactSha256": sha256_file(artifact),
            "sourceComplete": True,
        },
        {
            "type": "appHome",
            "source": str(app_home),
            "appHomeHash": app_home_hash,
            "scannedFileCount": app_home_stats["scannedFileCount"],
            "sourceComplete": app_home_stats["sourceComplete"],
        },
        {
            "type": "repository",
            "source": str(repository_root),
            "repositoryHash": repository_hash,
            "scannedFileCount": repository_stats["scannedFileCount"],
            "sourceComplete": repository_stats["sourceComplete"],
        },
    ]
    for provider in PROVIDER_SERVICES:
        service = str(provider["service"])
        repository_descriptors = repository_service_descriptors(service)
        app_home_descriptors = app_home_service_descriptor(service)
        artifact_descriptor = artifact_service_descriptor(service)
        all_providers: list[str] = []
        for descriptor in repository_descriptors:
            all_providers.extend(descriptor["providers"])  # type: ignore[arg-type]
        for descriptor in app_home_descriptors:
            all_providers.extend(descriptor["providers"])  # type: ignore[arg-type]
        all_providers.extend(artifact_descriptor["providers"])  # type: ignore[arg-type]
        ledger.append({
            "type": "provider-spi",
            "kind": provider["kind"],
            "service": service,
            "effectiveRule": provider["effectiveRule"],
            "repositoryDescriptors": repository_descriptors,
            "appHomeDescriptors": app_home_descriptors,
            "artifactDescriptor": artifact_descriptor,
            "providerOrder": all_providers,
            "sourceComplete": True,
        })
    for source in TYPED_SOURCES:
        path = repository_root / str(source["path"])
        ledger.append({
            "type": "typed-runtime-source",
            "kind": source["kind"],
            "source": str(path),
            "sourceComplete": path.exists(),
        })
    return ledger


def load_json(path: Path) -> object | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None


def sha256_text(value: str | None) -> str | None:
    if value is None:
        return None
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def find_submit_together_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for path in iter_files(root):
        if path.suffix.lower() in {".json", ".yaml", ".yml"} and SUBMIT_TOGETHER_PATTERN.search(
            path.read_text(encoding="utf-8", errors="ignore")
        ):
            files.append(path)
    return sorted(set(files))


def collect_entries() -> list[dict[str, object]]:
    entries: list[dict[str, object]] = []
    for path in find_submit_together_files(app_home):
        document = load_json(path)
        entry: dict[str, object] = {
            "id": str(path.relative_to(app_home)),
            "source": str(path),
            "ownerModel": None,
            "relation": None,
            "relationPath": str(path.relative_to(app_home)),
            "childModel": None,
            "referenceKey": None,
            "childFlows": {
                "insert": {"state": "unknown"},
                "update": {"state": "unknown"},
                "delete": {"state": "unknown"},
            },
            "beforeScript": {"present": False, "sha256": None},
            "afterScript": {"present": False, "sha256": None},
            "flowSpecificPlugins": [],
            "effectivePlugins": [],
            "issues": [{
                "id": str(path.relative_to(app_home)) + ":runtime-diagnostics-required",
                "category": "source",
                "severity": "blocking",
                "resolution": "pending",
                "message": "submitTogether metadata was found in appHome; run the runtime collector to attach canonical effective DataModel diagnostics before cutover",
            }],
        }
        if isinstance(document, dict):
            entry["ownerModel"] = document.get("fullName") or document.get("name")
            before_script = document.get("beforeScript")
            after_script = document.get("afterScript")
            entry["beforeScript"] = {"present": bool(before_script), "sha256": sha256_text(before_script)}
            entry["afterScript"] = {"present": bool(after_script), "sha256": sha256_text(after_script)}
            plugins = document.get("modelPluginDescriptors") or document.get("plugins")
            entry["flowSpecificPlugins"] = plugins if isinstance(plugins, list) else []
        entries.append(entry)
    return entries


ledger = source_ledger()
source_complete = all(
    source.get("sourceComplete") is True and source.get("enumerable", True) is not False
    for source in ledger
)

payload = {
    "schemaVersion": "submit-together-lifecycle-inventory/v1",
    "mode": mode,
    "application": {
        "artifactPath": str(artifact),
        "artifactSha256": sha256_file(artifact),
        "appHome": str(app_home),
        "appHomeHash": next(source["appHomeHash"] for source in ledger if source.get("type") == "appHome"),
        "repositoryRoot": str(repository_root),
        "repositoryHash": next(source["repositoryHash"] for source in ledger if source.get("type") == "repository"),
    },
    "sourceComplete": source_complete,
    "sourceLedger": ledger,
    "entries": collect_entries(),
}
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

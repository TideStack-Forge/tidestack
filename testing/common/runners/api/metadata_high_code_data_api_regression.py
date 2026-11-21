import argparse
import importlib
import json
import os
import sys
import time
from pathlib import Path
from types import SimpleNamespace


ROOT = Path(__file__).resolve().parents[4]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))

from testing.common.runners.fixture_registry import (  # noqa: E402
    first_fixture_output,
    resolve_scenario_fixture_bundle,
)
from testing.common.runners.fixture_preflight import (  # noqa: E402
    collect_fixture_preflight_failures,
    preflight_fixture_bundle,
    resolve_frontend_inputs,
    resolve_runtime_inputs,
    write_fixture_preflight_report,
)

DEFAULT_SCENARIO = ROOT / "testing/integration/regression/data-api/metadata-high-code-regression/scenario.yaml"


def _load_manifest(path):
  manifest_path = Path(path)
  return json.loads(manifest_path.read_text())


def _resolve_path(path_value):
  if not path_value:
    return None
  path = Path(path_value)
  if path.is_absolute():
    return path
  return ROOT / path


def _default_report_dir(manifest, run_label=None):
  bridge = manifest["legacyBridge"]
  evidence_root = _resolve_path(bridge["evidenceDir"])
  if run_label:
    return evidence_root / run_label
  return evidence_root / time.strftime("%Y%m%d-%H%M%S")


def build_legacy_args(manifest, cli_args, fixture_bundle=None, runtime_inputs=None):
  bridge = manifest["legacyBridge"]
  fixture_bundle = fixture_bundle or {}
  runtime_inputs = runtime_inputs or {}
  report_dir = _resolve_path(cli_args.report_dir) if cli_args.report_dir else _default_report_dir(manifest, cli_args.run_label)
  docs_report_dir = _resolve_path(cli_args.docs_report_dir or bridge["docsReportDir"])
  default_app_root = (
      cli_args.app_root
      or first_fixture_output(fixture_bundle, "data", "appRoot", cli_args=cli_args, env=os.environ)
      or bridge.get("appRoot")
  )
  app_root = _resolve_path(default_app_root) if default_app_root else None
  login_payload = first_fixture_output(fixture_bundle, "identities", "loginPayload")
  base_url = cli_args.base_url or runtime_inputs.get("develop", {}).get("baseUrl")
  app_base_url = cli_args.app_base_url or runtime_inputs.get("mothership", {}).get("baseUrl")

  legacy_args = SimpleNamespace(
      base_url=base_url,
      inventory_only=cli_args.mode == "inventory",
      execute=cli_args.mode == "execute",
      report_dir=str(report_dir),
      docs_report_dir=str(docs_report_dir),
      app_root=str(app_root) if app_root else None,
      company_id=cli_args.company_id,
      execution_ring=cli_args.execution_ring or bridge.get("defaultExecutionRing", "metadata-list"),
      token_file=cli_args.token_file,
      app_base_url=app_base_url,
      app_token_file=cli_args.app_token_file,
      login_payload=login_payload,
  )
  legacy_args.fixture_bundle = fixture_bundle
  legacy_args.fixture_runtime_inputs = runtime_inputs
  return legacy_args


def parse_args(argv=None):
  parser = argparse.ArgumentParser(description="Bridge metadata and high-code data API regression scenario into testing/")
  parser.add_argument("--scenario", default=str(DEFAULT_SCENARIO))
  parser.add_argument("--mode", choices=("inventory", "execute"), default="inventory")
  parser.add_argument("--run-label")
  parser.add_argument("--base-url")
  parser.add_argument("--app-base-url")
  parser.add_argument("--company-id")
  parser.add_argument("--execution-ring")
  parser.add_argument("--token-file")
  parser.add_argument("--app-token-file")
  parser.add_argument("--app-root")
  parser.add_argument("--repo", action="append", default=[], help="repository binding in NAME=PATH form; repeat for multiple repositories")
  parser.add_argument("--report-dir")
  parser.add_argument("--docs-report-dir")
  return parser.parse_args(argv)


def main(argv=None):
  cli_args = parse_args(argv)
  manifest = _load_manifest(cli_args.scenario)
  try:
    fixture_bundle = resolve_scenario_fixture_bundle(manifest)
  except (FileNotFoundError, ValueError) as exc:
    raise SystemExit(str(exc))
  runtime_inputs = resolve_runtime_inputs(fixture_bundle, cli_args, os.environ)
  frontend_inputs = resolve_frontend_inputs(fixture_bundle, os.environ)
  fixture_preflight = preflight_fixture_bundle(
      fixture_bundle,
      runtime_inputs,
      frontend_inputs,
      cli_args=cli_args,
      env=os.environ,
  )
  preflight_failures = collect_fixture_preflight_failures(fixture_preflight)
  supported_rings = set(manifest.get("legacyBridge", {}).get("supportedExecutionRings", []))
  if cli_args.mode == "execute" and supported_rings and cli_args.execution_ring and cli_args.execution_ring not in supported_rings:
    raise SystemExit(f"unsupported execution ring: {cli_args.execution_ring}")
  legacy_args = build_legacy_args(manifest, cli_args, fixture_bundle, runtime_inputs)
  write_fixture_preflight_report(fixture_preflight, legacy_args.report_dir)
  if preflight_failures:
    message = "; ".join(
        f"{failure.get('fixture_id')}: {failure.get('message')}"
        for failure in preflight_failures
    )
    raise SystemExit(f"fixture preflight failed: {message}")
  if legacy_args.execute and not legacy_args.base_url:
    raise SystemExit("--base-url is required when --mode execute")
  if not legacy_args.app_root:
    raise SystemExit("app root is required; provide --app-root, --repo app-metadata=/abs/path, or configure a data fixture input source")

  legacy_runner = importlib.import_module(manifest["legacyBridge"]["module"])
  return legacy_runner.run(legacy_args)


if __name__ == "__main__":
  raise SystemExit(main())

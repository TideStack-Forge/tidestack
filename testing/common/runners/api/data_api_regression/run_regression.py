import argparse
import json
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path
from urllib import error, parse, request


ROOT = Path(__file__).resolve().parents[5]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))

from testing.common.runners.api.data_api_regression.case_matrix import (  # noqa: E402
    build_case_matrix,
    compute_coverage_denominator,
    compute_model_sampling_coverage,
)
from testing.common.runners.api.data_api_regression.high_code_catalog import scan_controller_tree  # noqa: E402
from testing.common.runners.api.data_api_regression.inventory import (  # noqa: E402
    FILTER_VALUE_PLACEHOLDER_COMPANY_ID,
    collect_metadata_inventory,
)
from testing.common.runners.api.data_api_regression.reporting import (  # noqa: E402
    render_case_markdown,
    render_inventory_markdown,
    render_summary_report,
    write_case_csv,
    write_json,
    write_text,
)


REPORT_DATE = "2026-03-15"
REPORT_FILE_NAMES = {
    "inventory_md": f"{REPORT_DATE}-major-refactor-data-api-inventory.md",
    "inventory_json": f"{REPORT_DATE}-major-refactor-data-api-inventory.json",
    "cases_md": f"{REPORT_DATE}-major-refactor-data-api-test-cases.md",
    "cases_csv": f"{REPORT_DATE}-major-refactor-data-api-test-cases.csv",
    "summary_md": f"{REPORT_DATE}-major-refactor-metadata-high-code-data-api-regression-report.md",
}

HIGH_CODE_CONTROLLER_DIRS = [
    "security/ouroboros-web-security/ouroboros-web-security-rbac-manage/src/main/java/com/ouroboros/security/rbac/controller",
    "ability/ouroboros-organization-structure/ouroboros-organization-structure-runtime/src/main/java/com/ouroboros/organizationstructure/controller",
    "app-module/document-management/document-management-webapp/src/main/java/com/ouroboros/documentmanagement/webapp/controller",
    "ability/ouroboros-data-dict/ouroboros-data-dict-runtime/src/main/java/com/ouroboros/data/dict/controller",
    "ability/ouroboros-configuration/ouroboros-configuration-runtime/src/main/java/com/ouroboros/configuration/controller",
    "ability/ouroboros-user-setting/ouroboros-user-setting-runtime/src/main/java/com/ouroboros/usersetting/controller",
    "ability/ouroboros-notification/ouroboros-notification-runtime/src/main/java/com/ouroboros/notification/controller",
    "ability/ouroboros-business-log/ouroboros-business-log-runtime/src/main/java/com/ouroboros/businesslog/controller",
]

DEFAULT_LOGIN_PAYLOAD = {
    "account": "admin",
    "password": "666666",
}
LOCAL_RUNTIME_HOSTS = {"127.0.0.1", "localhost", "::1"}
REQUEST_TIMEOUT_SECONDS = 60
APP_NAMESPACE_PREFIX = "app."
PRIMARY_KEY_SAMPLE_PREFIX = "pk-"
DEFAULT_PRIMARY_KEY_SAMPLE_MAX_LENGTH = 20
RUNTIME_BASELINE_INVALID = "runtime-baseline-invalid"
RUNTIME_HTTP_PROBE_PATH = "/api/auth/authorities"
RUNTIME_HTTP_PROBE_WARMUP_RETRY_ATTEMPTS = 240
RUNTIME_HTTP_PROBE_WARMUP_RETRY_INTERVAL_SECONDS = 2
METADATA_GATE_THRESHOLD = 90.0
PRIMARY_SEED_GATE_THRESHOLD = 100.0
EXECUTION_PRECONDITION_GAP = "execution-precondition-gap"
UNEXPECTED_RESPONSE_FORMAT = "unexpected-response-format"
RUNTIME_AUTH_INVALID = "runtime-auth-invalid"


class RuntimeDependencyResolutionError(RuntimeError):

  def __init__(self, field_name, classification, message, http_status=None, business_code=None):
    super().__init__(message)
    self.field_name = field_name
    self.classification = classification
    self.http_status = http_status
    self.business_code = business_code


class ExecutionPreconditionError(RuntimeError):

  def __init__(self, message):
    super().__init__(message)


def _load_seed_cases():
  seed_file = ROOT / "testing/common/runners/api/data_api_regression/high_code_api_seed.json"
  return json.loads(seed_file.read_text())


def _collect_high_code_inventory():
  controller_files = []
  for relative_dir in HIGH_CODE_CONTROLLER_DIRS:
    controller_dir = ROOT / relative_dir
    if controller_dir.exists():
      controller_files.extend(sorted(controller_dir.glob("*.java")))

  records = []
  for record in scan_controller_tree(controller_files):
    records.append({
        "source_kind": "high-code",
        "model_key": record["controller"],
        "api_name": record["method"],
        "urls": [record["url"]],
        "risk": "P0" if "/children" in record["url"] or "/role" in record["url"] else "P1",
        "source_file": record["source_file"],
    })
  return records


def _assign_inventory_risks(metadata_records):
  enriched = []
  for record in metadata_records:
    risk = "P0" if record.get("has_before_script") or record.get("has_detail") else "P1"
    if record.get("api_name") not in ("search", "getOne", "add", "update", "delete"):
      risk = "P2"
    updated = dict(record)
    updated["risk"] = risk
    enriched.append(updated)
  return enriched


def _build_inventory(app_root):
  app_root = Path(app_root)
  metadata_records = collect_metadata_inventory(app_root / "business-model", app_root / "ui-schema")
  metadata_records = _assign_inventory_risks(metadata_records)
  high_code_records = _collect_high_code_inventory()
  all_records = metadata_records + high_code_records
  summary = {
      "metadata": len(metadata_records),
      "high_code": len(high_code_records),
      "total": len(all_records),
      "p0": sum(1 for record in all_records if record.get("risk") == "P0"),
      "p1": sum(1 for record in all_records if record.get("risk") == "P1"),
      "p2": sum(1 for record in all_records if record.get("risk") == "P2"),
  }
  return all_records, summary


def _write_inventory_and_case_artifacts(inventory_records, inventory_summary, cases, docs_report_dir):
  docs_report_dir = Path(docs_report_dir)
  docs_report_dir.mkdir(parents=True, exist_ok=True)

  write_json({"summary": inventory_summary, "items": inventory_records}, docs_report_dir / REPORT_FILE_NAMES["inventory_json"])
  write_text(
      render_inventory_markdown(inventory_records, inventory_summary),
      docs_report_dir / REPORT_FILE_NAMES["inventory_md"],
  )

  coverage_denominator = compute_coverage_denominator(cases, ("P0", "P1"))
  coverage_numerator = sum(
      1 for case in cases
      if case.get("risk") in ("P0", "P1") and case.get("actual_result")
  )
  write_case_csv(cases, docs_report_dir / REPORT_FILE_NAMES["cases_csv"])
  write_text(
      render_case_markdown(cases, coverage_denominator, coverage_numerator),
      docs_report_dir / REPORT_FILE_NAMES["cases_md"],
  )


def _write_results_placeholder(report_dir, inventory_summary, cases):
  report_dir = Path(report_dir)
  report_dir.mkdir(parents=True, exist_ok=True)
  case_coverage_denominator = compute_coverage_denominator(cases, ("P0", "P1"))
  business_model_coverage = compute_model_sampling_coverage(cases, [], ("list", "query-conditions"))
  metadata_read_coverage = compute_model_sampling_coverage(cases, [], ("list", "query-conditions"))
  metadata_detail_coverage = compute_model_sampling_coverage(cases, [], ("detail",))
  metadata_write_coverage = compute_model_sampling_coverage(
      cases,
      [],
      ("add", "update", "delete"),
      case_predicate=lambda case: case.get("stage_one_write_executable"),
  )
  complex_query_model_coverage = compute_model_sampling_coverage(cases, [], ("query-conditions",))
  high_code_primary_read_coverage = _compute_high_code_seed_case_coverage(cases, [], bucket="primary", method_group="read")
  high_code_primary_write_coverage = _compute_high_code_seed_case_coverage(cases, [], bucket="primary", method_group="write")
  results = {
      "inventory_summary": inventory_summary,
      "coverage_denominator": business_model_coverage["eligible_model_count"],
      "coverage_numerator": 0,
      "coverage_percent": 0,
      "business_model_coverage_percent": 0,
      "eligible_business_model_count": business_model_coverage["eligible_model_count"],
      "sampled_business_model_count": 0,
      "metadata_read_coverage_percent": 0,
      "eligible_metadata_read_model_count": metadata_read_coverage["eligible_model_count"],
      "sampled_metadata_read_model_count": 0,
      "metadata_detail_coverage_percent": 0,
      "eligible_metadata_detail_model_count": metadata_detail_coverage["eligible_model_count"],
      "sampled_metadata_detail_model_count": 0,
      "metadata_write_coverage_percent": 0,
      "eligible_metadata_write_model_count": metadata_write_coverage["eligible_model_count"],
      "sampled_metadata_write_model_count": 0,
      "complex_query_model_coverage_percent": 0,
      "eligible_complex_query_model_count": complex_query_model_coverage["eligible_model_count"],
      "sampled_complex_query_model_count": 0,
      "high_code_primary_read_coverage_percent": 0,
      "eligible_high_code_primary_read_case_count": high_code_primary_read_coverage["eligible_case_count"],
      "sampled_high_code_primary_read_case_count": 0,
      "pending_high_code_primary_read_case_count": high_code_primary_read_coverage["pending_case_count"],
      "high_code_primary_write_coverage_percent": 0,
      "eligible_high_code_primary_write_case_count": high_code_primary_write_coverage["eligible_case_count"],
      "sampled_high_code_primary_write_case_count": 0,
      "pending_high_code_primary_write_case_count": high_code_primary_write_coverage["pending_case_count"],
      "case_coverage_denominator": case_coverage_denominator,
      "case_coverage_numerator": 0,
      "case_coverage_percent": 0,
      "executed_cases": [],
      "failures": [],
  }
  write_json(results, report_dir / "results.json")

  summary = {
      "coverage_percent": 0,
      "coverage_denominator": business_model_coverage["eligible_model_count"],
      "coverage_numerator": 0,
      "business_model_coverage_percent": 0,
      "eligible_business_model_count": business_model_coverage["eligible_model_count"],
      "sampled_business_model_count": 0,
      "metadata_read_coverage_percent": 0,
      "eligible_metadata_read_model_count": metadata_read_coverage["eligible_model_count"],
      "sampled_metadata_read_model_count": 0,
      "metadata_detail_coverage_percent": 0,
      "eligible_metadata_detail_model_count": metadata_detail_coverage["eligible_model_count"],
      "sampled_metadata_detail_model_count": 0,
      "metadata_write_coverage_percent": 0,
      "eligible_metadata_write_model_count": metadata_write_coverage["eligible_model_count"],
      "sampled_metadata_write_model_count": 0,
      "complex_query_model_coverage_percent": 0,
      "eligible_complex_query_model_count": complex_query_model_coverage["eligible_model_count"],
      "sampled_complex_query_model_count": 0,
      "high_code_primary_read_coverage_percent": 0,
      "eligible_high_code_primary_read_case_count": high_code_primary_read_coverage["eligible_case_count"],
      "sampled_high_code_primary_read_case_count": 0,
      "pending_high_code_primary_read_case_count": high_code_primary_read_coverage["pending_case_count"],
      "high_code_primary_write_coverage_percent": 0,
      "eligible_high_code_primary_write_case_count": high_code_primary_write_coverage["eligible_case_count"],
      "sampled_high_code_primary_write_case_count": 0,
      "pending_high_code_primary_write_case_count": high_code_primary_write_coverage["pending_case_count"],
      "case_coverage_denominator": case_coverage_denominator,
      "case_coverage_numerator": 0,
      "case_coverage_percent": 0,
      "pass_count": 0,
      "fail_count": 0,
      "failures": [],
      "merge_readiness": "暂不具备合并条件",
  }
  return summary


def _has_unresolved_path_params(endpoint):
  return "{" in (endpoint or "") or "$" in (endpoint or "")


def _is_high_code_seed_read_executable(case):
  return (
      case.get("source_kind") == "high-code-seed"
      and case.get("method") == "GET"
      and not _has_unresolved_path_params(case.get("endpoint", ""))
  )


def _is_high_code_seed_write_executable(case):
  return (
      case.get("source_kind") == "high-code-seed"
      and case.get("method") != "GET"
      and case.get("stage_one_write_executable")
      and not _has_unresolved_path_params(case.get("endpoint", ""))
  )


def select_execution_cases(cases, execution_ring):
  if execution_ring == "metadata-list":
    target_scenarios = {"list"}
  elif execution_ring == "metadata-detail":
    target_scenarios = {"detail"}
  elif execution_ring == "metadata-query-conditions":
    target_scenarios = {"query-conditions"}
  elif execution_ring == "metadata-create":
    target_scenarios = {"add"}
  elif execution_ring == "metadata-update":
    target_scenarios = {"update"}
  elif execution_ring == "metadata-delete":
    target_scenarios = {"delete"}
  elif execution_ring == "high-code-read":
    return [
        case for case in cases
        if _is_high_code_seed_read_executable(case)
        and case.get("regression_bucket", "primary") == "primary"
    ]
  elif execution_ring == "high-code-special-read":
    return [
        case for case in cases
        if _is_high_code_seed_read_executable(case)
        and case.get("regression_bucket", "primary") != "primary"
    ]
  elif execution_ring == "high-code-write":
    return [
        case for case in cases
        if _is_high_code_seed_write_executable(case)
        and case.get("regression_bucket", "primary") == "primary"
    ]
  elif execution_ring == "high-code-special-write":
    return [
        case for case in cases
        if _is_high_code_seed_write_executable(case)
        and case.get("regression_bucket", "primary") != "primary"
    ]
  else:
    return []

  if execution_ring in ("metadata-create", "metadata-update", "metadata-delete"):
    return [
        case for case in cases
        if case.get("source_kind") == "metadata"
        and case.get("scenario") in target_scenarios
        and case.get("stage_one_write_executable")
        and case.get("sample_payload")
    ]

  if execution_ring == "metadata-detail":
    return [
        case for case in cases
        if case.get("source_kind") == "metadata"
        and case.get("scenario") == "detail"
        and case.get("method") == "GET"
    ]

  return [
      case for case in cases
      if case.get("source_kind") == "metadata"
      and case.get("scenario") in target_scenarios
      and case.get("method") == "GET"
      and not _has_unresolved_path_params(case.get("endpoint", ""))
  ]


def classify_execution_result(http_status, business_code, payload_parse_error=""):
  if payload_parse_error and http_status in (200, 201, 204):
    return {
        "actual_result": "FAIL",
        "classification": UNEXPECTED_RESPONSE_FORMAT,
    }

  if http_status in (200, 201) and business_code == 0:
    return {
        "actual_result": "PASS",
        "classification": "pass",
    }

  if http_status in (200, 201, 204) and business_code in (0, None):
    return {
        "actual_result": "PASS",
        "classification": "pass",
    }

  if http_status in (401, 403):
    return {
        "actual_result": "FAIL",
        "classification": "permission-boundary",
    }

  if business_code == -4000:
    return {
        "actual_result": "FAIL",
        "classification": "environment-gap",
    }

  return {
      "actual_result": "FAIL",
      "classification": "regression",
  }


def classify_case_execution(case, http_status, business_code, payload=None, payload_parse_error=""):
  payload = payload or {}
  message = payload.get("msg") if isinstance(payload, dict) else None
  if (
      case.get("reserved_route_conflict")
      and http_status == 405
      and business_code == -1
      and isinstance(message, str)
      and "Request method" in message
  ):
    return {
        "actual_result": "FAIL",
        "classification": "reserved-route-conflict",
    }
  return classify_execution_result(http_status, business_code, payload_parse_error)


def _resolve_case_target(case, default_base_url, app_base_url=None):
  module = case.get("module", "")
  if app_base_url and module.startswith(APP_NAMESPACE_PREFIX):
    return app_base_url.rstrip("/"), "mothership"
  return default_base_url.rstrip("/"), "default"


def _collect_runtime_targets(base_url, app_base_url=None, selected_cases=None):
  if selected_cases is None:
    targets = [
        {
            "target": "default",
            "base_url": base_url.rstrip("/"),
        }
    ]
    if app_base_url:
      targets.append({
          "target": "mothership",
          "base_url": app_base_url.rstrip("/"),
      })
    return targets

  targets = []
  seen_targets = set()
  for case in selected_cases:
    target_base_url, target_name = _resolve_case_target(case, base_url, app_base_url)
    if target_name in seen_targets:
      continue
    seen_targets.add(target_name)
    targets.append({
        "target": target_name,
        "base_url": target_base_url,
    })
  return targets


def _compute_high_code_seed_case_coverage(cases, executed_cases, bucket, method_group):
  indexed_cases = {
      case.get("case_id"): case
      for case in cases
      if case.get("case_id")
  }
  if method_group == "read":
    method_matcher = lambda case: case.get("method") == "GET"
    executable_matcher = _is_high_code_seed_read_executable
  else:
    method_matcher = lambda case: case.get("method") != "GET"
    executable_matcher = _is_high_code_seed_write_executable

  candidate_cases = [
      case for case in cases
      if case.get("source_kind") == "high-code-seed"
      and case.get("regression_bucket", "primary") == bucket
      and method_matcher(case)
  ]
  eligible_cases = [
      case for case in candidate_cases
      if executable_matcher(case)
  ]
  pending_cases = [
      case for case in candidate_cases
      if not executable_matcher(case)
  ]
  sampled_cases = [
      indexed_cases[executed_case.get("case_id")]
      for executed_case in executed_cases
      if executed_case.get("case_id") in indexed_cases
      and indexed_cases[executed_case.get("case_id")] in eligible_cases
  ]

  coverage_percent = 0
  if eligible_cases:
    coverage_percent = round(len(sampled_cases) / len(eligible_cases) * 100, 2)

  return {
      "eligible_case_count": len(eligible_cases),
      "sampled_case_count": len(sampled_cases),
      "pending_case_count": len(pending_cases),
      "coverage_percent": coverage_percent,
      "eligible_case_ids": sorted(case.get("case_id", "") for case in eligible_cases),
      "pending_case_ids": sorted(case.get("case_id", "") for case in pending_cases),
  }


def _compute_high_code_inventory_drift(inventory_records, cases):
  inventory_pairs = {
      (record.get("api_name", ""), (record.get("urls") or [""])[0])
      for record in inventory_records
      if record.get("source_kind") == "high-code"
      and (record.get("urls") or [""])[0]
  }
  seed_pairs = {
      (case.get("method", ""), case.get("endpoint", ""))
      for case in cases
      if case.get("source_kind") == "high-code-seed"
      and case.get("endpoint")
  }
  unseeded_pairs = sorted(inventory_pairs - seed_pairs)
  return {
      "high_code_inventory_api_count": len(inventory_pairs),
      "high_code_seed_case_count": len(seed_pairs),
      "unseeded_high_code_inventory_api_count": len(unseeded_pairs),
      "unseeded_high_code_inventory_examples": [
          f"{method} {endpoint}"
          for method, endpoint in unseeded_pairs[:20]
      ],
  }


def merge_execution_results(cases, executed_cases):
  executed_by_case_id = {case["case_id"]: case for case in executed_cases}
  current_case_ids = {
      case.get("case_id")
      for case in cases
      if case.get("case_id")
  }
  current_executed_cases = [
      executed_case
      for executed_case in executed_cases
      if executed_case.get("case_id") in current_case_ids
  ]
  merged_cases = []
  failures = []
  pass_count = 0

  for case in cases:
    merged_case = dict(case)
    executed_case = executed_by_case_id.get(case.get("case_id"))
    if executed_case:
      merged_case["actual_result"] = executed_case.get("actual_result", "")
      merged_case["notes"] = executed_case.get("notes", "")
      if executed_case.get("classification") == "pass":
        pass_count += 1
      else:
        failures.append({
            "case_id": executed_case.get("case_id", ""),
            "classification": executed_case.get("classification", ""),
            "message": executed_case.get("notes", ""),
        })
    merged_cases.append(merged_case)

  case_coverage_denominator = compute_coverage_denominator(cases, ("P0", "P1"))
  case_coverage_numerator = sum(
      1 for case in merged_cases
      if case.get("risk") in ("P0", "P1") and case.get("actual_result")
  )
  fail_count = len(failures)
  case_coverage_percent = 0
  if case_coverage_denominator:
    case_coverage_percent = round(case_coverage_numerator / case_coverage_denominator * 100, 2)

  business_model_coverage = compute_model_sampling_coverage(cases, current_executed_cases, ("list", "query-conditions"))
  metadata_read_coverage = compute_model_sampling_coverage(cases, current_executed_cases, ("list", "query-conditions"))
  metadata_detail_coverage = compute_model_sampling_coverage(cases, current_executed_cases, ("detail",))
  metadata_write_coverage = compute_model_sampling_coverage(
      cases,
      current_executed_cases,
      ("add", "update", "delete"),
      case_predicate=lambda case: case.get("stage_one_write_executable"),
  )
  complex_query_model_coverage = compute_model_sampling_coverage(cases, current_executed_cases, ("query-conditions",))
  high_code_primary_read_coverage = _compute_high_code_seed_case_coverage(cases, current_executed_cases, "primary", "read")
  high_code_primary_write_coverage = _compute_high_code_seed_case_coverage(cases, current_executed_cases, "primary", "write")

  return merged_cases, {
      "coverage_denominator": business_model_coverage["eligible_model_count"],
      "coverage_numerator": business_model_coverage["sampled_model_count"],
      "coverage_percent": business_model_coverage["coverage_percent"],
      "business_model_coverage_percent": business_model_coverage["coverage_percent"],
      "eligible_business_model_count": business_model_coverage["eligible_model_count"],
      "sampled_business_model_count": business_model_coverage["sampled_model_count"],
      "metadata_read_coverage_percent": metadata_read_coverage["coverage_percent"],
      "eligible_metadata_read_model_count": metadata_read_coverage["eligible_model_count"],
      "sampled_metadata_read_model_count": metadata_read_coverage["sampled_model_count"],
      "metadata_detail_coverage_percent": metadata_detail_coverage["coverage_percent"],
      "eligible_metadata_detail_model_count": metadata_detail_coverage["eligible_model_count"],
      "sampled_metadata_detail_model_count": metadata_detail_coverage["sampled_model_count"],
      "metadata_write_coverage_percent": metadata_write_coverage["coverage_percent"],
      "eligible_metadata_write_model_count": metadata_write_coverage["eligible_model_count"],
      "sampled_metadata_write_model_count": metadata_write_coverage["sampled_model_count"],
      "complex_query_model_coverage_percent": complex_query_model_coverage["coverage_percent"],
      "eligible_complex_query_model_count": complex_query_model_coverage["eligible_model_count"],
      "sampled_complex_query_model_count": complex_query_model_coverage["sampled_model_count"],
      "high_code_primary_read_coverage_percent": high_code_primary_read_coverage["coverage_percent"],
      "eligible_high_code_primary_read_case_count": high_code_primary_read_coverage["eligible_case_count"],
      "sampled_high_code_primary_read_case_count": high_code_primary_read_coverage["sampled_case_count"],
      "pending_high_code_primary_read_case_count": high_code_primary_read_coverage["pending_case_count"],
      "high_code_primary_write_coverage_percent": high_code_primary_write_coverage["coverage_percent"],
      "eligible_high_code_primary_write_case_count": high_code_primary_write_coverage["eligible_case_count"],
      "sampled_high_code_primary_write_case_count": high_code_primary_write_coverage["sampled_case_count"],
      "pending_high_code_primary_write_case_count": high_code_primary_write_coverage["pending_case_count"],
      "case_coverage_denominator": case_coverage_denominator,
      "case_coverage_numerator": case_coverage_numerator,
      "case_coverage_percent": case_coverage_percent,
      "pass_count": pass_count,
      "fail_count": fail_count,
      "executed_cases": sorted(current_executed_cases, key=lambda case: case.get("case_id", "")),
      "failures": failures,
  }


def _request_json(url, method="GET", token=None, body=None):
  def _decode_payload(raw):
    if not raw:
      return None, ""
    try:
      return json.loads(raw), ""
    except json.JSONDecodeError as exc:
      return None, str(exc)

  headers = {}
  if token:
    headers["Authorization"] = f"Bearer {token}"

  data = None
  if body is not None:
    headers["Content-Type"] = "application/json"
    data = json.dumps(body, ensure_ascii=False).encode()

  req = request.Request(url, data=data, headers=headers, method=method)
  try:
    with request.urlopen(req, timeout=REQUEST_TIMEOUT_SECONDS) as response:
      raw = response.read().decode()
      payload, payload_parse_error = _decode_payload(raw)
      return {
          "http_status": response.status,
          "payload": payload,
          "raw": raw,
          "payload_parse_error": payload_parse_error,
          "transport_error_kind": "",
      }
  except error.HTTPError as exc:
    raw = exc.read().decode()
    payload, payload_parse_error = _decode_payload(raw)
    return {
        "http_status": exc.code,
        "payload": payload,
        "raw": raw,
        "payload_parse_error": payload_parse_error,
        "transport_error_kind": "",
    }
  except error.URLError as exc:
    reason = str(getattr(exc, "reason", exc))
    return {
        "http_status": None,
        "payload": None,
        "raw": reason,
        "payload_parse_error": "",
        "transport_error_kind": "url-error",
    }


def _extract_business_code(response):
  payload = response.get("payload")
  if isinstance(payload, dict):
    return payload.get("code")
  return None


def _load_previous_results(report_dir):
  result_path = Path(report_dir) / "results.json"
  if not result_path.exists():
    return result_path, {}
  return result_path, json.loads(result_path.read_text())


def _first_non_empty_string(candidates):
  for candidate in candidates:
    if isinstance(candidate, str) and candidate.strip():
      return candidate
  return None


def _extract_token_value(payload):
  if not isinstance(payload, dict):
    return None

  access_token = payload.get("accessToken")
  data_payload = payload.get("data")
  data_access_token = data_payload.get("accessToken") if isinstance(data_payload, dict) else None
  return _first_non_empty_string([
      payload.get("token"),
      access_token,
      access_token.get("token") if isinstance(access_token, dict) else None,
      data_payload.get("token") if isinstance(data_payload, dict) else None,
      data_access_token,
      data_access_token.get("token") if isinstance(data_access_token, dict) else None,
  ])


def _load_token_from_file(token_file):
  token_text = Path(token_file).read_text().strip()
  if not token_text:
    raise RuntimeDependencyResolutionError("token_file", EXECUTION_PRECONDITION_GAP, "token_file missing token content")

  if not token_text.startswith("{"):
    return token_text

  try:
    payload = json.loads(token_text)
  except json.JSONDecodeError as exc:
    raise RuntimeDependencyResolutionError(
        "token_file",
        EXECUTION_PRECONDITION_GAP,
        f"token_file is not valid JSON: {exc}",
    ) from exc

  token = _extract_token_value(payload)
  if token:
    return token

  raise RuntimeDependencyResolutionError(
      "token_file",
      EXECUTION_PRECONDITION_GAP,
      "token_file missing token in supported payload shape",
  )


def _load_token_from_login(login_response):
  http_status = login_response.get("http_status")
  business_code = _extract_business_code(login_response)
  payload = login_response.get("payload")
  payload_parse_error = login_response.get("payload_parse_error", "")
  transport_error_kind = login_response.get("transport_error_kind", "")
  raw = login_response.get("raw", "")

  if payload_parse_error:
    raise RuntimeDependencyResolutionError(
        "login_response",
        UNEXPECTED_RESPONSE_FORMAT,
        f"login response not-json: {payload_parse_error}",
        http_status=http_status,
        business_code=business_code,
    )

  if transport_error_kind:
    raise RuntimeDependencyResolutionError(
        "login_response",
        RUNTIME_AUTH_INVALID,
        f"login request failed: {transport_error_kind}: {raw}",
        http_status=http_status,
        business_code=business_code,
    )

  if http_status not in (200, 201):
    message = payload.get("msg") if isinstance(payload, dict) else raw
    raise RuntimeDependencyResolutionError(
        "login_response",
        RUNTIME_AUTH_INVALID,
        f"login returned HTTP {http_status}: {message or 'unexpected auth response'}",
        http_status=http_status,
        business_code=business_code,
    )

  if business_code not in (0, None):
    message = payload.get("msg") if isinstance(payload, dict) else raw
    raise RuntimeDependencyResolutionError(
        "login_response",
        RUNTIME_AUTH_INVALID,
        f"login returned business error: {message or business_code}",
        http_status=http_status,
        business_code=business_code,
    )

  token = _extract_token_value(payload)
  if token:
    return token

  raise RuntimeDependencyResolutionError(
      "login_response",
      RUNTIME_AUTH_INVALID,
      "login response missing token in supported payload shape",
      http_status=http_status,
      business_code=business_code,
  )


def _load_token(args, base_url, token_file=None):
  if token_file:
    return _load_token_from_file(token_file)

  if not base_url:
    raise ValueError("--base-url is required for execute mode when --token-file is not provided")

  login_payload = getattr(args, "login_payload", None) or DEFAULT_LOGIN_PAYLOAD
  login_response = _request_json(
      base_url.rstrip("/") + "/api/auth/login",
      method="POST",
      body=login_payload,
  )
  return _load_token_from_login(login_response)


def _extract_authority_map(payload):
  if not isinstance(payload, dict):
    return None

  data = payload.get("data")
  if isinstance(data, dict):
    return data

  if "code" not in payload and "msg" not in payload:
    return payload
  return None


def _extract_authority_names(authority_map):
  authority_names = set()
  for authority_key, authority_value in (authority_map or {}).items():
    if isinstance(authority_key, str) and authority_key.strip():
      authority_names.add(authority_key.strip())
    if isinstance(authority_value, dict):
      authority_name = authority_value.get("authority")
      if isinstance(authority_name, str) and authority_name.strip():
        authority_names.add(authority_name.strip())
  return sorted(authority_names)


def _extract_authority_api_patterns(authority_map):
  api_patterns = []
  for authority in (authority_map or {}).values():
    if not isinstance(authority, dict):
      continue
    authority_object = authority.get("authorityObject")
    apis = authority_object.get("apis") if isinstance(authority_object, dict) else authority.get("apis")
    for api in apis or []:
      if isinstance(api, str) and api.strip():
        api_patterns.append(api.strip())
  return sorted(set(api_patterns))


def _case_endpoint_for_authority_matching(endpoint):
  return re.sub(r"\$\{[^}]+\}", "__segment__", endpoint or "")


def _authority_api_path_matches(pattern, endpoint):
  regex_parts = []
  index = 0
  while index < len(pattern):
    if pattern.startswith("{*", index):
      end = pattern.find("}", index + 2)
      if end == -1:
        regex_parts.append(re.escape(pattern[index:]))
        index = len(pattern)
      else:
        regex_parts.append(".*")
        index = end + 1
      continue
    if pattern.startswith("**", index):
      regex_parts.append(".*")
      index += 2
      continue

    char = pattern[index]
    if char == "{":
      end = pattern.find("}", index + 1)
      if end == -1:
        regex_parts.append(re.escape(char))
        index += 1
      else:
        regex_parts.append("[^/]+")
        index = end + 1
      continue
    if char == "*":
      regex_parts.append("[^/]*")
      index += 1
      continue

    regex_parts.append(re.escape(char))
    index += 1

  return re.match("^" + "".join(regex_parts) + "$", endpoint) is not None


def _authority_api_matches_case(api_pattern, case):
  if not isinstance(api_pattern, str) or " " not in api_pattern:
    return False

  method, path_pattern = api_pattern.split(" ", 1)
  case_method = str(case.get("method", "")).upper()
  if method != "*" and method.upper() != case_method:
    return False

  endpoint = _case_endpoint_for_authority_matching(case.get("endpoint", ""))
  return _authority_api_path_matches(path_pattern.strip(), endpoint)


def _authority_base_suffixes(case):
  scenario = str(case.get("scenario", "")).strip()
  method = str(case.get("method", "")).upper()

  if scenario in ("list", "query-conditions", "search", "manage-search"):
    return ("", "search")
  if scenario in ("detail", "tree-root", "tree-children", "accessible", "employee-bind-info"):
    return ("",)
  if scenario in ("add", "create", "items-create"):
    return ("", "add", "create")
  if scenario in ("update", "edit", "patch"):
    return ("", "edit", "update", "patch")
  if scenario in ("delete", "soft-delete-before-hard-delete"):
    return ("", "delete")
  if scenario == "roundtrip":
    return ("", "edit", "update", "patch")

  if method == "GET":
    return ("", "search")
  if method == "POST":
    return ("", "add", "create")
  if method in ("PUT", "PATCH"):
    return ("", "edit", "update", "patch")
  if method == "DELETE":
    return ("", "delete")
  return ("",)


def _expand_authority_base_candidates(base_candidate, case):
  if not isinstance(base_candidate, str) or not base_candidate.strip():
    return set()

  normalized_base = base_candidate.strip().strip(".")
  expanded = set()
  for suffix in _authority_base_suffixes(case):
    if suffix:
      expanded.add(f"{normalized_base}.{suffix}")
    else:
      expanded.add(normalized_base)
  return expanded


def _case_id_authority_base_candidates(case_id):
  if not isinstance(case_id, str) or not case_id.strip():
    return set()

  removable_tokens = {
      "accessible",
      "add",
      "children",
      "create",
      "delete",
      "detail",
      "edit",
      "employee-bind-info",
      "getOne",
      "items-create",
      "list",
      "manage",
      "patch",
      "query-conditions",
      "read",
      "root",
      "roundtrip",
      "search",
      "soft-delete-before-hard-delete",
      "tree",
      "tree-children",
      "tree-root",
      "update",
      "write",
  }
  tokens = [token.strip() for token in case_id.split(".") if token.strip()]
  if len(tokens) < 2:
    return set()

  candidates = {".".join(tokens)}
  trimmed_tokens = list(tokens)
  while len(trimmed_tokens) > 2 and trimmed_tokens[-1] in removable_tokens:
    trimmed_tokens = trimmed_tokens[:-1]
    candidates.add(".".join(trimmed_tokens))
  return candidates


def _case_authority_candidates(case):
  candidates = set()
  module = case.get("module")
  if isinstance(module, str) and module.strip():
    candidates.update(_expand_authority_base_candidates(module, case))

  for base_candidate in _case_id_authority_base_candidates(case.get("case_id", "")):
    candidates.update(_expand_authority_base_candidates(base_candidate, case))

  normalized_candidates = []
  for candidate in sorted(candidates):
    if not candidate:
      continue
    normalized = candidate.strip().strip(".")
    if normalized:
      normalized_candidates.append(normalized)
  return sorted(set(normalized_candidates))


def _authority_name_matches_case(authority_names, case):
  case_candidates = _case_authority_candidates(case)
  return any(candidate in authority_names for candidate in case_candidates)


def _validate_authenticated_authority_surface(base_url, token, selected_cases, target_name):
  if not selected_cases:
    return []

  response = _request_json(
      base_url.rstrip("/") + RUNTIME_HTTP_PROBE_PATH,
      token=token,
  )
  http_status = response.get("http_status")
  business_code = _extract_business_code(response)
  payload = response.get("payload")
  payload_parse_error = response.get("payload_parse_error", "")
  transport_error_kind = response.get("transport_error_kind", "")
  raw = response.get("raw", "")

  if payload_parse_error:
    raise RuntimeDependencyResolutionError(
        "authority_surface",
        UNEXPECTED_RESPONSE_FORMAT,
        f"authenticated authorities response not-json: {payload_parse_error}",
        http_status=http_status,
        business_code=business_code,
    )

  if transport_error_kind:
    raise RuntimeDependencyResolutionError(
        "authority_surface",
        RUNTIME_AUTH_INVALID,
        f"authenticated authorities request failed: {transport_error_kind}: {raw}",
        http_status=http_status,
        business_code=business_code,
    )

  if http_status not in (200, 201):
    message = payload.get("msg") if isinstance(payload, dict) else raw
    raise RuntimeDependencyResolutionError(
        "authority_surface",
        RUNTIME_AUTH_INVALID,
        f"authenticated authorities returned HTTP {http_status}: {message or 'unexpected auth response'}",
        http_status=http_status,
        business_code=business_code,
    )

  if business_code not in (0, None):
    message = payload.get("msg") if isinstance(payload, dict) else raw
    raise RuntimeDependencyResolutionError(
        "authority_surface",
        RUNTIME_AUTH_INVALID,
        f"authenticated authorities returned business error: {message or business_code}",
        http_status=http_status,
        business_code=business_code,
    )

  authority_map = _extract_authority_map(payload)
  if not isinstance(authority_map, dict):
    raise RuntimeDependencyResolutionError(
        "authority_surface",
        UNEXPECTED_RESPONSE_FORMAT,
        "authenticated authorities response missing authority map payload",
        http_status=http_status,
        business_code=business_code,
    )

  authority_names = _extract_authority_names(authority_map)
  authority_name_set = set(authority_names)
  authority_api_patterns = _extract_authority_api_patterns(authority_map)
  covered_case_ids = [
      case.get("case_id", "")
      for case in selected_cases
      if (
          _authority_name_matches_case(authority_name_set, case)
          or any(_authority_api_matches_case(api_pattern, case) for api_pattern in authority_api_patterns)
      )
  ]
  if covered_case_ids:
    return covered_case_ids

  expected_cases = ", ".join(case.get("case_id", "") for case in selected_cases[:3])
  authority_excerpt = ", ".join(authority_names[:5]) if authority_names else "<empty>"
  raise RuntimeDependencyResolutionError(
      "authority_surface",
      RUNTIME_BASELINE_INVALID,
      (
          f"authenticated authority surface on target {target_name} does not cover selected cases: "
          f"{expected_cases}; sample authorities: {authority_excerpt}"
      ),
      http_status=http_status,
      business_code=business_code,
  )


def _selected_cases_for_target(selected_cases, base_url, app_base_url, target_name):
  return [
      case
      for case in selected_cases
      if _resolve_case_target(case, base_url, app_base_url)[1] == target_name
  ]


def _merge_executed_cases(previous_cases, new_cases):
  merged = {case["case_id"]: case for case in previous_cases}
  for case in new_cases:
    merged[case["case_id"]] = case
  return sorted(merged.values(), key=lambda case: case.get("case_id", ""))


def _runtime_dependency_failure_case(target_name, source, exc):
  is_authority_surface_failure = exc.field_name == "authority_surface"
  notes = [f"target={target_name}", f"field={exc.field_name}", str(exc)]
  if exc.http_status is not None:
    notes.append(f"http={exc.http_status}")
  if exc.business_code is not None:
    notes.append(f"code={exc.business_code}")
  return {
      "case_id": f"runtime.authorities.{target_name}" if is_authority_surface_failure else f"runtime.token.{target_name}",
      "endpoint": source,
      "method": "AUTHZ" if is_authority_surface_failure else "AUTH",
      "http_status": exc.http_status,
      "business_code": exc.business_code,
      "actual_result": "FAIL",
      "classification": exc.classification,
      "notes": "; ".join(notes),
      "response_excerpt": "",
  }


def _abort_execute_with_runtime_failure(
    report_dir,
    docs_report_dir,
    inventory_summary,
    cases,
    preflight_reports,
    high_code_inventory_drift,
    target_name,
    source,
    exc,
):
  result_path, _previous_results = _load_previous_results(report_dir)
  summary = _write_results_placeholder(report_dir, inventory_summary, cases)
  summary["inventory_summary"] = inventory_summary
  failure = _runtime_dependency_failure_case(target_name, source, exc)
  summary["fail_count"] = 1
  summary["failures"] = [failure]
  summary["executed_cases"] = [failure]
  summary["runtime_preflight"] = preflight_reports
  summary.update(high_code_inventory_drift)
  if exc.field_name == "authority_surface":
    summary["merge_readiness"] = "运行时权限面无效，本轮执行已中止；必须先修复 runtime/app 装配后才能判断模块是否回归"
    print("execute mode aborted: authenticated authority surface invalid")
  else:
    summary["merge_readiness"] = "token 获取失败，本轮执行已中止；必须先修复认证/凭据基线后才能判断模块是否回归"
    print("execute mode aborted: token acquisition failed")
  write_json(summary, result_path)
  write_text(render_summary_report(summary), Path(docs_report_dir) / REPORT_FILE_NAMES["summary_md"])
  return 1


def _run_process(command):
  return subprocess.run(command, capture_output=True, text=True, check=False)


def _parse_base_url(base_url):
  parsed = parse.urlparse(base_url or "")
  return parsed.hostname, parsed.port


def _loader_entry_to_path(entry):
  if entry.startswith("file:"):
    parsed = parse.urlparse(entry)
    path_value = parse.unquote(parsed.path or "")
    if parsed.netloc and not path_value.startswith("/"):
      path_value = "/" + path_value
    return Path(path_value)
  return Path(entry)


def _extract_runtime_command_paths(command):
  try:
    tokens = shlex.split(command)
  except ValueError:
    tokens = command.split()

  jar_path = None
  loader_paths = []
  for index, token in enumerate(tokens):
    if token == "-jar" and index + 1 < len(tokens):
      jar_path = Path(tokens[index + 1])
      continue
    if token.startswith("-Dloader.path="):
      loader_value = token.split("=", 1)[1]
      for entry in loader_value.split(","):
        entry = entry.strip()
        if entry:
          loader_paths.append(_loader_entry_to_path(entry))

  return jar_path, loader_paths


def _find_listening_pid(port):
  result = _run_process(["lsof", "-nP", f"-iTCP:{port}", "-sTCP:LISTEN", "-Fp"])
  if result.returncode not in (0, 1):
    raise RuntimeError(result.stderr.strip() or result.stdout.strip() or f"lsof failed for port {port}")

  for line in result.stdout.splitlines():
    if line.startswith("p") and line[1:].strip():
      return line[1:].strip()
  return None


def _read_process_command(pid):
  result = _run_process(["ps", "-p", str(pid), "-o", "command="])
  if result.returncode != 0:
    raise RuntimeError(result.stderr.strip() or result.stdout.strip() or f"ps failed for pid {pid}")
  return result.stdout.strip()


def _list_open_jar_paths(pid):
  result = _run_process(["lsof", "-p", str(pid), "-Fn"])
  if result.returncode not in (0, 1):
    raise RuntimeError(result.stderr.strip() or result.stdout.strip() or f"lsof failed for pid {pid}")
  return [
      line[1:].strip()
      for line in result.stdout.splitlines()
      if line.startswith("n") and line[1:].strip().endswith(".jar")
  ]


def _is_under_any_prefix(path_value, prefixes):
  candidate = Path(path_value)
  for prefix in prefixes:
    try:
      candidate.relative_to(prefix)
      return True
    except ValueError:
      continue
  return False


def _collect_runtime_integrity_issues(jar_path, loader_paths, open_jar_paths):
  issues = []

  if jar_path and not jar_path.exists():
    issues.append({
        "kind": "missing-jar",
        "path": str(jar_path),
        "message": f"启动主 jar 已不存在: {jar_path}",
    })

  for loader_path in loader_paths:
    if not loader_path.exists():
      issues.append({
          "kind": "missing-loader-path",
          "path": str(loader_path),
          "message": f"loader.path 目录已不存在: {loader_path}",
      })

  runtime_prefixes = []
  if jar_path:
    runtime_prefixes.append(jar_path.parent)
  runtime_prefixes.extend(loader_paths)

  missing_open_jars = []
  for open_jar_path in open_jar_paths:
    if runtime_prefixes and not _is_under_any_prefix(open_jar_path, runtime_prefixes):
      continue
    if not Path(open_jar_path).exists():
      missing_open_jars.append(open_jar_path)

  if missing_open_jars:
    first_missing_path = missing_open_jars[0]
    issues.append({
        "kind": "missing-open-jar",
        "path": first_missing_path,
        "message": (
            "运行中进程仍持有已丢失的类路径 jar，延迟加载 SPI/资源时会随机失效: "
            f"{first_missing_path}"
        ),
    })

  return issues


def _probe_runtime_http(base_url):
  probe_url = base_url.rstrip("/") + RUNTIME_HTTP_PROBE_PATH
  response = _request_json(probe_url)
  http_status = response.get("http_status")
  payload = response.get("payload") or {}
  message = payload.get("msg") if isinstance(payload, dict) else ""
  payload_parse_error = response.get("payload_parse_error")
  is_expected_unauthorized_surface = (
      isinstance(payload, dict)
      and not payload_parse_error
      and payload.get("code") == -1
      and isinstance(payload.get("msg"), str)
      and bool(payload.get("msg"))
  )

  if http_status == 401 and is_expected_unauthorized_surface:
    note = "http-probe=401"
    if message:
      note = f"{note} msg={message}"
    return {
        "status": "pass",
        "http_status": http_status,
        "transport_error_kind": "",
        "notes": [note],
        "issues": [],
    }

  if http_status == 401:
    details = []
    if payload_parse_error:
      details.append(f"parse={payload_parse_error}")
    if isinstance(payload, dict):
      details.append(f"code={payload.get('code')}")
      if message:
        details.append(f"msg={message}")
    raw_excerpt = (response.get("raw") or "")[:160]
    if raw_excerpt and not message:
      details.append(f"raw={raw_excerpt}")

    issue_message = f"未认证探针 {probe_url} 返回了 401，但响应不是预期的后端 JSON 鉴权面"
    if details:
      issue_message = f"{issue_message}: {'; '.join(details)}"
    return {
        "status": "fail",
        "http_status": http_status,
        "transport_error_kind": "",
        "notes": [],
        "issues": [
            {
                "kind": "http-probe-unexpected-response-format",
                "message": issue_message,
            }
        ],
    }

  details = []
  if response.get("transport_error_kind"):
    details.append(f"transport={response.get('transport_error_kind')}")
  if message:
    details.append(f"msg={message}")
  if response.get("payload_parse_error"):
    details.append(f"parse={response.get('payload_parse_error')}")
  raw_excerpt = (response.get("raw") or "")[:160]
  if raw_excerpt and not message:
    details.append(f"raw={raw_excerpt}")

  issue_message = (
      f"未认证探针 {probe_url} 期望返回 401，实际为 "
      f"{http_status if http_status is not None else 'transport-error'}"
  )
  if details:
    issue_message = f"{issue_message}: {'; '.join(details)}"

  return {
      "status": "fail",
      "http_status": http_status,
      "transport_error_kind": response.get("transport_error_kind", ""),
      "notes": [],
      "issues": [
          {
              "kind": "http-probe-unexpected-status",
              "message": issue_message,
          }
      ],
  }


def _is_runtime_http_warmup_retryable(http_probe, allow_transport_retry=False):
  is_retryable_503 = (
      http_probe.get("status") == "fail"
      and http_probe.get("http_status") == 503
      and any(issue.get("kind") == "http-probe-unexpected-status" for issue in http_probe.get("issues", []))
  )
  is_retryable_transport = (
      allow_transport_retry
      and http_probe.get("status") == "fail"
      and http_probe.get("http_status") is None
      and http_probe.get("transport_error_kind") == "url-error"
      and any(issue.get("kind") == "http-probe-unexpected-status" for issue in http_probe.get("issues", []))
  )
  return is_retryable_503 or is_retryable_transport


def _with_runtime_http_warmup_notes(http_probe, retries):
  if retries <= 0:
    return http_probe

  notes = list(http_probe.get("notes", []))
  notes.append(f"http-probe-warmup-retries={retries}")
  updated_probe = dict(http_probe)
  updated_probe["notes"] = notes
  return updated_probe


def _with_runtime_http_warmup_failure(http_probe, retries):
  updated_probe = _with_runtime_http_warmup_notes(http_probe, retries)
  issues = []
  for issue in updated_probe.get("issues", []):
    updated_issue = dict(issue)
    updated_issue["message"] = (
        f"{issue.get('message', '')}"
        f"；在启动预热窗口内已重试 {retries} 次，仍未转为 401"
    )
    issues.append(updated_issue)
  updated_probe["issues"] = issues
  return updated_probe


def _probe_runtime_http_with_warmup(base_url, allow_transport_retry=False):
  retries = 0
  while True:
    http_probe = _probe_runtime_http(base_url)
    if http_probe.get("status") == "pass":
      return _with_runtime_http_warmup_notes(http_probe, retries)

    if not _is_runtime_http_warmup_retryable(http_probe, allow_transport_retry=allow_transport_retry):
      return http_probe

    if retries >= RUNTIME_HTTP_PROBE_WARMUP_RETRY_ATTEMPTS:
      return _with_runtime_http_warmup_failure(http_probe, retries)

    retries += 1
    time.sleep(RUNTIME_HTTP_PROBE_WARMUP_RETRY_INTERVAL_SECONDS)


def _preflight_local_runtime(base_url, target_name):
  report = {
      "target": target_name,
      "base_url": base_url,
      "status": "skipped",
      "issues": [],
      "notes": [],
  }
  if not base_url:
    report["notes"].append("未提供 base_url，跳过运行时预检")
    return report

  host, port = _parse_base_url(base_url)
  report["host"] = host
  report["port"] = port
  http_probe = _probe_runtime_http_with_warmup(
      base_url,
      allow_transport_retry=host in LOCAL_RUNTIME_HOSTS,
  )
  report["notes"].extend(http_probe.get("notes", []))
  if http_probe.get("status") == "fail":
    report["status"] = "fail"
    report["issues"].extend(http_probe.get("issues", []))
    return report

  if host not in LOCAL_RUNTIME_HOSTS:
    report["status"] = "pass"
    report["notes"].append("非本地运行时，跳过文件系统完整性预检")
    return report

  if port is None:
    report["status"] = "pass"
    report["notes"].append("未显式指定端口，跳过本地进程与类路径完整性预检")
    return report

  try:
    pid = _find_listening_pid(port)
  except RuntimeError as exc:
    report["status"] = "fail"
    report["issues"].append({
        "kind": "pid-discovery-failed",
        "message": f"无法定位监听端口 {port} 的进程: {str(exc)}",
    })
    return report

  if not pid:
    report["status"] = "fail"
    report["issues"].append({
        "kind": "missing-listener",
        "message": f"本地端口 {port} 没有监听进程，当前 runtime 不可作为回归基线",
    })
    return report

  report["pid"] = pid
  try:
    command = _read_process_command(pid)
  except RuntimeError as exc:
    report["status"] = "fail"
    report["issues"].append({
        "kind": "command-read-failed",
        "message": f"无法读取进程 {pid} 的启动命令: {str(exc)}",
    })
    return report

  report["command"] = command
  jar_path, loader_paths = _extract_runtime_command_paths(command)
  report["jar_path"] = str(jar_path) if jar_path else ""
  report["loader_paths"] = [str(path) for path in loader_paths]

  if not jar_path and not loader_paths:
    report["status"] = "pass"
    report["notes"].append("当前进程未通过 -jar/loader.path 启动，跳过类路径产物完整性预检")
    return report

  try:
    open_jar_paths = _list_open_jar_paths(pid)
  except RuntimeError as exc:
    report["status"] = "fail"
    report["issues"].append({
        "kind": "lsof-open-jar-failed",
        "message": f"无法读取进程 {pid} 的已打开 jar 列表: {str(exc)}",
    })
    return report

  report["issues"] = _collect_runtime_integrity_issues(jar_path, loader_paths, open_jar_paths)
  report["status"] = "fail" if report["issues"] else "pass"
  if report["status"] == "pass":
    report["notes"].append("运行时产物完整性通过")
  return report


def _build_runtime_preflight_failures(reports):
  failures = []
  executed_cases = []
  for report in reports:
    if report.get("status") != "fail":
      continue

    message = "; ".join(issue.get("message", "") for issue in report.get("issues", []))
    case_id = f"runtime.preflight.{report.get('target', 'default')}"
    failures.append({
        "case_id": case_id,
        "classification": RUNTIME_BASELINE_INVALID,
        "message": message,
    })
    executed_cases.append({
        "case_id": case_id,
        "endpoint": report.get("base_url", ""),
        "method": "PRECHECK",
        "http_status": None,
        "business_code": None,
        "actual_result": "FAIL",
        "classification": RUNTIME_BASELINE_INVALID,
        "notes": message,
        "response_excerpt": "",
    })

  return failures, executed_cases


def _preflight_runtime_targets(base_url, app_base_url=None, selected_cases=None):
  runtime_targets = _collect_runtime_targets(base_url, app_base_url, selected_cases)
  reports = [
      _preflight_local_runtime(runtime_target["base_url"], runtime_target["target"])
      for runtime_target in runtime_targets
  ]

  failures, executed_cases = _build_runtime_preflight_failures(reports)
  return reports, failures, executed_cases


def _resolve_sample_query(sample_query, company_id):
  resolved = {}
  for key, value in (sample_query or {}).items():
    if value == FILTER_VALUE_PLACEHOLDER_COMPANY_ID:
      if company_id in (None, ""):
        raise ExecutionPreconditionError(f"sample query requires companyId but --company-id is missing: {key}")
      resolved[key] = company_id
      continue
    if isinstance(value, list):
      resolved[key] = [
          (
              company_id
              if item == FILTER_VALUE_PLACEHOLDER_COMPANY_ID and company_id not in (None, "")
              else item
          )
          for item in value
      ]
      if FILTER_VALUE_PLACEHOLDER_COMPANY_ID in value and company_id in (None, ""):
        raise ExecutionPreconditionError(f"sample query requires companyId but --company-id is missing: {key}")
      continue
    resolved[key] = value
  return resolved


def _build_case_query(case, company_id):
  if case.get("source_kind") == "high-code-seed":
    return _resolve_sample_query(case.get("sample_query", {}), company_id)

  query = {
      "page": 1,
      "perPage": 1,
  }
  if company_id:
    query["companyId"] = company_id

  if case.get("scenario") == "query-conditions":
    query.update(_resolve_sample_query(case.get("sample_query", {}), company_id))
  return query


def _list_company_candidates(base_url, token):
  response = _request_json(base_url.rstrip("/") + "/api/organization/companies?page=1&perPage=200", token=token)
  business_code = _extract_business_code(response)
  classification = classify_execution_result(
      response.get("http_status"),
      business_code,
      response.get("payload_parse_error", ""),
  )
  if classification["classification"] != "pass":
    raise RuntimeDependencyResolutionError(
        "companyId",
        classification["classification"],
        "failed to load organization companies for default company resolution",
        http_status=response.get("http_status"),
        business_code=business_code,
    )

  items = [item for item in _extract_response_items(response) if isinstance(item, dict) and item.get("id") not in (None, "")]
  if not items:
    raise RuntimeDependencyResolutionError(
        "companyId",
        EXECUTION_PRECONDITION_GAP,
        "no organization company available for metadata execution context",
        http_status=response.get("http_status"),
        business_code=business_code,
    )
  return items


def _collect_runtime_reference_probes(selected_cases):
  probes = []
  seen = set()
  for case in selected_cases:
    if case.get("source_kind") != "metadata" or case.get("scenario") not in ("add", "update", "delete"):
      continue
    for dependency in case.get("runtime_write_dependencies", []):
      if dependency.get("dependency_kind") != "reference":
        continue
      model_key = dependency.get("model_key")
      if not model_key:
        continue
      probe_key = (model_key, dependency.get("field_name", ""), dependency.get("reference_key", ""))
      if probe_key in seen:
        continue
      seen.add(probe_key)
      probes.append(dict(dependency))
  return probes


def _score_company_candidate(base_url, token, company_id, reference_probes):
  score = 0
  notes = []
  for dependency in reference_probes:
    try:
      value, note = _resolve_reference_dependency(dependency, base_url, token, company_id)
      score += 1
      notes.append(note)
    except RuntimeDependencyResolutionError:
      continue
  return score, notes


def _resolve_execution_company_id(base_url, token, selected_cases, explicit_company_id=None):
  if explicit_company_id not in (None, ""):
    return explicit_company_id, [f"companyId={explicit_company_id}"]

  metadata_cases = [case for case in selected_cases if case.get("source_kind") == "metadata"]
  if not metadata_cases:
    return None, []

  companies = _list_company_candidates(base_url, token)
  reference_probes = _collect_runtime_reference_probes(selected_cases)
  scored_candidates = []
  for company in companies:
    company_id = company.get("id")
    score, probe_notes = _score_company_candidate(base_url, token, company_id, reference_probes)
    scored_candidates.append({
        "id": company_id,
        "name": company.get("name", ""),
        "score": score,
        "notes": probe_notes,
    })

  scored_candidates.sort(key=lambda item: (-item["score"], str(item["name"]), str(item["id"])))
  winner = scored_candidates[0]
  notes = [
      f"companyId={winner['id']}",
      f"companyName={winner['name']}",
      f"companyScore={winner['score']}/{len(reference_probes)}",
  ]
  return winner["id"], notes


def _unique_token():
  return str(int(time.time() * 1000))


def _build_primary_key_sample(unique_suffix):
  candidate = f"{PRIMARY_KEY_SAMPLE_PREFIX}{unique_suffix}"
  if len(candidate) <= DEFAULT_PRIMARY_KEY_SAMPLE_MAX_LENGTH:
    return candidate
  return candidate[-DEFAULT_PRIMARY_KEY_SAMPLE_MAX_LENGTH:]


def _materialize_sample_value(value, field_name, primary_key_field, company_id, unique_suffix):
  if value == FILTER_VALUE_PLACEHOLDER_COMPANY_ID:
    if company_id in (None, ""):
      raise ExecutionPreconditionError(
          f"sample payload requires companyId but --company-id is missing: {field_name or 'companyId'}"
      )
    return company_id
  if isinstance(value, dict):
    return {
        key: _materialize_sample_value(child, key, primary_key_field, company_id, unique_suffix)
        for key, child in value.items()
    }
  if isinstance(value, list):
    return [
        _materialize_sample_value(item, field_name, primary_key_field, company_id, unique_suffix)
        for item in value
    ]
  if isinstance(value, str):
    if field_name == primary_key_field and value == "sample-id":
      return _build_primary_key_sample(unique_suffix)
    if value == "sample-text":
      return f"{field_name or 'value'}-{unique_suffix}"
    if value == "sample-text-updated":
      return f"{field_name or 'value'}-updated-{unique_suffix}"
    if value == "sample-id":
      return f"{field_name or 'value'}-{unique_suffix}"
  return value


def _materialize_high_code_seed_payload(payload, unique_suffix, company_id=None, primary_key_field=""):
  return _materialize_sample_value(payload, "", primary_key_field, company_id, unique_suffix)


def _build_configuration_roundtrip_payload(base_url, token):
  response = _request_json(base_url + "/api/sys/configuration", token=token)
  business_code = _extract_business_code(response)
  classification = classify_execution_result(
      response.get("http_status"),
      business_code,
      response.get("payload_parse_error", ""),
  )
  if classification["classification"] != "pass":
    raise RuntimeDependencyResolutionError(
        "configuration.roundtrip",
        classification["classification"],
        "failed to load configuration list for roundtrip payload",
        http_status=response.get("http_status"),
        business_code=business_code,
    )

  items = _extract_response_items(response)
  for item in items:
    if not isinstance(item, dict):
      continue
    metadata = item.get("metadata") or {}
    if metadata.get("modifiable") and item.get("key"):
      return ([{
          "key": item.get("key"),
          "value": item.get("value"),
      }], [f"roundtrip.key={item.get('key')}"])

  raise RuntimeDependencyResolutionError(
      "configuration.roundtrip",
      EXECUTION_PRECONDITION_GAP,
      "no modifiable configuration item available for roundtrip payload",
      http_status=response.get("http_status"),
      business_code=business_code,
  )


def _build_contextual_employee_binding(base_url, token, unique_suffix):
  departments_response = _request_json(base_url + "/api/organization/departments?page=1&perPage=1", token=token)
  departments_code = _extract_business_code(departments_response)
  departments_classification = classify_execution_result(
      departments_response.get("http_status"),
      departments_code,
      departments_response.get("payload_parse_error", ""),
  )
  if departments_classification["classification"] != "pass":
    raise RuntimeDependencyResolutionError(
        "organization.departments",
        departments_classification["classification"],
        "failed to resolve a department for contextual employee binding",
        http_status=departments_response.get("http_status"),
        business_code=departments_code,
    )

  departments = _extract_response_items(departments_response)
  department = departments[0] if departments else None
  department_id = department.get("id") if isinstance(department, dict) else None
  if not department_id:
    raise RuntimeDependencyResolutionError(
        "organization.departments",
        EXECUTION_PRECONDITION_GAP,
        "no department available for contextual employee binding",
    )

  user_identity = f"hc-employee-{unique_suffix}"
  user_password = f"pwd-{unique_suffix}"
  create_payload = {
      "name": f"Regression Employee {unique_suffix}",
      "gender": "男",
      "birthday": "2026-01-01",
      "departmentId": department_id,
      "user": {
          "mode": "create",
          "username": user_identity,
          "password": user_password,
      },
  }
  create_response = _request_json(
      base_url + "/api/organization/employees",
      method="POST",
      token=token,
      body=create_payload,
  )
  create_code = _extract_business_code(create_response)
  create_classification = classify_execution_result(
      create_response.get("http_status"),
      create_code,
      create_response.get("payload_parse_error", ""),
  )
  if create_classification["classification"] != "pass":
    raise RuntimeDependencyResolutionError(
        "organization.employee.create",
        create_classification["classification"],
        "failed to create contextual employee binding for notification case",
        http_status=create_response.get("http_status"),
        business_code=create_code,
    )

  employee_id = _extract_created_id(create_response) or _extract_response_data(create_response)
  if not employee_id:
    raise RuntimeDependencyResolutionError(
        "organization.employee.create",
        "regression",
        "missing employee id after contextual employee creation",
        http_status=create_response.get("http_status"),
        business_code=create_code,
    )

  login_response = _request_json(
      base_url + "/api/auth/login",
      method="POST",
      body={
          "account": user_identity,
          "password": user_password,
      },
  )
  contextual_token = _load_token_from_login(login_response)

  cleanup_steps = [{
      "method": "DELETE",
      "endpoint": f"/api/organization/employees/{parse.quote(str(employee_id))}",
      "note_prefix": "context.cleanup.employee",
  }, {
      "method": "DELETE",
      "endpoint": f"/api/sys/user/{parse.quote(str(user_identity))}",
      "note_prefix": "context.cleanup.user",
  }]
  return contextual_token, [f"organization.employeeId={employee_id}", f"organization.user={user_identity}"], cleanup_steps


def _execute_cleanup_steps(base_url, token, steps, template_context):
  cleanup_response = None
  cleanup_classification = None
  notes = []
  for index, step in enumerate(steps or []):
    method = step.get("method") or "DELETE"
    endpoint_template = step.get("endpoint")
    if not endpoint_template:
      continue
    cleanup_target = _expand_endpoint_template(endpoint_template, template_context)
    cleanup_response = _request_json(base_url + cleanup_target, method=method, token=token, body=step.get("body"))
    cleanup_classification = classify_execution_result(
        cleanup_response.get("http_status"),
        _extract_business_code(cleanup_response),
        cleanup_response.get("payload_parse_error", ""),
    )
    prefix = step.get("note_prefix") or ("cleanup" if index == 0 else f"cleanup.{index + 1}")
    notes.append(f"{prefix}={'PASS' if cleanup_classification['classification'] == 'pass' else 'FAIL'}")
    notes.append(_response_notes(prefix, cleanup_response))
    if cleanup_classification["classification"] != "pass":
      break
  return cleanup_response, cleanup_classification, notes


def _materialize_sample_payload(case, company_id, unique_suffix):
  payload = {}
  primary_key_field = case.get("write_primary_key_field", "")
  for key, value in (case.get("sample_payload") or {}).items():
    payload[key] = _materialize_sample_value(value, key, primary_key_field, company_id, unique_suffix)
  return payload


def _to_kebab_case(value):
  return re.sub(r"(?<!^)(?=[A-Z])", "-", value).replace("_", "-").lower()


def _model_key_to_api_path(model_key):
  parts = [_to_kebab_case(part) for part in str(model_key).split(".") if part]
  return "/api/" + "/".join(parts)


def _append_company_query(url, company_id):
  if not company_id:
    return url
  separator = "&" if "?" in url else "?"
  return f"{url}{separator}{parse.urlencode({'companyId': company_id})}"


def _extract_response_data(response):
  payload = response.get("payload")
  if isinstance(payload, dict):
    return payload.get("data")
  return None


def _extract_created_id(response, primary_key_field=None):
  data = _extract_response_data(response)
  if isinstance(data, dict):
    return _extract_item_id(data, primary_key_field)
  if data is not None:
    return data
  return None


def _extract_item_id(item, primary_key_field=None):
  if not isinstance(item, dict):
    return None
  if primary_key_field and item.get(primary_key_field) is not None:
    return item.get(primary_key_field)
  for candidate in ("id", "ID"):
    if item.get(candidate) is not None:
      return item.get(candidate)
  return None


def _template_context_from_payload_and_response(payload, response, primary_key_field=None):
  context = {}
  if isinstance(payload, dict):
    context.update(payload)
  data = _extract_response_data(response)
  if isinstance(data, dict):
    context.update(data)
  created_id = _extract_created_id(response, primary_key_field)
  if created_id not in (None, ""):
    context.setdefault("id", created_id)
  return context


def _expand_endpoint_template(endpoint_template, values):
  def _replace(match):
    key = match.group(1)
    if key not in values or values[key] in (None, ""):
      raise KeyError(key)
    return parse.quote(str(values[key]), safe="")

  return re.sub(r"\$\{([^}]+)\}", _replace, endpoint_template)


def _response_notes(step_name, response):
  business_code = _extract_business_code(response)
  notes = f"{step_name}.http={response.get('http_status')} {step_name}.code={business_code}"
  if response.get("payload_parse_error"):
    notes = f"{notes} {step_name}.payload_parse_error={response.get('payload_parse_error')}"
  payload = response.get("payload") or {}
  if isinstance(payload, dict) and payload.get("msg"):
    notes = f"{notes} {step_name}.msg={payload.get('msg')}"
  return notes


def _build_case_url(base_url, endpoint, company_id=None):
  return _append_company_query(base_url.rstrip("/") + endpoint, company_id)


def _extract_response_items(response):
  data = _extract_response_data(response)
  if isinstance(data, dict):
    items = data.get("items")
    if isinstance(items, list):
      return items
  if isinstance(data, list):
    return data
  return []


def _resolve_reference_dependency(dependency, base_url, token, company_id):
  field_name = dependency.get("field_name", "")
  model_key = dependency.get("model_key")
  if not model_key:
    raise RuntimeDependencyResolutionError(field_name, "regression", f"missing model_key for {field_name}")

  lookup_url = _build_case_url(base_url, _model_key_to_api_path(model_key), company_id)
  separator = "&" if "?" in lookup_url else "?"
  lookup_url = f"{lookup_url}{separator}{parse.urlencode({'page': 1, 'perPage': 1})}"
  response = _request_json(lookup_url, token=token)
  http_status = response.get("http_status")
  business_code = _extract_business_code(response)
  classification = classify_execution_result(
      http_status,
      business_code,
      response.get("payload_parse_error", ""),
  )
  if classification["classification"] != "pass":
    raise RuntimeDependencyResolutionError(
        field_name,
        classification["classification"],
        f"runtime lookup failed for {field_name}",
        http_status=http_status,
        business_code=business_code,
    )

  items = _extract_response_items(response)
  if not items:
    seeded = _seed_reference_dependency_if_possible(dependency, base_url, token)
    if seeded:
      response = _request_json(lookup_url, token=token)
      http_status = response.get("http_status")
      business_code = _extract_business_code(response)
      classification = classify_execution_result(
          http_status,
          business_code,
          response.get("payload_parse_error", ""),
      )
      if classification["classification"] == "pass":
        items = _extract_response_items(response)
    if not items:
      raise RuntimeDependencyResolutionError(field_name, "environment-gap", f"no selectable data for {field_name}")

  item = items[0]
  if isinstance(item, dict):
    for candidate in (
        dependency.get("reference_key"),
        field_name,
        "id",
        "code",
    ):
      if not candidate:
        continue
      value = item.get(candidate)
      if value not in (None, ""):
        return value, f"runtime.{field_name}={value}"

  raise RuntimeDependencyResolutionError(field_name, "regression", f"no usable value found for {field_name}")


def _seed_reference_dependency_if_possible(dependency, base_url, token):
  model_key = dependency.get("model_key")
  suffix = _unique_token()
  if model_key == "baseInfo.factoryArea":
    payload = {
        "id": f"seed-factory-{suffix}",
        "factoryNo": f"seed-factory-{suffix}",
        "factoryNm": f"Seed Factory {suffix}",
        "carNums": "1",
        "range": "1",
        "isEffect": "0",
    }
    response = _request_json(base_url.rstrip("/") + "/api/base-info/factory-area", method="POST", token=token, body=payload)
  elif model_key == "baseInfo.customerInfo":
    dict_response = _request_json(base_url.rstrip("/") + "/api/sys/data-dict/dictCustType", token=token)
    dict_classification = classify_execution_result(
        dict_response.get("http_status"),
        _extract_business_code(dict_response),
        dict_response.get("payload_parse_error", ""),
    )
    if dict_classification["classification"] != "pass":
      return False
    dict_items = _extract_response_items(dict_response)
    cust_type = None
    if dict_items and isinstance(dict_items[0], dict):
      cust_type = dict_items[0].get("code") or dict_items[0].get("value") or dict_items[0].get("id")
    if cust_type in (None, ""):
      return False
    payload = {
        "id": f"seed-customer-{suffix}",
        "customerNo": f"seed-customer-{suffix}",
        "customerNm": f"Seed Customer {suffix}",
        "custType": cust_type,
        "isEffect": "1",
        "isAvailable": "1",
        "allowedOwe": "0",
        "creditLimit": "0",
        "loginName": f"seed-login-{suffix}",
        "loginPass": "123456",
    }
    response = _request_json(base_url.rstrip("/") + "/api/base-info/customer-info", method="POST", token=token, body=payload)
  else:
    return False

  classification = classify_execution_result(
      response.get("http_status"),
      _extract_business_code(response),
      response.get("payload_parse_error", ""),
  )
  return classification["classification"] == "pass"


def _resolve_data_dict_dependency(dependency, base_url, token):
  field_name = dependency.get("field_name", "")
  dict_name = dependency.get("dict_name")
  if not dict_name:
    raise RuntimeDependencyResolutionError(field_name, "regression", f"missing dict_name for {field_name}")

  lookup_url = base_url.rstrip("/") + "/api/sys/data-dict/" + parse.quote(str(dict_name))
  response = _request_json(lookup_url, token=token)
  http_status = response.get("http_status")
  business_code = _extract_business_code(response)
  classification = classify_execution_result(
      http_status,
      business_code,
      response.get("payload_parse_error", ""),
  )
  if classification["classification"] != "pass":
    raise RuntimeDependencyResolutionError(
        field_name,
        classification["classification"],
        f"runtime dict lookup failed for {field_name}",
        http_status=http_status,
        business_code=business_code,
    )

  items = _extract_response_items(response)
  if not items:
    raise RuntimeDependencyResolutionError(field_name, "environment-gap", f"no data dict item for {field_name}")

  item = items[0]
  if isinstance(item, dict):
    for candidate in ("code", "id", "value"):
      value = item.get(candidate)
      if value not in (None, ""):
        return value, f"runtime.{field_name}={value}"

  raise RuntimeDependencyResolutionError(field_name, "regression", f"no usable data dict value found for {field_name}")


def _resolve_runtime_write_dependencies(case, base_url, token, company_id):
  resolved = {}
  notes = []
  for dependency in case.get("runtime_write_dependencies", []):
    dependency_kind = dependency.get("dependency_kind")
    if dependency_kind == "reference":
      value, note = _resolve_reference_dependency(dependency, base_url, token, company_id)
    elif dependency_kind == "data-dict":
      value, note = _resolve_data_dict_dependency(dependency, base_url, token)
    else:
      raise RuntimeDependencyResolutionError(
          dependency.get("field_name", ""),
          "regression",
          f"unsupported runtime dependency kind {dependency_kind}",
      )
    resolved[dependency["field_name"]] = value
    notes.append(note)
  return resolved, notes


def _resolve_case_target_and_token(case, base_url, token, app_base_url=None, app_token=None):
  target_base_url, target_name = _resolve_case_target(case, base_url, app_base_url)
  target_token = app_token if target_name == "mothership" else token
  return target_base_url, target_name, target_token


def _execute_metadata_detail_case(case, base_url, token, company_id, app_base_url=None, app_token=None):
  target_base_url, target_name, target_token = _resolve_case_target_and_token(case, base_url, token, app_base_url, app_token)
  notes = []
  if target_name != "default":
    notes.append(f"target={target_name}")
  if company_id:
    notes.append(f"companyId={company_id}")

  list_endpoint = case.get("endpoint", "").replace("/${id}", "")
  list_query = {
      "page": 1,
      "perPage": 1,
  }
  if company_id:
    list_query["companyId"] = company_id
  list_url = target_base_url.rstrip("/") + list_endpoint + "?" + parse.urlencode(list_query, doseq=True)
  list_response = _request_json(list_url, token=target_token)
  list_business_code = _extract_business_code(list_response)
  list_classification = classify_execution_result(
      list_response.get("http_status"),
      list_business_code,
      list_response.get("payload_parse_error", ""),
  )
  notes.append(_response_notes("list", list_response))
  if list_classification["classification"] != "pass":
    return {
        "case_id": case["case_id"],
        "endpoint": case["endpoint"],
        "method": case["method"],
        "http_status": list_response.get("http_status"),
        "business_code": list_business_code,
        "actual_result": list_classification["actual_result"],
        "classification": list_classification["classification"],
        "notes": "; ".join(notes),
        "response_excerpt": (list_response.get("raw") or "")[:500],
    }

  items = _extract_response_items(list_response)
  if not items:
    return {
        "case_id": case["case_id"],
        "endpoint": case["endpoint"],
        "method": case["method"],
        "http_status": list_response.get("http_status"),
        "business_code": list_business_code,
        "actual_result": "FAIL",
        "classification": "environment-gap",
        "notes": "; ".join(notes + ["detail seed list returned no rows"]),
        "response_excerpt": (list_response.get("raw") or "")[:500],
    }

  detail_id = _extract_item_id(items[0], case.get("detail_primary_key_field"))
  if detail_id in (None, ""):
    return {
        "case_id": case["case_id"],
        "endpoint": case["endpoint"],
        "method": case["method"],
        "http_status": list_response.get("http_status"),
        "business_code": list_business_code,
        "actual_result": "FAIL",
        "classification": "regression",
        "notes": "; ".join(notes + ["failed to resolve detail id from list item"]),
        "response_excerpt": (list_response.get("raw") or "")[:500],
    }

  notes.append(f"detail.id={detail_id}")
  detail_endpoint = case["endpoint"].replace("${id}", parse.quote(str(detail_id)))
  detail_url = _build_case_url(target_base_url, detail_endpoint, company_id)
  detail_response = _request_json(detail_url, token=target_token)
  detail_business_code = _extract_business_code(detail_response)
  detail_classification = classify_execution_result(
      detail_response.get("http_status"),
      detail_business_code,
      detail_response.get("payload_parse_error", ""),
  )
  notes.append(_response_notes("detail", detail_response))

  return {
      "case_id": case["case_id"],
      "endpoint": case["endpoint"],
      "method": case["method"],
      "http_status": detail_response.get("http_status"),
      "business_code": detail_business_code,
      "actual_result": detail_classification["actual_result"],
      "classification": detail_classification["classification"],
      "notes": "; ".join(notes),
      "response_excerpt": (detail_response.get("raw") or "")[:500],
  }


def _resolve_existing_metadata_item(case, base_url, token, company_id):
  list_endpoint = case.get("endpoint", "").replace("/${id}", "")
  list_query = {
      "page": 1,
      "perPage": 1,
  }
  if company_id:
    list_query["companyId"] = company_id
  list_url = base_url.rstrip("/") + list_endpoint + "?" + parse.urlencode(list_query, doseq=True)
  list_response = _request_json(list_url, token=token)
  list_business_code = _extract_business_code(list_response)
  list_classification = classify_execution_result(
      list_response.get("http_status"),
      list_business_code,
      list_response.get("payload_parse_error", ""),
  )
  if list_classification["classification"] != "pass":
    return None, list_response, list_business_code, list_classification

  items = _extract_response_items(list_response)
  if not items:
    return None, list_response, list_business_code, {
        "actual_result": "FAIL",
        "classification": "environment-gap",
    }

  existing_id = _extract_item_id(items[0], case.get("write_primary_key_field"))
  if existing_id in (None, ""):
    return None, list_response, list_business_code, {
        "actual_result": "FAIL",
        "classification": "regression",
    }
  return existing_id, list_response, list_business_code, list_classification


def _execute_metadata_write_case(case, base_url, token, company_id, app_base_url=None, app_token=None):
  target_base_url, target_name, target_token = _resolve_case_target_and_token(case, base_url, token, app_base_url, app_token)
  unique_suffix = _unique_token()
  create_endpoint = case.get("endpoint", "").replace("/${id}", "")
  notes = []
  if target_name != "default":
    notes.append(f"target={target_name}")
  if company_id:
    notes.append(f"companyId={company_id}")

  try:
    runtime_values, runtime_notes = _resolve_runtime_write_dependencies(case, target_base_url, target_token, company_id)
  except RuntimeDependencyResolutionError as exc:
    notes.append(f"runtime-resolution.{exc.field_name}={str(exc)}")
    return {
        "case_id": case["case_id"],
        "endpoint": case["endpoint"],
        "method": case["method"],
        "http_status": exc.http_status,
        "business_code": exc.business_code,
        "actual_result": "FAIL",
        "classification": exc.classification,
        "notes": "; ".join(notes),
        "response_excerpt": "",
    }

  notes.extend(runtime_notes)

  try:
    create_payload = _materialize_sample_payload(case, company_id, unique_suffix)
  except ExecutionPreconditionError as exc:
    return {
        "case_id": case["case_id"],
        "endpoint": case["endpoint"],
        "method": case["method"],
        "http_status": None,
        "business_code": None,
        "actual_result": "FAIL",
        "classification": EXECUTION_PRECONDITION_GAP,
        "notes": "; ".join(notes + [str(exc)]),
        "response_excerpt": "",
    }
  create_payload.update(runtime_values)
  create_url = _build_case_url(target_base_url, create_endpoint, company_id)
  create_response = _request_json(create_url, method="POST", token=target_token, body=create_payload)
  create_classification = classify_case_execution(
      case,
      create_response.get("http_status"),
      _extract_business_code(create_response),
      create_response.get("payload"),
      create_response.get("payload_parse_error", ""),
  )
  notes.append(f"create.payload={json.dumps(create_payload, ensure_ascii=False, separators=(',', ':'))}")
  notes.append(_response_notes("create", create_response))

  created_id = None
  if create_classification["classification"] == "pass":
    created_id = _extract_created_id(create_response, case.get("write_primary_key_field")) or create_payload.get(case.get("write_primary_key_field"))
  elif case.get("scenario") in ("update", "delete"):
    notes.append("create-seed-fallback=list-existing")
    existing_id, list_response, list_business_code, list_classification = _resolve_existing_metadata_item(
        case,
        target_base_url,
        target_token,
        company_id,
    )
    notes.append(_response_notes("seed-list", list_response))
    if existing_id is not None:
      created_id = existing_id
    else:
      payload = list_response.get("payload") or {}
      if case.get("module", "").startswith(APP_NAMESPACE_PREFIX) \
          and list_response.get("http_status") == 405 \
          and _extract_business_code(list_response) == -1 \
          and isinstance(payload, dict) \
          and isinstance(payload.get("msg"), str) \
          and "Request method" in payload.get("msg"):
        list_classification = {
            "actual_result": "FAIL",
            "classification": "reserved-route-conflict",
        }
      return {
          "case_id": case["case_id"],
          "endpoint": case["endpoint"],
          "method": case["method"],
          "http_status": list_response.get("http_status"),
          "business_code": list_business_code,
          "actual_result": list_classification["actual_result"],
          "classification": list_classification["classification"],
          "notes": "; ".join(notes),
          "response_excerpt": (list_response.get("raw") or "")[:500],
      }
  else:
    return {
        "case_id": case["case_id"],
        "endpoint": case["endpoint"],
        "method": case["method"],
        "http_status": create_response.get("http_status"),
        "business_code": _extract_business_code(create_response),
        "actual_result": create_classification["actual_result"],
        "classification": create_classification["classification"],
        "notes": "; ".join(notes),
        "response_excerpt": (create_response.get("raw") or "")[:500],
    }

  if created_id is None:
    return {
        "case_id": case["case_id"],
        "endpoint": case["endpoint"],
        "method": case["method"],
        "http_status": create_response.get("http_status"),
        "business_code": _extract_business_code(create_response),
        "actual_result": "FAIL",
        "classification": "regression",
        "notes": "; ".join(notes + ["missing created id after successful create"]),
        "response_excerpt": (create_response.get("raw") or "")[:500],
    }

  operation_response = create_response
  operation_classification = create_classification
  response_excerpt = (create_response.get("raw") or "")[:500]

  if case.get("scenario") == "update":
    update_payload = {}
    update_case = dict(case)
    update_case["sample_payload"] = case.get("sample_update_payload", {})
    try:
      update_payload = _materialize_sample_payload(update_case, company_id, unique_suffix)
    except ExecutionPreconditionError as exc:
      return {
          "case_id": case["case_id"],
          "endpoint": case["endpoint"],
          "method": case["method"],
          "http_status": None,
          "business_code": None,
          "actual_result": "FAIL",
          "classification": EXECUTION_PRECONDITION_GAP,
          "notes": "; ".join(notes + [str(exc)]),
          "response_excerpt": "",
      }
    update_url = _build_case_url(target_base_url, case["endpoint"].replace("${id}", parse.quote(str(created_id))), company_id)
    operation_response = _request_json(update_url, method=case.get("method", "PATCH"), token=target_token, body=update_payload)
    operation_classification = classify_execution_result(
        operation_response.get("http_status"),
        _extract_business_code(operation_response),
        operation_response.get("payload_parse_error", ""),
    )
    notes.append(f"update.payload={json.dumps(update_payload, ensure_ascii=False, separators=(',', ':'))}")
    notes.append(_response_notes("update", operation_response))
    response_excerpt = (operation_response.get("raw") or "")[:500]
  elif case.get("scenario") == "delete":
    delete_url = _build_case_url(target_base_url, case["endpoint"].replace("${id}", parse.quote(str(created_id))), company_id)
    operation_response = _request_json(delete_url, method="DELETE", token=target_token)
    operation_classification = classify_execution_result(
        operation_response.get("http_status"),
        _extract_business_code(operation_response),
        operation_response.get("payload_parse_error", ""),
    )
    notes.append(_response_notes("delete", operation_response))
    response_excerpt = (operation_response.get("raw") or "")[:500]

  cleanup_response = None
  cleanup_classification = None
  if case.get("scenario") in ("add", "update"):
    cleanup_url = _build_case_url(target_base_url, create_endpoint + "/" + parse.quote(str(created_id)), company_id)
    cleanup_response = _request_json(cleanup_url, method="DELETE", token=target_token)
    cleanup_classification = classify_execution_result(
        cleanup_response.get("http_status"),
        _extract_business_code(cleanup_response),
        cleanup_response.get("payload_parse_error", ""),
    )
    notes.append(f"cleanup={'PASS' if cleanup_classification['classification'] == 'pass' else 'FAIL'}")
    notes.append(_response_notes("cleanup", cleanup_response))

  final_response = operation_response
  final_classification = operation_classification
  if cleanup_classification and cleanup_classification["classification"] != "pass":
    final_response = cleanup_response
    final_classification = cleanup_classification
    response_excerpt = (cleanup_response.get("raw") or "")[:500]

  return {
      "case_id": case["case_id"],
      "endpoint": case["endpoint"],
      "method": case["method"],
      "http_status": final_response.get("http_status"),
      "business_code": _extract_business_code(final_response),
      "actual_result": final_classification["actual_result"],
      "classification": final_classification["classification"],
      "notes": "; ".join(notes),
      "response_excerpt": response_excerpt,
  }


def _execute_high_code_seed_case(case, base_url, token, company_id, app_base_url=None, app_token=None):
  target_base_url, target_name, target_token = _resolve_case_target_and_token(case, base_url, token, app_base_url, app_token)
  context_cleanup_steps = []
  pre_notes = []
  try:
    query = _resolve_sample_query(case.get("sample_query", {}), company_id)
  except ExecutionPreconditionError as exc:
    return {
        "case_id": case["case_id"],
        "endpoint": case["endpoint"],
        "method": case["method"],
        "http_status": None,
        "business_code": None,
        "actual_result": "FAIL",
        "classification": EXECUTION_PRECONDITION_GAP,
        "notes": str(exc),
        "response_excerpt": "",
    }
  url = case["endpoint"]
  if query:
    url = f"{url}?{parse.urlencode(query, doseq=True)}"

  unique_suffix = _unique_token()
  try:
    body = _materialize_high_code_seed_payload(
        case.get("sample_payload"),
        unique_suffix,
        company_id=company_id,
        primary_key_field=case.get("write_primary_key_field", ""),
    ) if case.get("sample_payload") is not None else None
  except ExecutionPreconditionError as exc:
    return {
        "case_id": case["case_id"],
        "endpoint": case["endpoint"],
        "method": case["method"],
        "http_status": None,
        "business_code": None,
        "actual_result": "FAIL",
        "classification": EXECUTION_PRECONDITION_GAP,
        "notes": str(exc),
        "response_excerpt": "",
    }

  if case.get("scenario") == "roundtrip" and case.get("endpoint") == "/api/sys/configuration" and not body:
    try:
      body, roundtrip_notes = _build_configuration_roundtrip_payload(target_base_url, target_token)
      pre_notes.extend(roundtrip_notes)
    except RuntimeDependencyResolutionError as exc:
      return {
          "case_id": case["case_id"],
          "endpoint": case["endpoint"],
          "method": case["method"],
          "http_status": exc.http_status,
          "business_code": exc.business_code,
          "actual_result": "FAIL",
          "classification": exc.classification,
          "notes": "; ".join(pre_notes + [str(exc)]),
          "response_excerpt": "",
      }

  if case.get("scenario") == "employee-bind-info":
    try:
      contextual_token, contextual_notes, context_cleanup_steps = _build_contextual_employee_binding(
          target_base_url,
          target_token,
          unique_suffix,
      )
      target_token = contextual_token
      pre_notes.extend(contextual_notes)
    except RuntimeDependencyResolutionError as exc:
      return {
          "case_id": case["case_id"],
          "endpoint": case["endpoint"],
          "method": case["method"],
          "http_status": exc.http_status,
          "business_code": exc.business_code,
          "actual_result": "FAIL",
          "classification": exc.classification,
          "notes": "; ".join(pre_notes + [str(exc)]),
          "response_excerpt": "",
      }

  response = _request_json(target_base_url + url, method=case.get("method", "GET"), token=target_token, body=body)
  business_code = _extract_business_code(response)
  classification = classify_execution_result(
      response.get("http_status"),
      business_code,
      response.get("payload_parse_error", ""),
  )
  notes = "; ".join(pre_notes + [f"http={response.get('http_status')} code={business_code}"])
  if target_name != "default":
    notes = f"target={target_name}; {notes}"
  if query:
    notes = f"query={json.dumps(query, ensure_ascii=False, separators=(',', ':'))}; {notes}"
  if body is not None:
    notes = f"payload={json.dumps(body, ensure_ascii=False, separators=(',', ':'))}; {notes}"
  payload = response.get("payload") or {}
  if isinstance(payload, dict) and payload.get("msg"):
    notes = f"{notes}; msg={payload.get('msg')}"

  cleanup_response = None
  cleanup_classification = None
  cleanup_steps = []
  cleanup_method = case.get("cleanup_method")
  cleanup_endpoint = case.get("cleanup_endpoint")
  if cleanup_method and cleanup_endpoint:
    cleanup_steps.append({
        "method": cleanup_method,
        "endpoint": cleanup_endpoint,
        "note_prefix": "cleanup",
    })
  cleanup_steps.extend(case.get("cleanup_followups") or [])
  template_context = _template_context_from_payload_and_response(body, response, case.get("write_primary_key_field", ""))
  if classification["classification"] == "pass" and cleanup_steps:
    try:
      cleanup_response, cleanup_classification, cleanup_notes = _execute_cleanup_steps(
          target_base_url,
          target_token,
          cleanup_steps,
          template_context,
      )
      notes = f"{notes}; {'; '.join(cleanup_notes)}"
    except KeyError as exc:
      return {
          "case_id": case["case_id"],
          "endpoint": case["endpoint"],
          "method": case["method"],
          "http_status": response.get("http_status"),
          "business_code": business_code,
          "actual_result": "FAIL",
          "classification": "regression",
          "notes": f"{notes}; cleanup-missing-template-value={str(exc)}",
          "response_excerpt": (response.get("raw") or "")[:500],
      }

  if context_cleanup_steps:
    context_cleanup_response, context_cleanup_classification, context_cleanup_notes = _execute_cleanup_steps(
        target_base_url,
        token,
        context_cleanup_steps,
        template_context,
    )
    notes = f"{notes}; {'; '.join(context_cleanup_notes)}"
    if (cleanup_classification is None or cleanup_classification["classification"] == "pass") \
        and context_cleanup_classification is not None and context_cleanup_classification["classification"] != "pass":
      cleanup_response = context_cleanup_response
      cleanup_classification = context_cleanup_classification

  final_response = cleanup_response or response
  final_classification = cleanup_classification or classification

  return {
      "case_id": case["case_id"],
      "endpoint": case["endpoint"],
      "method": case["method"],
      "http_status": final_response.get("http_status"),
      "business_code": _extract_business_code(final_response),
      "actual_result": final_classification["actual_result"],
      "classification": final_classification["classification"],
      "notes": notes,
      "response_excerpt": (final_response.get("raw") or "")[:500],
  }


def _execute_metadata_case(case, base_url, token, company_id, app_base_url=None, app_token=None):
  target_base_url, target_name, target_token = _resolve_case_target_and_token(case, base_url, token, app_base_url, app_token)
  classification_case = case
  if target_name == "mothership":
    classification_case = dict(case)
    classification_case["reserved_route_conflict"] = False

  try:
    query = _build_case_query(case, company_id)
  except ExecutionPreconditionError as exc:
    notes = str(exc)
    if target_name != "default":
      notes = f"target={target_name}; {notes}"
    return {
        "case_id": case["case_id"],
        "endpoint": case["endpoint"],
        "method": case["method"],
        "http_status": None,
        "business_code": None,
        "actual_result": "FAIL",
        "classification": EXECUTION_PRECONDITION_GAP,
        "notes": notes,
        "response_excerpt": "",
    }

  url = case["endpoint"]
  if query:
    url = f"{url}?{parse.urlencode(query, doseq=True)}"

  response = _request_json(target_base_url + url, token=target_token)
  business_code = _extract_business_code(response)
  payload = response.get("payload") or {}
  classification = classify_case_execution(
      classification_case,
      response.get("http_status"),
      business_code,
      payload,
      response.get("payload_parse_error", ""),
  )
  notes = f"http={response.get('http_status')} code={business_code}"
  if company_id:
    notes = f"companyId={company_id}; {notes}"
  if case.get("scenario") == "query-conditions" and case.get("sample_query"):
    notes = f"query={json.dumps(_resolve_sample_query(case.get('sample_query', {}), company_id), ensure_ascii=False, separators=(',', ':'))}; {notes}"
  if case.get("reserved_route_conflict") and target_name == "default":
    notes = f"reserved-route={case.get('reserved_route_reason')}; {notes}"
  if target_name != "default":
    notes = f"target={target_name}; {notes}"

  if isinstance(payload, dict) and payload.get("msg"):
    notes = f"{notes}; msg={payload.get('msg')}"

  return {
      "case_id": case["case_id"],
      "endpoint": case["endpoint"],
      "method": case["method"],
      "http_status": response.get("http_status"),
      "business_code": business_code,
      "actual_result": classification["actual_result"],
      "classification": classification["classification"],
      "notes": notes,
      "response_excerpt": (response.get("raw") or "")[:500],
  }


def _build_merge_readiness(summary):
  blockers = []
  if summary.get("fail_count", 0):
    blockers.append(f"存在 {summary.get('fail_count', 0)} 个失败用例")
  if summary.get("metadata_read_coverage_percent", 0) < METADATA_GATE_THRESHOLD:
    blockers.append(
        f"元数据读覆盖率 {summary.get('metadata_read_coverage_percent', 0)}% < {METADATA_GATE_THRESHOLD}%"
    )
  if summary.get("eligible_metadata_detail_model_count", 0) and summary.get("metadata_detail_coverage_percent", 0) < METADATA_GATE_THRESHOLD:
    blockers.append(
        f"元数据详情覆盖率 {summary.get('metadata_detail_coverage_percent', 0)}% < {METADATA_GATE_THRESHOLD}%"
    )
  if summary.get("eligible_metadata_write_model_count", 0) and summary.get("metadata_write_coverage_percent", 0) < METADATA_GATE_THRESHOLD:
    blockers.append(
        f"元数据写覆盖率 {summary.get('metadata_write_coverage_percent', 0)}% < {METADATA_GATE_THRESHOLD}%"
    )
  if summary.get("eligible_high_code_primary_read_case_count", 0) and summary.get("high_code_primary_read_coverage_percent", 0) < PRIMARY_SEED_GATE_THRESHOLD:
    blockers.append(
        f"高代码主门禁读覆盖率 {summary.get('high_code_primary_read_coverage_percent', 0)}% < {PRIMARY_SEED_GATE_THRESHOLD}%"
    )
  if summary.get("pending_high_code_primary_read_case_count", 0):
    blockers.append(f"高代码主门禁读仍有 {summary.get('pending_high_code_primary_read_case_count', 0)} 个用例未具备执行条件")
  if summary.get("eligible_high_code_primary_write_case_count", 0) and summary.get("high_code_primary_write_coverage_percent", 0) < PRIMARY_SEED_GATE_THRESHOLD:
    blockers.append(
        f"高代码主门禁写覆盖率 {summary.get('high_code_primary_write_coverage_percent', 0)}% < {PRIMARY_SEED_GATE_THRESHOLD}%"
    )
  if summary.get("pending_high_code_primary_write_case_count", 0):
    blockers.append(f"高代码主门禁写仍有 {summary.get('pending_high_code_primary_write_case_count', 0)} 个用例未具备执行条件")

  if not blockers:
    return "各门禁分段均已达标，可进入下一轮合并评审；仍需结合专项集成结果综合判断"
  return "暂不具备进入合并评审条件: " + "；".join(blockers)


def run(args):
  inventory_records, inventory_summary = _build_inventory(args.app_root)
  cases = build_case_matrix(
      [record for record in inventory_records if record.get("source_kind") == "metadata"],
      _load_seed_cases(),
  )
  high_code_inventory_drift = _compute_high_code_inventory_drift(inventory_records, cases)

  _write_inventory_and_case_artifacts(inventory_records, inventory_summary, cases, args.docs_report_dir)
  if args.inventory_only:
    print("inventory artifacts generated")
    return 0

  if args.execute:
    selected_cases = select_execution_cases(cases, args.execution_ring)
    runtime_targets = _collect_runtime_targets(args.base_url, args.app_base_url, selected_cases)
    required_target_names = {runtime_target["target"] for runtime_target in runtime_targets}
    preflight_reports, preflight_failures, preflight_cases = _preflight_runtime_targets(
        args.base_url,
        args.app_base_url,
        selected_cases=selected_cases,
    )
    if preflight_failures:
      result_path, _previous_results = _load_previous_results(args.report_dir)
      summary = _write_results_placeholder(args.report_dir, inventory_summary, cases)
      summary["inventory_summary"] = inventory_summary
      summary["fail_count"] = len(preflight_failures)
      summary["failures"] = preflight_failures
      summary["executed_cases"] = preflight_cases
      summary["runtime_preflight"] = preflight_reports
      summary.update(high_code_inventory_drift)
      summary["merge_readiness"] = "运行时基线无效，本轮执行已中止；必须先修复 runtime 完整性后才能判断模块是否回归"
      write_json(summary, result_path)
      write_text(render_summary_report(summary), Path(args.docs_report_dir) / REPORT_FILE_NAMES["summary_md"])
      print("execute mode aborted: runtime baseline invalid")
      return 1

    token = None
    effective_company_id = args.company_id
    if "default" in required_target_names:
      try:
        token = _load_token(args, args.base_url, args.token_file)
      except RuntimeDependencyResolutionError as exc:
        return _abort_execute_with_runtime_failure(
            args.report_dir,
            args.docs_report_dir,
            inventory_summary,
            cases,
            preflight_reports,
            high_code_inventory_drift,
            "default",
            args.token_file or args.base_url.rstrip("/") + "/api/auth/login",
            exc,
        )
      try:
        _validate_authenticated_authority_surface(
            args.base_url,
            token,
            _selected_cases_for_target(selected_cases, args.base_url, args.app_base_url, "default"),
            "default",
        )
      except RuntimeDependencyResolutionError as exc:
        return _abort_execute_with_runtime_failure(
            args.report_dir,
            args.docs_report_dir,
            inventory_summary,
            cases,
            preflight_reports,
            high_code_inventory_drift,
            "default",
            args.base_url.rstrip("/") + RUNTIME_HTTP_PROBE_PATH,
            exc,
        )

      try:
        effective_company_id, _company_resolution_notes = _resolve_execution_company_id(
            args.base_url,
            token,
            selected_cases,
            args.company_id,
        )
      except RuntimeDependencyResolutionError as exc:
        return _abort_execute_with_runtime_failure(
            args.report_dir,
            args.docs_report_dir,
            inventory_summary,
            cases,
            preflight_reports,
            high_code_inventory_drift,
            "default",
            args.base_url.rstrip("/") + "/api/organization/companies",
            exc,
        )

    app_token = None
    if "mothership" in required_target_names and args.app_base_url:
      try:
        app_token = _load_token(args, args.app_base_url, args.app_token_file)
      except RuntimeDependencyResolutionError as exc:
        return _abort_execute_with_runtime_failure(
            args.report_dir,
            args.docs_report_dir,
            inventory_summary,
            cases,
            preflight_reports,
            high_code_inventory_drift,
            "mothership",
            args.app_token_file or args.app_base_url.rstrip("/") + "/api/auth/login",
            exc,
        )
      try:
        _validate_authenticated_authority_surface(
            args.app_base_url,
            app_token,
            _selected_cases_for_target(selected_cases, args.base_url, args.app_base_url, "mothership"),
            "mothership",
        )
      except RuntimeDependencyResolutionError as exc:
        return _abort_execute_with_runtime_failure(
            args.report_dir,
            args.docs_report_dir,
            inventory_summary,
            cases,
            preflight_reports,
            high_code_inventory_drift,
            "mothership",
            args.app_base_url.rstrip("/") + RUNTIME_HTTP_PROBE_PATH,
            exc,
        )

    new_executed_cases = [
        (
            _execute_metadata_write_case(case, args.base_url, token, effective_company_id, args.app_base_url, app_token)
            if case.get("source_kind") == "metadata" and case.get("scenario") in ("add", "update", "delete")
            else _execute_metadata_detail_case(case, args.base_url, token, effective_company_id, args.app_base_url, app_token)
            if case.get("source_kind") == "metadata" and case.get("scenario") == "detail"
            else _execute_high_code_seed_case(case, args.base_url, token, effective_company_id, args.app_base_url, app_token)
            if case.get("source_kind") == "high-code-seed"
            else _execute_metadata_case(case, args.base_url, token, effective_company_id, args.app_base_url, app_token)
        )
        for case in selected_cases
    ]

    result_path, previous_results = _load_previous_results(args.report_dir)
    combined_executed_cases = _merge_executed_cases(previous_results.get("executed_cases", []), new_executed_cases)
    merged_cases, summary = merge_execution_results(cases, combined_executed_cases)
    summary["inventory_summary"] = inventory_summary
    summary["runtime_preflight"] = preflight_reports
    summary.update(high_code_inventory_drift)
    summary["merge_readiness"] = _build_merge_readiness(summary)

    _write_inventory_and_case_artifacts(inventory_records, inventory_summary, merged_cases, args.docs_report_dir)
    write_json(summary, result_path)
    write_text(render_summary_report(summary), Path(args.docs_report_dir) / REPORT_FILE_NAMES["summary_md"])
    print(f"execute mode completed: {len(new_executed_cases)} new cases")
    return 0

  summary = _write_results_placeholder(args.report_dir, inventory_summary, cases)
  summary.update(high_code_inventory_drift)
  write_text(render_summary_report(summary), Path(args.docs_report_dir) / REPORT_FILE_NAMES["summary_md"])

  return 0


def parse_args():
  parser = argparse.ArgumentParser(description="Run metadata/high-code data API regression tooling")
  parser.add_argument("--base-url")
  parser.add_argument("--inventory-only", action="store_true")
  parser.add_argument("--execute", action="store_true")
  parser.add_argument("--report-dir", required=True)
  parser.add_argument("--docs-report-dir", required=True)
  parser.add_argument("--app-root", required=True)
  parser.add_argument("--company-id")
  parser.add_argument("--execution-ring", default="metadata-list")
  parser.add_argument("--token-file")
  parser.add_argument("--app-base-url")
  parser.add_argument("--app-token-file")
  return parser.parse_args()


if __name__ == "__main__":
  raise SystemExit(run(parse_args()))

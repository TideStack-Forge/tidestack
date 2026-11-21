RISK_ORDER = {
    "P0": 0,
    "P1": 1,
    "P2": 2,
}

HTTP_METHODS = {
    "search": "GET",
    "getOne": "GET",
    "add": "POST",
    "update": "PATCH",
    "delete": "DELETE",
}


def _base_case(record, case_id, scenario, risk, include_query=False, include_payload=False):
  case = {
      "case_id": case_id,
      "source_kind": record.get("source_kind", "metadata"),
      "module": record.get("model_key") or record.get("family", ""),
      "endpoint": (record.get("urls") or [record.get("endpoint", "")])[0],
      "method": HTTP_METHODS.get(record.get("api_name"), record.get("method", "GET")),
      "scenario": scenario,
      "risk": risk,
      "actual_result": "",
      "notes": "",
      "query_param_form": "",
      "sample_query": {},
      "payload_form": "",
      "sample_payload": {},
      "sample_update_payload": {},
      "detail_primary_key_field": record.get("detail_primary_key_field", ""),
      "runtime_write_dependencies": [],
      "write_primary_key_field": "",
      "stage_one_write_executable": False,
      "stage_one_write_reason": "",
      "regression_bucket": record.get("regression_bucket", "primary"),
      "case_note": record.get("case_note", ""),
      "reserved_route_conflict": record.get("reserved_route_conflict", False),
      "reserved_route_reason": record.get("reserved_route_reason", ""),
  }
  if include_query:
    case["query_param_form"] = record.get("query_param_form", "")
    case["sample_query"] = record.get("sample_query", {})
  if include_payload:
    case["payload_form"] = record.get("payload_form", "")
    case["sample_payload"] = record.get("sample_payload", {})
    case["sample_update_payload"] = record.get("sample_update_payload", {})
    case["runtime_write_dependencies"] = [dict(dependency) for dependency in record.get("runtime_write_dependencies", [])]
    case["write_primary_key_field"] = record.get("write_primary_key_field", "")
    case["stage_one_write_executable"] = record.get("stage_one_write_executable", False)
    case["stage_one_write_reason"] = record.get("stage_one_write_reason", "")
  return case


def _build_metadata_cases(record):
  model_key = record["model_key"]
  api_name = record["api_name"]
  cases = []

  if api_name == "search":
    primary_risk = "P0" if record.get("has_before_script") else "P1"
    cases.append(_base_case(record, f"{model_key}.search.list", "list", primary_risk))
    if record.get("has_before_script") or record.get("populate_count") or record.get("query_filter_count"):
      cases.append(
          _base_case(record, f"{model_key}.search.query-conditions", "query-conditions", primary_risk, include_query=True)
      )
    return cases

  if api_name == "getOne":
    cases.append(_base_case(record, f"{model_key}.getOne.detail", "detail", "P1"))
    return cases

  if api_name in ("add", "update"):
    risk = "P1"
    cases.append(_base_case(record, f"{model_key}.{api_name}.{api_name}", api_name, risk, include_payload=True))
    if record.get("has_detail"):
      master_detail_case = _base_case(record, f"{model_key}.{api_name}.master-detail-sync", "master-detail-sync", "P0", include_payload=True)
      master_detail_case["stage_one_write_executable"] = False
      master_detail_case["stage_one_write_reason"] = "stage-two-only: master-detail sync is deferred to dedicated write regression"
      cases.append(master_detail_case)
    return cases

  if api_name == "delete":
    cases.append(_base_case(record, f"{model_key}.delete.delete", "delete", "P1", include_payload=True))
    return cases

  risk = "P0" if record.get("has_before_script") else "P2"
  cases.append(_base_case(record, f"{model_key}.{api_name}.custom-action", "custom-action", risk))
  return cases


def _flatten_seed_cases(seed_cases):
  flattened = []
  for family_entry in seed_cases:
    family = family_entry.get("family", "")
    module = family_entry.get("module", family)
    for case in family_entry.get("cases", []):
      flattened.append({
          "case_id": case["case_id"],
          "source_kind": "high-code-seed",
          "module": module,
          "family": family,
          "endpoint": case["endpoint"],
          "method": case.get("method", "GET"),
          "scenario": case["scenario"],
          "risk": case["risk"],
          "actual_result": "",
          "notes": "",
          "query_param_form": case.get("query_param_form", ""),
          "sample_query": case.get("sample_query", {}),
          "payload_form": case.get("payload_form", ""),
      "sample_payload": case.get("sample_payload", {}),
      "sample_update_payload": case.get("sample_update_payload", {}),
      "cleanup_method": case.get("cleanup_method", ""),
      "cleanup_endpoint": case.get("cleanup_endpoint", ""),
      "runtime_write_dependencies": [dict(dependency) for dependency in case.get("runtime_write_dependencies", [])],
      "write_primary_key_field": case.get("write_primary_key_field", ""),
          "stage_one_write_executable": case.get("stage_one_write_executable", False),
          "stage_one_write_reason": case.get("stage_one_write_reason", ""),
          "regression_bucket": case.get("regression_bucket", "primary"),
          "case_note": case.get("case_note", ""),
          "reserved_route_conflict": case.get("reserved_route_conflict", False),
          "reserved_route_reason": case.get("reserved_route_reason", ""),
      })
  return flattened


def build_case_matrix(inventory_records, seed_cases):
  cases = []
  for record in inventory_records:
    cases.extend(_build_metadata_cases(record))
  cases.extend(_flatten_seed_cases(seed_cases))
  return sorted(
      cases,
      key=lambda case: (
          RISK_ORDER.get(case.get("risk", "P2"), 99),
          case.get("module", ""),
          case.get("endpoint", ""),
          case.get("case_id", ""),
      ),
  )


def compute_coverage_denominator(cases, covered_risks=("P0", "P1")):
  covered_risks = set(covered_risks)
  return sum(1 for case in cases if case.get("risk") in covered_risks)


def compute_model_sampling_coverage(cases, executed_cases, target_scenarios, source_kind="metadata", case_predicate=None):
  target_scenarios = set(target_scenarios)
  indexed_cases = {
      case.get("case_id"): case
      for case in cases
      if case.get("case_id")
  }
  case_predicate = case_predicate or (lambda case: True)

  eligible_models = {
      case.get("module")
      for case in cases
      if case.get("source_kind", "metadata") == source_kind
      and case.get("module")
      and case.get("scenario") in target_scenarios
      and case_predicate(case)
  }
  sampled_models = {
      indexed_cases[executed_case.get("case_id")].get("module")
      for executed_case in executed_cases
      if executed_case.get("case_id") in indexed_cases
      and indexed_cases[executed_case.get("case_id")].get("source_kind", "metadata") == source_kind
      and indexed_cases[executed_case.get("case_id")].get("module")
      and indexed_cases[executed_case.get("case_id")].get("scenario") in target_scenarios
      and case_predicate(indexed_cases[executed_case.get("case_id")])
  }

  coverage_percent = 0
  if eligible_models:
    coverage_percent = round(len(sampled_models) / len(eligible_models) * 100, 2)

  return {
      "eligible_model_count": len(eligible_models),
      "sampled_model_count": len(sampled_models),
      "coverage_percent": coverage_percent,
      "eligible_models": sorted(eligible_models),
      "sampled_models": sorted(sampled_models),
  }

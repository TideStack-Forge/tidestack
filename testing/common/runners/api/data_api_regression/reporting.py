import csv
import json
from pathlib import Path


CASE_COLUMNS = [
    "source_kind",
    "case_id",
    "module",
    "endpoint",
    "method",
    "scenario",
    "query_param_form",
    "sample_query",
    "payload_form",
    "sample_payload",
    "risk",
    "regression_bucket",
    "stage_one_write_executable",
    "stage_one_write_reason",
    "case_note",
    "actual_result",
    "notes",
]

INVENTORY_COLUMNS = [
    "source_kind",
    "model_key",
    "api_name",
    "urls",
    "query_param_form",
    "risk",
]


def _sorted_records(records, sort_keys):
  return sorted(records, key=lambda record: tuple(record.get(key, "") for key in sort_keys))


def _format_cell(value):
  if isinstance(value, (dict, list)):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
  if value is None:
    return ""
  return str(value).replace("\n", "<br>")


def _bucket_label(bucket):
  labels = {
      "primary": "primary",
      "develop-scope": "develop-scope",
      "contextual-user": "contextual-user",
  }
  return labels.get(bucket, bucket or "primary")


def _case_source_kind(case):
  return case.get("source_kind", "metadata")


def _render_table(rows, columns):
  lines = [
      "| " + " | ".join(columns) + " |",
      "| " + " | ".join("---" for _ in columns) + " |",
  ]
  for row in rows:
    lines.append("| " + " | ".join(_format_cell(row.get(column, "")) for column in columns) + " |")
  return lines


def write_json(data, output_path):
  output_path = Path(output_path)
  output_path.parent.mkdir(parents=True, exist_ok=True)
  output_path.write_text(json.dumps(data, ensure_ascii=False, indent=2))


def write_text(text, output_path):
  output_path = Path(output_path)
  output_path.parent.mkdir(parents=True, exist_ok=True)
  output_path.write_text(text)


def render_inventory_markdown(records, summary):
  lines = [
      "# 元数据与高代码数据 API 清单",
      "",
      f"- metadata: {summary.get('metadata', 0)}",
      f"- high_code: {summary.get('high_code', 0)}",
      f"- total: {summary.get('total', len(records))}",
      "",
      "| source_kind | model_key | api_name | urls | query_param_form | risk |",
      "| --- | --- | --- | --- | --- | --- |",
  ]
  for record in _sorted_records(records, ("source_kind", "model_key", "api_name")):
    lines.append(
        "| {source_kind} | {model_key} | {api_name} | {urls} | {query_param_form} | {risk} |".format(
            source_kind=_format_cell(record.get("source_kind", "")),
            model_key=_format_cell(record.get("model_key", record.get("module", ""))),
            api_name=_format_cell(record.get("api_name", record.get("scenario", ""))),
            urls="<br>".join(record.get("urls", [record.get("endpoint", "")])),
            query_param_form=_format_cell(record.get("query_param_form", "")),
            risk=_format_cell(record.get("risk", "")),
        )
    )
  return "\n".join(lines) + "\n"


def write_case_csv(cases, output_path):
  output_path = Path(output_path)
  output_path.parent.mkdir(parents=True, exist_ok=True)
  with output_path.open("w", newline="") as handle:
    writer = csv.DictWriter(handle, fieldnames=CASE_COLUMNS)
    writer.writeheader()
    for case in _sorted_records(cases, ("module", "endpoint", "risk", "case_id")):
      writer.writerow({column: _format_cell(case.get(column, "")) for column in CASE_COLUMNS})


def render_case_markdown(cases, coverage_denominator, coverage_numerator):
  metadata_cases = [
      case for case in _sorted_records(cases, ("module", "endpoint", "risk", "case_id"))
      if _case_source_kind(case) == "metadata"
  ]
  high_code_primary_cases = [
      case for case in _sorted_records(cases, ("module", "endpoint", "risk", "case_id"))
      if _case_source_kind(case) == "high-code-seed" and case.get("regression_bucket", "primary") == "primary"
  ]
  high_code_special_cases = [
      case for case in _sorted_records(cases, ("module", "endpoint", "risk", "case_id"))
      if _case_source_kind(case) == "high-code-seed" and case.get("regression_bucket", "primary") != "primary"
  ]
  primary_gate_case_count = sum(1 for case in cases if case.get("regression_bucket", "primary") == "primary")
  special_case_count = len(cases) - primary_gate_case_count
  primary_gate_coverage_denominator = sum(
      1
      for case in cases
      if case.get("risk") in ("P0", "P1") and case.get("regression_bucket", "primary") == "primary"
  )
  primary_gate_coverage_numerator = sum(
      1
      for case in cases
      if case.get("risk") in ("P0", "P1")
      and case.get("regression_bucket", "primary") == "primary"
      and case.get("actual_result")
  )
  lines = [
      "# 元数据与高代码数据 API 测试用例表",
      "",
      f"- total cases: {len(cases)}",
      f"- primary gate cases: {primary_gate_case_count}",
      f"- special-scope cases: {special_case_count}",
      f"- case coverage denominator: {coverage_denominator}",
      f"- case coverage numerator: {coverage_numerator}",
      f"- primary gate coverage denominator: {primary_gate_coverage_denominator}",
      f"- primary gate coverage numerator: {primary_gate_coverage_numerator}",
      "",
  ]

  if high_code_primary_cases:
    lines.extend([
        "## 高代码主门禁用例",
        "",
        f"- count: {len(high_code_primary_cases)}",
        "",
    ])
    lines.extend(_render_table(high_code_primary_cases, [
        "case_id",
        "module",
        "endpoint",
        "method",
        "scenario",
        "risk",
        "actual_result",
        "case_note",
    ]))
    lines.append("")

  if high_code_special_cases:
    lines.extend([
        "## 高代码专项用例",
        "",
        "- 这些用例保留在回归资产中，但不纳入当前 mothership 通用主门禁统计。",
        "",
    ])
    special_rows = []
    for case in high_code_special_cases:
      row = dict(case)
      row["regression_bucket"] = _bucket_label(case.get("regression_bucket", "primary"))
      special_rows.append(row)
    lines.extend(_render_table(special_rows, [
        "case_id",
        "module",
        "endpoint",
        "method",
        "scenario",
        "regression_bucket",
        "case_note",
        "actual_result",
    ]))
    lines.append("")

  lines.extend([
      "## 业务模型用例矩阵",
      "",
      f"- metadata cases: {len(metadata_cases)}",
      "",
  ])
  lines.extend(_render_table(metadata_cases, [
      "case_id",
      "module",
      "endpoint",
      "method",
      "scenario",
      "query_param_form",
      "sample_query",
      "payload_form",
      "sample_payload",
      "stage_one_write_executable",
      "stage_one_write_reason",
      "risk",
      "actual_result",
  ]))
  return "\n".join(lines) + "\n"


def render_summary_report(summary):
  failures = summary.get("failures", [])
  executed_cases = summary.get("executed_cases", [])
  runtime_preflight = summary.get("runtime_preflight", [])
  lines = [
      "# 重大重构元数据与高代码数据 API 回归报告",
      "",
      f"- 业务模型抽检覆盖率: {summary.get('business_model_coverage_percent', summary.get('coverage_percent', 0))}%",
      f"- 业务模型抽检分母: {summary.get('eligible_business_model_count', summary.get('coverage_denominator', 0))}",
      f"- 业务模型抽检分子: {summary.get('sampled_business_model_count', summary.get('coverage_numerator', 0))}",
      f"- 元数据读覆盖率: {summary.get('metadata_read_coverage_percent', 0)}%",
      f"- 元数据读分母: {summary.get('eligible_metadata_read_model_count', 0)}",
      f"- 元数据读分子: {summary.get('sampled_metadata_read_model_count', 0)}",
      f"- 元数据详情覆盖率: {summary.get('metadata_detail_coverage_percent', 0)}%",
      f"- 元数据详情分母: {summary.get('eligible_metadata_detail_model_count', 0)}",
      f"- 元数据详情分子: {summary.get('sampled_metadata_detail_model_count', 0)}",
      f"- 元数据写覆盖率: {summary.get('metadata_write_coverage_percent', 0)}%",
      f"- 元数据写分母: {summary.get('eligible_metadata_write_model_count', 0)}",
      f"- 元数据写分子: {summary.get('sampled_metadata_write_model_count', 0)}",
      f"- 复杂查询模型覆盖率: {summary.get('complex_query_model_coverage_percent', 0)}%",
      f"- 复杂查询模型分母: {summary.get('eligible_complex_query_model_count', 0)}",
      f"- 复杂查询模型分子: {summary.get('sampled_complex_query_model_count', 0)}",
      f"- 高代码主门禁读覆盖率: {summary.get('high_code_primary_read_coverage_percent', 0)}%",
      f"- 高代码主门禁读分母: {summary.get('eligible_high_code_primary_read_case_count', 0)}",
      f"- 高代码主门禁读分子: {summary.get('sampled_high_code_primary_read_case_count', 0)}",
      f"- 高代码主门禁读待编排数: {summary.get('pending_high_code_primary_read_case_count', 0)}",
      f"- 高代码主门禁写覆盖率: {summary.get('high_code_primary_write_coverage_percent', 0)}%",
      f"- 高代码主门禁写分母: {summary.get('eligible_high_code_primary_write_case_count', 0)}",
      f"- 高代码主门禁写分子: {summary.get('sampled_high_code_primary_write_case_count', 0)}",
      f"- 高代码主门禁写待编排数: {summary.get('pending_high_code_primary_write_case_count', 0)}",
      f"- 高代码库存接口数: {summary.get('high_code_inventory_api_count', 0)}",
      f"- 高代码种子用例数: {summary.get('high_code_seed_case_count', 0)}",
      f"- 未编排库存接口数: {summary.get('unseeded_high_code_inventory_api_count', 0)}",
      f"- case coverage: {summary.get('case_coverage_percent', 0)}%",
      f"- case coverage denominator: {summary.get('case_coverage_denominator', 0)}",
      f"- case coverage numerator: {summary.get('case_coverage_numerator', 0)}",
      f"- pass_count: {summary.get('pass_count', 0)}",
      f"- fail_count: {summary.get('fail_count', 0)}",
      "",
  ]
  if summary.get("unseeded_high_code_inventory_examples"):
    lines.extend([
        "## 高代码库存漂移",
        "",
    ])
    for example in summary.get("unseeded_high_code_inventory_examples", []):
      lines.append(f"- {example}")
    lines.extend([
        "",
    ])

  if runtime_preflight:
    lines.extend([
        "## 运行时预检",
        "",
        "| target | base_url | status | details |",
        "| --- | --- | --- | --- |",
    ])
    for report in runtime_preflight:
      details = "; ".join(issue.get("message", "") for issue in report.get("issues", []))
      if not details:
        details = "; ".join(report.get("notes", [])) or "通过"
      lines.append(
          "| {target} | {base_url} | {status} | {details} |".format(
              target=report.get("target", ""),
              base_url=report.get("base_url", ""),
              status=report.get("status", ""),
              details=details,
          )
      )
    lines.extend([
        "",
    ])

  lines.extend([
      "## 失败项",
      "",
  ])
  if failures:
    for failure in failures:
      lines.append(
          "- {case_id} [{classification}] {message}".format(
              case_id=failure.get("case_id", ""),
              classification=failure.get("classification", ""),
              message=failure.get("message", ""),
          )
      )
  else:
    lines.append("- 无")

  lines.extend([
      "",
      "## 测试用例表",
      "",
  ])
  if executed_cases:
    lines.extend([
        "| case_id | endpoint | method | actual_result | classification | notes |",
        "| --- | --- | --- | --- | --- | --- |",
    ])
    for case in _sorted_records(executed_cases, ("case_id", "endpoint", "method")):
      lines.append(
          "| {case_id} | {endpoint} | {method} | {actual_result} | {classification} | {notes} |".format(
              case_id=case.get("case_id", ""),
              endpoint=case.get("endpoint", ""),
              method=case.get("method", ""),
              actual_result=case.get("actual_result", ""),
              classification=case.get("classification", ""),
              notes=case.get("notes", ""),
          )
      )
  else:
    lines.append("- 无")

  lines.extend([
      "",
      "## 合并结论",
      "",
      summary.get("merge_readiness", "暂不具备合并条件"),
      "",
  ])
  return "\n".join(lines)

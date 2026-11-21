import importlib
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[6]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))


class ReportingTest(unittest.TestCase):

  def test_inventory_markdown_renders_summary_and_table(self):
    reporting = importlib.import_module("testing.common.runners.api.data_api_regression.reporting")

    markdown = reporting.render_inventory_markdown(
        [
            {
                "source_kind": "metadata",
                "model_key": "buy.waybillOrder",
                "api_name": "search",
                "urls": ["/api/buy/waybill-order"],
                "query_param_form": "companyId[EQ/custom/scalar]",
                "risk": "P0",
            }
        ],
        {"metadata": 1, "high_code": 0, "total": 1},
    )

    self.assertIn("元数据与高代码数据 API 清单", markdown)
    self.assertIn("buy.waybillOrder", markdown)
    self.assertIn("/api/buy/waybill-order", markdown)
    self.assertIn("companyId[EQ/custom/scalar]", markdown)

  def test_case_outputs_render_markdown_and_csv(self):
    reporting = importlib.import_module("testing.common.runners.api.data_api_regression.reporting")
    cases = [
        {
            "case_id": "buy.waybillOrder.search.query-conditions",
            "module": "buy.waybillOrder",
            "endpoint": "/api/buy/waybill-order",
            "method": "GET",
            "scenario": "query-conditions",
            "query_param_form": "companyId[EQ/custom/scalar]; lineDate.BETWEEN[BETWEEN/input-date-range/date-range]",
            "sample_query": {
                "companyId": "__COMPANY_ID__",
                "lineDate.BETWEEN": ["2024-01-01", "2024-12-31"],
            },
            "payload_form": "",
            "sample_payload": {},
            "risk": "P0",
            "regression_bucket": "primary",
            "case_note": "",
            "actual_result": "PASS",
            "notes": "",
        }
    ]

    markdown = reporting.render_case_markdown(cases, 1, 1)
    self.assertIn("业务模型用例矩阵", markdown)
    self.assertIn("case coverage denominator", markdown)
    self.assertIn("primary gate coverage denominator", markdown)
    self.assertIn("buy.waybillOrder.search.query-conditions", markdown)
    self.assertIn("companyId[EQ/custom/scalar]", markdown)
    self.assertIn("{\"companyId\":\"__COMPANY_ID__\"", markdown)

    write_case_markdown = reporting.render_case_markdown(
        [
            {
                "case_id": "baseInfo.simpleModel.add.add",
                "module": "baseInfo.simpleModel",
                "endpoint": "/api/base-info/simple-model",
                "method": "POST",
                "scenario": "add",
                "query_param_form": "",
                "sample_query": {},
                "payload_form": "id[String]; code[单行文本]",
                "sample_payload": {
                    "id": "sample-id",
                    "code": "sample-text",
                },
                "risk": "P1",
                "regression_bucket": "primary",
                "case_note": "",
                "actual_result": "PASS",
                "notes": "",
            }
        ],
        1,
        1,
    )
    self.assertIn("payload_form", write_case_markdown)
    self.assertIn("stage_one_write_reason", write_case_markdown)
    self.assertIn("{\"id\":\"sample-id\"", write_case_markdown)

    with tempfile.TemporaryDirectory() as temp_dir:
      csv_path = Path(temp_dir) / "cases.csv"
      reporting.write_case_csv(cases, csv_path)
      csv_text = csv_path.read_text()
      self.assertIn("source_kind,case_id,module,endpoint,method,scenario,query_param_form,sample_query,payload_form,sample_payload,risk,regression_bucket,stage_one_write_executable,stage_one_write_reason,case_note,actual_result,notes", csv_text)
      self.assertIn("buy.waybillOrder.search.query-conditions", csv_text)
      self.assertIn("companyId[EQ/custom/scalar]", csv_text)

  def test_case_markdown_separates_primary_and_special_high_code_cases(self):
    reporting = importlib.import_module("testing.common.runners.api.data_api_regression.reporting")

    markdown = reporting.render_case_markdown(
        [
            {
                "source_kind": "high-code-seed",
                "case_id": "data.dict.write.items-create",
                "module": "Data Dictionary",
                "endpoint": "/api/sys/data-dict-manage/{name}/items",
                "method": "POST",
                "scenario": "items-create",
                "query_param_form": "",
                "sample_query": {},
                "payload_form": "",
                "sample_payload": {},
                "risk": "P1",
                "regression_bucket": "primary",
                "case_note": "authority-backed primary path",
                "actual_result": "PASS",
                "notes": "",
            },
            {
                "source_kind": "high-code-seed",
                "case_id": "notification.template.write.create",
                "module": "Notification",
                "endpoint": "/api/sys/notification-template",
                "method": "POST",
                "scenario": "create",
                "query_param_form": "",
                "sample_query": {},
                "payload_form": "",
                "sample_payload": {},
                "risk": "P1",
                "regression_bucket": "develop-scope",
                "case_note": "develop-side management surface",
                "actual_result": "",
                "notes": "",
            },
        ],
        2,
        1,
    )

    self.assertIn("高代码主门禁用例", markdown)
    self.assertIn("高代码专项用例", markdown)
    self.assertIn("data.dict.write.items-create", markdown)
    self.assertIn("notification.template.write.create", markdown)
    self.assertIn("develop-side management surface", markdown)

  def test_summary_report_contains_coverage_failures_and_merge_gate(self):
    reporting = importlib.import_module("testing.common.runners.api.data_api_regression.reporting")

    markdown = reporting.render_summary_report({
        "coverage_percent": 92.3,
        "business_model_coverage_percent": 92.3,
        "eligible_business_model_count": 13,
        "sampled_business_model_count": 12,
        "metadata_read_coverage_percent": 92.3,
        "eligible_metadata_read_model_count": 13,
        "sampled_metadata_read_model_count": 12,
        "metadata_detail_coverage_percent": 84.62,
        "eligible_metadata_detail_model_count": 13,
        "sampled_metadata_detail_model_count": 11,
        "metadata_write_coverage_percent": 90.0,
        "eligible_metadata_write_model_count": 10,
        "sampled_metadata_write_model_count": 9,
        "complex_query_model_coverage_percent": 80.0,
        "eligible_complex_query_model_count": 10,
        "sampled_complex_query_model_count": 8,
        "high_code_primary_read_coverage_percent": 100.0,
        "eligible_high_code_primary_read_case_count": 6,
        "sampled_high_code_primary_read_case_count": 6,
        "pending_high_code_primary_read_case_count": 1,
        "high_code_primary_write_coverage_percent": 0,
        "eligible_high_code_primary_write_case_count": 0,
        "sampled_high_code_primary_write_case_count": 0,
        "pending_high_code_primary_write_case_count": 4,
        "high_code_inventory_api_count": 191,
        "high_code_seed_case_count": 22,
        "unseeded_high_code_inventory_api_count": 176,
        "unseeded_high_code_inventory_examples": [
            "POST /api/sys/role",
        ],
        "case_coverage_percent": 24.05,
        "case_coverage_denominator": 1206,
        "case_coverage_numerator": 290,
        "pass_count": 11,
        "fail_count": 1,
        "runtime_preflight": [
            {
                "target": "default",
                "base_url": "http://127.0.0.1:28888",
                "status": "fail",
                "issues": [
                    {
                        "message": "运行中进程仍持有已丢失的类路径 jar，延迟加载 SPI/资源时会随机失效: /tmp/runtime/app/metadata/extend/document-management-infra-support.jar"
                    }
                ],
                "notes": [],
            }
        ],
        "executed_cases": [
            {
                "case_id": "organization.nodes.tree.children",
                "endpoint": "/api/organization/nodes/{id}/children",
                "method": "GET",
                "actual_result": "PASS",
                "classification": "pass",
                "notes": "resolved via root node sampling"
            }
        ],
        "failures": [
            {
                "case_id": "organization.nodes.tree.children",
                "classification": "environment-gap",
                "message": "missing root node"
            }
        ],
        "merge_readiness": "暂不具备合并条件",
    })

    self.assertIn("业务模型抽检覆盖率", markdown)
    self.assertIn("92.3%", markdown)
    self.assertIn("元数据读覆盖率", markdown)
    self.assertIn("元数据详情覆盖率", markdown)
    self.assertIn("元数据写覆盖率", markdown)
    self.assertIn("复杂查询模型覆盖率", markdown)
    self.assertIn("高代码主门禁读覆盖率", markdown)
    self.assertIn("高代码主门禁写覆盖率", markdown)
    self.assertIn("高代码库存漂移", markdown)
    self.assertIn("未编排库存接口数", markdown)
    self.assertIn("运行时预检", markdown)
    self.assertIn("document-management-infra-support.jar", markdown)
    self.assertIn("case coverage", markdown)
    self.assertIn("organization.nodes.tree.children", markdown)
    self.assertIn("测试用例表", markdown)
    self.assertIn("/api/organization/nodes/{id}/children", markdown)
    self.assertIn("resolved via root node sampling", markdown)
    self.assertIn("暂不具备合并条件", markdown)


if __name__ == "__main__":
  unittest.main()

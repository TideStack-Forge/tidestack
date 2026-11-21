import importlib
import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[6]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))

SEED_FILE = ROOT / "testing/common/runners/api/data_api_regression/high_code_api_seed.json"


class CaseMatrixTest(unittest.TestCase):

  def test_metadata_inventory_expands_into_multiple_scenarios(self):
    case_matrix = importlib.import_module("testing.common.runners.api.data_api_regression.case_matrix")

    inventory_records = [
        {
            "source_kind": "metadata",
            "model_key": "buy.waybillOrder",
            "model_title": "采购运单",
            "api_name": "search",
            "urls": ["/api/buy/waybill-order"],
            "schema_ids": ["/buy/waybill-order/detail"],
            "has_before_script": True,
            "has_after_script": False,
            "has_detail": True,
            "populate_count": 2,
            "query_filter_count": 2,
            "query_param_form": "companyId[EQ/custom/scalar]; lineDate.BETWEEN[BETWEEN/input-date-range/date-range]",
            "sample_query": {
                "companyId": "__COMPANY_ID__",
                "lineDate.BETWEEN": ["2024-01-01", "2024-12-31"],
            },
            "reserved_route_conflict": False,
            "reserved_route_reason": "",
        },
        {
            "source_kind": "metadata",
            "model_key": "buy.waybillOrder",
            "model_title": "采购运单",
            "api_name": "add",
            "urls": ["/api/buy/waybill-order"],
            "schema_ids": [],
            "has_before_script": False,
            "has_after_script": False,
            "has_detail": True,
            "populate_count": 0,
            "query_filter_count": 0,
            "query_param_form": "",
            "sample_query": {},
            "payload_form": "name[单行文本]",
            "sample_payload": {"name": "sample-text"},
            "sample_update_payload": {"name": "sample-text-updated"},
            "write_primary_key_field": "id",
            "runtime_write_dependencies": [
                {
                    "field_name": "supplierNo",
                    "dependency_kind": "reference",
                    "model_key": "baseInfo.customerInfo",
                    "reference_key": "customerNo",
                    "selector_type": "select",
                }
            ],
            "stage_one_write_executable": True,
            "stage_one_write_reason": "",
            "reserved_route_conflict": False,
            "reserved_route_reason": "",
        },
    ]

    cases = case_matrix.build_case_matrix(inventory_records, [])

    case_ids = {case["case_id"] for case in cases}
    self.assertIn("buy.waybillOrder.search.list", case_ids)
    self.assertIn("buy.waybillOrder.search.query-conditions", case_ids)
    self.assertIn("buy.waybillOrder.add.master-detail-sync", case_ids)
    list_case = next(case for case in cases if case["case_id"] == "buy.waybillOrder.search.list")
    search_case = next(case for case in cases if case["case_id"] == "buy.waybillOrder.search.query-conditions")
    self.assertEqual("", list_case["query_param_form"])
    self.assertEqual({}, list_case["sample_query"])
    self.assertEqual(
        "companyId[EQ/custom/scalar]; lineDate.BETWEEN[BETWEEN/input-date-range/date-range]",
        search_case["query_param_form"],
    )
    self.assertEqual("__COMPANY_ID__", search_case["sample_query"]["companyId"])
    add_case = next(case for case in cases if case["case_id"] == "buy.waybillOrder.add.add")
    master_detail_case = next(case for case in cases if case["case_id"] == "buy.waybillOrder.add.master-detail-sync")
    self.assertEqual({"name": "sample-text"}, add_case["sample_payload"])
    self.assertEqual(
        [
            {
                "field_name": "supplierNo",
                "dependency_kind": "reference",
                "model_key": "baseInfo.customerInfo",
                "reference_key": "customerNo",
                "selector_type": "select",
            }
        ],
        add_case["runtime_write_dependencies"],
    )
    self.assertTrue(add_case["stage_one_write_executable"])
    self.assertFalse(master_detail_case["stage_one_write_executable"])
    self.assertIn("stage-two", master_detail_case["stage_one_write_reason"])

  def test_risk_tiers_and_coverage_denominator_are_scenario_based(self):
    case_matrix = importlib.import_module("testing.common.runners.api.data_api_regression.case_matrix")

    cases = case_matrix.build_case_matrix(
        [
            {
                "source_kind": "metadata",
                "model_key": "buy.waybillOrder",
                "model_title": "采购运单",
                "api_name": "search",
                "urls": ["/api/buy/waybill-order"],
                "schema_ids": [],
                "has_before_script": True,
                "has_after_script": False,
                "has_detail": False,
                "populate_count": 1,
                "query_filter_count": 1,
                "query_param_form": "companyId[EQ/custom/scalar]",
                "sample_query": {"companyId": "__COMPANY_ID__"},
                "reserved_route_conflict": False,
                "reserved_route_reason": "",
            }
        ],
        [],
    )

    risk_by_case = {case["case_id"]: case["risk"] for case in cases}
    self.assertEqual("P0", risk_by_case["buy.waybillOrder.search.list"])
    self.assertEqual("P0", risk_by_case["buy.waybillOrder.search.query-conditions"])
    self.assertEqual(2, case_matrix.compute_coverage_denominator(cases, ("P0", "P1")))

  def test_query_filters_alone_are_enough_to_generate_query_condition_case(self):
    case_matrix = importlib.import_module("testing.common.runners.api.data_api_regression.case_matrix")

    cases = case_matrix.build_case_matrix(
        [
            {
                "source_kind": "metadata",
                "model_key": "baseInfo.areaInfo",
                "model_title": "区域信息",
                "api_name": "search",
                "urls": ["/api/base-info/area-info"],
                "schema_ids": [],
                "has_before_script": False,
                "has_after_script": False,
                "has_detail": False,
                "populate_count": 0,
                "query_filter_count": 1,
                "query_param_form": "areaNm.CONTAINS[CONTAINS/input-text/scalar]",
                "sample_query": {"areaNm.CONTAINS": "sample-text"},
                "reserved_route_conflict": False,
                "reserved_route_reason": "",
            }
        ],
        [],
    )

    case_ids = {case["case_id"] for case in cases}
    self.assertIn("baseInfo.areaInfo.search.query-conditions", case_ids)

  def test_model_sampling_coverage_uses_distinct_metadata_modules(self):
    case_matrix = importlib.import_module("testing.common.runners.api.data_api_regression.case_matrix")

    cases = [
        {
            "case_id": "buy.waybillOrder.search.list",
            "source_kind": "metadata",
            "module": "buy.waybillOrder",
            "scenario": "list",
            "risk": "P0",
        },
        {
            "case_id": "buy.waybillOrder.search.query-conditions",
            "source_kind": "metadata",
            "module": "buy.waybillOrder",
            "scenario": "query-conditions",
            "risk": "P0",
        },
        {
            "case_id": "check.rawCheck.search.list",
            "source_kind": "metadata",
            "module": "check.rawCheck",
            "scenario": "list",
            "risk": "P1",
        },
        {
            "case_id": "sale.waybillOrder.search.list",
            "source_kind": "metadata",
            "module": "sale.waybillOrder",
            "scenario": "list",
            "risk": "P1",
        },
        {
            "case_id": "sys.user.list.search",
            "source_kind": "high-code-seed",
            "module": "sys.user",
            "scenario": "search",
            "risk": "P0",
        },
    ]
    executed_cases = [
        {"case_id": "buy.waybillOrder.search.list"},
        {"case_id": "buy.waybillOrder.search.query-conditions"},
        {"case_id": "check.rawCheck.search.list"},
    ]

    metrics = case_matrix.compute_model_sampling_coverage(cases, executed_cases, ("list", "query-conditions"))

    self.assertEqual(3, metrics["eligible_model_count"])
    self.assertEqual(2, metrics["sampled_model_count"])
    self.assertEqual(66.67, metrics["coverage_percent"])

    query_metrics = case_matrix.compute_model_sampling_coverage(cases, executed_cases, ("query-conditions",))

    self.assertEqual(1, query_metrics["eligible_model_count"])
    self.assertEqual(1, query_metrics["sampled_model_count"])
    self.assertEqual(100.0, query_metrics["coverage_percent"])

  def test_reserved_route_conflict_flag_flows_into_cases(self):
    case_matrix = importlib.import_module("testing.common.runners.api.data_api_regression.case_matrix")

    cases = case_matrix.build_case_matrix(
        [
            {
                "source_kind": "metadata",
                "model_key": "app.waybillBaseInfo",
                "model_title": "运单基本信息",
                "api_name": "search",
                "urls": ["/api/app/waybill-base-info"],
                "schema_ids": [],
                "has_before_script": False,
                "has_after_script": False,
                "has_detail": False,
                "populate_count": 0,
                "query_filter_count": 0,
                "query_param_form": "",
                "sample_query": {},
                "reserved_route_conflict": True,
                "reserved_route_reason": "namespace 'app' maps to /api/app/*, which is reserved by AppManagementController dev endpoints",
            }
        ],
        [],
    )

    self.assertTrue(cases[0]["reserved_route_conflict"])
    self.assertIn("AppManagementController", cases[0]["reserved_route_reason"])

  def test_high_code_seed_cases_merge_with_discovered_inventory(self):
    case_matrix = importlib.import_module("testing.common.runners.api.data_api_regression.case_matrix")

    seed_cases = json.loads(SEED_FILE.read_text())
    cases = case_matrix.build_case_matrix([], seed_cases)

    case_ids = {case["case_id"] for case in cases}
    expected_case_ids = {
        "sys.user.list.search",
        "sys.user.write.create",
        "sys.role.list.search",
        "organization.company.list.search",
        "organization.company.write.create",
        "organization.nodes.tree.children",
        "organization.tree.read.list",
        "document.library.read.accessible",
        "document.library.write.create",
        "document.library.write.soft-delete-before-hard-delete",
        "data.dict.read.manage",
        "data.dict.write.items-create",
        "configuration.read.list",
        "configuration.write.roundtrip",
        "user.setting.read.list",
        "user.setting.write.patch",
        "notification.template.read",
        "notification.template.write.create",
        "notification.channel.employee.bind.info",
        "business.log.template.read.list",
        "business.log.template.write.create",
        "business.log.read.list",
    }
    for expected_case_id in expected_case_ids:
      self.assertIn(expected_case_id, case_ids)

    data_dict_write = next(case for case in cases if case["case_id"] == "data.dict.write.items-create")
    notification_write = next(case for case in cases if case["case_id"] == "notification.template.write.create")
    notification_bind = next(case for case in cases if case["case_id"] == "notification.channel.employee.bind.info")
    self.assertEqual("primary", data_dict_write["regression_bucket"])
    self.assertEqual("develop-scope", notification_write["regression_bucket"])
    self.assertEqual("contextual-user", notification_bind["regression_bucket"])
    self.assertIn("/items", data_dict_write["endpoint"])

  def test_primary_high_code_write_seed_cases_preserve_executable_payload_metadata(self):
    case_matrix = importlib.import_module("testing.common.runners.api.data_api_regression.case_matrix")

    seed_cases = json.loads(SEED_FILE.read_text())
    cases = case_matrix.build_case_matrix([], seed_cases)
    by_case_id = {case["case_id"]: case for case in cases}

    executable_cases = [
        "sys.user.write.create",
        "organization.company.write.create",
        "document.library.write.create",
        "configuration.write.roundtrip",
        "user.setting.write.patch",
        "business.log.template.write.create",
    ]
    for case_id in executable_cases:
      self.assertTrue(by_case_id[case_id]["stage_one_write_executable"], case_id)
      self.assertTrue(by_case_id[case_id]["sample_payload"] is not None, case_id)

    self.assertEqual(
        "/api/sys/user/${userIdentity}",
        by_case_id["sys.user.write.create"]["cleanup_endpoint"],
    )
    self.assertEqual(
        "DELETE",
        by_case_id["document.library.write.create"]["cleanup_method"],
    )


if __name__ == "__main__":
  unittest.main()

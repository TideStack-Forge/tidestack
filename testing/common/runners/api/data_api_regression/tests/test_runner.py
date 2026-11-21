import importlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock


ROOT = Path(__file__).resolve().parents[6]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))


class RunnerTest(unittest.TestCase):

  def test_metadata_list_ring_only_selects_plain_get_list_cases(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    cases = [
        {
            "case_id": "buy.waybillOrder.search.list",
            "source_kind": "metadata",
            "endpoint": "/api/buy/waybill-order",
            "method": "GET",
            "scenario": "list",
        },
        {
            "case_id": "buy.waybillOrder.search.query-conditions",
            "source_kind": "metadata",
            "endpoint": "/api/buy/waybill-order",
            "method": "GET",
            "scenario": "query-conditions",
            "sample_query": {"companyId": "__COMPANY_ID__"},
        },
        {
            "case_id": "buy.waybillOrder.getOne.detail",
            "source_kind": "metadata",
            "endpoint": "/api/buy/waybill-order/${id}",
            "method": "GET",
            "scenario": "detail",
        },
        {
            "case_id": "sys.user.list.search",
            "source_kind": "high-code-seed",
            "endpoint": "/api/sys/user",
            "method": "GET",
            "scenario": "search",
        },
    ]

    selected = runner.select_execution_cases(cases, "metadata-list")

    self.assertEqual(["buy.waybillOrder.search.list"], [case["case_id"] for case in selected])

  def test_metadata_query_conditions_ring_selects_filter_driven_cases(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    cases = [
        {
            "case_id": "buy.waybillOrder.search.query-conditions",
            "source_kind": "metadata",
            "endpoint": "/api/buy/waybill-order",
            "method": "GET",
            "scenario": "query-conditions",
            "sample_query": {"companyId": "__COMPANY_ID__"},
        },
        {
            "case_id": "buy.waybillOrder.search.list",
            "source_kind": "metadata",
            "endpoint": "/api/buy/waybill-order",
            "method": "GET",
            "scenario": "list",
            "sample_query": {},
        },
    ]

    selected = runner.select_execution_cases(cases, "metadata-query-conditions")

    self.assertEqual(["buy.waybillOrder.search.query-conditions"], [case["case_id"] for case in selected])

  def test_metadata_detail_ring_selects_detail_cases(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    cases = [
        {
            "case_id": "buy.waybillOrder.getOne.detail",
            "source_kind": "metadata",
            "endpoint": "/api/buy/waybill-order/${id}",
            "method": "GET",
            "scenario": "detail",
        },
        {
            "case_id": "buy.waybillOrder.search.list",
            "source_kind": "metadata",
            "endpoint": "/api/buy/waybill-order",
            "method": "GET",
            "scenario": "list",
        },
    ]

    selected = runner.select_execution_cases(cases, "metadata-detail")

    self.assertEqual(["buy.waybillOrder.getOne.detail"], [case["case_id"] for case in selected])

  def test_metadata_write_rings_only_select_stage_one_executable_cases(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    cases = [
        {
            "case_id": "baseInfo.simpleModel.add.add",
            "source_kind": "metadata",
            "endpoint": "/api/base-info/simple-model",
            "method": "POST",
            "scenario": "add",
            "stage_one_write_executable": True,
            "sample_payload": {"id": "sample-id"},
        },
        {
            "case_id": "baseInfo.simpleModel.update.update",
            "source_kind": "metadata",
            "endpoint": "/api/base-info/simple-model/${id}",
            "method": "PATCH",
            "scenario": "update",
            "stage_one_write_executable": True,
            "sample_payload": {"id": "sample-id"},
            "sample_update_payload": {"name": "sample-text-updated"},
        },
        {
            "case_id": "baseInfo.simpleModel.delete.delete",
            "source_kind": "metadata",
            "endpoint": "/api/base-info/simple-model/${id}",
            "method": "DELETE",
            "scenario": "delete",
            "stage_one_write_executable": True,
            "sample_payload": {"id": "sample-id"},
        },
        {
            "case_id": "buy.waybillOrder.add.master-detail-sync",
            "source_kind": "metadata",
            "endpoint": "/api/buy/waybill-order",
            "method": "POST",
            "scenario": "master-detail-sync",
            "stage_one_write_executable": False,
        },
    ]

    self.assertEqual(
        ["baseInfo.simpleModel.add.add"],
        [case["case_id"] for case in runner.select_execution_cases(cases, "metadata-create")],
    )
    self.assertEqual(
        ["baseInfo.simpleModel.update.update"],
        [case["case_id"] for case in runner.select_execution_cases(cases, "metadata-update")],
    )
    self.assertEqual(
        ["baseInfo.simpleModel.delete.delete"],
        [case["case_id"] for case in runner.select_execution_cases(cases, "metadata-delete")],
    )

  def test_high_code_read_rings_select_plain_seed_get_cases_by_bucket(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    cases = [
        {
            "case_id": "sys.user.list.search",
            "source_kind": "high-code-seed",
            "endpoint": "/api/sys/user",
            "method": "GET",
            "scenario": "search",
            "regression_bucket": "primary",
        },
        {
            "case_id": "organization.nodes.tree.children",
            "source_kind": "high-code-seed",
            "endpoint": "/api/organization/nodes/{id}/children",
            "method": "GET",
            "scenario": "tree-children",
            "regression_bucket": "primary",
        },
        {
            "case_id": "notification.template.read",
            "source_kind": "high-code-seed",
            "endpoint": "/api/sys/notification-template",
            "method": "GET",
            "scenario": "search",
            "regression_bucket": "develop-scope",
        },
        {
            "case_id": "notification.channel.employee.bind.info",
            "source_kind": "high-code-seed",
            "endpoint": "/api/notification/internal-message/employee/bind/info",
            "method": "GET",
            "scenario": "employee-bind-info",
            "regression_bucket": "contextual-user",
        },
    ]

    self.assertEqual(
        ["sys.user.list.search"],
        [case["case_id"] for case in runner.select_execution_cases(cases, "high-code-read")],
    )
    self.assertEqual(
        [
            "notification.channel.employee.bind.info",
            "notification.template.read",
        ],
        sorted(case["case_id"] for case in runner.select_execution_cases(cases, "high-code-special-read")),
    )

  def test_high_code_write_rings_only_select_executable_seed_cases_by_bucket(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    cases = [
        {
            "case_id": "sys.user.write.create",
            "source_kind": "high-code-seed",
            "endpoint": "/api/sys/user",
            "method": "POST",
            "scenario": "create",
            "regression_bucket": "primary",
            "stage_one_write_executable": True,
            "sample_payload": {"userIdentity": "sample-user"},
        },
        {
            "case_id": "configuration.write.roundtrip",
            "source_kind": "high-code-seed",
            "endpoint": "/api/sys/configuration",
            "method": "PUT",
            "scenario": "roundtrip",
            "regression_bucket": "primary",
            "stage_one_write_executable": False,
            "sample_payload": {},
        },
        {
            "case_id": "notification.template.write.create",
            "source_kind": "high-code-seed",
            "endpoint": "/api/sys/notification-template",
            "method": "POST",
            "scenario": "create",
            "regression_bucket": "develop-scope",
            "stage_one_write_executable": True,
            "sample_payload": {"name": "sample-template"},
        },
        {
            "case_id": "data.dict.write.items-create",
            "source_kind": "high-code-seed",
            "endpoint": "/api/sys/data-dict-manage/{name}/items",
            "method": "POST",
            "scenario": "items-create",
            "regression_bucket": "primary",
            "stage_one_write_executable": True,
            "sample_payload": {"items": [{"code": "A"}]},
        },
    ]

    self.assertEqual(
        ["sys.user.write.create"],
        [case["case_id"] for case in runner.select_execution_cases(cases, "high-code-write")],
    )
    self.assertEqual(
        ["notification.template.write.create"],
        [case["case_id"] for case in runner.select_execution_cases(cases, "high-code-special-write")],
    )

  def test_high_code_query_does_not_force_metadata_paging_defaults(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    self.assertEqual(
        {},
        runner._build_case_query(
            {
                "source_kind": "high-code-seed",
                "sample_query": {},
            },
            company_id="company-1",
        ),
    )
    self.assertEqual(
        {"page": 1, "perPage": 1, "companyId": "company-1"},
        runner._build_case_query(
            {
                "source_kind": "metadata",
                "scenario": "list",
            },
            company_id="company-1",
        ),
    )

  def test_classify_execution_result_distinguishes_outcome_types(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    self.assertEqual(
        {"actual_result": "PASS", "classification": "pass"},
        runner.classify_execution_result(200, 0),
    )
    self.assertEqual(
        {"actual_result": "FAIL", "classification": "permission-boundary"},
        runner.classify_execution_result(403, -1),
    )
    self.assertEqual(
        {"actual_result": "FAIL", "classification": "environment-gap"},
        runner.classify_execution_result(200, -4000),
    )
    self.assertEqual(
        {"actual_result": "PASS", "classification": "pass"},
        runner.classify_execution_result(201, 0),
    )
    self.assertEqual(
        {"actual_result": "PASS", "classification": "pass"},
        runner.classify_execution_result(204, None),
    )

  def test_classify_execution_result_allows_json_success_without_business_code(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    self.assertEqual(
        {"actual_result": "PASS", "classification": "pass"},
        runner.classify_execution_result(200, None),
    )
    self.assertEqual(
        {"actual_result": "PASS", "classification": "pass"},
        runner.classify_execution_result(201, None),
    )

  def test_classify_execution_result_rejects_non_json_success_bodies(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    self.assertEqual(
        {"actual_result": "FAIL", "classification": "unexpected-response-format"},
        runner.classify_execution_result(200, None, "Expecting value: line 1 column 1 (char 0)"),
    )
    self.assertEqual(
        {"actual_result": "FAIL", "classification": "unexpected-response-format"},
        runner.classify_execution_result(201, None, "Expecting value: line 1 column 1 (char 0)"),
    )

  def test_classify_case_execution_recognizes_reserved_route_conflict(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    self.assertEqual(
        {"actual_result": "FAIL", "classification": "reserved-route-conflict"},
        runner.classify_case_execution(
            {
                "reserved_route_conflict": True,
            },
            405,
            -1,
            {"msg": "Request method 'GET' not supported"},
        ),
    )

  def test_merge_execution_results_updates_case_rows_and_summary(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    cases = [
        {
            "case_id": "buy.waybillOrder.search.list",
            "module": "buy.waybillOrder",
            "endpoint": "/api/buy/waybill-order",
            "method": "GET",
            "scenario": "list",
            "risk": "P0",
            "actual_result": "",
            "notes": "",
        },
        {
            "case_id": "buy.waybillOrder.search.query-conditions",
            "module": "buy.waybillOrder",
            "endpoint": "/api/buy/waybill-order",
            "method": "GET",
            "scenario": "query-conditions",
            "risk": "P0",
            "actual_result": "",
            "notes": "",
        },
        {
            "case_id": "check.rawCheck.search.list",
            "module": "check.rawCheck",
            "endpoint": "/api/check/raw-check",
            "method": "GET",
            "scenario": "list",
            "risk": "P0",
            "actual_result": "",
            "notes": "",
        },
        {
            "case_id": "sale.waybillOrder.search.list",
            "module": "sale.waybillOrder",
            "endpoint": "/api/sale/waybill-order",
            "method": "GET",
            "scenario": "list",
            "risk": "P0",
            "actual_result": "",
            "notes": "",
        },
    ]

    executed_cases = [
        {
            "case_id": "buy.waybillOrder.search.list",
            "actual_result": "PASS",
            "classification": "pass",
            "notes": "http=200 code=0",
        },
        {
            "case_id": "check.rawCheck.search.list",
            "actual_result": "FAIL",
            "classification": "environment-gap",
            "notes": "missing companyId",
        },
    ]

    merged_cases, summary = runner.merge_execution_results(cases, executed_cases)

    self.assertEqual("PASS", merged_cases[0]["actual_result"])
    self.assertEqual("", merged_cases[1]["actual_result"])
    self.assertEqual("FAIL", merged_cases[2]["actual_result"])
    self.assertEqual("http=200 code=0", merged_cases[0]["notes"])
    self.assertEqual(4, summary["case_coverage_denominator"])
    self.assertEqual(2, summary["case_coverage_numerator"])
    self.assertEqual(50.0, summary["case_coverage_percent"])
    self.assertEqual(3, summary["eligible_business_model_count"])
    self.assertEqual(2, summary["sampled_business_model_count"])
    self.assertEqual(66.67, summary["business_model_coverage_percent"])
    self.assertEqual(66.67, summary["coverage_percent"])
    self.assertEqual(1, summary["pass_count"])
    self.assertEqual(1, summary["fail_count"])
    self.assertEqual("environment-gap", summary["failures"][0]["classification"])

  def test_merge_execution_results_filters_stale_executed_cases_outside_current_matrix(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    cases = [
        {
            "case_id": "buy.waybillOrder.search.list",
            "module": "buy.waybillOrder",
            "endpoint": "/api/buy/waybill-order",
            "method": "GET",
            "scenario": "list",
            "risk": "P0",
            "actual_result": "",
            "notes": "",
        }
    ]

    executed_cases = [
        {
            "case_id": "runtime.preflight.default",
            "endpoint": "http://127.0.0.1:18888",
            "method": "PRECHECK",
            "actual_result": "FAIL",
            "classification": "runtime-baseline-invalid",
            "notes": "stale preflight failure",
        },
        {
            "case_id": "buy.waybillOrder.search.list",
            "endpoint": "/api/buy/waybill-order",
            "method": "GET",
            "actual_result": "PASS",
            "classification": "pass",
            "notes": "http=200 code=0",
        },
    ]

    _merged_cases, summary = runner.merge_execution_results(cases, executed_cases)

    self.assertEqual(0, summary["fail_count"])
    self.assertEqual(1, len(summary["executed_cases"]))
    self.assertEqual("buy.waybillOrder.search.list", summary["executed_cases"][0]["case_id"])

  def test_request_json_keeps_non_json_http_error_body_instead_of_crashing(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with mock.patch.object(
        runner.request,
        "urlopen",
        side_effect=runner.error.HTTPError(
            "http://127.0.0.1:18888/api/demo",
            502,
            "Bad Gateway",
            hdrs=None,
            fp=io.BytesIO(b"<html>bad gateway</html>"),
        ),
    ):
      response = runner._request_json("http://127.0.0.1:18888/api/demo")

    self.assertEqual(502, response["http_status"])
    self.assertIsNone(response["payload"])
    self.assertIn("<html>bad gateway</html>", response["raw"])
    self.assertTrue(response["payload_parse_error"])

  def test_request_json_returns_transport_error_for_connection_failures(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with mock.patch.object(
        runner.request,
        "urlopen",
        side_effect=runner.error.URLError("Connection refused"),
    ):
      response = runner._request_json("http://127.0.0.1:18888/api/demo")

    self.assertIsNone(response["http_status"])
    self.assertIsNone(response["payload"])
    self.assertEqual("url-error", response["transport_error_kind"])
    self.assertIn("Connection refused", response["raw"])

  def test_load_token_uses_fixture_supplied_login_payload(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with mock.patch.object(
        runner,
        "_request_json",
        return_value={
            "http_status": 200,
            "payload": {
                "data": {
                    "accessToken": {
                        "token": "token-1",
                    }
                }
            },
        },
    ) as mock_request:
      token = runner._load_token(
          SimpleNamespace(
              login_payload={
                  "account": "fixture-admin",
                  "password": "fixture-password",
              }
          ),
          "http://127.0.0.1:18888",
      )

    self.assertEqual("token-1", token)
    self.assertEqual(
        {
            "account": "fixture-admin",
            "password": "fixture-password",
        },
        mock_request.call_args.kwargs["body"],
    )

  def test_load_token_raises_precondition_gap_when_login_returns_business_error(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with mock.patch.object(
        runner,
        "_request_json",
        return_value={
            "http_status": 200,
            "payload": {
                "code": -1,
                "msg": "用户名或密码错误",
            },
            "raw": "{\"code\":-1,\"msg\":\"用户名或密码错误\"}",
            "payload_parse_error": "",
        },
    ):
      with self.assertRaises(runner.RuntimeDependencyResolutionError) as captured:
        runner._load_token(SimpleNamespace(login_payload=None), "http://127.0.0.1:18888")

    self.assertEqual("runtime-auth-invalid", captured.exception.classification)
    self.assertEqual(200, captured.exception.http_status)
    self.assertEqual(-1, captured.exception.business_code)
    self.assertIn("用户名或密码错误", str(captured.exception))

  def test_load_token_raises_precondition_gap_when_login_response_is_not_json(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with mock.patch.object(
        runner,
        "_request_json",
        return_value={
            "http_status": 200,
            "payload": None,
            "raw": "<html>login page</html>",
            "payload_parse_error": "Expecting value: line 1 column 1 (char 0)",
        },
    ):
      with self.assertRaises(runner.RuntimeDependencyResolutionError) as captured:
        runner._load_token(SimpleNamespace(login_payload=None), "http://127.0.0.1:18888")

    self.assertEqual("unexpected-response-format", captured.exception.classification)
    self.assertEqual(200, captured.exception.http_status)
    self.assertIn("not-json", str(captured.exception))

  def test_load_token_raises_precondition_gap_when_token_file_payload_shape_is_invalid(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      token_file = Path(temp_dir) / "token.json"
      token_file.write_text(json.dumps({"data": {}}))

      with self.assertRaises(runner.RuntimeDependencyResolutionError) as captured:
        runner._load_token(SimpleNamespace(login_payload=None), None, str(token_file))

    self.assertEqual("execution-precondition-gap", captured.exception.classification)
    self.assertEqual("token_file", captured.exception.field_name)
    self.assertIn("missing token", str(captured.exception))

  def test_validate_authenticated_authority_surface_rejects_runtime_without_matching_case_apis(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    selected_cases = [
        {
            "case_id": "baseInfo.weightPhoto.search.list",
            "source_kind": "metadata",
            "module": "baseInfo.weightPhoto",
            "endpoint": "/api/base-info/weight-photo",
            "method": "GET",
            "scenario": "list",
        }
    ]

    with mock.patch.object(
        runner,
        "_request_json",
        return_value={
            "http_status": 200,
            "payload": {
                "code": 0,
                "data": {
                    "dev.platform": {
                        "authorityObject": {
                            "apis": [
                                "GET /api/dev/**",
                                "GET /api/sys/user/**",
                            ]
                        }
                    }
                },
            },
            "raw": "{\"code\":0}",
            "payload_parse_error": "",
            "transport_error_kind": "",
        },
    ):
      with self.assertRaises(runner.RuntimeDependencyResolutionError) as captured:
        runner._validate_authenticated_authority_surface(
            "http://127.0.0.1:18888",
            "token",
            selected_cases,
            "default",
        )

    self.assertEqual("authority_surface", captured.exception.field_name)
    self.assertEqual(runner.RUNTIME_BASELINE_INVALID, captured.exception.classification)
    self.assertIn("baseInfo.weightPhoto.search.list", str(captured.exception))

  def test_case_authority_candidates_cover_metadata_and_high_code_shapes(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    metadata_candidates = runner._case_authority_candidates(
        {
            "case_id": "baseInfo.weightPhoto.search.list",
            "module": "baseInfo.weightPhoto",
            "method": "GET",
            "scenario": "list",
        }
    )
    high_code_candidates = runner._case_authority_candidates(
        {
            "case_id": "sys.user.write.create",
            "method": "POST",
            "scenario": "create",
        }
    )

    self.assertIn("baseInfo.weightPhoto", metadata_candidates)
    self.assertIn("baseInfo.weightPhoto.search", metadata_candidates)
    self.assertIn("sys.user", high_code_candidates)
    self.assertIn("sys.user.add", high_code_candidates)

  def test_validate_authenticated_authority_surface_accepts_matching_case_authority_name(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    selected_cases = [
        {
            "case_id": "baseInfo.weightPhoto.search.list",
            "source_kind": "metadata",
            "module": "baseInfo.weightPhoto",
            "endpoint": "/api/base-info/weight-photo",
            "method": "GET",
            "scenario": "list",
        }
    ]

    with mock.patch.object(
        runner,
        "_request_json",
        return_value={
            "http_status": 200,
            "payload": {
                "code": 0,
                "data": {
                    "baseInfo.weightPhoto.search": {
                        "authority": "baseInfo.weightPhoto.search",
                        "accessControlParams": {},
                    }
                },
            },
            "raw": "{\"code\":0}",
            "payload_parse_error": "",
            "transport_error_kind": "",
        },
    ):
      covered = runner._validate_authenticated_authority_surface(
          "http://127.0.0.1:18888",
          "token",
          selected_cases,
          "default",
      )

    self.assertEqual(["baseInfo.weightPhoto.search.list"], covered)

  def test_validate_authenticated_authority_surface_accepts_matching_case_api(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    selected_cases = [
        {
            "case_id": "baseInfo.weightPhoto.search.list",
            "source_kind": "metadata",
            "module": "baseInfo.weightPhoto",
            "endpoint": "/api/base-info/weight-photo",
            "method": "GET",
            "scenario": "list",
        }
    ]

    with mock.patch.object(
        runner,
        "_request_json",
        return_value={
            "http_status": 200,
            "payload": {
                "code": 0,
                "data": {
                    "baseInfo.weightPhoto.search": {
                        "authorityObject": {
                            "apis": [
                                "GET /api/base-info/weight-photo",
                            ]
                        }
                    }
                },
            },
            "raw": "{\"code\":0}",
            "payload_parse_error": "",
            "transport_error_kind": "",
        },
    ):
      runner._validate_authenticated_authority_surface(
          "http://127.0.0.1:18888",
          "token",
          selected_cases,
          "default",
      )

  def test_build_case_query_resolves_placeholder_and_preserves_lists(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    query = runner._build_case_query(
        {
            "scenario": "query-conditions",
            "sample_query": {
                "companyId": "__COMPANY_ID__",
                "lineDate.BETWEEN": ["2024-01-01", "2024-12-31"],
                "customerId": ["sample-1", "sample-2"],
            }
        },
        "2008020679764475904",
    )

    self.assertEqual("2008020679764475904", query["companyId"])
    self.assertEqual(["2024-01-01", "2024-12-31"], query["lineDate.BETWEEN"])
    self.assertEqual(["sample-1", "sample-2"], query["customerId"])

  def test_build_case_query_keeps_list_ring_on_plain_pagination(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    query = runner._build_case_query(
        {
            "scenario": "list",
            "sample_query": {
                "companyId": "__COMPANY_ID__",
                "keyword.CONTAINS": "sample-text",
            }
        },
        "2008020679764475904",
    )

    self.assertEqual({"page": 1, "perPage": 1, "companyId": "2008020679764475904"}, query)

  def test_execute_metadata_case_stops_early_when_company_context_is_missing(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with mock.patch.object(runner, "_request_json") as mock_request:
      executed = runner._execute_metadata_case(
          {
              "case_id": "buy.waybillOrder.search.query-conditions",
              "module": "buy.waybillOrder",
              "endpoint": "/api/buy/waybill-order",
              "method": "GET",
              "scenario": "query-conditions",
              "sample_query": {
                  "companyId": "__COMPANY_ID__",
                  "keyword.CONTAINS": "sample-text",
              },
          },
          "http://127.0.0.1:18888",
          "develop-token",
          None,
      )

    mock_request.assert_not_called()
    self.assertEqual("FAIL", executed["actual_result"])
    self.assertEqual("execution-precondition-gap", executed["classification"])
    self.assertIn("companyId", executed["notes"])

  def test_execute_metadata_write_case_stops_early_when_company_context_is_missing(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with mock.patch.object(runner, "_request_json") as mock_request:
      executed = runner._execute_metadata_write_case(
          {
              "case_id": "baseInfo.simpleModel.add.add",
              "module": "baseInfo.simpleModel",
              "endpoint": "/api/base-info/simple-model",
              "method": "POST",
              "scenario": "add",
              "sample_payload": {
                  "id": "sample-id",
                  "companyId": "__COMPANY_ID__",
                  "code": "sample-text",
              },
              "write_primary_key_field": "id",
              "runtime_write_dependencies": [],
          },
          "http://127.0.0.1:18888",
          "develop-token",
          None,
      )

    mock_request.assert_not_called()
    self.assertEqual("FAIL", executed["actual_result"])
    self.assertEqual("execution-precondition-gap", executed["classification"])
    self.assertIn("companyId", executed["notes"])

  def test_materialize_sample_payload_generates_unique_strings_and_preserves_context_values(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    payload = runner._materialize_sample_payload(
        {
            "module": "baseInfo.simpleModel",
            "write_primary_key_field": "id",
            "sample_payload": {
                "id": "sample-id",
                "companyId": "__COMPANY_ID__",
                "code": "sample-text",
                "lineDate": "2024-01-01",
                "qty": 1,
            },
        },
        "2008020679764475904",
        "123456",
    )

    self.assertEqual("2008020679764475904", payload["companyId"])
    self.assertEqual("2024-01-01", payload["lineDate"])
    self.assertEqual(1, payload["qty"])
    self.assertIn("123456", payload["id"])
    self.assertIn("code", payload["code"])
    self.assertIn("123456", payload["code"])

  def test_materialize_sample_payload_keeps_generated_primary_key_within_coding_length_budget(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    payload = runner._materialize_sample_payload(
        {
            "module": "store.outOtherOrder",
            "write_primary_key_field": "id",
            "sample_payload": {
                "id": "sample-id",
            },
        },
        "2008020679764475904",
        "1773620896036",
    )

    self.assertLessEqual(len(payload["id"]), 20)
    self.assertTrue(payload["id"].endswith("1773620896036"))

  def test_resolve_runtime_write_dependencies_fetches_reference_and_data_dict_values(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    case = {
        "runtime_write_dependencies": [
            {
                "field_name": "supplierNo",
                "dependency_kind": "reference",
                "model_key": "baseInfo.customerInfo",
                "reference_key": "customerNo",
                "selector_type": "select",
            },
            {
                "field_name": "orderType",
                "dependency_kind": "data-dict",
                "dict_name": "dictOrderType",
                "selector_type": "select",
            },
        ]
    }

    with mock.patch.object(
        runner,
        "_request_json",
        side_effect=[
            {
                "http_status": 200,
                "payload": {
                    "code": 0,
                    "data": {
                        "items": [
                            {
                                "customerNo": "CUST-001",
                            }
                        ]
                    },
                },
                "raw": "{}",
            },
            {
                "http_status": 200,
                "payload": {
                    "code": 0,
                    "data": {
                        "items": [
                            {
                                "code": "BUY",
                                "label": "采购",
                            }
                        ]
                    },
                },
                "raw": "{}",
            },
        ],
    ) as mock_request:
      resolved, notes = runner._resolve_runtime_write_dependencies(
          case,
          "http://127.0.0.1:18889",
          "token",
          "2008020679764475904",
      )

    self.assertEqual(
        {
            "supplierNo": "CUST-001",
            "orderType": "BUY",
        },
        resolved,
    )
    self.assertIn("supplierNo=CUST-001", notes[0])
    self.assertIn("orderType=BUY", notes[1])
    self.assertIn("/api/base-info/customer-info", mock_request.call_args_list[0].args[0])
    self.assertIn("companyId=2008020679764475904", mock_request.call_args_list[0].args[0])
    self.assertIn("/api/sys/data-dict/dictOrderType", mock_request.call_args_list[1].args[0])

  def test_execute_metadata_write_case_merges_runtime_dependency_values_into_create_payload(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    case = {
        "case_id": "baseInfo.runtimeSelect.add.add",
        "module": "baseInfo.runtimeSelect",
        "endpoint": "/api/base-info/runtime-select",
        "method": "POST",
        "scenario": "add",
        "write_primary_key_field": "id",
        "sample_payload": {
            "id": "sample-id",
            "remark": "sample-text",
        },
        "runtime_write_dependencies": [
            {
                "field_name": "supplierNo",
            }
        ],
    }

    with mock.patch.object(
        runner,
        "_resolve_runtime_write_dependencies",
        return_value=(
            {
                "supplierNo": "CUST-001",
            },
            [
                "runtime.supplierNo=CUST-001",
            ],
        ),
    ), mock.patch.object(
        runner,
        "_request_json",
        side_effect=[
            {
                "http_status": 200,
                "payload": {
                    "code": 0,
                    "data": {
                        "id": "new-id",
                    },
                },
                "raw": "{}",
            },
            {
                "http_status": 204,
                "payload": {
                    "code": 0,
                },
                "raw": "",
            },
        ],
    ) as mock_request:
      result = runner._execute_metadata_write_case(
          case,
          "http://127.0.0.1:18889",
          "token",
          "2008020679764475904",
      )

    create_payload = mock_request.call_args_list[0].kwargs["body"]
    self.assertEqual("CUST-001", create_payload["supplierNo"])
    self.assertIn("runtime.supplierNo=CUST-001", result["notes"])

  def test_resolve_case_target_routes_app_namespace_to_mothership(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    self.assertEqual(
        ("http://127.0.0.1:18889", "mothership"),
        runner._resolve_case_target(
            {"module": "app.portal"},
            "http://127.0.0.1:18888",
            "http://127.0.0.1:18889",
        ),
    )
    self.assertEqual(
        ("http://127.0.0.1:18888", "default"),
        runner._resolve_case_target(
            {"module": "baseInfo.weightPhoto"},
            "http://127.0.0.1:18888",
            "http://127.0.0.1:18889",
        ),
    )

  def test_execute_metadata_case_uses_mothership_token_and_skips_reserved_route_conflict_classification(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    captured = {}

    def fake_request_json(url, method="GET", token=None, body=None):
      captured["url"] = url
      captured["method"] = method
      captured["token"] = token
      captured["body"] = body
      return {
          "http_status": 405,
          "payload": {"code": -1, "msg": "Request method 'GET' not supported"},
          "raw": "{\"code\":-1,\"msg\":\"Request method 'GET' not supported\"}",
      }

    with mock.patch.object(runner, "_request_json", side_effect=fake_request_json):
      executed = runner._execute_metadata_case(
          {
              "case_id": "app.portal.search.list",
              "module": "app.portal",
              "endpoint": "/api/app/portal",
              "method": "GET",
              "scenario": "list",
              "reserved_route_conflict": True,
              "reserved_route_reason": "reserved",
          },
          "http://127.0.0.1:18888",
          "develop-token",
          "2008020679764475904",
          "http://127.0.0.1:18889",
          "mothership-token",
      )

    self.assertEqual("http://127.0.0.1:18889/api/app/portal?page=1&perPage=1&companyId=2008020679764475904", captured["url"])
    self.assertEqual("mothership-token", captured["token"])
    self.assertEqual("FAIL", executed["actual_result"])
    self.assertEqual("regression", executed["classification"])
    self.assertIn("target=mothership", executed["notes"])
    self.assertNotIn("reserved-route=", executed["notes"])

  def test_execute_metadata_case_rejects_non_json_success_response(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with mock.patch.object(
        runner,
        "_request_json",
        return_value={
            "http_status": 200,
            "payload": None,
            "raw": "<html>proxy fallback</html>",
            "payload_parse_error": "Expecting value: line 1 column 1 (char 0)",
            "transport_error_kind": "",
        },
    ):
      executed = runner._execute_metadata_case(
          {
              "case_id": "buy.waybillOrder.search.list",
              "module": "buy.waybillOrder",
              "endpoint": "/api/buy/waybill-order",
              "method": "GET",
              "scenario": "list",
          },
          "http://127.0.0.1:18888",
          "develop-token",
          "2008020679764475904",
      )

    self.assertEqual("FAIL", executed["actual_result"])
    self.assertEqual("unexpected-response-format", executed["classification"])

  def test_execute_metadata_detail_case_reads_list_then_detail(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    calls = []

    def fake_request_json(url, method="GET", token=None, body=None):
      calls.append({
          "url": url,
          "method": method,
          "token": token,
          "body": body,
      })
      if len(calls) == 1:
        return {
            "http_status": 200,
            "payload": {
                "code": 0,
                "data": {
                    "items": [
                        {
                            "id": "detail-1",
                        }
                    ]
                },
            },
            "raw": "{\"code\":0}",
        }
      return {
          "http_status": 200,
          "payload": {
              "code": 0,
              "data": {
                  "id": "detail-1",
              },
          },
          "raw": "{\"code\":0}",
      }

    with mock.patch.object(runner, "_request_json", side_effect=fake_request_json):
      executed = runner._execute_metadata_detail_case(
          {
              "case_id": "buy.waybillOrder.getOne.detail",
              "module": "buy.waybillOrder",
              "endpoint": "/api/buy/waybill-order/${id}",
              "method": "GET",
              "scenario": "detail",
              "detail_primary_key_field": "id",
          },
          "http://127.0.0.1:18888",
          "develop-token",
          "2008020679764475904",
      )

    self.assertEqual(["GET", "GET"], [call["method"] for call in calls])
    self.assertEqual(
        "http://127.0.0.1:18888/api/buy/waybill-order?page=1&perPage=1&companyId=2008020679764475904",
        calls[0]["url"],
    )
    self.assertEqual(
        "http://127.0.0.1:18888/api/buy/waybill-order/detail-1?companyId=2008020679764475904",
        calls[1]["url"],
    )
    self.assertEqual("PASS", executed["actual_result"])
    self.assertEqual("pass", executed["classification"])
    self.assertIn("detail.id=detail-1", executed["notes"])

  def test_execute_metadata_write_case_creates_then_patches_then_cleans_up(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    calls = []

    def fake_request_json(url, method="GET", token=None, body=None):
      calls.append({
          "url": url,
          "method": method,
          "token": token,
          "body": body,
      })
      if method == "POST":
        return {
            "http_status": 200,
            "payload": {"code": 0, "data": {"id": "created-1"}},
            "raw": "{\"code\":0,\"data\":{\"id\":\"created-1\"}}",
        }
      if method == "PATCH":
        return {
            "http_status": 200,
            "payload": {"code": 0, "data": {"id": "created-1", "name": "patched"}},
            "raw": "{\"code\":0}",
        }
      if method == "DELETE":
        return {
            "http_status": 200,
            "payload": {"code": 0, "data": {"id": "created-1"}},
            "raw": "{\"code\":0}",
        }
      raise AssertionError("unexpected method")

    with mock.patch.object(runner, "_request_json", side_effect=fake_request_json):
      executed = runner._execute_metadata_write_case(
          {
              "case_id": "baseInfo.simpleModel.update.update",
              "module": "baseInfo.simpleModel",
              "endpoint": "/api/base-info/simple-model/${id}",
              "method": "PATCH",
              "scenario": "update",
              "sample_payload": {"id": "sample-id", "code": "sample-text"},
              "sample_update_payload": {"name": "sample-text-updated"},
              "write_primary_key_field": "id",
          },
          "http://127.0.0.1:18888",
          "develop-token",
          "2008020679764475904",
      )

    self.assertEqual(["POST", "PATCH", "DELETE"], [call["method"] for call in calls])
    self.assertEqual(
        "http://127.0.0.1:18888/api/base-info/simple-model?companyId=2008020679764475904",
        calls[0]["url"],
    )
    self.assertEqual(
        "http://127.0.0.1:18888/api/base-info/simple-model/created-1?companyId=2008020679764475904",
        calls[1]["url"],
    )
    self.assertEqual(
        "http://127.0.0.1:18888/api/base-info/simple-model/created-1?companyId=2008020679764475904",
        calls[2]["url"],
    )
    self.assertEqual("develop-token", calls[0]["token"])
    self.assertEqual("PATCH", executed["method"])
    self.assertEqual("PASS", executed["actual_result"])
    self.assertEqual("pass", executed["classification"])
    self.assertIn("cleanup=PASS", executed["notes"])

  def test_execute_high_code_seed_case_materializes_unique_payload_and_cleans_up(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    calls = []

    def fake_request_json(url, method="GET", token=None, body=None):
      calls.append({
          "url": url,
          "method": method,
          "token": token,
          "body": body,
      })
      if method == "POST":
        return {
            "http_status": 201,
            "payload": {
                "code": 0,
                "data": {
                    "userIdentity": body["userIdentity"],
                },
            },
            "raw": "{\"code\":0}",
        }
      if method == "DELETE":
        return {
            "http_status": 204,
            "payload": {
                "code": 0,
            },
            "raw": "",
        }
      raise AssertionError("unexpected method")

    with mock.patch.object(runner, "_request_json", side_effect=fake_request_json), mock.patch.object(
        runner,
        "_unique_token",
        return_value="123456",
    ):
      executed = runner._execute_high_code_seed_case(
          {
              "case_id": "sys.user.write.create",
              "module": "RBAC",
              "endpoint": "/api/sys/user",
              "method": "POST",
              "scenario": "create",
              "sample_payload": {
                  "userIdentity": "sample-id",
                  "password": "sample-text",
              },
              "cleanup_method": "DELETE",
              "cleanup_endpoint": "/api/sys/user/${userIdentity}",
          },
          "http://127.0.0.1:18888",
          "develop-token",
          None,
      )

    self.assertEqual(["POST", "DELETE"], [call["method"] for call in calls])
    self.assertEqual("develop-token", calls[0]["token"])
    self.assertIn("123456", calls[0]["body"]["userIdentity"])
    self.assertIn("123456", calls[0]["body"]["password"])
    self.assertEqual(
        f"http://127.0.0.1:18888/api/sys/user/{calls[0]['body']['userIdentity']}",
        calls[1]["url"],
    )
    self.assertEqual("PASS", executed["actual_result"])
    self.assertEqual("pass", executed["classification"])
    self.assertIn("cleanup=PASS", executed["notes"])

  def test_results_placeholder_keeps_business_model_denominator_without_execution(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

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
            "case_id": "sale.waybillOrder.search.list",
            "source_kind": "metadata",
            "module": "sale.waybillOrder",
            "scenario": "list",
            "risk": "P1",
        },
    ]

    with tempfile.TemporaryDirectory() as temp_dir:
      summary = runner._write_results_placeholder(temp_dir, {"metadata": 2}, cases)
      results = json.loads((Path(temp_dir) / "results.json").read_text())

    self.assertEqual(2, summary["eligible_business_model_count"])
    self.assertEqual(2, summary["eligible_metadata_read_model_count"])
    self.assertEqual(0, summary["eligible_metadata_write_model_count"])
    self.assertEqual(1, summary["eligible_complex_query_model_count"])
    self.assertEqual(0, summary["sampled_business_model_count"])
    self.assertEqual(0, summary["high_code_primary_read_coverage_percent"])
    self.assertEqual(2, results["coverage_denominator"])
    self.assertEqual(1, results["eligible_complex_query_model_count"])

  def test_merge_execution_results_computes_segmented_gate_metrics(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    cases = [
        {
            "case_id": "buy.waybillOrder.search.list",
            "source_kind": "metadata",
            "module": "buy.waybillOrder",
            "endpoint": "/api/buy/waybill-order",
            "method": "GET",
            "scenario": "list",
            "risk": "P0",
            "actual_result": "",
            "notes": "",
        },
        {
            "case_id": "buy.waybillOrder.getOne.detail",
            "source_kind": "metadata",
            "module": "buy.waybillOrder",
            "endpoint": "/api/buy/waybill-order/${id}",
            "method": "GET",
            "scenario": "detail",
            "risk": "P1",
            "actual_result": "",
            "notes": "",
        },
        {
            "case_id": "buy.waybillOrder.add.add",
            "source_kind": "metadata",
            "module": "buy.waybillOrder",
            "endpoint": "/api/buy/waybill-order",
            "method": "POST",
            "scenario": "add",
            "risk": "P1",
            "stage_one_write_executable": True,
            "actual_result": "",
            "notes": "",
        },
        {
            "case_id": "sys.user.list.search",
            "source_kind": "high-code-seed",
            "module": "RBAC",
            "endpoint": "/api/sys/user",
            "method": "GET",
            "scenario": "search",
            "risk": "P1",
            "regression_bucket": "primary",
            "actual_result": "",
            "notes": "",
        },
        {
            "case_id": "organization.nodes.tree.children",
            "source_kind": "high-code-seed",
            "module": "Organization",
            "endpoint": "/api/organization/nodes/{id}/children",
            "method": "GET",
            "scenario": "tree-children",
            "risk": "P0",
            "regression_bucket": "primary",
            "actual_result": "",
            "notes": "",
        },
        {
            "case_id": "sys.user.write.create",
            "source_kind": "high-code-seed",
            "module": "RBAC",
            "endpoint": "/api/sys/user",
            "method": "POST",
            "scenario": "create",
            "risk": "P1",
            "regression_bucket": "primary",
            "stage_one_write_executable": False,
            "actual_result": "",
            "notes": "",
        },
    ]

    executed_cases = [
        {
            "case_id": "buy.waybillOrder.search.list",
            "actual_result": "PASS",
            "classification": "pass",
            "notes": "http=200 code=0",
        },
        {
            "case_id": "buy.waybillOrder.getOne.detail",
            "actual_result": "PASS",
            "classification": "pass",
            "notes": "detail.id=detail-1",
        },
        {
            "case_id": "buy.waybillOrder.add.add",
            "actual_result": "PASS",
            "classification": "pass",
            "notes": "create.http=200 create.code=0",
        },
        {
            "case_id": "sys.user.list.search",
            "actual_result": "PASS",
            "classification": "pass",
            "notes": "http=200 code=0",
        },
    ]

    _merged_cases, summary = runner.merge_execution_results(cases, executed_cases)

    self.assertEqual(100.0, summary["metadata_read_coverage_percent"])
    self.assertEqual(100.0, summary["metadata_detail_coverage_percent"])
    self.assertEqual(100.0, summary["metadata_write_coverage_percent"])
    self.assertEqual(100.0, summary["high_code_primary_read_coverage_percent"])
    self.assertEqual(0, summary["eligible_high_code_primary_write_case_count"])
    self.assertEqual(1, summary["pending_high_code_primary_read_case_count"])
    self.assertEqual(1, summary["pending_high_code_primary_write_case_count"])

  def test_extract_runtime_command_paths_parses_jar_and_loader_dirs(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    jar_path, loader_paths = runner._extract_runtime_command_paths(
        "/usr/bin/java "
        "-Dloader.path=file:/tmp/runtime/app/metadata/extend,file:/tmp/runtime/app/base/service/ouroboros-lib "
        "-jar /tmp/runtime/app/base/service/ouroboros-mothership.jar"
    )

    self.assertEqual(
        Path("/tmp/runtime/app/base/service/ouroboros-mothership.jar"),
        jar_path,
    )
    self.assertEqual(
        [
            Path("/tmp/runtime/app/metadata/extend"),
            Path("/tmp/runtime/app/base/service/ouroboros-lib"),
        ],
        loader_paths,
    )

  def test_collect_runtime_integrity_issues_flags_missing_open_jar(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      runtime_root = Path(temp_dir) / "runtime"
      jar_path = runtime_root / "app/base/service/ouroboros-mothership.jar"
      loader_path = runtime_root / "app/metadata/extend"
      jar_path.parent.mkdir(parents=True, exist_ok=True)
      loader_path.mkdir(parents=True, exist_ok=True)
      jar_path.write_text("jar")

      missing_spi_jar = str(loader_path / "document-management-infra-support-1.0.0-beta.2.jar")
      issues = runner._collect_runtime_integrity_issues(
          jar_path,
          [loader_path],
          [missing_spi_jar],
      )

    self.assertEqual("missing-open-jar", issues[0]["kind"])
    self.assertIn("document-management-infra-support", issues[0]["message"])

  def test_preflight_local_runtime_requires_unauthenticated_http_probe(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      jar_path = Path(temp_dir) / "runtime/ouroboros-mothership.jar"
      jar_path.parent.mkdir(parents=True, exist_ok=True)
      jar_path.write_text("jar")
      probe_urls = []

      def fake_request_json(url, method="GET", token=None, body=None):
        probe_urls.append(url)
        return {
            "http_status": 200,
            "payload": {"code": 0},
            "raw": "{\"code\":0}",
            "payload_parse_error": "",
        }

      with mock.patch.object(runner, "_request_json", side_effect=fake_request_json), mock.patch.object(
          runner,
          "_find_listening_pid",
          return_value="12345",
      ), mock.patch.object(
          runner,
          "_read_process_command",
          return_value=f"java -jar {jar_path}",
      ), mock.patch.object(
          runner,
          "_list_open_jar_paths",
          return_value=[str(jar_path)],
      ):
        report = runner._preflight_local_runtime("http://127.0.0.1:18888", "default")

    self.assertEqual("fail", report["status"])
    self.assertEqual("http-probe-unexpected-status", report["issues"][0]["kind"])
    self.assertEqual(
        "http://127.0.0.1:18888/api/auth/authorities",
        probe_urls[0],
    )

  def test_preflight_local_runtime_passes_after_401_probe_and_valid_artifacts(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      jar_path = Path(temp_dir) / "runtime/ouroboros-mothership.jar"
      jar_path.parent.mkdir(parents=True, exist_ok=True)
      jar_path.write_text("jar")

      with mock.patch.object(
          runner,
          "_request_json",
          return_value={
              "http_status": 401,
              "payload": {"code": -1, "msg": "无法访问（未认证）"},
              "raw": "{\"code\":-1,\"msg\":\"无法访问（未认证）\"}",
              "payload_parse_error": "",
          },
      ), mock.patch.object(
          runner,
          "_find_listening_pid",
          return_value="12345",
      ), mock.patch.object(
          runner,
          "_read_process_command",
          return_value=f"java -jar {jar_path}",
      ), mock.patch.object(
          runner,
          "_list_open_jar_paths",
          return_value=[str(jar_path)],
      ):
        report = runner._preflight_local_runtime("http://127.0.0.1:18888", "default")

    self.assertEqual("pass", report["status"])
    self.assertIn("http-probe=401", "; ".join(report["notes"]))

  def test_preflight_local_runtime_rejects_non_json_401_probe(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      jar_path = Path(temp_dir) / "runtime/ouroboros-mothership.jar"
      jar_path.parent.mkdir(parents=True, exist_ok=True)
      jar_path.write_text("jar")

      with mock.patch.object(
          runner,
          "_request_json",
          return_value={
              "http_status": 401,
              "payload": None,
              "raw": "<html>gateway login</html>",
              "payload_parse_error": "Expecting value: line 1 column 1 (char 0)",
              "transport_error_kind": "",
          },
      ), mock.patch.object(
          runner,
          "_find_listening_pid",
          return_value="12345",
      ), mock.patch.object(
          runner,
          "_read_process_command",
          return_value=f"java -jar {jar_path}",
      ), mock.patch.object(
          runner,
          "_list_open_jar_paths",
          return_value=[str(jar_path)],
      ):
        report = runner._preflight_local_runtime("http://127.0.0.1:18888", "default")

    self.assertEqual("fail", report["status"])
    self.assertEqual("http-probe-unexpected-response-format", report["issues"][0]["kind"])
    self.assertIn("401", report["issues"][0]["message"])
    self.assertIn("parse=", report["issues"][0]["message"])

  def test_preflight_local_runtime_retries_warmup_503_until_401(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      jar_path = Path(temp_dir) / "runtime/ouroboros-mothership.jar"
      jar_path.parent.mkdir(parents=True, exist_ok=True)
      jar_path.write_text("jar")

      with mock.patch.object(
          runner,
          "RUNTIME_HTTP_PROBE_WARMUP_RETRY_ATTEMPTS",
          2,
      ), mock.patch.object(
          runner,
          "_request_json",
          side_effect=[
              {
                  "http_status": 503,
                  "payload": None,
                  "raw": "",
                  "payload_parse_error": "",
                  "transport_error_kind": "",
              },
              {
                  "http_status": 503,
                  "payload": None,
                  "raw": "",
                  "payload_parse_error": "",
                  "transport_error_kind": "",
              },
              {
                  "http_status": 401,
                  "payload": {"code": -1, "msg": "无法访问（未认证）"},
                  "raw": "{\"code\":-1,\"msg\":\"无法访问（未认证）\"}",
                  "payload_parse_error": "",
                  "transport_error_kind": "",
              },
          ],
      ), mock.patch.object(
          runner.time,
          "sleep",
      ) as sleep_mock, mock.patch.object(
          runner,
          "_find_listening_pid",
          return_value="12345",
      ), mock.patch.object(
          runner,
          "_read_process_command",
          return_value=f"java -jar {jar_path}",
      ), mock.patch.object(
          runner,
          "_list_open_jar_paths",
          return_value=[str(jar_path)],
      ):
        report = runner._preflight_local_runtime("http://127.0.0.1:18888", "default")

    self.assertEqual("pass", report["status"])
    self.assertIn("http-probe=401", "; ".join(report["notes"]))
    self.assertIn("http-probe-warmup-retries=2", "; ".join(report["notes"]))
    self.assertEqual(2, sleep_mock.call_count)

  def test_preflight_local_runtime_fails_after_warmup_503_retry_budget_exhausted(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      jar_path = Path(temp_dir) / "runtime/ouroboros-mothership.jar"
      jar_path.parent.mkdir(parents=True, exist_ok=True)
      jar_path.write_text("jar")

      with mock.patch.object(
          runner,
          "RUNTIME_HTTP_PROBE_WARMUP_RETRY_ATTEMPTS",
          1,
      ), mock.patch.object(
          runner,
          "_request_json",
          side_effect=[
              {
                  "http_status": 503,
                  "payload": None,
                  "raw": "",
                  "payload_parse_error": "",
                  "transport_error_kind": "",
              },
              {
                  "http_status": 503,
                  "payload": None,
                  "raw": "",
                  "payload_parse_error": "",
                  "transport_error_kind": "",
              },
          ],
      ), mock.patch.object(
          runner.time,
          "sleep",
      ) as sleep_mock, mock.patch.object(
          runner,
          "_find_listening_pid",
          return_value="12345",
      ), mock.patch.object(
          runner,
          "_read_process_command",
          return_value=f"java -jar {jar_path}",
      ), mock.patch.object(
          runner,
          "_list_open_jar_paths",
          return_value=[str(jar_path)],
      ):
        report = runner._preflight_local_runtime("http://127.0.0.1:18888", "default")

    self.assertEqual("fail", report["status"])
    self.assertEqual("http-probe-unexpected-status", report["issues"][0]["kind"])
    self.assertIn("启动预热窗口", report["issues"][0]["message"])
    self.assertEqual(1, sleep_mock.call_count)

  def test_preflight_local_runtime_retries_local_transport_error_until_401(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      jar_path = Path(temp_dir) / "runtime/ouroboros-mothership.jar"
      jar_path.parent.mkdir(parents=True, exist_ok=True)
      jar_path.write_text("jar")

      with mock.patch.object(
          runner,
          "RUNTIME_HTTP_PROBE_WARMUP_RETRY_ATTEMPTS",
          2,
      ), mock.patch.object(
          runner,
          "_request_json",
          side_effect=[
              {
                  "http_status": None,
                  "payload": None,
                  "raw": "[Errno 61] Connection refused",
                  "payload_parse_error": "",
                  "transport_error_kind": "url-error",
              },
              {
                  "http_status": None,
                  "payload": None,
                  "raw": "[Errno 61] Connection refused",
                  "payload_parse_error": "",
                  "transport_error_kind": "url-error",
              },
              {
                  "http_status": 401,
                  "payload": {"code": -1, "msg": "无法访问（未认证）"},
                  "raw": "{\"code\":-1,\"msg\":\"无法访问（未认证）\"}",
                  "payload_parse_error": "",
                  "transport_error_kind": "",
              },
          ],
      ), mock.patch.object(
          runner.time,
          "sleep",
      ) as sleep_mock, mock.patch.object(
          runner,
          "_find_listening_pid",
          return_value="12345",
      ), mock.patch.object(
          runner,
          "_read_process_command",
          return_value=f"java -jar {jar_path}",
      ), mock.patch.object(
          runner,
          "_list_open_jar_paths",
          return_value=[str(jar_path)],
      ):
        report = runner._preflight_local_runtime("http://127.0.0.1:18888", "default")

    self.assertEqual("pass", report["status"])
    self.assertIn("http-probe-warmup-retries=2", "; ".join(report["notes"]))
    self.assertEqual(2, sleep_mock.call_count)

  def test_preflight_runtime_targets_only_checks_runtimes_hit_by_selected_cases(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    calls = []

    def fake_preflight_local_runtime(base_url, target):
      calls.append((base_url, target))
      return {
          "target": target,
          "base_url": base_url,
          "status": "pass",
          "issues": [],
          "notes": [],
      }

    with mock.patch.object(runner, "_preflight_local_runtime", side_effect=fake_preflight_local_runtime):
      reports, failures, executed_cases = runner._preflight_runtime_targets(
          "http://127.0.0.1:18888",
          "http://127.0.0.1:18889",
          selected_cases=[
              {
                  "case_id": "baseInfo.weightPhoto.search.list",
                  "module": "baseInfo.weightPhoto",
              }
          ],
      )

    self.assertEqual(
        [("http://127.0.0.1:18888", "default")],
        calls,
    )
    self.assertEqual(1, len(reports))
    self.assertEqual([], failures)
    self.assertEqual([], executed_cases)

  def test_preflight_runtime_targets_includes_mothership_when_selected_cases_need_it(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    calls = []

    def fake_preflight_local_runtime(base_url, target):
      calls.append((base_url, target))
      return {
          "target": target,
          "base_url": base_url,
          "status": "pass",
          "issues": [],
          "notes": [],
      }

    with mock.patch.object(runner, "_preflight_local_runtime", side_effect=fake_preflight_local_runtime):
      runner._preflight_runtime_targets(
          "http://127.0.0.1:18888",
          "http://127.0.0.1:18889",
          selected_cases=[
              {
                  "case_id": "app.portal.search.list",
                  "module": "app.portal",
              },
              {
                  "case_id": "baseInfo.weightPhoto.search.list",
                  "module": "baseInfo.weightPhoto",
              },
          ],
      )

    self.assertEqual(
        [
            ("http://127.0.0.1:18889", "mothership"),
            ("http://127.0.0.1:18888", "default"),
        ],
        calls,
    )

  def test_run_execute_aborts_when_runtime_preflight_fails(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      report_dir = Path(temp_dir) / "report"
      docs_report_dir = Path(temp_dir) / "docs"
      args = SimpleNamespace(
          base_url="http://127.0.0.1:28888",
          inventory_only=False,
          execute=True,
          report_dir=str(report_dir),
          docs_report_dir=str(docs_report_dir),
          app_root=temp_dir,
          company_id=None,
          execution_ring="high-code-read",
          token_file=None,
          app_base_url=None,
          app_token_file=None,
      )

      with mock.patch.object(
          runner,
          "_build_inventory",
          return_value=([], {"metadata": 0, "high_code": 0, "total": 0}),
      ), mock.patch.object(
          runner,
          "build_case_matrix",
          return_value=[],
      ), mock.patch.object(
          runner,
          "_write_inventory_and_case_artifacts",
      ), mock.patch.object(
          runner,
          "_preflight_runtime_targets",
          return_value=(
              [
                  {
                      "target": "default",
                      "base_url": "http://127.0.0.1:28888",
                      "status": "fail",
                      "issues": [
                          {
                              "message": "运行中进程仍持有已丢失的类路径 jar，延迟加载 SPI/资源时会随机失效: /tmp/runtime/app/metadata/extend/document-management-infra-support.jar",
                          }
                      ],
                      "notes": [],
                  }
              ],
              [
                  {
                      "case_id": "runtime.preflight.default",
                      "classification": runner.RUNTIME_BASELINE_INVALID,
                      "message": "运行中进程仍持有已丢失的类路径 jar，延迟加载 SPI/资源时会随机失效: /tmp/runtime/app/metadata/extend/document-management-infra-support.jar",
                  }
              ],
              [
                  {
                      "case_id": "runtime.preflight.default",
                      "endpoint": "http://127.0.0.1:28888",
                      "method": "PRECHECK",
                      "actual_result": "FAIL",
                      "classification": runner.RUNTIME_BASELINE_INVALID,
                      "notes": "运行中进程仍持有已丢失的类路径 jar，延迟加载 SPI/资源时会随机失效: /tmp/runtime/app/metadata/extend/document-management-infra-support.jar",
                  }
              ],
          ),
      ), mock.patch.object(
          runner,
          "_load_token",
      ) as mock_load_token:
        exit_code = runner.run(args)

      summary = json.loads((report_dir / "results.json").read_text())

    self.assertEqual(1, exit_code)
    mock_load_token.assert_not_called()
    self.assertEqual(runner.RUNTIME_BASELINE_INVALID, summary["failures"][0]["classification"])
    self.assertEqual("PRECHECK", summary["executed_cases"][0]["method"])
    self.assertIn("运行时基线无效", summary["merge_readiness"])

  def test_run_execute_aborts_with_structured_failure_when_default_token_load_fails(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      report_dir = Path(temp_dir) / "report"
      docs_report_dir = Path(temp_dir) / "docs"
      args = SimpleNamespace(
          base_url="http://127.0.0.1:28888",
          inventory_only=False,
          execute=True,
          report_dir=str(report_dir),
          docs_report_dir=str(docs_report_dir),
          app_root=temp_dir,
          company_id="company-1",
          execution_ring="metadata-list",
          token_file=None,
          app_base_url=None,
          app_token_file=None,
      )

      cases = [
          {
              "case_id": "baseInfo.weightPhoto.search.list",
              "source_kind": "metadata",
              "module": "baseInfo.weightPhoto",
              "endpoint": "/api/base-info/weight-photo",
              "method": "GET",
              "scenario": "list",
              "risk": "P1",
              "actual_result": "",
              "notes": "",
          }
      ]

      with mock.patch.object(
          runner,
          "_build_inventory",
          return_value=([], {"metadata": 1, "high_code": 0, "total": 1}),
      ), mock.patch.object(
          runner,
          "build_case_matrix",
          return_value=cases,
      ), mock.patch.object(
          runner,
          "_write_inventory_and_case_artifacts",
      ), mock.patch.object(
          runner,
          "_preflight_runtime_targets",
          return_value=([], [], []),
      ), mock.patch.object(
          runner,
          "_load_token",
          side_effect=runner.RuntimeDependencyResolutionError(
              "default_token",
              "runtime-auth-invalid",
              "login returned business error: 用户名或密码错误",
              http_status=200,
              business_code=-1,
          ),
      ) as mock_load_token, mock.patch.object(
          runner,
          "_validate_authenticated_authority_surface",
      ):
        exit_code = runner.run(args)

      summary = json.loads((report_dir / "results.json").read_text())

    self.assertEqual(1, exit_code)
    self.assertEqual(1, mock_load_token.call_count)
    self.assertEqual("runtime.token.default", summary["executed_cases"][0]["case_id"])
    self.assertEqual("runtime-auth-invalid", summary["failures"][0]["classification"])
    self.assertEqual(200, summary["failures"][0]["http_status"])
    self.assertEqual(-1, summary["failures"][0]["business_code"])
    self.assertIn("token 获取失败", summary["merge_readiness"])

  def test_run_execute_skips_mothership_token_when_selected_ring_never_hits_app_runtime(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      report_dir = Path(temp_dir) / "report"
      docs_report_dir = Path(temp_dir) / "docs"
      captured = {}
      args = SimpleNamespace(
          base_url="http://127.0.0.1:28888",
          inventory_only=False,
          execute=True,
          report_dir=str(report_dir),
          docs_report_dir=str(docs_report_dir),
          app_root=temp_dir,
          company_id="company-1",
          execution_ring="metadata-list",
          token_file=None,
          app_base_url="http://127.0.0.1:28889",
          app_token_file=None,
      )

      cases = [
          {
              "case_id": "baseInfo.weightPhoto.search.list",
              "source_kind": "metadata",
              "module": "baseInfo.weightPhoto",
              "endpoint": "/api/base-info/weight-photo",
              "method": "GET",
              "scenario": "list",
              "risk": "P1",
              "actual_result": "",
              "notes": "",
          }
      ]

      def fake_execute_metadata_case(case, base_url, token, company_id, app_base_url=None, app_token=None):
        captured["token"] = token
        captured["app_token"] = app_token
        return {
            "case_id": case["case_id"],
            "endpoint": case["endpoint"],
            "method": case["method"],
            "http_status": 200,
            "business_code": 0,
            "actual_result": "PASS",
            "classification": "pass",
            "notes": "http=200 code=0",
            "response_excerpt": "",
        }

      with mock.patch.object(
          runner,
          "_build_inventory",
          return_value=([], {"metadata": 1, "high_code": 0, "total": 1}),
      ), mock.patch.object(
          runner,
          "build_case_matrix",
          return_value=cases,
      ), mock.patch.object(
          runner,
          "_write_inventory_and_case_artifacts",
      ), mock.patch.object(
          runner,
          "_preflight_runtime_targets",
          return_value=([], [], []),
      ) as mock_preflight, mock.patch.object(
          runner,
          "_load_token",
          side_effect=["develop-token"],
      ) as mock_load_token, mock.patch.object(
          runner,
          "_validate_authenticated_authority_surface",
      ), mock.patch.object(
          runner,
          "_execute_metadata_case",
          side_effect=fake_execute_metadata_case,
      ):
        exit_code = runner.run(args)

    self.assertEqual(0, exit_code)
    self.assertEqual("develop-token", captured["token"])
    self.assertIsNone(captured["app_token"])
    self.assertEqual(1, mock_load_token.call_count)
    self.assertEqual("http://127.0.0.1:28888", mock_load_token.call_args_list[0].args[1])
    self.assertEqual(cases, mock_preflight.call_args.kwargs["selected_cases"])

  def test_run_execute_loads_only_mothership_token_for_app_only_ring(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      report_dir = Path(temp_dir) / "report"
      docs_report_dir = Path(temp_dir) / "docs"
      captured = {}
      args = SimpleNamespace(
          base_url="http://127.0.0.1:28888",
          inventory_only=False,
          execute=True,
          report_dir=str(report_dir),
          docs_report_dir=str(docs_report_dir),
          app_root=temp_dir,
          company_id="company-1",
          execution_ring="metadata-list",
          token_file=None,
          app_base_url="http://127.0.0.1:28889",
          app_token_file=None,
      )

      cases = [
          {
              "case_id": "app.portal.search.list",
              "source_kind": "metadata",
              "module": "app.portal",
              "endpoint": "/api/app/portal",
              "method": "GET",
              "scenario": "list",
              "risk": "P1",
              "actual_result": "",
              "notes": "",
          }
      ]

      def fake_execute_metadata_case(case, base_url, token, company_id, app_base_url=None, app_token=None):
        captured["token"] = token
        captured["app_token"] = app_token
        return {
            "case_id": case["case_id"],
            "endpoint": case["endpoint"],
            "method": case["method"],
            "http_status": 200,
            "business_code": 0,
            "actual_result": "PASS",
            "classification": "pass",
            "notes": "target=mothership; http=200 code=0",
            "response_excerpt": "",
        }

      with mock.patch.object(
          runner,
          "_build_inventory",
          return_value=([], {"metadata": 1, "high_code": 0, "total": 1}),
      ), mock.patch.object(
          runner,
          "build_case_matrix",
          return_value=cases,
      ), mock.patch.object(
          runner,
          "_write_inventory_and_case_artifacts",
      ), mock.patch.object(
          runner,
          "_preflight_runtime_targets",
          return_value=([], [], []),
      ) as mock_preflight, mock.patch.object(
          runner,
          "_load_token",
          side_effect=["mothership-token"],
      ) as mock_load_token, mock.patch.object(
          runner,
          "_validate_authenticated_authority_surface",
      ), mock.patch.object(
          runner,
          "_execute_metadata_case",
          side_effect=fake_execute_metadata_case,
      ):
        exit_code = runner.run(args)

    self.assertEqual(0, exit_code)
    self.assertIsNone(captured["token"])
    self.assertEqual("mothership-token", captured["app_token"])
    self.assertEqual(1, mock_load_token.call_count)
    self.assertEqual("http://127.0.0.1:28889", mock_load_token.call_args_list[0].args[1])
    self.assertEqual(cases, mock_preflight.call_args.kwargs["selected_cases"])

  def test_run_execute_aborts_when_authenticated_authority_surface_covers_no_selected_cases(self):
    runner = importlib.import_module("testing.common.runners.api.data_api_regression.run_regression")

    with tempfile.TemporaryDirectory() as temp_dir:
      report_dir = Path(temp_dir) / "report"
      docs_report_dir = Path(temp_dir) / "docs"
      args = SimpleNamespace(
          base_url="http://127.0.0.1:28888",
          inventory_only=False,
          execute=True,
          report_dir=str(report_dir),
          docs_report_dir=str(docs_report_dir),
          app_root=temp_dir,
          company_id="company-1",
          execution_ring="metadata-list",
          token_file=None,
          app_base_url=None,
          app_token_file=None,
      )

      cases = [
          {
              "case_id": "baseInfo.weightPhoto.search.list",
              "source_kind": "metadata",
              "module": "baseInfo.weightPhoto",
              "endpoint": "/api/base-info/weight-photo",
              "method": "GET",
              "scenario": "list",
              "risk": "P1",
              "actual_result": "",
              "notes": "",
          }
      ]

      with mock.patch.object(
          runner,
          "_build_inventory",
          return_value=([], {"metadata": 1, "high_code": 0, "total": 1}),
      ), mock.patch.object(
          runner,
          "build_case_matrix",
          return_value=cases,
      ), mock.patch.object(
          runner,
          "_write_inventory_and_case_artifacts",
      ), mock.patch.object(
          runner,
          "_preflight_runtime_targets",
          return_value=([], [], []),
      ), mock.patch.object(
          runner,
          "_load_token",
          return_value="develop-token",
      ) as mock_load_token, mock.patch.object(
          runner,
          "_validate_authenticated_authority_surface",
          side_effect=runner.RuntimeDependencyResolutionError(
              "authority_surface",
              runner.RUNTIME_BASELINE_INVALID,
              "authenticated authority surface on target default does not cover selected cases: baseInfo.weightPhoto.search.list",
              http_status=200,
              business_code=0,
          ),
      ):
        exit_code = runner.run(args)

      summary = json.loads((report_dir / "results.json").read_text())

    self.assertEqual(1, exit_code)
    self.assertEqual(1, mock_load_token.call_count)
    self.assertEqual("runtime.authorities.default", summary["executed_cases"][0]["case_id"])
    self.assertEqual(runner.RUNTIME_BASELINE_INVALID, summary["failures"][0]["classification"])
    self.assertIn("权限面无效", summary["merge_readiness"])


if __name__ == "__main__":
  unittest.main()

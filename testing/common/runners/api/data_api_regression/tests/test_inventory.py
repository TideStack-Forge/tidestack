import importlib
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[6]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))

FIXTURES = Path(__file__).resolve().parent / "fixtures"


class InventoryExtractionTest(unittest.TestCase):

  def test_metadata_inventory_merges_scripts_detail_populate_and_ui_urls(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    records = inventory.build_metadata_inventory(
        FIXTURES / "metadata_model_sample.json",
        [FIXTURES / "ui_schema_sample.json"],
    )

    self.assertEqual(["search", "getOne", "add"], [record["api_name"] for record in records])

    search_record = records[0]
    self.assertEqual("buy.waybillOrder", search_record["model_key"])
    self.assertTrue(search_record["has_before_script"])
    self.assertTrue(search_record["has_detail"])
    self.assertEqual(2, search_record["populate_count"])
    self.assertIn("/api/buy/waybill-order", search_record["urls"])
    self.assertIn("/buy/waybill-order/detail", search_record["schema_ids"])
    self.assertEqual(3, search_record["query_filter_count"])
    self.assertEqual(
        [
            "companyId",
            "lineDate.BETWEEN",
            "customerId",
        ],
        [query_filter["param_name"] for query_filter in search_record["query_filters"]],
    )
    self.assertEqual("__COMPANY_ID__", search_record["sample_query"]["companyId"])
    self.assertEqual(["2024-01-01", "2024-12-31"], search_record["sample_query"]["lineDate.BETWEEN"])
    self.assertEqual(["sample-1", "sample-2"], search_record["sample_query"]["customerId"])
    self.assertIn("companyId[EQ/custom/scalar]", search_record["query_param_form"])
    self.assertIn("lineDate.BETWEEN[BETWEEN/input-date-range/date-range]", search_record["query_param_form"])

  def test_metadata_inventory_still_works_when_ui_schema_is_not_persisted(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    records = inventory.build_metadata_inventory(FIXTURES / "metadata_model_sample.json")

    self.assertEqual(["search", "getOne", "add"], [record["api_name"] for record in records])
    search_record = records[0]
    self.assertEqual("buy.waybillOrder", search_record["model_key"])
    self.assertIn("/api/buy/waybill-order", search_record["urls"])
    self.assertEqual([], search_record["schema_ids"])
    self.assertTrue(search_record["has_before_script"])
    self.assertTrue(search_record["has_detail"])
    self.assertEqual(3, search_record["query_filter_count"])
    self.assertEqual("views.index.defaultFilters", search_record["query_filters"][0]["source_path"])

  def test_metadata_inventory_does_not_treat_plain_detail_view_as_master_detail(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "baseInfo.customerInfo.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "baseInfo",
                  "name": "customerInfo",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      }
                  ],
                  "views": {
                      "detail": {
                          "fields": [
                              {"name": "id", "type": "field"},
                          ]
                      }
                  },
                  "apis": {
                      "search": {"isEnabled": True},
                      "add": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

    by_api = {record["api_name"]: record for record in records}
    self.assertFalse(by_api["search"]["has_detail"])
    self.assertFalse(by_api["add"]["has_detail"])

  def test_metadata_inventory_treats_child_relations_as_master_detail(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "buy.buyOrder.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "buy",
                  "name": "buyOrder",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      }
                  ],
                  "relations": [
                      {
                          "name": "detailInfo",
                          "type": "child",
                          "isRequired": False,
                      }
                  ],
                  "apis": {
                      "search": {"isEnabled": True},
                      "add": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

    by_api = {record["api_name"]: record for record in records}
    self.assertTrue(by_api["search"]["has_detail"])
    self.assertTrue(by_api["add"]["has_detail"])

  def test_metadata_inventory_extracts_minimal_stage_one_write_samples_for_simple_single_table_model(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "baseInfo.simpleModel.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "baseInfo",
                  "name": "simpleModel",
                  "title": "简单模型",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "title": "ID",
                          "type": "String",
                          "isPrimaryKey": True,
                      },
                      {
                          "name": "code",
                          "title": "编号",
                          "type": "单行文本",
                      },
                      {
                          "name": "name",
                          "title": "名称",
                          "type": "单行文本",
                      },
                      {
                          "name": "lineDate",
                          "title": "日期",
                          "type": "日期",
                      },
                      {
                          "name": "qty",
                          "title": "数量",
                          "type": "整数数字",
                      },
                  ],
                  "views": {
                      "add": {
                          "fields": [
                              {"name": "id", "type": "field"},
                              {"name": "code", "type": "field"},
                              {"name": "name", "type": "field"},
                              {"name": "lineDate", "type": "field"},
                              {"name": "qty", "type": "field"},
                          ]
                      },
                      "edit": {
                          "fields": [
                              {"name": "name", "type": "field"},
                              {"name": "qty", "type": "field"},
                          ]
                      },
                  },
                  "apis": {
                      "add": {"isEnabled": True},
                      "update": {"isEnabled": True},
                      "delete": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

    by_api = {record["api_name"]: record for record in records}
    self.assertTrue(by_api["add"]["stage_one_write_executable"])
    self.assertEqual("id", by_api["add"]["write_primary_key_field"])
    self.assertEqual({"id": "sample-id"}, by_api["add"]["sample_payload"])
    self.assertEqual("id[String]", by_api["add"]["payload_form"])
    self.assertIn("optional field code omitted from minimal add payload", by_api["add"]["stage_one_write_reason"])
    self.assertEqual({"name": "sample-text-updated"}, by_api["update"]["sample_update_payload"])
    self.assertTrue(by_api["delete"]["stage_one_write_executable"])

  def test_metadata_inventory_marks_relation_or_auto_increment_write_models_as_stage_two_only(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "baseInfo.relationModel.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "baseInfo",
                  "name": "relationModel",
                  "title": "关联模型",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "title": "ID",
                          "type": "自增主键",
                          "isPrimaryKey": True,
                      },
                      {
                          "name": "factoryNo",
                          "title": "厂区",
                          "type": "数据字典",
                      },
                  ],
                  "views": {
                      "add": {
                          "fields": [
                              {"name": "factoryNo", "type": "relation"},
                          ]
                      }
                  },
                  "apis": {
                      "add": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

    self.assertEqual(1, len(records))
    self.assertFalse(records[0]["stage_one_write_executable"])
    self.assertIn("stage-two", records[0]["stage_one_write_reason"])

  def test_metadata_inventory_preserves_required_select_dependencies_for_runtime_resolution(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "baseInfo.runtimeSelect.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "baseInfo",
                  "name": "runtimeSelect",
                  "title": "运行期选择模型",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "title": "ID",
                          "type": "String",
                          "isPrimaryKey": True,
                      },
                      {
                          "name": "orderType",
                          "title": "订单类型",
                          "type": "数据字典",
                          "isRequired": True,
                          "dictName": "dictOrderType",
                          "selectorType": "select",
                      },
                      {
                          "name": "remark",
                          "title": "备注",
                          "type": "单行文本",
                      },
                  ],
                  "relations": [
                      {
                          "name": "supplierNo",
                          "title": "供应商",
                          "type": "reference",
                          "model": "baseInfo.customerInfo",
                          "referenceKey": "customerNo",
                          "isRequired": True,
                          "selectorType": "select",
                          "displayType": "formItem",
                      }
                  ],
                  "views": {
                      "add": {
                          "fields": [
                              {"name": "supplierNo", "type": "relation"},
                              {"name": "orderType", "type": "field"},
                              {"name": "remark", "type": "field"},
                          ]
                      }
                  },
                  "apis": {
                      "add": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

    self.assertEqual(1, len(records))
    add_record = records[0]
    self.assertTrue(add_record["stage_one_write_executable"])
    self.assertEqual("id", add_record["write_primary_key_field"])
    self.assertEqual({"id": "sample-id"}, add_record["sample_payload"])
    self.assertEqual(
        ["supplierNo", "orderType"],
        [dependency["field_name"] for dependency in add_record["runtime_write_dependencies"]],
    )
    self.assertEqual("reference", add_record["runtime_write_dependencies"][0]["dependency_kind"])
    self.assertEqual("baseInfo.customerInfo", add_record["runtime_write_dependencies"][0]["model_key"])
    self.assertEqual("customerNo", add_record["runtime_write_dependencies"][0]["reference_key"])
    self.assertEqual("data-dict", add_record["runtime_write_dependencies"][1]["dependency_kind"])
    self.assertEqual("dictOrderType", add_record["runtime_write_dependencies"][1]["dict_name"])

  def test_metadata_inventory_auto_view_includes_required_relations_for_runtime_resolution(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "app.autoRelation.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "app",
                  "name": "autoRelation",
                  "title": "自动表单关联模型",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "title": "ID",
                          "type": "String",
                          "isPrimaryKey": True,
                      },
                      {
                          "name": "mark",
                          "title": "备注",
                          "type": "单行文本",
                      },
                  ],
                  "relations": [
                      {
                          "name": "customerNo",
                          "title": "客商",
                          "type": "reference",
                          "model": "baseInfo.customerInfo",
                          "referenceKey": "customerNo",
                          "isRequired": True,
                          "selectorType": "select",
                          "displayType": "formItem",
                      },
                      {
                          "name": "loadArea",
                          "title": "厂区",
                          "type": "reference",
                          "model": "baseInfo.factoryArea",
                          "referenceKey": "factoryNo",
                          "isRequired": True,
                          "selectorType": "select",
                          "displayType": "formItem",
                      },
                      {
                          "name": "transNo",
                          "title": "承运商",
                          "type": "reference",
                          "model": "baseInfo.customerInfo",
                          "referenceKey": "customerNo",
                          "isRequired": False,
                          "selectorType": "select",
                          "displayType": "formItem",
                      },
                  ],
                  "views": {
                      "add": {
                          "isEnabled": True,
                          "isAuto": True,
                      }
                  },
                  "apis": {
                      "add": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

    self.assertEqual(1, len(records))
    add_record = records[0]
    self.assertTrue(add_record["stage_one_write_executable"])
    self.assertEqual({"id": "sample-id"}, add_record["sample_payload"])
    self.assertEqual(
        ["customerNo", "loadArea"],
        [dependency["field_name"] for dependency in add_record["runtime_write_dependencies"]],
    )
    self.assertNotIn("transNo", [dependency["field_name"] for dependency in add_record["runtime_write_dependencies"]])

  def test_metadata_inventory_marks_missing_required_add_view_fields_as_stage_two_only(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "store.strictStageOne.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "store",
                  "name": "strictStageOne",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      },
                      {
                          "name": "status",
                          "type": "数据字典",
                          "isRequired": True,
                      },
                      {
                          "name": "remark",
                          "type": "单行文本",
                      },
                  ],
                  "views": {
                      "add": {
                          "fields": [
                              {"name": "remark", "type": "field"},
                          ]
                      }
                  },
                  "apis": {
                      "add": {"isEnabled": True},
                      "update": {"isEnabled": True},
                      "delete": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

    by_api = {record["api_name"]: record for record in records}
    self.assertFalse(by_api["add"]["stage_one_write_executable"])
    self.assertFalse(by_api["update"]["stage_one_write_executable"])
    self.assertFalse(by_api["delete"]["stage_one_write_executable"])
    self.assertIn("required field status omitted from add view", by_api["add"]["stage_one_write_reason"])

  def test_metadata_inventory_allows_hidden_required_company_context_field(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "baseInfo.hiddenCompanyScope.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "baseInfo",
                  "name": "hiddenCompanyScope",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      },
                      {
                          "name": "companyId",
                          "type": "组织架构",
                          "isRequired": True,
                      },
                      {
                          "name": "name",
                          "type": "单行文本",
                      },
                  ],
                  "views": {
                      "add": {
                          "fields": [
                              {"name": "name", "type": "field"},
                          ]
                      }
                  },
                  "apis": {
                      "add": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

    self.assertEqual(1, len(records))
    self.assertTrue(records[0]["stage_one_write_executable"])
    self.assertEqual("__COMPANY_ID__", records[0]["sample_payload"]["companyId"])

  def test_metadata_inventory_includes_hidden_required_defaulted_fields_in_write_payload(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "store.hiddenStatus.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "store",
                  "name": "hiddenStatus",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      },
                      {
                          "name": "approveStatus",
                          "type": "单行文本",
                          "isRequired": True,
                          "defaultValue": "0",
                      },
                      {
                          "name": "remark",
                          "type": "单行文本",
                      },
                  ],
                  "views": {
                      "add": {
                          "fields": [
                              {"name": "remark", "type": "field"},
                          ]
                      },
                      "edit": {
                          "fields": [
                              {"name": "remark", "type": "field"},
                          ]
                      },
                  },
                  "apis": {
                      "add": {"isEnabled": True},
                      "update": {"isEnabled": True},
                      "delete": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

    by_api = {record["api_name"]: record for record in records}
    self.assertTrue(by_api["add"]["stage_one_write_executable"])
    self.assertEqual("0", by_api["add"]["sample_payload"]["approveStatus"])
    self.assertIn("approveStatus[单行文本]", by_api["add"]["payload_form"])

  def test_metadata_inventory_preserves_data_dict_default_shape_for_hidden_required_fields(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "store.hiddenDictStatus.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "store",
                  "name": "hiddenDictStatus",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      },
                      {
                          "name": "status",
                          "type": "数据字典",
                          "selectorType": "select",
                          "dictName": "dictDemoStatus",
                          "isRequired": True,
                          "defaultValue": "'0'",
                      },
                      {
                          "name": "remark",
                          "type": "单行文本",
                      },
                  ],
                  "views": {
                      "add": {
                          "fields": [
                              {"name": "remark", "type": "field"},
                          ]
                      },
                      "edit": {
                          "fields": [
                              {"name": "remark", "type": "field"},
                          ]
                      },
                  },
                  "apis": {
                      "add": {"isEnabled": True},
                      "update": {"isEnabled": True},
                      "delete": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

    by_api = {record["api_name"]: record for record in records}
    self.assertTrue(by_api["add"]["stage_one_write_executable"])
    self.assertEqual("0", by_api["add"]["sample_payload"]["status"])
    self.assertIn("status[数据字典]", by_api["add"]["payload_form"])

  def test_metadata_inventory_omits_optional_company_context_from_minimal_write_samples(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "baseInfo.companyScoped.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "baseInfo",
                  "name": "companyScoped",
                  "title": "企业范围模型",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "title": "ID",
                          "type": "String",
                          "isPrimaryKey": True,
                      },
                      {
                          "name": "companyId",
                          "title": "企业",
                          "type": "组织架构",
                      },
                      {
                          "name": "code",
                          "title": "编号",
                          "type": "单行文本",
                      },
                  ],
                  "views": {
                      "add": {
                          "fields": [
                              {"name": "companyId", "type": "field"},
                              {"name": "code", "type": "field"},
                          ]
                      },
                      "edit": {
                          "fields": [
                              {"name": "code", "type": "field"},
                          ]
                      },
                  },
                  "apis": {
                      "add": {"isEnabled": True},
                      "update": {"isEnabled": True},
                      "delete": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

    by_api = {record["api_name"]: record for record in records}
    self.assertTrue(by_api["add"]["stage_one_write_executable"])
    self.assertEqual("id", by_api["add"]["write_primary_key_field"])
    self.assertEqual({"id": "sample-id"}, by_api["add"]["sample_payload"])
    self.assertIn("optional field companyId omitted from minimal add payload", by_api["add"]["stage_one_write_reason"])

  def test_metadata_inventory_keeps_update_sample_value_shape_consistent_with_field_type(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "baseInfo.datePatch.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "baseInfo",
                  "name": "datePatch",
                  "title": "日期补丁模型",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      },
                      {
                          "name": "lineDate",
                          "type": "日期",
                      },
                  ],
                  "views": {
                      "add": {
                          "fields": [
                              {"name": "id", "type": "field"},
                              {"name": "lineDate", "type": "field"},
                          ]
                      },
                      "edit": {
                          "fields": [
                              {"name": "lineDate", "type": "field"},
                          ]
                      },
                  },
                  "apis": {
                      "add": {"isEnabled": True},
                      "update": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

    by_api = {record["api_name"]: record for record in records}
    self.assertEqual({"lineDate": "2024-01-02"}, by_api["update"]["sample_update_payload"])

  def test_metadata_inventory_normalizes_full_model_key_to_runtime_api_path(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "baseInfo.weightPhoto.business.json"
      ui_schema_file = Path(temp_dir) / "base-info.weight-photo.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "baseInfo",
                  "name": "weightPhoto",
                  "title": "磅单照片",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      }
                  ],
                  "apis": {
                      "search": {"isEnabled": True},
                  },
              }
          )
      )
      ui_schema_file.write_text(
          json.dumps(
              {
                  "schemaId": "/base-info/weight-photo/detail",
                  "api": "/api/base-info/weight-photo",
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file, [ui_schema_file])

      self.assertEqual(1, len(records))
      self.assertEqual("/api/base-info/weight-photo", records[0]["urls"][0])
      self.assertIn("/base-info/weight-photo/detail", records[0]["schema_ids"])
      self.assertNotIn("/api/baseInfo/weight-photo", records[0]["urls"])

  def test_metadata_inventory_supports_multilevel_namespace_fallback_from_filename(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "product.production.workshop.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "title": "车间",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      }
                  ],
                  "apis": {
                      "search": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

      self.assertEqual(1, len(records))
      self.assertEqual("product.production.workshop", records[0]["model_key"])
      self.assertEqual(["/api/product/production/workshop"], records[0]["urls"])

  def test_ui_schema_reference_extraction_collects_api_schema_and_source(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    references = inventory.extract_ui_schema_references_from_file(FIXTURES / "ui_schema_sample.json")

    reference_summary = {(reference["kind"], reference["value"]) for reference in references}
    self.assertIn(("api", "/api/buy/waybill-order"), reference_summary)
    self.assertIn(("source", "/api/base-info/customer-info"), reference_summary)
    self.assertIn(("schemaId", "/buy/waybill-order/detail"), reference_summary)
    self.assertIn(("crud", "/api/buy/waybill-order"), reference_summary)

  def test_high_code_scanner_keeps_data_api_relevant_mappings(self):
    high_code_catalog = importlib.import_module("testing.common.runners.api.data_api_regression.high_code_catalog")

    records = high_code_catalog.scan_controller_file(FIXTURES / "high_code_controller_sample.java")

    endpoint_summary = {(record["controller"], record["method"], record["url"]) for record in records}
    self.assertIn(("UserController", "GET", "/api/sys/user"), endpoint_summary)
    self.assertIn(("UserController", "POST", "/api/sys/user"), endpoint_summary)
    self.assertIn(("UserController", "DELETE", "/api/sys/user/{id}"), endpoint_summary)
    self.assertNotIn(("SystemInfoController", "GET", "/api/system/info"), endpoint_summary)

  def test_collect_metadata_inventory_skips_index_lists(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_dir = Path(temp_dir) / "business-model"
      business_model_dir.mkdir()
      (business_model_dir / "modules.json").write_text(json.dumps([{"name": "buy"}]))
      (business_model_dir / "buy.waybillOrder.business.json").write_text(
          json.dumps(
              {
                  "namespace": "buy",
                  "name": "waybillOrder",
                  "title": "采购运单",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      }
                  ],
                  "apis": {
                      "search": {
                          "isEnabled": True,
                          "beforeScript": "return $0"
                      }
                  },
              }
          )
      )

      records = inventory.collect_metadata_inventory(business_model_dir)

      self.assertEqual(1, len(records))
      self.assertEqual("buy.waybillOrder", records[0]["model_key"])

  def test_metadata_inventory_supports_legacy_view_root_for_filter_extraction(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "buy.legacyFilter.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "buy",
                  "name": "legacyFilter",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      }
                  ],
                  "apis": {"search": {"isEnabled": True}},
                  "view": {
                      "index": {
                          "defaultFilters": [
                              {
                                  "fieldName": "keyword",
                                  "filterOperator": "CONTAINS",
                                  "filterType": "input-text",
                              }
                          ]
                      }
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

      self.assertEqual(1, len(records))
      self.assertEqual("keyword.CONTAINS", records[0]["query_filters"][0]["param_name"])
      self.assertEqual("view.index.defaultFilters", records[0]["query_filters"][0]["source_path"])

  def test_app_namespace_search_and_add_are_marked_as_reserved_route_conflicts(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "app.waybillBaseInfo.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "app",
                  "name": "waybillBaseInfo",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      }
                  ],
                  "apis": {
                      "search": {"isEnabled": True},
                      "add": {"isEnabled": True},
                      "getOne": {"isEnabled": True},
                  },
              }
          )
      )

      records = inventory.build_metadata_inventory(business_model_file)

      by_api = {record["api_name"]: record for record in records}
      self.assertTrue(by_api["search"]["reserved_route_conflict"])
      self.assertTrue(by_api["add"]["reserved_route_conflict"])
      self.assertFalse(by_api["getOne"]["reserved_route_conflict"])
      self.assertIn("AppManagementController", by_api["search"]["reserved_route_reason"])

  def test_metadata_inventory_skips_disabled_model_and_api(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      disabled_model_file = Path(temp_dir) / "buy.disabledModel.business.json"
      disabled_model_file.write_text(
          json.dumps(
              {
                  "namespace": "buy",
                  "name": "disabledModel",
                  "isEnabled": False,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      }
                  ],
                  "apis": {
                      "search": {"isEnabled": True},
                  },
              }
          )
      )

      disabled_api_file = Path(temp_dir) / "buy.disabledApi.business.json"
      disabled_api_file.write_text(
          json.dumps(
              {
                  "namespace": "buy",
                  "name": "disabledApi",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      }
                  ],
                  "apis": {
                      "search": {"isEnabled": False},
                  },
              }
          )
      )

      self.assertEqual([], inventory.build_metadata_inventory(disabled_model_file))
      self.assertEqual([], inventory.build_metadata_inventory(disabled_api_file))

  def test_metadata_inventory_skips_models_without_fields(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "buy.summaryView.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "buy",
                  "name": "summaryView",
                  "isEnabled": True,
                  "fields": [],
                  "apis": {
                      "search": {"isEnabled": True},
                  },
              }
          )
      )

      self.assertEqual([], inventory.build_metadata_inventory(business_model_file))

  def test_metadata_inventory_excludes_project_owned_model_from_test_scope(self):
    inventory = importlib.import_module("testing.common.runners.api.data_api_regression.inventory")

    with tempfile.TemporaryDirectory() as temp_dir:
      business_model_file = Path(temp_dir) / "baseInfo.deviceManage.business.json"
      business_model_file.write_text(
          json.dumps(
              {
                  "namespace": "baseInfo",
                  "name": "deviceManage",
                  "isEnabled": True,
                  "fields": [
                      {
                          "name": "id",
                          "type": "String",
                          "isPrimaryKey": True,
                      }
                  ],
                  "apis": {
                      "search": {"isEnabled": True},
                  },
              }
          )
      )

      self.assertEqual([], inventory.build_metadata_inventory(business_model_file))


if __name__ == "__main__":
  unittest.main()

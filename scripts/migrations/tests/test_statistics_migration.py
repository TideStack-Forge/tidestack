import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "migrate-statistics-definitions.py"
SPEC = importlib.util.spec_from_file_location("statistics_migration", SCRIPT)
migration = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(migration)


class StatisticsMigrationTest(unittest.TestCase):
    def dataset(self):
        return {"name": "Demo.Sales", "status": "published", "queryDefinition": {
            "type": "sql", "datasourceName": "warehouse", "genericSql": "select :id as id",
            "variants": [{"dialect": "mysql", "sqlText": "select :id as id", "priority": 100}]}}

    def test_converts_sql_dialects_and_lifecycle_without_mutating_source(self):
        original = self.dataset()
        result = migration.convert_dataset(original)
        self.assertEqual("published", original["status"])
        self.assertTrue(result["isEnabled"])
        self.assertEqual("warehouse", result["queryDefinition"]["datasource"])
        self.assertEqual("select ${id} as id", result["queryDefinition"]["sql"])
        self.assertEqual([{"dialectName": "mysql", "sql": "select ${id} as id"}], result["queryDefinition"]["dialects"])
        self.assertEqual(result, migration.convert_dataset(result))

    def test_drafts_remain_disabled(self):
        value = self.dataset()
        value["status"] = "draft"
        self.assertFalse(migration.convert_dataset(value)["isEnabled"])

    def test_sql_literals_comments_and_casts_are_preserved(self):
        sql = "select ':literal', \"a:b\", `a:b`, $$:dollar$$, :id::text -- :comment\n/* :comment */"
        self.assertEqual(sql.replace(":id", "${id}"), migration.convert_sql(sql))

    def test_rejects_variant_semantics_that_require_manual_resolution(self):
        for key, value in [("datasourceTags", ["prod"]), ("dialectVersionRange", ">=8")]:
            dataset = self.dataset()
            dataset["queryDefinition"]["variants"][0][key] = value
            with self.assertRaises(ValueError):
                migration.convert_dataset(dataset)
        dataset = self.dataset()
        dataset["queryDefinition"]["variants"] *= 2
        with self.assertRaises(ValueError):
            migration.convert_dataset(dataset)

    def test_config_conversion_normalizes_identity_and_requires_connection_registration(self):
        row = {"appName": "CRM", "datasetName": "Demo.Sales", "testParameters": {"id": 2},
               "testDatasourceName": "warehouse", "jdbcConnection": {"password": "private"}}
        with self.assertRaises(ValueError):
            migration.convert_configs([row], {})
        result = migration.convert_configs([row], {"crm/demo.sales": {"kind": "platform", "platformDataSourceName": "warehouse"}})
        self.assertEqual("crm", result[0]["appName"])
        self.assertEqual({"id": 2}, result[0]["parameters"])
        self.assertNotIn("private", json.dumps(result))

    def test_rejects_conflicting_historical_rows(self):
        row = {"appName": "crm", "datasetName": "sales", "parameters": {"id": 1}}
        with self.assertRaises(ValueError):
            migration.convert_configs([row, {**row, "parameters": {"id": 2}}], {})

    def test_cli_preview_apply_and_failure_keep_original_data(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "source"
            source.mkdir()
            original = json.dumps(self.dataset())
            (source / "sales.dataset.json").write_text(original)
            output = root / "converted"
            command = [sys.executable, str(SCRIPT), "--source", str(source), "--output", str(output)]
            self.assertEqual(0, subprocess.run(command, capture_output=True).returncode)
            self.assertFalse(output.exists())
            (source / "legacy.report-view.json").write_text('{}')
            self.assertEqual(1, subprocess.run(command + ["--apply"], capture_output=True).returncode)
            self.assertFalse(output.exists())
            (source / "legacy.report-view.json").unlink()
            self.assertEqual(0, subprocess.run(command + ["--apply"], capture_output=True).returncode)
            self.assertTrue(json.loads((output / "sales.dataset.json").read_text())["isEnabled"])
            self.assertNotEqual(0, subprocess.run(command + ["--apply"], capture_output=True).returncode)
            self.assertEqual(original, (source / "sales.dataset.json").read_text())

    def test_rejects_case_only_duplicate_resources(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary)
            (source / "a.dataset.json").write_text(json.dumps(self.dataset()))
            (source / "b.dataset.json").write_text(json.dumps({**self.dataset(), "name": "DEMO.sales"}))
            _, errors = migration.plan(source, None, {})
            self.assertEqual(1, len(errors))


if __name__ == "__main__":
    unittest.main()

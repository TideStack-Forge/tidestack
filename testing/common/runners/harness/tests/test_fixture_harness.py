import importlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[5]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))


class FixtureHarnessTest(unittest.TestCase):

  def setUp(self):
    self.runner = importlib.import_module("testing.common.runners.harness.fixture_harness")

  def test_build_action_plan_marks_missing_commands_as_skipped(self):
    plan = self.runner.build_action_plan(
        {
            "services": [
                {
                    "id": "services/example",
                    "lifecycle": {
                        "check": "shell: echo ok",
                    },
                }
            ],
            "data": [
                {
                    "id": "data/example",
                    "lifecycle": {},
                }
            ],
            "identities": [
                {
                    "id": "identities/example",
                    "lifecycle": {
                        "check": "login should succeed",
                    },
                }
            ],
        },
        "check",
    )

    self.assertEqual("pending", plan[0]["status"])
    self.assertEqual("skipped", plan[1]["status"])
    self.assertEqual("lifecycle command not declared", plan[1]["reason"])
    self.assertEqual("manual", plan[2]["status"])
    self.assertEqual("login should succeed", plan[2]["description"])

  def test_execute_action_plan_captures_command_result(self):
    plan = [
        {
            "fixture_id": "services/example",
            "class": "services",
            "action": "check",
            "command": "echo harness-ok",
            "description": "",
            "status": "pending",
            "reason": "",
        }
    ]

    executions = self.runner.execute_action_plan(plan)

    self.assertEqual("pass", executions[0]["status"])
    self.assertEqual(0, executions[0]["exit_code"])
    self.assertIn("harness-ok", executions[0]["stdout"])

  def test_main_plan_mode_writes_report(self):
    manifest = {
        "id": "feature/oral-cases",
        "fixtures": {
            "services": ["example-service"],
        },
    }

    with tempfile.TemporaryDirectory() as tmp_dir:
      scenario_path = Path(tmp_dir) / "scenario.yaml"
      scenario_path.write_text(json.dumps(manifest))
      fixture_root = Path(tmp_dir) / "fixtures"
      fixture_dir = fixture_root / "services/example-service"
      fixture_dir.mkdir(parents=True)
      (fixture_dir / "fixture.yaml").write_text(
          json.dumps(
              {
                  "id": "services/example-service",
                  "title": "Example Service",
                  "class": "services",
                  "outputs": [],
                  "lifecycle": {
                      "check": "shell: echo ok",
                  },
              }
          )
      )
      report_dir = Path(tmp_dir) / "report"

      with mock.patch("testing.common.runners.fixture_registry.FIXTURES_ROOT", fixture_root):
        exit_code = self.runner.main(
            [
                "--scenario", str(scenario_path),
                "--action", "check",
                "--mode", "plan",
                "--report-dir", str(report_dir),
            ]
        )

      report_payload = json.loads((report_dir / "fixture-harness-check.json").read_text())

    self.assertEqual(0, exit_code)
    self.assertEqual("feature/oral-cases", report_payload["scenarioId"])
    self.assertEqual("plan", report_payload["summary"]["mode"])
    self.assertEqual("pending", report_payload["plan"][0]["status"])

  def test_main_run_mode_executes_declared_commands(self):
    manifest = {
        "id": "feature/oral-cases",
        "fixtures": {
            "services": ["example-service"],
        },
    }

    with tempfile.TemporaryDirectory() as tmp_dir:
      scenario_path = Path(tmp_dir) / "scenario.yaml"
      scenario_path.write_text(json.dumps(manifest))
      fixture_root = Path(tmp_dir) / "fixtures"
      fixture_dir = fixture_root / "services/example-service"
      fixture_dir.mkdir(parents=True)
      (fixture_dir / "fixture.yaml").write_text(
          json.dumps(
              {
                  "id": "services/example-service",
                  "title": "Example Service",
                  "class": "services",
                  "outputs": [],
                  "lifecycle": {
                      "check": "shell: echo run-ok",
                  },
              }
          )
      )
      report_dir = Path(tmp_dir) / "report"

      with mock.patch("testing.common.runners.fixture_registry.FIXTURES_ROOT", fixture_root):
        exit_code = self.runner.main(
            [
                "--scenario", str(scenario_path),
                "--action", "check",
                "--mode", "run",
                "--report-dir", str(report_dir),
            ]
        )

      report_payload = json.loads((report_dir / "fixture-harness-check.json").read_text())

    self.assertEqual(0, exit_code)
    self.assertEqual("run", report_payload["summary"]["mode"])
    self.assertEqual("pass", report_payload["executions"][0]["status"])
    self.assertIn("run-ok", report_payload["executions"][0]["stdout"])

  def test_main_blocks_when_fixture_preflight_fails(self):
    manifest = {
        "id": "feature/oral-cases",
        "fixtures": {
            "data": ["broken-data"],
        },
    }

    with tempfile.TemporaryDirectory() as tmp_dir:
      scenario_path = Path(tmp_dir) / "scenario.yaml"
      scenario_path.write_text(json.dumps(manifest))
      fixture_root = Path(tmp_dir) / "fixtures"
      fixture_dir = fixture_root / "data/broken-data"
      fixture_dir.mkdir(parents=True)
      (fixture_dir / "fixture.yaml").write_text(
          json.dumps(
              {
                  "id": "data/broken-data",
                  "title": "Broken Data",
                  "class": "data",
                  "outputs": [
                      {
                          "name": "appRoot",
                          "value": "tmp/does-not-exist",
                      }
                  ],
                  "lifecycle": {
                      "check": "shell: echo never-run",
                  },
              }
          )
      )
      report_dir = Path(tmp_dir) / "report"

      with mock.patch("testing.common.runners.fixture_registry.FIXTURES_ROOT", fixture_root):
        with self.assertRaises(SystemExit) as context:
          self.runner.main(
              [
                  "--scenario", str(scenario_path),
                  "--action", "check",
                  "--mode", "run",
                  "--report-dir", str(report_dir),
              ]
          )

      report_payload = json.loads((report_dir / "fixture-harness-check.json").read_text())

    self.assertIn("fixture preflight failed:", str(context.exception))
    self.assertTrue(report_payload["summary"]["blocked"])
    self.assertEqual("fixture-preflight-failed", report_payload["summary"]["blocker"])

  def test_main_plan_mode_accepts_repo_bound_data_fixture_inputs(self):
    manifest = {
        "id": "feature/oral-cases",
        "fixtures": {
            "data": ["app-metadata-root"],
        },
    }

    with tempfile.TemporaryDirectory() as tmp_dir:
      scenario_path = Path(tmp_dir) / "scenario.yaml"
      scenario_path.write_text(json.dumps(manifest))
      fixture_root = Path(tmp_dir) / "fixtures"
      fixture_dir = fixture_root / "data/app-metadata-root"
      fixture_dir.mkdir(parents=True)
      app_repo = Path(tmp_dir) / "repos/app-metadata"
      app_root = app_repo / "apps/sample-app"
      (app_root / "business-model").mkdir(parents=True)
      (app_root / "ui-schema").mkdir(parents=True)
      (fixture_dir / "fixture.yaml").write_text(
          json.dumps(
              {
                  "id": "data/app-metadata-root",
                  "title": "App Metadata Root",
                  "class": "data",
                  "inputs": [
                      {
                          "name": "appRoot",
                          "required": True,
                          "sources": [
                              {"kind": "repo", "name": "app-metadata", "join": "apps/sample-app"},
                          ],
                      }
                  ],
                  "outputs": [
                      {"name": "appRoot", "fromInput": "appRoot"},
                      {"name": "businessModelDir", "fromOutput": "appRoot", "join": "business-model"},
                      {"name": "uiSchemaDir", "fromOutput": "appRoot", "join": "ui-schema"},
                  ],
                  "lifecycle": {},
              }
          )
      )
      report_dir = Path(tmp_dir) / "report"

      with mock.patch("testing.common.runners.fixture_registry.FIXTURES_ROOT", fixture_root):
        exit_code = self.runner.main(
            [
                "--scenario", str(scenario_path),
                "--action", "check",
                "--mode", "plan",
                "--report-dir", str(report_dir),
                "--repo", f"app-metadata={app_repo}",
            ]
        )

      report_payload = json.loads((report_dir / "fixture-harness-check.json").read_text())

    self.assertEqual(0, exit_code)
    self.assertEqual("pass", report_payload["fixturePreflight"][0]["status"])


if __name__ == "__main__":
  unittest.main()

import importlib
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[5]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))


class BrowserAssetBridgeTest(unittest.TestCase):

  def setUp(self):
    self.runner = importlib.import_module("testing.common.runners.playwright.browser_asset_bridge")

  def test_preflight_marks_missing_dependency_script_as_blocked(self):
    with tempfile.TemporaryDirectory() as tmp_dir:
      project_root = Path(tmp_dir) / "frontend"
      project_root.mkdir()
      (project_root / "package.json").write_text(
          json.dumps(
              {
                  "scripts": {
                      "test:auth-flow": "node scripts/run-auth-flow-tests.js",
                  }
              }
          )
      )
      contract_path = Path(tmp_dir) / "contract.md"
      contract_path.write_text("# contract")
      source_path = Path(tmp_dir) / "flow.spec.ts"
      source_path.write_text("// source")
      manifest = {
          "scenario": "auth/ldap-login",
          "browserAssets": {
              "projectRoot": str(project_root),
              "contracts": [str(contract_path)],
              "sources": [str(source_path)],
              "scripts": [
                  {
                      "id": "auth-flow",
                      "kind": "npm",
                      "script": "test:auth-flow",
                      "requiresNodeModules": True,
                  }
              ],
          },
      }

      report = self.runner.preflight_browser_assets(manifest)

    self.assertEqual("pass", report["status"])
    self.assertEqual("blocked", report["scripts"][0]["status"])
    self.assertIn("node_modules is missing", report["scripts"][0]["reason"])

  def test_preflight_accepts_script_without_project_dependencies(self):
    with tempfile.TemporaryDirectory() as tmp_dir:
      project_root = Path(tmp_dir) / "frontend"
      project_root.mkdir()
      (project_root / "package.json").write_text(
          json.dumps(
              {
                  "scripts": {
                      "echo:test": "node -e \"console.log('ok')\"",
                  }
              }
          )
      )
      manifest = {
          "scenario": "feature/oral-cases",
          "browserAssets": {
              "projectRoot": str(project_root),
              "scripts": [
                  {
                      "id": "echo-test",
                      "kind": "npm",
                      "script": "echo:test",
                      "requiresNodeModules": False,
                  }
              ],
          },
      }

      report = self.runner.preflight_browser_assets(manifest)

    self.assertEqual("pending", report["scripts"][0]["status"])

  def test_main_plan_mode_writes_report(self):
    with tempfile.TemporaryDirectory() as tmp_dir:
      project_root = Path(tmp_dir) / "frontend"
      project_root.mkdir()
      (project_root / "package.json").write_text(json.dumps({"scripts": {}}))
      manifest_path = Path(tmp_dir) / "e2e.yaml"
      manifest_path.write_text(
          json.dumps(
              {
                  "scenario": "feature/oral-cases",
                  "browserAssets": {
                      "projectRoot": str(project_root),
                      "scripts": [],
                  },
              }
          )
      )
      report_dir = Path(tmp_dir) / "report"

      exit_code = self.runner.main(
          [
              "--e2e-manifest", str(manifest_path),
              "--mode", "plan",
              "--report-dir", str(report_dir),
          ]
      )

      payload = json.loads((report_dir / "browser-asset-bridge.json").read_text())

    self.assertEqual(0, exit_code)
    self.assertEqual("feature/oral-cases", payload["scenario"])

  def test_main_run_mode_executes_script(self):
    with tempfile.TemporaryDirectory() as tmp_dir:
      project_root = Path(tmp_dir) / "frontend"
      project_root.mkdir()
      (project_root / "package.json").write_text(
          json.dumps(
              {
                  "name": "frontend",
                  "scripts": {
                      "echo:test": "node -e \"console.log('bridge-ok')\"",
                  },
              }
          )
      )
      manifest_path = Path(tmp_dir) / "e2e.yaml"
      manifest_path.write_text(
          json.dumps(
              {
                  "scenario": "feature/oral-cases",
                  "browserAssets": {
                      "projectRoot": str(project_root),
                      "scripts": [
                          {
                              "id": "echo-test",
                              "kind": "npm",
                              "script": "echo:test",
                              "requiresNodeModules": False,
                          }
                      ],
                  },
              }
          )
      )
      report_dir = Path(tmp_dir) / "report"

      exit_code = self.runner.main(
          [
              "--e2e-manifest", str(manifest_path),
              "--mode", "run",
              "--script-id", "echo-test",
              "--report-dir", str(report_dir),
          ]
      )

      payload = json.loads((report_dir / "browser-asset-bridge.json").read_text())

    self.assertEqual(0, exit_code)
    self.assertEqual("pass", payload["executions"][0]["status"])
    self.assertIn("bridge-ok", payload["executions"][0]["stdout"])

  def test_main_run_mode_returns_blocked_for_missing_dependencies(self):
    with tempfile.TemporaryDirectory() as tmp_dir:
      project_root = Path(tmp_dir) / "frontend"
      project_root.mkdir()
      (project_root / "package.json").write_text(
          json.dumps(
              {
                  "scripts": {
                      "test:auth-flow": "node scripts/run-auth-flow-tests.js",
                  }
              }
          )
      )
      manifest_path = Path(tmp_dir) / "e2e.yaml"
      manifest_path.write_text(
          json.dumps(
              {
                  "scenario": "auth/ldap-login",
                  "browserAssets": {
                      "projectRoot": str(project_root),
                      "scripts": [
                          {
                              "id": "auth-flow",
                              "kind": "npm",
                              "script": "test:auth-flow",
                              "requiresNodeModules": True,
                          }
                      ],
                  },
              }
          )
      )
      report_dir = Path(tmp_dir) / "report"

      exit_code = self.runner.main(
          [
              "--e2e-manifest", str(manifest_path),
              "--mode", "run",
              "--script-id", "auth-flow",
              "--report-dir", str(report_dir),
          ]
      )

      payload = json.loads((report_dir / "browser-asset-bridge.json").read_text())

    self.assertEqual(1, exit_code)
    self.assertEqual("blocked", payload["executions"][0]["status"])

  def test_preflight_accepts_shell_script_when_required_paths_exist(self):
    with tempfile.TemporaryDirectory() as tmp_dir:
      helper = Path(tmp_dir) / "helper.sh"
      helper.write_text("#!/bin/bash\necho helper\n")
      helper.chmod(0o755)
      manifest = {
          "scenario": "lowcode/full-app-lifecycle",
          "browserAssets": {
              "scripts": [
                  {
                      "id": "bootstrap-contract",
                      "kind": "shell",
                      "command": f"bash {helper}",
                      "workdir": tmp_dir,
                      "requiredPaths": [str(helper)],
                  }
              ],
          },
      }

      report = self.runner.preflight_browser_assets(manifest)

    self.assertEqual("pending", report["scripts"][0]["status"])

  def test_main_run_mode_executes_shell_script(self):
    with tempfile.TemporaryDirectory() as tmp_dir:
      helper = Path(tmp_dir) / "helper.sh"
      helper.write_text("#!/bin/bash\necho shell-bridge-ok\n")
      helper.chmod(0o755)
      manifest_path = Path(tmp_dir) / "e2e.yaml"
      manifest_path.write_text(
          json.dumps(
              {
                  "scenario": "lowcode/full-app-lifecycle",
                  "browserAssets": {
                      "scripts": [
                          {
                              "id": "bootstrap-contract",
                              "kind": "shell",
                              "command": f"bash {helper}",
                              "workdir": tmp_dir,
                              "requiredPaths": [str(helper)],
                          }
                      ],
                  },
              }
          )
      )
      report_dir = Path(tmp_dir) / "report"

      exit_code = self.runner.main(
          [
              "--e2e-manifest", str(manifest_path),
              "--mode", "run",
              "--script-id", "bootstrap-contract",
              "--report-dir", str(report_dir),
          ]
      )

      payload = json.loads((report_dir / "browser-asset-bridge.json").read_text())

    self.assertEqual(0, exit_code)
    self.assertEqual("pass", payload["executions"][0]["status"])
    self.assertIn("shell-bridge-ok", payload["executions"][0]["stdout"])


if __name__ == "__main__":
  unittest.main()

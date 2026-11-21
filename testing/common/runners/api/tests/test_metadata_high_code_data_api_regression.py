import importlib
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[5]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))


class MetadataHighCodeDataApiRegressionRunnerTest(unittest.TestCase):

  def setUp(self):
    self.runner = importlib.import_module("testing.common.runners.api.metadata_high_code_data_api_regression")

  def test_build_legacy_args_uses_manifest_defaults(self):
    manifest = {
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "appRoot": "tmp/integration-app-root/sample-app",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
            "defaultExecutionRing": "metadata-list",
        },
    }
    cli_args = self.runner.parse_args([])

    legacy_args = self.runner.build_legacy_args(manifest, cli_args)

    self.assertTrue(legacy_args.inventory_only)
    self.assertFalse(legacy_args.execute)
    self.assertEqual(str(ROOT / "tmp/integration-app-root/sample-app"), legacy_args.app_root)
    self.assertEqual(str(ROOT / "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs"), legacy_args.docs_report_dir)
    self.assertIn("tmp/testing-evidence/metadata-high-code-data-api-regression", legacy_args.report_dir)
    self.assertEqual("metadata-list", legacy_args.execution_ring)

  def test_build_legacy_args_resolves_generic_data_fixture_input_sources(self):
    manifest = {
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
            "defaultExecutionRing": "metadata-list",
        },
    }
    cli_args = self.runner.parse_args([])
    with mock.patch.dict(os.environ, {"OB_TEST_APP_ROOT": "tmp/integration-app-root/sample-app"}, clear=False):
      legacy_args = self.runner.build_legacy_args(
          manifest,
          cli_args,
          {
              "data": [
                  {
                      "id": "data/app-metadata-root",
                      "inputs": [
                          {
                              "name": "appRoot",
                              "required": True,
                              "sources": [
                                  {"kind": "env", "name": "OB_TEST_APP_ROOT"},
                              ],
                          }
                      ],
                      "outputs": [
                          {
                              "name": "appRoot",
                              "fromInput": "appRoot",
                          }
                      ],
                  }
              ]
          },
      )

    self.assertEqual(str(ROOT / "tmp/integration-app-root/sample-app"), legacy_args.app_root)

  def test_build_legacy_args_resolves_generic_data_fixture_from_repo_binding(self):
    manifest = {
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
            "defaultExecutionRing": "metadata-list",
        },
    }
    cli_args = self.runner.parse_args([
        "--repo", "app-metadata=/repos/metadata",
    ])

    legacy_args = self.runner.build_legacy_args(
        manifest,
        cli_args,
        {
            "data": [
                {
                    "id": "data/app-metadata-root",
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
                        {
                            "name": "appRoot",
                            "fromInput": "appRoot",
                        }
                    ],
                }
            ]
        },
    )

    self.assertEqual("/repos/metadata/apps/sample-app", legacy_args.app_root)

  def test_build_legacy_args_uses_identity_fixture_login_payload(self):
    manifest = {
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "appRoot": "tmp/integration-app-root/sample-app",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
            "defaultExecutionRing": "metadata-list",
        },
    }
    cli_args = self.runner.parse_args([])

    legacy_args = self.runner.build_legacy_args(
        manifest,
        cli_args,
        {
            "identities": [
                {
                    "id": "identities/admin",
                    "outputs": [
                        {
                            "name": "loginPayload",
                            "value": {
                                "account": "fixture-admin",
                                "password": "fixture-password",
                            },
                        }
                    ],
                }
            ]
        },
    )

    self.assertEqual(
        {
            "account": "fixture-admin",
            "password": "fixture-password",
        },
        legacy_args.login_payload,
    )

  def test_build_legacy_args_uses_runtime_fixture_env_defaults(self):
    manifest = {
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "appRoot": "tmp/integration-app-root/sample-app",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
            "defaultExecutionRing": "metadata-list",
        },
    }
    cli_args = self.runner.parse_args([])
    runtime_inputs = {
        "develop": {"baseUrl": "http://127.0.0.1:8080"},
        "mothership": {"baseUrl": "http://127.0.0.1:8081"},
    }

    legacy_args = self.runner.build_legacy_args(manifest, cli_args, runtime_inputs=runtime_inputs)

    self.assertEqual("http://127.0.0.1:8080", legacy_args.base_url)
    self.assertEqual("http://127.0.0.1:8081", legacy_args.app_base_url)

  def test_build_legacy_args_honors_cli_overrides(self):
    manifest = {
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "appRoot": "tmp/integration-app-root/sample-app",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
        },
    }
    cli_args = self.runner.parse_args([
        "--mode", "execute",
        "--base-url", "http://127.0.0.1:8080",
        "--app-root", "tmp/custom-app-root",
        "--report-dir", "tmp/custom-report-dir",
        "--docs-report-dir", "tmp/custom-docs-reports",
        "--execution-ring", "metadata-update",
    ])

    legacy_args = self.runner.build_legacy_args(manifest, cli_args)

    self.assertTrue(legacy_args.execute)
    self.assertFalse(legacy_args.inventory_only)
    self.assertEqual("http://127.0.0.1:8080", legacy_args.base_url)
    self.assertEqual(str(ROOT / "tmp/custom-app-root"), legacy_args.app_root)
    self.assertEqual(str(ROOT / "tmp/custom-report-dir"), legacy_args.report_dir)
    self.assertEqual(str(ROOT / "tmp/custom-docs-reports"), legacy_args.docs_report_dir)
    self.assertEqual("metadata-update", legacy_args.execution_ring)

  def test_main_requires_base_url_for_execute_mode(self):
    manifest = {
        "fixtures": {
            "services": [],
            "runtimes": [],
            "frontends": [],
            "data": [],
            "identities": [],
        },
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "appRoot": "tmp/integration-app-root/sample-app",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
        },
    }

    with tempfile.TemporaryDirectory() as tmp_dir:
      scenario_path = Path(tmp_dir) / "scenario.yaml"
      scenario_path.write_text(json.dumps(manifest))
      with self.assertRaises(SystemExit) as context:
        self.runner.main(["--scenario", str(scenario_path), "--mode", "execute"])

    self.assertEqual("--base-url is required when --mode execute", str(context.exception))

  def test_main_requires_app_root_or_repo_backed_fixture_input(self):
    manifest = {
        "fixtures": {
            "services": [],
            "runtimes": [],
            "frontends": [],
            "data": [],
            "identities": [],
        },
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
        },
    }

    with tempfile.TemporaryDirectory() as tmp_dir:
      scenario_path = Path(tmp_dir) / "scenario.yaml"
      scenario_path.write_text(json.dumps(manifest))
      with self.assertRaises(SystemExit) as context:
        self.runner.main(["--scenario", str(scenario_path)])

    self.assertEqual(
        "app root is required; provide --app-root, --repo app-metadata=/abs/path, or configure a data fixture input source",
        str(context.exception),
    )


  def test_main_loads_manifest_and_delegates_to_legacy_runner(self):
    manifest = {
        "fixtures": {
            "services": [],
            "runtimes": [],
            "frontends": [],
            "data": [],
            "identities": [],
        },
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "appRoot": "tmp/integration-app-root/sample-app",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
            "defaultExecutionRing": "metadata-list",
            "supportedExecutionRings": ["metadata-list", "high-code-read"],
        },
    }

    with tempfile.TemporaryDirectory() as tmp_dir:
      scenario_path = Path(tmp_dir) / "scenario.yaml"
      scenario_path.write_text(json.dumps(manifest))
      legacy_module = mock.Mock()
      legacy_module.run.return_value = 0

      with mock.patch("testing.common.runners.api.metadata_high_code_data_api_regression.importlib.import_module", return_value=legacy_module):
        exit_code = self.runner.main(["--scenario", str(scenario_path)])

    self.assertEqual(0, exit_code)
    delegated_args = legacy_module.run.call_args[0][0]
    self.assertTrue(delegated_args.inventory_only)
    self.assertEqual(str(ROOT / "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs"), delegated_args.docs_report_dir)
    self.assertEqual({"services": [], "runtimes": [], "frontends": [], "data": [], "identities": []}, delegated_args.fixture_bundle)

  def test_main_uses_runtime_fixture_env_defaults_when_cli_missing(self):
    manifest = {
        "fixtures": {
            "services": [],
            "runtimes": ["develop", "mothership"],
            "frontends": [],
            "data": [],
            "identities": [],
        },
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "appRoot": "tmp/integration-app-root/sample-app",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
            "defaultExecutionRing": "metadata-list",
        },
    }

    with tempfile.TemporaryDirectory() as tmp_dir:
      scenario_path = Path(tmp_dir) / "scenario.yaml"
      scenario_path.write_text(json.dumps(manifest))
      legacy_module = mock.Mock()
      legacy_module.run.return_value = 0

      with mock.patch.dict(
          os.environ,
          {
              "OB_TEST_BASE_URL": "http://127.0.0.1:8080",
              "OB_TEST_APP_BASE_URL": "http://127.0.0.1:8081",
          },
          clear=False,
      ):
        with mock.patch("testing.common.runners.api.metadata_high_code_data_api_regression.importlib.import_module", return_value=legacy_module):
          exit_code = self.runner.main(["--scenario", str(scenario_path)])

    self.assertEqual(0, exit_code)
    delegated_args = legacy_module.run.call_args[0][0]
    self.assertEqual("http://127.0.0.1:8080", delegated_args.base_url)
    self.assertEqual("http://127.0.0.1:8081", delegated_args.app_base_url)

  def test_main_rejects_unsupported_execution_ring(self):
    manifest = {
        "fixtures": {
            "services": [],
            "runtimes": [],
            "frontends": [],
            "data": [],
            "identities": [],
        },
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "appRoot": "tmp/integration-app-root/sample-app",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
            "defaultExecutionRing": "metadata-list",
            "supportedExecutionRings": ["metadata-list", "high-code-read"],
        },
    }

    with tempfile.TemporaryDirectory() as tmp_dir:
      scenario_path = Path(tmp_dir) / "scenario.yaml"
      scenario_path.write_text(json.dumps(manifest))

      with self.assertRaises(SystemExit) as context:
        self.runner.main([
            "--scenario", str(scenario_path),
            "--mode", "execute",
            "--execution-ring", "high-code-special-read",
            "--base-url", "http://127.0.0.1:8080",
        ])

    self.assertEqual("unsupported execution ring: high-code-special-read", str(context.exception))

  def test_main_fails_when_scenario_references_missing_fixture(self):
    manifest = {
        "fixtures": {
            "runtimes": ["missing-runtime"],
        },
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "appRoot": "tmp/integration-app-root/sample-app",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
        },
    }

    with tempfile.TemporaryDirectory() as tmp_dir:
      scenario_path = Path(tmp_dir) / "scenario.yaml"
      scenario_path.write_text(json.dumps(manifest))

      with self.assertRaises(SystemExit) as context:
        self.runner.main(["--scenario", str(scenario_path)])

    self.assertEqual("missing fixture manifest: runtimes/missing-runtime", str(context.exception))

  def test_main_fails_when_fixture_preflight_detects_missing_data_asset(self):
    manifest = {
        "fixtures": {
            "services": [],
            "runtimes": [],
            "frontends": [],
            "data": ["broken-app-root"],
            "identities": [],
        },
        "legacyBridge": {
            "module": "testing.common.runners.api.data_api_regression.run_regression",
            "docsReportDir": "tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs",
            "evidenceDir": "tmp/testing-evidence/metadata-high-code-data-api-regression",
        },
    }

    with tempfile.TemporaryDirectory() as tmp_dir:
      scenario_path = Path(tmp_dir) / "scenario.yaml"
      scenario_path.write_text(json.dumps(manifest))
      fixture_root = Path(tmp_dir) / "fixtures"
      fixture_dir = fixture_root / "data/broken-app-root"
      fixture_dir.mkdir(parents=True)
      (fixture_dir / "fixture.yaml").write_text(
          json.dumps(
              {
                  "id": "data/broken-app-root",
                  "title": "Broken App Root",
                  "class": "data",
                  "outputs": [
                      {"name": "appRoot", "value": "tmp/does-not-exist"},
                  ],
              }
          )
      )

      with mock.patch("testing.common.runners.fixture_registry.FIXTURES_ROOT", fixture_root):
        with self.assertRaises(SystemExit) as context:
          self.runner.main(["--scenario", str(scenario_path)])

    self.assertIn("fixture preflight failed:", str(context.exception))
    self.assertIn("data/broken-app-root", str(context.exception))


if __name__ == "__main__":
  unittest.main()

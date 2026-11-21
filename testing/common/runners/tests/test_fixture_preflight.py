import importlib
import json
import sys
import tempfile
import unittest
from unittest import mock
from pathlib import Path
from types import SimpleNamespace


ROOT = Path(__file__).resolve().parents[4]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))


class FixturePreflightTest(unittest.TestCase):

  def setUp(self):
    self.preflight = importlib.import_module("testing.common.runners.fixture_preflight")

  def test_resolve_runtime_inputs_reads_fixture_env_defaults(self):
    fixture_bundle = {
        "runtimes": [
            {
                "id": "runtimes/develop",
                "outputs": [
                    {"name": "runtimeKind", "value": "develop"},
                    {"name": "baseUrlEnv", "value": "OB_TEST_BASE_URL"},
                ],
            },
            {
                "id": "runtimes/mothership",
                "outputs": [
                    {"name": "runtimeKind", "value": "mothership"},
                    {"name": "baseUrlEnv", "value": "OB_TEST_APP_BASE_URL"},
                ],
            },
        ]
    }
    cli_args = SimpleNamespace(base_url=None, app_base_url=None)

    runtime_inputs = self.preflight.resolve_runtime_inputs(
        fixture_bundle,
        cli_args,
        {
            "OB_TEST_BASE_URL": "http://127.0.0.1:8080",
            "OB_TEST_APP_BASE_URL": "http://127.0.0.1:8081",
        },
    )

    self.assertEqual("http://127.0.0.1:8080", runtime_inputs["develop"]["baseUrl"])
    self.assertEqual("http://127.0.0.1:8081", runtime_inputs["mothership"]["baseUrl"])

  def test_resolve_frontend_inputs_reads_fixture_env_defaults(self):
    fixture_bundle = {
        "frontends": [
            {
                "id": "frontends/develop-shell",
                "outputs": [
                    {"name": "frontendKind", "value": "develop-shell"},
                    {"name": "baseUrlEnv", "value": "OB_TEST_FRONTEND_BASE_URL"},
                ],
            }
        ]
    }

    frontend_inputs = self.preflight.resolve_frontend_inputs(
        fixture_bundle,
        {
            "OB_TEST_FRONTEND_BASE_URL": "http://127.0.0.1:8090",
        },
    )

    self.assertEqual("http://127.0.0.1:8090", frontend_inputs["develop-shell"]["baseUrl"])

  def test_preflight_fixture_bundle_detects_missing_data_paths(self):
    reports = self.preflight.preflight_fixture_bundle(
        {
            "data": [
                {
                    "id": "data/sample-app-root",
                    "class": "data",
                    "outputs": [
                        {"name": "appRoot", "value": "tmp/not-real-app-root"},
                    ],
                }
            ]
        }
    )

    failures = self.preflight.collect_fixture_preflight_failures(reports)

    self.assertEqual("fail", reports[0]["status"])
    self.assertEqual("data/sample-app-root", failures[0]["fixture_id"])
    self.assertIn("does not exist", failures[0]["message"])

  def test_preflight_fixture_bundle_resolves_required_data_input_from_env_and_derives_paths(self):
    with tempfile.TemporaryDirectory() as temp_dir:
      app_root = Path(temp_dir) / "sample-app"
      (app_root / "business-model").mkdir(parents=True)
      (app_root / "ui-schema").mkdir(parents=True)

      reports = self.preflight.preflight_fixture_bundle(
          {
              "data": [
                  {
                      "id": "data/app-metadata-root",
                      "class": "data",
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
                          {"name": "appRoot", "fromInput": "appRoot"},
                          {"name": "businessModelDir", "fromOutput": "appRoot", "join": "business-model"},
                          {"name": "uiSchemaDir", "fromOutput": "appRoot", "join": "ui-schema"},
                      ],
                  }
              ]
          },
          env={"OB_TEST_APP_ROOT": str(app_root)},
      )

    self.assertEqual("pass", reports[0]["status"])
    check_names = [check["name"] for check in reports[0]["checks"]]
    self.assertIn("input.appRoot.resolved", check_names)
    self.assertIn("data.businessModelDir.exists", check_names)
    self.assertIn("data.uiSchemaDir.exists", check_names)

  def test_preflight_fixture_bundle_resolves_required_data_input_from_repo_binding(self):
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = Path(temp_dir) / "metadata-repo"
      app_root = repo_root / "apps/sample-app"
      (app_root / "business-model").mkdir(parents=True)
      (app_root / "ui-schema").mkdir(parents=True)

      reports = self.preflight.preflight_fixture_bundle(
          {
              "data": [
                  {
                      "id": "data/app-metadata-root",
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
                  }
              ]
          },
          cli_args=SimpleNamespace(app_root=None, repo=[f"app-metadata={repo_root}"]),
      )

    self.assertEqual("pass", reports[0]["status"])
    check_names = [check["name"] for check in reports[0]["checks"]]
    self.assertIn("input.appRoot.resolved", check_names)
    resolution_message = next(
        check["message"]
        for check in reports[0]["checks"]
        if check["name"] == "input.appRoot.resolved"
    )
    self.assertEqual("appRoot resolved from --repo app-metadata=PATH", resolution_message)
    self.assertIn("data.appRoot.exists", check_names)
    self.assertIn("data.businessModelDir.exists", check_names)
    self.assertIn("data.uiSchemaDir.exists", check_names)

  def test_preflight_fixture_bundle_accepts_external_identity_injection(self):
    reports = self.preflight.preflight_fixture_bundle(
        {
            "identities": [
                {
                    "id": "identities/permission-boundary-user",
                    "class": "identities",
                    "outputs": [
                        {"name": "identityKind", "value": "permission-boundary"},
                    ],
                }
            ]
        }
    )

    self.assertEqual("pass", reports[0]["status"])
    self.assertTrue(any("external credential injection" in note for note in reports[0]["notes"]))

  def test_preflight_fixture_bundle_validates_frontend_shape(self):
    reports = self.preflight.preflight_fixture_bundle(
        {
            "frontends": [
                {
                    "id": "frontends/develop-shell",
                    "class": "frontends",
                    "outputs": [
                        {"name": "frontendKind", "value": "develop-shell"},
                        {"name": "baseUrlEnv", "value": "OB_TEST_FRONTEND_BASE_URL"},
                        {"name": "entryRoute", "value": "/login"},
                    ],
                }
            ]
        },
        frontend_inputs={
            "develop-shell": {
                "frontendKind": "develop-shell",
                "baseUrlEnv": "OB_TEST_FRONTEND_BASE_URL",
                "baseUrl": "http://127.0.0.1:8090",
            }
        },
    )

    self.assertEqual("pass", reports[0]["status"])
    check_names = [check["name"] for check in reports[0]["checks"]]
    self.assertIn("frontend.develop-shell.base-url-env", check_names)
    self.assertIn("frontend.develop-shell.entry-route-shape", check_names)
    self.assertIn("frontend.develop-shell.base-url-shape", check_names)

  def test_preflight_runtime_health_retries_secured_actuator_with_admin_token(self):
    class FakeResponse:
      def __init__(self, status, body=b""):
        self.status = status
        self.body = body

      def __enter__(self):
        return self

      def __exit__(self, exc_type, exc, traceback):
        return False

      def getcode(self):
        return self.status

      def read(self):
        return self.body

    def fake_urlopen(target, timeout=3):
      del timeout
      url = target.full_url if hasattr(target, "full_url") else target
      if url == "http://127.0.0.1:18888/actuator/health":
        if hasattr(target, "get_header") and target.get_header("Authorization") == "Bearer fake-token":
          return FakeResponse(200, b'{"status":"UP"}')
        raise self.preflight.error.HTTPError(url, 401, "Unauthorized", {}, None)
      if url == "http://127.0.0.1:18888/api/auth/login":
        self.assertEqual(
            {"account": "admin", "password": "666666"},
            json.loads(target.data.decode("utf-8")),
        )
        return FakeResponse(200, b'{"data":{"accessToken":{"token":"fake-token"}}}')
      raise AssertionError(f"unexpected urlopen target: {url}")

    with mock.patch.object(self.preflight.request, "urlopen", side_effect=fake_urlopen):
      reports = self.preflight.preflight_fixture_bundle(
          {
              "runtimes": [
                  {
                      "id": "runtimes/develop",
                      "class": "runtimes",
                      "outputs": [
                          {"name": "runtimeKind", "value": "develop"},
                          {"name": "baseUrlEnv", "value": "OB_TEST_BASE_URL"},
                          {"name": "healthProbe", "value": "/actuator/health"},
                      ],
                  }
              ]
          },
          runtime_inputs={
              "develop": {
                  "runtimeKind": "develop",
                  "baseUrlEnv": "OB_TEST_BASE_URL",
                  "baseUrl": "http://127.0.0.1:18888",
              }
          },
      )

    self.assertEqual("pass", reports[0]["status"])
    health_message = next(
        check["message"]
        for check in reports[0]["checks"]
        if check["name"] == "runtime.develop.health-live"
    )
    self.assertIn("authenticated actuator health probe", health_message)

  def test_preflight_runtime_health_uses_injected_admin_token(self):
    class FakeResponse:
      def __enter__(self):
        return self

      def __exit__(self, exc_type, exc, traceback):
        return False

      def getcode(self):
        return 200

      def read(self):
        return b'{"status":"UP"}'

    def fake_urlopen(target, timeout=3):
      del timeout
      url = target.full_url if hasattr(target, "full_url") else target
      if url == "http://127.0.0.1:18888/actuator/health":
        if target.get_header("Authorization") is None:
          raise self.preflight.error.HTTPError(url, 401, "Unauthorized", {}, None)
        self.assertEqual("Bearer injected-token", target.get_header("Authorization"))
        return FakeResponse()
      raise AssertionError(f"unexpected urlopen target: {url}")

    with mock.patch.object(self.preflight.request, "urlopen", side_effect=fake_urlopen):
      reports = self.preflight.preflight_fixture_bundle(
          {
              "runtimes": [
                  {
                      "id": "runtimes/develop",
                      "class": "runtimes",
                      "outputs": [
                          {"name": "runtimeKind", "value": "develop"},
                          {"name": "baseUrlEnv", "value": "OB_TEST_BASE_URL"},
                          {"name": "healthProbe", "value": "/actuator/health"},
                      ],
                  }
              ]
          },
          runtime_inputs={
              "develop": {
                  "runtimeKind": "develop",
                  "baseUrlEnv": "OB_TEST_BASE_URL",
                  "baseUrl": "http://127.0.0.1:18888",
              }
          },
          env={"OB_TEST_ADMIN_ACCESS_TOKEN": "injected-token"},
      )

    self.assertEqual("pass", reports[0]["status"])

  def test_preflight_runtime_health_reports_structured_login_failure(self):
    class FakeResponse:
      def __enter__(self):
        return self

      def __exit__(self, exc_type, exc, traceback):
        return False

      def getcode(self):
        return 200

      def read(self):
        return '{"code":-1,"msg":"用户名或密码错误","data":null}'.encode("utf-8")

    def fake_urlopen(target, timeout=3):
      del timeout
      url = target.full_url if hasattr(target, "full_url") else target
      if url == "http://127.0.0.1:18888/actuator/health":
        raise self.preflight.error.HTTPError(url, 401, "Unauthorized", {}, None)
      if url == "http://127.0.0.1:18888/api/auth/login":
        return FakeResponse()
      raise AssertionError(f"unexpected urlopen target: {url}")

    with mock.patch.object(self.preflight.request, "urlopen", side_effect=fake_urlopen):
      reports = self.preflight.preflight_fixture_bundle(
          {
              "runtimes": [
                  {
                      "id": "runtimes/develop",
                      "class": "runtimes",
                      "outputs": [
                          {"name": "runtimeKind", "value": "develop"},
                          {"name": "baseUrlEnv", "value": "OB_TEST_BASE_URL"},
                          {"name": "healthProbe", "value": "/actuator/health"},
                      ],
                  }
              ]
          },
          runtime_inputs={
              "develop": {
                  "runtimeKind": "develop",
                  "baseUrlEnv": "OB_TEST_BASE_URL",
                  "baseUrl": "http://127.0.0.1:18888",
              }
          },
      )

    self.assertEqual("fail", reports[0]["status"])
    health_message = next(
        check["message"]
        for check in reports[0]["checks"]
        if check["name"] == "runtime.develop.health-live"
    )
    self.assertIn("login failed", health_message)
    self.assertIn("用户名或密码错误", health_message)

  def test_write_fixture_preflight_report_creates_standard_json_artifact(self):
    reports = [
        {
            "fixture_id": "runtimes/develop",
            "class": "runtimes",
            "title": "Develop Runtime",
            "status": "pass",
            "checks": [],
            "notes": [],
        }
    ]

    with tempfile.TemporaryDirectory() as tmp_dir:
      output_path = self.preflight.write_fixture_preflight_report(reports, tmp_dir)
      payload = json.loads(output_path.read_text())

    self.assertEqual("fixture-preflight.json", output_path.name)
    self.assertEqual(tmp_dir, payload["reportDir"])
    self.assertEqual("runtimes/develop", payload["reports"][0]["fixture_id"])


if __name__ == "__main__":
  unittest.main()

import importlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock


ROOT = Path(__file__).resolve().parents[4]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))


class FixtureRegistryTest(unittest.TestCase):

  def setUp(self):
    self.registry = importlib.import_module("testing.common.runners.fixture_registry")

  def test_load_fixture_manifest_reads_and_validates_fixture(self):
    with tempfile.TemporaryDirectory() as temp_dir:
      fixtures_root = Path(temp_dir)
      fixture_dir = fixtures_root / "runtimes/develop"
      fixture_dir.mkdir(parents=True)
      (fixture_dir / "fixture.yaml").write_text(
          json.dumps(
              {
                  "id": "runtimes/develop",
                  "title": "Develop Runtime",
                  "class": "runtimes",
                  "outputs": [
                      {
                          "name": "runtimeKind",
                          "value": "develop",
                      }
                  ],
              }
          )
      )

      with mock.patch.object(self.registry, "FIXTURES_ROOT", fixtures_root):
        manifest = self.registry.load_fixture_manifest("runtimes", "develop")

    self.assertEqual("runtimes/develop", manifest["id"])
    self.assertEqual("develop", self.registry.fixture_output_value(manifest, "runtimeKind"))
    self.assertTrue(manifest["manifestPath"].endswith("runtimes/develop/fixture.yaml"))

  def test_load_fixture_manifest_fails_for_missing_fixture(self):
    with tempfile.TemporaryDirectory() as temp_dir:
      fixtures_root = Path(temp_dir)
      with mock.patch.object(self.registry, "FIXTURES_ROOT", fixtures_root):
        with self.assertRaises(FileNotFoundError) as context:
          self.registry.load_fixture_manifest("runtimes", "missing")

    self.assertEqual("missing fixture manifest: runtimes/missing", str(context.exception))

  def test_resolve_scenario_fixture_bundle_groups_manifests_by_class(self):
    with tempfile.TemporaryDirectory() as temp_dir:
      fixtures_root = Path(temp_dir)
      for fixture_class, fixture_name, outputs in (
          ("runtimes", "develop", [{"name": "runtimeKind", "value": "develop"}]),
          ("data", "app-metadata-root", [{"name": "appRoot", "value": "tmp/integration-app-root/sample-app"}]),
      ):
        fixture_dir = fixtures_root / fixture_class / fixture_name
        fixture_dir.mkdir(parents=True)
        (fixture_dir / "fixture.yaml").write_text(
            json.dumps(
                {
                    "id": f"{fixture_class}/{fixture_name}",
                    "title": fixture_name,
                    "class": fixture_class,
                    "outputs": outputs,
                }
            )
        )

      with mock.patch.object(self.registry, "FIXTURES_ROOT", fixtures_root):
        bundle = self.registry.resolve_scenario_fixture_bundle(
            {
                "fixtures": {
                    "runtimes": ["develop"],
                    "data": ["app-metadata-root"],
                }
            }
        )

    self.assertEqual("develop", self.registry.first_fixture_output(bundle, "runtimes", "runtimeKind"))
    self.assertEqual(
        "tmp/integration-app-root/sample-app",
        self.registry.first_fixture_output(bundle, "data", "appRoot"),
    )

  def test_fixture_output_value_can_resolve_input_sources_and_derived_outputs(self):
    fixture_manifest = {
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
        ],
    }

    self.assertEqual(
        "/tmp/sample-app",
        self.registry.fixture_output_value(
            fixture_manifest,
            "appRoot",
            env={"OB_TEST_APP_ROOT": "/tmp/sample-app"},
        ),
    )
    self.assertEqual(
        "/tmp/sample-app/business-model",
        self.registry.fixture_output_value(
            fixture_manifest,
            "businessModelDir",
            env={"OB_TEST_APP_ROOT": "/tmp/sample-app"},
        ),
    )

  def test_fixture_output_value_can_resolve_repo_bound_input_sources(self):
    fixture_manifest = {
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
        ],
    }

    cli_args = SimpleNamespace(repo=["app-metadata=/repos/metadata"], app_root=None)
    self.assertEqual(
        "/repos/metadata/apps/sample-app",
        self.registry.fixture_output_value(
            fixture_manifest,
            "appRoot",
            cli_args=cli_args,
        ),
    )
    self.assertEqual(
        "/repos/metadata/apps/sample-app/business-model",
        self.registry.fixture_output_value(
            fixture_manifest,
            "businessModelDir",
            cli_args=cli_args,
        ),
    )

  def test_resolve_repository_bindings_merges_env_and_cli_inputs(self):
    cli_args = SimpleNamespace(
        repo=[
            "app-metadata=/repos/metadata",
            "platform=/repos/platform-worktree",
        ]
    )

    bindings = self.registry.resolve_repository_bindings(
        cli_args=cli_args,
        env={
            "OB_TEST_REPOS": json.dumps(
                {
                    "app-metadata": "/repos/stale-metadata",
                    "support": "/repos/supporting-assets",
                }
            )
        },
    )

    self.assertEqual(
        {
            "app-metadata": "/repos/metadata",
            "platform": "/repos/platform-worktree",
            "support": "/repos/supporting-assets",
        },
        bindings,
    )


if __name__ == "__main__":
  unittest.main()

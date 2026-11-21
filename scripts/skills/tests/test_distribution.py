"""Exercise generated distributions, drift detection and standalone consumer validation."""

import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("skill_distribution", ROOT / "scripts/skills/sync_distribution.py")
distribution = importlib.util.module_from_spec(spec)
spec.loader.exec_module(distribution)


class DistributionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="portable skills ")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        source = self.root / distribution.SCHEMAS
        shutil.copytree(ROOT / distribution.SCHEMAS, source)
        fixtures = Path("testing/common/fixtures/data/metadata-schema-conformance/cases.json")
        (self.root / fixtures).parent.mkdir(parents=True)
        shutil.copy2(ROOT / fixtures, self.root / fixtures)
        skill = self.root / distribution.METADATA
        (skill / "scripts").mkdir(parents=True)
        (skill / "SKILL.md").write_text("---\nname: tidestack-metadata-definition\ndescription: Validate metadata\n---\n")
        shutil.copy2(ROOT / distribution.METADATA / "scripts/validate_resource_json.py", skill / "scripts")
        capabilities = self.root / distribution.CAPABILITIES
        capabilities.mkdir(parents=True)
        for name in ("index.json", "catalog.json", "catalog.schema.json"):
            shutil.copy2(ROOT / distribution.CAPABILITIES / name, capabilities / name)
        advisor = self.root / distribution.ADVISOR
        (advisor / "scripts").mkdir(parents=True)
        (advisor / "SKILL.md").write_text("---\nname: tidestack-howto\ndescription: Read-only advisor\n---\n")
        shutil.copy2(ROOT / distribution.ADVISOR / "scripts/query_capabilities.py", advisor / "scripts")
        manifest = self.root / distribution.TEMPLATE / "template.json"
        manifest.parent.mkdir(parents=True)
        manifest.write_text(json.dumps({"operations": [{"kind": "copy", "source": "tree/AGENTS.md", "target": "AGENTS.md"}]}))

    def test_public_schema_is_distributed_to_handbook_ide_and_portable_skills(self):
        name = "approval-form.schema.json"
        source = self.root / distribution.SCHEMAS / name
        self.assertFalse((self.root / distribution.HANDBOOK_SCHEMAS / name).exists())
        distribution.synchronize(self.root, False)
        for directory in (distribution.HANDBOOK_SCHEMAS, distribution.IDE_SCHEMAS):
            self.assertEqual(source.read_bytes(), (self.root / directory / name).read_bytes())
        self.assertEqual([], distribution.synchronize(self.root, True))
        for target in distribution.TARGETS:
            skill = self.root / target / "tidestack-metadata-definition"
            resource = ROOT / "skills/tidestack-approval-integration/examples/purchase.approval-form.json"
            result = subprocess.run([sys.executable, str(skill / "scripts/validate_resource_json.py"),
                                     "--schema", str(skill / "references" / name), "--json", str(resource)],
                                    cwd=self.temporary.name, text=True, capture_output=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_generate_then_check_is_idempotent_and_preserves_non_skill_operation(self):
        self.assertTrue(distribution.synchronize(self.root, False))
        self.assertEqual([], distribution.synchronize(self.root, True))
        manifest = json.loads((self.root / distribution.TEMPLATE / "template.json").read_text())
        self.assertEqual("AGENTS.md", manifest["operations"][0]["target"])
        for operation in manifest["operations"][1:]:
            self.assertTrue((self.root / distribution.TEMPLATE / operation["source"]).is_file())

    def test_check_detects_changed_copy_without_repairing_it(self):
        distribution.synchronize(self.root, False)
        target = self.root / distribution.TARGETS[0] / "tidestack-metadata-definition/SKILL.md"
        target.write_text("local drift")
        self.assertIn(str(target.relative_to(self.root)), distribution.synchronize(self.root, True))
        self.assertEqual("local drift", target.read_text())

    def test_missing_and_unindexed_public_schemas_are_rejected(self):
        schema = self.root / distribution.SCHEMAS / "approval-form.schema.json"
        content = schema.read_bytes()
        schema.unlink()
        with self.assertRaisesRegex(ValueError, "index does not match"):
            distribution.synchronize(self.root, True)
        schema.write_bytes(content)
        (schema.parent / "orphan.schema.json").write_text('{"type":"object"}')
        with self.assertRaisesRegex(ValueError, "index does not match"):
            distribution.synchronize(self.root, True)

    def test_check_detects_handbook_and_ide_drift_and_sync_repairs_them(self):
        distribution.synchronize(self.root, False)
        for directory in (distribution.HANDBOOK_SCHEMAS, distribution.IDE_SCHEMAS):
            target = self.root / directory / "webapi.schema.json"
            target.write_text('{"type":"object"}')
            self.assertIn(str(target.relative_to(self.root)), distribution.synchronize(self.root, True))
        distribution.synchronize(self.root, False)
        self.assertEqual([], distribution.synchronize(self.root, True))

    def test_legacy_copy_removed_but_unknown_directory_is_not_deleted(self):
        legacy_copies = [self.root / target / name / "SKILL.md"
                         for target in distribution.TARGETS
                         for name in ("ouroboros-menu-definition", "tidestack")]
        for legacy in legacy_copies:
            legacy.parent.mkdir(parents=True)
            legacy.write_text("legacy")
        distribution.synchronize(self.root, False)
        for legacy in legacy_copies:
            self.assertFalse(legacy.exists())
        unknown = self.root / distribution.TARGETS[0] / "user-skill/SKILL.md"
        unknown.parent.mkdir()
        unknown.write_text("user data")
        with self.assertRaisesRegex(ValueError, "Unmanaged"):
            distribution.synchronize(self.root, False)
        self.assertEqual("user data", unknown.read_text())

    def test_copied_validator_works_outside_platform_checkout(self):
        distribution.synchronize(self.root, False)
        skill = self.root / distribution.TARGETS[0] / "tidestack-metadata-definition"
        resource = self.root / "order.json"
        resource.write_bytes((ROOT / "skills/tidestack-business-model-development/examples/Order.business.json").read_bytes())
        command = [sys.executable, str(skill / "scripts/validate_resource_json.py"),
                   "--schema", str(skill / "references/business.schema.json"), "--json", str(resource)]
        result = subprocess.run(command, cwd=self.temporary.name, capture_output=True, text=True, timeout=10)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        data = json.loads(resource.read_text())
        data["actions"][0]["binding"] = {"kind": "handler", "flowName": "wrong-target"}
        resource.write_text(json.dumps(data))
        result = subprocess.run(command, capture_output=True, text=True, timeout=10)
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)

    def test_ui_resource_and_content_share_public_provenance_and_validate_offline(self):
        source = self.root / distribution.SCHEMAS / "ui-schema.schema.json"
        distribution.synchronize(self.root, False)
        provenance = json.loads((self.root / distribution.METADATA / "references/schema-sources.json").read_text())
        ui = next(entry for entry in provenance["entries"] if entry["schema"] == source.name)
        self.assertEqual(ui["sourceSha256"], ui["distributedSha256"])
        self.assertEqual(source.read_bytes(), (self.root / distribution.METADATA / "references" / source.name).read_bytes())
        skill = self.root / distribution.TARGETS[0] / "tidestack-metadata-definition"
        resource = self.root / "orders.ui-schema.json"
        command = [sys.executable, str(skill / "scripts/validate_resource_json.py"),
                   "--schema", str(skill / "references" / source.name), "--json", str(resource)]
        for data, expected in (({"name": "orders", "schema": [{"type": "input-text", "name": "title"}]}, 0),
                               ({"type": "page", "body": []}, 1)):
            resource.write_text(json.dumps(data))
            result = subprocess.run(command, cwd=self.temporary.name, capture_output=True, text=True, timeout=10)
            self.assertEqual(expected, result.returncode, result.stdout + result.stderr)

    def test_portable_validator_matches_shared_public_contract_cases(self):
        distribution.synchronize(self.root, False)
        skill = self.root / distribution.TARGETS[0] / "tidestack-metadata-definition"
        validator_spec = importlib.util.spec_from_file_location("portable_validator", skill / "scripts/validate_resource_json.py")
        validator = importlib.util.module_from_spec(validator_spec)
        validator_spec.loader.exec_module(validator)
        examples = json.loads((ROOT / "testing/common/fixtures/data/metadata-schema-conformance/cases.json").read_text())
        for example in examples:
            with self.subTest(case=example["id"]):
                schema_type = "ui-schema-content" if "/ui-schema/" in example.get("path", "") else example["type"]
                schema_path = skill / "references" / (schema_type + ".schema.json")
                errors = validator.validate_with_jsonschema(json.loads(schema_path.read_text()), example["document"], schema_path)
                self.assertIsNotNone(errors, "Full JSON Schema validation must be installed")
                self.assertEqual(example["valid"], not errors, errors)

    def test_capability_drift_tracks_authoritative_source_without_rewriting_check(self):
        distribution.synchronize(self.root, False)
        source = self.root / distribution.CAPABILITIES / "index.json"
        data = json.loads(source.read_text())
        data["capabilities"][0]["summary"] = "Updated authoritative summary"
        source.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
        copied = self.root / distribution.TARGETS[0] / "tidestack-howto/references/index.json"
        previous = copied.read_bytes()
        changes = distribution.synchronize(self.root, True)
        self.assertIn(str(copied.relative_to(self.root)), changes)
        self.assertEqual(previous, copied.read_bytes())
        distribution.synchronize(self.root, False)
        self.assertEqual(source.read_bytes(), copied.read_bytes())
        provenance = json.loads((self.root / distribution.ADVISOR / "references/capability-sources.json").read_text())
        entry = next(x for x in provenance["entries"] if x["source"].endswith("/index.json"))
        self.assertEqual(distribution.digest(source.read_bytes()), entry["sha256"])
        self.assertFalse(provenance["source"]["runtimeVerified"])

    def test_runtime_contract_additions_survive_handbook_and_template_sync(self):
        name = "rpc.schema.json"
        handbook = self.root / distribution.HANDBOOK_SCHEMAS / name
        handbook.parent.mkdir(parents=True, exist_ok=True)
        handbook.write_text('{"type":"object"}')
        runtime = self.root / distribution.SCHEMAS / name
        expected = runtime.read_bytes()
        distribution.synchronize(self.root, False)
        self.assertEqual(expected, runtime.read_bytes())
        copied_skill = self.root / distribution.TARGETS[0] / "tidestack-metadata-definition"
        self.assertEqual(expected, (copied_skill / "references" / name).read_bytes())
        provenance = json.loads((copied_skill / "references/schema-sources.json").read_text())
        entry = next(item for item in provenance["entries"] if item["schema"] == name)
        self.assertEqual(str(distribution.SCHEMAS / name), entry["source"])
        self.assertEqual(expected, handbook.read_bytes())
        resource = self.root / "call.rpc.json"
        call = {"name": "echo", "title": "Echo", "type": "http",
                "timeout": "#{external.timeout}", "inputSchema": {"type": "object"}}
        command = [sys.executable, str(copied_skill / "scripts/validate_resource_json.py"),
                   "--schema", str(copied_skill / "references" / name), "--json", str(resource)]
        for changes, expected_status in (({}, 0), ({"inputSchema": []}, 1), ({"timeout": {}}, 1)):
            with self.subTest(changes=changes):
                resource.write_text(json.dumps({**call, **changes}))
                result = subprocess.run(command, capture_output=True, text=True, timeout=10)
                self.assertEqual(expected_status, result.returncode, result.stdout + result.stderr)
        self.assertEqual([], distribution.synchronize(self.root, True))

    def test_portable_lookup_reads_only_requested_tier_and_rejects_unknown_id(self):
        distribution.synchronize(self.root, False)
        skill = self.root / distribution.TARGETS[0] / "tidestack-howto"
        command = [sys.executable, str(skill / "scripts/query_capabilities.py")]
        catalog_path = skill / "references/catalog.json"
        catalog = catalog_path.read_bytes()
        catalog_path.unlink()
        search = subprocess.run(command + ["--search", "integration", "--limit", "2"],
                                cwd=self.temporary.name, capture_output=True, text=True, timeout=10)
        self.assertEqual(0, search.returncode, search.stderr)
        result = json.loads(search.stdout)
        self.assertGreater(result["matched"], 2)
        self.assertEqual(2, len(result["capabilities"]))
        self.assertNotIn("sources", result["capabilities"][0])
        catalog_path.write_bytes(catalog)
        (skill / "references/index.json").unlink()
        detail = subprocess.run(command + ["--id", "be.integration.delayed", "--id", "be.business.actions"],
                                cwd=self.temporary.name, capture_output=True, text=True, timeout=10)
        self.assertEqual(0, detail.returncode, detail.stderr)
        result = json.loads(detail.stdout)
        self.assertEqual(["be.integration.delayed", "be.business.actions"], [x["id"] for x in result["capabilities"]])
        self.assertTrue(result["capabilities"][0]["requires"])
        self.assertFalse(result["source"]["runtimeVerified"])
        missing = subprocess.run(command + ["--id", "be.invalid.capability"],
                                 capture_output=True, text=True, timeout=10)
        self.assertEqual(1, missing.returncode)
        self.assertIn("be.invalid.capability", missing.stderr)
        self.assertEqual("", missing.stdout)


if __name__ == "__main__":
    unittest.main()

"""Full validation rejects constraints outside the explicitly selected subset."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


DIRECTORY = Path(__file__).resolve().parents[1]


class ValidationModesTest(unittest.TestCase):
    def run_validator(self, command, value, *options):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            schema = root / "schema.json"
            resource = root / "resource.json"
            schema.write_text(json.dumps({"$schema": "https://json-schema.org/draft/2020-12/schema",
                                          "type": "integer", "multipleOf": 10}))
            resource.write_text(json.dumps(value))
            return subprocess.run([*command, "--schema", str(schema), "--json", str(resource), *options],
                                  capture_output=True, text=True, timeout=10)

    def test_full_python_validation_rejects_out_of_range_value(self):
        result = self.run_validator([sys.executable, str(DIRECTORY / "validate_resource_json.py")], 5)
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("multiple of", result.stderr)

    def test_full_python_validation_accepts_valid_value(self):
        result = self.run_validator([sys.executable, str(DIRECTORY / "validate_resource_json.py")], 10)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Draft 2020-12 validation passed", result.stdout)

    def test_missing_python_dependency_does_not_succeed(self):
        result = self.run_validator([sys.executable, "-S", "-E", str(DIRECTORY / "validate_resource_json.py")], 5)
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertIn("requires jsonschema", result.stderr)

    def test_node_requires_explicit_subset_selection(self):
        command = ["node", str(DIRECTORY / "validate_resource_json.js")]
        result = self.run_validator(command, 5)
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        subset = self.run_validator(command, 5, "--subset")
        self.assertEqual(0, subset.returncode, subset.stdout + subset.stderr)
        self.assertIn("complete Draft 2020-12 validation NOT performed", subset.stdout)

    def test_local_reference_resolves_by_filename_and_canonical_id(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            schema = root / "resource.schema.json"
            content = root / "content.schema.json"
            resource = root / "resource.json"
            content.write_text(json.dumps({"$id": "https://example.invalid/content.schema.json",
                                           "type": "integer", "minimum": 10}))
            for ref in ("content.schema.json", "https://example.invalid/content.schema.json"):
                schema.write_text(json.dumps({"$ref": ref}))
                for value, expected in ((10, 0), (5, 1)):
                    with self.subTest(ref=ref, value=value):
                        resource.write_text(json.dumps(value))
                        result = subprocess.run([sys.executable, str(DIRECTORY / "validate_resource_json.py"),
                                                 "--schema", str(schema), "--json", str(resource)],
                                                capture_output=True, text=True, timeout=10)
                        self.assertEqual(expected, result.returncode, result.stdout + result.stderr)
            schema.write_text(json.dumps({"$ref": "https://example.invalid/not-installed.schema.json"}))
            result = subprocess.run([sys.executable, str(DIRECTORY / "validate_resource_json.py"),
                                     "--schema", str(schema), "--json", str(resource)],
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)

    def test_packaged_ui_schema_validates_envelope_and_inner_content(self):
        schema = DIRECTORY.parent / "references/ui-schema.schema.json"
        with tempfile.TemporaryDirectory() as temporary:
            resource = Path(temporary) / "page.ui-schema.json"
            valid = {"name": "order", "schema": {"type": "form", "title": ""}}
            for value, expected in ((valid, 0),
                                    ({**valid, "schema": [{"type": "tpl", "tpl": "Order"}]}, 0),
                                    ({**valid, "name": "  "}, 1),
                                    ({**valid, "schema": None}, 1),
                                    ({**valid, "schema": {"type": False}}, 1)):
                with self.subTest(value=value):
                    resource.write_text(json.dumps(value))
                    result = subprocess.run([sys.executable, str(DIRECTORY / "validate_resource_json.py"),
                                             "--schema", str(schema), "--json", str(resource)],
                                            capture_output=True, text=True, timeout=10)
                    self.assertEqual(expected, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()

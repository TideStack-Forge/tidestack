from __future__ import annotations

import importlib.util
import os
import tempfile
import unittest
from pathlib import Path


TOOLS_DIR = Path(__file__).resolve().parents[1]
os.environ["PYTHONDONTWRITEBYTECODE"] = "1"


def load_module():
    spec = importlib.util.spec_from_file_location(
        "validate_yaml_test",
        TOOLS_DIR / "validate-yaml.py",
    )
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot load validate-yaml.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


VALIDATE_YAML = load_module()


class DuplicateKeyValidationTest(unittest.TestCase):

    def validate_text(self, text: str):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.yaml"
            path.write_text(text, encoding="utf-8")
            return VALIDATE_YAML.validate_yaml_file(path)

    def test_duplicate_key_in_nested_mapping_fails(self):
        result = self.validate_text(
            """
items:
  - id: boot35-toolchain-baseline
    notes: first
    notes: second
"""
        )

        self.assertFalse(result.ok)
        self.assertIn("Duplicate key 'notes'", "\n".join(result.errors))

    def test_list_items_have_independent_mapping_scopes(self):
        result = self.validate_text(
            """
items:
  - id: one
    notes: first
  - id: two
    notes: second
"""
        )

        self.assertTrue(result.ok, result.errors)

    def test_json_style_list_objects_have_independent_mapping_scopes(self):
        result = self.validate_text(
            """
{
  "items": [
    {
      "id": "one",
      "notes": "first"
    },
    {
      "id": "two",
      "notes": "second"
    }
  ]
}
"""
        )

        self.assertTrue(result.ok, result.errors)

    def test_duplicate_key_in_json_style_object_fails(self):
        result = self.validate_text(
            """
{
  "items": [
    {
      "id": "one",
      "id": "two"
    }
  ]
}
"""
        )

        self.assertFalse(result.ok)
        self.assertIn("Duplicate key 'id'", "\n".join(result.errors))


if __name__ == "__main__":
    unittest.main()

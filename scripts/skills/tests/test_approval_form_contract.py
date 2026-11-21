"""Executable contracts for custom approval form authoring and IDE discovery."""
import copy
import json
from pathlib import Path
import subprocess
import unittest

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[3]


class ApprovalFormContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads((ROOT / 'metadata-schema/approval-form.schema.json').read_text())
        Draft202012Validator.check_schema(cls.schema)
        cls.validator = Draft202012Validator(cls.schema)
        cls.example = json.loads((ROOT / 'skills/tidestack-approval-integration/examples/purchase.approval-form.json').read_text())

    def test_examples_empty_fields_and_open_extension_types_are_structurally_valid(self):
        self.validator.validate(self.example)
        minimal = {'formatVersion': 1, 'name': 'empty', 'title': '空字段目录', 'fields': []}
        self.validator.validate(minimal)
        extension = copy.deepcopy(self.example)
        extension['fields'][0]['type'] = '应用扩展字段类型'
        extension['fields'][0].pop('params', None)
        self.validator.validate(extension)

    def test_rejects_wrong_versions_required_fields_unknown_attributes_and_parameter_shapes(self):
        invalids = []
        for key in ('formatVersion', 'name', 'title', 'fields'):
            value = copy.deepcopy(self.example)
            del value[key]
            invalids.append(value)
        for key, value in [('formatVersion', 2), ('formatVersion', '1'), ('name', '../bad'), ('title', ' '), ('fields', {}), ('unknown', True)]:
            item = copy.deepcopy(self.example)
            item[key] = value
            invalids.append(item)
        for key, value in [('params', None), ('params', []), ('name', '__internal'), ('name', 'nested.field'), ('type', ''), ('label', 4), ('unknown', True)]:
            item = copy.deepcopy(self.example)
            item['fields'][0][key] = value
            invalids.append(item)
        for item in invalids:
            with self.subTest(item=item):
                self.assertTrue(list(self.validator.iter_errors(item)))

    def test_workflow_binding_remains_open_and_keeps_the_historical_alias(self):
        schema = json.loads((ROOT / 'metadata-schema/approval-workflow.schema.json').read_text())
        validator = Draft202012Validator(schema)
        definition = json.loads((ROOT / 'skills/tidestack-approval-integration/examples/purchase.workflow.json').read_text())
        for form_type in ('custom', 'default', 'business-model', 'third-party'):
            for key in ('formId', 'formKey'):
                definition['form'] = {'formType': form_type, key: 'purchase'}
                validator.validate(definition)

    def test_ide_build_embeds_the_maintained_contract_and_creates_a_valid_description(self):
        subprocess.run(['node', 'ide-plugins/common/metadata-editor-schema/scripts/build.mjs'], cwd=ROOT, check=True, capture_output=True, text=True)
        manifest = json.loads((ROOT / 'ide-plugins/common/metadata-editor-schema/dist/metadataTypeContributions.json').read_text())
        contribution = next(item for item in manifest['types'] if item['type'] == 'approval-form')
        self.assertEqual('.approval-form.json', contribution['fileSuffix'])
        self.assertEqual(self.schema, contribution['jsonSchema'])
        self.validator.validate(contribution['fileTemplate']['document'])
        self.assertIn('approval-form/**/*.approval-form.json', contribution['sourcePathPatterns'])

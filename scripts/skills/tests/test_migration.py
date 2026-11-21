"""Migration is previewable, conflict-safe and respects effective Maven packaging."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET


SCRIPT = Path(__file__).resolve().parents[3] / "skills/tidestack-metadata-migration/scripts/migrate_metadata_resources.py"
EXECUTION = '<build><plugins><plugin><artifactId>maven-resources-plugin</artifactId><executions><execution><id>copy-metadata-sources</id></execution></executions></plugin></plugins></build>'


class MigrationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="metadata migration ")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.old = self.root / "src/main/resources/META-INF/ouroboros/menu-model"
        self.old.mkdir(parents=True)
        (self.old / "a.json").write_text('{"fullName":"sales.orders","title":"订单"}')

    def run_script(self, *options):
        return subprocess.run([sys.executable, str(SCRIPT), "--module", str(self.root), *options],
                              capture_output=True, text=True, timeout=10)

    def test_preview_write_and_second_run(self):
        preview = self.run_script("--skip-pom")
        self.assertEqual(0, preview.returncode, preview.stderr)
        self.assertTrue((self.old / "a.json").exists())
        self.assertFalse((self.root / "src/main/metadata").exists())
        written = self.run_script("--skip-pom", "--write")
        self.assertEqual(0, written.returncode, written.stderr)
        self.assertFalse((self.old / "a.json").exists())
        target = self.root / "src/main/metadata/a.menu.json"
        self.assertEqual("sales.orders", json.loads(target.read_text())["fullName"])
        self.assertEqual(0, self.run_script("--skip-pom", "--write").returncode)

    def test_later_conflict_prevents_all_moves(self):
        (self.old / "z.json").write_text('{"title":"source"}')
        target = self.root / "src/main/metadata/z.menu.json"
        target.parent.mkdir(parents=True)
        target.write_text('{"title":"different"}')
        result = self.run_script("--skip-pom", "--write")
        self.assertNotEqual(0, result.returncode)
        self.assertTrue((self.old / "a.json").exists())
        self.assertTrue((self.old / "z.json").exists())
        self.assertEqual({"title": "different"}, json.loads(target.read_text()))

    def test_effective_inherited_execution_does_not_add_child_execution(self):
        child = '<project xmlns="http://maven.apache.org/POM/4.0.0"><parent><artifactId>parent</artifactId></parent></project>'
        pom = self.root / "pom.xml"
        pom.write_text(child)
        effective = self.root / "effective.xml"
        effective.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0">' + EXECUTION + '</project>')
        result = self.run_script("--effective-pom", str(effective), "--write")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(child, pom.read_text())
        self.assertFalse((self.old / "a.json").exists())

    def test_unresolved_inheritance_fails_before_moving_resources(self):
        (self.root / "pom.xml").write_text('<project><parent><artifactId>parent</artifactId></parent></project>')
        result = self.run_script("--write")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("--effective-pom", result.stderr)
        self.assertTrue((self.old / "a.json").exists())

    def test_comment_does_not_count_as_executable_packaging(self):
        pom = self.root / "pom.xml"
        pom.write_text('<project><!-- copy-metadata-sources --></project>')
        result = self.run_script("--write")
        self.assertEqual(0, result.returncode, result.stderr)
        root = ET.fromstring(pom.read_text())
        self.assertEqual("copy-metadata-sources", root.findtext('build/plugins/plugin/executions/execution/id'))


if __name__ == "__main__":
    unittest.main()

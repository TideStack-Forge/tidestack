#!/usr/bin/env python3
"""Behavior checks for local component exports; all destinations are temporary."""

import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

SCRIPT = Path(__file__).with_name("export-component.py")
spec = importlib.util.spec_from_file_location("component_export", SCRIPT)
export = importlib.util.module_from_spec(spec)
spec.loader.exec_module(export)


class ComponentExportTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="tidestack-export-test-")
        self.addCleanup(self.temporary.cleanup)
        self.target = Path(self.temporary.name) / "mirror"

    def run_export(self, *args, component="script"):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--component", component,
             "--target", str(self.target), *args], capture_output=True, text=True,
        )

    def initialize(self, component="script"):
        result = self.run_export(component=component)
        self.assertEqual(0, result.returncode, result.stderr)

    def commit_target(self):
        subprocess.run(
            ["git", "-C", str(self.target), "-c", "user.name=Export Test",
             "-c", "user.email=export-test@example.invalid", "commit", "-m", "Fixture"],
            check=True, capture_output=True,
        )

    def test_initial_exports_have_staged_files_and_no_commit(self):
        for component in ("script", "project-templates"):
            with self.subTest(component=component):
                self.target = Path(self.temporary.name) / component
                self.initialize(component)
                result = self.run_export("--check", component=component)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual([], json.loads(result.stdout)["differences"])
                self.assertEqual("master", export.git(self.target, "branch", "--show-current"))
                self.assertTrue(export.git(self.target, "diff", "--cached", "--name-only"))
                head = subprocess.run(["git", "-C", str(self.target), "rev-parse", "--verify", "HEAD"], capture_output=True)
                self.assertNotEqual(0, head.returncode)

    def test_initial_export_preserves_an_existing_directory(self):
        self.target.mkdir()
        marker = self.target / "keep.txt"
        marker.write_text("user content")
        self.assertEqual(2, self.run_export().returncode)
        self.assertEqual("user content", marker.read_text())
        self.assertFalse((self.target / ".git").exists())

    def test_script_distribution_contains_its_platform_dependency_closure(self):
        self.initialize()
        namespace = {"p": "http://maven.apache.org/POM/4.0.0"}
        poms = [ET.parse(path).getroot() for path in self.target.rglob("pom.xml")]
        artifacts = {pom.findtext("p:artifactId", namespaces=namespace) for pom in poms}
        for pom in poms:
            for dependency in pom.findall(".//p:dependency", namespace):
                if dependency.findtext("p:groupId", namespaces=namespace) == "com.ouroboros":
                    self.assertIn(dependency.findtext("p:artifactId", namespaces=namespace), artifacts)

    def test_check_reports_edit_and_update_preserves_dirty_destination(self):
        self.initialize()
        self.commit_target()
        readme = self.target / "README.md"
        readme.write_text("local edit")
        result = self.run_export("--check")
        self.assertEqual(1, result.returncode)
        self.assertIn("README.md", json.loads(result.stdout)["differences"])
        self.assertEqual(2, self.run_export("--update").returncode)
        self.assertEqual("local edit", readme.read_text())

    def test_update_preserves_untracked_content(self):
        self.initialize()
        self.commit_target()
        local = self.target / "local.txt"
        local.write_text("keep")
        self.assertEqual(2, self.run_export("--update").returncode)
        self.assertEqual("keep", local.read_text())

    def test_clean_update_removes_old_exported_files_and_keeps_build_outputs(self):
        self.initialize()
        stale = self.target / "old-export.txt"
        stale.write_text("obsolete export")
        export.git(self.target, "add", "--all")
        self.commit_target()
        output = self.target / "libs/common/common-script/target/build.log"
        output.parent.mkdir(parents=True)
        output.write_text("local build")
        self.assertEqual(0, self.run_export("--update").returncode)
        self.assertFalse(stale.exists())
        self.assertEqual("local build", output.read_text())
        self.assertEqual(0, self.run_export("--check").returncode)

    def test_update_refuses_another_component(self):
        self.initialize()
        self.assertEqual(2, self.run_export("--update", component="project-templates").returncode)

    def test_check_detects_missing_files_and_executable_changes(self):
        self.initialize(component="project-templates")
        script = self.target / "project-templates/scripts/verify-backend-templates.sh"
        script.chmod(0o644 if script.stat().st_mode & 0o111 else 0o755)
        (self.target / "README.md").unlink()
        result = self.run_export("--check", component="project-templates")
        self.assertEqual(1, result.returncode)
        self.assertIn(str(script.relative_to(self.target)), json.loads(result.stdout)["differences"])
        self.assertIn("README.md", json.loads(result.stdout)["differences"])

    def test_frontend_exports_include_local_sdk_and_runnable_build_entries(self):
        for component, package in (("web-base", "main-app"),
                                   ("approval-workflow-designer", "ouroboros-approval-workflow-designer")):
            with self.subTest(component=component):
                self.target = Path(self.temporary.name) / component
                self.initialize(component)
                self.assertEqual(0, self.run_export("--check", component=component).returncode)
                self.assertTrue((self.target / "frontends/web/ouroboros-sdk/src/main.ts").is_file())
                package_root = self.target / "frontends/web" / package
                self.assertTrue((package_root / "webpack.config.js").is_file())
                commands = json.loads((self.target / "package.json").read_text())["scripts"]
                self.assertIn(f"frontends/web/{package}", commands["build"])
                self.assertTrue((self.target / "LICENSE").is_file())
                self.assertEqual("master", export.git(self.target, "branch", "--show-current"))
                if component == "web-base":
                    self.assertTrue((self.target / "frontends/web/publish-ouroboros-web.sh").is_file())
                    dependencies = json.loads((package_root / "package.json").read_text())["dependencies"]
                    for dependency in dependencies.values():
                        if dependency.startswith("file:"):
                            self.assertTrue((package_root / dependency[5:] / "package.json").is_file())
                else:
                    fixture = self.target / "testing/fixtures/approval-workflow/v2-explicit-graph/golden-scenario-1-single-branch.json"
                    self.assertIsInstance(json.loads(fixture.read_text()), dict)

    def test_approval_export_resolves_reactor_modules_parents_and_internal_dependencies(self):
        self.initialize(component="approval-workflow")
        fixture = self.target / "testing/fixtures/approval-workflow/v2-explicit-graph/golden-scenario-1-single-branch.json"
        self.assertIsInstance(json.loads(fixture.read_text()), dict)
        namespace = {"p": "http://maven.apache.org/POM/4.0.0"}
        artifacts = set()
        poms = list(self.target.rglob("pom.xml"))
        for path in poms:
            pom = ET.parse(path).getroot()
            artifacts.add(pom.findtext("p:artifactId", namespaces=namespace))
            for module in pom.findall("p:modules/p:module", namespace):
                self.assertTrue((path.parent / module.text / "pom.xml").is_file())
            parent = pom.find("p:parent", namespace)
            if parent is not None:
                relative = parent.findtext("p:relativePath", default="../pom.xml", namespaces=namespace)
                self.assertTrue((path.parent / relative).is_file(), path)
        for path in poms:
            pom = ET.parse(path).getroot()
            for dependency in pom.findall("p:dependencies/p:dependency", namespace):
                if dependency.findtext("p:groupId", namespaces=namespace) == "com.ouroboros":
                    self.assertIn(dependency.findtext("p:artifactId", namespaces=namespace), artifacts)
        self.assertEqual(0, self.run_export("--check", component="approval-workflow").returncode)

    def test_schema_export_index_resolves_from_repository_root(self):
        self.initialize(component="metadata-schema")
        index = json.loads((self.target / "index.json").read_text())
        for entry in index["entries"]:
            self.assertTrue((self.target / entry["path"]).is_file())
        self.assertEqual(index["count"], len(index["entries"]))
        self.assertEqual(0, self.run_export("--check", component="metadata-schema").returncode)

    def test_skills_export_keeps_installable_skill_directories_at_repository_root(self):
        self.initialize(component="skills")
        self.assertTrue((self.target / "tidestack-howto/SKILL.md").is_file())
        self.assertFalse((self.target / "skills").exists())
        self.assertEqual(0, self.run_export("--check", component="skills").returncode)


if __name__ == "__main__":
    unittest.main()

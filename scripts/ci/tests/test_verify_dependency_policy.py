from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import verify_dependency_policy as verifier


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def pom(group: str, artifact: str, scope: str = "") -> str:
    scope_xml = f"<scope>{scope}</scope>" if scope else ""
    return f"""<project>
  <dependencies>
    <dependency>
      <groupId>{group}</groupId>
      <artifactId>{artifact}</artifactId>
      {scope_xml}
    </dependency>
  </dependencies>
</project>
"""


def empty_pom() -> str:
    return """<project>
  <dependencies>
  </dependencies>
</project>
"""


def base_policy() -> dict:
    future = "2999-12-31"
    return {
        "schema_version": 1,
        "dependency_groups": [
            {
                "id": "groovy-runtime",
                "usage_scopes": ["runtime"],
                "coordinates": {
                    "canonical": [],
                    "forbidden": [
                        {
                            "id": "GRV-CODEHAUS-3",
                            "ecosystem": "maven",
                            "match": {"group_id": "org.codehaus.groovy", "artifact_id": "groovy", "type": "jar", "classifier": ""},
                            "version": {"kind": "exact", "value": "3.0.25"},
                            "usage_scopes": ["runtime"],
                            "evidence_ids": [],
                        }
                    ],
                },
                "boundaries": [],
                "required_states": {},
                "exceptions": [],
            },
            {
                "id": "frontend-tooling",
                "usage_scopes": ["build-tooling"],
                "coordinates": {"canonical": [], "forbidden": []},
                "boundaries": [],
                "required_states": {
                    "non_install_manifests": [],
                    "tooling_cohorts": [
                        {
                            "id": "webpack",
                            "package_names": ["webpack"],
                            "state": "canonical-major",
                            "allowed_major": 5,
                            "observations": [],
                            "evidence_ids": [],
                        }
                    ],
                },
                "exceptions": [],
            },
            {
                "id": "approval-workflow-engine",
                "usage_scopes": ["runtime"],
                "coordinates": {"canonical": [], "forbidden": []},
                "boundaries": [],
                "required_states": {},
                "exceptions": [],
            },
            {
                "id": "junit-compatibility",
                "usage_scopes": ["test"],
                "coordinates": {"canonical": [], "forbidden": []},
                "boundaries": [],
                "required_states": {"vintage_effective_mappings": []},
                "exceptions": [],
            },
        ],
        "_future": future,
    }


class DependencyPolicyVerifierTest(unittest.TestCase):
    def run_policy(self, root: Path, policy: dict) -> tuple[int, set[str]]:
        code, diagnostics, _ = verifier.verify(root, policy=policy)
        return code, {item.code for item in diagnostics}

    def test_current_repository_positive_scan(self) -> None:
        code, diagnostics, stats = verifier.verify(Path(__file__).resolve().parents[3])
        self.assertEqual(code, 0, [item.line() for item in diagnostics])
        self.assertGreaterEqual(stats["groups"], 4)

    def test_evidence_artifact_content_drift_is_checked_semantically(self) -> None:
        policy = base_policy()
        policy["evidence"] = [{
            "id": "E-CURRENT-MANIFEST",
            "kind": "xml",
            "artifacts": [{"path": "module/pom.xml"}],
        }]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "module/pom.xml", empty_pom())
            code, diagnostics, _ = verifier.verify(root, policy=policy)
            self.assertEqual(code, 0, [item.line() for item in diagnostics])

            write(root / "module/pom.xml", pom("org.codehaus.groovy", "groovy"))
            code, diagnostics, _ = verifier.verify(root, policy=policy)
        self.assertEqual(code, 1)
        self.assertIn("POLICY_FORBIDDEN_COORDINATE", {item.code for item in diagnostics})

    def test_missing_evidence_artifact_is_input_error(self) -> None:
        policy = base_policy()
        policy["evidence"] = [{
            "id": "E-MISSING",
            "kind": "file",
            "artifacts": [{"path": "missing.txt"}],
        }]
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(verifier.InputError):
                verifier.verify(Path(tmp), policy=policy)

    def test_forbidden_runtime_coordinate_returns_policy_violation(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "module/pom.xml", pom("org.codehaus.groovy", "groovy"))
            code, codes = self.run_policy(root, base_policy())
        self.assertEqual(code, 1)
        self.assertIn("POLICY_FORBIDDEN_COORDINATE", codes)

    def test_frontend_unregistered_duplicate_or_version_drift_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "ui/package.json", json.dumps({"scripts": {"build": "webpack"}, "devDependencies": {"webpack": "^4.0.0"}}))
            write(root / "ui/package-lock.json", "{}")
            code, codes = self.run_policy(root, base_policy())
        self.assertEqual(code, 1)
        self.assertIn("POLICY_FRONTEND_COHORT_DRIFT", codes)

    def test_missing_lock_without_exact_exception_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "ui/package.json", json.dumps({"scripts": {"build": "webpack"}}))
            code, codes = self.run_policy(root, base_policy())
        self.assertEqual(code, 1)
        self.assertIn("POLICY_FRONTEND_MISSING_LOCK", codes)

    def test_ide_plugins_package_json_is_outside_backend_policy_scope(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(
                root / "ide-plugins/vscode-plugin/package.json",
                json.dumps({"scripts": {"build": "webpack"}, "devDependencies": {"typescript": "^5.8.3", "vitest": "^4.1.10"}}),
            )
            code, codes = self.run_policy(root, base_policy())
        self.assertEqual(code, 0)
        self.assertNotIn("POLICY_FRONTEND_MISSING_LOCK", codes)
        self.assertNotIn("POLICY_FRONTEND_COHORT_DRIFT", codes)

    def test_scratch_repository_copies_do_not_change_policy_results(self) -> None:
        policy = base_policy()
        policy["dependency_groups"][3]["required_states"]["source_snapshot"] = {
            "import_rule": verifier.JUNIT4_IMPORT_RULE,
            "file_count": 0,
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            scratch = root / "tmp/debug-repository"
            write(scratch / "module/pom.xml", pom("org.codehaus.groovy", "groovy"))
            write(scratch / "legacy/pom.xml", pom("org.junit.vintage", "junit-vintage-engine", "test"))
            write(scratch / "legacy/src/test/java/example/LegacyTest.java", "import org.junit.Test;\nclass LegacyTest {}\n")
            write(scratch / "ui/package.json", json.dumps({"scripts": {"build": "webpack"}, "devDependencies": {"webpack": "^4.0.0"}}))

            code, diagnostics, stats = verifier.verify(root, policy=policy)

        self.assertEqual(code, 0, [item.line() for item in diagnostics])
        self.assertEqual(stats["poms"], 0)
        self.assertEqual(stats["package_json"], 0)

    def test_unused_vintage_without_state_exception_fails(self) -> None:
        policy = base_policy()
        junit = policy["dependency_groups"][3]
        junit["required_states"]["vintage_effective_mappings"] = [
            {"declaration_pom": "module/pom.xml", "effective_source_count": 0, "evidence_ids": []}
        ]
        with tempfile.TemporaryDirectory() as tmp:
            code, codes = self.run_policy(Path(tmp), policy)
        self.assertEqual(code, 1)
        self.assertIn("POLICY_JUNIT_UNUSED_VINTAGE", codes)

    def test_junit_vintage_declaration_count_drift_fails(self) -> None:
        policy = base_policy()
        junit = policy["dependency_groups"][3]
        junit["required_states"]["vintage_declarations"] = {
            "direct_count": 0,
            "management_count": 0,
            "management_paths": [],
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "module/pom.xml", pom("org.junit.vintage", "junit-vintage-engine", "test"))
            code, codes = self.run_policy(root, policy)
        self.assertEqual(code, 1)
        self.assertIn("POLICY_JUNIT_VINTAGE_DECLARATION_DRIFT", codes)

    def test_junit_direct_junit_drift_fails(self) -> None:
        policy = base_policy()
        junit = policy["dependency_groups"][3]
        junit["required_states"]["direct_junit_mappings"] = []
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "module/pom.xml", pom("junit", "junit", "test"))
            code, codes = self.run_policy(root, policy)
        self.assertEqual(code, 1)
        self.assertIn("POLICY_JUNIT_DIRECT_JUNIT_DRIFT", codes)

    def test_junit_uncovered_source_fails(self) -> None:
        policy = base_policy()
        junit = policy["dependency_groups"][3]
        junit["required_states"].update(
            {
                "source_snapshot": {
                    "import_rule": verifier.JUNIT4_IMPORT_RULE,
                    "file_count": 1,
                },
                "vintage_declarations": {
                    "direct_count": 0,
                    "management_count": 0,
                    "management_paths": [],
                },
                "direct_junit_mappings": [],
                "effective_coverage": {
                    "covered_source_count": 1,
                    "uncovered_source_count": 0,
                },
            }
        )
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "module/pom.xml", empty_pom())
            write(root / "module/src/test/java/example/LegacyTest.java", "import org.junit.Test;\nclass LegacyTest {}\n")
            code, codes = self.run_policy(root, policy)
        self.assertEqual(code, 1)
        self.assertIn("POLICY_JUNIT_COVERAGE_DRIFT", codes)
        self.assertIn("POLICY_JUNIT_UNCOVERED_SOURCE", codes)

    def test_expanded_or_expired_exception_fails(self) -> None:
        policy = base_policy()
        policy["dependency_groups"][0]["exceptions"] = [
            {
                "id": "EX-WIDE",
                "path_glob": "module/**",
                "usage_scopes": ["runtime"],
                "waives": {"type": "coordinate", "coordinate_id": "GRV-CODEHAUS-3"},
                "owner": "test",
                "reason": "wide waiver",
                "evidence_ids": [],
                "opened_on": "2000-01-01",
                "expires_on": "2000-01-02",
                "tightening_condition": {"type": "coordinate-absent", "coordinate_id": "GRV-CODEHAUS-3", "path_glob": "module/**", "usage_scope": "runtime"},
            }
        ]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "module/pom.xml", pom("org.codehaus.groovy", "groovy"))
            code, codes = self.run_policy(root, policy)
        self.assertEqual(code, 1)
        self.assertIn("POLICY_EXCEPTION_EXPANDED", codes)
        self.assertIn("POLICY_EXCEPTION_EXPIRED", codes)

    def test_activiti6_default_runtime_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(
                root / "core/ouroboros-approval-workflow/ouroboros-approval-workflow-runtime/pom.xml",
                pom("com.ouroboros", "ouroboros-approval-workflow-activiti6"),
            )
            code, codes = self.run_policy(root, base_policy())
        self.assertEqual(code, 1)
        self.assertIn("POLICY_APPROVAL_DEFAULT_BOUNDARY", codes)

    def test_missing_policy_is_input_error(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(verifier.InputError):
                verifier.verify(Path(tmp))


if __name__ == "__main__":
    unittest.main()

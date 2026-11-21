import importlib.util
import os
from pathlib import Path
import tempfile
import time
import unittest
import subprocess


SPEC = importlib.util.spec_from_file_location(
    "storage_verification", Path(__file__).resolve().parents[1] / "verification.py"
)
verification = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verification)


class ReportVerificationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.started_ns = time.time_ns()

    def report(self, name="ExampleTest", tests=1, failures=0, errors=0, skipped=0):
        path = self.root / f"TEST-{name}.xml"
        cases = "".join('<testcase name="case"/>' for _ in range(tests))
        path.write_text(
            f'<testsuite name="{name}" tests="{tests}" failures="{failures}" '
            f'errors="{errors}" skipped="{skipped}">{cases}</testsuite>'
        )
        return path

    def collect(self, reports):
        return verification.collect_reports(
            reports, {"ExampleTest"}, self.started_ns, self.root / "evidence"
        )

    def test_archives_actual_successful_results(self):
        result = self.collect([self.report(tests=3)])
        self.assertEqual(result["tests"], 3)
        self.assertEqual(result["suites"]["ExampleTest"]["skipped"], 0)
        self.assertTrue((self.root / "evidence/TEST-ExampleTest.xml").is_file())

    def test_missing_required_suite_fails(self):
        with self.assertRaisesRegex(ValueError, "Missing"):
            self.collect([])

    def test_zero_failure_error_and_skip_are_rejected(self):
        for counts in ({"tests": 0}, {"failures": 1}, {"errors": 1}, {"skipped": 1}):
            with self.subTest(counts=counts), self.assertRaises(ValueError):
                self.collect([self.report(**counts)])

    def test_previous_run_report_cannot_certify_this_run(self):
        path = self.report()
        os.utime(path, ns=(self.started_ns - 10_000_000_000,) * 2)
        with self.assertRaisesRegex(ValueError, "Stale"):
            self.collect([path])

    def test_duplicate_suite_is_rejected_instead_of_overwriting(self):
        path = self.report()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self.collect([path, path])

    def test_declared_count_must_match_actual_test_cases(self):
        path = self.report()
        path.write_text(path.read_text().replace('tests="1"', 'tests="4"'))
        with self.assertRaisesRegex(ValueError, "count"):
            self.collect([path])

    def test_testcase_failure_cannot_be_hidden_by_summary_counters(self):
        path = self.report()
        path.write_text(path.read_text().replace('<testcase name="case"/>',
                        '<testcase name="case"><failure/></testcase>'))
        with self.assertRaisesRegex(ValueError, "Unsuccessful testcase"):
            self.collect([path])


class SourceVerificationTest(unittest.TestCase):
    def test_edit_delete_and_new_file_invalidate_verified_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            path = root / "content.txt"
            path.write_text("original")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "-c", "user.name=Storage Test", "-c", "user.email=storage@example.invalid",
                            "-c", "commit.gpgsign=false", "commit", "-qm", "fixture"], cwd=root, check=True)
            original = verification.source_snapshot(root, "HEAD")
            path.write_text("changed")
            self.assertNotEqual(original["fingerprint"], verification.source_snapshot(root, "HEAD")["fingerprint"])
            path.unlink()
            self.assertEqual(verification.source_snapshot(root, "HEAD")["files"]["content.txt"], "deleted")
            path.write_text("original")
            (root / "new.txt").write_text("new")
            self.assertNotEqual(original["fingerprint"], verification.source_snapshot(root, "HEAD")["fingerprint"])

    def test_inventory_discovers_new_java_tests(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "libs/storage/backend/src/test/java/example/NewStorageTest.java"
            path.parent.mkdir(parents=True)
            path.write_text("fixture")
            self.assertEqual(verification.test_inventory(root), [path])


if __name__ == "__main__":
    unittest.main()

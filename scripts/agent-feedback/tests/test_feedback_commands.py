from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1]


class FeedbackCommandTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        destination = self.root / "scripts/agent-feedback"
        destination.mkdir(parents=True)
        for name in ("run-tests.sh", "check-health.sh", "resume-session.sh"):
            shutil.copy2(SCRIPTS / name, destination / name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.environment = dict(os.environ, PATH=f"{self.bin}{os.pathsep}{os.environ['PATH']}")

    def test_recovery_reports_git_state_and_uses_task_record_instead_of_legacy_plan(self):
        legacy = self.root / "docs/exec-plans/active"
        legacy.mkdir(parents=True)
        (legacy / "outdated.md").write_text("# OUTDATED TASK\n")
        git = self.bin / "git"
        git.write_text("#!/bin/bash\nif [[ $1 == log ]]; then echo 'abc123 current change'; else echo ' M README.md'; fi\n")
        git.chmod(0o755)

        result = subprocess.run(
            ["bash", "scripts/agent-feedback/resume-session.sh"], cwd=self.root,
            env=self.environment, capture_output=True, text=True, timeout=10,
        )

        self.assertEqual(result.returncode, 0, result.stderr)
        payload = json.loads(result.stdout)
        self.assertEqual(payload["recentCommits"], ["abc123 current change"])
        self.assertEqual(payload["gitStatus"], ["M README.md"])
        self.assertIn("current task, PR, or Issue", payload["suggestedNextSteps"][0])
        self.assertNotIn("OUTDATED TASK", result.stdout)

    def run_tests(self, compile_exit, test_exit):
        mvn = self.bin / "mvn"
        mvn.write_text(
            "#!/bin/bash\n"
            f"printf '%s\\n' \"$*\" >> '{self.root / 'maven-calls.txt'}'\n"
            f"if [[ $* == *test-compile* ]]; then exit {compile_exit}; fi\n"
            f"exit {test_exit}\n"
        )
        mvn.chmod(0o755)
        result = subprocess.run(
            ["bash", "scripts/agent-feedback/run-tests.sh", "--suite", "architecture"],
            cwd=self.root, env=self.environment, capture_output=True, text=True, timeout=10,
        )
        payload = json.loads(next((self.root / "tmp/agent-feedback/tests").glob("*/result.json")).read_text())
        calls = (self.root / "maven-calls.txt").read_text().splitlines()
        return result, payload, calls

    def test_failed_precompile_stops_suite_and_preserves_exit_status(self):
        result, payload, calls = self.run_tests(7, 0)
        self.assertEqual(result.returncode, 7, result.stderr)
        self.assertEqual(payload["exitCode"], 7)
        self.assertFalse(payload["ok"])
        self.assertEqual(len(calls), 1)

    def test_successful_precompile_runs_test_suite(self):
        result, payload, calls = self.run_tests(0, 0)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(payload["ok"])
        self.assertEqual(len(calls), 2)

    def test_failed_suite_preserves_test_exit_status(self):
        result, payload, calls = self.run_tests(0, 9)
        self.assertEqual(result.returncode, 9, result.stderr)
        self.assertFalse(payload["ok"])
        self.assertEqual(len(calls), 2)

    def check_health(self, handler):
        server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
        server.daemon_threads = True
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            started = time.monotonic()
            result = subprocess.run(
                ["bash", "scripts/agent-feedback/check-health.sh", "--base-url",
                 f"http://127.0.0.1:{server.server_port}", "--timeout-seconds", "1"],
                cwd=self.root, capture_output=True, text=True, timeout=6,
            )
            elapsed = time.monotonic() - started
        finally:
            server.shutdown()
            server.server_close()
        payload = json.loads(next((self.root / "tmp/agent-feedback/health").glob("*/result.json")).read_text())
        return result, payload, elapsed

    def test_up_health_endpoint_passes(self):
        class UpHandler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b'{"status":"UP"}')

            def log_message(self, *args):
                return

        result, payload, _ = self.check_health(UpHandler)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(payload["ok"])
        self.assertEqual(payload["lastHttpCode"], "200")

    def test_stalled_request_is_bounded_by_remaining_health_window(self):
        release = threading.Event()
        self.addCleanup(release.set)

        class StalledHandler(BaseHTTPRequestHandler):
            def do_GET(self):
                release.wait(5)

            def log_message(self, *args):
                return

        result, payload, elapsed = self.check_health(StalledHandler)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertFalse(payload["ok"])
        self.assertLess(elapsed, 2.5)


if __name__ == "__main__":
    unittest.main()

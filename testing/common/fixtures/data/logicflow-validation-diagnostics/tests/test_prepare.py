"""Exercise the fixture's response contract against a local HTTP endpoint."""

import json
import os
from pathlib import Path
import subprocess
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


SCRIPT = Path(__file__).resolve().parents[1] / "prepare.sh"


class PrepareContractTest(unittest.TestCase):
    def run_fixture(self, data):
        requests = []

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def respond(self, payload, status=200):
                body = json.dumps(payload).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_POST(self):
                self.respond({"code": 0, "data": {"accessToken": {"token": "fixture-token"}}})

            def do_GET(self):
                if self.path == "/api/app/":
                    self.respond({"code": 0, "data": {"items": [{"appInfo": {"name": "logicflowsmoke"}}]}})
                else:
                    self.respond({"code": 0, "data": [{"name": "smokevalidationfixture"}]})

            def do_PUT(self):
                payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                requests.append((urlparse(self.path), payload))
                self.respond({"code": 0, "data": data})

        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                result = subprocess.run(
                    ["bash", str(SCRIPT)], capture_output=True, text=True, timeout=15,
                    env={**os.environ, "SMOKE_PUBLIC_SHELL_URL": f"http://127.0.0.1:{server.server_port}",
                         "SMOKE_APP_NAME": "logicflowsmoke", "SMOKE_LOGICFLOW_MODEL_NAME": "smokevalidationfixture",
                         "SMOKE_EXPECTED_NODE_NAME": "caller"},
                )
            finally:
                server.shutdown()
                thread.join()
        self.assertEqual(1, len(requests))
        url, payload = requests[0]
        self.assertEqual(["true"], parse_qs(url.query).get("includeValidationSnapshot"))
        self.assertEqual("caller", payload["nodes"][0]["name"])
        return result

    def snapshot(self, node="caller"):
        return {"lastValidatedTime": "2026-11-12T12:00:00",
                "validationDiagnostics": [{"nodeNames": [node], "message": "Missing flow"}]}

    def test_accepts_snapshot_envelope(self):
        result = self.run_fixture({"flowModel": {}, "validationSnapshot": self.snapshot()})
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("fixture 准备完成", result.stdout)

    def test_rejects_legacy_top_level_diagnostics(self):
        result = self.run_fixture(self.snapshot())
        self.assertNotEqual(0, result.returncode)
        self.assertIn("诊断信封", result.stdout)

    def test_rejects_snapshot_without_target_node(self):
        result = self.run_fixture({"flowModel": {}, "validationSnapshot": self.snapshot("other")})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("目标节点", result.stdout)


if __name__ == "__main__":
    unittest.main()

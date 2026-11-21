"""Verify checkout selection and arguments without installing or starting webpack."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


SOURCE = Path(__file__).resolve().parents[1] / "start-tidestack-frontend.sh"


class FrontendCheckoutTest(unittest.TestCase):
    def launch(self, standalone):
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                body = json.dumps({"code": 0, "data": {"headerEntry": "/editor/"}}).encode()
                self.send_response(200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

        with tempfile.TemporaryDirectory(prefix="tidestack checkout ") as temporary:
            root = Path(temporary)
            checkout = root / "checkout"
            frontend = checkout / "frontends/web/main-app"
            binary = frontend / "node_modules/.bin/webpack"
            binary.parent.mkdir(parents=True)
            binary.write_text("#!/bin/sh\nexit 0\n")
            binary.chmod(0o755)
            (frontend / "package.json").write_text("{}")
            npm = root / "bin/npm"
            npm.parent.mkdir()
            npm.write_text("#!/usr/bin/env python3\nimport json,os,sys\nprint(json.dumps({'cwd':os.getcwd(),'argv':sys.argv[1:]}))\n")
            npm.chmod(0o755)
            skill_root = root / "installed" if standalone else checkout
            script = skill_root / "skills/tidestack-local-debugging/scripts/start-tidestack-frontend.sh"
            script.parent.mkdir(parents=True)
            shutil.copy2(SOURCE, script)
            with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
                thread = threading.Thread(target=server.serve_forever, daemon=True)
                thread.start()
                backend = f"http://127.0.0.1:{server.server_port}"
                args = ["bash", str(script), backend]
                if standalone:
                    args += ["--repo", str(checkout)]
                args += ["--host", "127.0.0.1", "--port", "8088"]
                try:
                    result = subprocess.run(args, capture_output=True, text=True, timeout=15,
                                            env={**os.environ, "PATH": str(npm.parent) + os.pathsep + os.environ["PATH"]})
                finally:
                    server.shutdown()
                    thread.join()
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            actual = json.loads(result.stdout.splitlines()[-1])
            self.assertEqual(frontend.resolve(), Path(actual["cwd"]).resolve())
            self.assertEqual(["start", "--", "--api", backend, "--host", "127.0.0.1", "--port", "8088",
                              "--micro_map", f"/editor/={backend}"], actual["argv"])

    def test_monorepo_location(self):
        self.launch(False)

    def test_standalone_install_uses_explicit_checkout(self):
        self.launch(True)

    def test_invalid_checkout_fails_before_start(self):
        result = subprocess.run(["bash", str(SOURCE), "http://127.0.0.1:1", "--repo", "/missing-checkout"],
                                capture_output=True, text=True, timeout=5)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("--repo", result.stderr)


if __name__ == "__main__":
    unittest.main()

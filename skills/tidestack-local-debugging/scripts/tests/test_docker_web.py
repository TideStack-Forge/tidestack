"""The launcher never removes existing containers and labels newly created ones."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "start_tidestack_web.sh"


class DockerWebTest(unittest.TestCase):
    def launch(self, existing=False, owner="other"):
        with tempfile.TemporaryDirectory(prefix="web launcher ") as temporary:
            root = Path(temporary)
            docker = root / "docker"
            log = root / "calls.jsonl"
            docker.write_text(f'#!{sys.executable}\n' + '''import json, os, sys
args = sys.argv[1:]
with open(os.environ["DOCKER_LOG"], "a") as stream:
    stream.write(json.dumps(args) + "\\n")
if args[:2] == ["container", "inspect"]:
    if os.environ["DOCKER_EXISTS"] != "yes": sys.exit(1)
    if "--format" in args:
        print(os.environ["DOCKER_OWNER"] if "Labels" in args[3] else "BACKEND_URL=http://127.0.0.1:8080")
''')
            docker.chmod(0o755)
            result = subprocess.run(["bash", str(SCRIPT), "--backend-url", "http://127.0.0.1:8080",
                                     "--image", "example/web:test", "--listen-port", "18983",
                                     "--container-name", "test-owned-web"], capture_output=True, text=True, timeout=10,
                                    env={**os.environ, "PATH": str(root) + os.pathsep + os.environ["PATH"],
                                         "DOCKER_LOG": str(log), "DOCKER_EXISTS": "yes" if existing else "no",
                                         "DOCKER_OWNER": owner})
            return result, [json.loads(line) for line in log.read_text().splitlines()]

    def test_existing_unowned_container_is_preserved(self):
        result, calls = self.launch(existing=True)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(call[0] in {"rm", "run"} for call in calls))

    def test_existing_owned_container_requires_explicit_stop(self):
        result, calls = self.launch(existing=True, owner="local-debugging")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("already exists", result.stderr)
        self.assertFalse(any(call[0] in {"rm", "run"} for call in calls))

    def test_new_container_is_labeled_and_receives_backend(self):
        result, calls = self.launch()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        run = next(call for call in calls if call[0] == "run")
        self.assertEqual("dev.tidestack.launcher=local-debugging", run[run.index("--label") + 1])
        self.assertIn("BACKEND_URL=http://127.0.0.1:8080", run)


if __name__ == "__main__":
    unittest.main()

"""gstack's supported Python test entry delegates to the canonical Maven gate."""

from pathlib import Path
import subprocess
import unittest


class StorageVerificationTest(unittest.TestCase):
    def test_storage_contract_consumers_and_architecture(self):
        scenario = Path(__file__).resolve().parent
        subprocess.run(["bash", str(scenario / "run.sh")], check=True)


if __name__ == "__main__":
    unittest.main()

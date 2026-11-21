import hashlib
import importlib.util
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error


MODULE = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("groovy_compiler", MODULE / "build/compiler.py")
compiler = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(compiler)


class CompilerAssetDownloadTest(unittest.TestCase):
    def setUp(self):
        cache = tempfile.TemporaryDirectory()
        self.addCleanup(cache.cleanup)
        self.cache = Path(cache.name)
        for patcher in (patch.object(compiler, "CACHE", self.cache), patch.dict(os.environ, {"PATH": ""})):
            patcher.start()
            self.addCleanup(patcher.stop)
        self.content = b"pinned compiler source\n"
        self.source = dict(compiler.MANIFEST["sources"][0])
        self.source["gitBlobSha1"] = hashlib.sha1(
            b"blob " + str(len(self.content)).encode() + b"\0" + self.content
        ).hexdigest()

    def upstream(self, request, timeout):
        url = request if isinstance(request, str) else request.full_url
        if url.startswith("https://api.github.com/"):
            raise urllib.error.HTTPError(url, 403, "rate limit exceeded", {}, None)
        return io.BytesIO(self.content)

    def test_pinned_source_downloads_when_github_api_is_rate_limited(self):
        with patch.object(compiler.urllib.request, "urlopen", side_effect=self.upstream):
            compiler.fetch(self.source)

        self.assertEqual((self.cache / self.source["path"]).read_bytes(), self.content)
        self.assertTrue(compiler.valid(self.source))

    def test_valid_cached_source_is_reused_without_network(self):
        path = self.cache / self.source["path"]
        path.parent.mkdir(parents=True)
        path.write_bytes(self.content)
        with patch.object(compiler.urllib.request, "urlopen", side_effect=AssertionError("network used")):
            compiler.fetch(self.source)

        self.assertEqual(path.read_bytes(), self.content)

    def test_corrupt_source_is_rejected_before_entering_cache(self):
        self.source["gitBlobSha1"] = "0" * 40
        with patch.object(compiler.urllib.request, "urlopen", side_effect=self.upstream):
            with self.assertRaisesRegex(ValueError, "digest mismatch"):
                compiler.fetch(self.source)

        self.assertFalse((self.cache / self.source["path"]).exists())

    def test_binary_download_still_validates_sha256(self):
        asset = {"path": "binary.jar", "url": "https://example.test/binary.jar",
                 "sha256": hashlib.sha256(self.content).hexdigest()}
        with patch.object(compiler.urllib.request, "urlopen", side_effect=self.upstream):
            compiler.fetch(asset)

        self.assertEqual((self.cache / asset["path"]).read_bytes(), self.content)
        self.assertTrue(compiler.valid(asset))

    def test_corrupt_binary_is_rejected_before_entering_cache(self):
        asset = {"path": "binary.jar", "url": "https://example.test/binary.jar", "sha256": "0" * 64}
        with patch.object(compiler.urllib.request, "urlopen", side_effect=self.upstream):
            with self.assertRaisesRegex(ValueError, "digest mismatch"):
                compiler.fetch(asset)

        self.assertFalse((self.cache / asset["path"]).exists())


if __name__ == "__main__":
    unittest.main()

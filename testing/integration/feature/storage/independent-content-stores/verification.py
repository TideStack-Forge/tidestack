"""Run the storage scenario and certify fresh Maven evidence for one source tree."""

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import signal
import socket
import subprocess
import tempfile
import time
from urllib.error import URLError
from urllib.request import urlopen
import zipfile


ROOT = Path(__file__).resolve().parents[5]
SCENARIO = Path(__file__).resolve().parent
AREAS = (
    "libs/storage", "ability/ouroboros-file-upload", "core/ouroboros-resource",
    "app-module/document-management",
)
PROJECTS = (
    "dev-module/ouroboros-dev-app-management",
    "app-module/document-management/document-management-autoconfigure",
    "app-module/document-management/document-management-webapp",
    "libs/storage/ouroboros-storage-s3",
    "ability/ouroboros-file-upload/ouroboros-file-upload-runtime",
    "ability/ouroboros-file-upload/ouroboros-file-upload-fields",
    "core/ouroboros-resource/ouroboros-resource-runtime-manage",
    "core/ouroboros-resource/ouroboros-resource-dev",
)
COVERAGE = (
    "libs/storage/ouroboros-storage-core", "libs/storage/ouroboros-storage-runtime",
    "libs/storage/ouroboros-storage-filesystem", "libs/storage/ouroboros-storage-s3",
    "ability/ouroboros-file-upload/ouroboros-file-upload-storage",
)
MINIO_IMAGE = "minio/minio:RELEASE.2025-04-22T22-12-26Z"
# Preserve the scenario's original obligations when adding newly discovered tests.
REQUIRED_SUITES = {
    "CoreStorageContractTest", "FileSystemBlobStoreTest", "StorageBindingTest",
    "S3BlobStoreFactoryTest", "S3MinioIT", "BlobUploadStorageServiceTest",
    "TimerCleanUpSchedulerTest", "DefaultFileAccessUrlGeneratorTest", "StorageWiringTest",
    "BlobDocumentStorageServiceTest", "DocumentContentTransactionTest",
    "DocumentManagementAutoConfigurationTest", "RuntimeOverrideResourceProviderTest",
    "DefaultResourceServiceTest", "ResourceProviderWiringTest", "RuntimeResourceManageServiceTest",
    "RuntimeResourceControllerTest", "AppExtendJarStorageTest", "AppLogoStorageTest",
}


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=root, text=True).strip()


def source_snapshot(root: Path, base: str) -> dict:
    base_sha = git(root, "rev-parse", "--verify", f"{base}^{{commit}}")
    subprocess.run(["git", "merge-base", "--is-ancestor", base_sha, "HEAD"], cwd=root, check=True)
    paths = subprocess.check_output(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=root
    ).decode().split("\0")
    files = {}
    for name in sorted(set(paths) - {""}):
        path = root / name
        if path.is_symlink():
            value = "symlink:" + os.readlink(path)
        elif path.is_file():
            mode = "executable:" if path.stat().st_mode & 0o111 else "file:"
            value = mode + hashlib.sha256(path.read_bytes()).hexdigest()
        elif not path.exists():
            value = "deleted"
        else:
            raise ValueError(f"Unsupported source entry: {name}")
        files[name] = value
    fingerprint = hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()
    return {"head": git(root, "rev-parse", "HEAD"), "base": base, "baseSha": base_sha,
            "fingerprint": fingerprint, "files": files}


def collect_reports(reports, required: set[str], started_ns: int, destination: Path) -> dict:
    from xml.etree import ElementTree

    destination.mkdir(parents=True, exist_ok=True)
    suites = {}
    for path in reports:
        suite = ElementTree.parse(path).getroot()
        name = suite.attrib["name"].split(".")[-1]
        if name not in required:
            continue
        if path.stat().st_mtime_ns < started_ns:
            raise ValueError(f"Stale report: {path}")
        if name in suites:
            raise ValueError(f"Duplicate suite: {name}")
        counts = {key: int(suite.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
        if counts["tests"] <= 0 or any(counts[key] for key in ("failures", "errors", "skipped")):
            raise ValueError(f"Unsuccessful suite: {name}: {counts}")
        if counts["tests"] != len(suite.findall("testcase")):
            raise ValueError(f"Incorrect testcase count: {name}")
        if any(case.find(kind) is not None for case in suite.findall("testcase")
               for kind in ("failure", "error", "skipped")):
            raise ValueError(f"Unsuccessful testcase: {name}")
        suites[name] = counts
        shutil.copy2(path, destination / path.name)
    if required != set(suites):
        raise ValueError(f"Missing required reports: {sorted(required - set(suites))}")
    result = {"tests": sum(s["tests"] for s in suites.values()), "suites": suites}
    write_json(destination / "results.json", result)
    return result


def test_inventory(root: Path) -> list[Path]:
    paths = [path for area in AREAS for path in (root / area).glob("**/src/test/java/**/*.java")
             if path.stem.endswith(("Test", "Tests", "IT"))]
    development = root / PROJECTS[0] / "src/test/java"
    paths += [*development.glob("**/AppLogoStorageTest.java"),
              *development.glob("**/AppExtendJarStorageTest.java")]
    if not paths or len({p.stem for p in paths}) != len(paths):
        raise ValueError("Empty or ambiguous Maven test inventory")
    return sorted(paths)


@contextmanager
def minio_environment(evidence: Path):
    env = os.environ.copy()
    if env.get("OSS_TEST_ENDPOINT"):
        for key in ("OSS_TEST_ACCESS_KEY", "OSS_TEST_SECRET_KEY"):
            if not env.get(key):
                raise ValueError(f"Missing {key}")
        with urlopen(env["OSS_TEST_ENDPOINT"] + "/minio/health/ready", timeout=5) as response:
            if response.status != 200:
                raise RuntimeError("External MinIO is not ready")
        yield env
        return
    env["MINIO_ROOT_USER"] = "storage-" + secrets.token_hex(8)
    env["MINIO_ROOT_PASSWORD"] = secrets.token_urlsafe(32)
    env["MINIO_BROWSER"] = "off"
    process = None
    container = None
    log = (evidence / "minio.log").open("w")
    try:
        binary = env.get("MINIO_BINARY") or shutil.which("minio")
        if binary:
            with socket.socket() as reservation:
                reservation.bind(("127.0.0.1", 0))
                port = reservation.getsockname()[1]
            process = subprocess.Popen(
                [binary, "server", str(evidence / "minio-data"), "--address", f"127.0.0.1:{port}",
                 "--console-address", "127.0.0.1:0"], env=env, stdout=log, stderr=subprocess.STDOUT
            )
        else:
            container = subprocess.check_output(
                ["docker", "run", "--detach", "--rm", "--publish", "127.0.0.1::9000",
                 "--env", "MINIO_ROOT_USER", "--env", "MINIO_ROOT_PASSWORD", MINIO_IMAGE,
                 "server", "/data"], env=env, text=True
            ).strip()
            address = subprocess.check_output(["docker", "port", container, "9000/tcp"], text=True).strip()
            port = int(address.rsplit(":", 1)[1])
        env.update(OSS_TEST_ENDPOINT=f"http://127.0.0.1:{port}",
                   OSS_TEST_ACCESS_KEY=env["MINIO_ROOT_USER"],
                   OSS_TEST_SECRET_KEY=env["MINIO_ROOT_PASSWORD"])
        deadline = time.monotonic() + 45
        while True:
            if process is not None and process.poll() is not None:
                raise RuntimeError("MinIO exited before readiness; see minio.log")
            try:
                with urlopen(env["OSS_TEST_ENDPOINT"] + "/minio/health/ready", timeout=1) as response:
                    if response.status == 200:
                        break
            except (URLError, TimeoutError):
                if time.monotonic() >= deadline:
                    raise RuntimeError("MinIO readiness timed out")
                time.sleep(0.2)
        write_json(evidence / "minio.json", {"endpoint": env["OSS_TEST_ENDPOINT"],
                   "binary": binary, "image": MINIO_IMAGE if container else None})
        yield env
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
        if container:
            try:
                subprocess.run(["docker", "logs", container], stdout=log, stderr=subprocess.STDOUT, check=True)
            finally:
                subprocess.run(["docker", "stop", container], check=True, stdout=subprocess.DEVNULL)
        log.close()
        write_json(evidence / "cleanup.json", {"ownedServiceStopped": True})


def run_stage(command: list[str], label: str, evidence: Path, env: dict) -> int:
    if command[0] == "mvn" and env.get("MAVEN_REPO_LOCAL"):
        command = [command[0], f"-Dmaven.repo.local={env['MAVEN_REPO_LOCAL']}", *command[1:]]
    started_ns = time.time_ns()
    print(f"RUN {label}: {' '.join(command)}", flush=True)
    with (evidence / f"{label}.log").open("w") as log:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    write_json(evidence / f"{label}-command.json", {
        "command": command, "cwd": str(ROOT), "startedNs": started_ns,
        "endedNs": time.time_ns(), "exitCode": result.returncode,
    })
    if result.returncode:
        raise RuntimeError(f"{label} exited {result.returncode}; see {evidence / (label + '.log')}")
    return started_ns


def verify_jars(evidence: Path) -> None:
    classes = ("com/ouroboros/upload/UploadStorageService.class",
               "com/ouroboros/upload/service/BlobUploadStorageService.class",
               "com/ouroboros/upload/TimerCleanUpScheduler.class")
    results = {}
    for module, expected in (("core", (True, False, False)), ("storage", (False, True, True))):
        jars = [p for p in (ROOT / f"ability/ouroboros-file-upload/ouroboros-file-upload-{module}/target").glob("*.jar")
                if not p.name.endswith(("-sources.jar", "-javadoc.jar", "-tests.jar"))]
        if len(jars) != 1:
            raise ValueError(f"Expected one {module} artifact: {jars}")
        with zipfile.ZipFile(jars[0]) as jar:
            present = tuple(name in jar.namelist() for name in classes)
        if present != expected:
            raise ValueError(f"Invalid {module} JAR contents: {present}")
        results[module] = {"path": str(jars[0]), "sha256": hashlib.sha256(jars[0].read_bytes()).hexdigest(),
                           "classes": dict(zip(classes, present))}
    write_json(evidence / "jar-boundaries.json", results)


def collect_coverage(started_ns: int, evidence: Path) -> None:
    from xml.etree import ElementTree

    destination = evidence / "coverage"
    destination.mkdir()
    results = {}
    for module in COVERAGE:
        report = ROOT / module / "target/site/jacoco/jacoco.xml"
        if report.stat().st_mtime_ns < started_ns:
            raise ValueError(f"Stale coverage report: {report}")
        counter = next(c for c in ElementTree.parse(report).getroot().findall("counter")
                       if c.attrib["type"] == "LINE")
        covered, missed = int(counter.attrib["covered"]), int(counter.attrib["missed"])
        if covered + missed == 0 or covered / (covered + missed) < 0.70:
            raise ValueError(f"Insufficient line coverage: {module}")
        name = Path(module).name
        results[name] = {"covered": covered, "total": covered + missed}
        shutil.copy2(report, destination / f"{name}.xml")
        execution = ROOT / module / "target/jacoco.exec"
        if execution.stat().st_mtime_ns < started_ns:
            raise ValueError(f"Stale coverage execution data: {execution}")
        shutil.copy2(execution, destination / f"{name}.exec")
    write_json(destination / "results.json", results)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default=os.environ.get("STORAGE_VERIFY_BASE", "dev-next"))
    args = parser.parse_args()
    evidence_root = ROOT / "tmp/testing-evidence/oss-file-storage"
    evidence_root.mkdir(parents=True, exist_ok=True)
    evidence = Path(tempfile.mkdtemp(prefix="gate-", dir=evidence_root))
    print(f"EVIDENCE {evidence}", flush=True)
    summary = {"status": "fail", "startedAt": datetime.now(timezone.utc).isoformat()}
    try:
        before = source_snapshot(ROOT, args.base)
        write_json(evidence / "source.json", before)
        inventory = test_inventory(ROOT)
        write_json(evidence / "test-inventory.json", [str(p.relative_to(ROOT)) for p in inventory])
        required = {p.stem for p in inventory} | REQUIRED_SUITES
        with minio_environment(evidence) as env:
            run_stage(["java", "-version"], "java-version", evidence, env)
            run_stage(["mvn", "-v"], "maven-version", evidence, env)
            run_stage(["python3", "-m", "unittest", "discover", "-s", str(SCENARIO / "tests"), "-v"], "verifier-tests", evidence, env)
            run_stage(["mvn", "-ntp", "clean", "-DskipTests", "test-compile"], "full-clean-compile", evidence, env)
            behavior_started = run_stage(["mvn", "-ntp", "-pl", ",".join(PROJECTS), "-am", "test",
                "-Dtest=" + ",".join(sorted(required)), "-Dsurefire.failIfNoSpecifiedTests=false"], "behavior", evidence, env)
            reports = [p for area in (*AREAS, PROJECTS[0]) for p in (ROOT / area).glob("**/target/surefire-reports/TEST-*.xml")]
            behavior = collect_reports(reports, required, behavior_started, evidence / "behavior")
            started = run_stage(["mvn", "-ntp", "-pl", "testing/architecture-tests", "-am", "test",
                "-Dsurefire.failIfNoSpecifiedTests=false"], "architecture", evidence, env)
            architecture_paths = list((ROOT / "testing/architecture-tests/target/surefire-reports").glob("TEST-*.xml"))
            architecture_required = {p.stem for p in (ROOT / "testing/architecture-tests/src/test/java").glob("**/*.java")
                                     if p.stem.endswith("Test") or p.stem == "ArchitectureTestWithAllowlist"}
            architecture = collect_reports(architecture_paths, architecture_required, started, evidence / "architecture")
            if not architecture["tests"]:
                raise ValueError("No architecture tests ran")
            run_stage(["mvn", "-ntp", "-pl", ",".join(COVERAGE), "jacoco:check@check"], "coverage", evidence, env)
            collect_coverage(behavior_started, evidence)
            run_stage(["bash", "scripts/architecture/check-package-structure.sh"], "package-structure", evidence, env)
            run_stage(["mvn", "-ntp", "-pl", COVERAGE[-1], "-am", "-DskipTests", "package"], "packaging", evidence, env)
            verify_jars(evidence)
        after = source_snapshot(ROOT, args.base)
        if (before["fingerprint"], before["head"], before["baseSha"]) != (after["fingerprint"], after["head"], after["baseSha"]):
            raise ValueError("Source or baseline changed during verification")
        summary.update(status="pass", head=before["head"], baseSha=before["baseSha"],
                       fingerprint=before["fingerprint"], behaviorTests=behavior["tests"],
                       behaviorSuites=len(required), architectureTests=architecture["tests"])
        print(f"PASS: {behavior['tests']} behavior tests, {architecture['tests']} architecture tests; source={before['fingerprint']}")
        print(f"{behavior['tests'] + architecture['tests']} tests, 0 failures")
    except (Exception, KeyboardInterrupt) as error:
        summary["error"] = str(error) or type(error).__name__
        raise
    finally:
        summary["endedAt"] = datetime.now(timezone.utc).isoformat()
        write_json(evidence / "summary.json", summary)


if __name__ == "__main__":
    def terminate(signum, frame):
        raise KeyboardInterrupt(f"Interrupted by signal {signum}")

    signal.signal(signal.SIGTERM, terminate)
    main()

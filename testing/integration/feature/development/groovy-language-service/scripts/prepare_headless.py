#!/usr/bin/env python3
"""Prepare the pinned experimental GE train. All generated files stay in this worktree's tmp/."""
import argparse
import base64
import concurrent.futures
import hashlib
import io
import json
from pathlib import Path
import subprocess
import urllib.request
import zipfile

SCENARIO = Path(__file__).resolve().parents[1]
ROOT = SCENARIO.parents[4]
CACHE = ROOT / "tmp/groovy-semantic-prototype"
MANIFEST = json.loads((SCENARIO / "headless-assets.json").read_text())
COMPILER = CACHE / "ge430-assembled/org.codehaus.groovy_4.0.30.qualifier.jar"


def assembly_valid() -> bool:
    provenance = COMPILER.with_suffix(".assembly.json")
    if not COMPILER.is_file() or not provenance.is_file():
        return False
    record = json.loads(provenance.read_text())
    return record["inputs"] == MANIFEST and record["compilerSha256"] == hashlib.sha256(COMPILER.read_bytes()).hexdigest()


def valid(asset: dict) -> bool:
    path = CACHE / asset["path"]
    if not path.is_file():
        return False
    data = path.read_bytes()
    if "gitBlobSha1" in asset:
        return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest() == asset["gitBlobSha1"]
    return hashlib.sha256(data).hexdigest() == asset["sha256"]


def fetch(asset: dict) -> None:
    if valid(asset):
        return
    path = CACHE / asset["path"]
    path.parent.mkdir(parents=True, exist_ok=True)
    if asset.get("size", 0) > 2_000_000:
        size = asset["size"]
        chunk = 1_000_000

        def part(start: int) -> bytes:
            end = min(size - 1, start + chunk - 1)
            request = urllib.request.Request(asset["url"], headers={"Range": f"bytes={start}-{end}"})
            with urllib.request.urlopen(request, timeout=60) as response:
                data = response.read()
                if response.status != 206 or len(data) != end - start + 1:
                    raise ValueError("Invalid ranged download for " + asset["path"])
                return data

        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            data = b"".join(pool.map(part, range(0, size, chunk)))
    else:
        with urllib.request.urlopen(asset["url"], timeout=60) as response:
            data = response.read()
        if "gitBlobSha1" in asset:
            data = base64.b64decode(json.loads(data)["content"])
    temporary = path.with_suffix(path.suffix + ".download")
    temporary.write_bytes(data)
    if "gitBlobSha1" in asset:
        actual = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
        expected = asset["gitBlobSha1"]
    else:
        actual, expected = hashlib.sha256(data).hexdigest(), asset["sha256"]
    if actual != expected:
        raise ValueError("Downloaded artifact digest mismatch: " + asset["path"])
    temporary.replace(path)
    print("Prepared", asset["path"], flush=True)


def write_entry(archive: zipfile.ZipFile, name: str, data: bytes) -> None:
    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    archive.writestr(info, data)


def assemble() -> None:
    core = CACHE / "ge-core"
    expanded = CACHE / "groovy-4.0.29-expanded"
    expanded.mkdir(exist_ok=True)
    baseline = core / "org.codehaus.groovy_4.0.29.v202512311652-e2512.jar"
    with zipfile.ZipFile(baseline) as archive:
        for name in ("eclipse-trace.jar", "groovy-eclipse.jar", "groovy-parser2.jar", "lib/ivy-2.5.3.jar"):
            destination = expanded / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(archive.read(name))
    parser = (expanded / "groovy-parser2.jar").read_bytes()
    if hashlib.sha1(b"blob " + str(len(parser)).encode() + b"\0" + parser).hexdigest() != MANIFEST["unchangedParserBlob"]:
        raise ValueError("The baseline parser differs from the target compiler source tree")
    classes = CACHE / "ge430-delta-classes"
    classes.mkdir(exist_ok=True)
    for path in classes.rglob("*.class"):
        path.unlink()
    source = CACHE / "ge430-src/base/org.codehaus.groovy40"
    sources = [CACHE / item["path"] for item in MANIFEST["sources"] if item["path"].endswith(".java")]
    libraries = [expanded / "groovy-eclipse.jar", expanded / "eclipse-trace.jar", expanded / "groovy-parser2.jar",
                 CACHE / "ge430-libs/groovy-4.0.30.jar", expanded / "lib/ivy-2.5.3.jar"]
    evidence = ROOT / "tmp/testing-evidence/groovy-language-service/headless"
    evidence.mkdir(parents=True, exist_ok=True)
    with (evidence / "compiler-build.log").open("w") as log:
        subprocess.run(["javac", "--release", "17", "-cp", ":".join(map(str, libraries)), "-d", str(classes),
                        *map(str, sources)], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=120)
    replaced = [str(p.relative_to(source / "src").with_suffix("")) for p in sources] + MANIFEST["removedOverrides"]
    patched = io.BytesIO()
    with zipfile.ZipFile(expanded / "groovy-eclipse.jar") as old, zipfile.ZipFile(patched, "w") as updated:
        for name in sorted(old.namelist()):
            if not any(name == prefix + ".class" or name.startswith(prefix + "$") for prefix in replaced):
                write_entry(updated, name, old.read(name))
        for path in sorted(classes.rglob("*.class")):
            write_entry(updated, str(path.relative_to(classes)), path.read_bytes())
    replacements = {"groovy-eclipse.jar": patched.getvalue(), "META-INF/MANIFEST.MF": (source / "META-INF/MANIFEST.MF").read_bytes()}
    for artifact in ("groovy", "groovy-test"):
        name = f"{artifact}-4.0.30.jar"
        replacements["lib/" + name] = (CACHE / "ge430-libs" / name).read_bytes()
    COMPILER.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(COMPILER, "w") as updated:
        for name in sorted(old.namelist()):
            if name not in replacements and name not in ("lib/groovy-4.0.29.jar", "lib/groovy-test-4.0.29.jar"):
                write_entry(updated, name, old.read(name))
        for name, data in sorted(replacements.items()):
            write_entry(updated, name, data)
    provenance = {"inputs": MANIFEST, "compilerSha256": hashlib.sha256(COMPILER.read_bytes()).hexdigest()}
    COMPILER.with_suffix(".assembly.json").write_text(json.dumps(provenance, indent=2) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    assets = MANIFEST["files"] + MANIFEST["sources"]
    if args.check:
        missing = [asset["path"] for asset in assets if not valid(asset)]
        if not assembly_valid():
            missing.append(str(COMPILER.relative_to(CACHE)))
        if missing:
            raise SystemExit("Missing or invalid qualification assets: " + ", ".join(missing))
        print("PASS: pinned binary/source inputs and assembled qualification compiler are present")
        return
    for asset in assets:
        fetch(asset)
    assemble()
    subprocess.run(["mvn", "-B", "-q", "-f", str(SCENARIO / "headless-pom.xml"), "dependency:build-classpath",
                    "-Dmdep.outputFile=" + str(CACHE / "headless-classpath.txt")], cwd=ROOT, check=True, timeout=180)
    print("Prepared experimental Groovy 4.0.30 train:", COMPILER)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Observe GE semantics and separately gate the reusable core or complete editor acceptance."""
import argparse
import json
from pathlib import Path
import subprocess
import time
import zipfile
import prepare_headless

SCENARIO = Path(__file__).resolve().parents[1]
ROOT = SCENARIO.parents[4]
CACHE = ROOT / "tmp/groovy-semantic-prototype"
EVIDENCE = ROOT / "tmp/testing-evidence/groovy-language-service/headless"


def check_inputs() -> None:
    missing = [a["path"] for a in prepare_headless.MANIFEST["files"] + prepare_headless.MANIFEST["sources"]
               if not prepare_headless.valid(a)]
    for path in (CACHE / "headless-classpath.txt", CACHE / "fixture.jar",
                 ROOT / "tmp/testing-evidence/groovy-language-service/platform/context-string.json",
                 ROOT / "tmp/testing-evidence/groovy-language-service/platform/context-integer.json",
                 prepare_headless.COMPILER):
        if not path.is_file():
            missing.append(str(path.relative_to(ROOT)))
    if missing:
        raise SystemExit("Missing or invalid inputs; run prepare_headless.py, community_probe.py and platform_probe.py: " + ", ".join(missing))
    if not prepare_headless.assembly_valid():
        raise SystemExit("The compiler assembly has changed; rerun prepare_headless.py")
    for path in (CACHE / "headless-classpath.txt").read_text().strip().split(":"):
        if not Path(path).is_file():
            raise SystemExit("Missing dependency: " + path)


def qualify(report: dict) -> list[dict]:
    documents = report["documents"]
    checks = []

    def check(case: str, passed: bool, evidence: object, gate: str = "core") -> None:
        checks.append({"case": case, "result": "pass" if passed else "fail", "gate": gate,
                       "assertionLevel": "strict", "evidence": evidence})

    def rows(case: str, text: str, expected_type: str) -> list[dict]:
        return [row for row in documents[case]["types"] if row["text"] == text and row["type"] == expected_type
                and row["confidence"] != "UNKNOWN"]

    def types(case: str, text: str, expected_type: str) -> bool:
        return bool(rows(case, text, expected_type))

    check("GLS-H-VERSION", report["groovyVersion"] == "4.0.30", report["groovyVersion"])
    check("GLS-H-GENERIC", types("generic", "get", "probe.Customer") and types("generic", "item.name", "java.lang.String"), documents["generic"])
    check("GLS-H-IT", types("implicit-it", "it", "probe.Customer") and types("implicit-it", "active", "boolean"), documents["implicit-it"])
    check("GLS-H-COLLECT", types("collect", "names", "java.util.List<java.lang.String>") and types("collect", "get", "java.lang.String"), documents["collect"])
    check("GLS-H-DELEGATE", all(types("delegate", text, kind) for text, kind in (
        ("appendTitle", "probe.CustomerApi$Builder"), ("delegate", "probe.CustomerApi$Builder"),
        ("title", "java.lang.String"), ("owner", "Probe_delegate"), ("thisObject", "Probe_delegate"))), documents["delegate"])
    check("GLS-H-JAVA-GENERIC", types("java-generic", "city", "java.lang.String"), documents["java-generic"])
    signatures = [r for r in documents["signature"]["types"] if r.get("method") == "substring"]
    check("GLS-H-SIGNATURE", any(r.get("parameters") == ["int", "int"] for r in signatures), signatures)
    check("GLS-H-SAFE-SPREAD", types("safe-spread", "names", "java.util.List<java.lang.String>") and types("safe-spread", "label", "groovy.lang.GString"), documents["safe-spread"])
    value_references = sorted({row["start"] for row in documents["symbols"]["types"]
                               if row["text"] == "value" and row.get("declarationStart") == 4 and row.get("declarationEnd") == 9})
    check("GLS-H-SYMBOL-BINDING", value_references == [4, 43], {"referencesToFirstDeclaration": value_references, "document": documents["symbols"]})
    for case in ("context", "context-restored"):
        passed = (types(case, "$0", "java.util.Map<java.lang.String, java.lang.Object>") and
                  types(case, "city", "java.lang.String") and types(case, "name", "java.lang.String") and
                  types(case, "names", "java.util.List<java.lang.String>") and types(case, "size", "int") and
                  types(case, "label", "java.lang.String"))
        check("GLS-H-" + case.upper(), passed, documents[case])
    check("GLS-H-CONTEXT-UPDATE", types("context-updated", "name", "java.lang.Number") and
          types("context-updated", "names", "java.util.List<java.lang.Number>") and types("context-updated", "label", "java.lang.Number"), documents["context-updated"])
    check("GLS-H-CONTEXT-REMOVE", types("context-removed", "names", "java.util.List<java.lang.Object>") and
          not types("context-removed", "names", "java.util.List<java.lang.String>"), documents["context-removed"])
    check("GLS-H-CONTEXT-JAVA", types("context-java", "it", "probe.Customer") and
          types("context-java", "names", "java.util.List<java.lang.String>"), documents["context-java"])
    syntax_errors = [d for d in documents["incomplete"]["diagnostics"] if d["error"]]
    check("GLS-H-SYNTAX-DIAGNOSTIC", bool(syntax_errors), syntax_errors)
    check("GLS-H-INCOMPLETE", any("toUpperCase" in text for text in documents["incomplete"].get("completions", [])), documents["incomplete"], "editor")
    check("GLS-H-CODESELECT", any(s.get("start") == 4 and s.get("length") == 5 for s in documents["symbols"].get("selections", [])), documents["symbols"], "editor")
    last_method = documents["safe-spread"]["source"].rindex("toUpperCase")
    check("GLS-H-GSTRING-METHOD", any(row["start"] == last_method and row["type"] == "java.lang.String" and row.get("method") == "toUpperCase"
          for row in documents["safe-spread"]["types"]), documents["safe-spread"], "editor")
    return checks


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--gate", choices=("core", "editor"), default="core")
    args = parser.parse_args()
    check_inputs()
    if args.check:
        print("PASS: compiler, dependencies, Java fixtures and real platform contexts are ready")
        return
    evidence_run = EVIDENCE / time.strftime("run-%Y%m%d-%H%M%S")
    evidence_run.mkdir(parents=True)

    def run(command: list[str], label: str) -> None:
        with (evidence_run / (label + ".log")).open("w") as output:
            subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, check=True, timeout=240)

    jars = [Path(p) for p in (CACHE / "headless-classpath.txt").read_text().strip().split(":")]
    osgi = next(p for p in jars if p.name.startswith("org.eclipse.osgi-"))
    dependencies = [p for p in jars if not p.name.startswith(("org.eclipse.osgi-", "org.eclipse.jdt.core-", "ecj-"))]
    core = CACHE / "ge-core"
    dependencies.extend([core / "jdt-groovy-core.jar", core / "groovy-eclipse-core.jar"])
    dependencies.extend(p for p in core.glob("org.*.jar") if not p.name.startswith("org.codehaus.groovy_"))
    dependencies.append(prepare_headless.COMPILER)
    compiled = CACHE / "headless-classes"
    compiled.mkdir(exist_ok=True)
    expanded = CACHE / "groovy-4.0.30-expanded"
    expanded.mkdir(exist_ok=True)
    with zipfile.ZipFile(prepare_headless.COMPILER) as archive:
        for name in ("eclipse-trace.jar", "groovy-eclipse.jar", "groovy-parser2.jar", "lib/ivy-2.5.3.jar", "lib/groovy-4.0.30.jar"):
            destination = expanded / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(archive.read(name))
    classpath = ":".join(map(str, [osgi, *dependencies, *expanded.rglob("*.jar")]))
    run(["javac", "--release", "21", "-cp", classpath, "-d", str(compiled), *map(str, (SCENARIO / "headless").glob("*.java"))], "compile")
    bundle = CACHE / "semantic-probe.jar"
    manifest = ("Manifest-Version: 1.0\nBundle-ManifestVersion: 2\n"
                "Bundle-SymbolicName: com.ouroboros.testing.groovy.semantic;singleton:=true\nBundle-Version: 1.0.0\n"
                "Require-Bundle: org.eclipse.core.resources,org.eclipse.core.runtime,\n"
                " org.eclipse.jdt.core,org.eclipse.jdt.launching,org.eclipse.jdt.groovy.core,\n"
                " org.codehaus.groovy,com.google.gson\n\n")
    with zipfile.ZipFile(bundle, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("META-INF/MANIFEST.MF", manifest)
        archive.write(SCENARIO / "headless/plugin.xml", "plugin.xml")
        for path in (compiled / "com").rglob("*.class"):
            archive.write(path, str(path.relative_to(compiled)))
    dependencies.append(bundle)
    bundle_list = evidence_run / "bundles.txt"
    bundle_list.write_text("\n".join(map(str, dependencies)) + "\n")
    started = time.perf_counter()
    run(["java", "-Xmx768m", "-Djava.awt.headless=true", "-cp", str(compiled) + ":" + str(osgi), "EquinoxProbe",
         str(evidence_run), str(bundle_list), str(CACHE / "fixture.jar"), str(evidence_run / "semantics.json")], "headless")
    report = json.loads((evidence_run / "semantics.json").read_text())
    checks = qualify(report)
    log = (evidence_run / "headless.log").read_text()
    if "!STACK" in log:
        checks.append({"case": "GLS-H-RUNTIME", "result": "fail", "gate": "core", "evidence": "Unexpected framework/visitor exception; see headless.log"})
    summary = {"checks": checks, "fullEditorAcceptance": False,
               "notCovered": ["formatting", "codeActions", "semanticTokens", "referencesAPI", "renameAPI", "LSPTransport", "Monaco"],
               "processWallMs": (time.perf_counter() - started) * 1000,
               "gitHead": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
               "assembly": json.loads(prepare_headless.COMPILER.with_suffix(".assembly.json").read_text())}
    (evidence_run / "results.json").write_text(json.dumps(summary, indent=2) + "\n")
    (EVIDENCE / "latest.json").write_text(json.dumps({"run": str(evidence_run)}, indent=2) + "\n")
    for check in checks:
        print(check["result"].upper(), check["case"], "[" + check["gate"] + "]")
    print("Complete editor acceptance:", summary["fullEditorAcceptance"], "; unimplemented capabilities:", ", ".join(summary["notCovered"]))
    print("Evidence:", evidence_run)
    required = [c for c in checks if args.gate == "editor" or c["gate"] == "core"]
    if any(c["result"] != "pass" for c in required):
        raise SystemExit(1)


if __name__ == "__main__":
    main()

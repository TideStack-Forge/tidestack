#!/usr/bin/env python3
"""Build a pinned community server against Groovy 4.0.30; qualify real LSP results."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request
import zipfile

from lsp_session import LspSession

SCENARIO = Path(__file__).resolve().parents[1]
ROOT = SCENARIO.parents[4]
CACHE = ROOT / "tmp/groovy-semantic-prototype"
EVIDENCE = ROOT / "tmp/testing-evidence/groovy-language-service/community"
COMMIT = "347d098a928707223ce44b52cc45174a6327a5f3"
SOURCE = CACHE / "upstream" / f"groovy-language-server-{COMMIT}"
GROOVY = Path.home() / ".m2/repository/org/apache/groovy/groovy/4.0.30/groovy-4.0.30.jar"
IMPORTS = "import probe.Customer\nimport probe.CustomerApi\n"


def command(arguments: list[str], log: Path) -> None:
    with log.open("w") as output:
        subprocess.run(arguments, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT,
                       check=True, timeout=240)


def prepare() -> str:
    CACHE.mkdir(parents=True, exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    if not SOURCE.is_dir():
        archive = CACHE / "community-source.zip"
        url = f"https://codeload.github.com/GroovyLanguageServer/groovy-language-server/zip/{COMMIT}"
        with urllib.request.urlopen(url, timeout=45) as response:
            archive.write_bytes(response.read())
        with zipfile.ZipFile(archive) as source:
            source.extractall(CACHE / "upstream")
    build = CACHE / "community-build"
    classpath_file = CACHE / "community-classpath.txt"
    command(["mvn", "-B", "-q", "-f", str(SCENARIO / "community-pom.xml"),
             f"-Dcommunity.source.dir={SOURCE / 'src/main/java'}", f"-Dprobe.build.dir={build}",
             "compile", "dependency:build-classpath", f"-Dmdep.outputFile={classpath_file}"],
            EVIDENCE / "build.log")
    fixture_classes = CACHE / "fixture-classes"
    fixture_classes.mkdir(exist_ok=True)
    command(["javac", "--release", "21", "-cp", str(GROOVY), "-d", str(fixture_classes),
             *map(str, sorted((SCENARIO / "fixtures").rglob("*.java")))], EVIDENCE / "fixture-build.log")
    fixture_jar = CACHE / "fixture.jar"
    command(["jar", "--create", "--file", str(fixture_jar), "-C", str(fixture_classes), "."],
            EVIDENCE / "fixture-jar.log")
    metadata = {"upstreamCommit": COMMIT, "groovy": "4.0.30", "runtimeVersionChanged": False,
                "sourceArchiveSha256": hashlib.sha256((CACHE / "community-source.zip").read_bytes()).hexdigest(),
                "java": subprocess.run(["java", "-version"], capture_output=True, text=True).stderr}
    (EVIDENCE / "environment.json").write_text(json.dumps(metadata, indent=2) + "\n")
    return str(build / "classes") + ":" + classpath_file.read_text().strip()


def position(source: str) -> tuple[str, dict]:
    offset = source.index("@cursor")
    prefix = source[:offset]
    return source.replace("@cursor", ""), {"line": prefix.count("\n"),
                                                "character": len(prefix.rsplit("\n", 1)[-1])}


def qualify(classpath: str) -> list[dict]:
    workspace = CACHE / "community-workspace"
    workspace.mkdir(exist_ok=True)
    session = LspSession(["java", "-Xms64m", "-Xmx512m", "-XX:ActiveProcessorCount=4", "-cp",
                          classpath, "net.prominic.groovyls.GroovyLanguageServer"], EVIDENCE, workspace)
    results = []
    try:
        initialized = session.request("initialize", {"processId": None, "rootUri": workspace.as_uri(),
                                      "capabilities": {"textDocument": {"completion": {"completionItem": {"snippetSupport": True}}}}})
        capabilities = initialized["capabilities"]
        results.append({"case": "GLS-INIT", "result": "pass", "observed": initialized})
        session.notify("initialized", {})
        session.notify("workspace/didChangeConfiguration", {"settings": {"groovy": {"classpath": [str(GROOVY), str(CACHE / "fixture.jar")]}}})

        def opened(case_id: str, text: str) -> tuple[str, dict, list]:
            text, cursor = position(text)
            uri = (workspace / f"{case_id}.groovy").as_uri()
            after = len(session.notifications)
            session.notify("textDocument/didOpen", {"textDocument": {"uri": uri, "languageId": "groovy", "version": 1, "text": text}})
            # LSP does not require an initial empty diagnostics notification for a valid file.
            session.request("textDocument/documentSymbol", {"textDocument": {"uri": uri}})
            diagnostic = [item for message in session.notifications[after:]
                          if message.get("method") == "textDocument/publishDiagnostics"
                          and message["params"]["uri"] == uri
                          for item in message["params"]["diagnostics"]]
            return uri, {"textDocument": {"uri": uri}, "position": cursor}, diagnostic

        completions = [
            ("GLS-STRING", "String value = 'x'\nvalue.toUp@cursor", "toUpperCase"),
            ("GLS-LOCAL", "def value = 'x'\nvalue.toUp@cursor", "toUpperCase"),
            ("GLS-JAVA", IMPORTS + "Customer c = new Customer()\nc.getN@cursor", "getName"),
            ("GLS-GENERIC", IMPORTS + "List<Customer> cs = []\ncs.get(0).getN@cursor", "getName"),
            ("GLS-IT", IMPORTS + "List<Customer> cs = []\ncs.findAll { it.getN@cursor }", "getName"),
            ("GLS-COLLECT", IMPORTS + "List<Customer> cs = []\ndef names = cs.collect { it.name }\nnames.get(0).toUp@cursor", "toUpperCase"),
            ("GLS-DELEGATE", IMPORTS + "CustomerApi.withBuilder { appendT@cursor }", "appendTitle"),
            ("GLS-CONTEXT-PROJECTION", "import probe.ContextRoot\nContextRoot $0 = new ContextRoot()\n$0.customer.getN@cursor", "getName"),
            ("GLS-INCOMPLETE", "String value = 'x'\nvalue.@cursor", "toUpperCase"),
        ]
        for case_id, text, expected in completions:
            uri, params, diagnostics = opened(case_id, text)
            start = time.monotonic()
            result = session.request("textDocument/completion", params)
            items = result if isinstance(result, list) else (result or {}).get("items", [])
            labels = [item.get("label", "") for item in items]
            passed = any(label == expected or label.startswith(expected + "(") for label in labels)
            results.append({"case": case_id, "result": "pass" if passed else "fail", "expected": expected,
                            "labels": labels, "diagnostics": diagnostics, "milliseconds": (time.monotonic() - start) * 1000})
            session.notify("textDocument/didClose", {"textDocument": {"uri": uri}})

        uri, params, _ = opened("GLS-HOVER", "String value = 'x'\nva@cursorlue")
        hover = session.request("textDocument/hover", params)
        results.append({"case": "GLS-HOVER", "result": "pass" if "String" in json.dumps(hover) else "fail", "observed": hover})
        session.notify("textDocument/didClose", {"textDocument": {"uri": uri}})
        uri, params, _ = opened("GLS-SIGNATURE", "String value = 'x'\nvalue.substring(0, @cursor1)")
        signature = session.request("textDocument/signatureHelp", params)
        signatures = (signature or {}).get("signatures", [])
        results.append({"case": "GLS-SIGNATURE", "result": "pass" if any("substring" in s.get("label", "") and s.get("parameters") for s in signatures) else "fail", "observed": signature})
        session.notify("textDocument/didClose", {"textDocument": {"uri": uri}})

        uri, params, _ = opened("GLS-SYMBOL", "def target = 'x'\nprintln ta@cursorrget\nclass Other { def f(target) { target } }")
        definitions = session.request("textDocument/definition", params)
        locations = definitions if isinstance(definitions, list) else [definitions] if definitions else []
        correct = any(loc.get("uri", loc.get("targetUri")) == uri
                      and loc.get("range", loc.get("targetSelectionRange", {})).get("start")
                      == {"line": 0, "character": 4} for loc in locations)
        results.append({"case": "GLS-DEFINITION", "result": "pass" if correct else "fail", "observed": definitions})
        references = session.request("textDocument/references", {**params, "context": {"includeDeclaration": True}})
        lines = sorted(loc["range"]["start"]["line"] for loc in references or [] if loc["uri"] == uri)
        results.append({"case": "GLS-REFERENCES", "result": "pass" if lines == [0, 1] else "fail", "observed": references})
        rename = session.request("textDocument/rename", {**params, "newName": "renamed"})
        changes = (rename or {}).get("changes", {}).get(uri, [])
        for document in (rename or {}).get("documentChanges", []):
            if document.get("textDocument", {}).get("uri") == uri:
                changes.extend(document.get("edits", []))
        changed_lines = sorted(edit["range"]["start"]["line"] for edit in changes)
        results.append({"case": "GLS-RENAME", "result": "pass" if changed_lines == [0, 1] and all(edit["newText"] == "renamed" for edit in changes) else "fail", "observed": rename})
        session.notify("textDocument/didClose", {"textDocument": {"uri": uri}})

        uri, _, broken = opened("GLS-DIAGNOSTIC", "def broken = @cursor")
        after = len(session.notifications)
        session.notify("textDocument/didChange", {"textDocument": {"uri": uri, "version": 2}, "contentChanges": [{"text": "def fixed = 42\nfixed"}]})
        fixed = session.diagnostics(uri, after=after)
        results.append({"case": "GLS-DIAGNOSTIC", "result": "pass" if broken and not fixed else "fail", "broken": broken, "fixed": fixed})
        results.append({"case": "GLS-CONTEXT", "result": "gated", "reason": "Platform semantic projection is the next independent stage; GLS-CONTEXT-PROJECTION only validates the generated declaration shape."})
        for capability in ["documentFormattingProvider", "codeActionProvider", "semanticTokensProvider"]:
            results.append({"case": "GLS-EXTRA-CAPABILITIES", "capability": capability,
                            "result": "not-covered", "reason": "not advertised" if not capabilities.get(capability) else "requires separate behavioral qualification"})
    finally:
        session.close()
    return results


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for tool in ["java", "javac", "jar", "mvn"]:
        if not shutil.which(tool):
            raise SystemExit(f"Missing tool: {tool}")
    if args.check:
        print(json.dumps({"root": str(ROOT), "sourceCached": SOURCE.is_dir(), "groovyCached": GROOVY.is_file(),
                          "state": "ready-to-prepare", "java": subprocess.run(["java", "-version"], capture_output=True, text=True).stderr}, indent=2))
        return
    results = qualify(prepare())
    (EVIDENCE / "results.json").write_text(json.dumps(results, indent=2, ensure_ascii=False) + "\n")
    for result in results:
        print(result["case"], result["result"], result.get("expected", result.get("capability", "")))
    print("Evidence:", EVIDENCE)
    # Qualification gaps are results, not a broken harness. Product promotion must require all mandatory cases.


if __name__ == "__main__":
    main()

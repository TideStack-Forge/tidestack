#!/usr/bin/env python3
"""Export real CodeContextService snapshots for the semantic qualification; no product server needed."""
from pathlib import Path
import json
import subprocess
import xml.etree.ElementTree as ET

SCENARIO = Path(__file__).resolve().parents[1]
ROOT = SCENARIO.parents[4]
EVIDENCE = ROOT / "tmp/testing-evidence/groovy-language-service/platform"
MODULE = "core/ouroboros-logicflow/ouroboros-logicflow-dev"


def main() -> None:
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    with (EVIDENCE / "build.log").open("w") as log:
        subprocess.run(["mvn", "-q", "-pl", MODULE, "-am", "test", "-Dtest=LogicFlowCodeContextProviderTest",
                        "-Dsurefire.failIfNoSpecifiedTests=false"], cwd=ROOT, stdout=log,
                       stderr=subprocess.STDOUT, check=True, timeout=240)
    report = ROOT / MODULE / "target/surefire-reports/TEST-com.ouroboros.logicflow.dev.scriptintelligence.LogicFlowCodeContextProviderTest.xml"
    classpath = next(p.attrib["value"] for p in ET.parse(report).getroot().find("properties")
                     if p.attrib["name"] == "java.class.path")
    classes = ROOT / "tmp/groovy-semantic-prototype/platform-classes"
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run(["javac", "--release", "21", "-cp", classpath, "-d", str(classes),
                    str(SCENARIO / "platform/ContextSnapshotProbe.java")], cwd=ROOT, check=True)
    with (EVIDENCE / "export.log").open("w") as log:
        subprocess.run(["java", "-cp", str(classes) + ":" + classpath + ":" + str(ROOT / "tmp/groovy-semantic-prototype/fixture.jar"),
                        "com.ouroboros.testing.groovy.ContextSnapshotProbe", str(EVIDENCE)],
                       cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=30)
    responses = [json.loads((EVIDENCE / f"context-{kind}.json").read_text()) for kind in ("string", "integer")]
    for response, kind in zip(responses, ("string", "integer")):
        bindings = {b["name"]: b["shape"]["schema"] for b in response["snapshot"]["globals"]}
        root = bindings["$0"]
        assert root["x-representation"] == "map", root
        assert root["properties"]["customers"]["items"]["properties"]["name"]["type"] == kind, root
        assert root["properties"]["profile"]["properties"]["label"]["type"] == kind, root
        assert root == bindings["$mapped"], bindings
        assert bindings["javaContext"]["x-javaType"] == "probe.ContextRoot", bindings
        assert not response["snapshot"]["diagnostics"], response
    assert responses[0]["contextVersion"] != responses[1]["contextVersion"]
    print("PASS: validation → mapping → script bindings; context revision changes; script body not executed")
    print("Evidence:", EVIDENCE)


if __name__ == "__main__":
    main()

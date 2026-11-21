#!/usr/bin/env python3
"""Run the installed-channel composition after Maven feature tests have built their classpaths."""
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


def main():
    source = Path(__file__).resolve()
    root = source.parents[5]
    classpath = []
    for module in (root / "ability/ouroboros-notification", root / "ability/ouroboros-configuration"):
        for report in module.rglob("target/surefire-reports/TEST-*.xml"):
            prop = ET.parse(report).find("properties/property[@name='java.class.path']")
            if prop is not None:
                classpath.extend(prop.get("value").split(os.pathsep))
    # Channels without tests still contribute their installed production SPI descriptors.
    classpath.extend(str(directory) for directory in
                     (root / "ability/ouroboros-notification/ouroboros-notification-channel").glob("*/target/classes"))
    # Production SPI composition excludes local test-only supplier registrations.
    classpath = list(dict.fromkeys(part for part in classpath if not part.endswith("/test-classes")))
    classpath.sort(key=lambda part: not part.startswith(str(root)))
    if not classpath:
        raise SystemExit("Run the notification/configuration Maven tests first")
    evidence = root / "tmp/testing-evidence/notification-runtime-management"
    evidence.mkdir(parents=True, exist_ok=True)
    agents = list((Path.home() / ".m2/repository/org/jacoco/org.jacoco.agent/0.8.15").glob("*-runtime.jar"))
    command = ["java", "-Dnet.bytebuddy.experimental=true"]
    if agents:
        command.append(f"-javaagent:{agents[0]}=destfile={evidence / 'channel-composition.exec'},append=false")
    command.extend(["-cp", os.pathsep.join(classpath), str(source.with_name("ChannelManagementScenario.java"))])
    result = subprocess.run(command, cwd=root)
    if result.returncode:
        raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()

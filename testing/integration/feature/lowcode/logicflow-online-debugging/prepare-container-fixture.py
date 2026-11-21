#!/usr/bin/env python3
"""LF-DBG-CONTAINER-001: stage source reactor classes and real extend JARs for the Docker harness."""
import argparse
import io
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile


def archive_classes(source, output):
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as jar:
        for item in sorted(source.rglob('*')):
            jar.write(item, str(item.relative_to(source)) + ('/' if item.is_dir() else ''))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--classpath-report', type=Path, required=True, help='Platform Surefire XML with surefire.test.class.path')
    parser.add_argument('--loader-tools', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--base-image', required=True)
    parser.add_argument('--rabbit-host', default='host.docker.internal')
    parser.add_argument('--rabbit-port', type=int, required=True)
    args = parser.parse_args()
    repo = args.repo.resolve()
    output = args.output.resolve()
    output.relative_to(repo / 'tmp')
    if output.exists():
        raise SystemExit('Use a new empty output directory; existing fixture evidence is preserved')
    props = {node.attrib['name']: node.attrib.get('value', '') for node in ET.parse(args.classpath_report).findall('./properties/property')}
    classpath = props.get('surefire.test.class.path', props.get('java.class.path'))
    if not classpath:
        raise SystemExit('Missing actual test classpath in Surefire report')
    paths = [Path(value) for value in classpath.split(':') if value]
    for path in paths:
        if not path.exists() and (path.name != 'classes' or (path.parent.parent / 'src/main').exists()):
            raise SystemExit(f'Classpath is incomplete; run platform test-compile first: {path}')
    paths = [path for path in paths if path.exists()]
    support = {
        'ouroboros-logicflow-debug-helper': repo / 'core/ouroboros-logicflow/ouroboros-logicflow-debug-helper',
        'ouroboros-dev-helper-core': repo / 'dev-module/ouroboros-dev-helper/ouroboros-dev-helper-core',
        'ouroboros-dev-helper-runtime': repo / 'dev-module/ouroboros-dev-helper/ouroboros-dev-helper-runtime',
        'ouroboros-common-dev': repo / 'libs/common/common-dev',
    }
    (output / 'image/lib').mkdir(parents=True)
    (output / 'support').mkdir()
    for name, module in support.items():
        artifacts = [p for p in (module / 'target').glob('*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar', '-tests.jar'))]
        if len(artifacts) != 1:
            raise SystemExit(f'Package source artifact first: {module}')
        shutil.copy2(artifacts[0], output / 'support' / (name + '.jar'))
    inventory = []
    for index, source in enumerate(paths):
        if 'test-classes' in source.parts or any(source.is_relative_to(module) for module in support.values()):
            continue
        target = output / 'image/lib' / f'{index:04d}-{source.parent.parent.name if source.is_dir() else source.name}.jar'
        if source.is_dir():
            archive_classes(source, target)
        else:
            shutil.copy2(source, target)
        inventory.append({'source': str(source), 'target': target.name})
    fixture_classes = repo / 'platform/ouroboros-develop/target/test-classes/com/ouroboros/develop/logicflowdebug'
    with zipfile.ZipFile(args.loader_tools) as tools:
        loader = tools.read('META-INF/loader/spring-boot-loader.jar')
    with zipfile.ZipFile(io.BytesIO(loader)) as loader, zipfile.ZipFile(output / 'image/fixture.jar', 'w', zipfile.ZIP_DEFLATED) as fixture:
        for item in loader.infolist():
            if not item.is_dir() and item.filename != 'META-INF/MANIFEST.MF':
                fixture.writestr(item.filename, loader.read(item))
        fixture.writestr('BOOT-INF/classes/', '')
        fixture.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\nMain-Class: org.springframework.boot.loader.launch.PropertiesLauncher\r\nStart-Class: com.ouroboros.develop.logicflowdebug.DebugMqFixture\r\n\r\n')
        classes = list(fixture_classes.glob('DebugMqFixture*.class'))
        if not classes:
            raise SystemExit('Compile DebugMqFixture first')
        for item in classes:
            fixture.write(item, 'BOOT-INF/classes/com/ouroboros/develop/logicflowdebug/' + item.name)
    command = ['java', '-Dloader.path=/app/lib,/app/deploy/extend', f'-Dlogicflow.rabbit.host={args.rabbit_host}', '-jar', '/app/fixture.jar',
               'debug-container-it', 'alice', str(args.rabbit_port), '/app/deploy/identity.json', 'watch']
    (output / 'image/Dockerfile').write_text(f'FROM {args.base_image}\nLABEL codex.task="logicflow-online-debug"\nCOPY lib /app/lib\nCOPY fixture.jar /app/fixture.jar\nENTRYPOINT ' + json.dumps(command) + '\n')
    (output / 'classpath-inventory.json').write_text(json.dumps(inventory, indent=2))
    print(output)


if __name__ == '__main__':
    main()

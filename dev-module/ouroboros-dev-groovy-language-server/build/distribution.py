#!/usr/bin/env python3
"""Build the isolated, pinned Groovy 4.0.30 language server distribution."""
import hashlib
import concurrent.futures
import json
from pathlib import Path
import shutil
import subprocess
import zipfile
import compiler

MODULE = Path(__file__).resolve().parents[1]
TARGET = MODULE / 'target'
DIST = TARGET / 'distribution'


def main():
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as downloads:
        list(downloads.map(compiler.fetch, compiler.MANIFEST['files'] + compiler.MANIFEST['sources']))
    if not compiler.assembly_valid():
        compiler.assemble()
    TARGET.mkdir(exist_ok=True)
    subprocess.run(['mvn', '-B', '-q', '-f', str(MODULE / 'build/dependencies.xml'),
                    'dependency:build-classpath', '-Dmdep.outputFile=' + str(TARGET / 'dependencies.txt')], check=True)
    jars = [Path(p) for p in (TARGET / 'dependencies.txt').read_text().strip().split(':')]
    repo = MODULE.parents[1]
    intelligence = next(p for p in (repo / 'libs/common/common-code-intelligence/target').glob('common-code-intelligence-*.jar') if not p.name.endswith('-sources.jar'))
    utility = next(p for p in (repo / 'libs/util/ouroboros-util/target').glob('ouroboros-util-*.jar') if not p.name.endswith('-sources.jar'))
    embedded = [intelligence, utility, *[p for p in jars if p.name.startswith('jackson-')]]
    osgi = next(p for p in jars if p.name.startswith('org.eclipse.osgi-'))
    bundles = [p for p in jars if not p.name.startswith(('org.eclipse.osgi-', 'org.eclipse.jdt.core-', 'ecj-', 'jackson-'))]
    core = compiler.CACHE / 'ge-core'
    bundles += [core / 'jdt-groovy-core.jar', core / 'groovy-eclipse-core.jar']
    bundles += [p for p in core.glob('org.*.jar') if not p.name.startswith('org.codehaus.groovy_')]
    bundles.append(compiler.COMPILER)
    expanded = TARGET / 'compiler-libs'
    expanded.mkdir(exist_ok=True)
    with zipfile.ZipFile(compiler.COMPILER) as archive:
        for name in ('eclipse-trace.jar', 'groovy-eclipse.jar', 'groovy-parser2.jar', 'lib/ivy-2.5.3.jar', 'lib/groovy-4.0.30.jar'):
            path = expanded / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(archive.read(name))
    classes = TARGET / 'server-classes'
    classes.mkdir(exist_ok=True)
    classpath = ':'.join(map(str, [osgi, *bundles, *embedded, *expanded.rglob('*.jar')]))
    subprocess.run(['javac', '--release', '21', '-cp', classpath, '-d', str(classes),
                    *map(str, (MODULE / 'src/server/java').rglob('*.java'))], check=True)
    (DIST / 'lib').mkdir(parents=True, exist_ok=True)
    server = DIST / 'lib/ouroboros-groovy-server.jar'
    manifest = ('Manifest-Version: 1.0\nBundle-ManifestVersion: 2\n'
                'Bundle-SymbolicName: com.ouroboros.dev.groovy;singleton:=true\nBundle-Version: 1.0.0\n'
                'Bundle-ClassPath: .,groovy-lexer.jar,' + ','.join(p.name for p in embedded) + '\n'
                'Require-Bundle: org.eclipse.core.resources,org.eclipse.core.runtime,\n'
                ' org.eclipse.jdt.core,org.eclipse.jdt.launching,org.eclipse.jdt.groovy.core,\n'
                ' org.codehaus.groovy,com.google.gson,org.eclipse.lsp4j,\n'
                ' org.eclipse.lsp4j.jsonrpc\n\n')
    with zipfile.ZipFile(server, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('META-INF/MANIFEST.MF', manifest)
        archive.write(MODULE / 'src/server/plugin.xml', 'plugin.xml')
        for path in embedded:
            archive.write(path, path.name)
        import io
        lexer = io.BytesIO()
        with zipfile.ZipFile(expanded / 'lib/groovy-4.0.30.jar') as runtime, zipfile.ZipFile(lexer, 'w') as lexical:
            for name in runtime.namelist():
                if name.startswith(('groovyjarjarantlr4/', 'org/apache/groovy/parser/antlr4/')) or name in ('META-INF/LICENSE', 'META-INF/NOTICE'):
                    lexical.writestr(name, runtime.read(name))
        archive.writestr('groovy-lexer.jar', lexer.getvalue())
        for path in classes.rglob('*.class'):
            archive.write(path, str(path.relative_to(classes)))
    for path in [osgi, *bundles]:
        shutil.copy2(path, DIST / 'lib' / path.name)
    (DIST / 'bundles.txt').write_text('\n'.join('lib/' + p.name for p in [*bundles, server]) + '\n')
    launch = TARGET / 'launcher-classes'
    launch.mkdir(exist_ok=True)
    subprocess.run(['javac', '--release', '21', '-cp', str(osgi), '-d', str(launch),
                    *map(str, (MODULE / 'src/launcher/java').rglob('*.java'))], check=True)
    with zipfile.ZipFile(DIST / 'launcher.jar', 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nMain-Class: com.ouroboros.dev.groovy.GroovyLanguageServerMain\nClass-Path: lib/' + osgi.name + '\n\n')
        for path in launch.rglob('*.class'):
            archive.write(path, str(path.relative_to(launch)))
    host = TARGET / 'host-classes'
    host.mkdir(exist_ok=True)
    gson = next(p for p in jars if p.name.startswith('gson-'))
    host_sources = MODULE.parent / 'ouroboros-dev-code-intelligence/src/host/java'
    subprocess.run(['javac', '--release', '21', '-cp', str(gson), '-d', str(host),
                    *map(str, host_sources.rglob('*.java'))], check=True)
    with zipfile.ZipFile(DIST / 'host.jar', 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nMain-Class: com.ouroboros.dev.language.LanguageServiceHost\nClass-Path: lib/' + gson.name + '\n\n')
        for path in host.rglob('*.class'):
            archive.write(path, str(path.relative_to(host)))
    (DIST / 'engine.json').write_text(json.dumps({
        'language': 'groovy', 'engineVersion': 'groovy-4.0.30',
        'command': ['java', '-Xms64m', '-Xmx768m', '-XX:ActiveProcessorCount=4', '-Djava.awt.headless=true',
                    '-jar', '/opt/language/launcher.jar', '{workspace}', '{classpath}']
    }, indent=2) + '\n')
    (DIST / 'Dockerfile').write_text('FROM eclipse-temurin:21-jre\nWORKDIR /opt/language\nCOPY . .\nEXPOSE 8099\nHEALTHCHECK --interval=1s --timeout=1s --start-period=30s CMD test -f /tmp/language/ready\nENTRYPOINT ["java", "-Xmx128m", "-jar", "host.jar", "8099", "/tmp/language", "/opt/language/engine.json"]\n')
    provenance = json.loads(compiler.COMPILER.with_suffix('.assembly.json').read_text())
    provenance['distributionFiles'] = {str(p.relative_to(DIST)): hashlib.sha256(p.read_bytes()).hexdigest()
                                       for p in sorted(DIST.rglob('*')) if p.is_file() and p.name != 'provenance.json'}
    (DIST / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
    shutil.make_archive(str(TARGET / 'ouroboros-dev-groovy-language-server'), 'zip', DIST)
    print('Built', DIST)


if __name__ == '__main__':
    main()

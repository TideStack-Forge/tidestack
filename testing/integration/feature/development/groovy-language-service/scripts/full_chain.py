#!/usr/bin/env python3
"""Launch independent helper/platform JVMs with real Rabbit environment sync and platform-owned LSP."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import signal
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[6]
SCENARIO = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'tmp/groovy-semantic-prototype'
EVIDENCE = ROOT / 'tmp/testing-evidence/groovy-language-service/chain'


def test_classpath(module, test):
    report = ROOT / module / 'target/surefire-reports' / ('TEST-' + test + '.xml')
    return next(p.attrib['value'] for p in ET.parse(report).find('properties') if p.attrib['name'] == 'java.class.path').split(':')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--container-image')
    args, java_args = parser.parse_known_args()
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    if args.prepare:
        command = ['mvn', '-B', '-q', '-pl', 'dev-module/ouroboros-dev-helper/ouroboros-dev-helper-runtime,dev-module/ouroboros-dev-code-intelligence,core/ouroboros-logicflow/ouroboros-logicflow-dev,dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-docker', '-am', 'test',
                   '-Dtest=ApplicationCodeEnvironmentTest,LanguageServerRuntimeTest,LogicFlowCodeContextProviderTest,DockerLanguageRuntimeIntegrationTest', '-Dsurefire.failIfNoSpecifiedTests=false',
                   '-Dgroovy.language.distribution=' + str(ROOT / 'dev-module/ouroboros-dev-groovy-language-server/target/distribution')]
        with (EVIDENCE / 'prepare.log').open('w') as log:
            subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
    paths = test_classpath('dev-module/ouroboros-dev-helper/ouroboros-dev-helper-runtime', 'com.ouroboros.devhelper.codeenvironment.ApplicationCodeEnvironmentTest')
    paths += test_classpath('core/ouroboros-logicflow/ouroboros-logicflow-dev', 'com.ouroboros.logicflow.dev.scriptintelligence.LogicFlowCodeContextProviderTest')
    paths += test_classpath('dev-module/ouroboros-dev-code-intelligence', 'com.ouroboros.dev.codeintelligence.language.LanguageServerRuntimeTest')
    paths += test_classpath('dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-docker', 'com.ouroboros.dev.runtime.docker.backend.DockerLanguageRuntimeIntegrationTest')
    classpath = ':'.join(dict.fromkeys(paths))
    if args.container_image:
        java_args += ['--ouroboros.code-intelligence.language.deployment=container', '--ouroboros.code-intelligence.language.images.groovy=' + args.container_image,
                      '--ouroboros.dev.container.docker-api.host=' + os.environ.get('DOCKER_HOST', 'unix:///var/run/docker.sock')]
    classes = CACHE / 'chain-classes'
    classes.mkdir(exist_ok=True)
    subprocess.run(['javac', '--release', '21', '-cp', classpath, '-d', str(classes), str(SCENARIO / 'platform/GroovyChainServer.java')], cwd=ROOT, check=True)
    environment = dict(os.environ)
    if 'SPRING_RABBITMQ_PASSWORD' not in environment:
        # Reuse the local development broker's configured credential without printing it to evidence.
        inspected = json.loads(subprocess.check_output(['docker', 'inspect', 'ouroboros-rabbitmq']))[0]
        configured = dict(e.split('=', 1) for e in inspected['Config']['Env'] if '=' in e)
        environment['SPRING_RABBITMQ_USERNAME'] = configured.get('RABBITMQ_DEFAULT_USER', 'guest')
        environment['SPRING_RABBITMQ_PASSWORD'] = configured.get('RABBITMQ_DEFAULT_PASS', 'guest')
    environment.setdefault('SPRING_RABBITMQ_HOST', '127.0.0.1')
    command = ['java', '-Xmx768m', '-XX:ActiveProcessorCount=4', '-cp', str(classes) + ':' + classpath,
               'com.ouroboros.testing.groovy.GroovyChainServer', str(CACHE / 'fixture.jar'),
               str(ROOT / 'dev-module/ouroboros-dev-groovy-language-server/target/distribution'),
               str(EVIDENCE / 'runtime')]
    processes = []
    def stop(signum, frame):
        raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM, stop)
    try:
        with (EVIDENCE / 'helper.log').open('w') as log:
            helper = subprocess.Popen(command + ['helper', *java_args], cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT)
            processes.append(helper)
            deadline = time.monotonic() + 60
            while 'GROOVY_HELPER_READY' not in (EVIDENCE / 'helper.log').read_text():
                if helper.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError('Helper failed to start; inspect helper.log')
                time.sleep(0.2)
            platform = subprocess.Popen(command + ['platform', *java_args], cwd=ROOT, env=environment)
            processes.append(platform)
            (EVIDENCE / 'processes.json').write_text(json.dumps({'helperPid': helper.pid, 'platformPid': platform.pid}))
            platform.wait()
    except KeyboardInterrupt:
        pass
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                process.terminate()
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired: process.kill(); process.wait()


if __name__ == '__main__':
    main()

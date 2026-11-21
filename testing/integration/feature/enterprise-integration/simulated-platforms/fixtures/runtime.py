"""Own the disposable network, real databases, provider service and formal backend nodes."""
import argparse
import datetime
import json
import os
import re
from pathlib import Path
import secrets
import socket
import subprocess
import time
import urllib.error
import urllib.request

REPO = Path(__file__).resolve().parents[6]
SCENARIO = Path(__file__).resolve().parents[1]
JAVA_IMAGE = 'registry.cn-hangzhou.aliyuncs.com/ouroboros/eclipse-temurin:21-jdk-noble-arm64'
NODE_IMAGE = 'docker.1ms.run/node:lts-alpine'
MYSQL_IMAGE = 'container-registry.oracle.com/mysql/community-server:8.3.0'
REDIS_IMAGE = 'redis:7-alpine'
HOSTS = ['api.dingtalk.com', 'oapi.dingtalk.com', 'login.dingtalk.com', 'qyapi.weixin.qq.com',
         'open.weixin.qq.com', 'open.feishu.cn', 'accounts.feishu.cn']
LABEL = 'codex.enterprise-simulation'


def command(args, **kwargs):
    return subprocess.run(args, check=True, text=True, capture_output=True, **kwargs).stdout.strip()


def write_private(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')
    path.chmod(0o600)


def free_port():
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0))
        return probe.getsockname()[1]


def mapping(container, port):
    return int(command(['docker', 'port', container, str(port)]).rsplit(':', 1)[1])


def record_manifest(root, manifest):
    write_private(root / 'secrets/runtime.json', manifest)


def start_container(root, manifest, role, options, image, arguments, *, network=None):
    name = manifest['prefix'] + '-' + role
    command(['docker', 'run', '-d', '--name', name, '--label', LABEL + '=' + manifest['id'],
             '--network', network or manifest['network'], *options, image, *arguments])
    manifest['containers'][role] = name
    record_manifest(root, manifest)
    return name


def wait_health(url, timeout=180, headers=None):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers or {}), timeout=3) as response:
                if response.status == 200:
                    return
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            time.sleep(1)
    raise RuntimeError('Runtime did not become healthy: ' + url)


def backend(root, manifest, role):
    classes = REPO / 'platform/ouroboros-mothership/target/classes'
    classpath = (REPO / 'platform/ouroboros-mothership/target/runtime.classpath').read_text().strip()
    # Reactor artifacts remain the formal packaged JARs, without test class injection.
    for entry in classpath.split(':'):
        if not Path(entry).exists():
            raise FileNotFoundError('Missing formal runtime dependency: ' + entry)
    base = REPO / 'platform/ouroboros-mothership/docker/root/app/base/service/application-base.yml'
    options = ['--network-alias', role, '-v', str(REPO) + ':' + str(REPO),
               '-v', str(Path.home() / '.m2') + ':' + str(Path.home() / '.m2') + ':ro',
               '-w', str(REPO)]
    args = ['java', '-Xmx1024m', '-XX:ActiveProcessorCount=4',
            '-Djavax.net.ssl.trustStore=' + str(root / 'secrets/truststore.p12'),
            '-Djavax.net.ssl.trustStorePassword=simulation-only',
            '-cp', str(classes) + ':' + classpath, 'com.ouroboros.mothership.OuroborosApplication',
            '--spring.config.additional-location=file:' + str(base) + ',file:' + str(root / 'local.yml')]
    name = start_container(root, manifest, role, options, JAVA_IMAGE, args)
    port = manifest['ports'][role]
    wait_health('http://127.0.0.1:' + str(port) + '/api/auth/providers', timeout=240)
    return name


def up(root):
    assert root.is_relative_to(REPO / 'tmp'), 'Keep runtime inside this worktree tmp/'
    assert not (root / 'secrets/runtime.json').exists(), 'Existing fixture must be preserved'
    for image in [JAVA_IMAGE, NODE_IMAGE, MYSQL_IMAGE, REDIS_IMAGE]:
        command(['docker', 'image', 'inspect', image])
    root.mkdir(parents=True, exist_ok=True)
    private = root / 'secrets'
    private.mkdir(mode=0o700)
    fixture_id = secrets.token_hex(4)
    manifest = {'id': fixture_id, 'prefix': 'codex-eis-' + fixture_id,
                'network': 'codex-eis-' + fixture_id, 'containers': {}, 'ports': {},
                'repo': str(REPO), 'root': str(root)}
    record_manifest(root, manifest)
    command(['docker', 'network', 'create', '--internal', '--label', LABEL + '=' + fixture_id, manifest['network']])
    password = secrets.token_urlsafe(30)
    admin_password = secrets.token_urlsafe(22)
    mysql_env = private / 'mysql.env'
    mysql_env.write_text('MYSQL_ROOT_PASSWORD=' + password + '\nMYSQL_ROOT_HOST=%\nMYSQL_DATABASE=enterprise_simulation\n')
    mysql_env.chmod(0o600)
    start_container(root, manifest, 'mysql', ['--platform', 'linux/amd64', '--network-alias', 'simulation-mysql',
                    '--env-file', str(mysql_env)], MYSQL_IMAGE, ['--innodb-buffer-pool-size=128M'])
    start_container(root, manifest, 'redis', ['--network-alias', 'simulation-redis'], REDIS_IMAGE, [])
    for _ in range(180):
        ready = subprocess.run(['docker', 'exec', manifest['containers']['mysql'], 'mysqladmin', 'ping', '--silent'], capture_output=True)
        if ready.returncode == 0:
            break
        time.sleep(1)
    else:
        raise RuntimeError('Dedicated MySQL not ready')

    san = ','.join('DNS:' + host for host in HOSTS)
    command(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '3',
             '-subj', '/CN=Ouroboros isolated enterprise simulation', '-addext', 'subjectAltName=' + san,
             '-keyout', str(private / 'tls.key'), '-out', str(private / 'tls.crt')])
    (private / 'tls.key').chmod(0o600)
    java_home = command(['/usr/libexec/java_home', '-v', '21'])
    command([java_home + '/bin/keytool', '-importcert', '-noprompt', '-alias', 'enterprise-simulation',
             '-file', str(private / 'tls.crt'), '-keystore', str(private / 'truststore.p12'),
             '-storetype', 'PKCS12', '-storepass', 'simulation-only'])
    providers = {}
    for provider in ['dingtalk', 'wecom', 'feishu']:
        providers[provider] = {'appId': 'sim-' + provider + '-app', 'corpId': 'sim-' + provider + '-corp',
                               'agentId': '10001', 'memberId': provider + '-admin', 'openId': 'ou_' + provider + '_admin',
                               'secret': secrets.token_urlsafe(32), 'callbackToken': secrets.token_urlsafe(24),
                               'aesKey': secrets.token_bytes(32).hex(), 'encryptKey': secrets.token_urlsafe(32)}
    simulation = {'providers': providers, 'controlKey': secrets.token_urlsafe(32),
                  'key': str(private / 'tls.key'), 'cert': str(private / 'tls.crt')}
    write_private(private / 'providers.json', simulation)
    aliases = [part for host in HOSTS for part in ['--network-alias', host]]
    start_container(root, manifest, 'providers', [*aliases, '--network-alias', 'simulation-providers',
                   '-v', str(REPO) + ':' + str(REPO) + ':ro', '-e', 'EIS_CONFIG=' + str(private / 'providers.json')],
                   NODE_IMAGE, ['node', str(SCENARIO / 'fixtures/provider-service.cjs')])
    access = start_container(root, manifest, 'network-access', [
        '-p', '127.0.0.1::8081', '-p', '127.0.0.1::8082', '-p', '127.0.0.1::8083', '-p', '127.0.0.1::8443',
        '-v', str(REPO) + ':' + str(REPO) + ':ro'], NODE_IMAGE,
        ['node', str(SCENARIO / 'fixtures/network-access.cjs')], network='bridge')
    command(['docker', 'network', 'connect', manifest['network'], access])
    for role, port in [('node-a', 8081), ('node-b', 8082), ('control', 8083), ('tls', 8443)]:
        manifest['ports'][role] = mapping(access, port)
    manifest['ports']['gateway'] = free_port()
    record_manifest(root, manifest)
    wait_health('http://127.0.0.1:' + str(manifest['ports']['control']) + '/state',
                headers={'authorization': 'Bearer ' + simulation['controlKey']})
    gateway_origin = 'http://127.0.0.1:' + str(manifest['ports']['gateway'])
    env = os.environ | {'SSO_ISOLATED_FIXTURE': '1', 'SSO_ADMIN_PASSWORD': admin_password,
          'SSO_FIXTURE_ROOT': str(root), 'SSO_BASE_URL': gateway_origin, 'SSO_BACKEND_PORT': '8080',
          'SSO_DB_PASSWORD': password, 'SSO_DB_USER': 'root',
          'SSO_DB_URL': 'jdbc:mysql://simulation-mysql:3306/enterprise_simulation?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'}
    command(['node', str(REPO / 'testing/integration/feature/auth/sso-dynamic-config-oidc/fixtures/init-local.cjs')], env=env, cwd=REPO)
    import shutil
    shutil.copyfile(SCENARIO / 'fixtures/enterprise-notice.notification.json',
                    root / 'metadata/notification-template/enterprise-notice.notification.json')
    config = json.loads((root / 'local.yml').read_text())
    config['server']['address'] = '0.0.0.0'
    config['spring']['data']['redis']['host'] = 'simulation-redis'
    config['spring']['data']['redis']['port'] = 6379
    config['ouroboros']['enterprise'] = {'execution-nodes': 2, 'credentials': {
        'active-key-id': 'original', 'keys': {'original': secrets.token_urlsafe(32), 'replacement': secrets.token_urlsafe(32)}}}
    # The production cipher expects standard Base64 (not URL-safe).
    import base64
    config['ouroboros']['enterprise']['credentials']['keys'] = {
        key: base64.b64encode(secrets.token_bytes(32)).decode() for key in ['original', 'replacement']}
    write_private(root / 'local.yml', config)
    write_private(private / 'acceptance.json', {'base': gateway_origin, 'password': admin_password,
                  'evidence': str(REPO / 'tmp/testing-evidence/enterprise-simulation'), 'root': str(root)})
    backend(root, manifest, 'node-a')
    backend(root, manifest, 'node-b')
    gateway_env = os.environ | {'SSO_ISOLATED_FIXTURE': '1', 'SSO_FIXTURE_ROOT': str(root),
         'SSO_ADMIN_PASSWORD': admin_password, 'SSO_BACKEND_PORT': str(manifest['ports']['node-a']),
         'SSO_FRONTEND_PORT': str(manifest['ports']['gateway'])}
    log = (root / 'gateway.log').open('w')
    os.fchmod(log.fileno(), 0o600)
    process = subprocess.Popen(['node', str(REPO / 'testing/integration/feature/auth/sso-dynamic-config-oidc/fixtures/gateway.cjs')],
                               cwd=REPO, env=gateway_env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    log.close()
    manifest['gatewayPid'] = process.pid
    record_manifest(root, manifest)
    wait_health(gateway_origin + '/api/auth/providers')
    print(json.dumps({'ready': True, 'root': str(root), 'base': gateway_origin, 'nodes': 2}))


def down(root):
    manifest = json.loads((root / 'secrets/runtime.json').read_text())
    stopped = []
    if manifest.get('gatewayPid'):
        import signal
        pid = manifest['gatewayPid']
        probe = subprocess.run(['ps', '-p', str(pid), '-o', 'args='], text=True, capture_output=True)
        if probe.returncode == 0:
            assert 'sso-dynamic-config-oidc/fixtures/gateway.cjs' in probe.stdout, 'Gateway PID changed'
            os.kill(pid, signal.SIGTERM)
    evidence = REPO / 'tmp/testing-evidence/enterprise-simulation'
    evidence.mkdir(parents=True, exist_ok=True)
    for role, name in reversed(list(manifest['containers'].items())):
        inspection = subprocess.run(['docker', 'inspect', name], text=True, capture_output=True)
        if inspection.returncode:
            continue
        item = json.loads(inspection.stdout)[0]
        assert item['Config']['Labels'].get(LABEL) == manifest['id'], 'Container owner mismatch'
        logs = subprocess.run(['docker', 'logs', name], text=True, capture_output=True, check=True)
        private_log = root / (role + '.log')
        private_log.write_text(logs.stdout + logs.stderr)
        private_log.chmod(0o600)
        safe = re.sub(r'(?i)(access_token|code|authCode|state|corpsecret|client_secret|bindingToken|token)=([^&\s"<>]+)', r'\1=REDACTED', logs.stdout + logs.stderr)
        private_settings = json.loads((root / 'secrets/providers.json').read_text())
        for provider in private_settings['providers'].values():
            for field in ['secret', 'callbackToken', 'aesKey', 'encryptKey']:
                safe = safe.replace(provider[field], 'REDACTED')
        safe = safe.replace(private_settings['controlKey'], 'REDACTED')
        (evidence / (role + '.log')).write_text(safe)
        command(['docker', 'rm', '-f', name])
        stopped.append(role)
    inspection = subprocess.run(['docker', 'network', 'inspect', manifest['network']], text=True, capture_output=True)
    if inspection.returncode == 0:
        item = json.loads(inspection.stdout)[0]
        assert item['Labels'].get(LABEL) == manifest['id']
        command(['docker', 'network', 'rm', manifest['network']])
    (evidence / 'cleanup.json').write_text(json.dumps({'time': datetime.datetime.now(datetime.timezone.utc).isoformat(),
           'containersRemoved': stopped, 'networkRemoved': True, 'gatewayStopped': True}, indent=2) + '\n')
    print(json.dumps({'cleaned': stopped, 'networkRemoved': True}))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['up', 'down', 'restart', 'probe', 'database-outage', 'key-stage'])
    parser.add_argument('--root', required=True, type=Path)
    parser.add_argument('--node', choices=['node-a', 'node-b'], default='node-b')
    parser.add_argument('--phase', choices=['callbacks', 'notifications', 'notification-recovery', 'key-before', 'key-after'], default='callbacks')
    parser.add_argument('--key-stage', choices=['dual', 'replacement-only'], default='dual')
    args = parser.parse_args()
    root = args.root.resolve()
    assert root.is_relative_to(REPO / 'tmp'), 'Runtime root must remain inside this worktree tmp/'
    if args.action == 'up':
        up(root)
    elif args.action == 'down':
        down(root)
    elif args.action == 'key-stage':
        config = json.loads((root / 'local.yml').read_text())
        credentials = config['ouroboros']['enterprise']['credentials']
        credentials['active-key-id'] = 'replacement'
        if args.key_stage == 'replacement-only':
            del credentials['keys']['original']
        write_private(root / 'local.yml', config)
        print(json.dumps({'activeKey': 'replacement', 'stage': args.key_stage}))
    elif args.action == 'database-outage':
        manifest = json.loads((root / 'secrets/runtime.json').read_text())
        name = manifest['containers']['mysql']
        item = json.loads(command(['docker', 'inspect', name]))[0]
        assert item['Config']['Labels'].get(LABEL) == manifest['id']
        command(['docker', 'pause', name])
        try:
            subprocess.run(['node', str(SCENARIO / 'scripts/database-outage.cjs'), str(root), 'unavailable'], check=True)
        finally:
            command(['docker', 'unpause', name])
        for node in ['node-a', 'node-b']:
            wait_health('http://127.0.0.1:' + str(manifest['ports'][node]) + '/api/auth/providers', timeout=60)
        subprocess.run(['node', str(SCENARIO / 'scripts/database-outage.cjs'), str(root), 'recovered'], check=True)
    elif args.action == 'probe':
        manifest = json.loads((root / 'secrets/runtime.json').read_text())
        classpath = (REPO / 'platform/ouroboros-mothership/target/runtime.classpath').read_text().strip()
        base = REPO / 'platform/ouroboros-mothership/docker/root/app/base/service/application-base.yml'
        source = SCENARIO / 'scripts/RuntimeMechanisms.java'
        command_args = ['docker', 'exec', '-w', str(REPO), manifest['containers']['node-a'], 'java',
            '-Xmx1024m', '-XX:ActiveProcessorCount=4',
            '-Deis.root=' + str(root), '-Deis.phase=' + args.phase,
            '-Djavax.net.ssl.trustStore=' + str(root / 'secrets/truststore.p12'),
            '-Djavax.net.ssl.trustStorePassword=simulation-only', '-cp',
            str(REPO / 'platform/ouroboros-mothership/target/classes') + ':' + classpath, str(source),
            '--server.port=0', '--spring.config.additional-location=file:' + str(base) + ',file:' + str(root / 'local.yml')]
        result = subprocess.run(command_args, text=True, capture_output=True)
        probe_log = root / ('probe-' + args.phase + '.log')
        probe_log.write_text(result.stdout + result.stderr)
        probe_log.chmod(0o600)
        print('\n'.join(line for line in result.stdout.splitlines() if line.startswith('SIM-')))
        if result.returncode:
            raise RuntimeError('QueryDSL observer failed; inspect the private fixture probe.log')
    else:
        manifest = json.loads((root / 'secrets/runtime.json').read_text())
        name = manifest['containers'][args.node]
        item = json.loads(command(['docker', 'inspect', name]))[0]
        assert item['Config']['Labels'].get(LABEL) == manifest['id']
        command(['docker', 'restart', name])
        wait_health('http://127.0.0.1:' + str(manifest['ports'][args.node]) + '/api/auth/providers', timeout=240)
        print(json.dumps({'restarted': args.node}))


if __name__ == '__main__':
    main()

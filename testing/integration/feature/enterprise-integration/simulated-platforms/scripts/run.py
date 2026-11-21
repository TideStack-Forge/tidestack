"""Reproduce the disposable simulation acceptance and always clean up its owned resources."""
import argparse
import datetime
import json
from pathlib import Path
import re
import subprocess
import sys

REPO = Path(__file__).resolve().parents[6]
SCENARIO = Path(__file__).resolve().parents[1]
RUNTIME = SCENARIO / 'fixtures/runtime.py'


def scrub(text):
    return re.sub(r'(?i)(access_token|code|authCode|state|corpsecret|client_secret|bindingToken|token)=([^&\s"<>]+)',
                  r'\1=REDACTED', text)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', required=True, type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    if not root.is_relative_to(REPO / 'tmp') or root.exists():
        raise SystemExit('Choose a new fixture root inside this worktree tmp/')
    evidence = REPO / 'tmp/testing-evidence/enterprise-simulation'
    evidence.mkdir(parents=True, exist_ok=True)
    completed = []

    def run(name, arguments):
        print(name + ': running', flush=True)
        result = subprocess.run(arguments, cwd=REPO, text=True, capture_output=True, timeout=480)
        output = scrub(result.stdout + result.stderr)
        (evidence / ('run-' + name + '.log')).write_text(output)
        if result.returncode:
            print(output[-6000:], flush=True)
            raise RuntimeError('Acceptance step failed: ' + name)
        completed.append(name)
        print(name + ': passed', flush=True)

    def runtime(name, action, *extra):
        run(name, [sys.executable, str(RUNTIME), action, '--root', str(root), *extra])

    def node(name, script, *extra):
        run(name, ['node', str(SCENARIO / 'scripts' / script), str(root), *extra])

    success = False
    try:
        runtime('bootstrap', 'up')
        node('provider-contract', 'provider-contract.cjs')
        node('internal-mechanisms', 'acceptance.cjs', 'all')
        runtime('reliable-notifications', 'probe', '--phase', 'notifications')
        node('production-browser', 'browser-acceptance.cjs')
        runtime('database-outage', 'database-outage')
        runtime('key-before', 'probe', '--phase', 'key-before')
        runtime('key-dual', 'key-stage')
        for node_name in ['node-a', 'node-b']:
            runtime('dual-key-restart-' + node_name, 'restart', '--node', node_name)
        node('credential-reencryption', 'acceptance.cjs', 'reencrypt')
        runtime('key-replacement-only', 'key-stage', '--key-stage', 'replacement-only')
        for node_name in ['node-a', 'node-b']:
            runtime('new-key-restart-' + node_name, 'restart', '--node', node_name)
        node('node-recovery', 'acceptance.cjs', 'recovery')
        runtime('key-after', 'probe', '--phase', 'key-after')
        runtime('callback-persistence', 'probe', '--phase', 'callbacks')
        runtime('notification-recovery', 'probe', '--phase', 'notification-recovery')
        success = True
    finally:
        try:
            if (root / 'secrets/runtime.json').exists():
                runtime('cleanup', 'down')
        finally:
            summary = {'time': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                       'result': 'pass' if success and 'cleanup' in completed else 'fail',
                       'boundary': 'isolated simulated providers; formal two-node Mothership; real dedicated MySQL and Redis',
                       'completedSteps': completed, 'fixtureRoot': str(root)}
            (evidence / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print('Enterprise simulation acceptance completed; owned resources cleaned.', flush=True)


if __name__ == '__main__':
    main()

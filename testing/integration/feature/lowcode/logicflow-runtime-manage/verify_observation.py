#!/usr/bin/env python3
"""LF-RM-OBS-* against the running two-JVM acceptance assembly."""
import argparse
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--repo', required=True, type=Path)
args = parser.parse_args()
evidence = args.repo.resolve() / 'tmp/testing-evidence/logicflow-runtime-manage'
roots = ['http://127.0.0.1:18886', 'http://127.0.0.1:18887']
base = '/api/runtime/logicflows/models/'
results = []


def call(root, path, data=None, role='editor'):
    request = urllib.request.Request(root + path, data=None if data is None else json.dumps(data).encode(),
        headers={'Content-Type': 'application/json', 'X-Fixture-Role': role}, method='GET' if data is None else 'PUT')
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def get(name, endpoint, root=roots[0]):
    status, body = call(root, base + name + '/' + endpoint)
    assert status == 200, (status, body)
    return body['data']


def await_result(probe, predicate):
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        value = probe()
        if predicate(value): return value
        time.sleep(.2)
    raise AssertionError(value)


def policy(name, monitor, diagnostic):
    before = get(name, 'observation-policy')
    body = dict(expectedRevision=before['revision'], monitorEnabled=monitor, diagnosticEnabled=diagnostic,
        durationMinutes=5, reason='LF-RM-OBS cluster acceptance')
    status, saved = call(roots[0], base + name + '/observation-policy', body)
    assert status == 200, (status, saved)
    revision = saved['data']['revision']
    for root in roots:
        boot = call(root, base + name)[1]['data']['instanceId']
        await_result(lambda: get(name, 'observation-status', root)['items'],
            lambda items: any(item['bootId'] == boot and item['revision'] == revision and item['error'] is None for item in items))
    return saved['data'], body


def execute():
    return [call(root, '/fixture/execute')[1] for root in roots]


def record(case, data):
    results.append({'case': case, 'result': 'pass', 'evidence': data})


before_models = [call(root, base + 'child')[1]['data'] for root in roots]
assert all(not get('parent', 'observation-policy', root)['policy']['monitorEnabled'] for root in roots)
assert not get('other', 'observation-policy')['policy']['diagnosticEnabled']
record('LF-RM-OBS-DEFAULT-001', {'parent': get('parent', 'observation-policy'), 'other': get('other', 'observation-policy')})
child_before = get('child', 'metrics')['completed']
child, stale = policy('child', True, True)
execute()
metrics = await_result(lambda: get('child', 'metrics'), lambda value: value['completed'] >= child_before + 2)
runs = get('child', 'diagnostic-runs')['items']
assert metrics['completed'] == child_before + 2, metrics
assert len({run['bootId'] for run in runs}) == 2
assert all(run['parentInvocationId'] is None and len(run['nodes']) == 2 for run in runs)
assert not get('parent', 'metrics')['collected']
assert not get('parent', 'diagnostic-runs')['items']
assert 'input' not in json.dumps(runs)
after_models = [call(root, base + 'child')[1]['data'] for root in roots]
assert all(before['publicationId'] == after['publicationId'] and before['runtimeRevision'] == after['runtimeRevision']
    for before, after in zip(before_models, after_models))
record('LF-RM-OBS-INDEPENDENT-001', {'metrics': metrics, 'runs': runs, 'publicationUnchanged': True})
status, conflict = call(roots[0], base + 'child/observation-policy', stale)
assert status == 409
assert call(roots[0], base + 'child/observation-policy', stale, role='reader')[0] == 403
assert call(roots[0], base + 'child/metrics', role='reader')[0] == 403
assert call(roots[0], base + 'child/observation-policy', {'application': 'foreign'})[0] == 400
record('LF-RM-OBS-CAS-001', {'staleHttp': status, 'conflict': conflict, 'readerWriteHttp': 403, 'readerMetricsHttp': 403, 'unknownApplicationHttp': 400})
policy('child', False, False)
policy('parent', True, False)
execute()
parent_metrics = await_result(lambda: get('parent', 'metrics'), lambda value: value['completed'] == 2)
assert get('child', 'metrics')['completed'] == metrics['completed']
assert not get('parent', 'diagnostic-runs')['items']
record('LF-RM-OBS-SWAP-001', {'parent': parent_metrics, 'child': get('child', 'metrics'), 'parentRuns': []})
policy('parent', True, True)
policy('child', True, True)
execute()
await_result(lambda: get('parent', 'diagnostic-runs')['items'], lambda value: len(value) == 2)
await_result(lambda: get('child', 'metrics'), lambda value: value['completed'] == metrics['completed'] + 2)
parent_runs = get('parent', 'diagnostic-runs')['items']
child_runs = get('child', 'diagnostic-runs')['items']
parent_ids = {run['id'] for run in parent_runs}
linked = [run for run in child_runs if run['parentInvocationId'] in parent_ids]
assert len(linked) == 2
record('LF-RM-OBS-BOTH-001', {'parents': parent_runs, 'children': linked})
lease = get('child', 'observation-policy')
old_boot = after_models[1]['instanceId']
request = urllib.request.Request('http://127.0.0.1:18888/fixture/restart-node', data=b'{}', headers={'Content-Type': 'application/json'}, method='POST')
with urllib.request.urlopen(request, timeout=20) as response: assert response.status == 200

def restarted():
    try: return get('child', 'observation-policy', roots[1])
    except (OSError, ValueError, AssertionError): return None

restored = await_result(restarted, lambda value: value is not None)
assert restored['policy']['monitorEnabled']
assert restored['policy']['diagnosticExpiresAt'] == lease['policy']['diagnosticExpiresAt']
assert restored['revision'] == lease['revision']
new_model = call(roots[1], base + 'child')[1]['data']
assert new_model['instanceId'] != old_boot
record('LF-RM-OBS-RESTART-001', {'oldBoot': old_boot, 'newBoot': new_model['instanceId'], 'policy': restored})
# Current boot statuses are eventually refreshed. Historical statuses remain readable.
policy('child', False, False)
policy('parent', False, False)
counts = {name: get(name, 'metrics')['completed'] for name in ['child', 'parent']}
execute()
time.sleep(6)
assert all(get(name, 'metrics')['completed'] == counts[name] for name in counts)
assert get('child', 'diagnostic-runs')['items'] and get('parent', 'diagnostic-runs')['items']
record('LF-RM-OBS-STOP-001', {'completedUnchanged': counts, 'historyReadable': True})
(evidence / 'ops-cluster-acceptance.json').write_text(json.dumps({'cases': results}, ensure_ascii=False, indent=2))
print(json.dumps({'pass': len(results), 'evidence': str(evidence / 'ops-cluster-acceptance.json')}, ensure_ascii=False))

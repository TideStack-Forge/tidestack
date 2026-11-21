#!/usr/bin/env python3
"""Verify HTTP → platform-owned LSP with RabbitMQ application environment sync independently of Monaco."""
import concurrent.futures
import http.cookiejar as cookiejar
import json
from pathlib import Path
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[6]
EVIDENCE = ROOT / 'tmp/testing-evidence/groovy-language-service/chain' / time.strftime('run-%Y%m%d-%H%M%S')
BASE = 'http://127.0.0.1:43184/api/dev/example/code-intelligence/sessions'


def main():
    EVIDENCE.mkdir(parents=True)
    client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookiejar.CookieJar()))
    exchanges, checks, sessions = [], [], []

    def http(method, suffix, data=None, opener=client):
        started = time.perf_counter()
        request = urllib.request.Request(BASE + suffix, data=json.dumps(data).encode() if data is not None else None,
                                         headers={'Content-Type': 'application/json'}, method=method)
        try:
            with opener.open(request, timeout=40) as response:
                result = json.loads(response.read())
                status = response.status
        except urllib.error.HTTPError as error:
            status, result = error.code, json.loads(error.read())
        exchanges.append({'method': method, 'suffix': suffix, 'request': data, 'status': status,
                          'elapsedMs': (time.perf_counter() - started) * 1000, 'response': result})
        return result

    def check(case, passed, observed):
        checks.append({'case': case, 'result': 'pass' if passed else 'fail', 'observed': observed})
        print(checks[-1]['result'].upper(), case, flush=True)

    def context():
        return {'documentId': 'inmemory://chain', 'documentVersion': 1, 'draftRevision': 1,
                'codeSite': 'logicflow.script', 'language': 'groovy', 'resource': {'type': 'logicflow', 'name': 'chain'},
                'location': {'nodeName': 'script', 'pointer': '/script'}, 'draft': {
                    'name': 'chain', 'inputSchema': {'type': 'object', 'properties': {'displayName': {'type': 'string'}}},
                    'entryNode': 'script', 'nodes': [{'name': 'script', 'type': 'script', 'language': 'groovy', 'script': ''}], 'connections': []}}

    def open_document(text):
        result = http('POST', '', {'context': context(), 'text': text})
        if result['code'] != 0:
            raise AssertionError(result)
        sessions.append(result['data']['sessionId'])
        return result['data']

    def request(document, method, params):
        result = http('POST', '/' + document['sessionId'] + '/messages', [
            {'jsonrpc': '2.0', 'id': method, 'method': 'textDocument/' + method,
             'params': {'textDocument': {'uri': document['uri']}, **params}}])
        assert result['code'] == 0, result
        messages = result['data']['messages']
        return next(message['result'] for message in messages if 'id' in message and 'result' in message)

    try:
        first = open_document('String value = "hello"\nvalue.')
        check('GLS-C-OPEN', any(message.get('result', {}).get('serverInfo', {}).get('version') == '4.0.30'
                               for message in first['messages']), first)
        items = request(first, 'completion', {'position': {'line': 1, 'character': 6}})
        check('GLS-C-ROUNDTRIP', any(item['label'] == 'toUpperCase' for item in items), items)
        second = open_document('Integer value = 1\nvalue.')
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            a = pool.submit(request, first, 'completion', {'position': {'line': 1, 'character': 6}})
            b = pool.submit(request, second, 'completion', {'position': {'line': 1, 'character': 6}})
            left, right = a.result(), b.result()
        check('GLS-C-CONCURRENT-ISOLATION', any(i['label'] == 'toUpperCase' for i in left)
              and not any(i['label'] == 'toUpperCase' for i in right) and any(i['label'] == 'intValue' for i in right), {'first': left, 'second': right})
        extension = open_document('import probe.CustomerApi\nCustomerApi.load().get(0).')
        items = request(extension, 'completion', {'position': {'line': 1, 'character': 26}})
        check('GLS-C-EXTENSION-CLASSPATH', any(item['label'] == 'address' for item in items), items)
        outsider = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookiejar.CookieJar()))
        denied = http('DELETE', '/' + first['sessionId'], opener=outsider)
        check('GLS-C-OWNER-BOUNDARY', denied.get('code') != 0, denied)
        http('DELETE', '/' + first['sessionId'])
        sessions.remove(first['sessionId'])
        rejected = http('POST', '/' + first['sessionId'] + '/messages', [
            {'jsonrpc': '2.0', 'id': 'closed', 'method': 'textDocument/completion', 'params': {'position': {'line': 0, 'character': 0}}}])
        check('GLS-C-CLOSED-SESSION', rejected.get('code') != 0, rejected)
        warm = [exchange['elapsedMs'] for exchange in exchanges if exchange['method'] == 'POST' and exchange['suffix'].endswith('/messages') and exchange['response'].get('code') == 0]
        check('GLS-C-WARM-LATENCY', bool(warm) and max(warm) < 1500, warm)
    finally:
        for session in sessions:
            http('DELETE', '/' + session)
        (EVIDENCE / 'results.json').write_text(json.dumps({'checks': checks, 'exchanges': exchanges}, indent=2) + '\n')
        print('Evidence:', EVIDENCE)
    if any(check['result'] != 'pass' for check in checks):
        raise SystemExit(1)


if __name__ == '__main__':
    main()

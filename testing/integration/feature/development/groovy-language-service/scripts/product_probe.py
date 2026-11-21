#!/usr/bin/env python3
"""Behavior acceptance against the built product LSP distribution, never fixture completions."""
import json
from pathlib import Path
import time
import subprocess
from lsp_session import LspSession

ROOT = Path(__file__).resolve().parents[6]
DIST = ROOT / 'dev-module/ouroboros-dev-groovy-language-server/target/distribution'
EVIDENCE = ROOT / 'tmp/testing-evidence/groovy-language-service/product' / time.strftime('run-%Y%m%d-%H%M%S')


def main():
    EVIDENCE.mkdir(parents=True)
    classpath = EVIDENCE / 'classpath.json'
    classpath.write_text(json.dumps([str(ROOT / 'tmp/groovy-semantic-prototype/fixture.jar')]))
    session = LspSession(['java', '-Xms64m', '-Xmx768m', '-XX:ActiveProcessorCount=4', '-jar', str(DIST / 'launcher.jar'),
                          str(EVIDENCE / 'runtime'), str(classpath)], EVIDENCE, ROOT)
    results = []
    uri = 'ouroboros://acceptance/main.groovy'
    version = 1
    source = ''

    def check(case, passed, observed):
        results.append({'case': case, 'result': 'pass' if passed else 'fail', 'observed': observed})
        print(results[-1]['result'].upper(), case, flush=True)

    def position(offset):
        before = source[:offset]
        return {'line': before.count('\n'), 'character': len(before.rsplit('\n', 1)[-1].encode('utf-16-le')) // 2}

    def params(offset):
        return {'textDocument': {'uri': uri}, 'position': position(offset)}

    def change(text):
        nonlocal version, source
        version += 1
        source = text
        session.notify('textDocument/didChange', {'textDocument': {'uri': uri, 'version': version}, 'contentChanges': [{'text': source}]})
        session.request('ouroboros/barrier', {})

    def completion(text):
        change(text)
        started = time.perf_counter()
        items = session.request('textDocument/completion', params(len(source)))
        return items, (time.perf_counter() - started) * 1000

    def has(items, label):
        return any(i['label'] == label for i in items)

    try:
        started = time.perf_counter()
        initialize = session.request('initialize', {'capabilities': {}})
        check('GLS-P-INITIALIZE', initialize['serverInfo']['version'] == '4.0.30', {'coldInitializeMs': (time.perf_counter() - started) * 1000, **initialize})
        session.notify('initialized', {})
        session.notify('textDocument/didOpen', {'textDocument': {'uri': uri, 'version': version, 'languageId': 'groovy', 'text': source}})
        session.request('ouroboros/barrier', {})
        items, elapsed = completion("String text = 'abc'\ntext.")
        check('GLS-P-TAIL-DOT', has(items, 'toUpperCase') and has(items, 'substring') and not has(items, 'window'), {'durationMs': elapsed, 'items': items})
        items, elapsed = completion("import probe.CustomerApi\ndef customers = CustomerApi.load()\ncustomers.collect { it.na")
        check('GLS-P-CLOSURE-RECOVERY', has(items, 'name'), {'durationMs': elapsed, 'items': items})
        items, elapsed = completion("import probe.CustomerApi\ndef names = CustomerApi.load().collect { it.name }\nnames.get(0).")
        check('GLS-P-COLLECT', has(items, 'toUpperCase'), {'durationMs': elapsed, 'items': items})
        items, elapsed = completion("import probe.CustomerApi\nCustomerApi.withBuilder { app")
        check('GLS-P-DELEGATE', has(items, 'appendTitle'), {'durationMs': elapsed, 'items': items})
        change('def label = "hello ${42}"\nlabel.toUpperCase()')
        hover = session.request('textDocument/hover', params(source.index('toUpperCase') + 2))
        check('GLS-P-GSTRING', 'java.lang.String' in json.dumps(hover) and 'toUpperCase' in json.dumps(hover), hover)
        change("def value = 'x'\ndef other(value) { value }\nvalue.toUpperCase()")
        definition = session.request('textDocument/definition', params(source.rindex('value') + 2))
        check('GLS-P-DEFINITION', definition and definition[0]['range'] == {'start': {'line': 0, 'character': 4}, 'end': {'line': 0, 'character': 9}}, definition)
        references = session.request('textDocument/references', {**params(source.rindex('value') + 2), 'context': {'includeDeclaration': True}})
        check('GLS-P-REFERENCES', sorted(x['range']['start']['line'] for x in references) == [0, 2], references)
        rename = session.request('textDocument/rename', {**params(source.rindex('value') + 2), 'newName': 'customerName'})
        edits = rename['documentChanges'][0]['edits']
        check('GLS-P-RENAME', len(edits) == 2 and all(e['newText'] == 'customerName' for e in edits) and rename['documentChanges'][0]['textDocument']['version'] == version, rename)
        change("String text='abc'\ntext.substring(0, 1)")
        signature = session.request('textDocument/signatureHelp', params(source.index('0,') + 2))
        check('GLS-P-SIGNATURE', any('substring(int ' in s['label'] and s['label'].count('int ') == 2 for s in signature['signatures']), signature)
        symbols = session.request('textDocument/documentSymbol', {'textDocument': {'uri': uri}})
        check('GLS-P-SYMBOLS', any(s['name'] == 'text' for s in symbols), symbols)
        tokens = session.request('textDocument/semanticTokens/full', {'textDocument': {'uri': uri}})
        check('GLS-P-TOKENS', bool(tokens['data']) and len(tokens['data']) % 5 == 0, tokens)
        change('if (true) {\nprintln "}"\n}')
        formatting = session.request('textDocument/formatting', {'textDocument': {'uri': uri}, 'options': {'tabSize': 2, 'insertSpaces': True}})
        check('GLS-P-FORMAT', formatting == [{'range': {'start': {'line': 1, 'character': 0}, 'end': {'line': 1, 'character': 0}}, 'newText': '  '}], formatting)
        change("String text='x'\ntext.substring(0")
        diagnostics = [n['params'] for n in session.notifications if n.get('method') == 'textDocument/publishDiagnostics' and n['params'].get('version') == version]
        check('GLS-P-DIAGNOSTICS', diagnostics and diagnostics[-1]['diagnostics'], diagnostics)
        actions = session.request('textDocument/codeAction', {'textDocument': {'uri': uri}, 'range': {'start': {'line': 0, 'character': 0}, 'end': position(len(source))}, 'context': {'diagnostics': []}})
        check('GLS-P-CODE-ACTION', actions and actions[0]['edit']['documentChanges'][0]['edits'][0]['newText'] == ')', actions)
        change('def customers = $0.customers\ndef names = customers.collect { it.name }\nnames.get(0).')
        contexts = ROOT / 'tmp/testing-evidence/groovy-language-service/platform'
        context = json.loads((contexts / 'context-string.json').read_text())
        session.notify('ouroboros/context', {'uri': uri, 'documentVersion': version, 'snapshot': context['snapshot']})
        session.request('ouroboros/barrier', {})
        items = session.request('textDocument/completion', params(len(source)))
        check('GLS-P-PLATFORM-CONTEXT', has(items, 'toUpperCase'), items)
        context = json.loads((contexts / 'context-integer.json').read_text())
        session.notify('ouroboros/context', {'uri': uri, 'documentVersion': version, 'snapshot': context['snapshot']})
        session.request('ouroboros/barrier', {})
        items = session.request('textDocument/completion', params(len(source)))
        check('GLS-P-CONTEXT-INVALIDATION', not has(items, 'toUpperCase') and has(items, 'intValue'), items)
        valid_source = source
        change('String text="hello"\ntext.substring(0')
        diagnostics = [n['params'] for n in session.notifications if n.get('method') == 'textDocument/publishDiagnostics' and n['params'].get('version') == version]
        check('GLS-P-INCOMPLETE-WITH-CONTEXT', bool(diagnostics and diagnostics[-1]['diagnostics']), diagnostics)
        change('')
        check('GLS-P-EMPTY-WITH-CONTEXT', not any(n.get('method') == 'window/logMessage' and 'failed' in n.get('params', {}).get('message', '') for n in session.notifications), session.notifications[-2:])
        change(valid_source)
        session.notify('ouroboros/context', {'uri': uri, 'documentVersion': version, 'snapshot': {'globals': []}})
        session.request('ouroboros/barrier', {})
        items = session.request('textDocument/completion', params(len(source)))
        check('GLS-P-CONTEXT-REMOVAL', not has(items, 'toUpperCase') and not has(items, 'intValue'), items)
        change('def name = "😀"\nname.toUpperCase()')
        definition = session.request('textDocument/definition', params(source.rindex('name') + 1))
        check('GLS-P-UTF16', definition and definition[0]['range']['start'] == {'line': 0, 'character': 4}, definition)
        sentinel = EVIDENCE / 'ast-test-executed.txt'
        change('@groovy.transform.ASTTest(value={ new File(' + json.dumps(str(sentinel)) + ').text="executed" })\ndef marker=1')
        check('GLS-P-NO-USER-AST-EXECUTION', not sentinel.exists(), {'sentinelExists': sentinel.exists()})
        resources = session.request('ouroboros/barrier', {})
        resources['rssKiB'] = int(subprocess.check_output(['ps', '-o', 'rss=', '-p', str(session.process.pid)]).strip())
        check('GLS-P-RESOURCE-OBSERVATION', resources['heapUsedBytes'] > 0 and resources['rssKiB'] > 0, resources)
        session.notify('textDocument/didClose', {'textDocument': {'uri': uri}})
        session.request('ouroboros/barrier', {})
        try:
            session.request('textDocument/hover', params(0))
            check('GLS-P-CLOSE', False, 'closed document was accepted')
        except RuntimeError as error:
            check('GLS-P-CLOSE', 'Document is not open' in str(error), str(error))
    finally:
        try:
            session.request('shutdown', {}); session.notify('exit', {}); session.process.stdin.close(); session.process.wait(timeout=25)
            check('GLS-P-EXIT', session.process.returncode == 0, session.process.returncode)
        finally:
            if session.process.poll() is None:
                session.process.kill(); session.process.wait()
        (EVIDENCE / 'results.json').write_text(json.dumps({'checks': results}, indent=2) + '\n')
        print('Evidence:', EVIDENCE)
    if any(c['result'] != 'pass' for c in results):
        raise SystemExit(1)


if __name__ == '__main__':
    main()

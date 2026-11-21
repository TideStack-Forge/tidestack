#!/usr/bin/env python3
"""Local acceptance assembly; production controllers, typed storage, runtime and UI assets."""
import argparse
import functools
import http.server
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import time
import urllib.error
import urllib.request
from html.parser import HTMLParser

parser = argparse.ArgumentParser()
parser.add_argument('--repo', required=True, type=Path)
parser.add_argument('--action', choices=['check', 'run', 'verify-api'], default='check')
args = parser.parse_args()
repo = args.repo.resolve()
evidence = repo / 'tmp/testing-evidence/logicflow-runtime-manage'
module = repo / 'ability/ouroboros-logicflow-runtime-manage'
editor = repo / 'frontends/web/ouroboros-logicflow-editor'
java = repo / 'tmp/toolchains/jdk-21.0.2.jdk/Contents/Home/bin/java'
classpath_file = module / 'target/test-classpath.txt'
ports = [18884, 18886, 18887, 18888]


def call_api(url, data=None, method='GET', role=None):
    headers = {'Content-Type': 'application/json'}
    if role: headers['X-Fixture-Role'] = role
    req = urllib.request.Request(url, data=None if data is None else json.dumps(data).encode(), headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def await_http(port):
    until = time.monotonic() + 45
    while time.monotonic() < until:
        try:
            status, _ = call_api(f'http://127.0.0.1:{port}/api/runtime/logicflows/instances')
            if status == 200: return
        except (OSError, ValueError): pass
        time.sleep(.2)
    raise RuntimeError(f'Fixture on {port} did not become ready; inspect its log')


def verify():
    import uuid
    roots = [f'http://127.0.0.1:{p}' for p in [18886, 18887]]
    base = roots[0] + '/api/runtime/logicflows'
    outcomes = []
    def record(case, payload): outcomes.append({'case': case, 'result': 'pass', 'evidence': payload})
    status, view = call_api(base + '/models/child'); assert status == 200
    before = view['data']; old_token = before['editToken']
    definition = before['definition']; definition['nodes'][0]['value'] = 'two-jvm-override'
    operation = str(uuid.uuid4())
    command = {'operationId': operation, 'editToken': old_token, 'definition': definition}
    status, result = call_api(base + '/models/child/override', command, 'PUT'); assert status == 202, (status,result)
    version = result['data']['committedVersion']; desired = version['epoch'] + ':' + str(version['revision'])
    until = time.monotonic() + 20
    observed = []
    while time.monotonic() < until:
        observed = [call_api(root + '/api/runtime/logicflows/models/child')[1]['data'] for root in roots]
        if all(item['appliedCatalogVersion'] == desired for item in observed): break
        time.sleep(.2)
    assert all(item['appliedCatalogVersion'] == desired for item in observed), observed
    executions = [call_api(root + '/fixture/execute')[1] for root in roots]
    assert all(item['result'] == 'two-jvm-override' for item in executions), executions
    assert executions[0]['instanceId'] != executions[1]['instanceId']
    record('LF-RM-CLUSTER-001', {'operation':result, 'instances':observed, 'executions':executions})
    status, replay = call_api(base + '/models/child/override', command, 'PUT'); assert status == 202
    assert replay['data'] == result['data']
    stale = dict(command, operationId=str(uuid.uuid4()))
    assert call_api(base + '/models/child/override', stale, 'PUT')[0] == 409
    assert call_api(base + '/models/child/override', command, 'PUT', 'reader')[0] == 403
    record('LF-RM-CAS-001', {'replayed':replay['data']['operationId'],'staleHttp':409,'readerWriteHttp':403})
    latest = call_api(base + '/models/child')[1]['data']
    invalid = json.loads(json.dumps(latest['definition']))
    invalid['nodes'][0].update(type='callFlow', flowName='missing', inputPorts=[{'name':'input','dataType':'any'}])
    validation = call_api(base + '/models/child/validate', {'editToken':latest['editToken'],'definition':invalid},'POST')
    assert validation[0] == 200 and validation[1]['data']['valid'] is False, validation
    rejected = call_api(base + '/models/child/override', {'operationId':str(uuid.uuid4()),'editToken':latest['editToken'],'definition':invalid},'PUT')
    assert rejected[0] == 422, rejected
    assert call_api(roots[0]+'/fixture/execute')[1]['result'] == 'two-jvm-override'
    record('LF-RM-INVALID-001', {'validation':validation[1], 'write':rejected})
    restart = call_api('http://127.0.0.1:18888/fixture/restart-node', {}, 'POST')
    assert restart[0] == 200, restart
    await_http(18887)
    execution = call_api(roots[1]+'/fixture/execute')[1]
    assert execution['result'] == 'two-jvm-override' and execution['instanceId'] != executions[1]['instanceId']
    record('LF-RM-RESTART-001', execution)
    latest = call_api(base + '/models/child')[1]['data']
    revoked = call_api(base + '/models/child/revoke', {'operationId':str(uuid.uuid4()),'editToken':latest['editToken']}, 'POST')
    assert revoked[0] == 202, revoked
    until = time.monotonic()+20
    while time.monotonic()<until:
        executions = [call_api(root+'/fixture/execute')[1] for root in roots]
        if all(item['result']=='default' for item in executions): break
        time.sleep(.2)
    assert all(item['result']=='default' for item in executions),executions
    record('LF-RM-REVOKE-001', {'response':revoked,'executions':executions})
    (evidence/'cluster-acceptance.json').write_text(json.dumps({'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip(),'cases':outcomes},ensure_ascii=False,indent=2))
    print(json.dumps({'pass':len(outcomes),'evidence':str(evidence/'cluster-acceptance.json')},ensure_ascii=False))


if args.action == 'verify-api':
    verify(); raise SystemExit()

required = [java, classpath_file, editor/'dist/editor-build.json', evidence/'browser-host/index.html']
for path in required:
    if not path.exists(): raise SystemExit(f'Missing fixture prerequisite: {path}')
for port in ports:
    with socket.socket() as probe:
        probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try: probe.bind(('127.0.0.1',port))
        except OSError: raise SystemExit(f'Port {port} is already owned')
if args.action == 'check':
    print(json.dumps({'status':'ready','ports':ports,'repo':str(repo)}));raise SystemExit()

class Assets(HTMLParser):
    def __init__(self): super().__init__();self.js=[];self.css=[]
    def handle_starttag(self,tag,attributes):
        a=dict(attributes)
        if tag=='script' and 'src' in a:self.js.append(a['src'])
        if tag=='link' and a.get('rel')=='stylesheet':self.css.append(a['href'])

build=json.loads((editor/'dist/editor-build.json').read_text())
assets=Assets();assets.feed((editor/'dist/index.html').read_text())
version_root=evidence/'asset-versions'/build['buildId']
shutil.copytree(editor/'dist',version_root,dirs_exist_ok=True)
manifest={'publicPath':build['publicPath'],'entry':assets.js[0],'css':assets.css}
(evidence/'active-editor-manifest.json').write_text(json.dumps(manifest))
processes=[]
classpath=os.pathsep.join([str(module/'target/test-classes'),str(module/'target/classes'),classpath_file.read_text().strip()])
def launch(name,*parameters):
    log=(evidence/(name+'.log')).open('ab')
    child=subprocess.Popen([str(java),'-cp',classpath,'com.ouroboros.logicflow.manage.RuntimeFlowAcceptanceServer',*parameters],cwd=repo,stdout=log,stderr=subprocess.STDOUT)
    log.close();processes.append(child);return child
url='jdbc:h2:tcp://127.0.0.1:18884/mem:runtime_manage;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE'
launch('shared-h2','database','18884')
time.sleep(.5)
launch('instance-a','18886',url,str(evidence/'app-a'));await_http(18886)
node_b=launch('instance-b','18887',url,str(evidence/'app-b'));await_http(18887)

class Host(http.server.SimpleHTTPRequestHandler):
    def do_POST(self):self.do_GET()
    def do_PUT(self):self.do_GET()
    def do_GET(self):
        global node_b
        path=self.path.split('?')[0]
        if path=='/fixture/restart-node':
            node_b.terminate();node_b.wait(timeout=10)
            node_b=launch('instance-b-restarted','18887',url,str(evidence/'app-b'))
            self.send(200, json.dumps({'status':'restarting','pid':node_b.pid}).encode());return
        if path.startswith('/api/') or path.startswith('/fixture/'):
            body=self.rfile.read(int(self.headers.get('Content-Length',0)))
            req=urllib.request.Request('http://127.0.0.1:18886'+self.path,data=body or None,method=self.command,headers={'Content-Type':'application/json','X-Fixture-Role':self.headers.get('X-Fixture-Role','editor')})
            try:
                with urllib.request.urlopen(req,timeout=20) as response:self.send(response.status,response.read())
            except urllib.error.HTTPError as error:self.send(error.code,error.read())
            return
        if path=='/editor-manifest.json':self.send(200,(evidence/'active-editor-manifest.json').read_bytes());return
        if path.startswith('/product-schema/'):
            name='detail' if path.endswith('detail.json') else 'list'
            relative='system-setting/logicflows/detail.ui-schema.json' if name=='detail' else 'system-setting/logicflows.ui-schema.json'
            self.send(200,(module/'src/main/metadata'/relative).read_bytes());return
        if path.startswith('/micro-logicflow-editor/'):
            segments=path.split('/')
            self.path='/'.join(segments[3:])
            self.directory=str(evidence/'asset-versions'/segments[2])
        else:
            self.directory=str(evidence/'browser-host')
            if path in ['/', '/system-setting/logicflows','/system-setting/logicflows/detail']:self.path='/index.html'
        super().do_GET()
    def send(self,status,body):
        self.send_response(status);self.send_header('Content-Type','application/json;charset=UTF-8');self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(body)
    def log_message(self,format,*values):
        with (evidence/'browser-network.log').open('a') as stream:stream.write((format%values)+'\n')

server=http.server.ThreadingHTTPServer(('127.0.0.1',18888),Host)
(evidence/'owned-processes.json').write_text(json.dumps({'pids':[p.pid for p in processes],'hostPid':os.getpid(),'ports':ports},indent=2))
def stop(signum,frame):raise KeyboardInterrupt()
signal.signal(signal.SIGTERM,stop)
try:
    print('Acceptance host ready: http://127.0.0.1:18888',flush=True)
    server.serve_forever()
finally:
    server.server_close()
    for child in reversed(processes):
        if child.poll() is None:child.terminate()
    for child in processes:
        try:child.wait(timeout=10)
        except subprocess.TimeoutExpired:child.kill();child.wait()

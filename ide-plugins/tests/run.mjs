import {spawn, spawnSync} from 'node:child_process'
import {createRequire} from 'node:module'
import {existsSync, createWriteStream} from 'node:fs'
import {mkdir, readFile, writeFile, cp} from 'node:fs/promises'
import {createServer} from 'node:net'
import {dirname, join, resolve} from 'node:path'
import {fileURLToPath} from 'node:url'
import {parseArgs} from 'node:util'

const repo = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const {values} = parseArgs({options: {check: {type: 'boolean'}, lane: {type: 'string'}, video: {type: 'boolean'}}})
const lanes = ['browser', 'vscode', 'intellij', 'jcef']
const lane = values.lane ?? 'all'
if (!['all', ...lanes].includes(lane)) throw new Error('Unknown lane; use all|browser|vscode|intellij|jcef')
const selected = lane === 'all' ? lanes : [lane]
const evidence = join(repo, 'tmp/testing-evidence/ide-metadata-editors', new Date().toISOString().replaceAll(':', '-'))
await mkdir(evidence, {recursive: true})
const report = {scenario: 'ide/metadata-editors', commit: spawnSync('git', ['rev-parse', 'HEAD'], {cwd: repo, encoding: 'utf8'}).stdout.trim(),
  dirty: Boolean(spawnSync('git', ['status', '--porcelain'], {cwd: repo, encoding: 'utf8'}).stdout.trim()),
  startedAt: new Date().toISOString(), node: process.version, platform: `${process.platform}-${process.arch}`, selected,
  notCovered: lanes.filter(name => !selected.includes(name)), video: Boolean(values.video), steps: [], status: 'running'}
let child
async function saveReport() { await writeFile(join(evidence, 'summary.json'), JSON.stringify(report, null, 2) + '\n') }
function stop() { if (child?.pid) process.kill(process.platform === 'win32' ? child.pid : -child.pid, 'SIGTERM') }
process.once('SIGINT', stop)
process.once('SIGTERM', stop)

async function run(id, command, parameters, workdir, extraEnv = {}) {
  console.log(`${id} ...`)
  const log = createWriteStream(join(evidence, id + '.log'))
  const step = {id, command: [command, ...parameters], workdir, status: 'running', startedAt: new Date().toISOString()}
  report.steps.push(step)
  await saveReport()
  try {
    const exitCode = await new Promise((accept, reject) => {
      child = spawn(command, parameters, {cwd: join(repo, workdir), env: {...process.env, ...extraEnv}, detached: process.platform !== 'win32'})
      child.stdout.pipe(log, {end: false}); child.stderr.pipe(log, {end: false})
      child.on('error', reject)
      child.on('close', (code, signal) => accept(code ?? signal ?? 1))
    })
    if (exitCode !== 0) throw new Error(`${id} failed (${exitCode}); see ${join(evidence, id + '.log')}`)
    step.status = 'pass'
    console.log(`${id}: pass`)
  } catch (error) { step.status = 'fail'; throw error }
  finally {
    child = undefined
    await new Promise(accept => log.end(accept))
    if (['intellij', 'jcef'].includes(id)) {
      const results = join(repo, 'ide-plugins/intellij-plugin/build/test-results/test')
      if (existsSync(results)) await cp(results, join(evidence, id + '-results'), {recursive: true})
    }
    step.finishedAt = new Date().toISOString()
    await saveReport()
  }
}

try {
  for (const directory of ['ide-plugins/common/metadata-editor-schema', 'ide-plugins/common/metadata-editor-runtime', 'ide-plugins/vscode-plugin', 'frontends/web', 'frontends/web/main-app']) {
    if (!existsSync(join(repo, directory, 'node_modules'))) throw new Error(`Missing dependencies: npm install in ${directory}`)
  }
  for (const command of ['npm', 'java', 'python3']) {
    const probe = spawnSync(command, [command === 'java' ? '-version' : '--version'], {encoding: 'utf8'})
    if (probe.error || probe.status !== 0) throw new Error(`Missing prerequisite: ${command}`)
  }
  const require = createRequire(join(repo, 'ide-plugins/common/metadata-editor-runtime/package.json'))
  const {chromium} = require('@playwright/test')
  if (!existsSync(chromium.executablePath())) throw new Error('Install Chromium: cd ide-plugins/common/metadata-editor-runtime && npx playwright install chromium')
  if (process.env.OB_VSCODE_EXECUTABLE && !existsSync(process.env.OB_VSCODE_EXECUTABLE)) throw new Error('OB_VSCODE_EXECUTABLE does not exist')
  const corpus = JSON.parse(await readFile(join(repo, 'testing/common/fixtures/data/ide-metadata-workspace/documents.json'), 'utf8'))
  if (!Object.keys(corpus).length || Object.values(corpus).some(value => !value.fileName || !value.metadataType || !value.value)) throw new Error('Invalid shared document corpus')
  report.preflight = 'pass'
  if (values.check) {
    report.status = 'preflight-pass'
    console.log('Dependencies ready. JCEF support is verified by the native lane, not this static preflight.')
  } else {
    await run('build', 'npm', ['run', 'compile'], 'ide-plugins/vscode-plugin')
    const nodeDefines = JSON.parse(await readFile(join(repo, 'ide-plugins/vscode-plugin/dist/media/editor-integrations/logicflow-node-defines.json'), 'utf8')).items
    if (!Array.isArray(nodeDefines) || !nodeDefines.length || nodeDefines.some(value => !value.type || !value.scaffold) ||
        corpus.logicflow.value.nodes.some(node => !nodeDefines.some(define => define.type === node.type))) throw new Error('Fixture nodes must use the production catalog')
    await run('runtime-unit', 'npm', ['run', 'test:unit'], 'ide-plugins/common/metadata-editor-runtime')
    await run('browser-types', 'npx', ['tsc', '-p', 'tests/tsconfig.integration.json'], 'ide-plugins/common/metadata-editor-runtime')
    await run('web-form-unit', 'npx', ['vitest', 'run', 'tests/unit/src/components/amis/editor/plugins/patch/form-smart-layout-panel.test.js'], 'frontends/web/main-app')
    const gradle = process.platform === 'win32' ? 'gradlew.bat' : './gradlew'
    if (selected.includes('intellij') || selected.includes('browser')) {
      await run('intellij', gradle, ['test', '-x', 'buildMetadataEditorCommon', ...(selected.includes('intellij') ? [] : ['--tests', '*MetadataBrowserFixtureTest']), '--console=plain'], 'ide-plugins/intellij-plugin')
    }
    if (selected.includes('browser')) {
      const browserEnv = {OB_IDE_EVIDENCE_DIR: evidence, OB_IDE_RECORD_VIDEO: values.video ? '1' : '0'}
      await run('web-browser', 'npm', ['run', 'test:integration:amis-editor'], 'frontends/web/main-app', browserEnv)
      const socket = createServer()
      await new Promise(accept => socket.listen(0, '127.0.0.1', accept))
      const port = socket.address().port
      await new Promise(accept => socket.close(accept))
      await run('browser', 'npm', ['run', 'test:integration'], 'ide-plugins/common/metadata-editor-runtime', {...browserEnv, OB_IDE_TEST_PORT: String(port)})
    }
    if (selected.includes('vscode')) {
      await run('vscode-package', 'npm', ['test'], 'ide-plugins/vscode-plugin')
      await run('vscode-native', 'npm', ['run', 'test:native'], 'ide-plugins/vscode-plugin')
    }
    if (selected.includes('jcef')) {
      await run('jcef', gradle, ['test', '-x', 'buildMetadataEditorCommon', '-PmetadataJcef', '--tests', '*MetadataJcefSmokeTest', '--console=plain'], 'ide-plugins/intellij-plugin')
    }
    report.status = 'pass'
  }
} catch (error) {
  report.status = report.preflight === 'pass' ? 'fail' : 'blocked'
  report.error = String(error)
  console.error(error)
  process.exitCode = 1
} finally {
  report.finishedAt = new Date().toISOString()
  await saveReport()
  console.log(`Evidence: ${evidence}`)
}

import assert from 'node:assert/strict'
import { mkdtemp, writeFile, copyFile, mkdir, symlink, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { createRequire } from 'node:module'

const root = process.cwd()
const sdk = path.join(root, 'frontends/web/ouroboros-sdk')
const require = createRequire(path.join(sdk, 'package.json'))
const ts = require('typescript')
const temporary = await mkdtemp(path.join(tmpdir(), 'ouroboros-business-client-'))

function compile(files, emit) {
  const program = ts.createProgram(files.map(file => path.join(temporary, file)), {
    strict: true, target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.NodeNext,
    moduleResolution: ts.ModuleResolutionKind.NodeNext, skipLibCheck: true,
    noEmit: !emit, outDir: path.join(temporary, 'out'),
  })
  const diagnostics = ts.getPreEmitDiagnostics(program)
  if (emit && diagnostics.length === 0) program.emit()
  return diagnostics
}

try {
  await mkdir(path.join(temporary, 'node_modules'))
  await symlink(sdk, path.join(temporary, 'node_modules/ouroboros-sdk'), 'dir')
  await writeFile(path.join(temporary, 'package.json'), '{"type":"module"}')
  await copyFile(path.join(root, 'core/ouroboros-business-model/ouroboros-business-model-default/target/generated-test-contracts/OrderBusiness.ts'),
    path.join(temporary, 'OrderBusiness.ts'))
  await copyFile(path.join(root, 'core/ouroboros-business-model/ouroboros-business-model-default/target/generated-test-contracts/UnicodeBusiness.ts'),
    path.join(temporary, 'UnicodeBusiness.ts'))
  await copyFile(path.join(root, 'core/ouroboros-business-model/ouroboros-business-model-default/target/generated-test-contracts/OpenBusiness.ts'),
    path.join(temporary, 'OpenBusiness.ts'))
  await copyFile(path.join(root, 'core/ouroboros-business-model/ouroboros-business-model-default/target/generated-test-contracts/FileBusiness.ts'),
    path.join(temporary, 'FileBusiness.ts'))
  await writeFile(path.join(temporary, 'valid.ts'), `
import { createOrderBusiness, submittedEvent } from './OrderBusiness.js'
export { submittedEvent }
export async function submit() {
  const result = await createOrderBusiness().actions.submit({ data: { comment: 'confirmed' } })
  const accepted: boolean = result.accepted
  return accepted
}
import { create_u20ac_Client, _u20ac_paidEvent } from './UnicodeBusiness.js'
export async function pay() {
  const result = await create_u20ac_Client().actions['€pay']({ data: { comment: 'paid' } })
  return { result, identity: _u20ac_paidEvent }
}
import { createOpenBusiness } from './OpenBusiness.js'
export async function open() {
  return createOpenBusiness().actions.submit({ data: { extra: 7 } })
}
import { createFileBusiness } from './FileBusiness.js'
export async function download() {
  const result = await createFileBusiness().actions.export({ data: { comment: 'download' } })
  const content: Blob = result.fileContent
  return { result, content }
}
`)
  const valid = compile(['valid.ts'], true)
  assert.equal(valid.length, 0, ts.formatDiagnosticsWithColorAndContext(valid, {
    getCanonicalFileName: value => value, getCurrentDirectory: () => root, getNewLine: () => '\n',
  }))
  await writeFile(path.join(temporary, 'invalid.ts'), `
import { createOrderBusiness } from './OrderBusiness.js'
createOrderBusiness().actions.submit({ data: { comment: 42 } })
createOrderBusiness().actions.submit({ data: { comment: 'confirmed', extra: true } })
`)
  const invalid = compile(['invalid.ts'], false)
  assert.ok(invalid.some(diagnostic => diagnostic.code === 2322), 'wrong field type must fail TypeScript compilation')
  assert.ok(invalid.some(diagnostic => diagnostic.code === 2353), 'closed Action input must reject extra fields')
  await writeFile(path.join(temporary, 'invalid-root.ts'), `
import { createOpenBusiness } from './OpenBusiness.js'
createOpenBusiness().actions.submit('scalar')
`)
  assert.ok(compile(['invalid-root.ts'], false).some(diagnostic => diagnostic.file?.fileName.endsWith('/invalid-root.ts')),
    'open Action input must reject scalar roots')
  globalThis.window = {}
  const SDK = await import(pathToFileURL(path.join(sdk, 'esm/index.js')))
  const calls = []
  const file = { fileName: 'orders.txt', fileContent: new Blob(['native file']), fileSize: 11, fileType: 'text/plain' }
  await SDK.initMicroApp({ render: async () => ({}) }).mount({ __utils__: { request: {
    post: async (url, body, config) => {
      calls.push({ url, body, config })
      return { data: { code: 0, data: config?.responseType === 'blob' ? file : { accepted: true } } }
    },
  } } })
  const generated = await import(pathToFileURL(path.join(temporary, 'out/valid.js')))
  assert.equal(await generated.submit(), true)
  assert.deepEqual(generated.submittedEvent, { modelFullName: 'sales.Order', eventName: 'submitted' })
  assert.equal(calls.length, 1)
  assert.equal(calls[0].url, '/api/business-model-behaviors/sales.Order/actions/submit/invoke')
  assert.equal(calls[0].body.input.data.comment, 'confirmed')
  assert.deepEqual(calls[0].body.input, { data: { comment: 'confirmed' } })
  assert.match(calls[0].body.expectedContractFingerprint, /^[0-9a-f]{64}$/)
  assert.deepEqual(await generated.pay(), { result: { accepted: true }, identity: { modelFullName: 'sales.Order', eventName: '€paid' } })
  assert.equal(calls[1].url, '/api/business-model-behaviors/sales.Order/actions/%E2%82%ACpay/invoke')
  assert.deepEqual(await generated.open(), { accepted: true })
  assert.deepEqual(calls[2].body.input, { data: { extra: 7 } })
  const downloaded = await generated.download()
  assert.equal(downloaded.result, file)
  assert.equal(downloaded.content, file.fileContent)
  assert.deepEqual(calls[3].config, { responseType: 'blob', noSave: false, throwOnError: true })
  assert.equal(calls[3].url, '/api/business-model-behaviors/sales.Order/actions/export/invoke')
  assert.equal(calls.length, 4)
  console.log('BAE-006 passed: generated TypeScript compiles, rejects incorrect input, and calls the built SDK with the dynamic contract.')
} finally {
  await rm(temporary, { recursive: true, force: true })
}

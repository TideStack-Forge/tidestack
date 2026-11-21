import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { describe, expect, it } from 'vitest'
import { createMetadataEditorHost, InMemoryMetadataFileBridge } from '../../src/usecases/host'
import { getContributionByType, metadataTypeContributions } from '../../src/core/contributions'

interface ContractCase {
  id: string
  type: string
  path?: string
  valid: boolean
  document: unknown
}

const cases: ContractCase[] = JSON.parse(readFileSync(new URL(
  '../../../metadata-editor-schema/tests/fixtures/metadata-schema-conformance.json', import.meta.url,
), 'utf8'))

describe('public metadata schema diagnostics', () => {
  it.each(metadataTypeContributions.filter((contribution) => contribution.fileTemplate))('accepts the $type creation template', async (contribution) => {
    const uri = `/repo/src/main/metadata/${contribution.fixedFileName ?? `probe${contribution.fileSuffix}`}`
    const host = createMetadataEditorHost(new InMemoryMetadataFileBridge({}))
    expect(await host.validateDocument(uri, JSON.stringify(contribution.fileTemplate!.document))).toEqual([])
  })

  it('packages validators with their runtime helpers for installations without node_modules', () => {
    const exports: { validators?: Record<string, (document: unknown) => boolean> } = {}
    const source = readFileSync(new URL('../../src/schema-resources/metadataValidators.cjs', import.meta.url), 'utf8')
    runInNewContext(source, { exports })
    const schemaId = String(getContributionByType('approval-form')!.jsonSchema.$id)
    const validate = exports.validators![schemaId]
    expect(validate({ formatVersion: 1, name: '采购', title: '采购表单', fields: [] })).toBe(true)
    expect(validate({ formatVersion: 2, name: '采购', title: '采购表单', fields: [] })).toBe(false)
  })

  it.each(cases.filter((example) => example.type !== 'ui-schema-content'))('$id', async (example) => {
    const contribution = getContributionByType(example.type)!
    const uri = example.path ?? `/repo/src/main/metadata/${contribution.fixedFileName ?? `probe${contribution.fileSuffix}`}`
    const host = createMetadataEditorHost(new InMemoryMetadataFileBridge({}))
    const diagnostics = await host.validateDocument(uri, JSON.stringify(example.document))
    expect(diagnostics.some((diagnostic) => diagnostic.severity === 'error'), JSON.stringify(diagnostics)).toBe(!example.valid)
  })

  it('validates without changing the original document or adding defaults', async () => {
    const uri = '/repo/src/main/metadata/probe.workflow.json'
    const text = JSON.stringify({ name: 'approval', title: '审批', form: { formType: 'custom', formId: 'purchase' } })
    const bridge = new InMemoryMetadataFileBridge({ [uri]: text })
    const host = createMetadataEditorHost(bridge)
    expect((await host.loadDocument(uri)).diagnostics).toEqual([])
    expect(await bridge.loadText(uri)).toEqual({ text, revision: '1' })
  })
})

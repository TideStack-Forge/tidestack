import { test } from 'vitest'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const hostSchemaPath = new URL(
  '../../../../../../core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/metadata/dev/{appName}/logicflow-model/{fullName}.ui-schema.json',
  import.meta.url
)

test('published logicflow host schema keeps diagnostics in the editor and warns after a save with validation errors', () => {
  const { schema } = JSON.parse(fs.readFileSync(hostSchemaPath, 'utf8'))
  const form = schema.body.find((component) => component.name === 'logicFlowForm')

  assert.deepEqual(form.body.map((component) => component.type), ['logic-flow-editor'])
  assert.equal(form.body[0].validationSnapshot, '${validationSnapshot}')

  const saveActions = form.onEvent.submitSucc.actions
  const updateForm = saveActions.find((action) => action.actionType === 'setValue')
  assert.equal(updateForm.componentId, form.id)
  assert.equal(updateForm.args.value.validationSnapshot, '${result.data.validationSnapshot}')

  const warning = saveActions.find(
    (action) => action.actionType === 'toast' && action.args.msgType === 'warning'
  )
  assert.equal(
    warning.expression,
    '${(result.data.validationSnapshot.validationDiagnostics && result.data.validationSnapshot.validationDiagnostics.length) || (result.data.validationSnapshot.validationErrors && result.data.validationSnapshot.validationErrors.length) || result.data.validationSnapshot.validationSummary}'
  )
  assert.equal(
    warning.args.msg,
    "${result.data.validationSnapshot.validationSummary ? '已保存，但存在后端校验错误：' + result.data.validationSnapshot.validationSummary : '已保存，但存在后端校验错误'}"
  )
})

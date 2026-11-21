import test from 'node:test'
import assert from 'node:assert/strict'

import { getSaveBlockingValidationMessage } from '../src/editorValidation/saveBlockingValidation.mjs'

test('getSaveBlockingValidationMessage blocks save when flow name is blank', () => {
  assert.equal(getSaveBlockingValidationMessage(), '模型名称不能为空')
  assert.equal(getSaveBlockingValidationMessage({}), '模型名称不能为空')
  assert.equal(getSaveBlockingValidationMessage({ name: '   ' }), '模型名称不能为空')
})

test('getSaveBlockingValidationMessage does not block save for design warnings', () => {
  assert.equal(
    getSaveBlockingValidationMessage({
      name: 'flow-a',
      entryNode: '',
      errorEntryNode: 'error-start',
      exitNode: 'error-start',
    }),
    undefined
  )
})

import test from 'node:test'
import assert from 'node:assert/strict'

import { derivePersistedValidationErrors } from '../src/logicFlowEditor/debug/persistedValidationDiagnostics.mjs'

test('derivePersistedValidationErrors reads persisted validation errors even when validationState is absent', () => {
  const entries = derivePersistedValidationErrors({
    validationErrors: ['脚本节点存在后端校验错误'],
    validationDiagnostics: [
      {
        message: '脚本节点存在后端校验错误',
        nodeNames: ['脚本节点'],
      },
    ],
  })

  assert.deepEqual(entries, [
    {
      message: '脚本节点存在后端校验错误',
      nodeName: '脚本节点',
      nodeNames: ['脚本节点'],
    },
  ])
})

test('derivePersistedValidationErrors preserves structured diagnostic location fields', () => {
  const entries = derivePersistedValidationErrors({
    validationDiagnostics: [
      {
        message: '目标输入端口不存在',
        nodeNames: [],
        scope: 'edge',
        fieldPath: 'targetInputPort',
        sourceNode: 'A',
        sourceOutputPort: 0,
        targetNode: 'B',
        targetInputPort: 2,
      },
    ],
  })

  assert.deepEqual(entries, [
    {
      message: '目标输入端口不存在',
      nodeName: null,
      scope: 'edge',
      fieldPath: 'targetInputPort',
      sourceNode: 'A',
      sourceOutputPort: 0,
      targetNode: 'B',
      targetInputPort: 2,
    },
  ])
})

test('derivePersistedValidationErrors falls back to validationSummary when detailed persisted diagnostics are absent', () => {
  const entries = derivePersistedValidationErrors({
    validationSummary: '仅返回后端校验摘要',
  })

  assert.deepEqual(entries, [{ message: '仅返回后端校验摘要' }])
})

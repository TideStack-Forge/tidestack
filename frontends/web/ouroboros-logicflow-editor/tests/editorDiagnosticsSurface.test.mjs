import test from 'node:test'
import assert from 'node:assert/strict'

import {
  LOGICFLOW_DIAGNOSTICS_STATUSES,
  buildLogicFlowDiagnosticsSurfaceContract,
} from '../src/logicFlowEditor/orchestration/editorDiagnosticsSurface.mjs'

test('buildLogicFlowDiagnosticsSurfaceContract exposes clean approval-aligned surface when no diagnostics exist', () => {
  const contract = buildLogicFlowDiagnosticsSurfaceContract()

  assert.equal(contract.status, LOGICFLOW_DIAGNOSTICS_STATUSES.CLEAN)
  assert.equal(contract.trigger.label, '正常')
  assert.equal(contract.trigger.count, 0)
  assert.equal(contract.popover.blockingCount, 0)
  assert.deepEqual(contract.popover.items, [])
})

test('buildLogicFlowDiagnosticsSurfaceContract treats current logicflow validation entries as blocking draft-invalid diagnostics', () => {
  const contract = buildLogicFlowDiagnosticsSurfaceContract({
    validationErrors: [
      { message: '节点「聚合节点」的输入端口「second」未连接', nodeName: '聚合节点' },
      { message: '异常入口节点不能与入口节点相同' },
    ],
  })

  assert.equal(contract.status, LOGICFLOW_DIAGNOSTICS_STATUSES.DRAFT_INVALID)
  assert.equal(contract.trigger.label, '存在错误')
  assert.equal(contract.trigger.count, 2)
  assert.equal(contract.popover.blockingCount, 2)
  assert.deepEqual(
    contract.popover.items.map((item) => ({
      message: item.message,
      nodeName: item.nodeName,
      severity: item.severity,
      blocking: item.blocking,
    })),
    [
      {
        message: '节点「聚合节点」的输入端口「second」未连接',
        nodeName: '聚合节点',
        severity: 'error',
        blocking: true,
      },
      {
        message: '异常入口节点不能与入口节点相同',
        nodeName: null,
        severity: 'error',
        blocking: true,
      },
    ]
  )
})

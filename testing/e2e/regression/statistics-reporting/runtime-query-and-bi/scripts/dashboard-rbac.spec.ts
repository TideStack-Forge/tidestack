import { test, expect } from '@playwright/test'
import {
  browserFetchRawResult,
  businessModelApiPath,
  login,
  runtimeFrontendBaseUrl,
  runtimeRestrictedCredential,
  runtimeScopedReaderCredential,
  type LoginCredential,
} from './support'

function requireRestrictedCredentialOrSkip(): LoginCredential {
  const credential = runtimeRestrictedCredential()
  test.skip(credential === null, 'restricted-user credentials are required for RBAC negative-path proof')
  if (credential === null) {
    throw new Error('restricted-user credentials are required for RBAC negative-path proof')
  }
  return credential
}

test('SRT-RBAC-001 boundary user can read dashboard structure but cannot execute chart data', async ({ page }) => {
  await login(page, runtimeFrontendBaseUrl(), requireRestrictedCredentialOrSkip())

  const dashboardResult = await browserFetchRawResult<any>(
    page,
    businessModelApiPath('sales-overview-runtime-dashboard'),
  )
  expect(dashboardResult.code).toBe(0)
  expect(dashboardResult.data?.widgets).toHaveLength(3)
  expect(dashboardResult.data?.widgets.every(
    (widget: Record<string, unknown>) => !('data' in widget),
  )).toBe(true)

  const result = await browserFetchRawResult(
    page,
    '/api/runtime/statistics-reporting/charts/sales-kpi-runtime-chart/execute',
    { method: 'POST', body: {} },
  )

  expect(result.code).not.toBe(0)
  expect(result.message).toContain('dataset access denied')
  expect(result.data).toBeNull()
})

test('SRT-RBAC-002 scoped reader cannot export when exportRoles are missing', async ({ page }) => {
  await login(page, runtimeFrontendBaseUrl(), runtimeScopedReaderCredential())

  const result = await browserFetchRawResult(page, '/api/runtime/statistics-reporting/charts/sales-crosstab-runtime-chart/export', {
    method: 'POST',
    body: {
      runtimeParameters: {
        ownerUserId: 'client-supplied-owner',
      },
    },
  })

  expect(result.code).not.toBe(0)
  expect(result.message).toContain('export')
  expect(result.data).toBeNull()
})

test('SRT-RBAC-003 dataset identity binding resists client tampering', async ({ page }) => {
  const scopedReader = runtimeScopedReaderCredential()
  await login(page, runtimeFrontendBaseUrl(), scopedReader)

  const result = await browserFetchRawResult<any>(page, '/api/statistics-reporting/datasets/srt.sales.orders/execute', {
    method: 'POST',
    body: {
      datasetName: 'srt.sales.orders',
      executionContext: {
        currentUser: 'client-supplied-owner',
      },
      runtimeParameters: {
        ownerUserId: 'client-supplied-owner',
      },
    },
  })

  expect(result.code).toBe(0)
  const executeResult = result.data ?? result.result
  expect(executeResult).toBeDefined()
  expect(executeResult.appliedParameters?.ownerUserId).toBe(scopedReader.account)
  expect(executeResult.diagnostics?.attributes?.permissionBindings).toContain('ownerUserId<-currentUser')
  expect((executeResult.projection?.rows ?? []).map((row: { orderId?: string }) => row.orderId).sort()).toEqual([
    'SO-2026-005',
    'SO-2026-006',
    'SO-2026-011',
  ])
})

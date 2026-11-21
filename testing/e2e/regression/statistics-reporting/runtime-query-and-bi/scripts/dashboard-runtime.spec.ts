import { test, expect } from '@playwright/test'
import {
  browserFetchJson,
  businessModelApiPath,
  businessModelRuntimeRoute,
  login,
  runtimeAdminCredential,
  runtimeFrontendBaseUrl,
} from './support'

test('SRT-DASHBOARD-001 dashboard API returns structure while chart APIs return widget data', async ({ page }) => {
  const dashboardFullName = 'sales-overview-runtime-dashboard'
  await login(page, runtimeFrontendBaseUrl(), runtimeAdminCredential())

  const dashboardResult = await browserFetchJson<any>(page, businessModelApiPath(dashboardFullName))
  const widgets = dashboardResult.widgets

  expect(widgets).toHaveLength(3)
  expect(widgets.every((widget: Record<string, unknown>) => !('data' in widget))).toBe(true)

  const kpiWidget = widgets.find((widget: { id?: string }) => widget.id === 'sales-kpi-runtime-chart-widget')
  const trendWidget = widgets.find((widget: { id?: string }) => widget.id === 'sales-trend-runtime-chart-widget')
  const crosstabWidget = widgets.find((widget: { id?: string }) => widget.id === 'sales-crosstab-runtime-chart-widget')

  expect(kpiWidget?.chartName).toBe('sales-kpi-runtime-chart')
  expect(trendWidget?.chartName).toBe('sales-trend-runtime-chart')
  expect(crosstabWidget?.chartName).toBe('sales-crosstab-runtime-chart')

  const executions = await Promise.all(
    widgets.map((widget: { chartName: string }) => browserFetchJson<any>(
      page,
      `/api/runtime/statistics-reporting/charts/${encodeURIComponent(widget.chartName)}/execute`,
      { method: 'POST', body: {} },
    )),
  )
  expect(executions.map((execution) => execution.chartType).sort()).toEqual([
    'crosstab',
    'line',
    'metric-card',
  ])
  expect(executions.every((execution) => execution.option && typeof execution.option === 'object')).toBe(true)
})

test('SRT-DASHBOARD-002 runtime dashboard renders independently loaded chart widgets', async ({ page }) => {
  const dashboardFullName = 'sales-overview-runtime-dashboard'
  await login(page, runtimeFrontendBaseUrl(), runtimeAdminCredential())

  const dashboardResponse = page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && new URL(response.url()).pathname === businessModelApiPath(dashboardFullName)
  ))
  const chartResponses = [
    'sales-kpi-runtime-chart',
    'sales-crosstab-runtime-chart',
    'sales-trend-runtime-chart',
  ].map((chartName) => page.waitForResponse((response) => {
    const pathname = new URL(response.url()).pathname
    return response.request().method() === 'POST'
      && pathname === `/api/runtime/statistics-reporting/charts/${chartName}/execute`
  }))

  await page.goto(businessModelRuntimeRoute(dashboardFullName))
  await page.waitForLoadState('networkidle')
  const responses = await Promise.all([dashboardResponse, ...chartResponses])

  expect(responses.every((response) => response.ok())).toBe(true)
  await expect(page.getByText('页面未找到')).toHaveCount(0)
  await expect(page.locator('.dashboard-wrapper, [class*="dashboard"]').first()).toBeVisible({ timeout: 10000 })

  const allWidgets = page.locator('[data-testid="widget"]')
  await expect(allWidgets).toHaveCount(3, { timeout: 10000 })

  await expect(page.locator('[data-testid="widget"][data-widget-id="sales-kpi-runtime-chart-widget"]')).toBeVisible()
  await expect(page.locator('[data-testid="widget"][data-widget-id="sales-trend-runtime-chart-widget"]')).toBeVisible()
  await expect(page.locator('[data-testid="widget"][data-widget-id="sales-crosstab-runtime-chart-widget"]')).toBeVisible()
  await expect(page.locator('canvas')).toHaveCount(3, { timeout: 10000 })
})

import { test, expect } from '@playwright/test'
import { browserFetchJson, login, runtimeAdminCredential, runtimeFrontendBaseUrl } from './support'

async function executeChart(page: Parameters<typeof browserFetchJson>[0], chartName: string) {
  return browserFetchJson<any>(
    page,
    `/api/runtime/statistics-reporting/charts/${encodeURIComponent(chartName)}/execute`,
    { method: 'POST', body: {} },
  )
}

test('SRT-CHART-001 metric card returns a backend-built ECharts option', async ({ page }) => {
  await login(page, runtimeFrontendBaseUrl(), runtimeAdminCredential())

  const result = await executeChart(page, 'sales-kpi-runtime-chart')

  expect(result.chartType).toBe('metric-card')
  expect(result.option.title.map((title: { subtext?: string }) => title.subtext)).toEqual([
    'Sales Amount',
    'Gross Profit',
  ])
  expect(result.option.title.map((title: { text?: string }) => title.text)).toEqual([
    '120000',
    '48000',
  ])
})

test('SRT-CHART-002 crosstab returns a backend-built ECharts option', async ({ page }) => {
  await login(page, runtimeFrontendBaseUrl(), runtimeAdminCredential())

  const result = await executeChart(page, 'sales-crosstab-runtime-chart')

  expect(result.chartType).toBe('crosstab')
  expect(result.option.graphic[0].style.text).toContain('Sales Amount')
  expect(result.option.graphic[0].style.text).toContain('east')
})

test('SRT-CHART-003 line chart executes and renders through ChartRuntime', async ({ page }) => {
  await login(page, runtimeFrontendBaseUrl(), runtimeAdminCredential())

  const result = await executeChart(page, 'sales-trend-runtime-chart')
  expect(result.chartType).toBe('line')
  expect(result.option.xAxis.data).toHaveLength(12)
  expect(result.option.series).toHaveLength(4)
  expect(result.option.series.find((series: { name?: string }) => series.name === 'east').data[0]).toBe(120000)

  const chartResponse = page.waitForResponse((response) => (
    response.request().method() === 'POST'
      && new URL(response.url()).pathname
        === '/api/runtime/statistics-reporting/charts/sales-trend-runtime-chart/execute'
  ))
  await page.goto(`${runtimeFrontendBaseUrl()}/runtime/statistics-reporting/charts/sales-trend-runtime-chart`)
  await page.waitForLoadState('networkidle')

  expect((await chartResponse).ok()).toBe(true)
  await expect(page.locator('canvas')).toBeVisible({ timeout: 10000 })
})

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value))
}

const CHART_EXECUTIONS = {
  'sales-line': {
    chartType: 'line',
    option: {
      title: { text: '销售趋势' },
      tooltip: { trigger: 'axis' },
      legend: { show: true, left: 'center', top: 0 },
      xAxis: {
        type: 'category',
        data: ['2026-05-01', '2026-05-02'],
        name: '下单日期',
      },
      yAxis: { type: 'value', name: '销售额' },
      series: [
        { name: 'east', type: 'line', data: [12, 18], smooth: true },
        { name: 'west', type: 'line', data: [9, 7], smooth: true },
      ],
    },
  },
  'sales-bar': {
    chartType: 'bar',
    option: {
      title: { text: '类目销售额' },
      tooltip: { trigger: 'axis' },
      legend: { show: false },
      xAxis: {
        type: 'category',
        data: ['办公用品', '数码设备', '家具'],
        name: '商品类目',
      },
      yAxis: { type: 'value', name: '销售额' },
      series: [{ name: '销售额', type: 'bar', data: [28, 44, 16] }],
    },
  },
  'sales-pie': {
    chartType: 'pie',
    option: {
      title: { text: '区域销售占比' },
      tooltip: { trigger: 'item' },
      legend: { show: true, left: 'center', top: 0 },
      series: [
        {
          name: '销售额',
          type: 'pie',
          radius: '60%',
          data: [
            { name: 'east', value: 40 },
            { name: 'west', value: 35 },
            { name: 'south', value: 25 },
          ],
        },
      ],
    },
  },
  'sales-metric': {
    chartType: 'metric-card',
    option: {
      title: [
        { text: '128', subtext: '销售额', left: '25%', top: 'middle', textAlign: 'center' },
        { text: '64', subtext: '毛利', left: '75%', top: 'middle', textAlign: 'center' },
      ],
    },
  },
  'sales-crosstab': {
    chartType: 'crosstab',
    option: {
      graphic: [
        {
          type: 'text',
          left: 16,
          top: 16,
          style: {
            text: '区域    已支付    待支付\neast    10    2\nwest    8    4',
            font: '13px monospace',
            lineHeight: 24,
            fill: '#262626',
          },
        },
      ],
    },
  },
}

const DASHBOARDS = {
  '/demo/sales-dashboard': {
    modelType: 'dashboard',
    namespace: 'demo',
    name: 'salesDashboard',
    title: '销售总览仪表板',
    isEnabled: true,
    layout: {
      cols: 24,
      rowHeight: 30,
      draggable: true,
      resizable: true,
    },
    widgets: [
      {
        id: 'chart-metric-widget',
        widgetName: 'chart',
        chartName: 'sales-metric',
        position: { x: 0, y: 0 },
        size: { cols: 12, rows: 8 },
        config: {
          title: '销售指标卡',
          showHeader: true,
        },
      },
      {
        id: 'chart-crosstab-widget',
        widgetName: 'chart',
        chartName: 'sales-crosstab',
        position: { x: 12, y: 0 },
        size: { cols: 12, rows: 10 },
        config: {
          title: '区域收款分布',
          showHeader: true,
        },
      },
    ],
  },
}

function buildRuntimeChartExecution(chartName) {
  const execution = CHART_EXECUTIONS[chartName]
  return execution ? cloneJson(execution) : null
}

function buildRuntimeDashboard(dashboardName) {
  const dashboard = DASHBOARDS[dashboardName.startsWith('/') ? dashboardName : `/${dashboardName}`]
  return dashboard ? cloneJson(dashboard) : null
}

function buildRuntimeDashboardSchema() {
  return {
    type: 'page',
    title: '销售总览仪表板',
    initApi: {
      method: 'get',
      url: '/api/demo/sales-dashboard',
    },
    body: {
      type: 'dashboard',
      editable: false,
      persistAsFields: true,
      source: [
        {
          name: 'chart',
          title: '图表',
          allowHeader: true,
          allowFooter: false,
          sizeConstraints: {
            min: { rows: 6, cols: 6 },
            max: { rows: 24, cols: 24 },
          },
          defaultSize: { rows: 10, cols: 12 },
          schemaTpl: {
            type: 'chart-runtime',
            chartName: '#{chartName}',
          },
        },
      ],
    },
  }
}

module.exports = {
  buildRuntimeChartExecution,
  buildRuntimeDashboard,
  buildRuntimeDashboardSchema,
}

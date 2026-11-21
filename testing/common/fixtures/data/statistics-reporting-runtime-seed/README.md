# Statistics Reporting Runtime Seed

这个 fixture 验证 SQL Dataset、Dataset-backed Chart 和 dashboard business model 的 runtime 合同。

## Seed Assets

- Dataset：`srt.sales.orders`
- Charts：`sales-kpi-runtime-report`、`sales-crosstab-runtime-report`、`sales-trend-runtime-report`
- Dashboard business model：`sales-overview-runtime-dashboard`
- restricted variant：启用 `statistics-reporting.read`、`statistics-reporting.export` 和 `ownerUserId <- currentUser`

## Lifecycle

```bash
OB_TEST_APP_ROOT=/abs/path/to/app-metadata-repo \
OB_TEST_BASE_URL=http://localhost:80 \
bash prepare.sh --reset

OB_TEST_APP_ROOT=/abs/path/to/app-metadata-repo \
OB_TEST_BASE_URL=http://localhost:80 \
bash prepare.sh --up

OB_TEST_APP_ROOT=/abs/path/to/app-metadata-repo \
OB_TEST_BASE_URL=http://localhost:80 \
bash prepare.sh --check
```

`--up` 使用真实开发 API：

- Dataset：`/api/dev/{appName}/dataset`
- Chart：`/api/dev/{appName}/chart`
- Dashboard：`/api/dev/{appName}/business-model`

`--up` 同时在 app metadata workspace 写入：

- `statistics-reporting/datasets/*.dataset.json`
- `statistics-reporting/charts/*.chart.json`
- `business-model/*.business.json`

连接字符串、密码、环境连接参数和测试夹具属于开发平台侧，不写入应用定义。

## Runtime Checks

`--check` 验证三个 Chart 的指标卡、交叉表、图表投影和 Dashboard 的三个 `chart` widget。restricted variant 额外验证 Dataset required role、Chart export role 和 server-side scope binding。

证据写入 `tmp/testing-evidence/statistics-reporting-runtime-query-and-bi/`。

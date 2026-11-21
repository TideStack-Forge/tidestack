# Statistics Reporting Runtime E2E Runbook

## Prerequisites

- Backend and runtime frontend are reachable.
- Root Playwright dependencies and Chromium are installed.
- `jq`, `curl`, `python3`, `node` and `npm` are available.
- `OB_TEST_APP_ROOT` points to an application metadata root containing `business-model/` and `ui-schema/`.
- `tmp/testing-evidence/` is writable.

## Start

The local bootstrap starts the backend and frontend:

```bash
bash scripts/dev-runtime/bootstrap-develop-browser-e2e-local.sh
```

Verify the backend:

```bash
curl -fsS http://127.0.0.1:18888/actuator/health
```

Install browser dependencies when needed:

```bash
npm ci --no-audit --no-fund
npx playwright install chromium
```

## Fixture Lifecycle

```bash
export OB_TEST_APP_ROOT=/abs/path/to/app-metadata-repo
export OB_TEST_BASE_URL=http://127.0.0.1:18888

bash testing/common/fixtures/data/statistics-reporting-runtime-seed/prepare.sh --reset
bash testing/common/fixtures/data/statistics-reporting-runtime-seed/prepare.sh --up
bash testing/common/fixtures/data/statistics-reporting-runtime-seed/prepare.sh --check
```

The fixture uses these authoring APIs:

- Dataset: `/api/dev/{appName}/dataset`
- Chart: `/api/dev/{appName}/chart`
- Dashboard business model: `/api/dev/{appName}/business-model`

The app metadata files are stored under `statistics-reporting/datasets`, `statistics-reporting/charts`, and `business-model`. JDBC connection configuration remains platform-side.

## Browser Tests

Plan the manifest:

```bash
python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/statistics-reporting/runtime-query-and-bi/e2e.yaml \
  --mode plan
```

Run the Chart, Dashboard, and RBAC specs:

```bash
npm run test:e2e:statistics-reporting:chart
npm run test:e2e:statistics-reporting:dashboard
npm run test:e2e:statistics-reporting:rbac
```

RBAC requires `OB_TEST_RESTRICTED_ACCOUNT` and `OB_TEST_RESTRICTED_PASSWORD`. Use `OB_TEST_SRT_SEED_SPEC_FILE=seed-spec-restricted.json` when preparing the restricted variant.

## Evidence

- `tmp/testing-evidence/statistics-reporting-runtime-query-and-bi/`
- `tmp/testing-evidence/playwright-report/index.html`
- `tmp/testing-evidence/test-results.json`

Failures should be classified as environment, metadata loading, runtime contract, frontend route/schema, or Dataset permission failures. Do not use legacy route aliases or historical temporary files to make a run pass.

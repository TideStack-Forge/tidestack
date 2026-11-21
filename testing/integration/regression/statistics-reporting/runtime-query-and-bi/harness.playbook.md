# Statistics Reporting Runtime Harness Playbook

## Purpose

本 playbook 定义 Dataset、Chart 和 Dashboard business model 回归场景的 fixture 准备与验证方式。

## Required Inputs

- `OB_TEST_BASE_URL`
- `OB_TEST_APP_BASE_URL`
- `OB_TEST_FRONTEND_BASE_URL`
- `OB_TEST_RUNTIME_FRONTEND_BASE_URL`
- `OB_TEST_APP_ROOT` 或 `--repo app-metadata=/abs/path/to/repo`

`OB_TEST_APP_ROOT` 必须包含 `business-model/` 和 `ui-schema/`。

## Sequence

```bash
python3 testing/common/runners/harness/fixture_harness.py \
  --scenario testing/integration/regression/statistics-reporting/runtime-query-and-bi/scenario.yaml \
  --action check --mode plan \
  --repo app-metadata=/abs/path/to/app-metadata-repo

OB_TEST_APP_ROOT=/abs/path/to/app-metadata-repo \
OB_TEST_BASE_URL=http://localhost:80 \
bash testing/common/fixtures/data/statistics-reporting-runtime-seed/prepare.sh --reset

OB_TEST_APP_ROOT=/abs/path/to/app-metadata-repo \
OB_TEST_BASE_URL=http://localhost:80 \
bash testing/common/fixtures/data/statistics-reporting-runtime-seed/prepare.sh --up

OB_TEST_APP_ROOT=/abs/path/to/app-metadata-repo \
OB_TEST_BASE_URL=http://localhost:80 \
bash testing/common/fixtures/data/statistics-reporting-runtime-seed/prepare.sh --check
```

## Fixture Contract

- `--reset` 删除 Dataset、三个 Chart 和 dashboard business model 文件
- `--up` 通过 `dataset`、`chart`、`business-model` dev API 创建定义，并写入 app default metadata
- `--check` 验证 live Dataset-backed Chart、Dashboard 结构/Chart 数据分离和权限边界
- Dashboard 文件位于 `business-model/{name}.business.json`
- Dataset / Chart app default 文件位于 `statistics-reporting/datasets` 和 `statistics-reporting/charts`
- JDBC 连接配置和测试数据只由开发平台侧提供，不写入应用元数据之外的产品定义

## Evidence

- `tmp/testing-evidence/harness/statistics-reporting-runtime-query-and-bi/**`
- `tmp/testing-evidence/statistics-reporting-runtime-query-and-bi/statistics-reporting-runtime-seed-check.json`

## Exit Condition

进入后端或前端回归测试前，scenario、contract、cases 和 seed fixture 必须存在，且 `--reset`、`--up`、`--check` 均能被静态检查识别；不得依赖历史 tmp 文件。

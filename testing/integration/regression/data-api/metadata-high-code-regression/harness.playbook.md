# Metadata And High-Code Data API Regression Harness Playbook

## Preflight

1. 先读取 [scenario.yaml](scenario.yaml)、[contract.md](contract.md)、[cases.md](cases.md)
2. 确认 `data/app-metadata-root` 的 `appRoot` 已从显式输入解析成功，优先使用 `--repo app-metadata=/abs/path/to/repo`
3. 确认 runtime fixture 的 `baseUrlEnv` 或 CLI 输入能够解析出真实 base URL
4. 确认目标运行时已启动，并且明确使用的是 `develop` 还是 `mothership`
5. 确认 runtime preflight 预期包含两层：
   - 未认证 HTTP 探针，例如 `/api/auth/authorities -> 401`
   - 本地运行时的类路径完整性检查
6. 确认管理员身份可登录；如果要测权限边界，准备无权限身份
7. 明确本轮排除 `baseInfo.deviceManage` 相关 API

默认情况下：

- `appRoot` 由 `data/app-metadata-root` 的输入来源规则解析
- 登录载荷由 `identities/admin` fixture 提供
- 运行时地址优先读取 CLI，其次读取 runtime fixture 暴露的 `baseUrlEnv`

推荐输入策略：

- 如果资产来自仓库 checkout，传 `--repo NAME=/abs/path`
- 如果资产来自导出包或 repo 内子目录，再传 `--app-root`
- 不要把某次 run 生成的 `tmp/...` 目录当成长期默认输入

只有在场景特殊化时才应使用 CLI override 覆盖这些默认值。

## Layered Execution Order

1. 先跑 inventory-only，确认 inventory、case table、summary 基础产物可生成
2. 再按 ring 执行 metadata live 场景：
   - `metadata-list`
   - `metadata-detail`
   - `metadata-query-conditions`
   - `metadata-create`
   - `metadata-update`
   - `metadata-delete`
3. 再按需要执行高代码 read/write rings：
   - `high-code-read`
   - `high-code-special-read`
   - `high-code-write`
   - `high-code-special-write`
4. 如果涉及 `app.*` 路由，优先切到 `mothership`
5. 对 pending seed，要先确认是“尚未执行就绪”还是“真实回归”，不要把两者混写

## Observation Points

- 看 `tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs` 下的 inventory / cases / summary 是否更新
- 看 `tmp/testing-evidence/metadata-high-code-data-api-regression/<run-id>/results.json`
- 看 `fixture-preflight.json` 是否先把 data/runtime/identity fixture 状态落证据
- 看 `tmp/testing-evidence/metadata-high-code-data-api-regression/analysis/` 下是否新增本轮 findings / 根因记录
- 看 live 失败是否属于：
  - permission-boundary
  - environment-gap
  - reserved-route-conflict
  - runtime-baseline-invalid
  - regression
- 看元数据写环分母是否只包含 `stage_one_write_executable=true` 的模型
- 看高代码写环是否只统计 execution-ready 的 seed，pending 是否仍在 summary 中可见

## Asset Truth Checks

每轮执行前至少确认以下 4 点：

1. 普通 `views.detail` 没有被当成主子表
2. 显式 `views.add` 下遗漏必填字段/关系的模型没有混进 stage-one 写环
3. 高代码写 seed 已同时具备样本构造和清理模板
4. runner 对 HTML、纯文本和连接失败能落证据，而不是直接崩掉

## Recommended Commands

Inventory-only:

```bash
python3 testing/common/runners/api/metadata_high_code_data_api_regression.py \
  --mode inventory \
  --repo app-metadata=/path/to/app-metadata-repo
```

Execute one ring:

```bash
python3 testing/common/runners/api/metadata_high_code_data_api_regression.py \
  --mode execute \
  --execution-ring metadata-detail \
  --repo app-metadata=/path/to/app-metadata-repo \
  --base-url http://127.0.0.1:8080 \
  --company-id YOUR_COMPANY_ID
```

Execute metadata write ring:

```bash
python3 testing/common/runners/api/metadata_high_code_data_api_regression.py \
  --mode execute \
  --execution-ring metadata-create \
  --repo app-metadata=/path/to/app-metadata-repo \
  --base-url http://127.0.0.1:8080 \
  --company-id YOUR_COMPANY_ID
```

Execute high-code GET ring:

```bash
python3 testing/common/runners/api/metadata_high_code_data_api_regression.py \
  --mode execute \
  --execution-ring high-code-read \
  --repo app-metadata=/path/to/app-metadata-repo \
  --base-url http://127.0.0.1:8080
```

Execute high-code write ring:

```bash
python3 testing/common/runners/api/metadata_high_code_data_api_regression.py \
  --mode execute \
  --execution-ring high-code-write \
  --repo app-metadata=/path/to/app-metadata-repo \
  --base-url http://127.0.0.1:8080
```

如果 `appRoot` 不在仓库根目录，可在 `--repo` 的基础上再显式补一个 `--app-root /abs/path/to/repo/subdir/app-root`。

如果本轮要验证的是“真实应用运行态”，而不是只验证 metadata 生成的基础链路，还要先判断 `extend` 是否参与运行时装配：

- 只测 metadata 标准 CRUD/query/detail，可先使用 `baseline-without-extend`
- 涉及自定义监听器、SPI、controller、service 覆盖时，必须先编译并挂载扩展 jar，再进入回归

报告里要明确写出本轮属于哪一种基线，避免把两种结论混在一起。

## Cleanup

1. 归档本轮 evidence 目录
2. 记录新增经验教训
3. 清理本轮拉起的不必要后台进程；默认全部清理，除非显式登记 `PID + 端口 + 保留原因`
4. 释放临时 token 文件或临时环境变量

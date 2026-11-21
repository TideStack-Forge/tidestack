# Statistics Reporting Runtime Regression Contract

## Objective

验证 Dataset、Chart 和 Dashboard business model 在最终用户运行时的真实合同：

1. 已启用的 Dataset 和 Chart 能被 runtime 正确加载和执行；
2. Chart API 能返回后端构建的指标卡、交叉表和折线图 ECharts option；
3. Dashboard business model 按完整名称投射路径，结构响应不包含数据，每个 `chart` widget 独立请求 Chart API；
4. Dataset 的 required roles、export roles 和 server-side scope binding 生效。

## Scope

- Dataset SQL V1 执行、参数绑定、字段白名单和导出
- Chart 的 `metric-card`、`crosstab` 和 `line` ECharts option
- Dashboard business model 动态 runtime 页面和 chart widget
- app default / runtime override 的 Dataset 与 Chart definition loading
- Dataset permission definition：`requiredRoles`、`exportRoles`、`scopeBindings`
- 不包含独立 Dashboard 资产、Query View、DataModel query、统计分析独立发布或运行时 SQL 编辑

## Fixtures

- `runtimes/develop`：开发 API 和元数据 workspace
- `runtimes/mothership`：最终用户 runtime
- `frontends/develop-shell`、`frontends/runtime-shell`
- `data/app-metadata-root`
- `data/statistics-reporting-runtime-seed`
- `identities/admin`、`identities/permission-boundary-user`

## Pass Criteria

- `SRT-CONTRACT-001`：runtime endpoint、route、schema 与本合同一致
- `SRT-TOPO-001`：Dataset、Chart 和 dashboard business model fixture 可重建
- `SRT-DATASET-001`：Dataset 执行按参数返回正确行和 schema
- `SRT-EXPORT-001`：导出只输出允许导出的字段
- `SRT-CHART-001`：metric-card Chart 返回后端构建的 ECharts option
- `SRT-CHART-002`：crosstab Chart 返回后端构建的 ECharts option
- `SRT-CHART-003`：line Chart 返回后端构建的 ECharts option 并由 ChartRuntime 渲染
- `SRT-DASHBOARD-001`：Dashboard API 只返回三个 chart widget 的结构，widget 数据由 Chart API 独立返回
- `SRT-DASHBOARD-002`：Dashboard 动态 runtime 页面正确显示三个 chart widget
- `SRT-RBAC-001`：用户可读取已授权 Dashboard 结构，但缺少 Dataset required role 时不能执行 Chart
- `SRT-RBAC-002`：缺少 export role 的用户不能导出 Chart
- `SRT-RBAC-003`：scope binding 使用 server-side identity，不能被客户端参数覆盖

## Failure Classification

- 环境：backend、frontend、metadata root 或 fixture 依赖不可用
- 定义装载：Dataset、Chart 或 business model 文件不存在、无效或重复
- runtime 合同：执行 endpoint、ECharts option 或 Dashboard 结构/Chart 数据边界不匹配
- 前端：runtime route、schema 或 widget renderer 不匹配
- 权限：Dataset role、export role 或 server-side scope 未生效

## Evidence Requirements

- `tmp/testing-evidence/statistics-reporting-runtime-query-and-bi/**`
- Dataset / Chart API 请求和响应
- Dashboard business model 文件、结构响应和独立 Chart 请求
- RBAC、导出和 scope binding 证据
- 失败时的 backend log、browser console 和 network 证据


## 综合查询与执行限制

综合查询编辑器通过默认业务模型公共编辑页加载共享 `composite-view-editor`。SRT-CV-UI-001 至 003 在真实前端渲染器和 HTTP 合同夹具下执行，覆盖正式表单生命周期、参数映射、预览和错误恢复。

平台配置 `statisticsReporting.executionPolicy` 的初始值为 30 秒、1000 行、100000 行。新请求读取配置快照及版本，数据集显式限制只能收紧。定义结构校验不依赖尚未就绪的运行时配置存储，开发保存检查当前平台值。执行时使用平台和数据集的较小值。

SRT-POLICY-001、SRT-CV-PRIVATE-001、SRT-CV-PUBLISH-001、SRT-EXPORT-002 和 SRT-CV-ACTION-001 要求真实平台服务与数据库。综合查询响应使用 `DatasetResult`，总数位于 `pageInfo.total`。私有查询进入同一执行服务，要求账户上下文与参数白名单。`?format=xlsx` 导出沿平台文件机制返回完整工作簿。

动作引用保持 WebAPI 的 operationId 或定义中的 METHOD path，按钮使用 WebAPI 路由注册机制解析出的实际请求路径，包含作用域前缀和应用基础路径。发布、权限、数据查询和写操作的证据分别记录。

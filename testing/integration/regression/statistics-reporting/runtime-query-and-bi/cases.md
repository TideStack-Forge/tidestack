# Statistics Reporting Runtime Regression Cases

| Case ID | Objective | Preconditions / Input | Owner Surface | Mode | Pass Criteria | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| SRT-CONTRACT-001 | 验证 Dataset、Chart 和 Dashboard business model runtime 合同 | develop/mothership/runtime-shell 可访问；fixture 已准备 | harness | harness | endpoint、route、schema 与当前设计一致 | route inventory, controller inventory |
| SRT-TOPO-001 | 验证 runtime fixture 拓扑和前置条件 | runtime、frontend、metadata root 可访问 | harness | harness | fixture 可 reset/up/check，失败分类明确 | harness report |
| SRT-DATASET-001 | Dataset 执行返回正确参数结果和字段 schema | enabled SQL Dataset；seed rows 已写入 | runtime | e2e | 参数绑定和结果行符合 Dataset 定义 | execute response |
| SRT-EXPORT-001 | Dataset-backed Chart 导出字段白名单 | schema 包含不可导出字段 | runtime | e2e | 导出不包含 `departmentId` 和 `ownerUserId` | export response |
| SRT-CHART-001 | metric-card Chart 生成 ECharts option | metric-card Chart 已启用 | runtime-shell | e2e | 标题和值由后端 option 返回 | API response |
| SRT-CHART-002 | crosstab Chart 生成 ECharts option | crosstab Chart 已启用 | runtime-shell | e2e | 行列和指标文本出现在后端 option | API response |
| SRT-CHART-003 | line Chart 执行并渲染 | line Chart 已启用 | runtime-shell | e2e | 后端 option 正确且页面出现 canvas | API response, browser trace, screenshot |
| SRT-DASHBOARD-001 | Dashboard 结构与 Chart 数据分离 | dashboard model 包含三个 `chart` widget | runtime | e2e | 结构无 `data`，每个 `chartName` 可独立执行 | dashboard and chart payloads |
| SRT-DASHBOARD-002 | Dashboard 动态 runtime 页面显示 chart widget | SRT-DASHBOARD-001 已通过 | runtime-shell | e2e | 完整名称派生页面可加载，三个 widget 和 canvas 可见 | screenshot, network trace |
| SRT-RBAC-001 | Dataset required role 控制 Chart 数据执行 | restricted Dataset 和 boundary actor | runtime-shell | e2e | Dashboard 结构可读，Chart 数据请求被服务端拒绝 | API response, auth evidence |
| SRT-RBAC-002 | Dataset export role 控制 Chart 导出 | scoped reader 无 export role | runtime-shell | e2e | export 请求被服务端拒绝 | API response |
| SRT-RBAC-003 | scope binding 抵抗客户端伪造 | `ownerUserId <- currentUser` | runtime-shell | e2e | 只返回 server identity 对应数据 | execute response |

| SRT-CV-UI-001 | 综合查询字段配置、完整草稿保存、分页和诊断定位 | 实际共享编辑器、默认业务模型工具栏，HTTP 合同夹具 | develop-shell | e2e | 保存元数据完整，第二页请求正确，失效字段诊断可聚焦到配置行 | 截图、录屏、请求响应 |
| SRT-CV-UI-002 | 行/批量映射与私有查询配置 | 当前数据源字段、行 API 与数组 API 合同 | develop-shell | e2e | 类型不匹配被标记，数组映射通过，账户参数和执行收紧值可编辑 | 映射截图、私有查询截图 |
| SRT-CV-UI-003 | 操作参数预览、查询失败恢复和窄屏 | 已配置行操作，预览接口可返回成功/超时 | develop-shell | e2e | 参数取当前记录，预览不调用业务写接口，超时可重试，390px 无页面横向溢出 | 截图、录屏、网络日志 |
| SRT-POLICY-001 | 平台动态执行策略与数据集收紧 | 独立配置存储、真实数据集执行流水线 | runtime | harness | 同次执行使用一个版本，页大小超限提前失败，非法配置无法保存，恢复默认生效 | 配置接口日志、执行响应 |
| SRT-CV-PRIVATE-001 | 私有查询与可信上下文 | 真实 SQL、有效私有定义、已登录身份 | runtime | harness | 请求上下文无法覆盖服务端账户，未声明参数被拒绝，输出仅有配置列 | 查询请求响应、JDBC 测试日志 |
| SRT-CV-PUBLISH-001 | 草稿保存、投射拒绝与修复恢复 | 工作区数据集、正式业务模型保存/投射入口 | develop | harness | 无效字段草稿保存带诊断，投射失败，修复后可投射 | 真实保存与投射接口日志 |
| SRT-EXPORT-002 | 完整 Excel 文件与导出边界 | 实际数据、可导出字段、平台文件响应 | runtime | harness | max 条成功且可读工作簿，max+1 明确失败，响应失败不产生部分文件，文件消费后清理 | Excel 文件、接口日志、单测日志 |
| SRT-CV-ACTION-001 | 运行页面调用既有业务 WebAPI | 平台投射的行删除与批量删除、隔离订单数据 | runtime-shell | harness | 行操作只处理当前行，批量操作只处理选中记录，业务数据反映预期副作用 | 浏览器录屏、API 请求、操作后查询 |

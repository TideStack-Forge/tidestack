# 统计分析

为应用提供“数据集 → 图表 → 仪表板/复合业务视图”的只读分析链。

## 功能组成

| 功能项 | 当前行为与使用边界 |
| --- | --- |
| 数据集 | 定义查询口径、参数与执行上下文，由统一执行服务装配和校验。 |
| 图表和仪表板 | 图表描述只读可视化，仪表板按组件独立查询图表并展示局部失败和重试。 |
| 复合业务视图 | 在业务页面复用分析结果，预期、阶段和验收以复合视图规格为准。 |

## 实现情况

数据集始终可选择默认逻辑数据源；无实际连接时可根据当前开发助手的数据模型推导字段，选择实际连接后可发现模型之外的数据库表并预览查询。非默认源直接使用应用绑定连接；默认源可选择平台已保存连接或当前应用的运行容器，引用与预览参数保存在统一开发态配置表中。编辑器提供 SQL 方言页签、参数、结果字段、引用关系、字段推导和查询预览。

当前 V1 数据集仅允许 SQL 查询，DataModelQueryDefinition 被校验器拒绝。Dataset 与 Chart 使用 isEnabled 控制运行时可用性，名称和引用不区分大小写。每个定义分别投射执行与导出权限。综合查询通过 composite-view 业务模型提供过滤、列展示、默认排序、分页和既有 WebAPI 操作按钮。旧定义通过一次性转换处理。

数据集连接、字段推导和测试配置持久化的后端验证位于统计分析开发模块的测试目录；编辑器与页面行为回归见[前端统计分析测试](../../frontends/web/main-app/tests/integration/statistics-reporting/README.md)。真实 MySQL 与 Docker 联调仍需按部署环境单独验收。

## 启用条件

需可访问的数据源、应用分析元数据与访问权限；不要将任意 SQL 能力解释成免权限或可写数据库入口。

## 相关代码

| 代码与职责入口 | 行为测试追溯 |
| --- | --- |
| [core/ouroboros-statistics-reporting](../../core/ouroboros-statistics-reporting) · [设计/指南](../../core/ouroboros-statistics-reporting/docs/module-design.md) | [测试入口](../../core/ouroboros-statistics-reporting/ouroboros-statistics-reporting-core/src/test/java/com/ouroboros/statistics/reporting/StatisticsReportingModuleSmokeTest.java) |

## 关联文档

- [core/ouroboros-statistics-reporting/docs/module-design.md](../../core/ouroboros-statistics-reporting/docs/module-design.md)
- [docs/requirements/analytics/composite-view.md](../requirements/analytics/composite-view.md)

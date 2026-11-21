# 数据初始化模块使用指南

## 目的

数据初始化模块用于随模块或应用发布声明式的基础数据、枚举数据和系统默认记录。它通过 `DataModel` 写入目标模型，运行在平台模型刷新完成之后，适合“缺失则补齐”的初始化场景，不适合承担数据迁移或批量修正已有业务数据。

本文面向业务模块和平台模块开发者，说明如何编写 `.data-init.json`、放置初始化文件、理解运行时幂等语义，并排查常见失败。

## 适用场景

推荐使用：

- 系统状态、字典、默认角色、内置配置等基础数据。
- 随 jar 发布、需要在运行时自动补齐的低代码模型记录。
- appHome 侧按环境补充的轻量初始化数据。

不推荐使用：

- 修改或覆盖已经存在的业务记录。
- 大规模历史数据迁移、字段重算或数据清洗。
- 需要复杂顺序、跨表校验或人工确认的数据变更。

这些场景应使用专门的迁移、运维脚本或业务服务流程。

## 启用方式

运行时入口在 `core/ouroboros-data/ouroboros-data-initialization-runtime`。平台侧通常经由 `ouroboros-data-platform-adapter` 引入该 runtime；独立应用如果需要直接使用，应依赖 `ouroboros-data-initialization-runtime`，并确保 Spring 上下文中存在默认运行时依赖：

- `PlatformTransactionManager`：默认 `InitializationTransaction` 需要它提供事务边界。
- `DistributedLockManager`：默认 `InitializationLock` 使用分布式锁，锁 key 为 `data-init`。
- 可用的 `DataModelCenter`：`targetModel` 必须能被 `DataModelCenter.getDataModel(targetModel)` 解析。

自动配置类会注册默认的 `ResourceDataInitializationPlanProvider`、`DataModelTargetDatasetGateway`、`DataModelInitializationLedger`、`DataInitializationReconciler` 和生命周期监听器。默认网关、账本、锁、事务和 reconciler 按对应类型使用 `@ConditionalOnMissingBean`。文件 provider 的条件是具体 `ResourceDataInitializationPlanProvider` 类型；仅新增另一个 `InitializationPlanProvider` 会与默认文件来源共同参与，并不会替换它。生命周期适配器也按其具体类型判断。

## 文件位置

### 随模块发布

作者态文件放在模块内：

```text
src/main/metadata/**/*.data-init.json
```

根 POM 的 `maven-resources-plugin` 会在构建时把它复制到：

```text
target/classes/META-INF/ouroboros/metadata/**/*.data-init.json
```

运行时从 classpath 的 `META-INF/ouroboros/metadata/**` 扫描所有 `.data-init.json` 文件。不要新增 loader 直接读取 `src/main/metadata`，也不要绕过现有 Provider + Center 链路。

### appHome 侧补充

当应用提供 `AppContext#getHomePath()` 时，运行时还会扫描：

```text
${appHome}/data-init/**/*.data-init.json
```

appHome 文件适合放环境相关的补充初始化数据。classpath 计划会先加载，appHome 计划随后加载；同一来源内按资源名或路径排序。

## JSON 结构

最小可用示例：

```json
{
  "id": "system-status",
  "version": "v1",
  "blocks": [
    {
      "id": "statuses",
      "targetModel": "Status",
      "matchFields": ["code"],
      "records": [
        {
          "code": "enabled",
          "label": "Enabled"
        },
        {
          "code": "disabled",
          "label": "Disabled"
        }
      ]
    }
  ]
}
```

字段说明：

| 字段 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 初始化计划 ID。应稳定、全局可读，例如 `system-status`。 |
| `version` | 否 | 计划版本。为空时归一为 `default`。用于账本追踪，不会强制更新已有记录。 |
| `blocks` | 是 | 初始化块列表，至少一个 block。 |
| `blocks[].id` | 是 | block ID。应在同一计划内稳定且唯一；当前构造器检查非空，没有跨 block 的 ID 去重校验。 |
| `blocks[].targetModel` | 是 | 目标数据模型名称，必须能被 `DataModelCenter` 解析。 |
| `blocks[].version` | 否 | block 版本。存在时覆盖计划级 `version`。 |
| `blocks[].matchFields` | 是 | 用于判断记录是否已存在的字段列表。会去重，不能为空。 |
| `blocks[].records` | 否 | 待插入记录列表。为空时该 block 不插入记录。 |

`records` 中的字段和值会原样传给目标 `DataModel.insert(values)`。字段名、值类型、必填约束和默认值遵循目标模型自身合同。

## 运行时语义

数据初始化在 `OuroborosModelRefreshedEvent` 之后触发，监听顺序为 `@Order(30)`。每次触发时，reconciler 会加载所有计划并逐个处理 block。

处理规则：

1. 对每个 block 建立账本运行记录，状态先置为 `RUNNING`。
2. 在事务边界内逐条处理 `records`。
3. 按 `matchFields` 从目标模型查询已有记录。
4. 如果查到一条记录，则跳过，不更新已有字段。
5. 如果查不到记录，则调用目标模型 `insert` 插入整条记录。
6. block 成功后账本状态置为 `SUCCESS`，并写入 `inserted` / `skipped` 汇总。
7. block 失败时账本状态置为 `FAILED`，记录错误信息并抛出异常。

重要约束：

- 初始化是 **insert-if-missing**，不是 upsert。已有记录不会被覆盖。
- 幂等判断基于目标模型查询结果，而不是账本成功记录。即使之前成功过，只要目标记录被删除，下次仍会补齐。
- `matchFields` 中的每个字段都必须出现在对应 record 中，否则 block 失败。
- `matchFields` 查询结果超过一条时视为目标数据不唯一，block 失败。
- 一个 block 在同一事务中完成；失败时按当前事务能力回滚目标写入和账本成功写入。

## 账本

运行时会注册 `DataInitializationLedgerRecord` 模型，对应物理表名：

```text
sys_data_initialization_ledger
```

账本用于追踪每个 block 的运行状态、来源、版本、内容哈希和结果汇总。账本 ID 由以下信息计算：

- `planId`
- `blockId`
- `targetModel`
- `assetVersion`，即 block 版本或计划版本
- `contentHash`，由 `planId`、block ID、目标模型、版本、匹配字段和 records 计算

账本是可观测性和审计机制，不是“只执行一次”的开关。初始化是否插入记录仍由 `matchFields` 查询目标模型决定。

## 版本与变更建议

- 新增 record：保持 plan/block ID 不变，新增记录即可；下次刷新会补齐缺失记录。
- 修改已有 record 的非匹配字段：数据初始化不会覆盖已存在记录；如需修改，应走迁移或业务更新流程。
- 修改 `matchFields`：谨慎处理，可能改变幂等判断，建议同步检查目标模型唯一性。
- 调整 seed 内容：建议提升 `version`，便于账本区分和审计；但不要把版本提升误认为已有数据会被更新。
- 拆分 block：按目标模型或业务域拆分，避免一个失败影响不相关数据。

## 扩展点

默认实现已覆盖常规文件扫描和 `DataModel` 写入。确实需要扩展时，优先覆盖现有 bean，不要创建平行初始化链路。

| 扩展点 | 用途 |
|--------|------|
| `InitializationPlanProvider` | 提供额外初始化计划来源。 |
| `TargetDatasetGateway` | 改写目标数据集查询和插入方式。 |
| `InitializationLedger` | 替换账本存储。 |
| `InitializationLock` | 替换并发控制。 |
| `InitializationTransaction` | 替换事务边界。 |
| `DataInitializationLifecycleAdapter` | 改写触发时机。 |

如果只是新增初始化数据，应只新增 `.data-init.json`，不要新增 runner、listener 或手写数据库脚本。

## 验证

验证资源打包：

```bash
mvn -pl <your-module> -am process-resources
find <your-module>/target/classes/META-INF/ouroboros/metadata -name '*.data-init.json'
```

验证初始化模块自身合同：

```bash
mvn -pl core/ouroboros-data/ouroboros-data-initialization-runtime -am test \
  -Dtest=ResourceDataInitializationPlanProviderTest,DataInitializationAutoConfigurationContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false

mvn -pl core/ouroboros-data/ouroboros-data-initialization-core -am test \
  -Dtest=DataInitializationReconcilerTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

运行时可通过日志确认完成情况，成功日志包含计划数、block 数、插入数和跳过数。

## 常见问题

### 文件没有被加载

检查文件名是否以 `.data-init.json` 结尾，并确认构建后出现在 `target/classes/META-INF/ouroboros/metadata/**`。作者态 `src/main/metadata` 不是运行时扫描目录。

### 启动时报缺少事务管理器

默认 `InitializationTransaction` 需要 `PlatformTransactionManager`。正常应用应提供 Spring 事务管理器；测试或特殊运行时可显式提供自定义 `InitializationTransaction`。

### 目标模型不存在

确认 `targetModel` 可以被 `DataModelCenter.getDataModel(targetModel)` 找到，并且目标模型已在模型刷新流程中注册完成。

### 已存在记录没有被更新

这是预期行为。数据初始化只补齐缺失记录，不覆盖已有记录。需要更新已有数据时，请使用迁移或业务服务。

### 查询结果不唯一

`matchFields` 没有唯一定位一条记录。应修正目标数据或改用能表达唯一性的匹配字段，并优先在目标模型上建立对应唯一约束。

# SCD2 历史插件设计

## 1. 背景

`ouroboros-data` 已经提供基于 `DataModelMetaDecorator` 和 `DataModelPlugin` 的增强机制，可在不侵入业务代码的前提下为模型补充审计、软删除等能力。

SCD2 历史插件沿用这套扩展体系，为指定源模型增加“按版本记录历史快照”的能力。它的目标不是替代业务表，而是在独立历史模型中记录每次增删改后的版本链，用于追溯字段变更、恢复任意时间点快照、以及确认删除发生时的业务状态。

这里使用的“拉链表”对应数据仓库语境中的 `Slowly Changing Dimension Type 2`（SCD Type 2）。在平台实现里，它是一个按模型启用的数据模型插件，而不是数据库触发器或独立同步任务。

## 2. 设计目标

- 支持按模型启用历史版本能力，不要求业务代码显式调用历史写入逻辑。
- 历史模型记录源模型每次 `insert`、`update`、`delete` 后的业务快照和版本管理字段。
- 仅当忽略字段之外的业务字段发生变化时，`update` 才生成新的历史版本。
- 与现有 `BasicAudit`、`AccessControl`、删除策略插件共存，并保持写入语义一致。
- 保持源模型对外返回值不被历史模型主键污染。

## 3. 设计边界

- data-core 插件不自动创建历史模型和表结构；业务模型特性投影层在 `enableScd2History=true` 时会自动生成并列历史 `DataModelMeta`，并向源模型写入运行时所需的规范化配置。
- 初版不再要求源模型和历史模型位于同一 `DataStation`；历史模型可通过源模型所属站点或全局 `DataModelCenter` 解析。
- 只覆盖通过 `DataModel` 入口发起的写入；绕过 `DataModel` 的底层 JDBC/Adapter 写入不在本设计保证范围内。

## 4. 配置与建模约定

### 4.1 源模型配置

业务模型层只暴露一个开关：

```json
{
  "enableScd2History": true
}
```

投影后，源数据模型的 `extraProps` 会包含 data-core 运行时消费的规范化配置，例如：

```json
{
  "enableScd2History": true,
  "scd2HistoryConfig": {
    "historyModelFullName": "demo.orderHistory",
    "businessKeyField": "businessKey",
    "validFromField": "validFrom",
    "validToField": "validTo",
    "isCurrentField": "isCurrent",
    "opField": "opType",
    "operatorField": "operator"
  }
}
```

data-core 当前默认值：

- `businessKeyField=businessKey`
- `validFromField=validFrom`
- `validToField=validTo`
- `isCurrentField=isCurrent`
- `opField=opType`
- `operatorField=operator`
- `storeDiff=false`
- `changedFieldsField=changedFields`
- `changeSetField=changeSet`
- `maxRows=200`

### 4.2 历史模型字段约定

业务模型投影层会自动生成与源模型同 namespace 的并列历史模型，默认名称为 `{源模型名}History`，存在同名模型时追加序号避让。历史模型默认由自己维护历史行主键，优先使用 `id` 作为历史表主键；源模型主键不再以同名快照字段复制，而是只写入 `businessKeyField`。

如果源模型字段为 `id/name/status/updatedAt`，则推荐的历史模型字段集合是：

- `id`
- `businessKey`
- `name`
- `status`
- `updatedAt`
- `validFrom`
- `validTo`
- `isCurrent`
- `opType`
- `operator`
- 可选：`changedFields`
- 可选：`changeSet`

其中：

- `id` 表示历史行自己的主键
- `businessKey` 表示源模型主键值
- `name/status/updatedAt` 表示业务字段快照
- `validFrom/validTo/isCurrent/opType` 表示 SCD2 版本管理字段
- `operator` 表示当前数据操作人身份，由运行时通过 `DataOperationIdentityProvider` 提供；无登录上下文时为 `null`
- `changedFields/changeSet` 只在 `storeDiff=true` 时要求存在

### 4.3 冲突规避原则

- 源模型主键让位给历史表自己的 `id`
- 历史表通过 `businessKey` 保存源模型主键
- 如果源模型已经存在历史元字段同名业务字段，投影层会为历史元字段自动改名并同步写入 `scd2HistoryConfig`
- 版本管理字段使用显式命名，如 `validFrom`、`validTo`、`isCurrent`、`opType`
- 操作人字段使用显式命名，默认 `operator`
- 差异字段使用显式命名，如 `changedFields`、`changeSet`

这套约定的目的，是让“历史行主键”和“业务主键”语义分离，避免后续联查、回溯和批量补历史时混淆。

## 5. 运行时装配方式

### 5.1 元数据装饰器

`Scd2HistoryDecorator` 负责在模型启用 `enableScd2History` 后向 `pluginDescriptors` 注入 `PluginDescriptor("Scd2History", config)`。

顺序规则：

- 若存在 `AccessControl`，则插入到它之后
- 若存在 `LogicalDelete` 或 `ArchiveDelete`，则插入到它们之前
- 默认顺序下位于 `BasicAudit` 之后、删除策略插件之前

这样做的原因是：

- 需要先让 `AccessControl` 把最终生效的 `where/data` 收敛好，历史插件再基于真实写入语义记历史
- 需要在删除策略插件把删除改写成逻辑删除更新或归档删除流程之前捕获 `delete` 语义，才能正确写出 `opType="DELETE"`

### 5.2 代理链路补齐

SCD2 插件依赖“所有公开写入口都经过插件链”。因此实现同时补齐了 `EnhancedDataModelProxy` 的写入代理，让以下入口不再绕过插件：

- `update(id, data)`
- `update(ids, data)`
- `delete(id)`
- `delete(ids)`
- `insertOrUpdate(...)`
- `batchInsertOrUpdate(...)`

没有这层补齐，插件只能覆盖 `update(where, data)` / `delete(where)` 等少数入口，历史记录会出现漏记。

## 6. 写入语义

### 6.1 `insert(data)`

- 源模型先执行插入
- 插入成功后，在历史模型写入 1 条 `INSERT` 版本
- 对外仍返回源模型记录，不返回历史模型记录

### 6.2 `update(where, data)`

- 先读取更新前快照
- 执行源模型更新
- 再读取更新后快照
- 对每条命中的记录做 diff
- 仅当忽略字段之外的业务字段发生变化时，才关闭旧版本并写入新的 `UPDATE` 版本

更新前后快照查询会通过“无插件源模型视图”执行，但使用的是已经被上游插件收敛过的 `where/data` 或其派生主键集合，因此不会影响对外查询语义，同时也避免把 `readAuthority` 一类查询时语义再次叠加到历史快照读取上。

如果只更新了 `ignoreFields` 中的字段，则不会创建新的历史版本。

### 6.3 `delete(where)`

- 先读取删除前快照
- 执行源模型删除
- 关闭旧的当前版本
- 写入 1 条 `DELETE` 版本

当前实现里，`DELETE` 版本本身会保留 `isCurrent=true`，表示“该业务键当前状态是已删除”。这让历史表既能表示“最后一个有效状态”，也能明确当前是否已经删除。

### 6.4 批量更新与批量删除

当前实现会按命中的每一条记录逐条补历史版本，而不是只写一条批处理摘要。这样历史表仍然以“业务键 + 版本链”为主语义，不因为批量操作丢失逐记录追溯能力。

### 6.5 差异明细

`Scd2HistoryDiff` 会基于源模型的物理字段计算变更集：

- `changedFields`：变更字段列表
- `changeSet`：字段级 `from/to` 明细

当 `storeDiff=false` 时：

- 仍会计算 diff，用于判断是否需要开新版本
- 但不会把 `changedFields/changeSet` 落到历史模型

当 `storeDiff=true` 时：

- 历史模型必须包含 `changedFieldsField` 与 `changeSetField`
- 插件会把差异信息按 JSON 或字段自身持久化类型写入历史模型

### 6.6 大批量保护

为了避免一次条件更新命中过多记录导致历史写入放大，配置中提供 `maxRows`：

- 当更新前读取到的命中行数大于 `maxRows` 时，插件直接失败
- 调用方应改为缩小 where 条件，或改成按主键分批处理

## 7. 一致性与安全边界

- 历史模型既可以在源模型所属 `DataStation` 中解析，也可以从全局 `DataModelCenter` 解析
- 建议在业务入口使用 Spring `@Transactional` 统一包裹
- 历史写入失败时，插件返回失败，由上层事务决定整体回滚
- 历史模型会通过 `withoutPlugins()` 获取无插件视图，避免历史写入再次进入权限、软删或历史插件自身
- 历史模型自身禁止开启 `enableScd2History`，防止递归写入
- 若装配阶段已经能解析到历史模型，则配置非法、必需字段缺失、历史模型递归启用等问题会直接失败，不再静默降级为“未安装插件”

## 8. 当前实现状态

当前实现口径如下（以插件、装饰器和业务模型投影源码为准）：

- 业务模型入口只暴露 `enableScd2History` 开关，data-core 运行时配置入口仍为 `scd2HistoryConfig`
- 业务模型特性投影会自动生成并列历史模型，并写入 `historyModelFullName`
- 历史表默认由自己维护 `id`
- 源模型主键默认写入 `businessKey`
- 源模型主键不会作为同名快照字段复制到历史表
- `changedFields/changeSet` 是可选字段，只在 `storeDiff=true` 时要求存在
- `insert(...)` 对外返回源模型记录
- `DELETE` 版本会作为当前版本保留

## 9. 验证资产与限制

以下自动化测试资产覆盖对应路径；测试存在不代表本次 HEAD 已执行通过：

- 代理链路测试：验证 `update(id/ids)`、`delete(id/ids)`、`insertOrUpdate`、`batchInsertOrUpdate` 不再绕过插件链
- 装饰器测试：验证 `Scd2History` 与 `AccessControl` / `BasicAudit` / `LogicalDelete` / `ArchiveDelete` 的顺序关系
- 核心插件单测：验证配置解析、字段校验、历史模型递归防护、`ignoreFields`、`storeDiff`、`maxRows` 等关键逻辑
- 集成测试：验证 `INSERT` / `UPDATE` / `DELETE`、忽略字段不生成新版本、差异明细落库、批量更新、批量删除等主路径

当前尚未专门覆盖的边界主要有：

- 更深层的并发竞争场景
- 更完整 SQL 场景下 `maxRows` 的专项失败断言
- 显式事务回滚语义的集成验证

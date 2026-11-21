# ADR-003: P0-3 Family-Splitting Strategy

**状态**: Accepted（已实施）\
**实施情况**（2026-11-16 核对）：`uischema`、`statistics` 与 `appmodule` 已由两套架构入口共享的 `ArchitectureLayerSupport` 按模块上下文和精确类型赋层；现有模块中新类型继承该模块角色，未知模块根目录会使门禁失败。
**日期**: 2026-04-20\
**决策者**: @arch-team\
**相关模块**: `testing/architecture-tests`, `core/ouroboros-ui-model`, `core/ouroboros-app-module-model`, `app-module/ui-schema-management`

## 背景

`P0-3` 用于约束 Ouroboros 的分层依赖方向，但近期审查表明：部分关键 package family 已经大到无法通过“family root → layer”方式被正确建模。

尤其是：

- `com.ouroboros.uischema`
- `com.ouroboros.appmodule`
- `com.ouroboros.statistics`

这些 family 同时跨越多个职责层：

- 核心语义模型 / 引擎
- 运行时适配
- 开发态工具
- 应用级管理 UI

在这种情况下，继续把 family root 直接映射到一个 layer，会导致 `P0-3` 同时出现两类风险：

1. **假阳性**：合法的不同职责被误判为跨层依赖
2. **假阴性**：为让测试变绿而不断调 layer map，反而把真实问题掩盖掉

此前一次“补全层图”的尝试已经暴露出大量新违规，证明当前问题首先是**family 拆分和边界定义**问题，而不是简单的规则实现问题。

## 决策

对 `P0-3`，不再继续把 `uischema`、`appmodule` 和 `statistics` 作为单一 family root 建模。

改为：

1. 对这些 family 采用 **subpackage-aware modeling**
2. 对仅靠包后缀仍无法区分职责的位置，允许使用 **module-context-aware classification**
3. 在 family/subpackage 语义未明确前，不进行大规模 `P0-3` baseline 扩张

### 明确决定

#### 1) `uischema` 不再按 family root 建模

优先按以下思路处理：

- Core
  - `com.ouroboros.uischema.schema..`
  - `com.ouroboros.uischema.schema.event..`
  - `com.ouroboros.uischema.schema.decorator..`
  - `com.ouroboros.uischema.wrapper..`
  - `com.ouroboros.uischema.util..`
- Dev
  - `com.ouroboros.uischema.dev..`
  - `com.ouroboros.uischema.dev.service.impl..`
  - `com.ouroboros.uischema.dev.inspector..`
- App
  - `com.ouroboros.uischema.model..`
  - `com.ouroboros.uischema.service..`
  - `com.ouroboros.uischema.service.impl..`

特殊情况：

- `com.ouroboros.uischema.controller..` 允许按模块上下文区分：
  - `ouroboros-ui-model-schema-runtime` → Bridge/runtime-facing
  - `app-module/ui-schema-management/**/.../uischema/controller/**` → App

#### 2) `appmodule` 不再按 family root 建模

当前按投影组件的实际边界建模：

- `ouroboros-app-module-model-core`、`-runtime`、`-procode` → Bridge；这些模块的公开接口与聚合行为携带权限树/菜单投影语义。
- `ouroboros-app-module-model-dev` → Dev。
- 四个 authority-linked 类型及其内部类同时以精确类型标识，见 [ADR-004](ADR-004-appmodule-authority-bridge-band.md)。不把整个 family 前缀或未来同名前缀模块自动归入 Bridge。
- bare root 与 `appmodule.model/runtime` 均按所属模块覆盖，不再留空。

#### 3) 门禁必须验证覆盖与真实依赖

两套架构测试共用 `ArchitectureLayerSupport`。Foundation 禁止向其他层依赖，Core 只依赖 Foundation；Bridge 允许 Foundation/Core/Security。Platform 是正式受检层。每个导入的生产类必须归层，不能靠遗漏包名或扩大 allowlist 恢复绿色。

#### 4) `statistics` 不再按 family root 建模

优先按以下思路处理：

- Core
  - `com.ouroboros.statistics.reporting.definition..`
  - `com.ouroboros.statistics.reporting.validation..`
- Ability / runtime capability
  - `com.ouroboros.statistics.reporting.runtime..`
- Dev
  - `com.ouroboros.statistics.reporting.dev..`

特殊情况：

- `DataOperationIdentityProvider` 的安全上下文实现属于 security adapter 职责，应位于 `com.ouroboros.security..` 包下；data core 只保留 SPI。

## 理由

### 为什么不继续 root-family 建模

- `uischema` 和 `appmodule` 的 family root 已证明过粗
- 直接 root 建模会把职责混杂问题转化为测试噪音
- 继续微调 layer map 容易把错误架构认知硬编码进测试

### 为什么允许 module-context-aware classification

仅靠包后缀不足以区分某些职责，例如：

- `uischema.controller` 同时存在于 core runtime 与 app management 中

如果禁止模块上下文感知，就只能二选一：

- 要么强行赋错层
- 要么不得不先做大规模重命名/重构

两者都不是当前最小、最诚实的方案。

## 后果

### 正面影响

1. `P0-3` 更接近仓库真实结构，而不是抽象理想图
2. `uischema` / `appmodule` 这类高歧义 family 可被精细建模
3. 未来 baseline 迁移可以绑定在更稳定的语义上

### 负面影响

1. `P0-3` 的规则实现会更复杂
2. 规则维护者需要同时理解 package + module context
3. 模块上下文的变化需要同步分类模型和真实编译夹具

### 风险

1. 子包粒度仍可能不够，需要再细分
2. module-context-aware 实现若设计不好，可能引入新的误判
3. 如果后续 baseline 迁移过快，仍可能掩盖语义错误

## 替代方案

### 方案 A：继续 family-root-only 建模
- **优点**: 简单
- **缺点**: 已被证明对 `uischema` / `appmodule` 不成立
- **为何未选择**: 结构上错误

### 方案 B：继续微调 layer map 直到测试变绿
- **优点**: 短期看起来推进快
- **缺点**: 容易把错误架构认知固化进规则
- **为何未选择**: 之前已经暴露出大规模噪音波次

### 方案 C：立即扩大 `P0-3` baseline
- **优点**: 测试容易恢复绿色
- **缺点**: 掩盖 model-definition 问题
- **为何未选择**: 会让语义漂移被沉没进 allowlist

### 方案 D：立即回退到 `consideringAllDependencies()`
- **优点**: 检查面更广
- **缺点**: 在 family 语义未决时会放大噪音
- **为何未选择**: 当前不是最小正确动作

## 约束与验证

不扩大 P0-3 存量豁免，不移除显式 reactor scope manifest。继续使用 `consideringOnlyDependenciesInLayers()`，同时要求所有导入类型归层；第三方框架类型不等于平台业务层。

`ArchitectureLayerSupportTest` 使用真实编译类型验证 Foundation 向上依赖拒绝以及 Core 访问 Security/Bridge 拒绝。正式入口导入当前 reactor 的全部生产输出，再执行存量 allowlist 过滤。

## 参考资料

- [P0-3 分层设计提案](../design/layers/p0-3-family-splitting.md)
- [现有 ADR 索引](README.md)

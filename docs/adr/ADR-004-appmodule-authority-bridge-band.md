# ADR-004: AppModule Authority Bridge Band

**状态**: Accepted（已实施）
**实施情况**（2026-11-16 核对）：`ouroboros-app-module-model-core`、`-runtime` 与 `-procode` 的权限/菜单投影类型按模块上下文归入 Bridge；`ArchitectureLayerSupport` 对四个 authority-linked 类型和该投影组件实施精确分类，禁止 Bridge 向 Ability/App/Dev/Platform 反向依赖。
**日期**: 2026-04-23  
**决策者**: @arch-team  
**相关模块**: `core/ouroboros-app-module-model`, `security/ouroboros-web-security-core`, `testing/architecture-tests`

## 背景

在 `P0-3` 对 `appmodule` 的 Phase 2 dry-run 中，一旦把 `com.ouroboros.appmodule.model..` 按 strict Core 处理，就立即暴露出一簇高度集中的依赖关系，全部围绕：

- `com.ouroboros.security.authority.Authority`
- `com.ouroboros.security.authority.accesscontrol.AccessControlDefine`

代表性类型包括：

- `AuthorityTreeNode`
- `ArrangedModuleAuthority`
- `PlacementModuleAuthority`
- `AuthoritiesAppModule`

这些类型并不是偶发 helper，而是直接：

- 实现或包装 `Authority`
- 三个 Authority 实现暴露访问控制定义；AuthoritiesAppModule 仅包装权限列表，不直接暴露 AccessControlDefine
- 将权限语义投影到模块组织结构之中

这说明问题不是一个小实现 bug，而是一个明确的架构判定问题：

> `appmodule.model` 中这组 authority-linked 类型，是否应被视为合法的 bridge / projection band？

## 决策

在当前架构治理下，接受 `appmodule.model` 中 authority-linked cluster 作为一个**合法的 bridge / projection band**，而不是把它们直接按 strict Core semantic model 处理。

当前最小确认集为：

- `AuthorityTreeNode`
- `ArrangedModuleAuthority`
- `PlacementModuleAuthority`
- `AuthoritiesAppModule`

这意味着：

1. `appmodule.model..` 不能整体按 strict Core 处理
2. authority-linked cluster 需要在 `P0-3` 中单独建模
3. `appmodule.dev..` 可继续按 `Dev` 推进（已验证通过）
4. `appmodule.runtime..` 与 bare root 按 core/runtime/procode 三个既有模块的权限/菜单投影职责归 Bridge；不按 family 前缀赋层

## 理由

### 1. 这更符合当前代码现实

`Authority` 不是一个浅 DTO，而是完整的安全语义接口，暴露：

- `getAccessControlDefine()`
- `getApis()`
- `getUiViews()`
- `getMenuItems()`

任何实现/包装 `Authority` 的类型都天然携带真实安全语义。

### 2. 这能解释 dry-run 失败，而不是掩盖它

第一次 `appmodule` dry-run 失败时，违规并不是随机分散的，而是紧密聚集在 authority-linked cluster 上。

这说明问题在于：

- 当前 `strict Core` 假设过于激进

而不是：

- 需要立刻扩 baseline
- 或存在一批无关的历史旧债

### 3. 这是最小诚实方案

相比两种替代方案：

- 直接把这组类视为历史 debt 并要求先重构
- 继续 unresolved 让 `appmodule` 整体卡住

当前接受 bridge/projection band 是最小、最可推进、最不掩盖现实的选择。

## 后果

### 正面影响

1. `appmodule.dev` 已可继续安全推进
2. `P0-3` 可以在不扩大 baseline 的前提下继续细化 `appmodule`
3. authority-linked cluster 有了清晰的中间语义位置，不再被误判成纯 Core 模型

### 负面影响

1. 架构模型增加了一个桥接带概念
2. `appmodule` 的分层表达会变得更复杂
3. 未来若要进一步净化结构，仍需要重构工作

### 风险

1. 过度接受桥接带，可能让更多边界模糊代码被合理化
2. 如果后续不再推进重构，这个 bridge band 可能长期固化
3. 未来模块若拆出不携带权限语义的独立核心契约，应重新按其真实职责分类

## 替代方案

### 方案 A：视为历史 layering debt，立即要求重构
- **优点**: 长期结构更干净
- **缺点**: 当前无法推进 `P0-3`，且没有现成重构计划
- **为何未选择**: 代价过高，不适合作为当前最小前进路径

### 方案 B：继续 unresolved
- **优点**: 保守
- **缺点**: `appmodule` 持续阻塞，无法形成渐进推进路径
- **为何未选择**: 已经有足够证据支持给出更具体的判定

### 方案 C：接受为 bridge / projection band
- **优点**: 最符合当前代码现实，允许最小前进
- **缺点**: 需要在 `P0-3` 中引入更细粒度的带/分类概念
- **为何选择**: 是最诚实、最可执行的当前方案

## 当前实施含义

当前工程规则为：

1. `appmodule.dev..` 继续按 `Dev` 推进
2. authority-linked cluster 按 `Bridge / Projection` 建模
3. 三个现有投影模块按模块上下文覆盖，未来模块须重新核对角色
4. `appmodule.runtime..` 与 bare root 已覆盖，Bridge 不得依赖 Ability/App/Dev/Platform

## 相关资料

- [AppModule Authority Bridge](../design/layers/appmodule-authority-bridge.md)
- [P0-3 Family Splitting](../design/layers/p0-3-family-splitting.md)
- [ADR-003: P0-3 Family-Splitting Strategy](ADR-003-p0-3-family-splitting-strategy.md)

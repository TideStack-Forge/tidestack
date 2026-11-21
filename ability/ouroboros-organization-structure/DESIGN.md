# Ouroboros Organization Structure 设计文档

## 1. 概述

`ouroboros-organization-structure` 是平台的组织与人员结构能力模块，负责管理公司、部门、岗位、员工、任职关系以及组织上下文感知能力。它为安全层、审批流和业务模块提供组织信息来源，但自身不等同于认证或 RBAC 系统。

## 2. 模块结构

聚合 `pom.xml` 显示该模块当前由两个子模块组成：

- `ouroboros-organization-structure-core`
- `ouroboros-organization-structure-runtime`

`core` 负责组织与人员关系的稳定抽象、`OrganizationQueryFacade` 及其返回 DTO；对象的 `source()` 提供 LOCAL/EXTERNAL 来源语义，DTO 不通过具体持久化模型类型判断。`runtime` 承接管理服务、REST API、自动配置以及与安全、UI、字段仓库等能力的运行时接入。人员档案配置通过已有 `ConfigurationService` / `ConfigurationManagementService` 核心契约读取和修改，保持原并发版本检查与管理写入机制。

## 3. 核心模型

### 3.1 组织节点与人员关系

该模块围绕统一的组织节点模型展开，关键实体包括：

- `OrganizationNode`
- `Company`
- `Department`
- `Position`
- `Employee`
- `Appointment`

这些抽象共同描述了组织树、人员归属和任职关系，是组织结构能力的核心语义层。

### 3.2 User 与 Subject 的协作背景

旧设计稿强调了员工与用户身份之间的关联关系，这说明该模块不仅管理组织结构本身，也承担“为安全和业务提供组织身份来源”的作用。但这种关联只是协作边界，不意味着它取代安全层定义登录和授权语义。

### 3.3 上下文系统

`OrganizationContext` 与 `OrganizationContextHolder` 负责在运行时感知当前组织上下文，这是该模块区别于静态组织数据管理的重要能力。它使服务层和上层模块可以在上下文中获取组织信息，而不需要在每层手工传递。

### 3.4 人员档案表单方案的设计动机与边界

动机：旧的 `template -> runtime override -> edit/display layout` 模型把实现细节直接暴露给用户，配置管理页面同时出现 `template`、`runtime-override`、`edit-layout`、`display-layout` 等底层概念，用户既难理解边界，也无法把人员档案表单当成一个完整对象来管理。拆到根因上有四点：

1. 用户面对的是底层拼装件，而不是完整业务对象。
2. 新增、编辑、详情三种视图没有被建模为同一方案下的三个 schema，只能靠多个配置键间接表达。
3. 运行时匹配没有稳定的“方案选择”概念，只是对同一个模板做叠加和重排。
4. 编辑体验停留在字段列表和 JSON 配置层，缺少正式的可视化表单设计入口。

目标：引入 `PersonnelProfileFormScheme` 作为用户可见、可管理、可迁移的正式领域对象，把三类 schema 收敛到同一实体下，保留旧配置键的兼容读取但降级为迁移输入和兜底来源，并复用既有 `amis-editor` / `amis-schema-editor` / editor plugin 基础设施而不是构建平行编辑器。

非目标：不重写 `PersonnelProfileField` 字段定义机制；不引入通用规则引擎或表达式驱动的复杂匹配系统；不把 `ui-schema-management` 替换成第二套通用前端配置中心；不在第一阶段修改员工档案值的存储结构；不为这个场景额外创建与本模块风格不一致的 repository 层。

边界：本设计覆盖管理边界（开发管理侧如何 CRUD 方案）、运行时边界（运行时如何基于上下文解析方案）与兼容边界（旧配置键如何被适配、预览、迁移和逐步降级）；不覆盖员工档案值的写入流程本身，也不改变人员档案字段仓库的定义来源。

### 3.5 人员档案表单方案持久化模型

本章及后续章节引用的 Java 文件路径均相对 `ability/ouroboros-organization-structure/ouroboros-organization-structure-runtime/src/main/java/com/ouroboros/organizationstructure/`，测试路径相对同模块 `src/test/java/com/ouroboros/organizationstructure/`。

`PersonnelProfileFormSchemeEntity` 是本模块对“场景化人员档案表单方案”的持久化建模，用 `@Model(rawName = "t_sys_personnel_profile_form_scheme", label = "人员档案表单方案")` 加 `@AuditFields` 声明（依据：`profile/entity/PersonnelProfileFormSchemeEntity.java:14-15`）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `String` | 主键，`@PrimaryKey(generator = "snowflake")` |
| `appName` | `String` | 所属开发应用，可空；空值即全局方案 |
| `name` | `String` | 方案名称，非空 |
| `description` | `String` | 方案说明，可空 |
| `enabled` | `Boolean` | 是否启用 |
| `isDefault` | `Boolean` | 是否为默认方案 |
| `priority` | `Integer` | 手工优先级，数字越大优先级越高，可空 |
| `scope` | `Map<String, Object>` | 方案作用域 |
| `matchingRules` | `Map<String, Object>` | 结构化匹配规则 |
| `createSchema` / `editSchema` / `detailSchema` | `Map<String, Object>` | 新增 / 编辑 / 详情场景的 AMIS schema |
| `legacySource` | `Map<String, Object>` | 兼容迁移元数据 |
| `createdAt` / `updatedAt` | `LocalDateTime` | 审计字段 |
| `createdBy` / `updatedBy` | `String` | 审计字段 |

约定：

- 沿用本仓库扁平审计字段，不额外创建 `baseInfo` 聚合对象。
- `scope`、`matchingRules`、三个 schema 与 `legacySource` 均以 `Map<String, Object>` 存储，与 `CustomUISchema.schema` 的存储风格一致；getter 对 `null` 返回空 Map，setter 做防御性拷贝（`PersonnelProfileFormSchemeEntity.java:128-174`）。
- 第一阶段不引入强类型规则对象或 wrapper，先靠服务层校验限制可写字段集合。

### 3.6 结构化字段约定

`scope` 当前只有两种取值：

```json
{ "type": "GLOBAL" }
```

```json
{ "type": "APP", "appName": "hr-dev" }
```

`GLOBAL` 表示平台兜底方案，`APP` 表示应用内方案；适配器按 `appName` 是否为空自动二选一（依据：`profile/service/impl/LegacyPersonnelProfileConfigAdapter.java:60-69`）。

`matchingRules` 的设计形态是结构化选择器，不是表达式语言：

```json
{
  "matchMode": "ALL",
  "organizationIds": ["org-1"],
  "departmentIds": ["dept-1"],
  "roleKeys": ["hr-manager"],
  "positionIds": ["pos-1"],
  "employeeTypes": ["FULL_TIME"],
  "employeeIds": []
}
```

设计约定为：维度之间 `AND`（配置了的维度都必须命中）、维度内部 `OR`（命中任一项即可）、`matchMode` 第一阶段只允许 `ALL`。

**实现事实**：`matchingRules`（以及 `scope`）当前只被持久化，没有任何读取方。本模块与主前端包中检索 `matchMode`、`organizationIds`、`roleKeys`、`employeeTypes`、`specificity` 等维度名均无命中，`matchingRules` 也只在实体定义与适配器写入空 Map 处出现（依据：`LegacyPersonnelProfileConfigAdapter.java:52`）。因此上面三条语义是**待落地约定，不是生效规则**。

### 3.7 运行时解析结果

设计稿曾建议引入只读视图模型 `ResolvedPersonnelProfileFormScheme`（含 `schemeId`、`mode`、`schema`、`matchedBy`、`priority`、`legacyFallback`），代码中不存在该类型。

实现事实是运行时直接返回 schema 的 `Map<String, Object>`：`PersonnelProfileRuntimeConfigService.getResolvedEditSchema()` 与 `getResolvedDetailSchema()`（依据：`profile/service/impl/PersonnelProfileRuntimeConfigServiceImpl.java:97-105`）。接口中没有 `getResolvedCreateSchema()`，即新增场景未接入运行时解析链。

## 4. 服务与运行时集成

### 4.1 服务层与 Provider / Aggregator 模式

设计稿中的服务层采用 Provider 与 Aggregator 组合模式：

- Provider 负责基础数据访问
- Aggregator 负责复杂聚合查询与组合逻辑

这种结构有利于把底层组织数据访问与上层业务读取逻辑分开。

### 4.2 Runtime 层

`runtime` 层负责：

- 组织和员工管理接口
- 自动配置
- 安全集成
- UI 模型和字段仓库接入

这意味着该模块不仅定义组织模型，也负责把这些模型真正暴露为平台能力。

### 4.3 人员档案表单方案服务边界

规范链路是：开发管理侧 `PersonnelProfileFormSchemeController` 管理方案 → `PersonnelProfileFormSchemeService` 用 `TypedDataModel` 持久化 → 运行时解析 → `EmployeePersonnelProfileUISchemaProvider` 取用 schema → 无持久化方案时由 `LegacyPersonnelProfileConfigAdapter` 兜底。设计稿中的“独立 resolver”这一环不存在，实际由持久化服务的 `findDefaultScheme` 加运行时配置服务的编排共同承担。

**持久化服务**：`PersonnelProfileFormSchemeService` / `PersonnelProfileFormSchemeServiceImpl` 直接持有 `TypedDataModel<String, PersonnelProfileFormSchemeEntity>`，不新增 repository 抽象。实际接口只有 7 个方法（依据：`profile/service/PersonnelProfileFormSchemeService.java:11-23`）：

```java
PersonnelProfileFormSchemeEntity create(PersonnelProfileFormSchemeEntity entity, String operatedBy);
Boolean update(String id, PersonnelProfileFormSchemeEntity entity, String operatedBy);
Boolean delete(String id, String operatedBy);
Optional<PersonnelProfileFormSchemeEntity> getById(String id);
Optional<PersonnelProfileFormSchemeEntity> getById(String id, String appName);
List<PersonnelProfileFormSchemeEntity> searchAll(String appName);
Optional<PersonnelProfileFormSchemeEntity> findDefaultScheme(String appName);
```

- 与设计稿的差异：`appName` 不作为 create/update/delete 的独立参数，而是由 controller 从路径覆盖写入实体（依据：`controller/PersonnelProfileFormSchemeController.java:64,81`）；设计稿提出的 `resolve(...)`、`previewLegacyMigration(...)`、`migrateFromLegacy(...)` 未出现在接口中。
- 读取侧是“全量查询 + 内存过滤”：`searchAll`、`getById`、`findDefaultScheme` 都建立在 `doFindAll()`（`TypedDataModel.query(new HashMap<>())`）之上，并用 `@Cacheable(sync = true)` 缓存（依据：`profile/service/impl/PersonnelProfileFormSchemeServiceImpl.java:98-101`）。
- 写操作使用 `@CacheEvict(allEntries = true)` 整体失效，缓存名为 `ouroboros:organization:personnel-profile-form-scheme`（依据：`PersonnelProfileFormSchemeServiceImpl.java:26,36,48,62`）。
- `delete(id, operatedBy)` 只看删除行数，不做存在性校验（依据：`PersonnelProfileFormSchemeServiceImpl.java:63-66`）；存在性由 controller 负责。

**兼容适配服务**：`LegacyPersonnelProfileConfigAdapter`（`@Service`）负责把旧配置翻译成新模型，公开入口只有一个 `buildDefaultScheme(String appName)`：

- 直接注入 `RuntimeConfigurationService` 读取四个旧配置键，不经过 `PersonnelProfileRuntimeConfigServiceImpl`（依据：`LegacyPersonnelProfileConfigAdapter.java:34-38,129-136`）。
- 产出 `name = "默认人员档案表单方案"`、`enabled = true`、`isDefault = true`、`priority = 0`、`matchingRules` 为空 Map 的方案（依据：`LegacyPersonnelProfileConfigAdapter.java:44-57`）。
- `createSchema` 与 `editSchema` 内容相同，均由 `system-setting/organization-management/edit-employee.ui-schema.json` 按编辑态展开；`detailSchema` 由 `employee-detail.ui-schema.json` 按详情态展开（依据：`LegacyPersonnelProfileConfigAdapter.java:53-55`）。
- 展开过程把 `personnel-profile-field` 节点替换为模板字段解析结果，把 `personnel-profile-layout` 节点替换为按字段顺序生成的节点列表，并跳过 `hidden` 字段（依据：`LegacyPersonnelProfileConfigAdapter.java:209-241`）。
- 结果只在内存中生成，不写库。设计稿提出的 `previewLegacyScheme` / `materializeLegacyScheme` 未实现。

**匹配解析服务**：设计稿建议把匹配逻辑从 `PersonnelProfileRuntimeConfigServiceImpl` 解耦为独立协作者 `PersonnelProfileFormSchemeResolver`。代码中不存在该类，方案选择目前完全由 `PersonnelProfileFormSchemeServiceImpl.findDefaultScheme(appName)` 承担，运行时编排仍在 `PersonnelProfileRuntimeConfigServiceImpl` 内。

### 4.4 人员档案表单方案管理接口

管理接口统一落在开发管理前缀下：`/api/dev/{appName}/organization-management/personnel-profile-form-scheme`（依据：`controller/PersonnelProfileFormSchemeController.java:28`）。实际只发布 5 个 CRUD 端点：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/dev/{appName}/organization-management/personnel-profile-form-scheme` | 返回当前应用的方案列表 |
| `POST` | `/api/dev/{appName}/organization-management/personnel-profile-form-scheme` | 创建方案，`appName` 取路径值 |
| `GET` | `/api/dev/{appName}/organization-management/personnel-profile-form-scheme/{id}` | 方案详情，缺失返回 `404` |
| `PUT` | `/api/dev/{appName}/organization-management/personnel-profile-form-scheme/{id}` | 更新方案，缺失返回 `404` |
| `DELETE` | `/api/dev/{appName}/organization-management/personnel-profile-form-scheme/{id}` | 删除方案，缺失返回 `404` |

请求与响应约定：

- 创建/更新请求体直接使用与持久化模型同形的 JSON（`name`/`description`/`enabled`/`isDefault`/`priority`/`scope`/`matchingRules`/`createSchema`/`editSchema`/`detailSchema`）；`id`、`appName`、审计字段由服务端接管。
- 列表返回 `ItemsWebApiResult<PersonnelProfileFormSchemeEntity>`（全量 `items`），**不是** `PagedWebApiResult`；分页由 dev 页面 `crud` 组件在前端完成（依据：`PersonnelProfileFormSchemeController.java:40-42` 与 `metadata/dev/{appName}/organization-management/personnel-profile-form-scheme.ui-schema.json:309-314`）。
- 单条响应为 `WebApiResult<PersonnelProfileFormSchemeEntity>`；404 分支由 controller 显式构造 `WebApiResult.error(404, "人员档案表单方案不存在")`，并可通过 `WebApiResultHttpStatusAdvice` 映射为 HTTP 404（依据：`controller/PersonnelProfileFormSchemeControllerHttpStatusTest.java:32-61`）。
- 操作人取自 `SecurityContextHolder` 当前 `Authentication` 的 principal，取不到或为空白时回落为 `"anonymous"`（依据：`PersonnelProfileFormSchemeController.java:100-106`）。
- 设计稿表中的 `resolve-preview`、`legacy-preview`、`migrate-from-legacy` 三个端点未实现。

### 4.5 人员档案表单方案校验规则

服务层实际强制的约束（依据：`profile/service/impl/PersonnelProfileFormSchemeServiceImpl.java:103-161`）：

- `name` 非空白，否则 `400 人员档案表单方案名称不能为空`。
- `createSchema`、`editSchema`、`detailSchema` 都不能为空 Map，分别报 `400 新增/编辑/详情表单 schema 不能为空`。注意实现只判断“非空 Map”，不校验它是否为合法 AMIS `page` schema。
- 更新时禁止改 `id`（`400 人员档案表单方案 ID 不可修改`）与改 `appName`（`400 人员档案表单方案所属应用不可修改`）。
- 同一 `appName` 下最多一个 `isDefault = true` 的方案，**与 `enabled` 取值无关**（`400 同一应用下只能存在一个默认人员档案表单方案`）；设计稿写的“最多一个 `enabled = true && isDefault = true`”过窄，实现是“最多一个默认方案”。
- `appName` 比较前先 `trimToNull`，因此空字符串与 `null` 视为同一全局作用域（依据：`PersonnelProfileFormSchemeServiceImpl.java:144-161`）。
- 跨应用写入不是服务层显式校验，而是 controller 用路径 `appName` 覆盖请求体实现。

设计稿中另外三条规则未实现：`scope.type = APP` 时 `scope.appName` 必须等于路径 `appName`；`matchingRules` 不允许未知维度；`name` 在同一 `appName` 下唯一。

### 4.6 人员档案运行时解析策略

**候选收集**（依据：`profile/service/impl/PersonnelProfileRuntimeConfigServiceImpl.java:198-214`）：

1. 若 `PersonnelProfileFormSchemeService` Bean 可用，调用 `findDefaultScheme(null)` 取默认方案；
2. 该方案通过可用性检查（`editSchema` 与 `detailSchema` 均非空）时直接采用；
3. 否则（Bean 不可用、无命中或 schema 不完整）回退到 `LegacyPersonnelProfileConfigAdapter.buildDefaultScheme(null)`，并同样过滤一次可用性；
4. 两条路径都不成立时返回空 Map。

实现细节：

- 运行时固定传 `null`，即**只消费全局默认方案**；`appName` 非空的应用级方案不会被运行时命中（依据：`PersonnelProfileFormSchemeServiceImpl.java:155-161` 的 `matchesAppName`）。
- 解析异常被 `catch (Exception)` 捕获后记 warn 日志再回退旧配置（依据：`PersonnelProfileRuntimeConfigServiceImpl.java:206-208`），因此“查询失败”和“没有方案”在行为上不可区分，也没有指标或错误码。

**排序**（依据：`PersonnelProfileFormSchemeServiceImpl.java:84-96`）：`findDefaultScheme` 先过滤 `enabled = true`、`isDefault = true`、`appName` 匹配，再按 `priority DESC`（`null` 视为 `Integer.MIN_VALUE`）→ `updatedAt DESC`（`null` 视为 `LocalDateTime.MIN`）→ `id ASC`（忽略大小写）排序后取第一条。

设计稿描述的 specificity 评分（`employeeIds` 500 / `departmentIds` 400 / `organizationIds` 350 / `roleKeys` 300 / `positionIds` 250 / `employeeTypes` 200 / 无规则 0）与 `specificity DESC -> priority DESC -> isDefault DESC -> updatedAt DESC -> id ASC` 排序均未实现；当前排序没有 `specificity` 与 `isDefault DESC` 两级。

**模式选择**：运行时不接收 `mode` 参数，只区分编辑与详情两条读取路径（依据：`ui/provider/EmployeePersonnelProfileUISchemaProvider.java:16-34,44-53`）：

- `/system-setting/organization-management/edit-employee` → `getResolvedEditSchema()`
- `/system-setting/organization-management/employee-detail` → `getResolvedDetailSchema()`

`EmployeePersonnelProfileUISchemaProvider` 带 `@Priority(120)`，只映射这两个路径；`createSchema` 没有任何运行时消费点。设计稿“解析请求必须显式传入 `mode`”与 `CREATE`/`EDIT`/`DETAIL` 三态选择未落地。

## 5. 扩展机制

该模块的主要扩展方向包括：

- 新组织类型
- 服务层实现扩展
- 数据访问提供者
- UI 扩展
- 上下文集成

但无论扩展落点在哪里，都应守住“它提供组织上下文和组织身份来源”的边界，而不是让模块逐步吞并安全真相、业务流程或审批规则。

## 6. 兼容与迁移

### 6.1 旧配置模型

过渡期的旧链路建立在通用配置系统的四个配置键上（依据：`profile/PersonnelProfileConfigurationKeys.java:11-20`）：

| 配置键 | 承载模型 | 作用 |
| --- | --- | --- |
| `organization.structure.personnel-profile.template` | `PersonnelProfileTemplateDefinition` | 字段定义与顺序（`key`/`label`/`description`/`fields`/`metadata`） |
| `organization.structure.personnel-profile.runtime-override` | `PersonnelProfileRuntimeOverrideConfig` | 按字段覆盖属性 |
| `organization.structure.personnel-profile.edit-layout` | `PersonnelProfileRuntimeLayoutConfig` | 编辑态字段布局 |
| `organization.structure.personnel-profile.display-layout` | `PersonnelProfileRuntimeLayoutConfig` | 详情态字段布局 |

运行时旧解析链是先读模板，再叠加 override 与 layout（`layout.applyTo(override.applyTo(template))`），见 `PersonnelProfileRuntimeConfigServiceImpl.java:82-95`；`PersonnelProfileConfigurationKeys.RUNTIME_LAYOUT` 是 `EDIT_LAYOUT` 的 `@Deprecated` 兼容别名。配置值以 JSON 字符串存放在 `RuntimeConfigurationService`，解析失败抛 `ValidationException("人员档案运行时配置解析失败: <key>")`（依据：`PersonnelProfileRuntimeConfigServiceImpl.java:127-133`）。

### 6.2 兼容读取

`PersonnelProfileRuntimeConfigServiceImpl` 在过渡期仍然保留，但职责收敛为两条：

- 它不再是用户可见模型的唯一真相来源；面向 UI 的读取已改为 `getResolvedEditSchema()` / `getResolvedDetailSchema()`，优先取持久化方案。
- 只有在没有可用持久化方案时，才由 `LegacyPersonnelProfileConfigAdapter` 把旧配置即时投影成默认方案兜底。

旧配置键在代码层已经失去管理入口：`PersonnelProfileTemplateController` 与配置元数据 Provider `OrganizationStructurePersonnelProfileConfigurationMetadataProvider` 都在提交 `706988996c` 中随新方案页面一起移除，`controller/` 下只剩 `EmployeeController`、`OrganizationManagementController`、`OrganizationNodeController`、`OrganizationTreeController`、`AdminLevelController` 与 `PersonnelProfileFormSchemeController`。四个配置键现在只剩三处引用——配置键常量、运行时配置服务与兼容适配器（依据：`PersonnelProfileConfigurationKeys.java:11-14`）；`PersonnelProfileRuntimeConfigService` 上的 `saveTemplateDefinition` / `saveRuntimeOverrideConfig` / `saveRuntimeLayoutConfig` / `saveDisplayLayoutConfig` 与模板读取方法也都没有生产调用方，只在单元测试中被调用，生产侧只有 `getResolvedEditSchema()` / `getResolvedDetailSchema()` 有真实消费方（`EmployeePersonnelProfileUISchemaProvider`）。

### 6.3 迁移策略

设计稿的迁移分两步：`legacy-preview` 只读投影、`migrate-from-legacy` 落库成 `isDefault = true` 的真实方案并在 `legacySource` 记录来源。

实现状态：

- 只读投影已可用，入口是 `LegacyPersonnelProfileConfigAdapter.buildDefaultScheme(appName)`，但它没有被任何管理接口暴露，也没有迁移页面调用它；唯一调用方是运行时兜底（依据：`PersonnelProfileRuntimeConfigServiceImpl.java:210`）。
- 落库迁移动作完全未实现：`legacySource` 只在内存对象上填充，从不写入数据库。
- `legacySource` 的实际结构为 `{"source": "runtime-configuration", "templateKey": "personnel-profile", "importedKeys": [四个配置键]}`（依据：`LegacyPersonnelProfileConfigAdapter.java:71-81` 与 `PersonnelProfileConfigurationDefaults.java:22`）。

迁移落地后应达到的目标形态：新页面走方案 CRUD、运行时优先使用持久化方案且不再实时叠加旧配置。设计稿要求的“旧配置项降级为只读/高级入口”已被更强的做法取代——旧配置页面与元数据入口被整体移除，只保留兼容读取链路。

## 7. 前端编辑器契约

### 7.1 页面边界

已落地的 dev 管理页面是单页 CRUD（依据：`metadata/dev/{appName}/organization-management/personnel-profile-form-scheme.ui-schema.json`）：

- 列表：`crud` 组件，列为方案名称、方案说明、默认方案、启用状态、优先级、操作；顶部有说明当前应用只维护一套默认方案的 `alert`。
- 新增/编辑：弹窗表单，字段为 `name`、`description`、`enabled`、`isDefault`、`priority`、`createSchema`、`editSchema`、`detailSchema`。
- 查看：弹窗内 `tabs`，包含“基本信息 / 新增 Schema / 编辑 Schema / 详情 Schema”四个页签。
- 删除：带确认文案的 `ajax` 动作。

页面注册在 dev 元数据里：菜单 `metadata/Organization.PersonnelProfileFormScheme.dev-menu.json`（`fullName = OrganizationManagement.PersonnelProfileFormScheme`、`url = /dev/{appName}/organization-management/personnel-profile-form-scheme`、`stage = dev`、`showMode = tab`、`order = 110`）与视图模型 `metadata/dev/{appName}/organization-management/personnel-profile-form-scheme.ui-model.json`（`type = amis`）。

设计稿提出的“迁移页/弹窗”和“匹配规则编辑区”没有对应页面元素：表单里既没有 `scope` 也没有 `matchingRules` 字段。旧配置入口不是被降级成只读页，而是随 `PersonnelProfileTemplateController` 一起被整体移除。

### 7.2 编辑器策略

实现事实与设计稿不同，以代码为准：

- 三个 schema 使用的是 `json-editor`（查看态用 `type: "json"` 展示源码），不是设计稿要求的 `type: "amis-editor"` 主编辑形态（依据：`personnel-profile-form-scheme.ui-schema.json:76-92,186-211`）。仓库 metadata 中检索不到 `amis-editor` 的落点。
- 人员档案字段的编辑器插件已经具备：`PersonnelProfileFieldPlugin`（`rendererName = personnel-profile-field`）在 `frontends/web/main-app/src/components/amis/editor/plugins/PersonnelProfileField.tsx:70` 通过 `registerEditorPlugin` 自注册，并在 `plugins/index.ts:12` 引入；它提供拖拽脚手架与 `fieldName`/`label`/`required`/`disabled`/`description` 面板字段，但不属于主编辑入口。
- 渲染侧对应 `frontends/web/main-app/src/components/amis/renderers/PersonnelProfileField.tsx`。

因此“字段通过 editor plugin 暴露为可拖拽块”已落地，而“可视化表单方案主编辑器”仍是未完成项。

### 7.3 预览接口

设计稿要求编辑器不自己实现规则匹配，预览时调用后端 `resolve-preview`，让设计时与运行时共用同一套解析逻辑。

实现状态：该接口不存在，前端也无调用点；当前唯一的“预览”能力是查看弹窗里直接展示已保存的三个 schema JSON。规则匹配尚未实现，因此设计时一致性问题暂时不成立。

## 8. 测试策略

### 8.1 后端

已有测试落在 `ouroboros-organization-structure-runtime/src/test/java/com/ouroboros/organizationstructure/`：

- `profile/service/impl/PersonnelProfileFormSchemeServiceImplTest`：`findDefaultScheme` 按应用/全局取值、同应用默认方案唯一性拒绝、`searchAll` 按应用过滤。
- `profile/service/impl/LegacyPersonnelProfileConfigAdapterTest`：旧配置投影出可用 schema、`APP` 与 `GLOBAL` 作用域差异、运行时表单资源的 `initApi`/`api` 契约。
- `profile/service/impl/PersonnelProfileRuntimeConfigServiceImplTest`：旧配置四键读写、持久化方案优先、legacy 兜底、空 legacy 方案被忽略。
- `profile/model/PersonnelProfileTemplateDefinitionTest`：模板字段校验（重复字段名被拒）与模板/布局配置的 JSON 往返。
- `ui/provider/EmployeePersonnelProfileUISchemaProviderTest`：provider 返回解析后的 edit/detail schema 并注册两个路径。
- `ui/provider/PersonnelProfileFormSchemeDevSchemaContractTest`：dev 管理页 schema 契约（API URL、列表列、新增/编辑表单字段、详情页签）。
- `ui/provider/PersonnelProfileFormSchemeUISchemaPathResolutionTest`：只暴露 form-scheme dev 页面，旧 `personnel-profile-template` 页面与菜单已下线。
- `controller/PersonnelProfileFormSchemeControllerTest` 与 `controller/PersonnelProfileFormSchemeControllerHttpStatusTest`：CRUD 流程与 HTTP 404 语义。

缺口：没有场景匹配/排序测试（因为功能未实现），没有迁移相关测试，没有 `EmployeePersonnelProfileUISchemaProvider` 对真实 `PersonnelProfileRuntimeConfigServiceImpl` 的端到端集成测试。

### 8.2 前端

已有 `frontends/web/main-app/tests/unit/src/components/amis/personnel-profile-field.test.js`，覆盖字段组件导出、字段名存在/缺失时的占位渲染、编辑器插件元数据、脚手架、属性面板及模块导入。它不是完整方案管理页的浏览器 E2E；方案 CRUD、保存到运行时渲染的完整页面路径仍需补充验证。本轮仅核对测试资产，未执行前端测试。

### 8.3 手工验收

- 创建默认方案并保存三个 schema，确认列表、详情页签与运行时表单渲染一致。
- 构造不同 `appName` 的方案，确认运行时只取全局默认方案、应用级方案不生效（当前预期行为）。
- 清空持久化方案，确认兜底路径回到旧配置投影结果。
- 确认配置管理页面里已经没有旧的人员档案模板配置入口（旧链路只剩下兼容读取）。

## 9. 风险与取舍

| 风险/取舍 | 说明 | 处理策略 |
| --- | --- | --- |
| 使用 `Map<String, Object>` 而不是强类型规则对象 | 类型安全弱一些，但与现有 `CustomUISchema` 风格一致，首阶段成本最低 | 通过服务层校验限制可写字段集合；规则稳定后再抽象 |
| 不新增 repository 层 | 失去一层抽象，但避免与本模块 service + typed model 习惯冲突 | 若未来跨存储后端再引入，不在本阶段超前设计 |
| 三个 schema 均要求显式保存 | 编辑时工作量稍大，但运行时逻辑最简单、最可解释 | 前端提供复制按钮类交互，降低重复编辑成本 |
| 旧配置只在无 scheme 时回退 | 迁移后不会双轨混用，避免不可解释结果 | 通过只读投影与后续迁移动作让迁移可见 |
| 全量查询 + 进程内缓存 | 实现简单，但方案数量增长后 `doFindAll()` 与缓存失效成本上升 | 方案规模可控时接受；需要时改为带条件的 `TypedDataModel` 查询 |
| 运行时只取全局默认方案 | 与先落地的 CRUD 能力相称，但应用级方案暂时“可配不可用” | 场景匹配落地时一并解决，避免现在用 priority 模拟规则系统 |

## 10. 已知限制与未落地项

1. **场景匹配未实现**：`matchingRules` 与 `scope` 无任何读取方，specificity 评分与多级排序不存在（见 3.6、4.6）。
2. **三个设计时接口未实现**：`resolve-preview`、`legacy-preview`、`migrate-from-legacy` 都没有代码落点（见 4.4、7.3）。
3. **迁移只到“只读投影”**：`buildDefaultScheme` 的结果从不写库，`legacySource` 只存在于内存对象上（见 6.3）。
4. **新增场景未接入运行时**：没有 `getResolvedCreateSchema()`，`createSchema` 只被管理接口存取（见 3.7、4.6）。
5. **主编辑器仍是 JSON**：管理页用 `json-editor`，未采用 `amis-editor` 主编辑形态；字段级插件已具备（见 7.2）。
6. **运行时忽略应用维度**：固定 `findDefaultScheme(null)`，`appName` 非空的方案在运行时永远不命中（见 4.6）。
7. **校验缺口**：无 `scope` 与路径一致性校验、无方案名唯一性校验、无 schema 结构校验、无未知匹配维度校验（见 4.5）。
8. **兼容回退吞掉异常**：解析失败只记 warn 后回退旧配置，没有指标或错误码（见 4.6）。
9. **UI provider 持有静态服务引用**：`EmployeePersonnelProfileUISchemaProvider` 把 `PersonnelProfileRuntimeConfigService` 存在 `static` 字段，生产路径实际退化为 `ApplicationContextUtil.getBean` 查找，`setRuntimeConfigService` 只被测试调用（依据：`ui/provider/EmployeePersonnelProfileUISchemaProvider.java:19-23,44-53`）。
10. **旧配置链路只读且无管理入口**：四个旧配置键没有配置页面或元数据 Provider，`PersonnelProfileRuntimeConfigService` 的 `save*` 方法无生产调用方，兼容兜底只能消费历史值或默认值（见 6.2）。

## 11. 补充资料

以下材料保留为背景与演进证据，不替代本文：

- `补充深度资料`：[人员档案表单方案重新设计执行计划](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/docs/exec-plans/active/2026-05-07-personnel-profile-form-scheme-redesign-execution-plan.md)（历史执行计划，其中“先基线、再模型、再编辑器、再运行时、最后迁移收口”的交付顺序仍然有效；实施状态以本文第 10 节为准）
- `补充深度资料`：[ouroboros-organization-structure 模块设计摘要](../../docs/design/modules/ability/ouroboros-organization-structure.md)
- `补充深度资料`：[ui-schema-management 模块设计摘要](../../docs/design/modules/app-module/ui-schema-management.md)

## 12. 维护重点

维护该模块时，应持续关注：

- `core` 与 `runtime` 分工是否稳定
- 组织抽象是否仍然统一而不过度业务化
- 与安全、审批流、业务模型的边界是否清晰
- 上下文持有与延迟加载机制是否仍然安全、可控、可调试
- 人员档案表单方案的“设计态”与“运行态”是否仍共用同一条解析链：在 `resolve-preview` 落地前，编辑器预览与运行时渲染是两套事实，改动任一侧都要手工比对
- 修改 `PersonnelProfileFormSchemeEntity` 字段或校验规则时，同步检查 dev 页面契约测试（`PersonnelProfileFormSchemeDevSchemaContractTest`）与 `matchingRules`/`scope` 的待落地约定
- 兼容读取只在“无可用持久化方案”时生效；任何把旧配置重新混入候选集的改动都会破坏这条边界

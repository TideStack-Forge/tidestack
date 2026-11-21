# Ouroboros Configuration 设计文档

## 1. 概述

`ouroboros-configuration` 是平台的统一配置能力模块，负责定义配置元数据、读取与更新运行时配置，并把配置管理能力延伸到开发平台与逻辑流场景。它解决的是“配置如何被定义、管理和消费”的统一问题，而不是单个业务模块自己的参数语义。

## 2. 模块结构

聚合 `pom.xml` 显示该模块由四个子模块组成：

- `ouroboros-configuration-core`
- `ouroboros-configuration-runtime`
- `ouroboros-configuration-dev`
- `ouroboros-configuration-logicflow-node`

这四层分别对应配置协议、运行时服务、开发管理接入与流程编排接入，体现出配置能力既服务运行时，也服务维护侧和流程侧。

## 3. 核心模型

### 3.1 配置服务接口

`ConfigurationService` 是统一配置服务抽象，定义配置读取和管理的基础契约。`RuntimeConfigurationService` 则承接运行时环境中的具体配置读写语义，是运行时配置层的主服务接口。

### 3.2 配置元数据

配置体系的基础不只是值，而是 `ConfigurationMetadata`：

- 描述配置键、标签、说明、值类型、默认值、编辑器 schema 和专用管理入口
- 通过 `ConfigValueType` 确定值类型边界和转换规则
- 通过元数据把“如何管理配置”前置为平台能力

这意味着配置体系是“元数据驱动”，而不是简单的 key-value 存储。

### 3.3 配置提供器与中心

`ConfigurationMetadataProvider` 是元数据扩展入口，允许不同模块以统一方式声明自己的配置项。`ConfigurationMetadataCenter` 则负责在运行时统一汇总这些元数据，形成可查询、可管理的配置中心。

## 4. 运行时与上层集成

### 4.1 运行时配置管理

`ouroboros-configuration-runtime` 负责对外提供配置读取和更新能力，并通过控制器暴露管理接口。它回答的是“当前环境下哪些配置在生效”。

#### 4.1.1 认证配置的权威读取与管理策略

认证和发证使用 `ConfigurationService.getSnapshot(key)` 从同一个有效值取得内容与版本。元数据默认值用于没有保存值的场景；已保存的坏值不得回退到默认值。配置管理通过原服务的策略扩展控制可见字段和可写操作，受保护配置的通用管理接口同样需要授权与版本检查。保存沿用原事务和数据库 CAS，提交后再驱逐缓存并发布变更事件。客户端凭据投影、命令和恢复默认规则见[OAuth2 客户端配置的安全读写契约](../ouroboros-oidc-server/configuration-management-design.md)。

账号资料规则适配器位于本模块 `configuration.profile`，实现安全 core 的 `UserProfilePolicySource` 与本组件的管理策略契约；安全模块仅声明规则元数据和消费快照，不新增 security → ability 依赖。

客户端列表和资料规则仍使用原配置值表。值列经原数据模型迁移扩展为长文本，保留原键、值与版本；无需另建表或搬迁配置。升级前备份配置值表，升级后核对旧值与版本，再用实际大小的结构化配置验证写入。回退时保留已扩展列；若按旧模型自动迁移，应先确认现存值不会被缩回旧列的容量。

#### 4.1.2 配置项的专用管理接管

组件通过原 `ConfigurationMetadataProvider` 或配置元数据文件，在配置定义中声明 `managementEntry`，指向站内专用管理页面。声明按配置 key 生效；同一页面可以接管多个配置项。配置的 key、类型、默认值、分组及校验器仍在统一配置中定义，运行时值仍使用原配置表。

```json
{
  "key": "security.auth.oauth2.clients",
  "label": "接入应用（OAuth2 / OIDC）",
  "valueType": "array",
  "groupPath": "security.auth",
  "managementEntry": "/system-setting/auth-clients"
}
```

Java 元数据提供者使用 `metadata.setManagementEntry("/system-setting/auth-clients")` 声明同一内容。入口必须为 `/` 开头的站内绝对路径，外部 URL、协议相对路径、空字符串和空白值拒绝。未声明时沿用原系统配置工作台；原元数据合并及 JSON 复制保留该声明。

| 元数据声明 | 系统配置工作台行为 |
| --- | --- |
| 未声明 managementEntry，未指定 editorSchema | 按值类型生成原通用编辑器 |
| 未声明 managementEntry，指定 editorSchema | 在原工作台内使用自定义编辑器 |
| 声明 managementEntry | 由专用入口接管，工作台不返回该配置项的表单、详情或统计，也不把它加入组保存 |

工作台在原快照组装处统一排除被接管的配置项，分组定义继续沿用原规则。完整管理投影和内部配置快照仍保留它，专用页面通过原管理服务读取和保存。接管声明是管理入口的选择；服务端可见范围、字段权限及受控写入继续由 `ConfigurationManagementAccessPolicy` 执行。专用接口可以复用原通用配置接口，或将组件操作转换为同一管理变更命令。

组件按原菜单、UI 模型和 Controller 机制注册声明指向的专用入口。本机制不自动生成页面或接口，也不新增配置存储、接管注册表或另一套权限判断。接入应用已使用本声明，原临时 `managementEditorStandalone` 标记和工作台组保存的专用过滤逻辑移除。

#### 4.1.3 受限组件命令与事务依赖

DCR 需要在没有平台管理身份时，按配置所属组件认可的注册资格创建客户端。原 `ConfigurationManagementAccessPolicy` 已补充只读写入依赖，并使写入准备返回候选值与审计人；管理命令仍保留原平台身份与 RBAC。核心不增加 DCR 权限类型、策略注册表或专用存储路径，也不把 `purpose` 或调用参数当作授权证明。

包含跨键依赖的原配置事务对目标与依赖键按固定顺序，复用既有 `DataModel.queryForUpdate` 读取并锁定数据库快照，再调用同一策略、值校验及 CAS；普通无依赖操作保留原 CAS，不增加锁定查询。依赖不被写入、不增加 revision；缺行不能假定已锁定。DCR 注册策略无行时默认关闭，启用必须先经正常管理写入建立策略行。完整接口、缺行、并发与失败契约的唯一正文见[安全读写契约 §11](../ouroboros-oidc-server/configuration-management-design.md#11-组件命令的受限写入与依赖快照dcr-扩展)。组件命令由所属策略独立核验资格；普通管理写入仍要求平台管理身份。

#### 4.1.4 撤销数据库覆盖的恢复规则

配置元数据可声明 `restoreDefaultRemovesOverride=true`。原 `RestoreDefaults` 命令在此项下按 expectedRevision 条件删除原值表覆盖，支持空部署默认；随后取值继续跟随部署默认。通知渠道使用此规则。未声明该选项时仍执行原恢复规则，受保护配置先经过所属组件的管理策略，不能绕过身份、权限、可修改性或版本检查。

删除与重新创建不能复用旧行的并发 token。原值模型增加可空 generation，新插入生成 UUID，并与递增 revision 组成不透明 token；旧行保留原 token，更新/删除同时匹配 generation，历史空值不被误当成新行。客户端只应原样传回 token。

工作台凭据留空表示不提交，填写表示替换；显式勾选恢复才生成 `RESTORE_DEFAULT`，并与组内其他修改使用原批量事务。管理读继续由组件策略脱敏。多实例可见性沿原 Spring CacheManager 和提交后驱逐机制，共享缓存部署与本地缓存的边界见[通知管理指南](../ouroboros-notification/docs/runtime-management-guide.md)。

### 4.2 开发平台集成

`ouroboros-configuration-dev` 把配置体系接入开发管理界面，使维护者能够在平台中查看、编辑和治理配置项。这里的关键不是手写页面，而是依赖配置元数据驱动生成和管理。

### 4.3 逻辑流接入

`ouroboros-configuration-logicflow-node` 把配置能力带入逻辑流执行环境，使配置值能够成为流程编排时的输入和运行时依赖。

## 5. 扩展机制

该模块的核心扩展点包括：

- `ConfigurationMetadataProvider`，包括通过 `managementEntry` 声明专用管理接管
- `ConfigurationManagementAccessPolicy`，控制受保护配置的管理读写
- 配置值转换器
- 开发态编辑器 schema
- 逻辑流节点接入

这些扩展都建立在共享 SPI、自动配置和元数据驱动模型之上。维护时应避免把“运行时覆盖逻辑”“业务默认值”“环境差异”混成一套无边界机制。

## 6. 维护重点

维护该模块时，应优先关注：

- 保持 `core / runtime / dev / logicflow-node` 分工清晰。
- 配置权限、作用域和可见性应尽量在元数据层声明，而不是到运行时再临时判断。
- 新配置来源和新编辑器能力应优先复用元数据提供者机制接入。
- 不要让开发平台页面反向主导配置核心模型。

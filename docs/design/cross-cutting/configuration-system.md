# Ouroboros 配置体系

## 目标

本页统一说明 Ouroboros 如何定义配置、在运行时读取和更新配置、以及如何把配置能力暴露给开发管理侧。它关注的是配置系统的共享规则，而不是某个模块自己的业务参数含义。

## 配置元数据

配置体系的基础是 `ConfigurationMetadata`：

- 它定义配置键、标签、描述、值类型、作用域、默认值和编辑器 schema 等元信息。
- `ConfigValueType` 提供基础类型与复杂类型的转换边界。
- `ConfigurationMetadataProvider` 负责声明配置来源，让配置项先有可管理的元数据，再谈运行时值。

因此，配置系统不是“只有 key-value 存储”，而是“元数据先行，运行时值随后”的模型。

## 运行时配置

运行时配置由 `ouroboros-configuration-runtime` 承接：

- `RuntimeConfigurationService` 负责读取、更新和清理缓存。
- `ConfigurationMetadataCenter` 负责统一汇总配置元数据。
- 运行时控制器负责把配置读取和更新能力暴露为平台接口。

运行时层回答的是“当前环境下实际生效的配置是什么”，而不是“配置项应该长什么样”。后者仍然属于元数据层。

## 开发态配置管理

开发态配置管理由 `ouroboros-configuration-dev` 负责：

- 它把配置元数据和运行时配置能力带入开发平台，让维护者可以在开发管理界面中查看、编辑和治理配置。
- 配置管理界面的可生成性来自元数据驱动，而不是手写每一个配置表单。
- `ouroboros-configuration-logicflow-node` 则把配置能力延伸到逻辑流场景，使配置值可以参与流程编排和运行。

因此，`dev` 与 `logicflow-node` 都是配置体系的消费侧和扩展侧，而不是新的配置真相来源。

## 扩展与覆盖边界

配置体系的扩展和覆盖需要保持清晰边界：

- 扩展入口主要是 `ConfigurationMetadataProvider`，允许从文件、JAR 或其他来源补充配置元数据。
- 覆盖边界主要体现在默认值、运行时值、环境配置和平台管理操作之间的优先级，而不是任意模块都能直接改写配置真相。
- `scope`、前后端可见性、是否可修改等元数据，应在配置定义阶段确定，避免把配置权限问题拖到运行时临时判断。

维护者应避免把业务默认值、部署环境差异和动态运营开关混成同一层语义；配置系统负责统一承接这些输入，但并不消除它们之间的层次区别。

## 深入阅读入口

- [configuration-definition-conventions.md](configuration-definition-conventions.md)
- [../modules/ability/ouroboros-configuration.md](../modules/ability/ouroboros-configuration.md)
- [../../../ability/ouroboros-configuration/DESIGN.md](../../../ability/ouroboros-configuration/DESIGN.md)
- [../layers/ability-layer.md](../layers/ability-layer.md)
- 如需理解共享扩展入口，再看 [extension-mechanisms.md](extension-mechanisms.md)

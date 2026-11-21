# 开发模块开发指南

本页面向扩展开发平台管理能力的维护者。开发模块提供模型编辑、管理页面、开发菜单和元数据操作；业务应用开发见[应用开发指南](../docs/guides/app-development-guide.md)。

## 元数据入口

当前模块优先在 `src/main/metadata/` 放置带类型后缀的资源，例如 `*.dev-menu.json`、`*.ui-model.json`、`*.ui-schema.json`。构建后进入 `META-INF/ouroboros/metadata/`，由既有提供者发现；`dev-menu` 与最终用户的 `menu` 分开。旧 `dev-menu-model/`、`ui-schema/` 等目录只作为明确的兼容来源，不能当成新模块默认布局。

菜单分组、页面路径、开发管理 SPI 和自动配置沿用正式机制。具体步骤与示例统一维护在[开发模块模式指南](../docs/guides/development-module-guide.md)，资源命名和兼容顺序见[元数据资源约定](../docs/design/cross-cutting/meta-inf-resource-conventions.md)。

## 按任务阅读

- 创建开发模块与选择脚手架：[完整指南](../docs/guides/development-module-guide.md)。
- 查已有模式与最小实现：[快速参考](docs/quick-reference.md)。
- 理解模型管理注解与 SPI：[模型管理脚手架](ouroboros-dev-model-manage-webapi-scaffold/DESIGN.md)。

本页只维护入口，不复制完整指南的目录、注册清单和示例。

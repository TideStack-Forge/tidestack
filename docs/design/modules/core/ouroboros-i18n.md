# ouroboros-i18n 模块设计摘要

## 模块定位

`ouroboros-i18n` 提供平台的多语言能力：从各来源提取可翻译文案、维护语言环境（locale）字典与词条、在运行时按当前语言解析文案，并把统一错误码翻译成本地化错误消息。它是界面文案与接口错误信息两侧共用的语言环境底座。

## 责任边界

该模块负责文案提取、语言环境与词条管理、运行时文案解析和错误消息本地化，不负责具体业务的文案内容（由各模块在自己的资源与元数据中提供），也不替代统一错误码体系本身（错误码定义在 `core/ouroboros-error-code`，本模块只做其文案与本地化）。

## 依赖关系

由 `ouroboros-i18n-core`（契约与模型）、`ouroboros-i18n-runtime`（提取、语言环境服务与错误消息本地化）与 `ouroboros-i18n-dev`（开发态语言环境管理入口）三个子模块组成；运行时消费统一错误码体系，并被需要本地化文案的上层模块（如 `ability/ouroboros-app-runtime`）使用。

## 深入文档索引

- `推荐阅读`：[core/ouroboros-i18n/DESIGN.md](../../../../core/ouroboros-i18n/DESIGN.md)（含错误信息国际化的已落地实现与 SPI 清单）
- `前后端共同约定`：[语言接口与词条结构](../../../../core/ouroboros-i18n/docs/frontend-integration.md)；[Web 主应用集成设计](../../../../frontends/web/main-app/docs/i18n-design.md)（加载、缓存、React/AMIS 消费）
- `补充深度资料`：[core/ouroboros-error-code 模块摘要](ouroboros-error-code.md)（错误码体系与本模块的桥接点）

## 演进状态

三个子模块均已落地；错误码文案通过 `ErrorCodeTextExtractor` 提取，语言来源包括 Jar、文件系统与数据库，`I18nLocaleProvider` 使用 `getDictionary` 查询。具体合并与缓存机制统一在模块 DESIGN.md 维护，自动发现清单以各自 `META-INF/services` 资源为准。

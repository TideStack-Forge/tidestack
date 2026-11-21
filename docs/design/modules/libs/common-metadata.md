# common-metadata 模块设计摘要

## 模块定位

`common-metadata` 是 `common-*` 中的元数据加载底座模块，负责把“从 classpath 或文件系统读取某一类元数据文件、解析成模型、按 key 合并、回报来源与诊断”这套流程抽象成可复用能力。它是平台内 `Jar*Provider` 的事实公共入口，但不承载任何具体元数据类型的业务语义。

## 责任边界

该模块负责元数据来源抽象、类型化加载规格、加载编排、合并策略与加载诊断，不负责具体元数据模型的字段校验，也不负责 Spring 装配：它不注册任何 `@Configuration`，不提供 `META-INF` 条目，是一个纯 Java 库，装配职责留给使用方。

## 依赖关系

模块的 `pom.xml` 未声明任何自身依赖，通过父 pom `libs/common` 继承 `ouroboros-util`，并在实现中使用其 `Json`、`ResourceUtil` 以及 `io.vavr.control.Try`。它是被依赖方而非依赖方：主干中 `libs/common/common-app`、`core/ouroboros-i18n-runtime`、`core/ouroboros-error-code-runtime`、`core/ouroboros-field-type-core`、`core/ouroboros-field-type-dev`、`core/ouroboros-ui-model-runtime`、`core/ouroboros-ui-model-schema`、`core/ouroboros-ui-model-microweb`、`core/ouroboros-menu-model-runtime`、`core/ouroboros-logicflow-runtime`、`core/ouroboros-webapi-runtime`、`core/ouroboros-app-module-model-procode`、`core/ouroboros-app-module-model-runtime`、`core/ouroboros-approval-workflow-runtime`、`core/ouroboros-business-model-runtime`、`core/ouroboros-data-initialization-runtime`、`ability/ouroboros-app-runtime`、`ability/ouroboros-configuration-runtime`、`ability/ouroboros-data-dict-runtime`、`ability/ouroboros-message-queue-runtime`、`ability/ouroboros-notification-runtime`、`ability/ouroboros-organization-structure-runtime`、`ability/ouroboros-rpc-model-runtime`、`ability/ouroboros-scheduler-runtime`、`ability/ouroboros-business-log-runtime`、`ability/ouroboros-widget-runtime`、`dev-module/ouroboros-dev-app-management` 与 `security/ouroboros-web-security-rbac-manage` 都声明了它。

## 关键抽象

- `MetadataSource`：元数据来源契约，按规格返回 `MetadataResource` 列表。
- `TypedMetadataSpec<T>`：类型化加载规格，描述 typed 后缀、来源计划、解析器链、key 提取器与各类策略。
- `TypedMetadataCatalog`：加载编排入口，逐资源解析、按 key 合并并累积追踪与诊断。
- `MetadataLoadResult<T>`：加载结果，包含不可变 `values`、来源追踪与诊断，并提供 `throwIfErrors()`。
- `MetadataSourceTrace` 与 `MetadataDiagnostic`：来源级追踪（接受、覆盖、跳过）与告警、错误诊断。

## 关键子模块

- `ClasspathMetadataSource`：从 `META-INF/ouroboros/metadata/` 按后缀发现元数据。
- `FileSystemMetadataSource`：从工作区目录按后缀与文件模式发现元数据。
- `ClasspathMetadataFiles`：为 `META-INF/ouroboros/` 的旧布局与新布局提供覆盖读取顺序。
- `TypedMetadataSpec.SourcePlan`：`TYPED_SUFFIX_ONLY` 与 `LEGACY_DIRECTORY_THEN_TYPED_SUFFIX` 两种来源计划。

## 扩展机制

接入新元数据类型的方式是构造一个 `TypedMetadataSpec<T>`：声明 typed 后缀、解析器、`keyExtractor`，并按需选择 `MergeStrategy`（`LAST_WINS`、`FIRST_WINS`、`FAIL_ON_DUPLICATE`）与 `ParseFailurePolicy`、`BlankKeyPolicy`（`WARN_AND_SKIP`、`ERROR`）。需要新来源（例如远程元数据）时应实现 `MetadataSource` 并在调用侧注入，而不是在 `TypedMetadataCatalog` 内增加来源分支。

## 深入文档索引

- `暂无独立深度设计文档`：模块内没有 `DESIGN.md`、`README.md` 或 `GUIDE.md`，`libs/common/GUIDE.md` 的模块总览表也未收录本模块。
- `补充深度资料`：[common-dev 模块设计摘要](common-dev.md) 覆盖相邻的开发态元数据契约。
- `补充深度资料`：[common-modules 组件族边界（跨模块专题）](../../cross-cutting/common-modules.md) 覆盖组件族边界与清单。

## 演进状态

模块已经收敛为职责单一的库，接口面（来源、规格、目录、结果）稳定，且被跨 core、ability、libs 三层的 `Jar*Provider` 广泛复用，是当前元数据读取事实上的统一入口。缺口主要在文档而非代码：它承担公共加载契约，却既没有模块内文档，也未进入 `libs/common/GUIDE.md`。`SourcePlan` 的双模式与 `ClasspathMetadataFiles` 的 legacy 兼容路径是旧新元数据布局过渡的显式设计，后续维护不应在此基础上继续叠加第三套来源分支。

# Ouroboros 模板、表达式与脚本运行时

## 目标

本页用于统一解释 Ouroboros 中 FreeMarker 模板、SpEL 表达式和 JS/Groovy 脚本分别承担什么职责，以及它们如何共享上下文扩展能力。它关注的是“运行时解释层的边界”，而不是某个模块自己的模板文件清单。

## 三类机制的职责分工

Ouroboros 当前同时使用三类解释机制，但它们并不等价：

- FreeMarker 模板负责“生成结构化内容”，常见结果是 UI Schema、编辑器片段、逻辑流配置或其他 JSON 资源。
- SpEL 表达式负责“在既定结构中做轻量求值”，适合条件、映射、字段取值和简单计算。
- JS/Groovy 脚本负责“执行更开放的运行时逻辑”，适合表达式无法覆盖的流程片段、动态计算和脚本化扩展。

维护者最容易踩的坑，是把三者混成同一种“都能动态执行”的工具。实际上它们解决的是不同层级的问题。

## FreeMarker：生成结构，不直接承载运行时业务

`common-template` 通过 `JarTemplateEngine` 从 JAR 资源路径读取 `.ftl` 或 `.json.ftl` 模板，主要用于生成结构化输出。当前典型场景包括：

- `META-INF/ouroboros/...` 下的 UI Schema 与开发片段模板。
- 业务模型默认实现中大量编辑器 schema / API 片段模板。
- 开发管理侧按元数据生成页面片段或配置草稿。

因此，模板层最适合做“结构装配”和“重复片段复用”。如果某段逻辑已经需要复杂状态判断、跨服务访问或多步运行时副作用，就不应继续堆在模板里。

## SpEL：轻量运行时求值

`common-expression` 通过 `EL` 提供 `${ ... }` 风格的表达式求值，适合放在配置字段、映射关系和动态占位中。它的优势是：

- 表达式短、嵌入性强。
- 结果类型可以按调用方需要转换。
- 可以通过 `ExpressionContextContributor` 挂入全局工具类和平台上下文。

它更像“运行时参数化”，而不是通用脚本平台。若一段规则已经需要多步流程、复杂集合变换或难以读懂的嵌套表达式，就应该考虑上移到脚本或下沉回 Java。

## JS / Groovy：开放脚本能力

`common-script` 通过 `Scripts` 统一承接 JavaScript 与 Groovy：

- JavaScript 走 GraalVM，适合表达式延展、数据转换和重复执行场景。
- Groovy 更适合靠近 Java 生态的复杂脚本逻辑。
- `ScriptContextContributor` 允许平台把全局对象、工具类和领域上下文注入脚本环境。

这意味着脚本层拥有最高的灵活度，但也带来最高的可维护风险。只有在模板与表达式都无法清晰表达需求时，才应引入脚本。

## Groovy 运行时依赖

默认运行时通过 `common-script` 引入 `org.apache.groovy:groovy`，版本由 BOM 统一管理。当前 BOM 声明为 4.0.30，具体值由构建输入维护。

默认运行时排除 `org.codehaus.groovy:*` 与 `groovy-all`。`legacy-compat/quickbuilder` 保留独立兼容边界。静态依赖约束由[依赖策略](../../../scripts/ci/dependency-policy.yaml)和验证器维护，目标构建的运行时依赖树需按实际构建单独核对。

## 共享上下文扩展

表达式与脚本两条链路都支持“上下文贡献者”扩展：

- `ExpressionContextContributor` 为 SpEL 注入全局对象。
- `ScriptContextContributor` 为 JS/Groovy 注入全局对象。

仓库里有不少实现会同时实现两者，让同一份平台上下文在表达式和脚本中保持一致，例如：

- `ConfigurationContextContributor.Proxy` 通过 `Config` 暴露配置读取能力。
- 组织、编码主键、通知等能力也会把领域上下文同时挂到 EL 与脚本侧。
- `common-script` 自带 `logger`、`console`、`Dates`、`Converter`、`Maps` 等基础上下文。

这套机制的价值，在于让维护者可以先用表达式解决简单问题，必要时再升级到脚本，而不用重新学习一套完全不同的上下文词汇。

## 选型顺序

当前平台建议遵循以下选型顺序：

1. 先问是不是“生成结构”问题。若是，优先用 FreeMarker 模板。
2. 若结构已确定，只差简单取值、条件或映射，优先用 SpEL。
3. 只有当逻辑已经超出表达式可读边界时，才使用 JS/Groovy 脚本。

换句话说，不要一开始就跳到脚本。脚本能力是兜底扩展，而不是默认首选。

## 性能与维护约束

使用这三类机制时，应特别注意：

- 模板生成失败时要有清晰降级路径，不要让调用方把 `null` 当成正常结果继续传递。
- 频繁重复执行的 JavaScript 可考虑编译后复用；Groovy 当前编译优化能力较弱，不应假设它天然适合高频热路径。
- 表达式和脚本上下文应保持稳定、最小暴露，避免把大量 Spring Bean 或底层设施直接泄漏进去。
- 若某段模板、表达式或脚本已经很难在评审中解释清楚，它通常就该回到更明确的 Java 抽象里。

## 与模块设计的关系

模块摘要页只需要说明自己主要依赖了模板、表达式还是脚本机制，以及模板资源大致位于哪里；真正的共享边界，例如上下文注入、语言选型、性能约束和可维护性判断，应统一回收到本页。

## 深入阅读入口

- [后端脚本与表达式代码提示设计](script-expression-intelligence.md)：待评审提案，覆盖后端代码的上下文描述、编辑器接入与形状分析，尚未实现。
- [extension-mechanisms.md](extension-mechanisms.md)
- [../../../libs/common/common-template/GUIDE.md](../../../libs/common/common-template/GUIDE.md)
- [../../../libs/common/common-expression/GUIDE.md](../../../libs/common/common-expression/GUIDE.md)
- [../../../libs/common/common-script/GUIDE.md](../../../libs/common/common-script/GUIDE.md)
- [../modules/core/ouroboros-business-model.md](../modules/core/ouroboros-business-model.md)
- [../modules/core/ouroboros-ui-model.md](../modules/core/ouroboros-ui-model.md)

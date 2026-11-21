# common-expression 模块设计摘要

## 模块定位

`common-expression` 是 `common-*` 中的表达式求值模块，负责为平台提供轻量、类型安全且可扩展的表达式计算能力。它更偏向“内嵌表达式与上下文求值”而不是完整脚本运行，是很多动态配置、规则条件和模板变量解析的基础组件。

## 责任边界

该模块负责表达式求值、模板表达式包装、全局上下文注入和默认表达式实现注册；它不负责脚本语言运行、不负责模板文件加载，也不负责复杂业务规则编排。它的边界是“表达式级别的解释执行”，而不是“通用程序执行”。

## 依赖关系

当前默认实现 `SpElImpl` 基于 Spring SpEL，结合 `SPI` 装配 `ExpressionContextContributor`，并通过 `ContextWrappingHelper` 合并全局上下文与调用方变量。该模块既被模板、规则和配置场景直接使用，也为一些上层动态能力提供比脚本更轻量的选择。

## 关键抽象

- `EL`：表达式求值的静态入口，统一暴露 `eval`、`wrapExpression`、值计划与受限条件计划；使用边界见模块 GUIDE 的“编译计划与受限条件”。
- `SpElImpl`：当前默认表达式引擎实现，通过 `META-INF/services/com.ouroboros.expression.EL` 注册。
- `ExpressionContextContributor`：向表达式上下文注入工具类与全局变量的 SPI 接口。
- `ContextWrappingHelper`：只读上下文合并器，用于把局部变量与全局 bindings 融合成最终求值上下文。
- `UtilsContextContributor`：内置工具贡献者，提供常用辅助能力。

## 关键子模块

- `EL`：平台统一入口和静态 facade。
- `impl/SpElImpl`：SpEL 执行实现。
- `contributor/`：默认上下文贡献者实现。
- `ContextWrappingHelper`：上下文组合基础设施。

## 扩展机制

该模块通过两层 SPI 承接扩展：`EL` 允许替换默认求值实现，`ExpressionContextContributor` 允许补充全局上下文工具。维护时如果需要新增表达式能力，优先新增 contributor 或新的 `EL` 实现，而不是在 `SpElImpl` 中堆叠越来越多的特殊分支。相关治理可参考 [../../cross-cutting/spi-governance.md](../../cross-cutting/spi-governance.md) 与 [../../cross-cutting/template-and-script-runtime.md](../../cross-cutting/template-and-script-runtime.md)。

## 深入文档索引

- `推荐阅读`：[libs/common/common-expression/GUIDE.md](../../../../libs/common/common-expression/GUIDE.md)
- `补充深度资料`：[模板与脚本运行时](../../cross-cutting/template-and-script-runtime.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `common-expression` 自身的 `DESIGN.md` 或专题设计页。

## 演进状态

`common-expression` 当前边界清晰，适合继续维持为“轻量表达式层”。后续最需要避免的是把脚本级能力、业务 DSL 或模板治理逻辑继续压进表达式模块，导致它失去简洁和可预测性。

# common-script 模块设计摘要

## 模块定位

`common-script` 是 `common-*` 中的脚本执行模块，负责为平台提供统一的脚本运行与预编译入口。它让上层模块可以在不直接绑定某个脚本引擎实现的情况下执行 JavaScript、Groovy 以及后续可扩展的脚本语言。

## 责任边界

该模块负责脚本执行、脚本编译、绑定上下文注入和语言实现注册；它不负责脚本内容治理、不负责表达式模板语法、不负责权限沙箱策略，也不负责业务模块如何决定“什么时候允许运行脚本”。它解决的是脚本运行时统一入口问题，而不是脚本产品策略问题。

## 依赖关系

模块内置 `JavaScriptEngine` 和 `GroovyEngine` 两套实现，基于 `SPI` 汇总更多 `ScriptRunner` / `ScriptCompiler` / `ScriptContextContributor`。向上它通常被配置、规则、扩展节点或开发态动态逻辑场景消费，并和 `common-expression`、`common-template` 共同构成平台的动态解释执行能力。

## 关键抽象

- `Scripts`：统一静态入口，负责选择语言实现并暴露 `execute` / `compile`。
- `ScriptRunner`：脚本执行接口，定义某语言如何直接运行脚本。
- `ScriptCompiler`：脚本编译接口，定义某语言如何生成可复用执行函数。
- `ScriptContextContributor`：向脚本上下文注入全局对象和工具类的扩展接口。
- `JavaScriptEngine`：当前默认 JS 实现，负责 GraalVM 上下文、HostAccess 和结果映射。
- `GroovyEngine`：当前默认 Groovy 实现，承担 Groovy 脚本运行。

## 关键子模块

- `Scripts`：统一分发入口。
- `JavaScriptEngine`：更复杂的脚本桥接实现，包含 Java/JS 对象映射规则。
- `GroovyEngine`：相对轻量的 Groovy 运行实现。
- `contributor/`：内置上下文贡献者，如 `LoggerContextContributor` 和 `UtilsContextContributor`。

## 扩展机制

该模块的核心扩展面就是 `ScriptRunner`、`ScriptCompiler` 和 `ScriptContextContributor` 三类 SPI。维护时如果新增语言或全局工具，优先新增实现并通过 `META-INF/services` 注册，而不是继续把所有差异塞进现有引擎类。共享治理可参考 [../../cross-cutting/spi-governance.md](../../cross-cutting/spi-governance.md) 与 [../../cross-cutting/template-and-script-runtime.md](../../cross-cutting/template-and-script-runtime.md)。

## 深入文档索引

- `推荐阅读`：[libs/common/common-script/GUIDE.md](../../../../libs/common/common-script/GUIDE.md)
- `补充深度资料`：[模板与脚本运行时](../../cross-cutting/template-and-script-runtime.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `common-script` 自身的 `DESIGN.md` 或专题设计页。

## 演进状态

`common-script` 仍处于可扩展但需要克制的状态。它已经提供了统一执行入口和 SPI 扩展面，后续维护重点应放在语言接入边界、上下文注入边界和结果映射稳定性上，避免把脚本治理、安全和业务规则判断一并拉进模块内部。

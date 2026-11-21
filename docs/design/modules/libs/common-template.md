# common-template 模块设计摘要

## 模块定位

`common-template` 是 `common-*` 中的模板生成模块，负责基于 classpath/JAR 资源生成动态文本和结构化配置。它在平台中的典型作用是把 UI Schema、Logic Flow 配置或其他声明式资源从“静态文件”提升为“可带上下文渲染的模板资产”。

## 责任边界

该模块负责模板加载、变量包装、模板渲染以及将渲染结果反序列化为目标对象；它不负责模板文件治理流程、不负责脚本执行、不负责业务字段来源本身，也不负责模板产物的运行时语义。它做的是“根据输入上下文生成内容”，不是“决定内容代表什么业务规则”。

## 依赖关系

模块当前核心实现是 `JarTemplateEngine`，底层依赖 FreeMarker，并借用 `common-expression` 中的 `ContextWrappingHelper` 与 `ouroboros-util` 的 JSON 工具处理上下文和对象映射。向上它常被开发平台、UI 模型和配置生成场景复用，因此既是资源约定的一部分，也是动态生成链路的一部分。

## 关键抽象

- `JarTemplateEngine`：模板运行时主入口，从类路径资源读取模板并生成字符串或目标对象。
- `JarTemplateLoader`：`JarTemplateEngine` 内部的类路径模板加载器，负责把模板名映射到资源路径。
- 包装上下文 `wrapper`：为模板自动注入 `Jsons` / `Json` / `JsonUtils` 等工具能力。
- `generate(..., Class<T>)`：从模板直接生成强类型对象，是很多“生成 JSON 再反序列化”场景的核心入口。

## 关键子模块

- `com.ouroboros.template.JarTemplateEngine`：模块几乎所有对外语义都收敛在这一入口类里。
- 模板文件本身通常分布在各上层模块自己的 `META-INF/...` 资源目录中，`common-template` 只提供执行引擎，不持有模板资产。

## 扩展机制

这个模块的主要扩展面不是 SPI，而是模板资源目录和上下文输入结构。维护时若要扩展模板能力，优先保持 `JarTemplateEngine` 作为稳定入口，把变化控制在模板文件和上下文组织层。相关运行时约束可参考 [../../cross-cutting/template-and-script-runtime.md](../../cross-cutting/template-and-script-runtime.md) 与 [../../cross-cutting/meta-inf-resource-conventions.md](../../cross-cutting/meta-inf-resource-conventions.md)。

## 深入文档索引

- `推荐阅读`：[libs/common/common-template/GUIDE.md](../../../../libs/common/common-template/GUIDE.md)
- `补充深度资料`：[模板与脚本运行时](../../cross-cutting/template-and-script-runtime.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `common-template` 自身的 `DESIGN.md` 或专题设计页。

## 演进状态

`common-template` 当前保持“小而稳”的引擎定位，这是它的优势。后续演进应继续把它维持为通用模板运行时，而不是在这里叠加越来越多的模板治理或业务 DSL 语义。

# common-model 模块设计摘要

## 模块定位

`common-model` 是 `common-*` 中的模型基类模块，负责提供统一的模型构建、克隆、映射和扩展属性能力。它为平台里那些希望既保持较稳定结构、又需要轻量扩展字段的对象提供了共享建模方式。

## 责任边界

该模块负责模型对象的基础编程模型，例如 Builder 模式、克隆复制、`map()` 派生修改和 `extraProps` 动态字段承载；它不负责持久化映射、不负责 Typed Data Model 运行时、不负责业务验证规则，也不负责低代码模型生成流程本身。它解决的是“模型对象如何写得一致”，不是“模型如何落库或运行”。

## 依赖关系

模块基本保持轻量，只依赖 Jackson 和少量基础工具。它向上被 `common-app`、`dev-module` 以及一些核心模型对象直接复用，是很多“需要额外属性但不想引入复杂持久化框架”的对象层基础设施。

## 关键抽象

- `Model<M>`：顶层模型接口，定义 `clone()` 和 Builder 契约。
- `AbstractModel`：标准模型基类，提供 `clone()` 和基于 builder 的 `map()` 派生能力。
- `WithExtraProps`：扩展属性访问接口，约定动态字段读取方式。
- `AbstractExtraPropsModel`：支持 `extraProps` 的模型基类，适合承载额外 JSON 字段。
- `@LowCodeModel`、`@ProCodeModel`：模型来源标记注解，用于区分低代码与专业代码模型。

## 关键子模块

- `com.ouroboros.model`：Builder/clone/extraProps 的核心抽象。
- `com.ouroboros.model.annotation`：模型来源标记注解。

## 扩展机制

`common-model` 本身没有复杂 SPI，它的扩展方式主要是上层模块继承 `AbstractModel` 或 `AbstractExtraPropsModel` 来建立自己的对象体系。维护时应优先保持这些基类的简单和稳定，不要把持久化、校验或运行语义不断塞回模型基类层。

## 深入文档索引

- `推荐阅读`：[libs/common/common-model/GUIDE.md](../../../../libs/common/common-model/GUIDE.md)
- `补充深度资料`：[跨模块模型系统](../../cross-cutting/model-system.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `common-model` 自身的 `DESIGN.md` 或专题设计页。

## 演进状态

`common-model` 当前已经形成稳定基线，尤其适合作为共享对象编程风格的底座。后续最需要注意的是守住“对象模型基类”边界，不让它演变成混合了持久化、校验和运行时逻辑的大杂烩。

# common-exception-mapper 模块设计摘要

## 模块定位

`common-exception-mapper` 是 `common-*` 中承接第三方异常适配的模块，负责把底层或外部异常统一转换为 Ouroboros 异常体系。它把“异常语言定义”与“异常来源适配”分离开来，使平台可以在不污染基础异常层次的前提下吸收更多底层实现。

## 责任边界

该模块负责异常映射器接口、SPI 装载和默认回退；它不定义核心异常类型、不负责 Web 响应封装、不负责日志输出，也不负责业务模块里的局部错误恢复。它解决的是“如何把外部异常翻译成 OuroborosException”，不是“如何展示或处理这些异常”。

## 依赖关系

模块直接依赖 `common-exception`，并通过 `ouroboros-util` 的 SPI 能力发现所有 `OuroborosExceptionMapper` 实现。数据模型适配由 `ouroboros-data-platform-adapter` 的 `com.ouroboros.data.runtime.adapter.DataModelExceptionMapper` 提供，并在该模块注册服务；libs 不依赖 data。

## 关键抽象

- `OuroborosExceptionMapper`：异常映射接口，也是统一静态入口 `toOuroborosException(...)` 的所在位置。
- `DataModelExceptionMapper`：位于 data platform adapter，将数据异常转为平台异常。
- 默认回退映射：当没有匹配 mapper 时，最终退回 `InternalException` 或调用方提供的默认 mapper。

## 关键子模块

- `OuroborosExceptionMapper`：映射契约和静态工具入口。
- 数据来源的 mapper：由来源适配模块拥有，common 层不内置 data 实现。
- `META-INF/services/com.ouroboros.exception.mapper.OuroborosExceptionMapper`：实现所属适配模块的注册入口。

## 扩展机制

该模块的核心就是 `OuroborosExceptionMapper` SPI。维护者如果要接入新的底层系统异常，优先新增 mapper 并注册到 `META-INF/services`，利用 `supports()` 和优先级控制映射顺序，而不是在调用方到处手写 `catch + wrap`。共享治理可参考 [../../cross-cutting/spi-governance.md](../../cross-cutting/spi-governance.md)。

## 深入文档索引

- `推荐阅读`：[libs/common/common-exception-mapper/GUIDE.md](../../../../libs/common/common-exception-mapper/GUIDE.md)
- `使用入口`：[libs/common/common-exception-mapper/README.md](../../../../libs/common/common-exception-mapper/README.md)
- `补充深度资料`：[SPI 治理](../../cross-cutting/spi-governance.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `common-exception-mapper` 自身的 `DESIGN.md` 或专题设计页。

## 演进状态

`common-exception-mapper` 当前已经具备稳定骨架，但它也很容易随着底层依赖扩张而不断增加 mapper。后续维护应重点守住“来源适配层”边界，让映射器只做翻译，不把来源系统的业务语义直接泄漏进平台异常体系。

# common-exception 模块设计摘要

## 模块定位

`common-exception` 是 `common-*` 中的全局异常语义基线模块，负责定义 Ouroboros 统一异常层次、默认错误码分段和验证错误结构。它是平台错误处理的一致性起点，也是 `common-web`、`common-exception-mapper` 等模块能够协同工作的前提。

## 责任边界

该模块负责异常类型定义、错误码语义和验证错误结构表达；它不负责把第三方异常转换为平台异常、不负责 HTTP 响应封装、不负责日志策略，也不负责具体业务模块的错误处理流程。它定义的是异常语言本身，而不是异常流转链路。

## 依赖关系

`common-exception` 处在公共组件族较底部的位置，被 `common-exception-mapper`、`common-web`、`common-service` 和大量上层模块直接依赖。很多平台级错误处理协议，都是先建立在这里的异常类型，再由其他模块继续映射和包装。

## 关键抽象

- `OuroborosException`：平台异常抽象基类，统一持有 `errorCode`。
- `BusinessException`：业务规则层面的异常。
- `ValidationException`：验证失败异常，支持字段级 `errors` 信息。
- `InternalException`：平台内部错误异常。
- `PlatformException`：平台使用不当或开发期错误异常。

## 关键子模块

- `OuroborosException`：基础父类。
- `BusinessException` / `ValidationException`：面向客户端输入和业务规则的异常分支。
- `InternalException` / `PlatformException`：面向内部故障和平台误用的异常分支。

## 扩展机制

该模块本身不提供复杂 SPI，主要作为异常体系定义层被其他模块消费。若需要扩展第三方异常到 Ouroboros 异常的映射，应优先在 [common-exception-mapper 模块设计摘要](common-exception-mapper.md) 对应的映射层完成，而不是直接在这里新增大量面向来源系统的异常类型。

## 深入文档索引

- `推荐阅读`：[libs/common/common-exception/GUIDE.md](../../../../libs/common/common-exception/GUIDE.md)
- `补充深度资料`：[common-exception-mapper 模块设计摘要](common-exception-mapper.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `common-exception` 自身的 `DESIGN.md` 或专题设计页。

## 演进状态

`common-exception` 是平台最应该保持稳定的公共语义层之一。后续维护的关键不是增加更多异常类型，而是持续守住现有层次的含义，避免不同模块对同一类错误产生语义漂移。

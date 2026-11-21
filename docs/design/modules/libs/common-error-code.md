# common-error-code 模块设计摘要

## 模块定位

`common-error-code` 是 `common-*` 中的错误码引用契约模块，负责定义三件事：异常携带哪些错误码信息、异常在响应侧被解析成什么结构、以及谁负责把任意 `Throwable` 映射成该结构。它是错误处理链路最底层的词汇表，而不是错误码主数据的存放地。

## 责任边界

该模块只包含四个契约类，不定义异常层次、不提供 HTTP 响应封装、不做多语言翻译，也不定义错误码清单与注册表。异常类型归 `common-exception`，错误码主数据与注册表归 `core/ouroboros-error-code`，翻译与本地化归 `ouroboros-i18n`。

## 依赖关系

模块的 `pom.xml` 未额外声明依赖，四个类的 import 全部来自 JDK；有效 Maven 依赖仍包含父 `libs/common/pom.xml` 继承的 `ouroboros-util`、Jakarta 注解等。`common-exception` 在自己的 POM 中声明依赖本模块，`core/ouroboros-i18n-core`、`core/ouroboros-i18n-runtime` 与 `ability/ouroboros-app-runtime` 也声明了对它的依赖，`bom` 负责版本管理。

## 关键抽象

- `ErrorCodeReference`：异常侧错误码引用契约，暴露 `getCode()`、`getBizCode()` 与 `getFallbackMessageTemplate()`。
- `SimpleErrorCodeReference`：不可变默认实现，具备值语义的 `equals` 与 `hashCode`。
- `ResolvedErrorDescriptor`：解析后的错误描述载体，含 code、bizCode、回退模板、`messageArgs`、原始消息与结构化 `errors`，构造时做防御性拷贝。
- `ThrowableErrorDescriptorMapper`：`Throwable` 到 `ResolvedErrorDescriptor` 的映射扩展点，语义为 `supports` 加 `map`。

## 关键子模块

- `ErrorCodeReference`：被 `OuroborosException` 持有并经由 `getErrorCodeReference()` 暴露，由 `BusinessException`、`PlatformException`、`InternalException` 的构造函数接收的错误码契约。
- `ResolvedErrorDescriptor`：`ErrorMessageLocalizer` 与 `ErrorResponseResolver` 之间传递错误的统一形态。
- `ThrowableErrorDescriptorMapper`：由 `ability/ouroboros-app-runtime` 的 `DefaultThrowableErrorDescriptorMapper` 实现，是异常到错误描述映射的 SPI 入口。
- `SimpleErrorCodeReference`：面向非异常场景的轻量引用实现。

## 扩展机制

本模块不注册任何 `META-INF` 条目，也不提供自动配置，本身没有新增扩展点；扩展方式是实现 `ThrowableErrorDescriptorMapper` 并沿用既有 SPI 机制注册，由 `ErrorResponseResolver` 收集使用。它是典型的契约下沉模块：需要携带新的错误信息时应扩展 `ResolvedErrorDescriptor` 的 Builder，而不是让上层各自定义错误描述载体。

## 深入文档索引

- `暂无独立深度设计文档`：模块内没有 `DESIGN.md` 或 `GUIDE.md`，`libs/common/GUIDE.md` 的模块总览表也未收录本模块。
- `补充深度资料`：[common-exception 模块设计摘要](common-exception.md) 覆盖异常侧如何实现本模块契约。
- `补充深度资料`：[ouroboros-error-code 模块设计摘要](../core/ouroboros-error-code.md)，注意它与本模块是两个不同模块。

## 演进状态

模块收敛度很高：四个只使用 JDK 类型的契约类、无自动配置，语义边界清晰，是 `common-*` 中依赖位置最低的契约之一。需要注意的是它与 `core/ouroboros-error-code` 共用 `com.ouroboros.errorcode` 根包名，彼此之间却没有 Maven 依赖也没有代码引用，容易被误读为同一体系；实际分工是“错误描述的运行时契约在 `libs`，错误码主数据、定义来源与注册表在 `core`”，二者由 `ability/ouroboros-app-runtime` 的 `ErrorResponseResolver` 与 `core/ouroboros-i18n-runtime` 的 `ErrorCodeTextExtractor`、`DefaultErrorMessageLocalizer` 桥接。本模块缺乏独立文档，正是这一误读风险的直接来源。

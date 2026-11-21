# common-web 模块设计摘要

## 模块定位

`common-web` 是 `common-*` 组件族中面向 Web 接口层的公共模块，负责统一平台 REST API 的响应封装、分页/列表返回结构以及异常到接口响应的映射入口。它的核心价值是让上层控制器把“返回协议”收敛成一致约定，而不是每个模块自行拼装响应体。

## 责任边界

该模块负责 API 响应信封、分页与列表数据容器、下拉选项结构、请求解析辅助，以及 `WebApiResultHttpStatusAdvice` 对响应码到 HTTP 状态的映射；它不负责控制器业务逻辑、权限判定或前端页面语义。Advice 通过 Spring 组件发现接入，不是本模块的独立自动配置。

## 依赖关系

从实现看，`common-web` 直接建立在 `vavr`、Jackson 与 `ouroboros-util` / SPI 机制之上，并与 `common-exception` 体系形成天然协作。它被 `core`、`ability`、`dev-module` 和 `platform` 大量控制器直接消费，是最贴近控制器层但仍保持通用性的公共模块之一。

## 关键抽象

- `AbstractWebApiResult`：统一响应信封基类，约定 `code`、`msg`、`data`、`errors` 和 `extra` 结构。
- `WebApiResult`：通用单值/对象响应入口，支持从 `Try`、`Either`、`Optional` 构造结果。
- `PagedWebApiResult`、`ItemsWebApiResult`：分页列表和普通列表的标准响应外壳。
- `PagedResult`、`ItemsResult`：与响应外壳配套的数据容器。
- `SelectOption`、`TreeSelectOption`：面向前端选择器的通用结构。
- `WebApiResultExceptionMapper`：异常映射扩展接口，决定异常如何转成接口错误结构。

## 关键子模块

- `AbstractWebApiResult`、`WebApiResult`：核心响应协议。
- `Paged*` / `Items*`：列表型响应封装。
- `RequestUtil`：请求读取辅助工具。
- `DefaultWebApiResultExceptionMapper`：默认异常映射实现。
- `WebApiResultHttpStatus`、`WebApiResultHttpStatusAdvice`：HTTP 状态解析与响应拦截，保留显式 `@ResponseStatus` 和已设置的非默认 HTTP 状态。

## 扩展机制

该模块最关键的扩展点是 `WebApiResultExceptionMapper`，通过 `META-INF/services` 注入替代或补充默认异常映射策略。维护时如果要改变平台级错误响应表达，优先从异常映射器扩展入手，而不是让各控制器单独散落自定义分支。SPI 治理可参考 [../../cross-cutting/spi-governance.md](../../cross-cutting/spi-governance.md)。

## 深入文档索引

- `推荐阅读`：[libs/common/common-web/GUIDE.md](../../../../libs/common/common-web/GUIDE.md)
- `补充深度资料`：[跨模块安全与访问控制](../../../../security/DESIGN.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `common-web` 自身的 `DESIGN.md` 或专题设计页。

## 演进状态

`common-web` 的响应协议已经比较稳定，但它会持续受到异常体系和前端消费模式变化的牵引。后续维护最需要守住的是“统一响应契约”这条线，避免把模块级、产品级的特例处理重新分散回控制器。

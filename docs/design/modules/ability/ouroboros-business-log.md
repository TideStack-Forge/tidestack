# ouroboros-business-log 模块设计摘要

## 模块定位

`ouroboros-business-log` 是能力层中的业务操作审计模块，负责把 HTTP 侧的业务动作抽取成统一日志模型，并沉淀为平台级审计记录与管理入口。它解决的是“如何记录业务操作轨迹”的共性问题，而不是替代具体业务模块自己的事件建模。

## 责任边界

该模块负责业务日志模型、模板匹配、请求/响应抽取、敏感字段脱敏和日志持久化抽象，同时提供查看与配置所需的运行时管理入口。它不负责定义业务操作本身的领域语义，也不应演化成通用监控、链路追踪或安全审计平台。

## 依赖关系

模块由 `core`、`runtime`、`dev` 组成：`core` 定义日志、模板和存储接口，`runtime` 承接 Filter、提取器、模板提供者、持久化和运行侧管理；`dev` 沿 BaseDevelopService/VCS 管理 `.business-log.json` 源文件及开发侧模板页面。新日志规则应优先通过显式声明来源的模板 provider 扩展，而不是直接改 `BusinessLogFilter`。

## 深入文档索引

- `推荐阅读`：[ability/ouroboros-business-log/DESIGN.md](../../../../ability/ouroboros-business-log/DESIGN.md)
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 演进状态

当前结构以聚合 POM 和源码为准；`DESIGN.md` 维护模板来源、提取、存储与开发管理的职责边界。

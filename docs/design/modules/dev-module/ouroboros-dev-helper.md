# ouroboros-dev-helper 模块设计摘要

## 模块定位

`ouroboros-dev-helper` 是开发平台与运行时应用之间的开发辅助桥接模块，负责命令交互、元数据收集与同步、应用状态感知和流式通信能力。它是开发平台“看到运行时、驱动运行时”的关键支撑层。

## 责任边界

该模块负责开发平台与运行时应用之间的命令通道、元数据收集器和同步机制，不负责应用版本治理本身，也不负责应用管理产品逻辑。它提供的是开发辅助通信骨架，而不是单独的业务管理面。

## 依赖关系

从模块结构看，模块分为 `core`、`dev`、`runtime` 和 `dev-metadata-provider`，并依赖消息队列与能力层 runtime。这说明它既服务开发平台端，也服务运行时应用端，是天然横跨开发态和运行态的桥接模块。

## 深入文档索引

- `推荐阅读`：[dev-module/ouroboros-dev-helper/DESIGN.md](../../../../dev-module/ouroboros-dev-helper/DESIGN.md)
- `暂无独立深度设计文档`：当前仓库中尚无该模块的补充专题页。
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 演进状态

`ouroboros-dev-helper` 的设计已经把“开发平台端”和“运行时端”分开，这是它最值得守住的边界。后续维护应避免把更多平台业务逻辑塞进命令和元数据通道层。

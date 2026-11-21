# document-management 模块设计摘要

## 模块定位

`document-management` 是应用交付层中的业务应用模块，负责把文档库、目录、文档、权限和存储能力装配成完整的文档管理产品形态。它不是新的底层能力层，而是一个充分消费平台已有模型、安全和存储能力的业务应用。

## 责任边界

该模块负责文档库业务模型、节点层级、权限继承、文档内容操作和面向用户的应用接口，不负责重新定义底层数据模型、安全体系或存储基础设施。它可以通过 `StorageService` 等抽象接入不同存储实现，但不应把对象存储、文件系统或权限规则的底层语义重新内建一套。

## 依赖关系

从设计稿可以看出，`document-management` 显式依赖数据模型、UI Schema、权限系统与平台集成能力：文档与目录被统一抽象成 `LibraryNode`，权限使用平台安全语义，UI 与 API 则作为应用层交付面向外暴露。它是典型的“应用层装配模块”，而不是 `ability` 或 `core` 层的公共能力。

## 深入文档索引

- `推荐阅读`：[app-module/document-management/DESIGN.md](../../../../app-module/document-management/DESIGN.md)
- `推荐阅读`：[app-module/document-management/README.md](../../../../app-module/document-management/README.md)
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 维护边界

[模块架构](../../../../app-module/document-management/ARCHITECTURE.md)只维护组件与依赖总览；DESIGN.md 是节点、服务、存储及接口的共同正文。本文不依据设计篇幅推断成熟度或运行验收。

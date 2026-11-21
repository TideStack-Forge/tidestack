# ouroboros-dev-app-management 模块设计摘要

## 模块定位

`ouroboros-dev-app-management` 是开发平台中的应用管理核心模块，负责管理开发阶段应用的创建、分类、容器运行、调试支持和开发菜单等能力。它是维护者进入开发平台业务管理面的关键模块之一。

## 责任边界

该模块负责开发环境下的应用生命周期和管理界面能力，不负责底层版本控制实现、不负责平台最终统一运行时基座，也不负责把所有开发辅助能力统一吞并进一个模块。它消费容器、配置、版本控制、消息队列等基础能力，但不应取代这些模块的独立职责。

## 依赖关系

它显式依赖版本控制、容器、配置、菜单模型、WebSocket 和消息队列等模块，是典型的开发平台装配层：位于 `dev-module` 核心位置，向上承接开发平台产品流程，向下消费多项开发基础能力。

## 深入文档索引

- `推荐阅读`：[dev-module/ouroboros-dev-app-management/DESIGN.md](../../../../dev-module/ouroboros-dev-app-management/DESIGN.md)
- [应用开发看板](application-development-dashboard.md)说明能力关系、功能矩阵、主题配色与正式菜单导航。
- [模型实时协同](../../cross-cutting/model-collaboration.md)说明通用协同持久化、权限和 STOMP 广播。业务模型画布为首个资源类型。
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 演进状态

`ouroboros-dev-app-management` 已经显现出“开发平台业务中枢”的角色，后续需要重点控制职责膨胀。凡是可以下沉到版本控制、容器、helper 或配置能力的内容，都不应继续堆在该模块里。

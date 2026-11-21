# ouroboros-sdk 模块设计摘要

## 模块定位

`frontends/web/ouroboros-sdk` 是 Ouroboros 微前端集成 SDK，负责向独立前端模块暴露统一的平台能力、生命周期封装和类型契约。它是主应用与微前端之间最重要的前端共享边界之一。

## 责任边界

该模块负责微前端初始化接口、平台能力注入、公共类型导出和测试约束；它不负责主应用路由壳层、不负责独立微前端业务实现，也不负责后端协议本身。它提供的是“如何接入平台”的统一前端契约，而不是“平台前端如何运行”的全部实现。

## 依赖关系

从源码看，SDK 以 TypeScript 为核心，输出 `esm` 产物，通过 Rollup 构建，并被 `main-app` 和多个微前端子项目共同消费。它在逻辑上连接 `main-app` 的平台能力注入与独立微前端的生命周期实现，因此是前端架构中耦合最敏感的共享模块之一。

## 关键抽象

- `initMicroApp()`：标准微前端初始化入口。
- `initLayoutComponent()`：布局型微前端初始化入口。
- `initAmisComponentLib()`：Amis 组件库型微前端初始化入口。
- `main.ts`：平台能力桩对象、活绑定注入和生命周期实现的集中入口。
- `types.ts`：面向消费方的公共类型契约。
- `lifecycle.ts`：对外导出的生命周期桥接层。

## 关键子模块

- `src/main.ts`：主 API 出口和能力注入逻辑。
- `src/types.ts`：类型系统与接入参数定义。
- `src/lifecycle.ts`：适配 qiankun 生命周期调用。
- `tests/unit/`：SDK 契约、构建配置和运行时行为测试；组织与执行入口见模块 [tests/README.md](../../../../frontends/web/ouroboros-sdk/tests/README.md)。

## 扩展机制

这个模块的扩展主要体现为新增平台能力导出、扩展生命周期参数和维持向后兼容。维护时应优先把新能力稳定地收敛到 SDK 契约中，而不是让微前端直接依赖主应用内部实现。相关边界可参考 [../../cross-cutting/integration-boundaries.md](../../cross-cutting/integration-boundaries.md)。

## 深入文档索引

- `推荐阅读`：[frontends/web/ouroboros-sdk/README.md](../../../../frontends/web/ouroboros-sdk/README.md)
- `相关规范`：[frontends/web/TESTING.md](../../../../frontends/web/TESTING.md)
- `补充深度资料`：[前端基座菜单扩展机制设计（菜单徽标 + 用户菜单注册）](../../../../frontends/web/main-app/docs/menu-extension-design.md)

## 演进状态

`ouroboros-sdk` 仍处于高频演进模块，因为它承接了平台能力的前端共享契约。后续维护最需要守住的是兼容性和边界稳定性，避免 SDK 变成主应用内部细节的直接泄漏面。

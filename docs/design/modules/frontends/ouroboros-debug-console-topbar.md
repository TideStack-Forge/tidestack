# ouroboros-debug-console-topbar 模块设计摘要

## 模块定位

`frontends/web/ouroboros-debug-console-topbar` 是开发平台顶部调试工具栏微前端，负责在开发/调试态为前端主壳层提供容器控制、日志与辅助操作入口。它是布局型微前端，而不是业务页面型微前端。

## 责任边界

该模块负责顶栏调试工具的展示和交互、开发态用户导航与辅助按钮；它不负责主应用整体布局、不负责 SDK 定义、不负责后端调试能力本身，也不负责业务页面内容。它承担的是“开发态顶栏扩展件”的角色。

## 依赖关系

模块依赖 React、`ouroboros-sdk` 和主应用注入的布局型 props，通过 `initLayoutComponent()` 挂载到主壳层指定容器。它与开发平台运行态、容器调试工具和用户态信息都有直接协作关系。

## 关键抽象

- `initLayoutComponent()`：布局型微前端初始化入口。
- `TopBar`：顶栏核心渲染组件。
- `ContainerControlButtons`、`HelperControlButtons`、`Log`、`CloseAppButton`：开发态辅助控件。
- `beforeMount(props.__utils__)`：通过平台工具注入初始化调试侧工具能力。

## 关键子模块

- `src/main.js`：微前端生命周期入口与重新渲染逻辑。
- `src/TopBar.jsx`：顶栏主组件。
- `src/components/`：调试控件、日志和用户导航等子组件。
- `src/utils/`：平台工具初始化与图标注册。

## 扩展机制

该模块的扩展面主要在布局 props、辅助按钮集合和平台工具注入，而不是独立路由或页面体系。后续若增加新的调试按钮或状态展示，优先保持它们作为独立组件挂入顶栏，而不是让顶栏组件继续膨胀。

## 深入文档索引

- `推荐阅读`：[frontends/web/ouroboros-debug-console-topbar/package.json](../../../../frontends/web/ouroboros-debug-console-topbar/package.json)
- `暂无独立深度设计文档`：当前仓库中尚无该模块的 README 或 `DESIGN.md`，需要以源码结构为主理解。

## 演进状态

`ouroboros-debug-console-topbar` 当前边界比较清晰，但也明显是依赖平台开发态上下文的模块。后续维护应继续守住它作为“布局扩展件”的定位，不要把更多页面级逻辑堆进顶栏本体。

# LogicFlow 编辑器嵌入与生命周期

> [设计总览](../../DESIGN.md) · [作品化契约](../authoring-contracts/overview.md)

编辑器当前使用 React，画布使用 X6。宿主传入 `getProps()`、`rootData`、`onChange`，通过组件 ref 获取辅助桥接面；节点和图语义仍以 core 为准。

## 资源所有权

| 层 | 当前入口 | 职责 |
| --- | --- | --- |
| 编辑器 | [index.jsx](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/index.jsx) | 组合 state、画布和属性面板；连接诊断、选择及操作桥 |
| state | [useLogicFlowEditorState.js](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/state/useLogicFlowEditorState.js) | 初始化读取 store，订阅变化，获取 NodeDefine，归一只读态与属性面板上下文 |
| Canvas | [canvas/index.jsx](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/components/canvas/index.jsx) | 拥有 Graph、画布插件、图数据同步及事件转发 |
| controller | [logicFlowCanvasController.mjs](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/canvas/logicFlowCanvasController.mjs) | 拥有 DnD 与 currentGraph 引用，提供视口/拖拽操作 |
| PropertiesPanel | [propertiesPanel/index.jsx](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/components/propertiesPanel/index.jsx) | 拥有 AMIS scope、挂载容器与 resize dragging provider |

## 挂载与更新

state 的 `useEffect` 会立即执行一次 `syncState()`，随后订阅 store；因此已有初始模型也会被消费。Canvas 的 effect 根据 `data`、`nodeDefines` 和只读态运行，有有效数据且尚无 graph 时调用 `initGraph`，不依赖挂载后再额外触发一次 Vue watcher。

Graph 就绪后通过 `onGraphInited` 通知顶层初始化 DnD。后续模型更新由 `getFlowSyncAction` 选择 `noop`、只更新流程属性或更新图数据；节点定义类型签名变化时先更新定义再同步图。上下文变化是否重建资源，以各 effect 和同步合同为准。

诊断优先消费 `getProps().validationSnapshot`；没有独立快照时保留旧读面兼容。正式保存模型不应写入诊断字段，见[端口与数据契约](../ports-and-data-contracts/overview.md)。

在线调试通过 `getProps().env.logicFlowDebug` 接入宿主的接口、通知和目标控件，默认使用开发平台适配器。宿主上下文标识区分应用、克隆草稿和测试修订；更换时只释放、重建调试工作台，Canvas 继续拥有原 Graph。装配方法、属性接口覆盖和应用端演进边界见[编辑器宿主合同](../../../../frontends/web/ouroboros-logicflow-editor/docs/runtime-debug-host.md)。

## 清理

- state effect 的 cleanup 取消 store 订阅。
- Canvas cleanup 通过 `disposeCanvasRuntime` 取消 debounce、销毁 graph，并清空 graph/已渲染数据等引用；滚动观察与计时器按对应 effect 清理。
- 顶层 effect cleanup 调用 controller 的 `dispose()`，释放 DnD 与 currentGraph；运行时调试订阅也返回清理函数。
- 属性面板通过排队的 `destroyRuntime()` 串行释放 AMIS scope、拖拽 provider 和容器，卸载时取消滚动提示计时器并触发销毁；显式调用可以等待该异步清理完成。

资源所有权保持在编辑器内部，宿主持有句柄不等于接管它的生命周期。

## ref 桥接面

`forwardRef` / `useImperativeHandle` 暴露 `navigateToValidationNode`、`getGraph`、`getNodeDefines`、`showNodeProperties`、`showEdgeProperties`、`showLogicFlowProperties`、`readFlowData`、`getPropertiesPanelScope`、`getPropertiesPanelElement` 和异步 `disposePropertiesPanel`。

它们服务嵌入、测试与维护，不是跨产品线稳定 SDK 协议。panel bridge 保留异步 show 的返回值；harness 可在 unmount 前等待 `disposePropertiesPanel()`。

## 验证入口

生命周期、controller、属性面板与 orchestration 的测试位于编辑器 `tests/`，真实挂载 harness 位于 `tests/integration/logicflow/harness/`。统一命令、历史 runner 边界与跨栈夹具差距见[测试与验证体系](../testing-and-verification/overview.md)。测试资产存在不代表本轮已执行。

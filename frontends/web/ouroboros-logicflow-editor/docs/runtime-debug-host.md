# 编辑器宿主接入合同

逻辑流编辑器沿用 `logic-flow-editor` 表单项、`value` / `onChange`、`nodeDefineApi` 和 `env.fetcher`。克隆、保存、测试版本管理和发布属于宿主；编辑器消费流程定义，编辑和调试共享同一个 X6 Graph。

## 运行时管理宿主与加载

表单项 `logicFlowHost` 可显式声明运行环境能力。`debug: false` 隐藏调试并禁止回退开发平台；`lockContract: true` 锁定流程名称、启用及公开输入输出类型/Schema；`intelligenceApi: null` 关闭开发代码提示。后端仍须独立验证合同与权限。

`referenceApis` 按 `flow`、`data-model`、`configuration`、`rpc-model`、`notification-template`、`mq-binding` 提供字符串或 AMIS API 对象。显式运行时宿主缺少对应能力时返回空选项，不回退 dev 地址；MQ 目录同时传递 producer/consumer `bindingType`。目录只提供标识和标签，不提供配置值及凭据。

注册代理首次挂载时共享加载 Promise，每个表单保存自己的最新 props 与草稿；未就绪时 validate 阻止提交，失败最多人工重试三次。卸载后迟到加载不得创建 Graph。调试工作台和独立预览另行按需加载，进入调试时立即禁用画布编辑。

正式 `package:sync` 把带版本目录的 entry、JS/CSS 内容哈希和所有异步块放入唯一共享资源 JAR。部署须保留仍有打开页面使用的旧版本目录。IDE 设置 `OUROBOROS_EDITOR_PUBLIC_PATH=auto` 时沿用 app.js/app.css 固定入口，其他块相对当前脚本加载。组件 `style` 可设置容器高度；画布布局样式属于画布包，不能依赖调试样式加载。

## 调试宿主配置

通过表单项的运行环境 `env.logicFlowDebug` 注入调试宿主。省略时按当前 `appName` 使用开发平台适配器；设为 `false` 时隐藏调试入口，退出已打开的调试模式。显式提供宿主时，不要求开发平台 `appName`，也不再使用默认开发平台请求或订阅。

| 字段 | 合同 |
| --- | --- |
| `key` | 当前调试上下文的稳定字符串。应用、克隆草稿或测试修订改变时必须改变，例如 `orders:draft:42:revision:7`。只重置调试工作台，不重建画布 |
| `api` | 下表的异步操作，返回归一化结果。宿主处理 URL、HTTP 方法、认证和响应包裹；错误通过拒绝 Promise 表达 |
| `subscribe(captureId, notify)` | 可选。收到记录变化提示后调用 `notify()`，返回释放订阅的函数。可以使用宿主已有消息机制；省略时仍定期查询事实 |
| `TargetControl` | 可选 React 控件，接收 `targets`、`runtimeId`、`disabled`、`onChange(runtimeId)`。默认提供通用目标下拉框；宿主可显示固定草稿沙箱或自己的选择控件 |
| `recordingHint` / `triggerHint` | 可选，分别说明开始记录和触发执行的方法。开发平台适配器使用测试容器和测试应用文案 |

宿主对象和 `api` 在同一上下文内保持引用稳定；配置改变时传入新对象，不原地修改 `env`。`key` 必须区分可能出现相同流程名、运行编号的不同草稿和修订。替换上下文后旧工作台卸载，定时器和订阅释放，旧开始请求的迟到响应无法写入新工作台。退出调试不等于撤销运行端的租约；仍沿用服务端有界租约和排空规则。

| `api` 操作 | 参数 | 归一化结果 |
| --- | --- | --- |
| `targets()` | 无 | 目标数组，包含 `runtimeId`、`label`、`supported`，可带 `backend` |
| `captures(flowName)` | 流程名 | 当前上下文的历史记录数组 |
| `start(request)` | `{runtimeId, flowName, operationId}` | 记录，包含 `captureId`、`state`、`createdAt`、`runs`；相同 `operationId` 的重试须幂等 |
| `read(captureId)` | 记录 ID | 最新记录 |
| `renew(captureId)` | 记录 ID | 续租后的记录 |
| `stop(captureId)` | 记录 ID | 停止后的记录，已有执行仍可读 |
| `events(captureId, runId, afterSeq)` | 记录、根执行、游标 | `{items, afterSeq, hasMore, complete, availability}`；保留事件序号、缺口和未完整状态 |
| `graph(captureId, graphId)` | 记录、拓扑 ID | 采集时拓扑快照 |

事件、状态、拓扑及记录字段沿用[在线调试设计](../../../../core/ouroboros-logicflow/design/online-debugging/overview.md)，宿主负责把不同接口格式转换为该合同，不能把缺失事件伪造成成功。默认开发平台的实现位于 `devRuntimeDebugHost.jsx` 和 `runtimeDebugApi.mjs`；工作台、回放和通知补偿不依赖该实现。

应用端可按以下方式装配；`draftDebugApi`、`draftNotifications` 和 `DraftSandboxTarget` 由未来应用端提供，此示例不是已实现的应用端功能：

```jsx
const environment = {
  ...existingEnvironment,
  logicFlowDebug: {
    key: `orders:draft:${draftId}:revision:${testRevision}`,
    api: draftDebugApi,
    subscribe: (captureId, notify) => draftNotifications.subscribe(captureId, notify),
    TargetControl: DraftSandboxTarget,
    recordingHint: '记录当前克隆草稿的测试执行。',
    triggerHint: '触发草稿测试，执行会出现在这里。',
  },
}
```

## 节点与属性接口

节点定义由宿主的 `nodeDefineApi` 提供，保存沿用 `onChange`，不由调试工作台发起。流程、数据模型、RPC、配置、通知模板和 MQ 绑定选择器优先使用属性定义中的 `source`，支持字符串或完整 AMIS API 对象；未配置时保留开发平台默认地址。脚本属性可提供 `intelligenceApi` 和 `intelligenceScene`。嵌套 fieldSet/combo 仍经原有转译器递归处理，无须另注册一套全局转译器。

```json
{
  "type": "flow-select",
  "name": "flow",
  "source": {
    "method": "get",
    "url": "/application/drafts/${draftId}/flows",
    "responseData": {"items": "${options}"}
  }
}
```

不同宿主的编辑和展示差异通过既有属性 Schema、`readonly` / `static`、共享浮动面板以及调试 `TargetControl` 表达；保持画布和回放算法一致。

## 服务端责任和验证边界

应用端应提供自己的路由和开发管理员权限、草稿归属、测试修订及运行目标校验。前端宿主只能选择界面行为，不能授予调试权限。当前服务端采集编排通过 `LogicFlowDebugAccess`、`LogicFlowDebugRuntime`、`LogicFlowDebugNotifications` 和 `LogicFlowDebugEventStore` 与平台适配层隔离；TEST_APP 的归属和实时容器检查保留在 `dev-app-management` 实现中。

未来应用端应在自己的装配中接入这些端口和所需服务，不直接套用开发平台 Controller 或扫描整套开发平台配置。草稿须有独立测试身份及修订；不得仅按同名流程混用原应用或其他草稿的执行，也不通过放宽当前 TEST_APP 校验来调试生产业务。克隆、持久化、发布、应用端 Controller 和实际测试运行环境尚未实现，需在该功能落地时验收。

包内行为测试覆盖开发平台默认接入、另一套应用路径与固定草稿目标控件、相同画布上的路径和堆栈展示、无 `appName` 接入、草稿切换中的迟到响应隔离，以及禁用宿主后释放订阅和恢复编辑。应用路径由测试适配器实现，证明复用合同，不代表已有应用端服务。

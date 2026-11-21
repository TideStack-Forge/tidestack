# 用例

各例前提为依赖和正式资源预检通过。严格断言失败保留日志、浏览器 trace、截图；原生层失败保留测试报告。脚本退出码非零，缺依赖不得记为 pass。

| ID | 分支 | 前置条件与行为 | 通过标准 | 层 |
|---|---|---|---|---|
| IDE-OPEN-001 | normal/boundary | Page/Form 打开并等待属性面板初始化 | 原始字节不变、无保存、无新增属性 | 两种 HTML 浏览器、原生 smoke |
| IDE-SAVE-002 | state | 编辑标题，立即 flush，重开 | 只改标题，未知字段保留，重开可见 | 两种 HTML 浏览器 |
| IDE-CONFLICT-003 | dependency/recovery | 尚未广播的外部更新后保存旧修订 | 保留外部字段和本地编辑，不覆盖外部修改 | 两种 HTML 浏览器 |
| IDE-EXTERNAL-004 | state | 外部修改并推送正式状态 | UI 更新、无回写 | 两种 HTML 浏览器 |
| IDE-POPUP-005 | state/boundary | inline 和共享弹窗各编辑、保存、重开三轮 | definitions 不增长，合法引用和未使用定义保留 | 两种 HTML 浏览器 |
| IDE-SERVICE-006 | normal/permission/recovery | devKey 调用、并行会话、错误、动态目录刷新、关闭 | 请求抵达 HTTP 服务，正确认证/参数/隔离/清理；故障恢复可见 | 两种 HTML 桥接浏览器、现有客户端单元测试 |
| IDE-NATIVE-007 | state/error | 原生文档编辑、修订冲突、undo/redo、保存 | 实际文档/磁盘状态正确、dirty 正确 | VS Code Extension Host、IntelliJ Platform |
| IDE-SMOKE-008 | dependency | 载入正式资源并执行编辑器 | 收到实际浏览器就绪及保存结果；JCEF 必须受支持 | VS Code Webview、IntelliJ JCEF |
| IDE-FLOW-009 | normal/state | 从在线服务获取节点定义，编辑节点属性并保存重开 | 真实画布显示节点与连线；打开不回写；修改保存，连线与未知字段保留 | 两种 HTML 浏览器 |
| IDE-SCRIPT-010 | normal/error/recovery | 在逻辑流节点中使用真实 ScriptEditor，通过 HTTP/LSP 夹具返回提示与诊断 | 提示可插入；错误标记可见且修复后消失；请求携带完整草稿；保存重开与会话清理正确 | 两种 HTML 浏览器 |
| IDE-FLOW-011 | normal/state/offline | 在线返回完整生产节点目录，离线使用各宿主随包快照；搜索类型、拖入节点、编辑保存重开 | 全目录标签和已声明图标可解析；打开不回写；新增节点及属性保存；离线不请求服务 | 两种 HTML 浏览器 |
| IDE-FLOW-012 | boundary/permission | 在线目录真实为空或节点 API 返回 403 | 不替换为离线目录；鉴权失败可见；已有未知类型节点和文档字节不变 | 两种 HTML 浏览器 |
| IDE-FLOW-013 | normal/state | 真实目录全部图标默认白色、调用方修改 CSS color；画布配置 iconColor | 浏览器像素渲染中实心与描边均继承指定颜色，图标有可见像素 | 两种 HTML 浏览器 |
| IDE-SCRIPT-014 | normal/state/offline | 在线、离线分别展开业务模型五种 CRUD 的执行前/后脚本，输入、保存并重开；在线请求提示和完整草稿 | 十处均为可见 Monaco，Groovy/JavaScript 语言正确；修改互不覆盖；服务收到对应位置与完整草稿；JCEF 离线也能编辑并回传保存 | 两种 HTML 浏览器、真实 JCEF |

现有 AMIS 组件浏览器回归继续覆盖 ScriptEditor 未保存草稿、Page 功能开关、Form 用途编辑能力。语言服务使用可控协议夹具，验证真实 UI、宿主桥接与消息消费；不替代真实编译器验收。未覆盖：完整语言服务器消息乱序交互、所有元数据类型的 UI 操作、所有容器/远程拓扑；这些不属于本套已通过声明。

# Logicflow Editor Integration Tests

`frontends/web/ouroboros-logicflow-editor/tests/integration/` 负责真实挂载编辑器后的前端集成测试。

当前约束：

- 使用 `Vitest + jsdom`，不依赖浏览器拖拽手势。
- 通过 package-local `tests/integration/logicflow/harness/platform/` 注入真实 amis 渲染能力。
- 通过 package-local harness 调用 editor 暴露的窄测试桥，执行“打开属性面板 / 提交表单 / 读取 flowData / addNode / connect”这类动作。
- `X6` 在 `jsdom` 下缺少 `getCTM`、`createSVGMatrix` 一类 SVG 几何 API，相关 polyfill 由 `tests/integration/logicflow/harness/ensureSvgGeometryPolyfills.mjs` 在 package-local harness 内补齐，不放进共享平台层。

当前已覆盖的主路径：

- 节点属性通过真实 amis 表单回写到 `flowData.nodes`
- 流程级可编辑属性通过真实 amis 表单回写到 `flowData`
- 通过 harness 新增节点后，序列化节点结构保留当前端口契约
- 通过 harness 新增连线后，序列化连接结构保留 `sourceNode/sourceOutputPort/targetNode/targetInputPort`

注意：

- 当前流程属性面板里的 `name` 字段是只读展示，因此流程级 round-trip 用例覆盖的是 `description`，不是流程名编辑。

命令：

- `npm run test:integration`
- `npm run test:all`

# 执行 IDE 编辑器回归

在待验证 checkout 根目录运行。所有场景使用仓库内共享样本，不需要开发服务器、devKey 或个人项目。

```bash
node ide-plugins/tests/run.mjs --check
node ide-plugins/tests/run.mjs
```

需要操作录像时运行 `node ide-plugins/tests/run.mjs --video`，也可与 `--lane browser` 组合。Web 编辑器及两种宿主 HTML 的每条浏览器用例均保存 1280×720 WebM 视频，包含用例名称和操作提示。视频保留成功和失败用例，分别位于本轮 `web-browser-artifacts/`、`browser-artifacts/`，路径见 `web-browser.json`、`browser.json` 的用例附件。原生 Extension Host/JCEF 测试使用日志和报告取证，本选项仅录制 Playwright 页面。

默认入口构建当前源码、复制并校验 VS Code 正式资源，执行共享运行时单元测试、浏览器夹具类型检查、Web Form 单元测试与 Web 编辑器浏览器回归、IntelliJ Platform 测试和 HTML 导出、两个宿主的 AMIS/逻辑流/ScriptEditor 浏览器用例、VS Code 原生测试与 JCEF 冒烟。首次需要网络下载 VS Code 1.95.3（缓存到 checkout 的 `tmp/ide-metadata-fixture/vscode-cache/`） 与 Gradle 所需的 IDEA SDK/JCEF JBR。VS Code 的最低支持版本仍以插件 manifest 为准，固定的测试版本不代表完整兼容矩阵。

依赖：Node/npm、Python 3、Java、插件和 Web 工程 npm 依赖、Playwright Chromium、可用图形会话。安装 npm 依赖使用各工程既有命令；Chromium 在 `ide-plugins/common/metadata-editor-runtime` 下运行 `npx playwright install chromium`。`--check` 只验证静态前提，JCEF 是否真正可运行由原生测试判定；没有 JCEF 或图形环境会失败，不会降级后通过。

按修改范围选择较快的执行层：

```bash
node ide-plugins/tests/run.mjs --lane browser
node ide-plugins/tests/run.mjs --lane vscode
node ide-plugins/tests/run.mjs --lane intellij
node ide-plugins/tests/run.mjs --lane jcef
```

局部执行的报告明确列出未覆盖层；发布前运行默认全部入口。若要测试已安装的 VS Code，通过 `OB_VSCODE_EXECUTABLE=/absolute/path/to/executable` 指定实际可执行文件，不指向 CLI shell 脚本。

VS Code 每轮使用独立用户目录、扩展目录和工作区，不触碰个人配置。macOS Unix socket 路径长度有限，启动配置暂存系统短路径 `/tmp/ob-ide-*`，退出自动删除。浏览器磁盘文档放在 checkout 的 `tmp/ide-metadata-fixture/`，测试后自动删除；Kotlin 导出的模板可保留供复跑。两个宿主的初始文档、保存和在线服务只通过现有公开入口注入。

统一报告位于 `tmp/testing-evidence/ide-metadata-editors/<run>/summary.json`，记录提交、环境、命令、覆盖层和退出结果。浏览器失败保留截图、trace 与 JSON 报告；Gradle 测试报告与 VS Code 断言日志随同保存。每个阶段失败均非零退出，不自动重试掩盖不稳定。一次性证据不提交，CI 应归档整个运行目录。

定位业务模型脚本回归时，先按上述流程构建资源并导出 Kotlin 页面，再在 `ide-plugins/common/metadata-editor-runtime` 执行 `OB_IDE_RECORD_VIDEO=1 npm run test:integration -- tests/integration/hosts/code-editors.spec.ts`。此组通过完整业务模型页面覆盖五种 CRUD 的执行前后脚本、在线提示与离线编辑；`--lane jcef` 另行验证真实 JCEF 中的脚本挂载和保存。

调试单例时，先运行一次对应层保证正式资源和 Kotlin 模板就绪，再在 runtime 工程运行 `npx playwright test tests/integration/hosts --grep IDE-POPUP-005`。未改公共资源时不需要启动真实 IDE 重复每个编辑器操作；公共 HTML/运行时改动跑 browser 层，宿主 Document/lifecycle 改动跑对应 native 层，CSP、资源路径、JCEF 版本与 worker 改动再跑全部。

节点目录问题使用 `npx playwright test tests/integration/hosts/logicflow.spec.ts --grep 'IDE-FLOW-011|IDE-FLOW-012|IDE-FLOW-013'` 复跑。`IDE-FLOW-011` 分别验证两个正式宿主的在线与离线模式，在线返回构建产物的完整目录，离线从各自资源包直接读取；`IDE-FLOW-012` 验证真实空目录和 403 不会变成离线成功结果。`IDE-FLOW-013` 将正式 symbol 与引用者的计算样式交给浏览器 SVG 渲染器，检查默认白色、CSS color 切换和画布 iconColor 的实际像素，并附截图。流程样本只能使用正式目录中的类型，禁止为方便录屏维护另一份简化定义。

增加编辑回归时先更新 cases 与共享样本，再在模块内增加行为断言，沿用稳定 case ID。新 IDE 只实现测试的宿主端点适配并消费同一组场景，不复制一份 AMIS/逻辑流用例。新增后端拓扑优先扩展后端 transport 合同测试，避免把每个 IDE、编辑器和传输方式全部排列组合。

当前边界以 [cases.md](cases.md) 为准：浏览器 HTTP 服务是协议夹具，不证明真实后端、MQ、IPC、容器和所有语言服务交互都已验收。IntelliJ JCEF 用正式生成器、真实查询、真实 Document 与保存协调器验证加载/保存；完整 `MetadataFileEditor` 的所有窗口/焦点行为仍需在发行支持版本上补充验收。

# 项目生成联调资产

本目录保留项目生成器的 Playwright 场景驱动和本地配置样例。运行时显式绑定 checkout，每次使用独立证据目录。项目生成的当前机制见 [模块设计摘要](../docs/design/modules/dev-module/ouroboros-dev-project-generation.md)，标准测试入口见 [项目测试规范](../docs/rules/testing.md)。

## 资产与复用条件

| 文件 | 用途与当前限制 |
| --- | --- |
| [run-project-generation-ui.js](run-project-generation-ui.js) | 使用 Playwright 操作三类项目生成入口并校验 ZIP；`--repo` 必填，Playwright 从指定 checkout 内解析；`--output` 可指定一个尚不存在的证据目录 |
| [runtime/application-project-generation-local.yml](runtime/application-project-generation-local.yml) | 本地模板 catalog 联调配置；通过 `PROJECT_GENERATION_DEBUG_CONFIG` 显式指定 Spring 配置导入，通过 `PROJECT_GENERATION_REPO_ROOT` 指定模板、制品与缓存所在 checkout |

脚本要求 Node.js 22，以及指定 checkout 主应用已安装的 Playwright 与浏览器。`BACKEND_BASE_URL` 和 `FRONTEND_BASE_URL` 分别指定后端与前端地址，默认 `http://127.0.0.1:18880` 和 `http://127.0.0.1:18081`；这些地址必须对应待验收运行时。

未指定 `--output` 时，脚本选择 `<repo>/tmp/testing-evidence/project-generation/<时间戳-UUID>/`。已存在的输出路径会被拒绝，不会清理或覆盖旧证据。`--check` 仅检查依赖定位与输出路径，输出 JSON 计划；不创建证据目录、不连接运行时，也不检查浏览器二进制和模板可用性。

从目标 checkout 根目录执行预检，环境就绪后去掉 `--check` 执行场景：

```bash
node project-generation-test/run-project-generation-ui.js --repo "$PWD" --check
node --test project-generation-test/tests/run-project-generation-ui.test.js
```

本地配置需设置 `PROJECT_GENERATION_REPO_ROOT` 为该 checkout 绝对路径，`PROJECT_GENERATION_DEBUG_CONFIG` 为隔离运行时配置的 Spring location（例如 `file:/abs/runtime/application-debug.yml`）；导入文件和模板 catalog/制品须预先准备。配置样例不会启动服务或构建模板。沿 `project-templates/scripts/verify-frontend-templates.mjs --qualification`、`verify-backend-templates.mjs --qualification` 和 `verify-template-publication.mjs` 准备本地合成目录；后者以执行时间生成30天有效期，过期时重新生成。该目录仅用于本地验证，不代表生产发布。

路径隔离、失败证据保存及HTTP 200业务错误已有7项行为验证；隔离运行时已完成三类入口的浏览器下载与ZIP合同检查，本次未触发API回退。该脚本仍未登记为正式 E2E manifest；API回退和下载后工程构建需分别验收，边界见 [DOC-014](../docs/tech-debt-tracker.md#doc-014-应用开发工具的验证缺口)。

## 场景范围与结果解释

脚本使用 `demo` 应用，覆盖以下入口：

| 路径 | 场景 |
| --- | --- |
| `/dev/demo/micro-web` | 微前端项目生成、选配与下载 |
| `/dev/demo/extend-jar-management` | 后端扩展包项目生成、选配与下载 |
| `/platform-component-lib` | 平台组件库项目生成、选配与下载 |

目标环境需要对应入口、模板 catalog/制品、可用测试账户与依赖。脚本包含历史本地登录假设，不能把它视为所有环境的登录前提。测试前应使用隔离环境和受控样例数据，沿正式平台机制准备资源。

脚本产生的 `manifest.json` 记录仓库/输出位置、服务地址、时间、步骤、截图、下载尝试和 ZIP 校验结果；启动后的失败也会保留失败状态与原因。浏览器下载缺失时，`downloadAttempts` 保留该结果，API 回退成功另记；整次结果标为 `completed-with-api-fallback`。截图、包和日志属于单次验证产物，应归 CI 或关联任务。当前 checkout 只维护脚本与配置，不以产物路径名声称产物存在。

结果需分别解释：

- 界面入口、表单选配和截图证明的是对应 UI 步骤；需结合实际执行记录判断。
- 浏览器下载失败时，脚本可能改走 API 下载。API 下载成功不能记为浏览器下载链路通过，应在任务记录中分别列出。
- ZIP 可列出内容及依赖/注册点检查，不等同于生成工程已构建、启动或业务验收通过。
- 每次有效验证还需在任务记录关联源提交、工作区差异、目标环境、样例数据与实际结果；manifest 本身没有完整的源码基线。

历史联调记录可通过 Git 的原文件版本追溯；其中的本机环境和单次成绩只适用于原执行时点。当前验证结论应关联固定基线，在对应任务或 CI 产物中记录。

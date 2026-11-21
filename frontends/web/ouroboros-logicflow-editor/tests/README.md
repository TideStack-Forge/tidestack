# Logicflow Editor Testing Standard

`frontends/web/ouroboros-logicflow-editor` 当前提供 `unit + integration` 两层前端测试。
包内已有一批历史单测保留在 `tests/*.test.mjs`，由 `node --test` 执行；新的包级守卫与源代码测试按前端子树规范放到 `tests/unit/`，由 `Vitest` 执行。
`tests/integration/` 用于编辑器真实挂载和属性面板交互这类集成切片，不纳入默认 `npm test`。

## Placement Rules

- 不要把测试文件放到 `src/` 或 `src/__tests__/`。
- 历史单测仍位于 `tests/*.test.mjs`，新增单测优先迁移或落到 `tests/unit/`。
- 包级守卫与配置测试放到 `tests/unit/config/`。
- 面向源代码的测试优先放到 `tests/unit/src/`，按需镜像 `src/` 结构。
- 集成测试放到 `tests/integration/<theme>/`，并使用独立命令与配置。
- integration 所需的 harness、fixture、平台注入 helper 继续放在当前包的 `tests/integration/**` 下，不回写到 `frontends/web/tests/` 根目录。

## Commands

- `npm run test:unit`
- `npm run test:integration`
- `npm run test:all`
- `npm test`

`npm run test:unit` 会先运行 `tests/*.test.mjs` 历史单测，再运行 `tests/unit/**` 下的 `Vitest` 测试。
`npm run test:integration` 运行 `tests/integration/**` 下的 jsdom + React integration tests。

## Runtime management browser host

`tests/integration/logicflow/harness/runtime-manage-browser.jsx` 挂载真实 AMIS 产品 schema 和生产 UMD 资产，仅提供隔离身份与请求桥。Webpack 宿主使用独立的 chunk namespace，避免与编辑器入口冲突。双 JVM、共享数据库、权限与页面闭环属于仓库级跨栈场景，启动和验收见 [harness.playbook.md](../../../../testing/integration/feature/lowcode/logicflow-runtime-manage/harness.playbook.md)。它不纳入默认前端测试，也不代表正式平台部署验收。

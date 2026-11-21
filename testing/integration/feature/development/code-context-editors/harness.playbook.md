# 执行

前提：Java 21、Maven、Python 3、前端依赖及 Playwright Chromium。确认 43183/43184 无不相关服务；已有服务复用前必须检查 /health 为 code-context-scenarios。仓库根的业务模型 dev reactor 必须能通过测试。

在 frontends/web/main-app 执行：

```sh
CODE_CONTEXT_BACKEND=scenarios npx playwright test --config=playwright.code-intelligence.config.js
```

配置按顺序构建真实 Java provider 服务和前端 harness。输出在 target/playwright-code-context-scenarios。spec 的 case ID 与 cases.md 一致。手动提前启动的服务须由启动者关闭，不能杀死端口上其他进程。

分段命令、夹具范围和 Groovy 独立场景见该前端 tests/integration/script-editor-intelligence/README.md；运行证据在任务 tmp 目录，正式资产不保存一次性截图和制品。

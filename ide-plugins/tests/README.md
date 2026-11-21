# IDE 编辑器测试

从仓库根目录运行 `node ide-plugins/tests/run.mjs --check` 预检，运行 `node ide-plugins/tests/run.mjs` 完整验证。支持 `--lane browser|vscode|intellij|jcef`，只跑指定层；未选层明确记为未覆盖。

增加 `--video` 保留全部浏览器用例的 1280×720 WebM 录像，包括成功用例。录像路径随用例写入本轮 Playwright JSON 报告；原生 IDE 测试保留断言日志。

唯一场景契约、用例和维护流程见 [IDE 编辑器回归运行手册](../../testing/integration/regression/ide/metadata-editors/harness.playbook.md)。共享行为用例位于 runtime 的 `tests/integration/hosts/`；两种宿主消费相同文件样本、使用各自正式 HTML/通信脚本。

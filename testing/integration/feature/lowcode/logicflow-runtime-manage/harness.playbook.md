# 本地联调

从指定 worktree 执行，显式传入 `--repo /absolute/worktree`。该 runner 不连接外部环境；端口 18884、18886–18888 必须空闲。

1. 使用 JDK 21 和 Maven 构建管理模块及依赖，执行其真实测试。生成 `target/test-classpath.txt`：`mvn -pl ability/ouroboros-logicflow-runtime-manage test-compile dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt`。当前目标和依赖必须来自同一基线。
2. 在编辑器包执行 `npm run package:sync`，再用 `node node_modules/webpack-cli/bin/cli.js --config tests/integration/logicflow/harness/runtime-manage-browser.webpack.cjs` 构建宿主。
3. `python3 testing/integration/feature/lowcode/logicflow-runtime-manage/acceptance.py --repo /absolute/worktree --action check`，核对环境和资源。
4. 同一命令改 `--action run`，启动 H2、两个独立 JVM 和宿主。默认工具链在工作区 `tmp/toolchains/jdk-21.0.2.jdk/Contents/Home`。日志及进程身份在本场景证据目录。
5. 另一个终端执行 `--action verify-api`，生成 `cluster-acceptance.json`。浏览器打开 `http://127.0.0.1:18888/system-setting/logicflows`，沿真实页面操作、记录网络和截图。`?role=reader` 仅改变夹具身份权限。
6. Ctrl-C 停止 runner，只回收其拥有的子进程。`owned-processes.json` 记录初始 PID/端口；重启节点的 PID 在专属日志与重启回执中。证据不随进程清理删除。

这是隔离集成验收，不代表正式平台部署、认证链、真实数据库方言或生产 Nginx 验收完成。

逐流程观测验收在宿主就绪后运行 `python3 testing/integration/feature/lowcode/logicflow-runtime-manage/verify_observation.py --repo <worktree>`。该脚本先要求 parent/other 为缺省策略，再依次验证子流开启、仅父流监控、父子均采集、重启及关闭。每轮使用新共享数据库启动宿主，结果写入 `ops-cluster-acceptance.json`。浏览器按 LF-RM-OBS-UI-001 验证设置原因与时长、空态、节点时序、实例采用、只读、窄屏及故障态。

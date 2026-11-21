# 执行说明

先检查工作区、JDK 21、Docker 及隔离 Redis/RabbitMQ 健康。环境参数 `logicflow.redis.port` 与 `logicflow.rabbit.port` 必须指向专用服务；`logicflow.rabbit.container` 仅供带 `codex.task=logicflow-online-debug` 标签的隔离容器故障注入。禁止使用日常开发容器。

当前直接执行入口为本页的 Maven/Playwright 夹具。通用 `fixture_harness.py` 尚缺本场景 `services/redis` 等 fixture manifest，不能据此宣称其 plan/check 已通过，也不能以 `--skip-preflight` 消除缺口。直接运行前须逐项核实隔离服务健康和端口、任务资源标签、实际 classpath、四个支持包及浏览器 HTTP 目标，并保留预检结果；统一生命周期编排的缺口与业务段验证结果分别报告。

从仓库根运行 Maven `-pl platform/ouroboros-develop -am -Dsurefire.failIfNoSpecifiedTests=false`，以 `-Dtest=LogicFlowDebugSharedRepositoryIT,LogicFlowDebugMultiActiveMqIT` 定向运行；测试使用真实生产仓储、MQBridge 和助手命令，业务模型夹具只代替外部元数据输入。前端包运行 `npm run test:integration`。完整命令和实际结果保存到 `tmp/testing-evidence/logicflow-online-debugging/`。

平台和助手必须独立上下文，助手为独立 JVM；不能用同一 JVM 的静态身份模拟两台测试容器。故障场景恢复服务后再判断补发结果。失败需要保留进程日志；关闭任务进程并仅清理任务资源。

## 源构建容器

先 `mvn -B -pl platform/ouroboros-develop -am -DskipTests package`，再运行一次 `LogicFlowDebugHttpContractTest` 取得平台 Surefire XML 中的实际 classpath。`prepare-container-fixture.py --help` 列出完整参数：显式指定 `--repo`、`--classpath-report`、`--loader-tools`（当前 Spring Boot 的 loader-tools JAR）、`--output`（repo/tmp 下新的空目录）、`--base-image`（可用 Java 21 镜像）、`--rabbit-port`，容器内主机默认 `host.docker.internal`。

脚本把当前 reactor classes 打包为带目录项的 JAR，四个助手支持包保留在 support 下；通过生产 materializer 拷贝到 extend 后加载。执行 `docker build -t <task-image> <output>/image`。这验证平台的实际 Docker backend、运行时 inspect、助手注入与 MQ，而非手工模拟容器列表。

定向测试增加 `LogicFlowDebugDockerIT`，传入 `-Dlogicflow.docker.image=<task-image>`、`-Dlogicflow.docker.host=<docker-socket-uri>`、`-Dlogicflow.docker.fixture=<output>`。测试创建两个前后继容器，断言四个支持包真实从 extend 加载、正常/异常执行与输入传递可读、旧目标拒绝、新目标立即开始。资源只清理任务标签匹配的容器；失败容器日志保存在 output 下。

## 隔离工作台浏览器联调

用平台 Surefire XML 的 `surefire.test.class.path` 启动 `com.ouroboros.develop.logicflowdebug.DebugBrowserFixture <output>`，传入上述 Redis、Rabbit 和 Docker 系统属性。启动器保持 stdin 打开，看到 `BROWSER_FIXTURE_READY` 后访问环回端口 18878。退出时向 stdin 写入换行，finally 清理容器与随机缓存区域。

在逻辑流编辑器包运行 `npm run test:integration:runtime-debug`；Playwright 配置自行启动环回端口 18879 的页面并代理到 18878。需要先安装本包锁定版本的 Chromium。测试不拦截 HTTP，使用真实 React/X6、控制器、仓储、MQ 和 Docker，触发两次正常和一次异常，验证动画、红色失败节点、输入输出、堆栈、停止及刷新后历史，并保存截图和 trace。

此夹具的认证策略固定为 alice，业务模型由测试输入构造；它不证明主应用登录/导航或 Kubernetes 集群部署。权限由独立 HTTP/适配器测试覆盖，执行使用真实 FunctionalFlowCompiler。输入刺激接口仅存在于 test 源码，产品没有该接口。

## 分段验证后联合调试

先分别执行下表中的段，每段独立保存命令、日志、Surefire XML 和用例结果；失败时在该段调查，不能以最终页面可用代替段内合同。各段使用自己构造的输入、缓存区域和进程，不读取上一段产生的业务数据。

| 段 | 可独立验证的边界 | 入口及真实依赖 |
| --- | --- | --- |
| 1 执行与采集 | 正常/异常/异步事件、输入来源、观察器故障隔离、租约与有界缓冲 | FlowRuntimeLifecycleTest、LogicFlowDebugCaptureRuntimeTest、LogicFlowDebugRuntimeAnalyzerTest、DebugSnapshotterTest；传输替身，不依赖 MQ/Redis |
| 2 命令与目标 | profile/JVM 身份过滤、旧响应拒绝、探测/续租/停止编排 | DefaultDevHelperCommandServiceGenerationTest、HelperCommandDispatcherGenerationTest、LogicFlowCaptureCommandsTest、TestAppLogicFlowDebugRuntimeTest、TestAppRuntimeSpecFactoryTest、LogicFlowDebugCaptureServiceTest；运行时和传输边界替身 |
| 3 共享仓储 | 原子占用、乱序重试去重、跨客户端恢复、缓存驱逐与清空 | LogicFlowDebugSharedRepositoryIT、SpringBeanCacheManagerRedisIT；真实 Redis |
| 4 多活 MQ | 双平台/双助手进程目标隔离、MQ 重启补传、不影响业务结果 | LogicFlowDebugMultiActiveMqIT；真实 RabbitMQ、Redis、独立助手 JVM |
| 5 HTTP 与通知 | HTTP 权限及公开合同、跨副本私有路由与用户隔离 | LogicFlowDebugHttpContractTest、RedisDefaultIdentityPrivateDeliveryIT；实际 MVC 映射、真实 Redis 和 WebSocket，HTTP 合同的服务输入由夹具提供 |
| 6 前端投影 | 从事件到执行列表、原画布标注、节点状态、播放控制及退出恢复 | 编辑器 `npm run test:integration`；真实 React/X6，HTTP/STOMP 边界替身 |

Maven 各段通过 `-Dtest=<该段类名列表>` 单独运行，沿上文的 reactor/环境参数执行；第 1 段可缩小到 `-pl core/ouroboros-logicflow/ouroboros-logicflow-debug-helper -am`，第 5 段同时选择 `ability/ouroboros-websocket/ouroboros-websocket-integration-tests`。检查实际执行了测试且失败、错误、跳过均为零。每段的成功仅证明表中边界，不能外推到表外依赖。

六段通过后，运行 `LogicFlowDebugDockerIT` 验证真实容器注入与重建，再启动上述浏览器夹具进行联合调试。可以为同一个浏览器场景启用录屏：

```sh
LOGICFLOW_DEBUG_RECORD=1 \
LOGICFLOW_DEBUG_EVIDENCE_DIR=/absolute/repo/tmp/testing-evidence/logicflow-online-debugging/recording-run/browser-results \
npm run test:integration:runtime-debug
```

每次使用新的证据目录，避免 Playwright 清空上次证据。录屏模式仅增加阅读停留时间，不改变产品的执行、消息、存储或回放逻辑；所有步骤仍按可观察状态断言。测试页上方的正常/异常触发按钮明确标注为测试入口，下方装配实际 Canvas 和 RuntimeDebugWorkbench，调试仅标注预先加载的设计节点。记录包括容器选择、两次正常执行、动画、节点输入输出、一次异常及堆栈、时间线/单步、停止和刷新后历史恢复。

该模式保留成功运行的 `video.webm`、`trace.zip`、截图、`chapters.json`、实际 HTTP 请求响应 `http-exchange.json` 和 Playwright JSON 报告。章节时间从测试主体开始计算，不作为视频容器的精确时间码。需要通用播放器格式时，将原始视频转为 MP4，同时保留原件：

```sh
ffmpeg -i /absolute/path/video.webm -c:v libx264 -crf 20 -pix_fmt yuv420p -movflags +faststart /absolute/path/logicflow-online-debug-demo.mp4
```

该视频是隔离工作台联调录像，页面直接挂载 RuntimeDebugWorkbench，不是正式主应用或现有逻辑流编辑器的实机验收。它证明工作台经过 HTTP/MQ/Redis/Docker 链路的行为；其固定身份和缺少 STOMP 客户端的范围应明确保留。正式编辑器验收使用下节入口。

## 正式主应用与编辑器录屏

归属 LF-DBG-UI-003、LF-DBG-CONTAINER-002。使用任务独立的开发平台、主应用、编辑器微前端和 TEST_APP；平台从 `OuroborosDevelopApplication` 正式入口启动，测试应用从本分支构建的完整 mothership 运行依赖启动。镜像必须包含本分支 `init.sh`。不要用 DebugBrowserFixture 或只覆盖少数 JAR 的不完整基座替代正式运行时。

前置条件：平台与测试应用各使用专用数据库/缓存命名空间，MQ 使用任务独立 vhost；通过正式应用 API 创建验收应用，再通过 `/api/dev/{appName}/logic-flow` 和 `/api/dev/{appName}/webapi` 创建模型。业务流程 `order.calculate` 为“接收订单 → 提取参数 → 校验金额 → 计算含税金额 → 返回结果”，金额小于零抛出 `IllegalArgumentException("订单金额不能小于零…")`，正常返回金额、6% 税额和含税总额。公开 GET `/public-api/debug/order` 绑定该逻辑流，参数为 `amount` 和 `orderId`。接口路径必须显式带公开前缀，不能仅靠 scope 字段推断。

通过正式容器创建 API 启动候选镜像，选择 TEST_APP、DOCKER、本人 profile 和独立测试端口。逐项预检：主应用可正常登录、目标应用卡片可打开、逻辑流列表存在该模型、测试容器运行且助手已加载、普通业务 HTTP 请求返回正确含税金额。通过正式平台重启操作再检查 `/app/deploy/extend` 仍在 JVM loader.path；重新开始采集必须成功，不能用重建容器代替重启恢复验证。

主应用的 `--api` 指向候选开发平台，`--logicflow_editor` 指向本分支编辑器 dev server，二者使用正式入口。编辑器 dev server 建议 `--no-client --no-hot`，避免微前端预加载的 webpack 警告 overlay 遮挡主应用。生产构建另跑 `npm run build`。保存真实登录会话为 Playwright storageState；该文件含认证凭据，仅放忽略目录，不能随证据分发。

在编辑器包运行，证据目录每次使用新路径：

```sh
LOGICFLOW_PLATFORM_URL=http://127.0.0.1:18891 \
LOGICFLOW_PLATFORM_AUTH=/absolute/repo/tmp/platform-auth.json \
LOGICFLOW_RUNTIME_CONTAINER=ouroboros-test-app-lf-debug-demo-admin \
LOGICFLOW_DEBUG_APP=lf-debug-demo \
LOGICFLOW_DEBUG_EVIDENCE_DIR=/absolute/repo/tmp/testing-evidence/logicflow-online-debugging/platform-run \
npm run test:integration:runtime-debug-platform
```

测试从主应用正常导航，进入现有编辑器，选择运行中的本人容器并开始采集；通过该容器的普通公开 HTTP 业务接口触发两次正常和一次异常。当前基座 Nginx 未转发 `/public-api`，因此验收使用 `docker exec curl` 请求容器内 8080 端口，仍经过正式 WebAPI 路由、安全过滤器和业务执行器；该限制与调试链路分别记录，不声称本录屏覆盖公网业务入口或后端 API 隧道。没有替换认证、拦截接口或新增测试执行端点。

浏览器首先记录原画布/节点 DOM、坐标和缩放，验证调试仍复用它们、隐藏编辑面板且禁止拖拽；退出恢复原样式与面板，再次进入查看历史，最终验证节点恢复可拖动（不保存该试拖）。撤销历史由完整编辑器组件集成验证。浏览器断言选次后全部实际连线立即同时流动、节点最终状态、暂停流动保留高亮、正常输入输出、红色异常节点与堆栈、下游阻断、停止后返回编辑器再进入恢复历史。1280 窗口下点击“适应画布”后检查完整路径和整体溢出；尺寸变化不触发重新布局或自动缩放。正式 SDK 必须发出私有采集订阅并收到提示；不以 REST 补偿成功替代真实 STOMP 证据。

产物包括原始 WebM、正式编辑器/完整路径/异常/历史截图、章节时间、只保存调试接口与业务响应的 `http-exchange.json`、私有订阅和提示计数、Playwright JSON 报告。trace 默认关闭，避免包含登录 token 的网络请求进入可分发证据。转码 MP4 时保留原件，允许裁去最初的静态资源等待；不拼接或合成产品画面。

本场景复用已有真实登录会话，证明主应用导航和编辑器接入；不包含重新输入密码的登录操作。单平台录屏使用本地缓存时，多活共享仓储和跨副本推送仍由前述独立 Redis/MQ 场景证明。Kubernetes、替换 MQ binder、生产部署和全仓 verify 均不能由该视频推定。

## 性能与边界

编译平台测试类后执行 `measure-performance.py --repo <repo> --classpath-report <surefire-xml> --java <jdk21-java> --output <new-repo/tmp/path>`。每种模式使用三个独立 JVM，交替顺序，每次预热 10,000 次、测量 10,000 次。baseline 从 classpath 排除助手，idle 已装载但无租约，active 以 100 次为一轮并排空确认。传输使用 mock，测量业务线程采集成本；网络恢复由真实 MQ 场景验证。

runner 保留全部原始样本和 p95 中位数：idle 增幅 ≤2%；active 增幅 ≤10% 或增加 5 ms（取更宽者）。资源断言覆盖完整接收、零拒绝/缺失、排空后零待发字节和关闭后发送线程释放。高并发、容量溢出、快照大对象及租约边界另由模块行为测试覆盖；不可用正常吞吐测量代替这些边界测试。

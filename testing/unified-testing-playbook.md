# Unified Testing Playbook

这份文档把近期前后端联调、重大重构回归、平台 smoke、业务模型回归中反复验证过的经验，整理成一套常态化执行流程。

它是 `testing/` 下的综合执行标准，覆盖：

- 即时前后端联调
- harness 模式排障
- 集成 smoke
- 合并前 regression
- 全自动 E2E 的进入条件

如果你要看“这套测试怎么被管理、怎么建账、怎么闭环”，请先看 [testing/test-management-standard.md](test-management-standard.md)。

## 适用范围

适用于以下场景：

- 重大重构合并前回归
- 前后端联调
- 外部依赖接入验证，例如 LDAP、OIDC、消息队列、对象存储
- 低代码全链路测试
- 基座高代码数据 API 验证

## 场景资产入口

每次执行前，先读取该场景目录中的统一资产。

如果是集成测试，入口在 `testing/integration/**`。
如果是全自动 E2E，入口在 `testing/e2e/**`，但仍必须回看对应的集成场景契约。

集成场景至少读取：

- `scenario.yaml`
- `contract.md`
- `cases.md`

如果场景是 harness 模式，还要读取：

- `harness.playbook.md`

如果场景具备全自动 E2E，还要读取：

- 对应 `testing/e2e/**` manifest

这一步的目标是先对齐场景语义，再决定执行方式。

在真正进入场景执行前，还必须先跑一轮 fixture 预检，至少确认：

- data fixture 指向的仓库绑定或 app-root 输入真实存在
- runtime fixture 的 base URL 输入来源可解析
- identity fixture 的凭据来源没有失真

如果 fixture 预检已经失败，就不应继续把后续异常直接归因到业务代码。

对于 data fixture，默认优先使用显式仓库参数，而不是依赖某次历史 run 留下来的 `tmp/...` 目录。例如：

- `--repo app-metadata=/abs/path/to/repo`
- `--repo platform-config=/abs/path/to/repo`

如果场景需要多个仓库，可以重复传入 `--repo NAME=/abs/path`，再由 fixture 自己声明消费哪个 alias。

## 代表场景

当前优先场景包括：

- `testing/integration/feature/oral-cases`
- `testing/integration/smoke/lowcode/full-app-lifecycle`
- `testing/integration/regression/data-api/metadata-high-code-regression`
- `testing/integration/smoke/auth/ldap-login`

这些场景只是入口示例，不应反向污染公共经验。像 LDAP、`mes-business-model` 这类具体实例，只能作为“某类场景的一个例子”，不能替代通用规则本身。

## 一、先识别运行模型，不要先猜 Bug

任何联调和集成测试开始前，先确认“当前联调三元组”：

1. 后端实际运行的是哪套产物
2. 前端入口实际指向哪里
3. 外部依赖和测试数据来自哪里

如果测试数据来自仓库 checkout，这一步必须把“仓库地址”显式写进本轮输入，而不是默认假设某个本地临时目录已经准备好。

必须避免以下误判：

- 把源码状态误认为运行时状态
- 把后端监听地址误认为前端公开入口
- 把 develop 平台误认为目标业务运行时

### 何时用 `develop`

优先用于：

- 开发平台侧的管理面
- 应用创建、业务模型创建、模型管理、开发辅助页面
- `dev/*` 这类明显属于 develop 管理面的接口

不要拿 `develop` 默认替代真实应用运行时。

### 何时用 `mothership`

优先用于：

- `app.*` 路由
- 生成应用的真实运行态
- 需要验证运行基座是否正确装配 metadata / extend / runtime 权限的场景

当一个问题可能是 develop 路由冲突、管理面代理、壳应用转发导致时，应优先切到 `mothership` 再判断。

### 何时两者都要用

低开全链路通常必须同时用：

- `develop`: 建应用、建模、启动运行时
- `mothership`: 打开真实应用、登录、验证生成后的行为

## 二、环境准备按固定顺序执行

推荐顺序：

1. 明确目标场景与通过标准
2. 先跑 fixture 预检和 lifecycle plan，确认要拉起哪些依赖、哪些会被跳过
3. 拉起外部依赖夹具
4. 准备运行时覆盖配置和可写目录
5. 安装或刷新后端产物
6. 拉起后端运行时
7. 对本地运行时执行完整性预检
8. 拉起前端壳或宿主页
9. 准备测试账号、组织和权限上下文

如果测试入口依赖 Maven reactor，还要先校验测试命令是否能拿到当前工作树里的依赖模块，而不是误用本地仓库旧产物。

### 常见拉起方式

以下命令从仓库根执行。浏览器本地联调优先复用 `scripts/dev-runtime/bootstrap-develop-browser-e2e-local.sh`；手工 classpath 命令必须沿用它的配置覆盖，不能只拿旧 runtime.classpath 启动。

#### 1. 本地 classpath 方式拉起 `develop`

适合：

- 平台管理面联调
- browser smoke
- 需要快速复用本地前端 dev server 的场景

现有脚本采用的模式是（是否在目标环境通过须另留本轮证据）：

- 先做 `mvn -pl :ouroboros-develop -am -DskipTests -Djacoco.skip=true install`
- 刷新 runtime classpath
- 用 `java -cp "$(cat runtime.classpath)" com.ouroboros.develop.OuroborosDevelopApplication ...` 拉起

这类模式的关键不是命令本身，而是：

- classpath 必须和当前 worktree 构建结果对齐
- debug config、数据根目录、文档目录要显式覆盖到本地可写路径
- 主前端使用 `npm run serve`（`scripts/webpack-serve.js`）时，前后端端口和日志都要单独留档
- 如果通过 shell 启动器拉起前后端，不能只把“脚本成功退出”当成服务已就绪；至少还要在父脚本退出后再次确认目标端口仍在监听、关键入口 URL 仍可访问，且后台进程没有随父脚本一起被回收

#### 2. 分层 `jar + loader.path` 方式拉起 `mothership`

适合：

- 真实运行态验证
- `app.*` 路由验证
- 需要显式装配 metadata / extend / base libs 的场景

这类模式的核心是：

- 启动入口用 `ouroboros-mothership.jar`
- `loader.path` 明确包含应用级 extend、基座级 `ouroboros-lib`、`third-party-lib`
- `spring.config.location` 同时包含 base config、app config 和本地覆盖 config

如果只拉一个 `jar`，却没有把运行时扩展层和应用仓 `extend` 带上，很多问题根本不具备判断价值。

### 扩展 jar 不是一刀切，看本轮目标

`mothership` 的标准启动脚本会把以下目录拼进 `loader.path`：

- 应用级自定义扩展：`APP_ROOT/extend`
- 应用级扩展：`APP_METADATA_DIR/extend`
- 基座级 `ouroboros-lib`
- 基座级 `third-party-lib`

因此，“要不要编译扩展 jar”不能靠猜，必须先回答这轮到底在验证什么：

- 如果本轮只验证 metadata 生成的标准 CRUD / query / detail 链路，且这些链路不依赖自定义 SPI、监听器、Bean 覆盖或自定义 controller，则可以先用“不带应用扩展 jar”的基线跑通基础链路。
- 如果本轮要验证真实应用运行态，尤其涉及：
  - `extend` 下的自定义监听器 / SPI
  - 自定义 controller / service / filter
  - 需要扩展 jar 才会生效的运行时装配
  则必须先编译并挂载对应扩展 jar，再执行回归。

结论必须写进证据里，不能混说成同一种测试：

- `baseline-without-extend`
- `baseline-with-extend`

两者都可以有价值，但验证目标不同，结论不能互相替代。

#### 3. 先打包镜像，再用 Docker 拉起

适合：

- 接近真实部署形态的 smoke
- 需要验证容器内目录结构、环境变量、挂载、Docker API、网关等行为

通用顺序是：

1. 先构建目标镜像
2. 准备容器所需的 metadata、config、upload、document、logs 挂载目录
3. 再用 `docker run` 或 compose 拉起
4. 启动后先看容器日志和未认证探针，再进入业务验证

Docker 模式下要特别注意：

- `mothership` 容器内的类路径和本地 IDE/classpath 模式不是一回事
- 容器里的 metadata / extend / deploy 目录层级要和启动脚本约定一致
- 平台联调若依赖 Docker Remote API，要先确认 Docker API 本身是通的

### 启动方式选择原则

- 优先选最接近当前验证目标的运行形态
- 不要为了省事，用 develop 去替代真实 app runtime
- 不要为了图快，用本地 classpath 结果替代容器形态结论
- 一旦要做 merge gate，运行形态必须可解释、可复现、可留证据

fixture lifecycle 还要额外区分两类：

- 描述性 lifecycle：只表达 harness/manual 步骤，不应被自动执行
- 可执行 lifecycle：显式声明 shell 命令，才能进入统一 runner 的自动编排

前端浏览器资产也要先做依赖就绪度预检：

- `package.json` 是否存在
- 目标 npm script 是否已声明
- `node_modules` 是否已安装

如果浏览器 bridge 报告的是“脚本存在，但依赖未就绪”，这应优先归类为环境/依赖阻断，而不是业务回归。

对于多模块定向回归，优先使用：

- `-pl <target-module> -am`
- `-Dsurefire.failIfNoSpecifiedTests=false`

原因是：

- 只用 `-pl` 容易让目标测试模块吃到本地仓库里的旧 Jar
- 这会把“依赖没进 reactor”的问题误判成源码编译错误或接口回归
- 加上 `-am` 后，依赖模块会以当前工作树源码进入 reactor
- 加上 `surefire.failIfNoSpecifiedTests=false` 后，前置模块不会因为“不包含目标测试类”而提前失败

### 启动完成不等于运行就绪

后端打印以下日志时，只能说明 Web 容器基本起来了：

- `Tomcat started on port(s)`
- `Started ...Application`

但这还不等于场景已经可执行。

至少还要继续确认：

- 未认证 HTTP 探针已经进入预期的 JSON 鉴权面
- 后台初始化、数据库迁移、元数据加载、运行时扩展装配已经完成
- 启动保护期已经结束，不再持续返回 `503`
- 关键依赖，如 Docker API、数据库、消息队列，不再处于初始化阻塞状态

如果在这一步还没完成，就直接打业务接口，测试结论通常只会被“启动后半段未完成”污染。

## 三、分层验证，不要一上来就点页面

推荐验证层级：

1. 依赖层
2. 后端层
3. 前端壳层
4. 页面/交互层
5. 业务链路层

### 依赖层

验证 LDAP、数据库、消息队列、对象存储等依赖是否通。

### 后端层

先验证：

- 服务是否已启动
- 健康检查是否通过
- 关键 API 是否存在
- 日志里是否有启动阻断错误

如果是本地运行时，还要在正式执行前做一次完整性预检：

- `-jar` 指向的主 Jar 是否仍然存在
- `-Dloader.path` 指向的目录是否仍然存在
- 运行中进程已打开的关键类路径 Jar 是否已被外部删除

如果出现“进程仍然活着，但类路径 Jar 已不存在”的情况：

- 必须立即判定该 runtime 基线失真
- 终止本轮回归执行
- 不要继续把接口异常归因到业务模块或当前分支

### 前端壳层

先验证：

- 入口地址是否正确
- 基座是否加载
- 代理链路是否正确

### 页面/交互层

再验证：

- 页面是否能打开
- 表单是否能真正提交
- 下载、弹窗、自定义组件是否能真实完成动作

### 业务链路层

最后验证：

- CRUD
- 主子表
- 查询过滤
- 权限边界
- 业务动作按钮

## 四、不能只看列表，写操作必须进入主链路

以下判断都不可靠：

- 页面能打开
- 列表有数据
- 查询接口返回 `200`

正式验证至少要覆盖：

- `create`
- `update`
- `delete` 或破坏性替代验证
- `detail`
- `list`

对低代码或业务模型生成 API，尤其要覆盖：

- 主子表同步提交
- 复杂查询条件组合
- 过滤器参数形态
- 引用字段回填

## 五、权限边界必须单独建模

权限不足不是“顺手看看”即可，必须和正常路径分开建模。

至少要区分：

- 正常有权限用户
- 无权限用户
- 首次绑定或上下文不完整用户

这样才能避免把 RBAC、组织过滤、LDAP 绑定、模型过滤器问题误判成代码回归。

## 六、结果判定必须看三层

至少同时看：

1. HTTP 状态码
2. 业务返回码/响应结构
3. 实际数据效果

注意事项：

- HTTP `200` 不等于成功
- 页面无报错不等于写入成功
- 空数据不等于查询分支没执行
- 启动期 `503` 先判断是否仍处于保护期

如果接口直接返回 Tomcat 默认 `500 text/html` 页面，而不是业务 JSON：

- 优先判定为运行时链路、Filter、SPI 或底层装配问题
- 立即查看后端日志中的第一条异常
- 不要先把它归类成业务接口回归

如果本轮 live 回归使用的是“借用 runtime”而不是当前分支 freshly-built 产物：

- 可用于证明测试 harness、runner、执行链路是否成立
- 不能直接把模块级故障当成当前分支 merge blocker
- 需要先判断该故障是否与当前分支改动存在直接因果关系

如果借用 runtime 的本地完整性预检失败，例如：

- `loader.path` 目录已丢失
- `lsof` 显示进程仍持有已不存在的 Jar
- 启动主 Jar 已不存在

则本轮结果应标记为：

- `runtime-baseline-invalid`

含义是：

- 当前测试链路可以继续改进
- 但这套 runtime 不能再作为 merge gate 或模块健康判断依据
- 必须先修复或重建 runtime，再重新执行回归

如果测试失败出现在“模块测试尚未真正开始前”的构建阶段，例如：

- reactor 前置模块先因为 `-Dtest=...` 匹配不到测试类而失败
- 目标模块没有带 `-am`，实际解析到本地仓库旧依赖

则这类失败应优先标记为：

- `test-command-invalid`

含义是：

- 当前失败还不能归因到业务逻辑或当前分支实现
- 需要先修正测试命令和 reactor 边界，再重新执行目标回归

## 七、复杂查询参数要从模型和过滤器反推

复杂查询不能只靠猜参数名。

优先从以下来源提取参数形态：

- business model 过滤器定义
- UI schema / AMIS 配置
- `crud` / `api` / `source` / `schemaId`
- `beforeScript` / `afterScript`

否则很容易：

- 漏测脚本分支
- 把参数名写错
- 把权限过滤和业务过滤混淆

## 八、资产真相必须先于覆盖率数字

统一测试体系的首要目标不是“把分母做大”，而是“让分母真实”。

尤其在重大重构场景下，以下几类误判必须先排除：

- 普通 `views.detail` 页面被误判成主子表
- 显式 `views.add` 下遗漏必填字段，但仍被算成 stage-one 可写
- 高代码 seed 已建档，但其实没有样本、路径参数或清理模板
- 借用 runtime 本身已经失真，却仍继续累计 merge gate 结果

标准动作：

1. 先验证测试资产是否真实可执行，再统计覆盖率
2. 对“已建档”和“已可执行”分别统计，不能混为一谈
3. 覆盖率下降如果来自伪分母被剔除，应视为体系收敛，而不是质量退化

## 九、主子表识别必须基于真实业务结构，不能全 JSON 扫描关键字

业务模型里大量普通详情页都带 `views.detail`，如果简单扫描 `detail` 键，会把普通单表模型错误升级成主子表高风险模型。

标准动作：

- 主子表识别优先读取 `child relation`
- API 级 detail 配置只能作为补充证据
- 普通详情页只能生成 `detail` 验证，不应自动生成 `master-detail-sync`

## 十、显式 add view 存在时，stage-one 写环必须尊重 add view 边界

一旦模型已经有显式 `views.add`，就说明这是运行时真实暴露给前端/生成表单的写入边界。

此时不能因为字段定义里“还看得到字段”，就自动把遗漏的必填字段补回去并继续算作 stage-one 可写。

只有以下情况允许从 add view 外部保守补值：

- 默认值已经能稳定生成
- 明确的上下文注入字段，例如 `companyId`
- 主键样本需要补齐，且补齐规则已标准化

除此之外：

- 必填字段遗漏应降级为 `stage-two-only`
- 覆盖率统计必须同步移出 stage-one 分母

## 十一、runtime preflight 必须同时覆盖进程完整性和 HTTP 探针

端口监听、PID 存在、类路径完整，只能说明进程“像是活着”。

合并前基线还必须补一层未认证 HTTP 探针，例如：

- `GET /api/auth/authorities -> 401`

只有这样才能快速发现：

- 跑错运行时
- 启动后段未真正可用
- 路由面已经偏离预期

如果 HTTP 探针未达预期：

- 应直接标记为 `runtime-baseline-invalid`
- 停止继续累计 merge gate 结果

## 十二、请求层必须先容错，分类和报告层才能成立

执行器面对的并不总是业务 JSON，还可能是：

- Spring Boot 默认 HTML 错页
- 代理层纯文本错误
- 连接失败 / 超时 / DNS 错误

如果请求层先在 JSON 解析时崩掉，后面的失败分类、证据留档、报告汇总都会失效。

标准动作：

- 请求层必须把 `non-json`、`transport error` 结构化返回
- 分类层再决定它属于 runtime 问题、权限边界还是回归
- 报告必须能保留原始 excerpt，而不是整轮直接中断

## 十三、证据必须留档

每轮执行至少留以下证据中的一部分：

- 后端日志
- 前端 console
- 网络请求与响应
- Playwright trace
- 截图
- 用例表执行结果
- 测试报告

没有证据的“通过”不可复查，也不适合做合并依据。

## 十四、清理是标准步骤，不是可选善后

每次测试完成后必须执行：

1. 清理不再需要的后台进程
2. 释放占用端口
3. 清理过期 PID 文件
4. 标记保留的必要长运行进程

这是正式经验教训，不能遗漏。

更严格的执行规则是：

- 默认清理本轮自己拉起的所有后台进程
- 如果确实要保留，必须在结论里显式写出 `PID + 端口 + 保留原因`
- 如果没有显式登记，就不应把进程继续留在后台

否则其他人无法判断哪些进程仍有用，哪些只是历史残留。

## 十五、对外部认证和复杂前端，先分清什么适合 E2E，什么适合 harness

适合全自动 E2E 的：

- 稳定登录链路
- 稳定 CRUD happy path
- 契约明确的 smoke 路径

适合 harness 的：

- 需要动态排查的场景
- 口头临时指定的测试用例
- 需要综合页面、日志、网络、运行时状态共同判断的问题

两者共享同一份场景契约和用例表，不要重复造两套。

## 十六、推荐执行闭环

1. 读取场景契约和用例表
2. 拉起夹具
3. 做分层健康检查
4. 执行 harness 或 E2E
5. 采集证据
6. 生成报告
7. 清理进程和临时资源
8. 回填经验教训

## 十七、经验教训回填规则

当出现以下情况时，应把经验回填到标准文档或场景 playbook：

- 同类误判第二次出现
- 某个依赖经常成为伪阻塞
- 某种路由、权限、过滤器组合容易误测
- 某种启动/停止/清理动作反复遗漏
- 某种 reactor / 构建命令经常导致假失败或假回归

经验只有进入标准，才算真正沉淀。

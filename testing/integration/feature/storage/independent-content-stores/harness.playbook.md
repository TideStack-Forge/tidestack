# 执行独立存储验证

在仓库根执行：

```bash
bash testing/integration/feature/storage/independent-content-stores/run.sh --base dev-next
```

`--base` 使用实际本地引用或固定提交，不隐式改成远端引用。CI检验当前交付源码时使用 `--base HEAD`。执行期间 HEAD、基线或任何源码变化都会导致失败。证据写入 `tmp/testing-evidence/oss-file-storage/gate-*/`，包括完整源码指纹、测试清单、命令/起止时间/退出码、XML、覆盖率、JAR指纹和最终summary；失败不会生成pass状态。

## 真实服务

已有隔离 MinIO 时，通过 `OSS_TEST_ENDPOINT`、`OSS_TEST_ACCESS_KEY`、`OSS_TEST_SECRET_KEY` 提供测试环境。凭据和环境必须允许创建、删除测试bucket，不得使用生产账号。

未提供 endpoint 时，入口自动准备自身MinIO：优先使用 `MINIO_BINARY` 显式指定的可执行文件或PATH中的minio，否则使用固定Docker镜像 `minio/minio:RELEASE.2025-04-22T22-12-26Z`。为每次运行生成临时凭据并绑定本机随机端口；通过真实health检查后开始测试，结束时只停止本次进程/容器。CI已有Docker构建环境可直接复用；无Docker时须预装MinIO或提供隔离endpoint，依赖缺失会失败。

每个S3用例操作自身新建的oss-test-UUID bucket。H2和文件目录由JUnit按例隔离。MinIO源码构建版本可能显示DEVELOPMENT.GOGET；使用这种二进制时另记固定源码commit，不能当作发行二进制认证。日志与证据不记录测试凭据。

## 完整流程

1. 固定基线和源码指纹，生成完整场景测试清单。
2. 验证器自身行为测试；完整reactor clean test-compile。
3. 执行动态发现的存储、上传、资源、文档回归及开发JAR/Logo回归，严格检查本轮报告。
4. 执行完整架构模块、原70%覆盖率门禁、包结构门禁和实际上传JAR分包核对。
5. 清理本次MinIO，再次核对源码、HEAD和基线，全部成功才记录pass。

后端Jenkins的verify-content-storage阶段与本地preflight调用此入口，沿用已有门禁开关。`MAVEN_REPO_LOCAL`会透传给全部Maven步骤。

## 辅助检查器接入

`pregate.json`提供gstack正式支持的runner/dependencies声明，将相关改动映射到真实执行本入口的Python测试桥接。没有空映射、降级或忽略失败。因为本仓库忽略.gstack且目标分支尚未携带策略，首次使用按gstack的官方user-policy机制安装该文件；后续仍以版本库内这份声明作为可审阅来源。

runner逐文件使用标准库 unittest discover，按实际文件路径加载用例。手册的manifest或锁文件变动选中该包既有的6个测试文件，并且只执行一次原`test:coverage`入口；未知类型直接失败。依赖策略和本次8个JUnit迁移文件映射到现有依赖策略行为测试，检查真实源码的框架分类、继承归属和台账一致性。runner还固定执行这8组原有Java测试的Maven回归，防止只检查分类却遗漏实际用例执行；Java、Python或手册任一步失败均使入口失败。前端依赖需先在对应包执行`npm ci`。

辅助检查器通过BOM引用选中的历史候选台账测试仍执行；其正例使用冻结台账指定的真实Git基线与唯一获准的项目版本元数据改动，保留全部原断言并验证提前升级Boot会失败。它验证升级前阶段合同；当前源码通过完整reactor编译、模块行为和架构门禁独立验证。

注意：当前gstack-checks的 `--base dev-next` 固定解析为origin/dev-next，不能表示本地同名分支。当两者不同时，直接运行的范围不等于本地分支验收范围。需保留两个SHA，使用以本仓库为本地origin的隔离Git克隆进行辅助检查：该克隆的origin/dev-next自然对应实际本地目标，并检出完全相同的待验收提交；同时将项目策略复制到克隆外的官方user-policy位置。不得修改真实origin的跟踪引用或检查结果账本来制造通过。

隔离克隆的READY_FOR_SHIP仅认证该克隆中相同源码的运行结果；原工作区的辅助账本不会因此改变。主工作区正式run.sh结果按自身固定基线和源码指纹独立核验。未配置此辅助工具的CI仍执行相同Maven验收入口，不依赖本机gstack安装。

本场景验证功能、事务恢复、实例隔离与依赖边界；不包括真实云账号认证、完整平台浏览器链路、生产迁移、容量压测或长期稳定性验收。

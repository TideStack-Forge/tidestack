# RPC 工作区测试验收合同

开发人员在正式主应用 Drawer 中编辑 RPC 模型。保存只改当前应用工作区；测试通过开发平台复用原 RPC 执行链，使用已保存的主模型及递归认证依赖；填写信息后显式提交，仅提交本模型资源，成功才关闭。

## 夹具与隔离

Java 21 与 Maven 使用当前 worktree 源码，前端使用同 worktree 的 main-app。真实部署验证使用独立端口、应用、数据库 schema 与数据根目录；目标是本地可控 HTTP stub。认证走正式登录、应用权限和资源锁。不得用前端模拟请求或运行态全局刷新代替工作区读取。

组件测试在所属 Java 模块使用真实协议发送、SPI builder、配置读取和 Git/锁实现；输入资源或身份由可控夹具提供。权限过滤器测试覆盖查看者 GET 初始化及 POST 执行被拒绝。AMIS harness 复用项目原 Drawer、字段和动作归一化，只替换 transport，不能代替部署证据。

## 结果判定

按 RPC-T01～RPC-T22 核对，详见 [用例映射](cases.md) 和 [设计](../../../../../ability/ouroboros-rpc-model/docs/model-testing.md)。请求次数、实际字节、资源内容、VCS 变更文件、锁、隔离状态和接口 DTO 为判据；源文件字符串匹配不是行为证据。覆盖率使用所属 Maven 模块 BUNDLE LINE 门槛 70%，不以某个高覆盖包代替整包门槛。

必须保存两次保存零 commit、测试最新地址、提交范围与成功关闭的真实旅程；敏感参数、认证原值和令牌不得写入截图或公共证据。目标非 2xx、空响应、解码、连接失败、超时和超限分别判断。

## 复验入口

分组扩展沿用同一场景，增加 RPC-G01～RPC-G06。组件验证覆盖元数据路径、保存、装配与 API 响应；AMIS harness 覆盖真实表单、左右分组列表、标准 Picker 弹窗和逻辑流选择器转译结果。分组界面测试的后端响应由 transport 夹具提供，不据此宣称真实部署联调通过。

仓库根目录执行：

```sh
mvn -pl :common-expression,:ouroboros-dev-model-manage-webapi-scaffold,:ouroboros-rpc-model-runtime,:ouroboros-rpc-model-dev test jacoco:check@check
mvn -pl :ouroboros-rpc-model-dev -am -Dtest=RpcModelTestServiceTest,RpcModelTestRequestTest,RpcModelWorkspaceSaveTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl :architecture-tests -am test
```

主应用目录执行：

```sh
npx vitest run tests/unit/src/components/amis/renderers/rpc-model-editor tests/unit/src/utils/http.test.js
npx playwright test --config=playwright.rpc-model-testing.config.js
npm run test:integration:modal-form-rendering
npm run lint
npm run build
```

真实模式先设置 `RPC_TESTING_BACKEND_URL`、`RPC_TESTING_FRONTEND_URL`、`RPC_TESTING_TARGET_STUB_URL`、`RPC_TESTING_APP_NAME`、`RPC_TESTING_APP_WORKSPACE`、`RPC_TESTING_USERNAME`、`RPC_TESTING_PASSWORD`，再运行同一个 Playwright 配置。目标 stub 由 spec 启动；真实后端和 main-app 必须已启动，webpack API 指向相同后端。环境值不完整立即失败；不允许回退 mock。

本次隔离实例使用 18180 / 18181 / 18182，应用 `rpctesting20261114`。真实日志与截图位于 worktree 的 `tmp/testing-evidence/rpc-model-testing/`；`live-journey.json` 包含前后 commit、提交文件、资源状态与目标请求。数据库和私密 fixture 配置属于本地证据，不纳入源码提交。

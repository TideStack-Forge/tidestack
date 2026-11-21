# Fixtures

`testing/common/fixtures/` 存放可复用的测试夹具定义，用来组合拉起服务、运行时、前端壳、测试数据和身份上下文。

## 分类

- `services/`: LDAP、OIDC、MySQL、Redis、RabbitMQ 等
- `runtimes/`: `develop`、`mothership`、纯业务 runtime
- `frontends/`: 壳应用、模块级 harness 宿主页
- `data/`: 元数据仓、业务种子数据、组织数据
- `identities/`: 测试账号、角色、组织上下文

## 一个正式夹具的最小资产

建议包含：

- `fixture.yaml`
- `README.md`

如果需要脚本入口，可再提供：

- `up`
- `check`
- `reset`
- `down`

短期允许这些动作通过旧脚本包装实现，长期应被统一 runner 识别。

当前统一 harness runner 已支持读取这些 lifecycle 字段并输出标准化计划/执行证据。即使某个 fixture 暂时没有声明脚本命令，也应显式留空，让计划里能看到它是 `skipped` 而不是“被遗忘”。

为了避免把人工步骤误执行成 shell，当前约定如下：

- 纯文本字符串表示“描述性 lifecycle”，会在 harness 计划里标记为 `manual`
- `shell: <command>` 表示可执行命令
- 对需要同时写说明和命令的情况，建议使用对象形式：
  - `{"shell": "...", "description": "..."}`

## 设计要求

1. 夹具必须明确输入和输出
2. 夹具不能把业务场景硬编码进去
3. 夹具要能被多个场景复用
4. 夹具健康检查必须可自动判断
5. 夹具重置语义必须清晰
6. 公共夹具名称必须描述通用能力，不能直接用具体项目实例名命名
7. 如果关键信息来自 CLI / 环境变量 / 外部目录，必须写进元数据结构，不能靠口头约定补

## 当前约定

为了让 runner 能稳定解析，当前 fixture manifest 约定：

- `id` 使用 `<class>/<name>` 形式，例如 `runtimes/develop`
- `class` 必须与所在目录一致
- `outputs` 使用对象数组，最少包含 `name` 和 `value`
- 若输出来自外部输入或派生关系，可使用：
  - `fromInput`
  - `fromOutput`
  - `join`
- `inputs` 可声明 `sources`，显式说明取值来源，例如 `cli` 或 `env`
- 场景 runner 可以把 fixture `outputs` 作为默认输入来源，但场景真相仍以 `scenario.yaml + contract.md` 为准

## 预检约定

fixture 不是纯文档资产，进入正式场景后必须能被 runner 预检。

当前默认预检包括：

- `data`: `appRoot` 这类必需输入的可解析性，以及 `businessModelDir`、`uiSchemaDir` 这类派生路径存在性
- `identities`: 如果内嵌 `loginPayload`，则至少包含 `account/password`
- `runtimes`: `baseUrlEnv`、`unauthenticatedProbe` 和解析后的 base URL 形态

如果某个 fixture 故意不在仓库里保存真实凭据或运行时地址，应在 `outputs`/`prerequisites` 中明确表达“外部注入”，让 runner 能把它标成可解释的待注入状态，而不是静默失真。

# RPC 模型测试用例映射

测试实现位于所属 Java 模块与 `frontends/web/main-app/tests/`，本表不复制执行器。组件验证、AMIS transport harness 和真实后端旅程分别取证；本表列出的范围不等于每个边界都由真实部署覆盖。

| ID | 判据 | 测试归属 |
| --- | --- | --- |
| RPC-T01 | dirty、恢复原值、保存前禁测 | rpcModelEditorState.test.js；rpc-model-editor.spec.js；真实 journey |
| RPC-T02 | 保存、初始化失败、草稿保留 | state 单测；初始化 hash 对比；提交失败浏览器分支 |
| RPC-T03 | 新建重复保存零 commit | RpcModelWorkspaceSaveTest；state 单测；真实 journey |
| RPC-T04 | 重复保存、旧初始化、卸载后锁响应 | state 请求时序与迟到响应单测 |
| RPC-T05 | 编辑/调试入口、页签保留 | AMIS 浏览器；同一 add/edit Schema 与 initialTab；真实列表入口 |
| RPC-T06 | 短变量、属性、计算、索引 | SpElInputAnalysisTest；RpcTemplateTest；RpcModelTestServiceTest |
| RPC-T07 | 默认配置、显式空、并发隔离 | RpcModelTestServiceTest.emptyOverridesAndConcurrentConfigViewsNeverModifyDefaults；输入浏览器 |
| RPC-T08 | 全部内置认证与令牌缓存 | RpcModelTestServiceTest Basic/Plain/static/dynamic/HMAC；runtime 认证回归 |
| RPC-T09 | 应用同名资源隔离 | RpcModelTestServiceTest.snapshotIsScopedToApplicationEvenForIdenticalNames |
| RPC-T10 | 模型/认证/令牌/分支冲突 | service changedDependencies、dynamicToken、unstableReads；零目标计数 |
| RPC-T11 | JSON/204/非2xx/解码/超时/TLS | service actualNonSuccess、noContent、responseDecode、transientFailures；protocol 回归 |
| RPC-T12 | 逐次重试、恢复成功、总预算 | service actualNonSuccess、transientFailures、responseDecode；单 POST 浏览器 |
| RPC-T13 | 求值、请求、认证、环失败 | service disabledModelsCycles、requestLimit；Expression 模板测试 |
| RPC-T14 | 应用查看者拒绝 | DevAppEditAccessFilterTest，初始化与执行均为 POST |
| RPC-T15 | 脱敏、错误、认证正文 | service HMAC、Basic/Plain/token、JSON 敏感字段；密码输入浏览器 |
| RPC-T16 | 保存/测试不提交，显式提交范围 | RpcModelWorkspaceSaveTest；真实 live-journey.json |
| RPC-T17 | 请求上限、预览/UTF-8 截断、响应上限 | service requestLimit、noContentTruncation；redactor 安全省略 |
| RPC-T18 | 输入过期、401 不重放、关闭 | state stale/dispose；HTTP client 401 单测；浏览器关闭分支 |
| RPC-T19 | 普通运行治理与其他模型回归 | runtime 全套；PreparedModelCreateTest；modal-form-rendering 浏览器 |
| RPC-T20 | 按钮及提交信息验证 | RpcModelWorkspaceSaveTest；ModelDevelopCommitTest；AMIS 浏览器 |
| RPC-T21 | 分步成功、失败仅重试提交 | state commitFailure / commitResponseLost；AMIS 浏览器；真实 journey |
| RPC-T22 | 锁生命周期、丢弃只改草稿、原策略 | state lock/dispose；AMIS X/ESC/关闭；PreparedModelCreateTest；资源锁集成 |
| RPC-G01 | 选择已有分组、新建路径、清空并保存，模型文件及名称不变 | RpcModelWorkspaceSaveTest；rpc-model-editor.spec.js |
| RPC-G02 | 高代码与文件声明使用同一分组路径；兼容未分组及一级字面量，拒绝空层级 | RpcModelGroupingTest；RpcRuntimeRegistryServiceImplTest；RpcRuntimeControllerTest |
| RPC-G03 | 开发与运行列表采用左侧分组树、右侧模型列表，父分组包含子分组，搜索与分组联动，未分组和全部模型可切换 | rpc-model-groups.test.js；rpc-model-editor.spec.js |
| RPC-G04 | 模型选择弹窗采用左树右表，只从右表确认模型名称，取消不改原值；保留自定义来源映射、元数据装配优先级及 select/raw 接口合同 | DevAppMetadataControllerTest；propertyTranspilerSources.test.js；rpc-model-editor.spec.js |
| RPC-G05 | 已存模型名称回显标题，空集合仍可浏览全部/未分组并显示空态 | rpc-model-groups.test.js；rpc-model-editor.spec.js |
| RPC-G06 | 刷新重建完整分组树，分页不裁掉分组，换组重置页码，窄屏左右布局不撑破页面 | rpc-model-groups.test.js；rpc-model-editor.spec.js |

真实部署证明主要路径与 Git/锁状态；全部认证、资源冲突和故障注入由组件测试证明。第三方认证、非 OpenAPI 协议、浏览器强杀后可靠解锁、生产网络连通性及远端副作用撤销不属于通过范围。

## 字段语义与配置依赖修复验收

| ID | 判据 | 测试归属 |
| --- | --- | --- |
| SEM-01 | 完整模型按 RpcFieldSemantics 分析，配置字段与 JSONPath 不作为 SpEL，普通文本不解释 | RpcModelTestServiceTest.semanticsDiscoversTopLevelConfigurationAndIgnoresNonTemplateLeaves / semanticsJsonPathsAndDescriptionsAreNeverParsedAsVariableExpressions；RpcConfigurationTemplatesTest |
| SEM-02 | 配置化认证、模式与令牌模型可解析和切换 | ServiceTest semanticsResolvesConfiguredCredentialAndModeBeforeCollectingDependencies / semanticsConfiguredModeAndTokenModelChangesInvalidateBeforeNetworkAndPruneInputs；真实 journey |
| SEM-03 | 缺项可补全，初始化不构建认证、不发送任何请求；不回显秘密 | ServiceTest semanticsInitializationDoesNotConstructConfiguredCredentials / semanticsMissingStructuralConfigCanBeCompletedWithoutAnyInitializationCalls；RpcModelTestRequestTest；真实 journey |
| SEM-04 | 实际依赖进入 revision，普通秘密不进入版本；一次配置读取一致；保留应用与调用隔离 | ServiceTest semanticsConfigurationIsMemoizedAndOrdinarySecretsAreNotVersions，加 RPC-T07/09/10；真实 stale revision 零调用 |
| SEM-05 | 覆盖变更立即禁测、自动重建，保留有效值并移除旧输入，旧响应不能启测 | state 单测；AMIS typed inputs；真实 journey |
| SEM-06 | 保存零 commit；显式提交需信息并关闭；权限与旧弹窗保持原机制 | RPC-T03/14/16/19/20/21/22 |

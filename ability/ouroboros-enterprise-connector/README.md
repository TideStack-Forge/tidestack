# 企业连接基础

登录、审批、目录、通知和 RPC 共用企业连接及协议基础；业务状态仍归各领域所有。已从原审批集成提取并接通各领域调用，完整实施状态见 [方案](../../docs/design/cross-cutting/enterprise-collaboration-integration.md) 和临时实施清单。

## 模块归属

| 模块 | 职责 |
|---|---|
| core | 企业连接 Provider SPI、安全快照、内存 typed credential、认证 profile 端口、有界 token cache、已验证业务事件及协议/订阅合同 |
| runtime | 原配置快照解析、部署级渠道基数、凭据加密与受保护管理策略、托管 SecretResolver、认证 profile 桥接、统一业务回调分发 |
| dingtalk / wecom / feishu | 各平台凭据解析、字段合同、登录及回调协议；不依赖审批模块 |
| manage | 独立管理展示适配，按 Provider 合同贡献配置页，不增加 UI Model 的集成语义 |

审批 Provider 不再声明公共连接字段或凭据类型，而是消费 `ResolvedEnterpriseConnection`。公共 schema 的唯一来源为 `EnterpriseConnectionProviderRegistry`；安装模块通过 `META-INF/services/com.ouroboros.enterprise.EnterpriseConnectionProvider` 注册。各自动配置同时登记于现有 Spring 注册文件。

## 托管凭据

`enterprise.connections` 继续保存非敏感连接；`enterprise.credentials` 在原配置值表中保存每连接一条 AES-256-GCM 密文。两项均为受保护的 BACKEND 配置，通用配置编辑、脚本读取及前端配置输出不开放。管理员管理读取只返回凭据配置状态和版本。

主密钥通过部署配置传入，所有节点使用相同 key ring，密钥本身不写入配置数据库：

```yaml
ouroboros:
  enterprise:
    credentials:
      active-key-id: primary
      keys:
        primary: ${OUROBOROS_ENTERPRISE_PRIMARY_KEY}
```

该环境变量须为 base64 编码的 32 字节 AES 密钥。缺失主密钥允许模块启动，但保存或使用托管凭据时明确失败，不回退为明文。解密保留旧 keyId 所需的密钥。主密钥轮换使用受控命令 `POST /api/sys/enterprise-connections/credentials/reencrypt`，提交连接和凭据两个配置项的 `expectedRevisions`，要求查看与换密钥权限。命令沿原配置事务/CAS 重加密所有记录，保留应用凭据版本，因此不会把纯存储加密变更误当成应用密钥轮换。连接作为只读依赖锁定并校验版本，不为重加密伪造连接变更。先让各节点具备新旧密钥，再统一切换 active key、运行命令并核对库中 keyId；确认备份恢复也不依赖旧 key 后才移除旧密钥。专用双节点仿真已验证重加密、移除旧 key 及实际重启后的恢复；存量生产部署仍须按实际 key ring 和备份验证。

`managed:<connectionId>` 由原安全层 CompositeSecretResolver 的模块贡献解析，包括原 JSON Pointer 投影。`env:`、`config:`、`file:` 仍沿原语义读取；迁入数据库需显式替换并核对原应用身份，明文不传回浏览器。

连接与凭据由同一专用命令通过原配置多项事务一次提交，两个 key 均要求 expectedRevision。省略凭据保留原值；替换生成新 nonce 和 credentialVersion；企业或应用身份变更不允许冒充密钥轮换。加密附加数据绑定 provider、connectionId 及版本，复制或篡改密文会导致验证失败。停用要求专用权限和明确确认，管理员处理在途业务及供应商侧配置。

每次使用托管连接核对数据库权威快照，有限重读连接/凭据版本，避免跨节点使用过期缓存或把两次发布混合。认证 profile revision 纳入 credentialVersion。三家应用 token 换取已归各供应商的 `EnterpriseApplicationTokens` 实现；审批和登录成员解析委托同一实现及共享缓存。缓存按 provider/连接/技术用途/权威 revision 和外部稳定凭据隔离，键不保存密钥原文；刷新等待、token HTTP 与后续业务 HTTP 共用单调 deadline。旧请求晚到不写入新分区；失效通知只清除被拒绝的 token，不误删刚刷新的值。RPC、目录和通知已接入同一应用 token 与版本复核；多节点真实部署轮换仍未验收。

企业微信已补自建应用凭据和网页授权协议，与原第三方套件实现分离；官方接口、返回字段和身份边界见 [合同核对](../../docs/references/wecom-self-built-auth-contract.md)。三家纯登录不再强制业务回调材料，回调调用处仍明确校验所需字段。连接型登录的临时绑定证明记录 profile revision；回调处理、读取绑定证明及账号密码验证后都重新核对，停用或应用密钥轮换后不能消费旧证明。

缺失插件的旧配置只保留在受保护存储中，管理投影显示不可用状态；安装插件的受控更新原样保全其他缺失插件记录。缺失插件不能被编辑或用于运行时解析；已安装插件仍按原完整 schema 校验。

连接管理页的“凭据维护”展示孤立凭据候选。确认后调用 `POST /api/sys/enterprise-connections/credentials/{connectionId}/clear`，要求查看与换密钥权限，并提交连接和凭据两个配置版本。原配置事务内重新核对所有连接标识及 `managed:` 引用；停用连接和缺失插件连接仍受保护。此操作只移除孤立密文，不删除连接、组织绑定或历史业务数据；重复清除不影响其他记录。

## 检查已保存连接

“检查已保存连接”调用 `POST /api/sys/enterprise-connections/{connectionId}/verify`，同时要求查看权限和独立的 `.connections.enterprise.verify` 权限。只检查已保存且已启用的连接，不提交当前表单草稿或新输入的密钥；停用连接仍拒绝发起请求。保存和启用不依赖此检查结果，也不因失败自动停用其他能力。

检查沿三家既有应用令牌换取协议和共享调用预算，直接请求供应商，不读写或清空应用令牌缓存。默认超时 5000 ms，通过 Spring 属性 `ouroboros.enterprise.diagnostics.timeout-ms` 设置。完成时重新核对连接及凭据代次，发生轮换或停用则不能报告当前配置检查成功。响应仅返回连接、供应商、时间和范围说明，不返回令牌或密钥；供应商错误使用安全摘要。

本次成功仅证明当次应用令牌换取通过。登录用户身份、目录可见范围、通知权限、工作台及业务回调仍须分别验收；真实发消息和写 API 沿原业务权限及测试入口执行。

## 共享调用预算

各连接页的“调用预算”维护受保护配置 `enterprise.api-budgets`，例如 `{"feishu": 5}` 表示当前部署飞书渠道每秒最多 5 次请求。保存沿原配置 CAS，使用该配置自己的 revision；不会提交或覆盖连接编辑草稿。要求查看与配置权限，保全其他渠道以及缺失插件的预算。

三家认证与审批 HTTP client、目录/消息 RPC 执行器在每次实际请求前共用一个 `EnterpriseApiBudget`。本地预算拒绝发生在发出请求之前；通知可据此重试，已部分发送仍按结果未知处理。未配置或无法分配预算时拒绝调用。部署通过 Spring 属性 `ouroboros.enterprise.execution-nodes` 指定实际执行节点数，默认 1；每节点按总速率除以节点数保守分配，新增节点前须同步调整。预算变更不会重置已消耗的时间槽。

三家通知的 SPI 项只提供类型、管理描述和托管 Supplier 类型。实际 Adapter、成员解析和 RPC 服务经当前 Spring 容器装配，由原通知 Center 在消费入口绑定；SPI 缓存不保存这些服务。模板检查和选项反序列化只读取描述，不触发外部请求。

这是节点内准入及静态节点分额，没有持久化每日额度计数器。管理员应结合供应商每秒/每日及接口组限制选择保守速率；需要每日任务上限时沿所属业务调度约束。真实多节点总速率与供应商额度仍需部署验收。

## 业务回调

统一入口为 `GET/POST /api/enterprise-callbacks/{connectionId}/callback`。请求只由连接派生 provider，协议插件负责验签、时间窗、解密和 challenge/ACK；`EnterpriseCallbackSubscriber` 通过 Spring 自动配置贡献稳定订阅 ID、provider 和精确事件类型。普通 Bean 实现随所属模块原配置装配，安装 JAR 后重启生效；不增加手工路由表或公共事件库。

首批审批订阅为钉钉 `bpms_instance_change/bpms_task_change`、企微 `sys_approval_change`、飞书 `approval.instance.status_changed_v4/approval.task.status_changed_v4`。翻译保留审批事件原 ID、摘要、状态及时间算法，调用原 `ExternalCallbackReceiptService`；所有匹配订阅可靠受理后才返回协议 ACK，失败返回可重投的错误，重投由领域 receipt/inbox 幂等接收。请求限额默认 1 MiB、整体受理预算 2500 ms，通过 Spring 的 `ouroboros.enterprise.callbacks.max-body-bytes/timeout-ms` 设置。首期没有同步命令 handler，飞书卡片交互明确拒绝。

旧三家 Controller 和通用 `/api/approval-workflow/external/{adapterId}/callbacks/{connectionId}` 均退出。发布时停止旧地址投递、保留原审批 inbox/任务，部署后将供应商后台地址切换到新入口并验证 challenge 与事件受理；回退时按相反顺序切换，不能同时向两个地址投递。原 `ExternalCallbackCodec` 端口仍供领域能力派生和非 HTTP 合同使用，协议部分委托同一基础实现，不保留第二份验签。旧钉钉专属 receipt 实现随入口退出，历史表不自动删除；审批 inbox 仍按原事件键幂等。新 receipt 以稳定事件身份代替一次传输的 nonce，因此首次接收旧事件可能新增 receipt，但不会重新处理已有 inbox 事件。

钉钉默认保留旧企业级回调的 CorpID framing；在应用后台配置 HTTP 事件订阅时，明确选择 `callbackMode=APPLICATION`，使用 AppKey framing。响应使用签名及加密 `success` JSON，不能返回明文。见[协议核对](../../docs/references/enterprise-callback-protocol-contract.md)。管理页从后端读取业务回调地址，复用原公开平台地址配置；没有公开地址时仅展示路径，不从不可信请求 Host 推断公网地址。

## 验证边界

本次内部机制按[外部服务仿真场景](../../testing/integration/feature/enterprise-integration/simulated-platforms/contract.md)验收，使用两个正式后端进程和专用 MySQL/Redis；完整流程及清理已通过。真实企业联调、维护迁移及生产节点演练按[企业集成验收](../../docs/runbooks/enterprise-integration-acceptance.md)逐项核对，所需环境、账号与授权范围列在该清单中。

已增加连接隔离/停用/基数、密文完整性、受控投影、应用身份轮换、独立权限、多节点读取、共享 token、截止时间及缺失插件保全测试，并保留原审批链路回归。连接管理页面、绑定证明失效、主密钥重加密和孤立凭据引用保护均已有实现。统一回调迁移、目录绑定/同步及到期快照清理、三家通知及终态内容清理、工作台入口、企业 RPC 和共享预算均已有本地行为测试。原配置多项事务的整笔回滚、目录持久化和通知增量迁移/清理已在隔离 MySQL 8.3 验证，双 Spring 容器共享 Redis 缓存验证通过。完整连接删除不在当前 API 中开放；存量生产数据迁移、生产节点轮换及真实企业联调仍需发布验收。完整本地检查及发布待验边界见[方案 §15.1](../../docs/design/cross-cutting/enterprise-collaboration-integration.md#151-本地验证与发布待验)。

本地 HTTP mock 和装配测试不代表真实企业授权、回调或通知已联调。配置、目录及通知的数据库组件测试可通过 `OUROBOROS_BEHAVIOR_MYSQL_URL/USER/PASSWORD` 指向专用测试库；配置双容器缓存测试另需 `-Dnotification.redis.port=<本机测试端口>`。审批数据库集成沿原模块的 `mysql-integration` profile 执行。测试库必须隔离，缺少条件而跳过的用例不记为业务通过。

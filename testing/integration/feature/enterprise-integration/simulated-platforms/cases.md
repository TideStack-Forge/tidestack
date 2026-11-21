# 仿真验收用例

前置条件：正式 Mothership 和生产前端已构建，Docker 中已有运行所需镜像；场景 runner 创建独立数据库、缓存、证书、三方服务和两个正式节点。场景按 `scripts/run.py` 编排执行；用户、组织及连接通过正式服务/API 创建，不直接写业务表。原始证据位于 `tmp/testing-evidence/enterprise-simulation/`。

| ID | 分支 | 通过标准 |
|---|---|---|
| EIS-CONNECTION-001 | normal / dependency | 三家连接沿原管理 API 保存托管凭据并启用；真实 HTTPS 令牌诊断成功，读取无明文 |
| EIS-AUTH-001 | normal / state | 三家一次性授权→未绑定→账号密码绑定→再次登录；原目标恢复，管理员解绑后不以目录关系回退 |
| EIS-AUTH-002 | invalid / recovery | 错误密码、错误 state、授权码重放、轮换/停用前的绑定证明被拒绝，不新增绑定 |
| EIS-DIRECTORY-001 | normal / state | 三家分页读取、绑定和单向同步，应用后有真实本地部门/成员关系；不同来源不重复建立登录绑定 |
| EIS-DIRECTORY-002 | error / recovery | 分页失败或范围缩减不应用部分快照；主来源、版本冲突和确认解绑沿原规则处理 |
| EIS-NOTIFICATION-001 | normal / dependency | 三家接入测试向当前管理员关联成员发送，仿真外部收到正确连接、成员和固定正文 |
| EIS-NOTIFICATION-002 | error / recovery | 限流/明确失败与写入后断连分别处理，未知结果不自动重复外部写入，任务结果可核对 |
| EIS-CALLBACK-001 | normal / state | 三家独立构造的签名/密文挑战和审批事件经统一入口受理；合法 ACK，重投不重复领域事实 |
| EIS-CALLBACK-002 | invalid / recovery | 错签名、错误接收者、过期或超限请求、停用连接被拒绝；未可靠受理不成功 ACK |
| EIS-RPC-001 | permission / boundary | 安装的三家通讯录/消息包从原运行入口调用；模型管理权不代替调用权，令牌目标固定且证据脱敏 |
| EIS-NODE-001 | concurrency / transition | 两个正式节点共享库/Redis；轮换后两节点使用新代次，停用后两节点无外呼，同版本并发保存恰一成功 |
| EIS-RECOVERY-001 | dependency / recovery | 正式节点重启后配置/绑定/目录/回调事实保留；应用主密钥重加密保留凭据代次 |
| EIS-NOTIFICATION-003 | permission | 集成不增加通用 HTTP 发送权限，直接调用原未授权发送接口被拒绝且无外部消息 |
| EIS-NOTIFICATION-004 | normal / state / recovery | 原文件模板、地址解析器、可靠 Dispatcher 和 NotificationService 向三家无 User 员工发送已批准的普通测试通知；SUCCESS/UNKNOWN 与尝试次数实际入库，新 JVM 重跑不重复发送 |
| EIS-SIM-001 | invalid / dependency | 仿真服务拒绝错误密钥、串渠道令牌、错误 PKCE 和授权码重放；单次故障注入只失败一次，不能用宽松假服务掩盖产品错误 |
| EIS-BUDGET-001 | boundary / concurrency | 两节点按部署总预算分摊，超额 RPC 被拒绝，仿真服务收到的调用不超过两节点总额 |
| EIS-OUTAGE-001 | dependency / recovery | 暂停本场景 MySQL 时三家回调在期限内无成功 ACK；恢复后重投可受理，最终持久事实仍唯一 |
| EIS-KEY-001 | transition / recovery | 两节点切换双主密钥环，原管理 API 重加密；过期连接依赖版本拒绝且两个版本不变；移除旧主密钥并实际重启后仍能解密和调用，credentialVersion 不变 |
| EIS-BROWSER-001 | normal / state | 三家正式绑定页面输入账号密码后进入原工作台目标；新浏览器上下文处理已绑定的一次性登录码后恢复同一目标，不替换前端/平台请求 |

HTTP 场景保存 `internal-mechanisms.jsonl`；仿真服务自身校验、浏览器、数据库故障和主密钥演练各有 `run-*.log`；可靠通知、回调持久化与主密钥代次由 `RuntimeMechanisms.java` 沿原 QueryDSL/领域服务观察，保存 `persistent-*.json`。`summary.json` 只有全部步骤和清理通过才标记 pass；`cleanup.json` 核对资源归属并记录删除结果。不同测试层不相加作为唯一测试数量。

无 User 员工的业务内容披露仍由原 BusinessEventNotificationListener 决定；普通测试通知驱动显式提供批准策略，不代表其获得业务内容权限。该业务身份/披露/地址策略另由原组件 `BusinessEventNotificationListenerTest` 和 `WorkflowNotificationDeliveryIntegrationTest` 验证，证据边界单独记录。

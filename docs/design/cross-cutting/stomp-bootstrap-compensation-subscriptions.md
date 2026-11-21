# STOMP 补偿订阅设计

## 状态

部分实现。后端 init support layer、主应用/SDK subscribeWithInitial 及 subscribe* 的 initial* 选项已有实现，app-logs 与 helper logs 已接入 init 通道；未从源码推断多 broker 验收或全部业务流迁移完成。

## 背景

Ouroboros 当前已经提供基于 Spring Messaging + SockJS + STOMP 的实时通信基座，并沿用 Spring 的典型 destination 分工：

- `/app`：客户端向应用发送请求或命令。
- `/topic`：服务端向多个订阅者广播共享实时流。
- `/queue`：broker destination 前缀之一；在 Ouroboros 当前 runtime 中，也被约定为连接级私有 reply lane 的承载前缀。
- `/user`：服务端向单个用户的所有会话发送私有消息。

现有业务流已经出现一类稳定需求：订阅建立后，客户端需要先拿到一份补偿数据，再继续消费实时增量。

典型场景包括：

- 日志订阅时先回最近 N 条日志，再继续追加新日志。
- 状态订阅时先回当前状态快照，再继续推送状态变化。
- 监控面板重新连接后先追平最近状态，再继续消费实时事件。

如果直接把补偿消息混入共享 `/topic`，会带来几个问题：

- 补偿数据会污染共享广播流，其他订阅者会收到自己没有请求的初始化数据。
- 共享流中同时混入 init 和 live 语义，客户端和排障链路都更难理解。
- 重新连接的单个客户端会放大共享 topic 的流量和噪声。
- 多实例下更难维持 "当前连接的补偿" 与 "所有连接共享的 live" 的边界。

因此需要一套可复用的补偿订阅能力，在不破坏现有 destination 分工的前提下，为上层提供更顺手的单一订阅 API。

## 目标

- 为前端调用方提供单一逻辑订阅 API。
- 保持现有 `/app`、`/topic`、`/queue`、`/user` 语义不变。
- 默认将补偿数据建模为连接级私有返回，而不是共享广播，也不是用户级多会话广播。
- 避免 init 和 steady-state live 之间出现事件丢失窗口。
- 复用现有 `StompMessageSender`、`StompMessageSubscriber` 和主应用 STOMP runtime。
- 当前能力边界是 single-instance simple broker；Redis pub/sub 与 RabbitMQ relay 的多实例效果仍需独立验证。

## 非目标

- 不重定义 STOMP 协议本身。
- 不引入持久化重放、事件溯源或长期历史存储。
- 不把所有实时业务统一成一套强制共享 payload DTO。
- 不把 `/topic` 改造成既承载共享 live 又承载定向补偿的混合通道。
- 不把 `/user` 作为默认补偿通道。

## 设计结论

推荐设计为：

- 逻辑上单一订阅 API。
- 物理上多通道协作。

每个逻辑订阅由三条 lane 组成：

- live lane：该 flow 的 canonical live destination；多数 flow 仍是共享 `/topic`，app-logs pilot 当前为 `/user/queue/app/{appName}/logs`。
- init request lane：默认直接由完整 live destination 派生，即 `/app${liveDestination}/init`；必要时允许显式 override。
- init reply lane：默认直接由完整 live destination 派生，即 `/queue/init${liveDestination}/{token}`。

其中：

- live lane 长期存在，承载后续所有共享增量。
- init reply lane 只在初始化阶段存在，收到完成标记后关闭。
- init request lane 用来触发补偿计算，仍保持 Spring Messaging 的应用处理语义。

## 当前状态与目标状态

当前已实现前端 subscribeWithInitial(destination, listener, options)，并可通过 subscribe/subscribeTopic/subscribeQueue/subscribeUserTopic 的第三个参数传 initial* 选项。主应用 runtime 负责 cycle、token、过期帧过滤、completion 清理和重连新周期；SDK 类型与导出面已同步。

后端已有 InitRequest、InitRequestContext、InitRouteDefinition、InitReplyFrame、InitReplySender、InitRequestHandler 与 StompInitSupport，以及相应 runtime 默认实现。app-logs 和 helper logs 已使用 init request/reply 通道，其余业务流是否迁移须逐项确认。Redis 与 RabbitMQ 的多实例路由效果仍需独立运行证据。

下文的三通道、顺序和消息边界是共同设计合同；尚未接入的业务流以此为目标。不能把接口存在、历史 pilot 记录或单实例通过等同于所有场景已发布或验证。

## 为什么这样设计

### 为什么补偿不能混入共享 `/topic`

共享 topic 的职责是向多个订阅者广播同一条实时事实流。补偿数据并不满足这个前提，因为它通常具有以下特征：

- 它只对发起订阅的那个客户端有意义。
- 它的内容、大小和生成时间点都可能因订阅者而异。
- 它常常代表一个初始化视图，而不是共享事实事件。

把补偿混入 `/topic` 会让 topic 同时承担：

- 共享广播语义。
- 定向初始化语义。

这是不必要的语义混流。Spring Messaging 提供了 application destination 与 broker destination 的职责分离能力，而本设计据此进一步约定：不同责任的消息不应依赖“同一条通道里再靠业务字段区分”。

### 为什么默认不用 `/user`

`/user` 解决的是“发给某个用户”，不是“只发给当前连接”。

当同一个用户同时登录多个客户端或多个 tab 时，如果把补偿发到 `/user`：

- 一个 tab 的补偿请求可能把补偿数据送到另一个 tab。
- 多个连接会共享初始化噪声。
- 请求-响应的边界变弱，排障时更难确认是哪次订阅触发了这份 init 数据。

因此默认应优先使用连接级 reply lane。对 Ouroboros 当前 runtime 来说，这条 lane 落在 `/queue` 前缀之下，并依赖现有 session envelope 与订阅改写机制完成连接级路由。只有当业务明确要求“同一用户的所有在线端都要拿到同一份补偿结果”时，才考虑 `/user` 作为显式例外。

### 为什么必须先订 live 再触发 init

如果顺序是：

1. 请求 init。
2. 收到 init。
3. 再去订 live。

那么 init 生成完成到 live 订阅生效之间会有一个时间窗。这个时间窗内发生的新事件，既不在旧快照里，也没有被 live 流接住，会直接丢失。

正确顺序必须是：

1. 先订阅 live topic。
2. 再订阅 init queue。
3. 再发送 init request。

运行时按同一会话保序接收入站消息并保序发布出站消息，保证上述订阅、请求以及 init-data、init-complete 的处理顺序。协同画布的实时链路测试通过实际 SockJS/STOMP 会话验证这条路径。

这样 live 流已经接上，init 只是补一段初始化数据，不会形成事件缺口。需要注意，这个顺序只能消除“订阅空窗”，不能自动保证严格无重无漏；如果某条业务流要求快照与增量之间做精确 merge，仍需要在 payload 中引入 sequence、version 或 watermark 等业务边界字段。

## 通道模型

### 逻辑视图

```text
逻辑订阅能力
└── subscribeWithInitial(...)
    ├── live lane: /topic/...
    ├── init request lane: /app/.../init
    └── init reply lane: /queue/init/.../{token}
```

### 物理视图

```text
Client Runtime
  |-- SUBSCRIBE --> /topic/{live}
  |-- SUBSCRIBE --> /queue/init/{resource}/{token}
  |-- SEND -------> /app/{resource}/init
  |
  |<-- MESSAGE ---- /queue/init/{resource}/{token}   init-data
  |<-- MESSAGE ---- /queue/init/{resource}/{token}   init-complete
  |
  |-- UNSUBSCRIBE -> /queue/init/{resource}/{token}
  |
  |<-- MESSAGE ---- /topic/{live}        live event
```

## 时序

### 文字时序

以 `room/123` 的消息流为例：

1. 前端调用一个逻辑 API，例如 `SDK.Stomp.subscribeWithInitial(...)`。
2. 运行时确保 STOMP 已连接。
3. 运行时先订阅固定 live topic：`/topic/rooms/123/messages`。
4. 运行时为本次订阅生成一个临时 init token，并订阅私有 queue，例如 `/queue/init/rooms/123/messages/{token}`。
5. 运行时向 `/app/rooms/123/messages/init` 发送 init request；当前已实现 contract 中，`subscriptionToken` 通过 STOMP headers 传递，请求体只承载业务参数，例如 `limit`、`cursor`、过滤条件等。
6. 服务端收到 `/app` 请求后，从 STOMP header 中拿到 `sessionId`，并验证：
    - 当前用户是否有权限订阅 room 123。
    - header 中 `subscriptionToken` 的 shape 与 init reply lane 命名是否在允许的 namespace 内。
7. 服务端计算 init 数据。
8. 服务端根据固定的服务端命名规则，由业务 scope + header 中的 `subscriptionToken` 推导当前 cycle 的 init reply lane，例如 `/queue/init/rooms/123/messages/{token}`，而不是信任客户端直接传入任意 queue path。
9. 服务端通过 `StompMessageSender.sendToQueue(sessionId, initQueue, payload)` 连续发送一批 `init-data` 消息。
10. 服务端通过同一个 queue 再发送一条 `init-complete` 消息；失败时发送 `init-error`。
11. 前端运行时在 init queue 上处理这些消息，并把它们交给统一 listener 或 merge 逻辑。
12. 当前端收到 `init-complete` 后，自动关闭临时 init queue 订阅。
13. live topic 继续保留，后续所有增量都从 `/topic/rooms/123/messages` 持续到达。

### reconnect / init cycle 语义

`subscribeWithInitial` 必须把每次 init 视为一个显式 cycle，而不是把 reconnect 简化成“把旧订阅接着用”。

- reconnect 会启动一轮新的 init cycle。
- 每一轮 init cycle 都必须生成新的 `subscriptionToken`，并推导出新的 init reply lane destination。
- 旧 cycle 的 init reply lane 不能被复用，即使业务 key 没变也一样。
- 客户端 runtime 必须只接受当前有效 cycle 的 `init-data`、`init-complete` 和 `init-error`。
- 如果旧 cycle 的消息或 completion 因网络、broker 或重试而迟到，客户端 runtime 必须忽略它们，不能污染新 cycle。
- `bootstrapDone` 与整体 `unsubscribe()` 这些运行时行为，都只应作用于当前有效 cycle。

### ASCII 时序图

```text
Client Runtime               App Handler                  Broker / Runtime
     |                           |                               |
     | SUBSCRIBE /topic/live     |                               |
     |-------------------------->|                               |
     |                           |                               |
     | SUBSCRIBE /queue/init/.../T|                              |
     |-------------------------->|                               |
     |                           |                               |
     | SEND /app/.../init        |                               |
     | headers:{subscriptionToken:T}                        |
     | body:{...business payload} |                               |
     |-------------------------->|                               |
     |                           | resolve sessionId             |
     |                           | compute init                  |
     |                           | sendToQueue(sessionId,        |
     |                           |   /queue/init/.../T, part-1)  |
     |                           |------------------------------>|
     |<-----------------------------------------------------------|
     | init-data                |                               |
     |                           | sendToQueue(sessionId,        |
     |                           |   /queue/init/.../T, complete)|
     |                           |------------------------------>|
     |<-----------------------------------------------------------|
     | init-complete            |                               |
     | UNSUBSCRIBE /queue/init/.../T|                            |
     |-------------------------->|                               |
     |                           |                               |
     |<------------------------------ /topic/live ---------------|
     | live event                |                               |
```

## 命名约定

### Live lane

live lane 一般走 `/topic`，但已落地 flow 可以按业务边界收敛为受控私有 lane。示例：

- `/user/queue/app/{appName}/logs`，当前 app-logs pilot 已采用的受控 live lane
- `/topic/rooms/{roomId}/messages`
- `/topic/runtime/{runtimeId}/status`

### Init request lane

init request lane 默认走 `/app${liveDestination}/init`，也可以在 registrar DSL 中显式覆盖；示例：

- `/app/user/queue/app/{appName}/logs/init`
- `/app/rooms/{roomId}/messages/init`
- `/app/runtime/{runtimeId}/status/init`

### Init reply lane

init reply lane 固定走 `/queue/init/.../{token}`，示例：

- `/queue/init/user/queue/app/{appName}/logs/{token}`
- `/queue/init/rooms/{roomId}/messages/{token}`
- `/queue/init/runtime/{runtimeId}/status/{token}`

这里要求携带 `subscriptionToken`，由客户端和服务端依据固定规则共同推导出 queue destination，而不是让前端完全自由传入任意 queue 路径。当前已实现 contract 中该 token 通过 STOMP headers 传递，request body 可以只包含业务 payload。target-state contract 要求最终以服务端命名规则为准，后端只能接受 token 和业务 scope，不应信任任意客户端自报的 init reply lane path。

### 术语标准

本文对 STOMP 补偿订阅采用以下规范词汇：

- `live lane`：共享实时通道，命名保持不变。
- `init request lane`：客户端向服务端发起补偿请求的应用通道。
- `init reply lane`：服务端向当前连接返回补偿数据的私有通道。
- `init-data`：补偿数据帧。
- `init-complete`：补偿完成帧。
- `init-error`：补偿失败帧。

历史文档中出现的 `bootstrap request lane`、`bootstrap reply lane`、`bootstrap-data`、`bootstrap-complete`、`bootstrap-error`，以及混用的 `initial` 表述，都应视为 legacy wording。后续文档统一以 `init` 为规范根命名。

## 消息控制约定

不强制立即抽成共享 Java DTO，但建议先收敛一套轻量控制 envelope 约定。最小字段建议：

- `subscriptionToken`
- `phase`：`init-data`、`init-complete` 或 `init-error`
- `payload`
- `correlationId`：可选
- `schemaVersion`：可选

如业务流需要更强一致性，还应在业务 payload 中自行补充：

- `version`
- `sequence`
- `watermark`
- `lastUpdatedAt`

这些字段属于具体流契约，不应强塞到通用 transport SPI 中。

## 后端设计

### 已落地的 support-layer contracts 与 runtime defaults

当前 backend 已经落地一套可复用的 init support layer：

- core contracts：
  - `InitRequest`
  - `InitRequestContext`
  - `InitRouteDefinition`
  - `InitReplyFrame`
  - `InitReplySender`
  - `InitRequestHandler`
  - `StompInitSupport`
- runtime defaults：
  - `DefaultInitReplySender`
  - `DefaultInitRequestValidator`
  - `DefaultInitRequestContext`
  - `DefaultStompInitSupport`
- wiring path：
  - `OuroborosWebSocketAutoConfiguration`
- transport primitives：
  - `StompMessageSender.send(...)`
  - `StompMessageSender.sendToQueue(sessionId, ...)`

这意味着本文不再只是“建议增加 helper”。backend contract 本身已经存在，adopting flow 需要做的是接入这套 contract，而不是在控制器里重复实现 frame/header/protocol boilerplate。

其中：

- `InitRequest` / request-entry contract 需要让 handler 拿到 `subscriptionToken`；当前已实现 flow 通过 STOMP headers 提供，而不是要求 request body 暴露该字段。
- `InitRequestHandler<T>` 负责派生 `InitRouteDefinition`，并返回 init payload items。
- `StompInitSupport.init(...)` 负责统一发送 `init-data`、`init-complete`、`init-error`。
- `InitReplySender` + `DefaultInitReplySender` 把 reply 固定落到 `sendToQueue(sessionId, ...)`。
- `DefaultInitRequestValidator` 负责校验 `sessionId`、header 中的 `subscriptionToken`、`/app` request lane 与 `/queue` reply lane namespace。
- `DefaultInitRequestContext` 提供 runtime 默认 context 实现，adopting flow 也可以传入等价实现。

### 主后端 contract 现在锚定在 support layer 与 session 语义

本文的后端设计锚点，首先不是某个具体控制器写法，而是下面这组 support-layer contracts 与语义边界：

- live lane 继续由 `StompMessageSender.send(...)` 发往该 flow 的 canonical live destination；多数 flow 仍是 `/topic/...`，但 app-logs pilot 当前已落到受控 `/user/queue/...` lane。
- init request 由 adopting flow 的 `@MessageMapping("/.../init")` 或等价应用入口接收。
- init route / payload / control frame 语义由 `InitRequestHandler<T>` + `StompInitSupport` 承接。
- init reply lane 必须由 `InitReplySender` 发送到 per-cycle `/queue/init/.../{token}`，默认实现最终落到 `StompMessageSender.sendToQueue(sessionId, ...)`。
- `sessionId` 是 init reply lane 成立的必要条件，没有 `sessionId` 就不能退化成 user-scoped 或 broadcast fallback。

也就是说，target-state contract 的第一性约束是：

- 共享或受控 live 继续走该 flow 既定的 sender 出站语义，不与 init reply lane 混流。
- init reply 必须走 support layer 的 session-scoped queue 语义。
- init request 必须有一个与该 flow 匹配的应用入口，用来接住 `init request lane`，然后委托 support layer 完成 reply lane 派生、校验与 control frame 发送。

### 当前推荐的 request-entry 形态

在当前 Spring Messaging runtime 下，推荐继续保持下面这条 canonical path：

- init request 通过 `@MessageMapping("/.../init")` 或等价应用处理入口接收。
- init reply 通过 `InitReplySender` 发送，默认实现走 `StompMessageSender.sendToQueue(...)`。
- live 继续通过 `StompMessageSender.send(...)` 发到该 flow 的 canonical live destination。

这里要明确：`@MessageMapping("/.../init")` 是当前推荐、也最贴合现有 Spring Messaging 形态的 request-entry form，但它不是本文 contract 的第一性基础。第一性基础仍然是 `InitRequest*` / `InitReply*` / `StompInitSupport` 这些 support-layer contracts，加上 session-scoped reply 语义，以及每个 adopting flow 必须存在一条匹配的 init request entry。

对每一个采用本文 contract 的业务流，上述三件事都必须成套存在，不能只有前端 `initial*` 调用约定，而没有匹配的 init request entry、request DTO、reply lane 派生规则和 control frame 发送语义。

当前 app-logs pilot 的 backend entry 形态是：

- runtime-owned init entry：`/app/user/queue/app/{appName}/logs/init`，由 `StompCompensationRegistrar` + `StompMessageSubscriber` 接管
- frontend caller destination：`/app/user/queue/app/{appName}/logs/init`
- current live lane：`/user/queue/app/{appName}/logs`
- reply lane：`/queue/init/user/queue/app/{appName}/logs/{token}`

当前 registrar DSL 以 `register(destination)` 传入的完整 live destination 作为唯一事实源：

```java
stompCompensationRegistrar
    .register("/user/queue/app/{appName}/logs")
    .initialData(context -> cachedLogs(...))
    .done();
```

如果某条 flow 需要显式覆盖 init request lane，可通过链式可选参数声明：

```java
stompCompensationRegistrar
    .register("/user/queue/app/{appName}/logs")
    .requestDestination("/app/custom/{appName}/logs/init")
    .initialData(context -> cachedLogs(...))
    .done();
```

`requestDestination(...)` 和 `initialData(...)` 都只是配置，真正注册在 `done()` 执行时发生；这两项在 `done()` 之前可以按任意顺序调用。

这里的 override 只影响 request lane，不影响 reply lane；reply lane 仍然继续从完整 live destination 推导。

其中 adopting flow 只负责提供业务 scope、principal、header 中的 `subscriptionToken` 与 request body 中的业务 payload；reply lane 派生、校验与 control frame 发送由 registrar/runtime 统一接管。该接入方式不需要 per-controller frame 发送与 reply-lane 协议样板代码；其他 replay 控制器须逐项确认是否遵守相同合同。

示例形态：

```java
@MessageMapping("/rooms/{roomId}/messages/init")
public void initRoomMessages(
    @DestinationVariable String roomId,
    Principal principal,
    StompHeaderAccessor accessor,
    RoomInitRequest request
) {
  String sessionId = accessor.getSessionId();
  String subscriptionToken = accessor.getFirstNativeHeader("subscriptionToken");
  String initQueue = initQueueFor(roomId, subscriptionToken);

  // 校验 sessionId、业务 scope、header 中 subscriptionToken 的 shape 与 queue namespace

  sender.sendToQueue(sessionId, initQueue, initDataFrame(...));
  sender.sendToQueue(sessionId, initQueue, initCompleteFrame(...));
}
```

### 后端 `init` contract obligations

采用本文 contract 的后端流，必须明确完成以下定义与实现。

#### 1. 定义 init request lane handler

- 每个 adopting flow 都必须暴露一个与该 flow 匹配的 init request entry，用来承接 `init request lane`。
- 在当前 Spring Messaging runtime 里，这个 entry 推荐实现为 `@MessageMapping("/.../init")`，也可以是等价应用处理入口。
- 这个 entry 是对应前端 `init request lane` 的唯一入口，不应继续把 target-state contract 建立在任意 `subscribe`、`replay` 或 user-scoped convenience path 上。
- 如果当前仍处于 pilot 或迁移阶段，可以保留 legacy bootstrap-root 实现，但文档和代码注释都应把它标记为 current state，而不是 target state。

#### 2. 定义 request DTO

- 当前已实现 contract 中，`subscriptionToken` 通过 STOMP headers 携带，不要求 request DTO / body 再重复声明。
- request DTO 仍必须包含该业务流计算 init 所需的业务请求字段，例如 `limit`、`cursor`、过滤条件、租户或 resource scope。
- 是否保留 `initial*` 作为前端选项名是兼容问题，不改变后端 contract 的规范术语。后端设计文档和 handler 语义仍应使用 `init request lane`、`init reply lane`、`init-data`、`init-complete`、`init-error`。

#### 3. 服务端派生 init reply lane

- 后端必须根据固定的服务端命名规则，由业务 scope + header 中的 `subscriptionToken` 派生 init reply lane。
- 后端不得信任客户端直接提供的任意 queue path。
- 一个 init cycle 只能映射到一个包含 `{token}` 的 per-cycle init reply lane destination。
- 同一业务 key 在下一轮 reconnect / retry cycle 中，也必须生成新的 `{token}` lane，而不是复用旧 lane。

#### 4. 解析并强制要求 `sessionId`

- 后端必须从 STOMP header / accessor 中解析 `sessionId`。
- 没有 `sessionId` 时，不能退化到 user-scoped 广播或任意其他 fallback path。
- init reply 必须通过 `sendToQueue(sessionId, ...)` 发送到当前连接。

#### 5. 校验业务 scope 与 token / namespace

- 后端必须校验当前用户或当前连接是否有权访问对应业务 scope。
- 后端必须校验 header 中 `subscriptionToken` 的 shape，以及推导出的 init reply lane 是否落在允许的 namespace 中。
- 后端必须在发送 reply frame 之前完成这些校验，而不是先发数据再补鉴权。

#### 6. 发送规范 control frames

- request path 成功时，后端必须发送一条或多条 `init-data`，然后发送 `init-complete`。
- request path 失败时，后端必须发送 `init-error`。
- live events 仍必须保留在该 flow 的 canonical live lane，不应混入 init reply lane。
- init reply 也不应改走 `/topic/...` 广播，或退回默认 `/user/...` 多会话广播。

### 后端 contract checklist

一个 adopting flow 只有在下面清单全部成立时，才算满足本文的 target-state 后端契约：

- [ ] 已定义匹配该流的 init request entry，当前推荐形态是 `@MessageMapping("/.../init")` 或等价 init request handler。
- [ ] `subscriptionToken` 通过 STOMP headers 传递，request DTO 只承载必要的业务请求字段。
- [ ] 后端通过固定命名规则派生 init reply lane，而不是信任任意客户端 queue path。
- [ ] handler 会解析并要求 `sessionId`。
- [ ] handler 会先校验业务 scope、允许的 namespace 与 header 中 `subscriptionToken` 的 shape。
- [ ] init reply 通过 `sendToQueue(sessionId, ...)` 发送。
- [ ] request path 成功时发送 `init-data` 与 `init-complete`。
- [ ] request path 失败时发送 `init-error`。
- [ ] live events 继续走该 flow 的 canonical live lane，不会与 init reply lane 混流。
- [ ] 一轮 init cycle 只对应一个包含 `{token}` 的 per-cycle init reply lane。

### `StompMessageSubscriber` 的角色

本设计下，不需要再为 init 额外创造另一套平行 SPI。core support layer 已经存在。

`StompMessageSubscriber` 更合适承担的角色是：

- 对 `/app/.../init` 请求流量做观测或策略控制。
- 后续如果多个模块都需要统一治理 init 请求，再在 runtime 层追加 helper，而不是立刻修改 core SPI。

### 最小扩展建议

优先建议：**复用已落地的 support-layer contracts，不新增平行协议层**。

如果后续多个模块还会重复出现 app-logs 之外的接线样板，应继续围绕现有 `InitRequestHandler<T>`、`StompInitSupport`、`InitReplySender` 与默认 validator/context 做薄扩展，而不是回退到每个控制器各自手写 `init-data`、`init-complete`、`init-error` 帧。

## 前端 SDK 与主应用运行时设计

### 当前基础

当前前端 runtime 已经具备：

- `subscribeTopic`
- `subscribeQueue`
- `subscribeUserTopic`
- `subscribe`
- `sendJson`
- `connect` / `reconnect`
- `onStompConnected` / `onStompDisconnected`

并且 `send(...)` 在未连接时会缓存消息，连接成功后再发送；`subscribe(...)` 会先登记订阅，连接成功后再补订。这意味着主应用 runtime 已经具备实现高阶 init 订阅 helper 的基础。

### 当前 API

```ts
SDK.Stomp.subscribeWithInitial(destination, listener, options?)
// 返回 SubscribeWithInitialHandle：{ bootstrapDone: Promise<void>, unsubscribe: () => void }
```

options 使用 initialDestination、createInitialRequest、initialBody、initialTimeoutMs、keepSingleListener、splitInitialHandlers 与 onInitialData/onInitialLive/onInitialComplete/onInitialError。字段权威定义在 SDK src/types.ts，调用示例在[用户指南](../../../frontends/web/main-app/docs/stomp-user-guide.md#subscribewithinitialdestination-listener-options)。当前没有 unsubscribeBootstrap() 或 isInitActive() 句柄方法。

### 主应用 runtime 职责

`frontends/web/main-app/src/utils/Stomp.ts` 是当前实现入口，负责以下语义：

1. 确保 STOMP 已连接。
2. 先订 live topic。
3. 再订 init queue。
4. 再发 init request。
5. 收到 `init-complete` 后自动取消 init queue。
6. 总体 `unsubscribe` 时同时取消 live 和 init（如果 init 尚未结束）。
7. reconnect 时重新生成 init token，创建新的 init reply lane，并自动开启新的 init cycle。
8. 旧 cycle 的迟到 `init-data`、`init-complete` 或 `init-error` 必须按 stale frame 丢弃。

### SDK 导出面与同步要求

`subscribeWithInitial(...)` 已同时出现在主应用与 SDK 暴露面，后续变更需协调维护：

- `frontends/web/main-app/src/utils/Stomp.ts`：维护 canonical implementation 与 cycle 管理、stale frame 忽略、自动重连后的 init 重建。
- `frontends/web/ouroboros-sdk/src/types.ts`：维护 `Stomp` 接口定义与 `subscribeWithInitial` 类型签名。
- `frontends/web/ouroboros-sdk/src/main.ts`：维护 `Stomp` live binding / stub 导出能力。
- 相关文档与测试：补充调用约定、重连 contract、init queue 生命周期，以及对应单元测试。

因此“保持底层 API 不变”是对的，但“新增高阶 API”的实现面不仅在 main-app，还覆盖 SDK 类型、导出与文档层。

### 运行时请求体建议

建议请求体中只显式传业务 payload：

```json
{
  "...": "business request"
}
```

`subscriptionToken` 在当前已实现 contract 中通过 STOMP headers 传递。最终 queue destination 由前后端根据固定约定共同推导，优先避免把任意 queue path 直接交给客户端控制。

### reconnect 与并发 init 语义

`subscribeWithInitial(...)` 的后续维护必须把 reconnect 与并发 init 的行为定义成明确 contract，而不是只停留在“重连后重新发一遍 init request”的描述层面。

最小 contract 建议如下：

- 每一轮 init 都必须生成新的 `subscriptionToken`。
- `init reply lane` 必须是 **per-cycle 唯一 destination**，不能复用上一次 init queue。
- reconnect 后开启的新 init 周期，必须视为新的 cycle；上一轮 cycle 的 completion 或 data 如果迟到，必须被忽略。
- `SubscribeWithInitialHandle.bootstrapDone` 只对**当前有效 cycle** 负责 resolve/reject。
- 如果调用方已执行 整体 `unsubscribe()`，旧 cycle 的后续 frame 必须被静默丢弃。
- 如果业务需要对 init 与 live 做精确 merge，仍应依赖业务 payload 中的 `sequence` / `version` / `watermark`，而不是依赖 reconnect 时序本身。

换句话说，reconnect 不是“恢复原 init”，而是“开启一轮新的 init cycle，并使旧 cycle 失效”。

## 多实例与 broker 策略考虑

### Single-instance Simple Broker

在 simple broker 模式下：

- `/topic` 作为共享 live lane 没有额外分布式成本。
- `sendToQueue(sessionId, ...)` 作为 init reply lane 直接命中本地连接。

这是主方案最简单的验证环境。

### Redis Pub/Sub（后续验证）

Redis 模式下，当前策略是：

- 服务端出站消息通过 `BrokerEnvelopeType` 区分为 `TOPIC`、`USER`、`SESSION`。
- Redis publisher 按 envelope type 分不同 channel 广播。
- 远端实例收到消息后再按 envelope type 做本地分派。

这意味着：

- shared live 必须继续保持 `TOPIC` 语义。
- init reply 必须继续保持 `SESSION` 语义。
- 路由关键字是 `sessionId`，不是 username，也不是 queue 字符串本身。

因此 init reply lane 用 `sendToQueue(sessionId, ...)` 是天然契合当前多实例策略的。这里的 session-scoped 语义不是由 `/queue` 前缀天然提供，而是由现有 `SESSION` envelope、subscription rewrite 与本地 dispatch 共同保证。反过来，把补偿塞进共享 `/topic` 会破坏这层区分。

### RabbitMQ Relay（后续验证）

RabbitMQ relay 模式下：

- `/topic`、`/queue` 由 broker relay 接管。
- `/user` 的行为依赖 Spring 的 user destination 解析与 broker relay 规则。

这里依然推荐：

- live 保持 `/topic`
- init reply 保持连接级 `/queue`

原因不是 RabbitMQ 不支持更复杂模型，而是 init 的真实需求是 connection-scoped initialization，而不是 user-scoped fanout。这里同样应理解为：连接级 reply 语义依赖 Ouroboros runtime 的 session 路由约定，而不是单纯依赖 broker 对 `/queue` 的默认语义解释。

是否在 relay 模式下完全按预期保持这套语义，仍应在验证阶段重点确认，包括 init reply 是否只回到请求会话、旧 cycle 迟到消息是否会被正确忽略，以及 session-scoped routing 在 relay 下是否与 simple/Redis 模式保持一致。

## 正确性要求

### 必须满足的顺序约束

- 必须先订 live topic。
- 必须在发送 init request 前订好 init queue。
- 必须有显式 `init-complete`。
- 默认在收到 completion 后立即关闭 init queue。
- reconnect 后必须生成新的 init token，并使上一轮 init cycle 失效。
- init queue destination 必须保证 per-cycle 唯一，避免误伤其他 listener。

### 不保证的内容

该通用模式不自动保证：

- 全局串行一致性。
- exactly-once。
- 长时间离线后的 durable replay。

如果某条业务流需要更强一致性，需要由业务协议自行提供 sequence、version 或 watermark 边界。

## 风险

| 风险 | 影响 | 缓解方式 |
|------|------|----------|
| init 与 live 合并逻辑错误 | UI 状态重复或过期 | 明确业务 merge 规则，必要时在 payload 中引入 sequence/version |
| queue namespace 过于开放 | 客户端可构造不受控回包路径 | 由 token 推导 queue path，并在服务端校验允许 namespace |
| reconnect 导致 init 风暴 | 服务端和 broker 压力上升 | 对高频 reconnect 做退避和节流，增加观测指标 |
| 忘记发送 completion | 临时 init queue 泄漏 | 将 completion 视为 contract 必须项，前端增加失败超时处理 |
| 某些流天生需要 user-wide init | 默认 session 语义不满足需求 | 把 `/user` 作为显式例外场景，而不是默认模型 |

## 替代方案与取舍

### 方案 A：补偿直接发到共享 `/topic`

不推荐。

缺点：

- 污染共享流。
- 所有订阅者都要理解 init control frame。
- 多实例下放大无意义广播。

### 方案 B：默认使用 `/user` 做补偿回复

不推荐作为默认。

缺点：

- 同一用户多端会串。
- 无法自然表达“当前连接的初始化流程”。

### 方案 C：先 init，再订 live

拒绝。

缺点：

- 会造成事件缺口。

### 方案 D：长期保留私有 queue，与 `/topic` 并存

通常不优选。

缺点：

- 生命周期复杂。
- 更接近“私有流 + 公共流并存”，不是“共享流 + 临时补偿”。

### 方案 E：立刻为 init 新增一套 core SPI

暂不建议。

原因：

- 现有 sender/subscriber 已经够用。
- 过早抽象容易引入平行机制。

## 接入与剩余范围

新业务流复用现有 init support 与前端 API；每条流明确 live destination、init request、session reply、completion/error 和业务合并边界。旧业务流按各自合同迁移，单次顺序/计划与运行证据归任务。

Redis 与 RabbitMQ 适配需单独验证 session 隔离、同用户多会话、重连新 cycle 和迟到帧；当前源码或单实例结果不证明这些部署已经通过。

## 测试策略

### 后端单元测试

- init handler 通过 `sendToQueue(sessionId, ...)` 回补偿。
- live 继续通过 `send(...)` 发 topic。
- 非法 queue namespace 被拒绝。
- 空 init 也会发 `init-complete`。

### Runtime 单元测试

- `sendToQueue` 的 `/queue/...` 规范化保持不变。
- session envelope 路由仍正确。
- 不会误用 `sendToUser` 替代连接级 init reply。

### 集成测试

- live topic 与 init queue 可以同时存在。
- init reply 只回到请求 session。
- 同一用户的另一个会话不会收到 init。
- `init-complete` 会触发 queue 取消订阅。
- reconnect 会重建 init lane。

### 前端单元测试

- `SDK.Stomp.subscribeWithInitial` 对外可用。
- live topic 在 init request 之前订阅。
- init queue 在 init request 之前订阅。
- completion 到达后自动取消 init queue。
- overall `unsubscribe` 正确释放 live 与 init。
- reconnect 会自动重跑 init。

### 端到端测试

- 页面首次加载后能完成 init 并进入 steady-state。
- 网络短断开后自动恢复，不需要用户刷新。
- 多 tab 同用户不会串 init 数据。
- 多实例部署下 init 仍能正确路由回请求连接。

## 观测建议

建议记录以下指标：

- init request count
- init completion count
- init failure count
- init duration
- active init queue count
- reconnect-triggered init count

建议日志字段：

- destination
- subscriptionToken
- sessionId
- username（如有）
- correlationId
- broker strategy
- init phase

## 实施规则

- `/app` 只承担 init request，不承担共享 live 投递。
- `/topic` 只承担共享 live，不承担定向补偿。
- `/queue` 作为默认 init reply lane。
- `/user` 仅用于明确的 user-scoped 需求。
- init completion 必须显式发送，不能靠超时推断。
- init 完成后应自动关闭私有 queue。
- reconnect 视作新的 init 周期。

## 与现有实现的关系

本设计不是另起一套机制，而是把仓库里已经存在的若干基础能力和业务模式，收敛成可复用 contract：

- 现有前端已经存在 `subscribe(...)` 后再 `send(...)` 触发回补偿的用法。
- 现有后端已经存在在订阅请求后发送缓存日志的实现。
- 现有 broker strategy 已经原生区分 `TOPIC`、`USER`、`SESSION` 三种出站语义。

但需要再次强调：

- 现有前后端业务流的主流先例，仍以 subscribe-then-send replay flow 为主，而且 reply 往往通过 `convertAndSendToUser(...)` 落到 user-scoped destination。
- app-logs 与 helper logs 已使用 init 通道；其他历史流仍须逐项核对是否满足三通道和 session reply 合同。
- 本设计继续约束既有实现及尚未迁移的业务流。

本设计只是在这些现有能力之上，定义一个统一、稳定、跨前后端的 shared contract，并通过试点流逐步迁移，而不是要求所有现存 replay 流一步到位切换。

## 参考

- Spring Messaging 中 application destination、broker destination 与 user destination 的分工。
- `docs/adr/ADR-001-pluggable-websocket-broker.md`
- `docs/design/modules/ability/ouroboros-websocket.md`
- `frontends/web/main-app/src/utils/Stomp.ts`
- `frontends/web/ouroboros-sdk/src/types.ts`

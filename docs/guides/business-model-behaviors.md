# 业务模型 Action 与 Event 开发指南

业务身份由动态模型定义。Action 是请求执行的行为，Event 是已经发生的业务事实，Subscription 是消费方对事实的处理责任。Java 类型和注解是同一契约的可选接入方式。模型投影与执行边界见[模块设计](../../core/ouroboros-business-model/DESIGN.md#8-动态业务行为与事实)。

兼容性由业务模型的 `formatVersion` 决定：缺省、`0.1`、`1.0` 沿用原有关系校验与投影规则，生成实现的自定义 API 缺少输入映射时使用旧输入语义；`2.0` 启用新增关系结构校验，并要求自定义动作引用显式声明输入映射。读取旧定义不改写关系或版本号，具体规则见[升级兼容说明](../runbooks/business-model-behaviors.md#200-rc1-应用升级兼容)。

## 定义与发布

在默认模型的 `actions`、`events`、`eventSubscriptions` 数组中编辑定义。示例订单模型 `sales.Order`：

```json
{
  "actions": [{
    "name": "submit", "title": "提交订单", "isEnabled": true,
    "readOnly": false,
    "inputSchema": {"type": "object", "required": ["data"], "additionalProperties": false, "properties": {"data": {"type": "object", "properties": {"orderId": {"type": "string"}}, "required": ["orderId"], "additionalProperties": false}}},
    "outputSchema": {"type": "object", "properties": {"orderId": {"type": "string"}}, "required": ["orderId"], "additionalProperties": false},
    "binding": {"kind": "handler", "handlerName": "submitOrderHandler"},
    "emits": [{"modelFullName": "sales.Order", "eventName": "submitted"}]
  }],
  "events": [{
    "name": "submitted", "title": "订单已提交", "subject": "model",
    "occurrenceCondition": "订单提交已保存", "allowedProducerKinds": ["action"],
    "payloadSchema": {"type": "object", "properties": {"orderId": {"type": "string"}}, "required": ["orderId"], "additionalProperties": false}
  }]
}
```

在消费方 `inventory.Stock` 添加订阅及对应 `reserve` Action：

```json
{"eventSubscriptions": [{
  "name": "reserveForOrder", "title": "提交时预留库存",
  "description": "预留失败时一起回滚订单提交。",
  "sourceEvent": {"modelFullName": "sales.Order", "eventName": "submitted"},
  "executionMode": "JOIN_SOURCE_TRANSACTION",
  "target": {"kind": "action", "modelFullName": "inventory.Stock", "actionName": "reserve",
    "inputMapping": {"data.orderId": "${ event.payload.orderId }"}}
}]}
```

平台按消费模型解析显式配置的服务账号，该账号必须是启用的现有 User，显式拥有源 Event read 与目标 Action 权限，以及所需完整载荷字段 / 对象读取权限。原事件 actor 只用于审计；订阅不配置执行账号、不继承单据创建人或提交人，不提供 runAs 或自动管理员回退。一个订阅选择 Action 或 listener 中的一种。`filter` 是入队时执行的受限谓词，不能调用方法、类型或产生副作用；当前过滤器变更不会删除已经入队的交付责任。


`executionMode` 必填，只选择一个完整执行语义：

| 模式 | 执行时机与事务 | 失败与恢复 |
| --- | --- | --- |
| `JOIN_SOURCE_TRANSACTION` | emit 内同步完成，共享源事务与同一实际 JDBC 连接 | 任一失败使源事务回滚；同步成功记录随源事务提交，不进入异步重试 |
| `AFTER_COMMIT_TRANSACTIONAL` | 源提交后，在消费事务内执行 Action 或数据库监听器 | 本地业务写入与交付确认同事务；失败由 Outbox 恢复 |
| `AFTER_COMMIT_INDEPENDENT` | 源提交后，释放交付锁，在无消费事务时调用独立监听器 | 监听器持久化回执、幂等和部分成功恢复；Outbox 仍负责重试与确认 |

同步监听器返回只代表当前步骤完成，最终成功以根事务提交为准。即使调用方捕获同步消费异常，源事务仍不可提交。外部 HTTP、消息和邮件不能被本地 JDBC 回滚；需要独立模式及可靠接收协议。Action 只支持前两种模式；监听器声明的 `ATOMIC / INDEPENDENT` 是实现能力，平台据此校验所选模式，不是订阅的第二个配置属性。

订阅结构见[订阅定义 UML](../../core/ouroboros-business-model/docs/business-model-actions-and-events.md#63-订阅定义-uml)；三种模式的事务边界、成功与失败流程及恢复状态见[执行流程图](../../core/ouroboros-business-model/docs/business-model-actions-and-events.md#711-三种执行方式的事务边界)。

监听器目标使用 `{"kind":"listener","listenerName":"businessNotificationListener","config":{"template":"order-submitted"},"inputMapping":{"employeeIds":"${ event.payload.recipientEmployeeIds }","payload":"${ event.payload }"}}`。`config` 是静态 JSON，`inputMapping` 使用统一 EL，按监听器输入 Schema 验证；Action 的映射仍按 params/data 分区。订阅所属消费模型由包含它的模型确定。

订阅不保证顺序；有先后依赖的步骤放进同一 Action/流程。允许级联事件，同步传播最多 32 层，超过限制时携带传播路径失败并回滚。投递责任创建时固定执行模式；改变配置只影响后续事实，已有责任遇到模式不一致进入 CONTRACT_BLOCKED，不能补接已经提交的源事务。

模型经既有保存、验证、投影与应用刷新链路发布。同名冲突、缺失引用或不可用绑定拒绝发布并定位成员。CRUD 与显式绑定的 custom API 由原配置归一化，不能再定义同名 Action；未绑定的高代码 custom API 保留普通 WebAPI。关闭原功能开关同步禁用对应入口。getOne/search 默认只读，包含写入或外部副作用时显式声明原功能 `readOnly=false`。只读由正式业务写入守卫与事件端口执行；底层事务保留写入平台审计的能力，成功审计与源事务一起提交。

CRUD 始终归一化为对应 Action；自定义功能只有在 API 配置 `isBindAction=true` 时才映射为同名 Action。平台生成实现默认绑定同名 Action；高代码直接通过 `@RequestMapping` / `@WebRequestMapping` 提供接口时，默认保留普通 WebAPI 链路，不经过 BusinessActionRuntime。编辑器中的“绑定同名业务 Action”选项开启后，原 HTTP URL 与手动 invoke 才共用同一 Action，并要求高代码通过 `@BusinessActionBinding` 或正式 Handler 提供实现；关闭后由高代码 Controller 直接处理。未显式配置该选项时，历史生成实现按 `isGenerateImplementation=true` 兼容推导为绑定，历史高代码接口按 `false` 推导为普通 WebAPI。仅有前端交互、没有后端 API 的功能不要求可执行 Action。

Action 输入以 `BusinessActionInput<P,D>` 表达 params / data 两个业务分区，根值只允许这两个键。params 是具名操作参数，data 是载荷（可以是对象、数组或其他 Schema 允许的值）。`of`、`paramsOnly`、`dataOnly`、`empty` 工厂区分分区缺失与显式 null；默认值只补缺失，所有输入在实现执行前按动态 Schema 校验。

对象通过 params / data 内的 `x-business-object-ref` 或 `x-business-relation` 声明角色，支持多个模型和数组；没有特殊 target。对象仅支持唯一的 id 主键，服务端重新读取并授权，将状态放到 `context.objectStates` 的 JSON Pointer 键下，例如 `/params/orders/0`，业务输入中仍保留引用。不能用客户端状态替代服务端授权。

Event payload 始终是不可变 JSON 对象；Action output 可以是任意 Schema 允许的结果。Input 的 Schema 是动态定义的权威；Java 泛型、DTO 和生成类型只提供支持。

## 高低代码绑定

Handler 实现 `BusinessActionHandler` 的 `handle(BusinessActionContext, BusinessActionInput<?,?>)`，并通过 `getName()` 返回实现名称。注解方法无需继承接口，可以在 Spring 同步方法上声明完整 `@BusinessAction` 或实现绑定 `@BusinessActionBinding`，业务参数通过 `@BusinessActionParam` / `@BusinessActionData` 选取属性或整个分区：

```java
@BusinessActionBinding(modelFullName="sales.Order", actionName="approve", handlerName="approveOrderHandler")
public ApprovalResult approve(BusinessActionContext context,
    @BusinessActionParam("orders") List<BusinessObjectReference> orders,
    @BusinessActionParam("notify") boolean notify,
    @BusinessActionData ApprovalData data) { /* 业务实现 */ }
```

必填、默认值和可空性只在 Schema 中维护；可缺失或可空字段绑定 primitive 会被拒绝。参数有重复、字段不存在、raw / 无法确定元素类型的泛型、混用整个 Input 与业务参数等也会拒绝发布。支持仅接收 `BusinessActionInput<?,?>`，支持无输入方法；Context 可选。接口实现与配置沿既有 Bean / SPI 机制注册到 META-INF。

低代码绑定 `{"kind":"logicflow","flowName":"sales.Order.submitFlow"}` 默认读取包含 `params/data/body/context` 的工作视图，`body` 默认是 `data` 的同一对象别名。发布快照复用输入校验及流程投影计划；标准视图按需创建可变工作数据，自定义 `binding.inputMapping` 只投影一次。beforeScript 可以修改工作数据，调用者输入与已授权引用保持稳定。新版映射使用 `${ input... }` / `${ context... }`，指向同一 Input 与可信 Context；旧格式 `$.` 在格式入口兼容转换。

CRUD 投影保留既有 Flow 节点、返回值及主子表插件：

| Action | 手动 Input | 既有 Flow 参数 |
|---|---|---|
| add | `dataOnly(data)` | data |
| getOne / delete | `paramsOnly({record:{id}})` | params.id |
| update | `of({record:{id}},data)` | params.id、data |
| search | `paramsOnly({query})` 或 empty | params |
| batchDelete | `paramsOnly({records:[{id}]})` | data.ids |
| import | `dataOnly({file:原生文件})` | data.file |

update 的载荷若重复提供 id，必须与 record.id 一致。生成 Schema 用 `x-business-object-identity: "/params/record/id"` 显式声明别名约束；不同角色的同名 id 不互相比较。导入字段无 JSON 约束时保留原生同步资源，不序列化或提前读取文件；订阅、Event 和重试数据使用可持久化文件引用。

新版 WebAPI 使用显式 `handlerType: "BusinessAction"` 和 `formatVersion: "2.0"`，以 `businessModel` / `action` 引用业务身份，并显式声明 `actionInputMapping`（无输入时为 `{}`），例如 `{"params.record.id":"${ request.pathVariables['id'] }","params.notify":"${ request.params.notify }","data":"${ request.body }"}`。API 的底层实现只在 Action binding 中配置。新版自定义 API 缺少映射或引用 URL 中不存在的路径变量会拒绝发布。

业务模型采用缺省、`0.1` 或 `1.0` 格式时，旧生成实现（`isGenerateImplementation=true`）未声明映射会兼容推导为 `params ← request.params`、`data ← request.body`，保留原元数据。显式映射保持原意；高代码主动绑定 Action 和新版动作引用仍要求显式映射。升级步骤见[运行手册](../runbooks/business-model-behaviors.md#200-rc1-应用升级兼容)。

HTTP 适配器的 Lazy Input 按 RequestInfoMap 的方式提供按键读取视图，直接进入 Runtime 和 Handler；读取 params 不顺带组装 data，枚举分区键不求值，body 与业务值按调用缓存。执行前的契约校验会读取已映射业务值并准备稳定值；未映射 body 不被读取，不能通过方法不读取某字段绕过校验。query / pathVariables / body / headers 是显式来源，业务 params 不继承旧 RequestInfoMap.params 的 Header 回退。Context 自动建立；HTTP 入口或 Controller 同步 invoke 可以取得当前真实请求，参数仍来自显式 Input。MVC 已用 @RequestBody 解析的载荷由请求 Advice 缓存，Context 读取其动态值视图，不再次消费输入流；body/data 表达解析后的载荷，不是原始字节。完整 RequestInfoMap 通过 `context.request` 保留；独立事件消费、Outbox 恢复不继承请求。`binding.requiresHttpRequest=true` 的绑定在缺少请求时执行前拒绝。

旧脚本的 `$request` 是输入端口名。Action 传给 LogicFlow 的工作视图兼容原 `RequestInfoMap` 的读取形状，CRUD 的 beforeScript/afterScript 和自定义脚本无需改写成 `$request.context.request`。已投影的业务字段优先（包括显式 null）；未单独映射 `body` 时，`$request.body` 与 `$request.data` 指向同一份 Flow 工作数据。HTTP 的 `headers/uri/path/method/pathVariables` 等属性惰性委托给原请求；Flow `params` 的已映射业务键优先，其他键沿用原 request.params 的 query/form → 路径变量 → Header 回退，Header 保持多值且不进入键枚举。参数工作修改不写回业务 Input 或请求。显式 `binding.inputMapping` 优先于请求回退，未映射的请求字段仍可读取；完整原请求也继续保留在 `context.request`。后台 invoke 没有 HTTP 上下文时不提供 HTTP 属性，依赖真实请求的绑定应声明 `requiresHttpRequest`。这里扩展的是 Flow 工作视图，高代码的 `BusinessActionInput` 仍只包含显式 `params/data`。详细规则见[输入参数设计](../../core/ouroboros-business-model/docs/business-action-input-design.md)。

通过 `emitBusinessEvent` 正式节点发出事实；节点指定 modelFullName/eventName，输入 `{payload, subjectId?, correlationId?, causationId?}`。绑定到 Action 的流程不声明 producerRef，沿当前 Action 的 emits 与只读约束校验。

高代码通过 `BusinessEventPort.emit(identity, subject, payload, BusinessEventEmissionContext.fromAction())` 发出事实。非 Action 生产者须实现并注册 `BusinessEventProducer`、声明允许的 kind，拥有 Event emit Authority，并使用 `port.inTransaction(() -> { /* 正式 DataModel 写入及 emit */ })` 包住源事务。异常不能被捕获后当作成功；端口失败会使源事务回滚。

`@BusinessEvent` 贡献 Event 定义，payloadSchema 接受 JSON Schema 或具有相同身份 `@BusinessEventPayload` 的可加载载荷类型。`@BusinessEventSubscription` 可贡献完整订阅，或仅绑定动态订阅的 listenerName。监听方法签名为 `void receive(BusinessEventOccurrence<Payload>, BusinessEventDeliveryContext)`；接口形式实现 `BusinessEventListener`。监听器必须同步完成，异步代理或不可检查的代理不能确认交付。

## 动态调用与可用性

内部代码可直接使用 `model.invokeAction("submit", BusinessActionInput.of(params,data))`；模型只传递稳定身份和原 Input，实际执行仍由当前已发布 Runtime 完成。也可使用 `runtime.invoke(model, action, input)`，或传入携带契约指纹的 `BusinessActionInvocation`。所有入口重新授权并验证契约，不要求调用者组装 Context。

HTTP 调用 `POST /api/business-model-behaviors/sales.Order/actions/submit/invoke`，body 为 `{"input":{"data":{"orderId":"ORD-1"}}}`。普通成功结果包装为 WebApiResult（code=0，data 为业务结果）；实现返回 FileResult 时，控制器沿 FileResponses 输出文件响应，不再包装 JSON。SDK 的普通数据调用示例：

```typescript
import { businessActions } from 'ouroboros-sdk'
const result = await businessActions.invoke(
  { modelFullName: 'sales.Order', actionName: 'submit' },
  { data: { orderId: 'ORD-1' } }
)
```

`GET /api/business-model-behaviors/{model}/actions` 的 WebApiResult.data 返回经过权限过滤的行为说明。相同 Action 路径下 `POST /availability` 在标准 WebApiResult.data 中返回 `status`（EXECUTABLE / BLOCKED / NEEDS_INPUT）、reasons 与 blockage（NONE / AUTHORIZATION / CONTRACT / PRECONDITION）；可用性检查不运行实现、不写数据、不发事件，执行时仍重新检查。SDK `businessActions.checkAvailability` 保留该三态，调用失败仍抛出业务或传输异常。

## 前置条件与默认变更事实

Action 可以声明动态受限谓词，注解 `@BusinessAction(precondition="JSON 定义", emits="JSON 引用数组")` 贡献相同契约。例如：

```json
{"precondition":{"expression":"${objectStates['/params/order'].status == 'DRAFT'}","message":"只有草稿可以批准"}}
```

`/params/order` 必须是 Input Schema 声明的对象角色。规则只读取 input、已授权 objectStates；不允许 request、Bean、类型、方法调用或赋值，结果必须为 Boolean。availability 只读检查；写 Action 在正式事务中按稳定顺序锁定对象、重读当前状态后再判断，不能把预检当成执行凭证。通过唯一关系键定位的对象在锁后重验该键仍指向原对象；改变或改绑即拒绝。规则引用的字段必须可读，缺权限不会被当成业务 null。只读 Action 不取写锁，业务自行负责未声明对象、集合范围或外部资源的一致性。非空 authorization 尚无执行语义，会被发布校验拒绝；权限继续使用正式 Authority。

默认数据模型发布时生成 recordsCreated、recordsUpdated、recordsDeleted。默认 add/update/delete/batchDelete/import Action 经正式 DataModel 实际完成写入后，自动登记对应事实，无需脚本重复 emit；自定义 Action 的业务事实仍显式产生。默认事件不按方法名猜测写集，不根据返回结果或输入 ID 制造事实。UpdateWhenDataChange 等插件跳过的更新没有操作事实；子集合写入仍进入审计，需要子模型事件时显式建模。

`recordsUpdated` 表达更新操作，不承诺字段值改变。MySQL 默认 matched-row 模式下，同值 UPDATE 可以计为一次操作；changed-row 模式返回 0 时不产生事实。若返回的正数少于锁定对象数，平台无法证明哪些对象被更新，整笔回滚；MySQL 批量写入建议保持驱动默认的 `useAffectedRows=false`。

载荷是 `{operation, objectIds, affectedCount}`，subject 为 model；不包含原始请求、完整行或前后镜像。默认每份最多 500 个 ID、64 KiB JSON 载荷，超过则拆成独立事件，各份有自己的 eventId 和交付责任；不提供整批重组或顺序承诺。源写入、所有事实和 Outbox 同事务提交，任一登记失败全部回滚。消费者以 eventId/deliveryId 幂等。

默认事件要求 Event read、全部载荷字段以及完整无过滤的模型 search 权限，删除后也按此条件交付；受行/字段限制身份不能读取历史 ID。requireUnfilteredModelRead 还可以在自定义 Event 或 @BusinessEvent 中声明；事实保留产生时的限制，后续配置不能放宽历史事实。模型内显式事实可使用 `model.emitEvent("submitted", payload)`；实例事实显式传入 BusinessEventSubject.instance。模型方法复用 EventPort 的事务、emits、权限与 Schema 检查，不代表消费者已处理完成。

行为契约页将动作、事件和事件订阅放在同一个工作台。左侧按名称和来源查找，右侧编辑本模型的动态定义；平台生成或代码贡献的成员只读展示，并提供可用的来源入口。配置改变后自动检查完整模型草稿；检查结果只代表当前开发环境，发布应用仍执行正式校验。旧模型新增行为前，在“更多 → 兼容设置”预览并确认格式升级；打开、查看或修改其他字段不会自动升级。

### 定义动作与事件

新增动作在“执行配置”中选择逻辑流，可打开已有流或新建后刷新候选。默认传入平台标准动作输入；只有流程需要其他输入形状时才展开“兼容旧流程输入”，按目标字段配置 `${ input.params... }`、`${ input.data... }` 或 `${ context.request... }`。这些表达式由平台表达式引擎执行，编辑器不求值。代码动作在代码中定义，旧 Handler 绑定可通过高级定义保留维护。

动作的“参数 params”“数据 data”“返回结果”和事件的“事件数据”使用字段结构编辑器；组合 Schema、引用等高级结构使用“高级数据定义”保真维护。修改字段类型或删除字段需确认。配置保留未识别扩展字段，不将生成目录写回动态定义。

| 事件主体 | 发出事件时提供 | 事件数据 payload |
| --- | --- | --- |
| 对象 | 所属模型和主体 ID | 显式传入符合事件数据结构的 JSON 对象 |
| 模型 | 所属模型，不提供主体 ID | 显式传入符合事件数据结构的 JSON 对象 |

主体与载荷相互独立，切换主体不会裁剪或填充载荷，也不会自动读取对象快照。“允许发出事件”声明动作实现可派发的事件，不会替实现自动 emit。默认 CRUD 事件的自动产生规则见上文。

### 将事件传入动作

新增动态订阅选择来源事件和响应动作。已有代码注解订阅直接生效，无需在此重复配置监听器；旧 listener 订阅仍在高级定义中维护。其他模块的模型或成员在当前环境不可见时，可以手动填写全名和标识；编辑器保留原值并标记待核验，不推断其存在或权限。

参数 params 与数据 data 分别选择逐字段、整体传入或不传入。逐字段行的目标填写业务字段路径，例如参数 `order.id`；来源可选择事件字段、带类型的固定值或平台表达式。两个分区分别维护，切换某个分区不会改写另一个分区。固定文本会按平台 EL 字面量规则转义，数字、布尔值、null、对象和列表保持对应类型。

```json
{
  "params.order.id": "${ event.subject.id }",
  "data.amount": "${ event.payload.amount }",
  "data.channel": "${ 'payment-event' }"
}
```

“整体传入”显式配置该分区，例如 `{"data":"${ event.payload }"}`；“不传入”删除该分区映射。缺失、null 与空对象不互相替代。重复目标、父子目标覆盖及未完成 JSON 会阻止保存或离开当前编辑；不符合表格结构的旧映射可继续在高级定义中维护。来源、目标类型和最终权限仍由正式校验及执行路径决定。

订阅在“筛选与交付设置”中选择执行模式。应用管理员通过 `ouroboros.business-event.consumer.principals` 按消费模型配置启用的服务账号，并通过现有授权机制授予其最小权限。缺少账号或必要权限拒绝启用；每次执行重新检查当前权限。

### 对照与诊断

通过“更多”打开已发布版本对照、运行诊断或兼容设置。运行诊断须先填写目标应用 API 地址（可使用容器页已有后端 API 会话的 `/api/tunnel/{sessionId}/api` 前缀），只操作该应用已发布契约，可检查可用性、确认后执行、查询安全交付诊断；修复后由具有重驱权限的用户恢复责任。Action 列表及诊断返回的 canRedrive 取自目标应用当前身份，不套用开发平台的权限；后端在执行时重新检查。前置条件阻塞以 PRECONDITION_BLOCKED 展示。

## 可选类型生成

开发端 `GET /api/dev/{appName}/business-model/{fullName}/types?packageName=example.business&className=OrderBusiness` 从正式 Bundle 派生源码。响应 data 包含 javaSources（相对路径 → Java 源码）与 typeScript；保存到应用自己的源码目录后编译。默认包为 com.ouroboros.generated、类为 BusinessModelClient。appName 是既有开发端路径参数，不是新增业务身份。

导出 Java 包名和类名须为合法标识符，且不能包含 Java 解析标识符时忽略的字符（例如零宽空格）；类名不能使用会遮蔽依赖包根的 java 或 com。生成成员与 TypeScript 工厂将非 ASCII 字符编码为 `_u<十六进制码点>_`（下划线编码为双下划线），例如 `€pay` 对应 `_u20ac_pay`；调用传输和事件身份仍保留原始业务名。TypeScript 的 actions 支持用原业务名索引，例如 `actions['€pay']`，然后按方法调用。

按上例生成后，Java 使用 `new OrderBusiness(runtime).submit(BusinessActionInput.dataOnly(new OrderBusiness.SubmitInput("ORD-1")))`；TypeScript 使用 `createOrderBusiness().actions.submit({data:{orderId:'ORD-1'}})`。对象角色与集合都在 Input 内，没有 object(id) 或额外 targetId。

严格对象 Schema 派生 record / DTO，开放、nullable 或组合等无法保真形状使用 Map/Object/unknown。Java 生成类型按 params/data 两个分区组合成 BusinessActionInput，保持原生资源。生成 record 的 JsonProperty 名称和 NON_NULL 省略语义在输入快照中保留；普通 record 的显式 null 按 Schema 校验。TypeScript 保留根对象约束与分区结构。

Action 客户端携带输入协议及 input/output 指纹；只改实现绑定可继续调用，改变契约后须重新生成。Event 发出方法携带 payload Schema 指纹；严格载荷 record 的 `@BusinessEventPayload` 也带身份与指纹。生成 record 可直接用于上述监听方法，平台在当前 Schema 和原始事实 Schema 均匹配时才调用。开放载荷的接口监听器可实现 `getPayloadSchemaFingerprint()` 声明生成契约；普通动态 Map 监听器不要求指纹。

验证入口见[正式场景](../../testing/integration/feature/lowcode/business-model-actions-events/contract.md)，部署、暂停与重驱见[运行手册](../runbooks/business-model-behaviors.md)。

运行与诊断表单选择目标应用 API。独立应用需使用目标应用登录得到的 accessToken，填写弹窗内的“目标应用访问令牌”；留空时沿用当前应用登录态。该值仅用于当前表单的 Authorization 请求头，不保存到模型。应用前后端分离部署时按已有部署规范配置指定前端 origin 的 CORS 与认证过滤顺序。

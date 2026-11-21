# 后端代码提示入口清单

本文是[代码提示机制](script-expression-intelligence.md)的源码入口与接入状态清单。执行语义由源模型、投影和运行入口决定；编辑控件提供提示不表示所有动态形状均可确定。

范围只包含后端执行的脚本、表达式、配置引用及需要区分的相邻后端语法。前端执行的 AMIS 公式、显隐条件、浏览器脚本、前端事件和微应用公式均排除。编辑器在前端显示，不影响其所编辑代码的后端归属。

## 当前接入状态

| 位置族 | 当前能力 |
| --- | --- |
| 8 个内置逻辑流代码节点 | 元数据声明位置；统一编辑器；SPI 节点分析；mapping 用 JSON 宿主保留字面结构 |
| CRUD 前后/替代脚本、旧自定义 API 脚本、脚本 Action、导入映射、工作流 formDataMapping | 复用正式 ModelBundle 和 codeSourcePointer 定位，再使用相同逻辑流分析链 |
| Action 条件/输入输出映射、事件过滤/消费映射、HTTP Action 输入映射 | 按格式版本与实际字段作用域接入；受限 predicate 不暴露全局工具 |
| 字段默认值、字段类型后端参数模板 | 统一代码上下文；草稿新类型、动态参数无依据时保持未知 |
| RPC 模型/凭据 | 执行配置项全面支持配置引用；结构项先解析再类型转换；模板值只扫描一次；已知调用输入推断、显式 Schema 和 JSONPath 路径补全 |
| 配置值、Config.get、RPC/通知配置引用 | 共用过滤后的键目录；不暴露值；protected 配置禁用模板提示 |
| Email payload 模板、通知 schema/options | 共用 JSON 宿主；Email 只补顶层键，其余只处理正式配置引用 |
| 审批 UEL | 现有低代码设计器主要编辑结构化条件；未发现通用 sourceExpression 自由文本字段，不提供 UEL 语言服务 |
| 业务日志 FreeMarker | 已接入开发元数据 add/edit.summaryTemplate，结合 HTTP 合同提供 Request/Response 与模板作用域候选；运行期系统设置不访问开发平台接口 |
| 文件名/目录固定占位符 | 当前为系统属性/Java 高代码入口，不新建低代码 IDE |
| SQL、源码模板、无消费者字段 | SQL 保留既有 Monaco；源码生成模板不新建工具；无消费者控件已撤除，详见后文 |

## 1. 清单依据与覆盖边界

从执行端反查调用方，再对照源模型、投影模板和编辑字段：

- 执行入口：`EL`、`Scripts`、`LogicFlowExpression`、`LogicFlowScriptEngine`、`DataExpressionEvaluator`，以及 RPC、通知等专用模板解析器。
- 模型声明：递归检查全部 `src/main/metadata/*.flow-node.json` 的属性定义，对照业务模型投影、RPC/认证元数据、配置、字段类型和审批实现。
- 编辑入口：检查 `script-editor`、逻辑流 `expression`/`mapping` 转译器、业务模型模板与专用 React renderer，并对普通文本/JSON 控件反查实际后端消费者。
- 排除误报：模板文件中的 AMIS `${…}`、Spring 配置插值和 SQL 控件不按普通业务 EL 统计；同一执行字段的新增、编辑、详情页面不重复算成三种语义。

下列清单穷举该源码基线中识别到的后端代码位置族；可重复集合用 `[]` 或 `*` 表示，而不是按用户模型实例计数。仓库以外的应用扩展包不能靠静态搜索穷举：应由真实应用 helper 发现已加载的执行 contributor、节点元数据与语言能力。扩展新增代码字段仍须声明 code site；无法识别的扩展只能提供基础编辑，不能默认猜为 SpEL。

## 2. 逻辑流：8 个包含代码属性的内置节点定义

以下 8 个定义由元数据递归盘点确认。`mappingEachItem` 是 MappingNode 的执行模式，不另算一种节点。

| 节点与字段 | 执行语义与局部作用域 | 当前编辑入口及接入点 |
| --- | --- | --- |
| [ScriptNode.script](../../../core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/metadata/ScriptNode.flow-node.json) | JS/Groovy；`$0…`、`$端口名`；单 Map 输入展开字段 | 统一 `script-editor` 与上下文门面，保留旧脚本快照入口 |
| [SwitchNode.conditions[].expression](../../../core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/metadata/SwitchNode.flow-node.json) | 裸表达式经 wrap 后执行；普通节点输入上下文；结果作分支判定 | `expression` 已转为统一 SpEL 编辑控件 |
| [MappingNode.mapping](../../../core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/metadata/MappingNode.flow-node.json) | 字符串值为表达式、Map 递归映射；List 等其他值为常量，不递归求值；普通模式用节点输入，逐项模式用当前项字段、`$0`、`$item`、`$index`、`$parentContext` | `mapping` 已改用统一 JSON 编辑器；仅执行字符串位置提示，保留嵌套位置、字面量和 `&` 展开语义 |
| [FilterNode.filterExpr](../../../core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/metadata/FilterNode.flow-node.json) | 当前项字段、`$0`、`$index`、`$parentContext`；没有专门注入 `$item` | `expression` 转译入口 |
| [ConcatNode.isEqualExpr](../../../core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/metadata/ConcatNode.flow-node.json) | 比较两项；仅局部 `a`、`b`、`$0`、`$1` | `expression` 转译入口 |
| [DistinctNode.isEqualExpr](../../../core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/metadata/DistinctNode.flow-node.json) | 同上，元素去重比较 | 同上 |
| [IntersectNode.isEqualExpr](../../../core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/metadata/IntersectNode.flow-node.json) | 同上，交集比较 | 同上 |
| [SubstractNode.isEqualExpr](../../../core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/metadata/SubstractNode.flow-node.json) | 同上，差集比较；文件名保留源码拼写 | 同上 |

实际上下文由 [NodeUtil](../../../core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/NodeUtil.java)、[MappingNode](../../../core/ouroboros-logicflow/ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/MappingNode.java)、[FilterNode](../../../core/ouroboros-logicflow/ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/FilterNode.java) 和 [ScriptNode](../../../core/ouroboros-logicflow/ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/ScriptNode.java) 确定。全局 contributor 叠加顺序也必须复现执行器；不要把所有节点输出都塞入作用域。

前端集中接入点为 [expression.js](../../../frontends/web/ouroboros-logicflow-editor/src/property-transpiler/transpilers/expression.js)、[mapping.js](../../../frontends/web/ouroboros-logicflow-editor/src/property-transpiler/transpilers/mapping.js)、[script-editor.js](../../../frontends/web/ouroboros-logicflow-editor/src/property-transpiler/transpilers/script-editor.js)。Java 侧沿 [NodeDefine](../../../core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/java/com/ouroboros/logicflow/authoring/model/NodeDefine.java) 的正式属性元数据增加声明；通过统一[节点分析 SPI](script-expression-intelligence.md#4-后端扩展与装配)推断输入作用域与映射输出，常量、数据和结构变换等无代码字段节点也参与传播。

## 3. 业务模型：脚本槽位与后端映射

### 3.1 CRUD 的 15 个脚本槽位

五种操作各有前置、后置和替换脚本，共 15 个字段；`scriptLanguage` 等配套语言配置随对应脚本处理。

| 操作 | 完整字段 | 编辑定义 | 正式投影 |
| --- | --- | --- | --- |
| add | `apis.add.beforeScript`、`apis.add.afterScript`、`apis.add.script` | [add.json.ftl](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/resources/META-INF/editor-schema-templates/default/apis/subViews/add.json.ftl) | [AddMPS](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/java/com/ouroboros/businessmodel/projection/logicflow/AddMPS.java) |
| update | `apis.update.beforeScript`、`apis.update.afterScript`、`apis.update.script` | [update.json.ftl](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/resources/META-INF/editor-schema-templates/default/apis/subViews/update.json.ftl) | [UpdateMPS](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/java/com/ouroboros/businessmodel/projection/logicflow/UpdateMPS.java) |
| delete | `apis.delete.beforeScript`、`apis.delete.afterScript`、`apis.delete.script` | [delete.json.ftl](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/resources/META-INF/editor-schema-templates/default/apis/subViews/delete.json.ftl) | [DeleteMPS](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/java/com/ouroboros/businessmodel/projection/logicflow/DeleteMPS.java) |
| getOne | `apis.getOne.beforeScript`、`apis.getOne.afterScript`、`apis.getOne.script` | [getOne.json.ftl](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/resources/META-INF/editor-schema-templates/default/apis/subViews/getOne.json.ftl) | [GetOneMPS](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/java/com/ouroboros/businessmodel/projection/logicflow/GetOneMPS.java) |
| search | `apis.search.beforeScript`、`apis.search.afterScript`、`apis.search.script` | [search.json.ftl](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/resources/META-INF/editor-schema-templates/default/apis/subViews/search.json.ftl) | [SearchMPS](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/java/com/ouroboros/businessmodel/projection/logicflow/SearchMPS.java) |

共同 UI 入口是 [apis/lib.json.ftl 的 scriptSetting 宏](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/resources/META-INF/editor-schema-templates/default/apis/lib.json.ftl)。脚本实际转换为逻辑流节点，变量由生成流程及连线决定，不能把前置与后置都固定描述成同一份 `$request`/`$result`。

### 3.2 其他脚本与流程映射

| 位置 | 执行、形状来源与形状依据 | 接入方式 |
| --- | --- | --- |
| 旧格式自定义 API 的 `api.script` / `api.scriptLanguage` | [自定义 API 编辑模板](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/resources/META-INF/editor-schema-templates/default/custom/api/detail.json.ftl)；经 [CustomAPIMPS](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/java/com/ouroboros/businessmodel/projection/custom/CustomAPIMPS.java) 投影 | 从正式投影定位脚本；保留旧模型合同 |
| 新建“脚本 Action” | [BusinessScriptActionCreate](../../../frontends/web/ouroboros-amis-runtime/src/renderers/BusinessScriptActionCreate.tsx) 使用统一脚本编辑器及正式预览上下文；[DefaultBusinessModelScriptAction](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/java/com/ouroboros/businessmodel/DefaultBusinessModelScriptAction.java) 创建正式 Action 与 Flow | 复用 [singleScript 原型](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/resources/META-INF/prototype-templates/logic-flow/singleScript.json.ftl)；由 Action input Schema 派生 `$input`，按实际端口提供 `$dataModel` |
| 导入逐行映射 | [ImportMPS](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/java/com/ouroboros/businessmodel/projection/logicflow/ImportMPS.java) 读取 `features.import.dataMapMode` / `dataMap`，生成逐项 mapping；[编辑模板](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/resources/META-INF/editor-schema-templates/default/features/subViews/import.json.ftl) 已对齐 `dataMapMode` / `dataMap` | 提示引用正式逐项作用域，UI 与后端共用相同源字段 |
| `workflow.formDataMapping` | [编辑模板](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/resources/META-INF/editor-schema-templates/default/workflow/subViews/start.json.ftl) 配置；[StartProcessLogic](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/java/com/ouroboros/businessmodel/projection/workflow/logicflow/StartProcessLogic.java) 与 [SubmitTaskLogic](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/java/com/ouroboros/businessmodel/projection/workflow/logicflow/SubmitTaskLogic.java) 投影到后端 mapping 节点 | 在源模型处提供表达式值编辑，复用生成流程作用域；虽名为 formData，属于后端代码范围 |

### 3.3 Action、HTTP 与事件合同

这些字段多位于 [BusinessBehaviorDefinitions](../../../frontends/web/ouroboros-amis-runtime/src/renderers/BusinessBehaviorDefinitions.tsx) 的普通文本/JSON 控件，也可能从源 JSON 或 Java 注解提供。JSON 是容器，不意味着内部字符串都是字面量。

| 字段族 | 实际执行上下文/语法 | 权威消费者与形状来源 |
| --- | --- | --- |
| `actions[].binding.inputMapping` | 普通平台 EL；根 `input`、`context`；缺省映射的输入展开规则由 Action 层决定 | [BusinessActionInputPlan](../../../core/ouroboros-business-model/ouroboros-business-model-core/src/main/java/com/ouroboros/businessmodel/action/BusinessActionInputPlan.java)、[BusinessBehaviorMappingPlan](../../../core/ouroboros-business-model/ouroboros-business-model-core/src/main/java/com/ouroboros/businessmodel/behavior/BusinessBehaviorMappingPlan.java)；Action input Schema |
| `actions[].binding.outputMapping` | 普通 EL；根 `result`；当前格式可递归映射，旧格式对象/数组曾按字面量处理 | [DefaultBusinessActionRuntime](../../../core/ouroboros-business-model/ouroboros-business-model-runtime/src/main/java/com/ouroboros/businessmodel/action/DefaultBusinessActionRuntime.java)、BusinessBehaviorMappingPlan；流程输出与 Action output Schema |
| `actions[].precondition.expression` | **受限 predicate**；`input`、`objectStates`，不能调用方法或注入普通全局对象 | [BusinessActionPreconditionPlan](../../../core/ouroboros-business-model/ouroboros-business-model-core/src/main/java/com/ouroboros/businessmodel/action/BusinessActionPreconditionPlan.java)；对象角色键按 JSON Pointer 插入 |
| Action 的 HTTP inputMapping | 平台 EL，按 HTTP 合同限制来源；根 `request`、`query`、`headers`，旧映射有专门转换 | [BusinessActionWebApiContract](../../../core/ouroboros-webapi-model/ouroboros-webapi-model-core/src/main/java/com/ouroboros/webapi/BusinessActionWebApiContract.java)、[BusinessActionHttpInputPlan](../../../core/ouroboros-webapi-model/ouroboros-webapi-runtime/src/main/java/com/ouroboros/webapi/BusinessActionHttpInputPlan.java)；HTTP parameters/requestBody Schema |
| `eventSubscriptions[].filter` | 布尔字面量或受限 predicate；只有 `event` 根 | [BusinessEventSubscriptionMapping](../../../core/ouroboros-business-model/ouroboros-business-model-runtime/src/main/java/com/ouroboros/businessmodel/event/BusinessEventSubscriptionMapping.java)；事件 occurrence 信封及 payload Schema |
| `eventSubscriptions[].target.inputMapping` | 普通 EL；根 `event`；Action 输入按 params/data 分区，listener 使用独立输入合同；无旧 `$.` 方言 | [DefaultBusinessEventSubscriptionRuntime](../../../core/ouroboros-business-model/ouroboros-business-model-runtime/src/main/java/com/ouroboros/businessmodel/event/DefaultBusinessEventSubscriptionRuntime.java)、BusinessBehaviorMappingPlan.subscriptionInput；目标 Action input Schema 或 listener 的 `getListenerInputSchema(config)`；`target.config` 本身是字面配置 |

新旧格式的 selector 缺值、集合路径与字面量行为不同。provider 必须读取源格式版本，插入当前模型可接受的表达式；不自动升级整个源模型。Java 注解中的相同合同复用后端描述，平台 UI 不承担 Java IDE 插件能力。

### 3.4 无消费者字段的退出

已核对并撤除 `apis.add.defaultValues` 的禁用控件、`workflow.customParams` 控件及 `workflow.paramMode` 回退。正式运行时消费 `formDataMode/formDataMapping`，继续维护这两个字段。已有模型数据没有删除，也没有为无消费者字段添加新的运行语义。

## 4. 字段、数据模型与字段类型

| 位置 | 语法与上下文 | 源码依据与编辑入口 |
| --- | --- | --- |
| 数据模型/业务模型字段 `defaultValue` | 通过 DataExpressionEvaluator wrap 后求值；当前记录字段及只读 `$record`；结果约束来自字段类型 | [DefaultDataModelField](../../../core/ouroboros-data/ouroboros-data-core/src/main/java/com/ouroboros/data/model/DefaultDataModelField.java)、[业务字段编辑模板](../../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/resources/META-INF/editor-schema-templates/default/fields/editor.json.ftl) |
| 主键生成器 `coding:…` 的编码模板与序列器名 | 混合 `${…}`、日期 `{yyyy…}`、序列 `<0001>` / `<名称:0001>`；EL 上下文是当前记录；不承诺自动提供 `$record` | [FixLengthCodingGeneratorFactory](../../../core/ouroboros-data/ouroboros-data-pkgen-coding/src/main/java/com/ouroboros/data/pkgenerator/FixLengthCodingGeneratorFactory.java)；在字段类型主键生成器模板中提供参数/日期候选；未发现独立低代码序列维护字段，不调用 next/peek |
| 字段类型定义中的 `dataType`、`primaryKeyGenerator` 模板 | 后端递归解析模板字符串 `${…}`；根是 defaultParams 与实例 params 的合并结果；整个字符串是表达式时可返回原始类型 | [FieldTypeTemplateResolver](../../../core/ouroboros-field-type/ouroboros-field-type-core/src/main/java/com/ouroboros/fieldtype/FieldTypeTemplateResolver.java)、[DefaultFieldTypeCompiler](../../../core/ouroboros-field-type/ouroboros-field-type-core/src/main/java/com/ouroboros/fieldtype/DefaultFieldTypeCompiler.java) |
| 字段类型的 UI input/display 模板中的**后端参数插值阶段** | 同一编译器在后端先解析模板，因此这一层仍需参数提示；编译结果中留给浏览器执行的表达式不在范围 | [字段类型新增](../../../core/ouroboros-field-type/ouroboros-field-type-dev/src/main/metadata/dev/{appName}/field-types/add.ui-schema.json)、[编辑](../../../core/ouroboros-field-type/ouroboros-field-type-dev/src/main/metadata/dev/{appName}/field-types/edit.ui-schema.json)；须由字段注解/模板阶段指明语义，不能扫描所有 `${…}` 一概接入 |

data 与 logicflow 内核分别通过正式表达式端口隔离公共 EL，提示描述不能让内核反向依赖 helper/dev。平台实际引擎桥接见 [CommonExpressionDataExpressionEvaluator](../../../core/ouroboros-data/ouroboros-data-runtime/src/main/java/com/ouroboros/data/runtime/expression/CommonExpressionDataExpressionEvaluator.java)。

## 5. RPC 模型与认证

公共语义入口为 [ExpressionUtils](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-core/src/main/java/com/ouroboros/rpc/util/ExpressionUtils.java)：只扫描原文一次，分别解析 `#{key}`、`${…}` 与旧式 `$路径`，保留整体表达式的原始结果类型。替入的配置/表达式值不再被解释为代码，表达式大括号和字符串按词法边界匹配。配置读取通常是 ConfigurationService 优先、Spring Environment 兜底；提示只取目录，不读取值。旧 `$customer.name` 路径继续兼容，新候选优先生成 `${…}`。Spring Environment 兜底仍遵循 Spring 配置语义。

| 字段族 | 实际语义/上下文 | 执行及编辑入口 |
| --- | --- | --- |
| HTTP RPC `request.url`、`request.parameters[].value` | 单次原文扫描的混合模板；局部变量来自该 RPC 的实际输入 | [HttpRpcRequestBuilder](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-runtime/src/main/java/com/ouroboros/rpc/util/HttpRpcRequestBuilder.java)、[模型公共表单](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-dev/src/main/metadata/dev/{appName}/rpc-model/form-items.ui-schema.json) |
| PlainCredentials 的 `parameters[].value` | 同一混合模板；applyToRequest 传入的 context | [PlainRpcCredential](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-runtime/src/main/java/com/ouroboros/rpc/security/PlainRpcCredential.java)、[PlainCredentials 表单](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-dev/src/main/metadata/dev/{appName}/rpc-auth/type/PlainCredentials.ui-schema.json) |
| HttpBasic 的 username/password | 同一求值器，但局部 context 为空；不能提示请求参数 | [HttpBasicRpcAuthCredential](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-runtime/src/main/java/com/ouroboros/rpc/security/HttpBasicRpcAuthCredential.java)、[HttpBasic 表单](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-dev/src/main/metadata/dev/{appName}/rpc-auth/type/HttpBasic.ui-schema.json) |
| AccessToken 的 `customPlacement[].value` | applyToRequest 的 context 另加 `accessToken` | [AbstractAccessTokenRPCAuthCredential](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-runtime/src/main/java/com/ouroboros/rpc/security/AbstractAccessTokenRPCAuthCredential.java)、[AccessToken 表单](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-dev/src/main/metadata/dev/{appName}/rpc-auth/type/AccessToken.ui-schema.json) |
| `staticAccessToken` | 字面量与 `#{key}` 配置插值；不是完整 EL 模板 | [StaticAccessTokenRPCAuthCredential](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-runtime/src/main/java/com/ouroboros/rpc/security/StaticAccessTokenRPCAuthCredential.java)；独立 profile |
| 动态 token 的 token/expires 提取路径 | JSONPath，结果来自另一个 RPC 的响应；不是 EL | [DynamicAccessTokenRPCAuthCredential](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-runtime/src/main/java/com/ouroboros/rpc/security/DynamicAccessTokenRPCAuthCredential.java)；已接入独立 JSONPath adapter；从 token 请求模型 outputSchema 推导根结构，共享宿主 |
| HMAC 的 `accessKey`、`secretKey`、`securityToken`、`region`、`service`、`action`、`version`、`nonce` | 对应 preset 实际调用 resolveValue 的字段才执行模板求值；不同 preset 必填项与请求兜底不同；枚举/算法/header 名不当成 EL | [HmacSignatureConfig](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-runtime/src/main/java/com/ouroboros/rpc/security/hmac/HmacSignatureConfig.java)、[preset 实现目录](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-runtime/src/main/java/com/ouroboros/rpc/security/hmac/preset)、[HmacSignature 表单](../../../ability/ouroboros-rpc-model/ouroboros-rpc-model-dev/src/main/metadata/dev/{appName}/rpc-auth/type/HmacSignature.ui-schema.json) |

HMAC 盘点包含 AliyunAcs3、AliyunRpcV1、AwsSigV4、HuaweiSdk、TencentTc3、Volcengine 的 preset 及公共 AbstractHeader 实现。按 preset 暴露配置与实际 variables，不能给所有凭据字段统一套请求作用域。

RPC 模型显式 inputSchema 优先；缺少时由 InvocationInputProvider 汇集合规可达 CallRPC 的实际上游形状。共享凭据合并字面引用它的模型输入，已知但未知形状的调用会保留未知。配置间接引用和动态调用无法穷举，不把已知调用合同写回元数据或宣称运行时保证。

结构字段也已接入配置：method、参数 name/in/required、authCredential、timeout/retry；Plain 的参数结构；AccessToken 的模式、placement、请求 modelName、过期配置和路径；HMAC preset/hashAlgorithm/signedHeaders/signatureMethod。正式 RpcFieldSemantics 同时约束后端投影与编辑提示。身份、类型和 Schema 元数据不接受模板。配置变化通过生命周期重建凭据，动态 token 使用实例代际缓存防止旧 fetch 污染新配置。

## 6. 配置本身与配置引用

| 位置 | 语义 | 接入规则 |
| --- | --- | --- |
| 配置值表达式 `${…}` | [RuntimeConfigurationServiceImpl](../../../ability/ouroboros-configuration/ouroboros-configuration-runtime/src/main/java/com/ouroboros/configuration/service/impl/RuntimeConfigurationServiceImpl.java) 使用 EL；上下文含环境属性及此前求出的配置键；protected 项绕过普通求值，敏感来源引用受限 | 使用配置专属 profile；提示授权可见、实际允许引用的键，区分求值先后与依赖；不展示实际值 |
| 普通 EL/脚本中的 `Config.get('key')` | 正式 contributor 对象的方法参数；不是新的模板引擎 | 同一执行对象描述增加参数候选来源；只补键、标题、类型等元数据 |
| RPC/认证中的 `#{key}` | 各调用点的配置读取策略与求值阶段见上一节 | 统一目录按位置过滤；不能把配置 `${key}` 和 RPC `#{key}` 互换 |
| 通知渠道配置/schema 中的 `#{key}` | [NotificationDispatcherImpl](../../../ability/ouroboros-notification/ouroboros-notification-runtime/src/main/java/com/ouroboros/notification/NotificationDispatcherImpl.java) 递归处理字符串，走 ConfigurationService | 同一配置键补全能力，保留本位置的插入语法与无 Spring 兜底差异 |

EL 对单 token 会先尝试整体键命中，配置键如 `rpc.base-url` / `spring.datasource.url` 不应被强行解释成成员链。提示阶段不执行配置表达式，也不枚举全部环境变量作为候选。目录权限、敏感键可见性、protected 项和引用限制沿用配置机制。

## 7. 相邻后端语法：共用编辑宿主，分别处理语言

| 场景 | 实际语言与上下文 | 接入方式 |
| --- | --- | --- |
| Email 通知 titleTemplate/contentTemplate | [EmailNotificationAdapter](../../../ability/ouroboros-notification/ouroboros-notification-channel/ouroboros-notification-channel-email/src/main/java/com/ouroboros/notification/email/adapter/EmailNotificationAdapter.java) 对 `${key}` 作顶层 payload 文本替换，不是 SpEL | 从 payloadSchema 提供键；不提供方法/Java 类型。入口在 [notification 编辑](../../../ability/ouroboros-notification/ouroboros-notification-dev/src/main/metadata/dev/{appName}/notification/edit.ui-schema.json) 与渠道 schema |
| Wechat 等远端模板 | 远端模板标识和供应商 payload；不能仅按 `${…}` 外观判断 | 渠道有自己的语法描述时接入；不假设等价于 Email 或 EL |
| 业务日志内容模板 | [StringTemplateEngine](../../../ability/ouroboros-business-log/ouroboros-business-log-runtime/src/main/java/com/ouroboros/businesslog/util/StringTemplateEngine.java) 使用 FreeMarker；[BusinessLogExtractor](../../../ability/ouroboros-business-log/ouroboros-business-log-runtime/src/main/java/com/ouroboros/businesslog/util/BusinessLogExtractor.java) 提供 Request/Response/Auth/Employee/Department/Company/IpAddress 等上下文与请求取值回退 | 已接入 business-log-template 的 add/edit.summaryTemplate；FreeMarker adapter 提供成员、简单 list/assign 作用域与 builtin；平台复用运行时同配置 parser 提供基本语法诊断，不执行模板或加载 include，不保证运行时变量存在 |
| 审批条件、节点参数 sourceExpression | [FlowableFlowDefinitionService](../../../core/ouroboros-approval-workflow/ouroboros-approval-workflow-flowable/src/main/java/com/ouroboros/workflow/approval/flowable/service/FlowableFlowDefinitionService.java) 与 [BpmnUtil](../../../core/ouroboros-approval-workflow/ouroboros-approval-workflow-flowable/src/main/java/com/ouroboros/workflow/approval/flowable/util/BpmnUtil.java) 使用 Flowable UEL；部分条件由结构化条件对象生成 | 独立 UEL profile；复用 [DynamicFormProvider](../../../core/ouroboros-approval-workflow/ouroboros-approval-workflow-dynamic-form/src/main/java/com/ouroboros/workflow/approval/dynamicform/DynamicFormProvider.java) 等正式变量来源；不把结构条件强制改成文本 |
| 上传文件名/目录模板 | [TemplateUtils](../../../ability/ouroboros-file-upload/ouroboros-file-upload-core/src/main/java/com/ouroboros/upload/generator/TemplateUtils.java) 的 `{yyyy}`、`{MM}`、`{dd}`、`{HH}`、`{mm}`、`{ss}`、`{timestamp}`、`{random:N}` | 固定占位符候选即可；不套 EL |
| SQL 数据集 | [dataset 新增表单](../../../core/ouroboros-statistics-reporting/ouroboros-statistics-reporting-dev/src/main/resources/META-INF/ouroboros/ui-schema/dev/{appName}/dataset/add.json) 虽使用 `script-editor`，language 为 sql | 保留 SQL 编辑能力；不纳入 JS/Groovy/EL 迁移 |
| 项目生成、UI Schema/流程原型模板 | common-template / FreeMarker 的生成期模板，源码维护入口不同于业务用户字段 | 与运行时代码位置分别建模；不提供完整模板开发工具 |

`legacy-compat` 下 Activiti6 条件仅属历史兼容实现，不能用于定义当前 Flowable 的提示语义。QueryDSL 的 SExpression 和结构化过滤属于查询 DSL；不改写为文本 EL。

## 8. 已有执行环境与编辑器的复用入口

### 8.1 运行时目录与本地变量聚合

| 入口 | 可复用能力 / 需修正的限制 |
| --- | --- |
| [RuntimeScriptIntelligenceSnapshotFactory](../../../libs/common/common-script/src/main/java/com/ouroboros/script/intelligence/RuntimeScriptIntelligenceSnapshotFactory.java) | 全局成员反射与共享环境快照工厂；已有内容版本、结构类型和独立 EL profile |
| [SpiScriptContextCatalog](../../../libs/common/common-script/src/main/java/com/ouroboros/script/intelligence/impl/SpiScriptContextCatalog.java) | 从正式执行 SPI 获取绑定及覆盖顺序；EL 使用自己的正式 SPI |
| [ClassGraphJavaTypeMetadataIndex](../../../libs/common/common-code-intelligence/src/main/java/com/ouroboros/script/intelligence/impl/ClassGraphJavaTypeMetadataIndex.java) | Java 成员索引；合并 allowlist 资源，FQN 可区分同名类 |
| [ScriptIntelligenceMetadataCollector](../../../dev-module/ouroboros-dev-helper/ouroboros-dev-helper-runtime/src/main/java/com/ouroboros/devhelper/metadata/collector/ScriptIntelligenceMetadataCollector.java) | 在真实应用容器采集元数据，避免以开发平台 classpath 冒充应用环境 |
| [ApplicationMetadataSnapshotProvider](../../../libs/common/common-dev/src/main/java/com/ouroboros/dev/metadata/ApplicationMetadataSnapshotProvider.java) | 正式元数据快照入口，携带来源状态与运行身份；旧 MetadataProviderRuntimeScriptIntelligenceCatalog 已删除 |
| [CodeContextService](../../../dev-module/ouroboros-dev-code-intelligence/src/main/java/com/ouroboros/dev/codeintelligence/service/CodeContextService.java) | 统一跨域协调；逻辑流分析沿 LogicFlowCodeContextProvider 接入，旧 LogicFlowScriptIntelligenceServiceImpl 及入口已删除；基本语法诊断通过同一响应返回 |
| [ScriptEditor](../../../frontends/web/ouroboros-amis-runtime/src/renderers/ScriptEditor.tsx) 与 [intelligence 目录](../../../frontends/web/ouroboros-amis-runtime/src/renderers/script-editor-intelligence) | Monaco、独立 JS 类型模块及 Groovy/SpEL/模板/JSON adapter，共用 bridge、模型隔离与释放 |

### 8.2 已登记全局对象

下表按源码 SPI 注册盘点，最终以目标应用实际加载目录为准；普通 EL 全局不适用于受限 predicate。

| 来源 | 脚本 | 普通 EL | 正式注册依据 |
| --- | --- | --- | --- |
| 公共工具 | Dates、Converter、Maps、logger、console | Dates、Converter；对象实现与脚本侧不完全相同 | [脚本 SPI](../../../libs/common/common-script/src/main/resources/META-INF/services/com.ouroboros.script.ScriptContextContributor)、[EL SPI](../../../libs/common/common-expression/src/main/resources/META-INF/services/com.ouroboros.expression.ExpressionContextContributor) |
| 配置 | Config | Config | [配置 SPI](../../../ability/ouroboros-configuration/ouroboros-configuration-runtime/src/main/resources/META-INF/services/com.ouroboros.script.ScriptContextContributor)；同模块另有 EL 注册 |
| 组织 | OrganizationHolder、OrganizationContext | 同名对象 | [组织 SPI](../../../ability/ouroboros-organization-structure/ouroboros-organization-structure-runtime/src/main/resources/META-INF/services/com.ouroboros.script.ScriptContextContributor)；同模块另有 EL 注册 |
| 安全 | AuthenticationHolder、AuthContext | 同名对象 | [安全 SPI](../../../security/ouroboros-web-security/ouroboros-web-security-runtime/src/main/resources/META-INF/services/com.ouroboros.script.ScriptContextContributor)；同模块另有 EL 注册 |
| 数据 | DataModelCenter、codeGen | codeGen | [数据脚本 SPI](../../../core/ouroboros-data/ouroboros-data-platform-adapter/src/main/resources/META-INF/services/com.ouroboros.script.ScriptContextContributor)、[数据 EL SPI](../../../core/ouroboros-data/ouroboros-data-platform-adapter/src/main/resources/META-INF/services/com.ouroboros.expression.ExpressionContextContributor) |
| 应用运行时 | BeanFactory、Exceptions | 无对应注册 | [应用运行时 SPI](../../../ability/ouroboros-app-runtime/src/main/resources/META-INF/services/com.ouroboros.script.ScriptContextContributor) |
| 调度 | Scheduler | 无对应注册 | [调度 SPI](../../../ability/ouroboros-scheduler/ouroboros-scheduler-runtime/src/main/resources/META-INF/services/com.ouroboros.script.ScriptContextContributor)；调度调用正式流程，未发现另一处独立用户脚本引擎 |
| 通知 | NotificationDispatcher | 无对应注册 | [通知 SPI](../../../ability/ouroboros-notification/ouroboros-notification-runtime/src/main/resources/META-INF/services/com.ouroboros.script.ScriptContextContributor)；不能因共享上下文文案而额外提示为 EL 全局 |

绑定对象的运行值不属于目录；特别是懒加载 Map/代理，不能为了遍历成员而触发 getter、Bean 获取或业务调用。

## 9. 明确排除与后续接入约束

前端执行场景全数排除，不迁移其表达式控件和运行引擎。Spring `@Value` / `@RabbitListener` 等系统配置、RabbitMQ 的系统 routingKeyExpression 属于运维/Java 配置，不列为低代码用户代码入口。测试样例、生成产物和历史模型不重复计数；外部微应用产品内部公式不据名称猜测为公共 EL。

新增或迁移一个入口时，需要在正式字段声明处确定：后端消费者、语言 profile、code site、源位置、局部变量来源、结果约束及形状依据。若找不到消费者或语义不一致，先解决合同问题；若形状未知，提供未知状态与基础候选。具体扩展与分批实施见[机制设计](script-expression-intelligence.md)，不在此建立另一套注册流程。

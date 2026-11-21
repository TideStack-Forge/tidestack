# Groovy 语义用例

全部使用 Java 21，记录实际 Groovy 编译器版本。case ID 对应 runner 输出；具体光标和预期结果由 runner 的公开样例定义。

| ID | 分支 | 目标行为 | 通过标准 |
| --- | --- | --- | --- |
| GLS-INIT | normal | 初始化、能力协商 | 真实 initialize 结果，后续请求可完成 |
| GLS-STRING | normal | 显式 String 成员补全 | 包含 toUpperCase |
| GLS-LOCAL | normal | def 局部变量传播 | String 初始化后可补全 toUpperCase |
| GLS-JAVA | dependency | 真实 Java API / getter | 外部 Java bean 的 getName 等成员可见 |
| GLS-GENERIC | normal | 泛型 List 元素 | get(0) 后保留 Customer 成员 |
| GLS-IT | normal | findAll 隐式 it | closure 内保留 Customer 类型 |
| GLS-COLLECT | normal | collect 链式传播 | 映射为 String 后元素包含 toUpperCase |
| GLS-DELEGATE | normal | DelegatesTo DSL | closure 中 delegate 的成员可见 |
| GLS-INCOMPLETE | boundary | 未完成成员访问 | 编辑中的尾点仍提供对应成员 |
| GLS-HOVER | normal | hover 类型 | 类型描述包含 String |
| GLS-SIGNATURE | normal | 方法签名 | 包含方法名和参数 |
| GLS-DEFINITION | state | 局部符号跳转 | 跳至声明处 |
| GLS-REFERENCES | state | 作用域内引用 | 返回声明及实际使用位置，排除同名无关变量 |
| GLS-RENAME | state | 作用域内重命名 | 只修改目标符号的声明和引用 |
| GLS-DIAGNOSTIC | invalid | 语法诊断与恢复 | 无效源码有诊断，修正版本后旧诊断清除 |
| GLS-CONTEXT | dependency | 平台 Binding 类型传播 | $0 → 局部变量 → 集合/闭包 → 字段链条均保留形状 |
| GLS-CONTEXT-PROJECTION | dependency | 生成 Java 声明的属性链 | ContextRoot 的 customer 链保留 Customer 成员 |
| GLS-EXTRA-CAPABILITIES | boundary | 格式化/代码操作/语义着色 | 分别记录声明、真实结果及未覆盖原因 |

权限分支由 chain_probe.py 的 owner 与关闭边界断言验证。性能结果记录冷启动/首请求/后续请求及内存，原型不以单次耗时作为性能承诺。

## Headless 语义与平台连接

下表是独立 GE runner 的 case ID。它们验证原型公开输出 semantics.json 中的类型、签名、声明范围和诊断；不读取实现源码断言结构。所有 case 均记录输入全文及观察结果。

| ID | 分支 | 通过标准 |
| --- | --- | --- |
| GLS-H-VERSION | dependency | 实际加载 Groovy 4.0.30 |
| GLS-H-GENERIC | normal | get 返回 Customer，item.name 为 String |
| GLS-H-IT | normal | findAll 内 it 为 Customer，active 为 boolean |
| GLS-H-COLLECT | normal | collect 返回 List<String>，get 返回 String |
| GLS-H-DELEGATE | normal | appendTitle/delegate 识别 Builder；title、owner、thisObject 正确 |
| GLS-H-JAVA-GENERIC | dependency | Java 方法返回 List<Customer> 的嵌套 city 为 String |
| GLS-H-SIGNATURE | normal | substring(0,1) 绑定两个 int 参数的签名 |
| GLS-H-SAFE-SPREAD | normal | spread 后保留 List<String>，safe navigation 中的插值保留 GString |
| GLS-H-SYMBOL-BINDING | state | AST 声明绑定只关联外层 value 的声明与使用，排除方法参数同名变量；不冒充 references/rename API |
| GLS-H-CONTEXT | dependency | 真实校验→映射→脚本上下文进入局部变量、集合元素、闭包和嵌套属性；Map.size() 为 int |
| GLS-H-CONTEXT-JAVA | dependency | 反射生成的 ContextRoot 经 CodeContextService 进入分析，Java List 元素和 collect 结果正确 |
| GLS-H-CONTEXT-UPDATE | state | 同一文档上下文 name 从 string 改 integer，collect 也改为 List<Number>；Schema 不提供 Java 整数宽度，不伪造 Long |
| GLS-H-CONTEXT-REMOVE | recovery | 移除上下文后 collect 为 List<Object>，不残留 List<String> |
| GLS-H-CONTEXT-RESTORED | recovery | 恢复上下文后属性、collect 和 Map 方法均恢复正确类型 |
| GLS-H-SYNTAX-DIAGNOSTIC | invalid | text. 产生语法错误诊断 |
| GLS-H-INCOMPLETE | boundary | 真正 codeComplete 入口对 text. 返回 toUpperCase；core 门禁不覆盖此编辑能力 |
| GLS-H-CODESELECT | state | 真正 codeSelect 入口返回外层声明的准确范围；core 门禁不覆盖此编辑能力 |
| GLS-H-GSTRING-METHOD | normal | GString 的 toUpperCase 识别为 String 方法；core 门禁不覆盖此编辑能力 |
| GLS-H-RUNTIME | error | 仅在发生 framework/visitor 未预期异常时追加 fail；不能被候选能力差距掩盖 |


## 正式产品与插件接入验收

product_probe.py 的 GLS-P-* 覆盖以上编辑能力、尾点/不完整源码恢复、GString、UTF-16、上下文替换/移除、ASTTest 不执行、资源观察及退出。chain_probe.py 的 GLS-C-* 覆盖 HTTP/平台 stdio 往返及 MQ/助手环境同步、并发文档隔离、helper 实际扩展 classpath、owner、关闭和 warm 请求延迟。

浏览器 groovy-lsp-live.spec.js 分五个用例：上游输入/闭包泛型/扩展 Java API 与修改后刷新；hover/签名/作用域导航重命名/诊断/实际 quickfix/格式化；实际语义颜色与文档符号列表；后端 JS 的 GraalJS 全局对象、显式浏览器名称无成员、同名业务字段和普通前端 DOM 隔离。第五个用例验证 Groovy 上游输出经映射进入下游编辑器，字段来自真实 Java 类型。每个用例保存录屏及请求响应附件。

EditorLanguageServicesTest 的第二语言 fixture 验证插件发现、不同 contextMessages 投影、不依赖 Groovy barrier、未知/重复语言拒绝、进程退出后旧会话拒绝与新会话恢复、资源回收；前端 editor-lsp-session.test.js 验证不同语言 capabilities 的共享装配及失败重开。LogicFlowCodeContextProviderTest 对 JS/Groovy/fixture-language 验证相同映射传播，不要求为新语言复制图分析。

当前正式部署为独立 helper / platform JVM；helper 只导出实际依赖，LSP 子进程必须属于平台。检查 chain/processes.json、进程树及 workspace/classpath.json，确认助手无语义子进程、扩展 JAR 从平台内容缓存读取。缓存和容量的公共合同见平台 LANGUAGE-PLUGINS.md。


## 通用输出协议与容器部署

| ID | 分支 | 通过标准 | 实现 |
| --- | --- | --- | --- |
| GLS-TCP-ENV | dependency | 400000 字节制品按分块同步，SHA 校验、远端路径与第二语言 completion 正确，同内容复用 | TcpLanguageDeploymentTest |
| GLS-TCP-INVALID | invalid/recovery | 协议版本、偏移、摘要和未完成环境被拒绝，同连接可恢复 | TcpLanguageDeploymentTest |
| GLS-RUNTIME-LEASE | state/error | 原运行时创建无应用工作区的语言实例；连接关闭删除；启动失败回滚并保留清理异常 | TcpLanguageDeploymentTest |
| GLS-DOCKER-SEMANTICS | dependency/state | 真实 Docker、loopback 动态端口、CPU/内存生效、无挂载、Java completion/输出合同正确、关闭后容器消失 | DockerLanguageRuntimeIntegrationTest |
| GLS-K8S-LIFECYCLE | state | 模拟 API 中 Deployment/ClusterIP、limits、无 PVC、启停删除及原租约清理可观察 | KubernetesLanguageRuntimeBackendTest |
| GLS-OUTPUT-PROPAGATION | normal/recovery | Groovy→校验→映射→Groovy→下游，多跳输出及源码修改替换正确，getter 不执行 | GroovyDataflowContractTest |
| GLS-C-OUTPUT-EDITOR | dependency | 真实容器语义结果传入下游 Monaco；候选 address/name 可见，统一端点合同含 Java 类型 | groovy-lsp-live.spec.js |

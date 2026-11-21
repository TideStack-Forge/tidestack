# Groovy 语义服务验证合同

本场景验证独立语言服务对后端 Groovy 4 脚本的实际编辑语义。产品运行时固定 Groovy 4.0.30 / Java 21；浏览器执行脚本不在范围内。

通过标准来自真实请求的结果，不以 capabilities 声明、类存在、源码结构或脚本执行成功代替语义能力。补全需包含预期成员；hover/signature 需有正确类型/参数；定位、引用、重命名需指向正确作用域的范围。未声明能力记录 not-covered，声明但结果错误记录 fail，环境不可启动记录 blocked。

Java 类型从独立编译的真实 classpath 夹具提供；平台变量由既有 CodeContextService / TypeShape 提供，逻辑流推断继续使用既有 FlowNodeAnalyzer SPI。语义扩展应保留 Map 的动态访问和方法语义，不能把运行时 Map 悄悄换成 DTO，不能强制所有用户脚本 CompileStatic。

正式链路分段独立验收：产品 stdio LSP、TCP 环境交付、原 dev-runtime 容器生命周期、HTTP/平台 LSP + RabbitMQ/助手环境同步、AMIS/Monaco。每段保留真实输入/输出和用例断言；最后浏览器必须实际显示候选、应用重命名和 quick fix、显示文档符号与语义着色。协议插件夹具仅验证扩展合同，不替代 Groovy 语义验收。

正式构建位于 dev-module/ouroboros-dev-groovy-language-server，固定完整 81 个官方 compiler override/trace 源文件重编译，资产摘要与 provenance 归构建模块管理。早期 headless 的 7 类差异装配只保留为研究记录，不参与正式产品。

分段门禁：product_probe.py 任一 check 失败非零退出；chain_probe.py 验证真实命令通道、并发文档隔离、扩展 classpath、owner 和关闭边界；浏览器用真实 AMIS/Monaco 和语言服务，表单布局、草稿和工具 Binding 目录使用夹具；GraalJS 内建对象由助手调用实际执行器探针采集。没有模拟 LSP completion 结果，不包含整个平台登录、画布导航或目标容器发布。

数据形状通过 schema 校验→mapping→脚本输入传播；共享语言插件接入保持相同领域层。默认执行脚本语言服务与新语言机制均需行为测试。测试不执行待分析脚本，验证 ASTTest 未运行；请求响应、通知、stderr、版本、资源观察和逐 case 结果存入 evidence。性能仅是本机样例观察，不能等同生产 SLA。

正式部署使用独立 helper / platform JVM；helper 只导出实际依赖。stdio 模式的 LSP 子进程属于平台；container 模式的语义引擎在原 dev-runtime 托管的专属容器内，平台和助手均没有内嵌 Groovy 编译器。检查 chain/processes.json、进程树、classpath 与 Docker inspect，确认环境隔离和资源回收。缓存、容量、内部 TCP 信任边界和一小时容器租约见平台 LANGUAGE-PLUGINS.md。

GLS-C-OUTPUT-EDITOR 要求上游 Groovy 无显式 outputSchema，通过真实 Java 方法得到 List<Customer>，经映射后，下游 Monaco 显示只存在于 Java 类型中的 address/name。另向统一 context 端点查询并记录输出合同，不能用测试 fixture 手填字段替代语义推断。Docker 真容器验证不替代 Kubernetes 真集群验收，也不把既有静态环境样本作为生产负载基准。

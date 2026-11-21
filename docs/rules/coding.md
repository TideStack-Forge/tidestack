# 项目编码约定

适用于主线各工程，局部目录只补充真实差异。系统分层与正式机制见 [架构](../../ARCHITECTURE.md)、[架构约束](../architecture-constraint-guide.md)。

## 异常与错误映射

业务使用 common-exception 的 BusinessException、ValidationException、InternalException、PlatformException；引擎 data/logicflow 内核可使用内核异常，在对外API边界映射到平台异常。禁止吞异常或用日志替代必要失败信号。请求、响应与错误合同从[接口入口](../api/README.md)定位 common-web 与相关Controller；不按通用示例假设所有接口使用相同HTTP状态。

## 配置与数据访问

需要动态修改的配置由 ouroboros-configuration 管理；系统级启动配置使用 Spring 配置系统。来源、覆盖与分组遵循[配置定义](../design/cross-cutting/configuration-definition-conventions.md)，不硬编码动态取值。

业务数据通过 ouroboros-data 的QueryDSL/类型化模型访问。server级DDL、只读元数据探查等底层例外见 [JDBC例外](jdbc-exemptions.md)，使用 @AllowDirectJDBC 声明依据；不以此为一般业务SQL旁路。新增例外须有无法由正式抽象承载的证据、限定作用域和复查条件。

## 依赖与扩展

跨模块业务依赖核心契约，实现依赖核心契约，由正式装配入口选择实现；Java 引用和 Maven 直接、继承及传递生产依赖必须同时满足边界。适用角色、装配与测试边界及检查覆盖统一见[接口与实现依赖隔离（P1-16）](../architecture-constraints.md#15-接口与实现依赖隔离-p1-16)。同层依赖许可不能替代此项检查。

后端依赖版本在 [BOM](../../bom/pom.xml)集中管理，子模块引用受管版本。独立Maven插件、IDE、Schema和前端以各自构建配置为准，不把独立版本强改成平台版本。前端包使用其提交的package-lock，安装方式按前端入口；升级依赖同时检查锁文件与消费者。

SPI、Provider、AutoConfiguration沿[扩展机制](../design/cross-cutting/extension-mechanisms.md)及实际META-INF注册；先查当前Registry/Factory和生命周期，不再建并行加载链。

## 风格、lint与存量

Java风格以 [IDE配置](../../ouroboros-code-style.xml)、现有同模块实现和工具配置为依据。新代码遵守当前红线，修改代码只修本次触及范围，未触及存量通过明确allowlist/ratchet与[技术债](../tech-debt-tracker.md)渐进收敛；禁止借格式化混入大范围无关改动。

| 工程 | 正式配置与入口 | 证明范围 |
| --- | --- | --- |
| 后端 | 根POM的Checkstyle/PMD；根目录执行 mvn checkstyle:check；scripts/architecture/check-package-structure.sh；testing/architecture-tests | 各工具分别证明风格、包边界或依赖，不代表业务测试 |
| 前端 | 各包.eslintrc及package.json的lint命令；先读 [前端入口](../../frontends/web/AGENTS.md) | 按包执行npm run lint，不能推定每个独立包都配置相同工具 |
| IDE/Schema/模板/插件 | 对应Gradle/npm/Maven配置和包说明 | 缺少统一lint门禁的对象如实按 [CI/CD](ci-cd.md)和技术债报告 |

禁止 @SuppressWarnings("unchecked")；不要为过门禁扩充豁免。其他治理例外必须注明范围、原因、替代验证和退出条件，精确列表由正式检查配置维护。

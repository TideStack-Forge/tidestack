# Ouroboros 架构约束定义

本文档定义 Ouroboros 项目的所有架构约束，包括分层约束、横切关注点约束和品味不变量。约束的检查方式和实际生效范围分别列出；人工规则与未接入项不能视为自动强制。

## 约束分类

> 本页是架构约束的**唯一事实来源**（约束定义、失败级别、触发阶段与修复指引）。本地命令、CI 集成、Ratchet 操作步骤与工具配置见 [架构约束检查系统（操作入口）](architecture-constraint-guide.md)。

- **P0 约束**：必须机械化检查，违反导致构建失败
- **P1 约束**：应该机械化检查，违反导致警告或需要审批
- **P2 约束**：建议性约束，通过代码审查执行

## 1. 分层约束

### 1.1 层级定义

```
┌─────────────────────────────────────────┐
│         Platform Layer (platform)        │  可运行应用（基座）
├─────────────────────────────────────────┤
│   App/Dev Layer (app-module, dev-module) │  应用和开发工具
├─────────────────────────────────────────┤
│        Ability Layer (ability)           │  业务能力服务
├─────────────────────────────────────────┤
│  Security Layer (security)             │  安全与访问控制
├─────────────────────────────────────────┤
│         Core Layer (core)               │  低代码核心引擎
├─────────────────────────────────────────┤
│      Foundation Layer (libs, bom)        │  基础设施和工具
└─────────────────────────────────────────┘
```

### 1.2 依赖方向规则 (P0-3)

**规则**: 
- 基础依赖链：`libs ← core ← security ← ability ← app-module/dev-module ← platform`（六层顺序与 `docs/design/layers/README.md`、根 `pom.xml:16-25` 一致）
- **Security 位于 Core 之上、Ability 之下**：`core` 不得依赖 `security`（存量越层依赖已登记在 allowlist，属 P0-3 待收敛项）
- **Security 实现包允许使用 Ability 包** 的说法已废止；`security` 不得依赖 `ability`（存量越层依赖同样登记在 allowlist）

**包与模块映射**：两套架构测试共用 `ArchitectureLayerSupport`，依据正式 reactor 输出所属模块和精确类型判层。物理目录不等于语义层。

| 语义层 | 所属范围 | 允许访问的其他层 |
| --- | --- | --- |
| Foundation | `libs/**`，含共享开发态合同 | 无 |
| Core | 未被下列语义角色覆盖的 `core/**` | Foundation |
| Bridge | AppModule 的 core/runtime/procode 三个投影模块、菜单 runtime、UI schema runtime，以及 `BusinessModelModule` 精确类型 | Foundation、Core、Security |
| Security | `security/**`、`com.ouroboros.security..` 适配器、业务模型 security-core 与承载权限注解投影的 business-model-util | Foundation、Core、Bridge |
| Ability | `ability/**`；统计 runtime、业务模型 runtime/default、逻辑流 debug-helper | Foundation、Core、Bridge、Security |
| App | `app-module/**`；审批工作流及外部集成的非 core 实现 | Foundation、Core、Bridge、Security、Ability |
| Dev | `dev-module/**`、各领域 `-dev` 模块 | 上述各层及 App |
| Platform | `platform/**` | 上述各层及 Dev |

各层允许同层依赖，模块循环另由 P1-14 阻止。Bridge 是 ADR-004 的权限/菜单投影边界，不能作为纯 Core 引用安全能力的通道；业务模型安全契约已独立为 `ouroboros-business-model-security-core`，纯业务模型 core 不依赖安全合同。AppModule 的基本接口、聚合器及 procode/runtime 实现都暴露或聚合权限树，因此三个现有模块按完整投影组件建模；不按整个 family 前缀赋予未来模块 Bridge 身份。

**检查方式**：ArchUnit 导入正式 Maven reactor 全部生产输出，要求每个导入类型归入一个层，并禁止 Foundation 向其他层依赖。新类型在现有模块中继承该模块的角色；未知根目录的模块分类失败。两套检查共享规则，不能通过遗漏包名或 `allowEmptyShould` 绕过边界。原有 allowlist 保留精确存量登记，不随本轮扩大。规则行为使用真实编译夹具验证合法依赖、基础层向上依赖及 Core 到 Security/Bridge 的拒绝。

**错误信息**：`[P0-3] Module roles and accepted projection boundaries`，随后给出具体的依赖位置。

**存量基线**：以 `testing/architecture-tests/src/test/resources/architecture-allowlist.tsv` 和当前完整 reactor 的扫描结果为准，不固化历史数量。

### 1.3 禁止循环依赖 (P1-14)

**规则**: 模块间不得出现循环依赖

**检查方式**: Maven 解析完整 reactor 清单；ArchUnit 导入声明的生产类，按真实 Maven 模块路径构建直接依赖图。逐条检查有向边是否处于循环中，不按一级包名合并模块，不使用有数量上限的环枚举作为通过依据。缺失编译产物时阻断检查。

**错误信息**: `P1-14 module dependencies form cycles; unapproved directed edges:`，随后列出 `来源模块路径 -> 目标模块路径`。历史豁免只能精确匹配 `module_cycle_edge` 的两个模块路径，禁止使用包名前缀或环起始名称。运行命令与扫描前置条件见[架构约束检查指南](architecture-constraint-guide.md#模块扫描与循环依赖判定)。

### 1.4 为什么不用 Maven Enforcer 表达分层

Maven Enforcer 的 `bannedDependencies` 是**全局**规则，无法针对模块定制，也表达不了"Core 不能依赖 Security"这类条件规则，因此**分层依赖由 ArchUnit 检查**（`testing/architecture-tests`，开启构建测试时随 `build` 阶段运行）。根 POM 中 enforcer 实际只做三件事：`requireMavenVersion`（`pom.xml:250`）、`requireJavaVersion`（`:253`）与 `bannedDependencies`（`:255-270`）。**不存在 `maven-enforcer-rules/` 自定义规则模块，也没有配置 `requireUpperBoundDeps`**。

### 1.5 接口与实现依赖隔离 (P1-16)

**适用范围**：全仓维护的模块，包括根 reactor 之外的独立工程；与 P0-3 的层级约束同时满足。同层可依赖不代表可以绑定其他模块的实现。

**规则**：业务消费者依赖核心契约，实现依赖核心契约，正式装配入口选择实现。

- 核心契约模块承载公开接口、SPI、DTO、值对象及不依赖外部实现的公共规则。不得直接或经其他模块传递依赖本能力或其他能力的具体实现、后端适配器及装配聚合包，也不得在公开签名中泄漏这些实现类型。契约不要求所有类型都是 Java interface。
- 跨模块业务调用使用核心契约，不直接引用实现类，也不因需要接口而依赖装有该接口的实现包。契约误放在实现包时，在既有能力边界内调整归属；不另建平行接口、隐藏注册表或包装链掩盖问题。
- 实现模块实现契约；跨能力协作仍面向对方契约。实现内部的类协作、必要的第三方技术库及同一后端的内部组成不据此强制逐类拆接口。
- 自动配置、既有 Factory/SPI 注册、starter、能力聚合和平台启动/部署模块可按其正式装配职责引入实现。此许可限于装配位置，不扩散到同包中的业务类，也不豁免 P0-3。模块角色以设计、源码、注册和消费者为证，不仅根据 `core`、`runtime`、`default`、`impl` 等名称判断。
- Maven 依赖必须体现同一边界：核对 effective model 中的直接、父 POM 继承、激活 profile 及传递依赖；不能仅删除 Java import，或改用 `runtime`、`provided`、`optional` 来掩盖核心/业务模块对实现的生产依赖。`dependencyManagement`/BOM 只管理版本，不等于引入实现；`test` scope 的真实实现与夹具可以用于验证，不应进入生产依赖。

**检查方式与当前覆盖**：全仓规则通过模块角色、Java 类型引用和 Maven 依赖链联合审查。`ContractDependencyRulesTest` 检查当前 reactor 中 `-core` 制品到 `-runtime` / `-default` 制品的生产依赖可达性，并锁定已整改消费者的 Maven 边界、业务模型实现依赖、统计预览与跨开发模块调用。`StorageDependencyRulesTest` 继续检查上传、资源、文档及公共存储的指定边界；两者均复用 effective Maven 清单及编译类导入机制。这些检查覆盖明确的边界，不替代对全部模块角色和契约签名的语义审查。独立工程、未激活 profile 和未覆盖模块须单独列出检查结果，不能由专项测试通过推断全仓通过。操作见[检查指南](architecture-constraint-guide.md#接口与实现依赖检查)。

**修复方向**：先定位契约归属和正式装配入口，将误放的契约回归核心模块，业务调用改用契约；将实现选择移到既有装配入口，移除错误的直接或传递生产依赖。保留现有 SPI、Factory、生命周期与业务行为，不以扩大 allowlist 代替修复。

## 2. 横切关注点约束

### 2.1 日志 (P0-5)

**规则**: 
- 必须使用 SLF4J，禁止 `System.out.println` 或 `commons-logging`
- 日志中不得包含敏感信息（密码、Token、密钥）

**检查方式**: 
- Checkstyle Regexp: `log\.(info|debug|warn|error).*password|token|secret`
- Maven Enforcer bannedDependencies: `commons-logging:commons-logging`

**错误信息**: `[P0-5] 禁止敏感信息泄露：日志中不得包含密码、Token 等敏感信息。使用脱敏工具或移除敏感字段。`

### 2.2 数据库访问 (P0-4, P1-11)

**规则**:
- P0-4: 禁止 SQL 注入风险，必须使用参数化查询
- P1-11: 禁止直接操作数据库，必须通过 `ouroboros-data` 模块的 QueryDSL API

**检查方式**: 
- Checkstyle Regexp: `"\s*SELECT\s+.*\+` (检测字符串拼接 SQL)
- ArchUnit: 检查是否直接使用 JDBC/JPA 而非 QueryDSL

**错误信息**: 
- `[P0-4] 禁止 SQL 注入风险：不得拼接 SQL 字符串。使用 QueryDSL 参数化查询。`
- `[P1-11] 禁止直接操作数据库：必须通过 ouroboros-data 模块。详见 libs/ouroboros-data/GUIDE.md`

### 2.3 配置管理 (P1-10)

**规则**: 动态业务配置通过 `ouroboros-configuration` 管理；无需动态修改的系统级配置使用 Spring 配置系统，不能硬编码可变值

**检查方式**: ArchUnit + 代码审查

**错误信息**: `[P1-10] 禁止硬编码配置：配置值必须通过 ouroboros-configuration 模块管理。详见 ability/ouroboros-configuration/GUIDE.md`

### 2.4 安全 (P1-12)

**规则**: 禁止绕过安全检查，不得使用 `@PermitAll` 注解（除非有明确的业务理由）

**检查方式**: ArchUnit + 代码审查

**错误信息**: `[P1-12] 禁止绕过安全检查：不得使用 @PermitAll。如有特殊需求，需在代码审查中说明理由。`

### 2.5 异常处理 (P0-1, P1-8)

**规则**:
- P0-1: 禁止异常吞噬，不得使用空 catch 块或 `catch (Exception ignored)`
- P1-8: 异常必须使用 Ouroboros 异常体系（`BusinessException`、`ValidationException` 等）

**检查方式**: 
- Checkstyle EmptyCatchBlock
- ArchUnit: 检查业务异常是否继承 `OuroborosException`

**错误信息**: 
- `[P0-1] 禁止异常吞噬：不得使用空 catch 块。必须记录日志或抛出异常。`
- `[P1-8] 异常必须使用 Ouroboros 异常体系：业务异常应继承 OuroborosException。详见 libs/common/common-exception/GUIDE.md`

## 3. 扩展机制约束

### 3.1 SPI 注册 (P0-6)

**规则**: 所有 SPI 实现必须在 `META-INF/services/` 中注册

**检查方式**：两套规则共用 `ArchitectureSpiSupport`，只读取正式 reactor 生产输出内的服务描述；校验实现所属制品、public 具体类及 public 无参构造，并用 reactor 输出和 Maven 解析的非 test 第三方依赖建立隔离加载器，验证真实服务类型可赋值性。漏注册检查覆盖 NodeBuilder（即使整个描述文件丢失）和现有契约的 `@Priority` 提供者；普通 Spring 组件不因实现相同接口就自动变成 ServiceLoader 提供者。其他未标记且未出现在任何描述中的扩展仍需代码审查识别，不能将这项检查称为自动识别所有 Java 接口实现。

**错误信息**: `[P0-6] 禁止未注册的 SPI：{class} 实现了 {interface} 但未在 META-INF/services/{interface} 中注册。`

### 3.2 Spring 自动配置 (P0-7)

**规则**: 自动配置入口必须在 `META-INF/spring.factories` 或 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册；子配置可以由已注册入口通过 `@Import` 递归引入。顶层 Java 配置类不一定是独立的自动发现入口。

**检查方式**: 默认与增强 ArchUnit 检查复用同一注册判定，只读取 Maven reactor 清单中生产输出目录的两种注册元数据，并沿已导入类的 `@Import` 检查可达性。未注册且无法从已注册入口到达的配置仍然失败；没有注册入口的配置导入环不能互相证明已注册。

**错误信息**: `[P0-7] 禁止未注册的自动配置：{class} 标注了 @Configuration 但未在 META-INF/spring.factories 中注册，也未在 AutoConfiguration.imports 中注册或被已注册配置通过 @Import 引入。`

## 4. 代码品味不变量

### 4.1 未实现的 TODO (P0-2)

**规则**: 不得提交包含 `// TODO: implement this method` 的代码

**检查方式**: Checkstyle TodoComment

**错误信息**: `[P0-2] 禁止未实现的 TODO：不得提交包含 'TODO: implement' 的代码。必须实现或抛出 UnsupportedOperationException。`

### 4.2 类型安全 (P1-9)

**规则**: 禁止类型擦除，不得使用 `@SuppressWarnings("unchecked")`

**检查方式**: ArchUnit + Checkstyle

**错误信息**: `[P1-9] 禁止类型擦除：不得使用 @SuppressWarnings("unchecked")。修复泛型类型声明。`

### 4.3 过时 API (P1-13)

**规则**: 禁止使用 `@Deprecated` 标注的 API

**检查方式**: Maven compiler warnings + ArchUnit

**错误信息**: `[P1-13] 禁止使用过时 API：{api} 已标记为 @Deprecated。使用推荐的替代方案。`

### 4.4 代码复杂度

**规则**:
- 文件不超过 2000 行
- 方法不超过 150 行
- 圈复杂度不超过 15

**检查方式**: Checkstyle FileLength, MethodLength, CyclomaticComplexity

**错误信息**: `代码复杂度超标：{metric} = {value}，限制为 {limit}。考虑重构。`

### 4.5 命名规范

**规则**:
- Service 类应该在 `..service..` 包中
- Repository 类应该在 `..repository..` 包中
- 包名必须全小写：`^[a-z]+(\.[a-z][a-z0-9]*)*$`
- 类名必须大驼峰：`^[A-Z][a-zA-Z0-9]*$`
- 方法名必须小驼峰：`^[a-z][a-zA-Z0-9]*$`
- 常量必须全大写下划线：`^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$`

**检查方式**: ArchUnit + Checkstyle

**错误信息**: `命名规范违规：{element} 不符合命名规范。详见代码风格指南。`

## 5. 测试约束 (P1-15)

**规则**: 新增功能必须包含单元测试

**检查方式**: JaCoCo 覆盖率报告 + 代码审查

**错误信息**: `[P1-15] 禁止未测试的代码：新增功能必须包含单元测试。当前覆盖率：{coverage}%`

## 6. Ratchet 策略（棘轮机制）

对于存量违规，采用 Ratchet 策略：

1. **扫描存量违规**：记录所有违规到 allowlist
2. **配置检查规则**：新代码必须通过架构检查，allowlist 中的违规不报错
3. **渐进式修复**：每两周尝试从 allowlist 移除一条，评估是否可以修复
4. **只减不增**：allowlist 只能减少，不能增加新的违规

### 6.1 Allowlist 格式

```tsv
rule_id	violation_type	source	target	reason	owner	exit_date
P0-3	layer_violation	com.ouroboros.common.web	com.ouroboros.data.core	基础层污染	@arch-team	2026-05-01
P1-14	module_cycle_edge	core/module-a	ability/module-b	经审查的历史模块依赖	@module-owner	2026-12-31
```

### 6.2 Allowlist 管理

- 位置：`testing/architecture-tests/src/test/resources/architecture-allowlist.tsv`
- 更新频率：每两周审查一次
- 审批流程：移除 allowlist 条目需要架构团队审批

## 7. 检查工具矩阵

| 约束 ID | 约束名称 | 检查工具 | Jenkins 阶段 | 失败级别 |
|---------|---------|---------|--------------|---------|
| P0-1 | 禁止异常吞噬 | Checkstyle | `verify-checkstyle` | error |
| P0-2 | 禁止未实现的 TODO | Checkstyle | `verify-checkstyle` | error |
| P0-3 | 禁止跨层依赖 | ArchUnit | `build`（开启构建测试时） | error (新代码) |
| P0-4 | 禁止 SQL 注入 | Checkstyle | `verify-checkstyle` | error |
| P0-5 | 禁止敏感信息泄露 | Checkstyle | `verify-checkstyle` | error |
| P0-6 | 禁止未注册的 SPI | ArchUnit + 脚本 | `build`（开启构建测试时） | error |
| P0-7 | 禁止未注册的自动配置 | ArchUnit + 脚本 | `build`（开启构建测试时） | error |
| P1-8 | 异常体系 | ArchUnit | `build`（开启构建测试时） | warning |
| P1-9 | 禁止类型擦除 | Checkstyle | `verify-checkstyle` | warning |
| P1-10 | 禁止硬编码配置 | `scripts/architecture/check-hardcoded-config.sh` | —（本地 / 发版前预检） | warning |
| P1-11 | 禁止直接操作数据库 | ArchUnit + `trackers/jdbc-exemptions.md` | `build`（开启构建测试时） | warning |
| P1-12 | 禁止绕过安全检查 | ArchUnit | `build`（开启构建测试时） | warning |
| P1-13 | 禁止过时 API | Compiler | `build`（编译期） | warning |
| P1-14 | 禁止循环依赖 | ArchUnit | `build`（开启构建测试时） | error |
| P1-15 | 禁止未测试代码 | JaCoCo（Maven 插件，绑 `test` 阶段） | `build`（`pom.xml:277-315`） | warning |
| P1-16 | 接口与实现依赖隔离 | 全仓联合审查；核心制品及指定消费者有 ArchUnit/Maven 检查 | 自动覆盖随架构测试执行；其他边界人工审查 | 自动检查违规为 error；其他结果保留审查证据 |

## 8. 修复指引

### 8.1 跨层依赖修复

**问题**: Core 层依赖 Security 层

**修复方案**:
1. **依赖倒置**: 在 Core 层定义接口，Security 层实现
2. **SPI 机制**: 使用 Java SPI 解耦
3. **事件机制**: 通过事件总线通信
4. **上下文传递**: 通过方法参数传递必要信息

**示例**:
```java
// 错误：Core 层直接依赖 Security 层
import com.ouroboros.security.SecurityContext;
public class CoreService {
    public void doSomething() {
        User user = SecurityContext.getCurrentUser(); // 违规
    }
}

// 正确：通过参数传递
public class CoreService {
    public void doSomething(User user) {
        // 使用传入的 user
    }
}
```

### 8.2 循环依赖修复

**问题**: 模块 A 和模块 B 相互依赖

**修复方案**:
1. **提取公共模块**: 将共享代码提取到新模块 C
2. **合并模块**: 如果职责相近，考虑合并
3. **依赖倒置**: 定义接口打破循环

### 8.3 异常吞噬修复

**问题**: 空 catch 块

**修复方案**:
```java
// 错误
try {
    riskyOperation();
} catch (Exception e) {
    // 吞噬异常
}

// 正确：记录日志
try {
    riskyOperation();
} catch (Exception e) {
    log.error("Operation failed", e);
    throw new BusinessException("操作失败", e);
}

// 正确：预期的异常
try {
    riskyOperation();
} catch (FileNotFoundException expected) {
    // This is expected - 必须有注释说明为什么预期
    return Optional.empty();
}
```

## 9. 参考资料

- [ARCHITECTURE.md](../ARCHITECTURE.md) - 架构文档
- [DESIGN.md](../DESIGN.md) - 设计规范
- [docs/testing.md](rules/testing.md) - 测试规范
- [ArchUnit 用户指南](https://www.archunit.org/userguide/html/000_Index.html)
- [Checkstyle 配置](https://checkstyle.org/config.html)
- [Maven Enforcer Plugin](https://maven.apache.org/enforcer/maven-enforcer-plugin/)

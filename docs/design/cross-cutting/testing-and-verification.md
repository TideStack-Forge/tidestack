# Ouroboros 测试与验证策略

## 目标

本页用于说明 Ouroboros 在 monorepo 内常见的测试形态、最小验证路径和按改动类型选择验证方式的原则。它关注的是维护者如何证明改动真实生效，而不是某个模块自己的测试实现细节。

## 当前测试基线

从根 `pom.xml`、BOM 和现有模块可以看到，当前仓库的共享测试基线主要包括：

- JUnit 5 作为主测试框架。
- Spring Boot Test 用于运行时与自动装配相关测试。
- `gmavenplus-plugin` 支持 Groovy 测试。
- `maven-surefire-plugin` 与 `maven-failsafe-plugin` 由根项目统一管理。

这意味着仓库的测试设计本身就是“Java 单元测试 + Spring 集成测试 + Groovy DSL/脚本测试”的混合体系，而不是单一路线。

## 常见测试形态

### 1. 纯单元测试

适用于工具类、值对象、DSL 解析器、转换器和纯计算逻辑。常见位置包括：

- `src/test/java/`
- `src/test/groovy/`

这类测试应尽量不依赖完整 Spring 上下文，追求最小反馈回路。

### 2. Spring Boot 运行时测试

适用于自动配置、控制器、服务装配、运行时 provider 和安全/集成链路验证。常见形态是：

- 测试目录内提供 `Application` / `TestApplication` 启动类。
- `src/test/resources/application.yml` 提供测试配置。
- 通过 Spring 上下文直接验证 Bean、Controller 或运行时行为。

这类测试更适合证明“装配是否正确”，而不是只证明单个方法逻辑。

### 3. 资源驱动测试

适用于依赖 classpath 元数据的模块，例如 RPC 模型、UI Schema、业务模型样例或 SPI 扩展点。常见做法是：

- 在 `src/test/resources/META-INF/...` 放测试专用资源。
- 让正式 provider / loader 在测试环境中按真实路径加载它们。

这类测试特别适合验证 `META-INF` 资源约定、SPI 注册和 JSON 元数据读取是否生效。

### 4. Groovy / DSL 测试

Ouroboros 某些核心模块对 DSL、查询表达式或脚本能力有天然偏好，因此会在 `src/test/groovy/` 下保留大量测试。它更适合：

- 数据 DSL 和表达式能力。
- 需要用更紧凑语法描述复杂输入输出的场景。
- 与脚本运行时保持同语言语义的验证。

## 按改动类型选择验证

维护时建议按改动类型选择最小充分验证：

- 改工具类、纯算法或 DSL 转换器：优先跑对应单元测试或 Groovy 测试。
- 改 Spring AutoConfiguration、Controller、运行时服务：优先跑对应模块的 Spring Boot 测试。
- 改 SPI 注册、`META-INF/services` 或 `META-INF/ouroboros/*` 资源：优先跑资源驱动测试，或补一条能真实加载资源的测试。
- 改模板、UI Schema、节点定义、配置元数据：优先验证对应 provider / builder / loader 是否能读取并产出预期结果，而不是只看文件格式。

关键原则是：验证应尽量落在真实消费链路上，而不是只“编译通过”。

## 共享目录约定

当前仓库里几类测试目录和资源已经比较稳定：

- `src/test/java/`：主流 Java 测试。
- `src/test/groovy/`：Groovy DSL / 脚本类测试。
- `src/test/resources/application.yml`：Spring 测试配置。
- `src/test/resources/META-INF/...`：测试专用 SPI / 元数据资源。
- `src/test/resources/schema.sql` 等：嵌入式数据库或资源型夹具。

因此，新增测试时优先复用这些约定，而不是为每个模块重新发明结构。

## 常用命令

当前维护者最常用的验证命令仍然是 Maven 定向测试：

- `mvn test --file ./path/to/module/pom.xml`
- `mvn test --file ./path/to/module/pom.xml -Dtest=TestClassName`
- `mvn test -Dmaven.test.skip=false`

当需要按 artifact 维度联动依赖模块时，也可以使用聚合构建方式，例如 `-pl :artifactId -am`。`bash build.sh build` 更适合做更宽范围的构建与发布链验证，而不是每次小改动都作为第一选择。

## 验证的分层策略

建议采用“从小到大”的验证顺序：

1. 先跑最小相关测试，快速确认核心行为。
2. 若改动涉及装配或多模块协作，再扩大到模块级测试。
3. 若改动影响构建或发布链路，再考虑更大范围的 `mvn` 或 `build.sh` 验证。

这样既能保持反馈速度，也能避免把“跑了很大一圈”误当成“验证很精准”。

## 文档变更的验证

如果改动的是 `docs/design` 体系本身，最小验证重点不是 Maven，而是：

- Markdown 相对链接是否仍可解析。
- 根入口、分类入口和专题入口是否已经接通。
- 文档状态标签是否和当前规则一致。

这类验证虽然不是代码测试，但同样属于维护者应完成的交付质量检查。

## 维护约束

维护测试与验证策略时，应坚持以下约束：

- 不要用“构建一次全仓太慢”为理由跳过最小相关测试。
- 不要把测试只写成实现细节断言，而不验证真实消费入口。
- 若某类共享机制高度依赖资源约定，应优先补资源驱动测试。
- 当验证路径发生共性变化时，应同步更新 `docs/design`，而不是只写在提交说明里。

## 深入阅读入口

- [../architecture/build-and-release.md](../architecture/build-and-release.md)
- [meta-inf-resource-conventions.md](meta-inf-resource-conventions.md)
- [spring-autoconfiguration.md](spring-autoconfiguration.md)
- [../../../AGENTS.md](../../../AGENTS.md)

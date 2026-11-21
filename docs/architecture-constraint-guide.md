# Ouroboros 架构约束检查系统

本文档面向开发者，说明如何使用架构约束检查系统。

## 概述

Ouroboros 使用多层架构约束检查系统，确保代码符合架构规范：

1. **Checkstyle**: 代码风格和基础约束（P0-1, P0-2, P0-4, P0-5）
2. **ArchUnit**: 架构分层和依赖约束（P0-3, P1-8, P1-14 等）
3. **Maven Enforcer**: 依赖管理约束
4. **Package Structure Guard**: 包结构边界检查

检查是否运行取决于流水线门控与构建测试开关。后端 snapshot 默认关闭门控并跳过构建测试；其他发布路径默认启用，可由发布指令覆盖。具体见 [CI/CD 规则](rules/ci-cd.md)。

## 快速开始

### 本地检查入口

```bash
# 运行 Maven 生命周期中绑定的检查（不包含独立包结构脚本）
mvn verify

# 单独运行 Checkstyle
mvn checkstyle:check

# 单独运行 ArchUnit 测试（与 Jenkins 一致的两阶段路径）
mvn -DskipTests test-compile
mvn -pl testing/architecture-tests -am test -Dsurefire.failIfNoSpecifiedTests=false

# 单独运行包结构检查
bash scripts/architecture/check-package-structure.sh
```

### 查看约束定义

所有约束定义在 [docs/architecture-constraints.md](architecture-constraints.md)，包括：
- 约束 ID（如 P0-3, P1-14）
- 检查工具
- 错误信息
- 修复指引

## Ratchet 机制（棘轮策略）

### 什么是 Ratchet？

Ratchet 机制允许存量违规继续存在，但禁止新增违规：

- **存量违规**: 记录在 `testing/architecture-tests/src/test/resources/architecture-allowlist.tsv`
- **新增违规**: 导致 CI 失败
- **修复进度**: allowlist 只能减少，不能增加

### 为什么需要 Ratchet？

存量架构违规登记在 `testing/architecture-tests/src/test/resources/architecture-allowlist.tsv`，数量以当前清单和扫描结果为准。立即强制清除全部存量会阻塞正常变更，Ratchet 机制允许：

1. 立即启用约束检查，防止新增违规
2. 渐进式修复存量违规
3. 保持开发速度

### Allowlist 格式

```tsv
rule_id	violation_type	source	target	reason	owner	exit_date
P0-3	layer_violation	com.ouroboros.common.web	com.ouroboros.data.core	基础层污染	@arch-team	2026-05-01
P1-14	module_cycle_edge	core/module-a	ability/module-b	经审查的历史模块依赖	@module-owner	2026-12-31
```

### 模块扫描与循环依赖判定

架构检查的扫描范围由 Maven 当前解析的完整 reactor 生成，包含当前激活 profile 中有生产代码或资源的模块。`-pl` 只选择本轮执行的项目，不缩小架构检查的预期范围。独立使用 `-f testing/architecture-tests/pom.xml` 无法提供完整 reactor，会明确失败；应从仓库根目录执行上述两阶段命令，并在两阶段使用相同的 profile。

清单生成于测试模块的 `target/architecture-reactor.tsv`，每轮 Maven 执行重新生成，不纳入版本库。导入器只读取清单声明的输出目录，忽略仓库中的其他 `target/classes`，并保留 `architecture-scope-exclusions.tsv` 的既有范围排除规则。任何应有模块缺少输出目录，或声明有代码的模块没有编译类时，检查报告前置条件缺失，禁止以部分扫描结果通过。切换分支、删除或移动源码后，应先 `mvn clean` 再完整编译，避免同一模块目录内残留旧类。

P1-14 按类文件所属的真实 Maven 模块路径构图，检查生产类之间的直接依赖。`logicflow-core`、`logicflow-runtime` 和 `logicflow-dev` 分别作为节点，不再按 `com.ouroboros.logicflow` 包名合并；第三方依赖和模块内部依赖不构成跨模块边。Maven 自身继续负责 POM 声明依赖图的循环检查。

循环判定逐条检查依赖边的目标是否能到达来源，完整判定后才输出未获豁免的有向边，因此不受 ArchUnit 最多枚举 100 个环的限制。失败消息列出完整模块路径，例如 `core/module-a -> ability/module-b`。P1-14 历史基线只能用 `module_cycle_edge` 精确匹配来源和目标；只写包名前缀、环起始名称的旧基线会被拒绝。上面的条目仅说明格式，不是新增豁免。

### 接口与实现依赖检查

全仓判定依据是[接口与实现依赖隔离（P1-16）](architecture-constraints.md#15-接口与实现依赖隔离-p1-16)。先按设计、源码和注册识别契约、业务消费者、实现及正式装配入口，再检查 Java 类型引用和 Maven 生产依赖；不得仅按模块后缀认定违规或豁免。

根 reactor 的有效依赖/作用域沿用 `testing/architecture-tests/src/test/scripts/write-reactor-manifest.groovy`，在 `generate-test-resources` 阶段输出 `target/architecture-maven-dependencies.tsv`。此清单包含父 POM 继承及当前激活 profile 的直接依赖，传递关系需沿模块图追踪；BOM 管理项不是依赖边。独立工程、其他 profile、源码生成及仅以反射/配置指定的类型另行核实并标明覆盖范围。

`ContractDependencyRulesTest` 检查当前 reactor 的核心制品是否经生产依赖引入 runtime/default 实现，并锁定已整改的 AI、组织、RPC、业务模型、动态表单、审批对接密钥解析及其他消费者依赖；编译类规则还检查统计预览和跨开发模块调用，同时验证基座与开发平台保留必要的运行时装配依赖。`StorageDependencyRulesTest` 检查存储设计明确列出的边界。完成上文全 reactor 编译后可定向运行：

```bash
mvn -pl testing/architecture-tests -am test -Dtest=ContractDependencyRulesTest,StorageDependencyRulesTest -Dsurefire.failIfNoSpecifiedTests=false
```

上述测试通过只证明当前 profile 下的指定边界；自动规则无法仅凭命名判断全部模块角色，也不覆盖独立工程和未激活 profile。全仓审查报告仍应保留模块角色依据、来源文件与行号、完整依赖链，以及确定违规、待核实项和装配许可的区分。发现存量问题先记录证据与最小修复位置，不自动加入 allowlist。

### 扫描当前违规

```bash
# 扫描所有违规并生成报告
cd testing/architecture-tests
./scan-violations.sh

# 报告位置
cat ../../tmp/testing-evidence/architecture-violations-*.txt
```

### 更新 Allowlist（需要审批）

```bash
# 警告：此操作需要架构团队审批
./scan-violations.sh --update-allowlist
```

## 常见违规及修复

各类违规的错误信息、修复方案与示例统一维护在 [架构约束定义 §8 修复指引](architecture-constraints.md#8-修复指引)，本页不再重复：

- P0-3 跨层依赖 → §8.1
- P1-14 循环依赖 → §8.2
- P0-1 异常吞噬 → §8.3
- 其余约束的失败级别与触发阶段见 [检查工具矩阵](architecture-constraints.md#7-检查工具矩阵)

## CI 集成

### Jenkins 流水线

门控开启时，架构检查在以下 CI 阶段运行：

1. `verify-checkstyle`: Checkstyle 检查
2. `verify-package-structure`: 包结构检查
3. `build`: 开启构建测试时，通过 Maven reactor 中的 `testing/architecture-tests` 模块运行 ArchUnit 测试

### 失败处理

如果 CI 失败：

1. 查看错误信息，识别违规的约束 ID
2. 参考 [docs/architecture-constraints.md](architecture-constraints.md) 查找修复指引
3. 修复代码并重新提交
4. 如果是合理的例外情况，联系架构团队申请 allowlist 豁免

### 豁免申请流程

1. 在 PR 中说明为什么需要豁免
2. 提供替代方案或修复计划
3. 架构团队审批后，更新 allowlist
4. 设置 `exit_date`，承诺修复时间

## 工具配置

### IDEA 集成

1. 导入 Checkstyle 配置：
   - Settings → Tools → Checkstyle
   - 添加配置文件：`docs/examples/automation-checks/checkstyle.xml`

2. 运行 ArchUnit 测试：
   - 右键 `testing/architecture-tests/src/test/java/com/ouroboros/architecture/ArchitectureTestWithAllowlist.java`
   - Run 'ArchitectureTestWithAllowlist'

### VS Code 集成

1. 安装 Checkstyle 插件
2. 配置 `settings.json`:
   ```json
   {
     "java.checkstyle.configuration": "${workspaceFolder}/docs/examples/automation-checks/checkstyle.xml"
   }
   ```

## 约束演进

### 添加新约束

1. 在 [docs/architecture-constraints.md](architecture-constraints.md) 中定义约束
2. 实现检查逻辑（Checkstyle/ArchUnit/Script）
3. 扫描存量违规，生成 allowlist
4. 更新 CI 配置
5. 更新本文档

### 移除 Allowlist 条目

每两周审查一次 allowlist：

1. 选择一条违规
2. 评估修复成本
3. 如果可行，修复代码
4. 从 allowlist 中移除
5. 验证 CI 通过

## 参考资料

- [架构约束定义](architecture-constraints.md) - 完整约束列表和修复指引
- [ARCHITECTURE.md](../ARCHITECTURE.md) - 架构文档
- [DESIGN.md](../DESIGN.md) - 设计规范
- [ArchUnit 用户指南](https://www.archunit.org/userguide/html/000_Index.html)
- [Checkstyle 配置](https://checkstyle.org/config.html)

## 常见问题

### Q: 为什么我的代码通过了本地检查，但 CI 失败？

A: 可能原因：
1. 本地只运行 `mvn verify`，遗漏了独立包结构检查或与 CI 不同的测试开关
2. 本地 allowlist 文件过期
3. CI 使用了更严格的配置

解决方案：运行 `mvn clean verify` 确保本地环境一致。

### Q: 如何临时禁用某个检查？

A: 不建议禁用检查。如果确实需要：
1. 在 PR 中说明理由
2. 申请 allowlist 豁免
3. 设置明确的修复时间

### Q: Allowlist 文件应该提交到版本库吗？

A: 是的。Allowlist 是项目状态的一部分，应该版本化管理。

### Q: 如何查看当前有多少存量违规？

A: 运行 `./testing/architecture-tests/scan-violations.sh` 查看统计报告。

# 自动化检查设计文档

本文维护当前检查入口、生效条件和未启用的质量机制。流水线阶段与开关以实际配置为准。

## 1. 概述

本文档定义 Ouroboros 项目的自动化检查机制，确保代码质量和架构合规性。

> **关于现状与设计**：第 2–3 节描述已落地的红线规则检查与架构约束 Ratchet 机制；第 4–5 节按
> `Jenkinsfile`、根 `pom.xml`、`scripts/**`、`testing/architecture-tests/**` 描述**当前真实生效**的 CI 与门禁；
> 设计过但**尚未落地**的机制统一收敛到第 6 节，不再混在现状描述中。

### 1.1 检查层次（现状）

```
┌─────────────────────────────────────────┐
│  Maven validate 阶段（本地 mvn 即触发）    │  Checkstyle + PMD，可用 -Dxxx.skip 跳过
├─────────────────────────────────────────┤
│  CI 流水线门禁（Jenkins verify-* 阶段）    │  分支 / 标签构建门禁（GitLab 提交状态回写）
├─────────────────────────────────────────┤
│  架构 Ratchet（allowlist 基线）           │  独立预检显式选择 Ratchet 测试
└─────────────────────────────────────────┘
```

未落地的层次（IDE 实时检查、Git Hook 提交前检查、SonarQube 定期扫描）见第 6 节。

### 1.2 检查工具选型（现状）

| 检查类型 | 工具 | 现状 | 落地位置 |
|---------|------|------|---------|
| 代码风格 | Checkstyle 3.6.0 | 已落地 | `pom.xml:214-234` → `docs/examples/automation-checks/checkstyle.xml` |
| 代码质量 | PMD 3.28.0 | 已落地 | `pom.xml:319-342` → `docs/examples/automation-checks/pmd-ruleset.xml` |
| 架构约束 | ArchUnit | 已落地 | `testing/architecture-tests/`，CI 运行 `ArchitectureTestWithAllowlist` |
| 测试覆盖率 | JaCoCo 0.8.15 | 已落地（覆盖率拦截未接入 CI，见 6.5）| `pom.xml:277-316`（`BUNDLE` LINE ≥ 0.70）|
| 依赖检查 | Maven Enforcer 3.6.3 | 已落地 | `pom.xml:236-275`（版本要求 / 禁用依赖 / 依赖收敛）|
| Bug 检测 | SpotBugs | **未落地** | 见第 6 节 |

## 2. P0 规则机械化

### 2.1 禁止异常吞噬

#### 2.1.1 Checkstyle 规则

```xml
<!-- docs/examples/automation-checks/checkstyle.xml（节选，完整规则见该文件） -->
<module name="Checker">
  <module name="TreeWalker">
    <!-- 禁止空 catch 块 -->
    <module name="EmptyCatchBlock">
      <property name="exceptionVariableName" value="expected|ignore|ignored"/>
      <property name="commentFormat" value="This is expected"/>
    </module>
  </module>
</module>
```

#### 2.1.2 PMD 规则

```xml
<!-- docs/examples/automation-checks/pmd-ruleset.xml（节选，完整规则见该文件） -->
<ruleset name="Ouroboros Custom Rules">
  <rule ref="category/java/errorprone.xml/EmptyCatchBlock">
    <properties>
      <property name="allowCommentedBlocks" value="false"/>
    </properties>
  </rule>
  
  <!-- 自定义规则：检测 catch (Exception ignored) -->
  <rule name="NoIgnoredException"
        language="java"
        message="不得使用 'ignored' 或 'ignore' 作为异常变量名，必须记录日志或抛出异常"
        class="net.sourceforge.pmd.lang.rule.xpath.XPathRule">
    <properties>
      <property name="xpath">
        <value>
          <![CDATA[
            //CatchStatement/FormalParameter/VariableDeclaratorId[
              @Name='ignored' or @Name='ignore'
            ]
          ]]>
        </value>
      </property>
    </properties>
  </rule>
</ruleset>
```

#### 2.1.3 错误信息和修复指引

**错误信息**:
```
[ERROR] EmptyCatchBlock: 不得使用空 catch 块或 'catch (Exception ignored)'
位置: LdapHealthChecker.java:47
违反规则: P0 - 禁止异常吞噬

修复方案：
1. 记录日志（至少 WARN 级别）
2. 返回错误状态或抛出异常
3. 如果确实需要忽略，在注释中说明原因

示例：
// ❌ 错误
} catch (Exception ignored) {
}

// ✅ 正确
} catch (Exception e) {
    log.warn("LDAP health check failed", e);
    return Health.down()
        .withDetail("error", e.getMessage())
        .build();
}

详见: AGENTS.md「红线」及 docs/rules/coding.md 的异常处理规则
```

### 2.2 禁止未实现的 TODO

#### 2.2.1 Checkstyle 规则

```xml
<!-- docs/examples/automation-checks/checkstyle.xml（节选，完整规则见该文件） -->
<module name="Checker">
  <module name="TreeWalker">
    <!-- 禁止 TODO 注释 -->
    <module name="TodoComment">
      <property name="format" value="TODO:\s*implement"/>
      <message key="todo.match"
               value="不得提交包含 'TODO: implement' 的代码，必须实现或抛出 UnsupportedOperationException"/>
    </module>
  </module>
</module>
```

#### 2.2.2 执行现状

`TODO: implement` 红线由 Checkstyle 的 `TodoComment` 规则在 Maven `validate` 阶段拦截
（配置：`docs/examples/automation-checks/checkstyle.xml`；CI 阶段：`Jenkinsfile:301` 的 `verify-checkstyle`）。
早期设计过的自定义 `TodoCheckMojo` Maven 插件**未实现**（`maven-plugins/` 下只有 `ouroboros-dependency-maven-plugin`），
已移入第 6 节「未落地提案」。

#### 2.2.3 错误信息和修复指引

**错误信息**:
```
[ERROR] TodoCheck: 发现未实现的 TODO
位置: UserAgentDetailsExtractor.java:25
内容: // TODO: implement this method

修复方案：
1. 实现该方法，或
2. 抛出 UnsupportedOperationException 并说明原因

示例：
// ❌ 错误
public void someMethod() {
    // TODO: implement this method
}

// ✅ 正确（临时方案）
public void someMethod() {
    throw new UnsupportedOperationException(
        "This method is not yet implemented. " +
        "See issue #123 for implementation plan."
    );
}

// ✅ 正确（最终方案）
public void someMethod() {
    // 实际实现
}
```

### 2.3 禁止跨层依赖

见第 3 节架构约束机械化。

## 3. 架构约束机械化

### 3.1 ArchUnit 结构测试

**现状**：可执行的架构测试位于 `testing/architecture-tests/src/test/java/com/ouroboros/architecture/`，
开启构建测试时，`build` 通过 Maven reactor 进入该模块；模块 Surefire 默认选择 `**/*Test.java`，并显式包含 `ArchitectureTestWithAllowlist`。独立预检通过 `-Dtest=ArchitectureTestWithAllowlist` 显式选择 Ratchet（`scripts/ci/preflight-backend-release.sh`）。
下面 3.1.1–3.1.3 的代码为**规则意图示意**，实际实现以该模块源码为准（`docs/examples/automation-checks/ArchitectureTest.java` 仅为示例文件）。

#### 3.1.1 依赖方向检查

```java
// ArchitectureTest.java
package com.ouroboros.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

public class ArchitectureTest {
    
    private final JavaClasses classes = new ClassFileImporter()
        .importPackages("com.ouroboros");
    
    @Test
    public void layerDependencyRulesShouldBeRespected() {
        layeredArchitecture()
            .layer("Foundation").definedBy("com.ouroboros.util..", "com.ouroboros.common..")
            .layer("Core").definedBy("com.ouroboros.data..", "com.ouroboros.logicflow..", 
                                     "com.ouroboros.ui..", "com.ouroboros.webapi..")
            .layer("Security").definedBy("com.ouroboros.security..")
            .layer("Ability").definedBy("com.ouroboros.ability..")
            .layer("App").definedBy("com.ouroboros.app..")
            .layer("Dev").definedBy("com.ouroboros.dev..")
            
            .whereLayer("Core").mayOnlyAccessLayers("Foundation")
            .whereLayer("Security").mayOnlyAccessLayers("Core", "Foundation")
            .whereLayer("Ability").mayOnlyAccessLayers("Security", "Core", "Foundation")
            .whereLayer("App").mayOnlyAccessLayers("Ability", "Security", "Core", "Foundation")
            .whereLayer("Dev").mayOnlyAccessLayers("Ability", "Security", "Core", "Foundation")
            
            .check(classes);
    }
    
    @Test
    public void noCircularDependenciesBetweenModules() {
        slices().matching("com.ouroboros.(*)..")
            .should().beFreeOfCycles()
            .check(classes);
    }
}
```

#### 3.1.2 异常体系检查

```java
// ExceptionArchitectureTest.java
@Test
public void businessExceptionsShouldExtendOuroborosException() {
    classes()
        .that().resideInAPackage("..service..")
        .and().haveSimpleNameEndingWith("Exception")
        .should().beAssignableTo(OuroborosException.class)
        .orShould().beAssignableTo(RuntimeException.class)
        .check(classes);
}

@Test
public void shouldNotThrowGenericRuntimeException() {
    noClasses()
        .should().throwExceptionOfType(RuntimeException.class)
        .because("应该使用 Ouroboros 异常体系（BusinessException, ValidationException 等）")
        .check(classes);
}
```

#### 3.1.3 SPI 注册检查

```java
// SpiArchitectureTest.java
@Test
public void spiImplementationsShouldBeRegistered() {
    // 检查所有实现了 SPI 接口的类是否在 META-INF/services/ 中注册
    JavaClasses classes = new ClassFileImporter()
        .importPackages("com.ouroboros");
    
    Set<String> spiInterfaces = Set.of(
        "com.ouroboros.security.authority.AuthorityProvider",
        "com.ouroboros.security.rbac.RoleProvider",
        "com.ouroboros.logicflow.nodes.NodeBuilder"
        // ... 其他 SPI 接口
    );
    
    for (String spiInterface : spiInterfaces) {
        // 查找所有实现类
        Set<JavaClass> implementations = classes.stream()
            .filter(c -> c.implement(spiInterface))
            .collect(Collectors.toSet());
        
        // 读取 META-INF/services/ 中的注册
        Set<String> registered = readSpiRegistrations(spiInterface);
        
        // 验证所有实现类都已注册
        for (JavaClass impl : implementations) {
            assertThat(registered)
                .as("SPI 实现 %s 必须在 META-INF/services/%s 中注册", 
                    impl.getName(), spiInterface)
                .contains(impl.getName());
        }
    }
}
```

### 3.2 Ratchet 策略实现

Ratchet（棘轮）机制的现状实现位于 `testing/architecture-tests` 模块，规则是**只减不增**：
allowlist 记录存量违规基线，被显式选中的 Ratchet 测试发现新增违规时失败；是否进入某条交付链需核对该链的选择参数。

#### 3.2.1 Allowlist 文件格式

真实文件：`testing/architecture-tests/src/test/resources/architecture-allowlist.tsv`（历史基线始于 2026-04-19，条目随整改持续变化，不以固定行数判断当前状态）。
TSV 表头与示例：

```
rule_id	violation_type	source	target	reason	owner	exit_date
P0-7-ENHANCED	baseline_violation	类 com.ouroboros.xxx.XxxAutoConfiguration 标注了 @Configuration 但未在 META-INF/spring.factories 中注册		2026-04-19 baseline freeze	@arch-team	2026-06-30
```

字段含义：`rule_id` 规则标识、`violation_type` 违规类型（存量基线为 `baseline_violation`）、
`source` / `target` 违规两端、`reason` 冻结原因、`owner` 责任人、`exit_date` 计划消除日期。

#### 3.2.2 Allowlist 检查逻辑

实现在 `testing/architecture-tests/src/test/java/com/ouroboros/architecture/ArchitectureTestWithAllowlist.java`：

- `loadAllowlist()` 读取 TSV，跳过注释行，缺失文件时返回空集合（`:73-100`）。
- `checkRuleWithAllowlist(ruleId, rule)` 逐条运行 ArchUnit 规则并比对 allowlist 签名，
  把不在 allowlist 中的违规收集为 `newViolations`，非空即 `fail` 并打印违规明细（`:103-136`）。
- 与设计版本不同：**只拦截新增违规**，不会因 allowlist 中已修复的条目残留而失败；
  allowlist 的收紧通过人工在修复后删除对应行完成。

覆盖的规则（同文件）：分层依赖（`:164`）、UI Schema 运行时控制器不得依赖 App 管理包（`:257`）、
UI Schema 管理控制器不得依赖 Dev 包（`:274`）、模块间无循环依赖（`:290`）、
业务异常继承体系（`:305`）、`@Configuration` 注册（`:322`）、安全校验不得被绕过（`:335`）、
不得绕过 `ouroboros-data` 直连 JDBC（`:347`）。

> 根 Jenkinsfile 没有独立的 `ArchitectureTestWithAllowlist` 阶段。构建开启测试时，模块默认选择 `*Test.java` 和显式包含的 `ArchitectureTestWithAllowlist`；独立 preflight 也显式选择该 Ratchet 类。其他以 Test 结尾的测试应按模块 Surefire 和各自跳过条件判断，不能据此一概排除在构建链外。

## 4. CI 配置

> 后端流水线的唯一事实来源是仓库根 `Jenkinsfile`（参数与阶段以该文件为准）；前端流水线另见 `frontends/web/Jenkinsfile`。
>
> 文档类检查（链接与锚点、文档质量）**不在 CI 阶段内**：CI 只保留代码与构建门禁。这两个脚本仍留在 `scripts/docs/`，由本地按需运行，或由发版前预检 `scripts/ci/preflight-backend-release.sh` 统一执行（`scripts/ci/preflight-backend-release.sh:87-88`）。
> 仓库**没有** `.github/workflows/`（`.github/` 下仅有 `modernize/java-upgrade/`）。

### 4.1 Maven POM 配置（根 pom.xml 现状）

| 插件 | 版本 | 绑定阶段 | 生效配置 | 证据 |
|------|------|---------|---------|------|
| maven-checkstyle-plugin | 3.6.0 | `validate` | `configLocation=docs/examples/automation-checks/checkstyle.xml`，`failOnViolation=true`，`includeTestSourceDirectory=false`，受 `${checkstyle.skip}` 控制 | `pom.xml:214-234` |
| maven-pmd-plugin | 3.28.0 | `validate` | 规则集 `docs/examples/automation-checks/pmd-ruleset.xml`，`targetJdk=21`，`failOnViolation=true`，受 `${pmd.skip}` 控制 | `pom.xml:319-342` |
| jacoco-maven-plugin | 0.8.15 | `prepare-agent` / `test`(report) / `check` | `check` 规则：`BUNDLE` 级 `LINE` `COVEREDRATIO` ≥ **0.70** | `pom.xml:103-107`、`pom.xml:277-316` |
| maven-enforcer-plugin | 3.6.3 | `enforce` | `requireMavenVersion [3.6.3,)`、`requireJavaVersion [17,22)`、`bannedDependencies`（commons-logging 与各类 JSON Schema 实现）、`dependencyConvergence` | `pom.xml:236-275` |
| maven-compiler-plugin | 3.15.0 | `compile` | `source`/`target` 21，`jdkToolchain` 21 | `pom.xml:53-69` |

Checkstyle 与 PMD 绑定在 `validate` 阶段，本地构建默认也会触发；`build.sh` 的构建命令显式跳过二者
（`static_check_skip_args="-Dcheckstyle.skip=true -Dpmd.skip=true"`，`build.sh:110`），静态检查由 Jenkins `verify-*` 阶段把关。

> 注意：`docs/examples/automation-checks/checkstyle.xml` 与 `pmd-ruleset.xml` 是**构建输入**（根 pom 直接引用），
> 不要复制到仓库根目录。

### 4.2 Jenkinsfile 阶段（现状）

- 所有 `verify-*` 阶段的 `when` 条件均为 `!env.CI_SKIP.toBoolean() && env.GATE_CHECKS_ENABLED.toBoolean()`（例如 `Jenkinsfile:237`）；
  `build` / `deploy` / `publish-*` 分别由 `TO_BUILD` / `TO_DEPLOY` / `PUBLISH_MOTHERSHIP` / `PUBLISH_DEVELOP` 控制。
- 每条阶段命令都经 `runTrackedSh` 包装：`set -eo pipefail` 并把输出 `tee` 到 `.jenkins-last-stage.log`（`Jenkinsfile` 的 `runTrackedSh` 函数），
  失败通知截取该日志并屏蔽凭据。
- 每个阶段通过 `gitlabBuilds` / `gitlabCommitStatus` 回写 GitLab 提交状态；成功 / 失败通知走钉钉机器人。

| # | 阶段 | 执行条件 | 实际命令 / 脚本 | 证据 |
|---|------|---------|----------------|------|
| 1 | `prepare` | 总是执行 | 解析版本号与构建参数、判定门控与发布开关、写入环境变量；`chmod +x build.sh` | `Jenkinsfile:57`、`Jenkinsfile:200-226` |
| 2 | `verify-node-npm-baseline` | `!CI_SKIP && GATE_CHECKS_ENABLED` | `bash scripts/ci/verify-node-npm-baseline.sh`（默认 `strict`：Node/npm 版本必须**不低于**基线 `.nvmrc`=22.23.1 与 `package.json#packageManager`=npm@10.9.8，高于基线同样通过；`NODE_NPM_BASELINE_MODE=presence` 可降级为只检查命令存在） | `Jenkinsfile:235`；`scripts/ci/verify-node-npm-baseline.sh:6,25,30` |
| 3 | `prepare-maven-version` | `!CI_SKIP`（不受门控开关影响） | `mvn -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" versions:set -DnewVersion="${VERSION}" -DgenerateBackupPoms=false` | `Jenkinsfile:250` |
| 4 | `verify-checkstyle` | `!CI_SKIP && GATE_CHECKS_ENABLED` | `mvn -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" checkstyle:check` | `Jenkinsfile:265` |
| 5 | `verify-pmd` | 同上 | `mvn -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" -DskipTests -Djacoco.skip=true -Dcheckstyle.skip=true pmd:check` | `Jenkinsfile:280` |
| 6 | `verify-package-structure` | 同上 | `bash scripts/architecture/check-package-structure.sh`（边界表 + 包结构 allowlist） | `Jenkinsfile:295`；`scripts/architecture/check-package-structure.sh:16-17` |
| 7 | `build` | `TO_BUILD` | `./build.sh build -v ${VERSION} -s ${STATUS_FILE} -c ${BUILD_TESTS_ENABLED}` | `Jenkinsfile:311` |
| 8 | `deploy` | `TO_DEPLOY`（= `!CI_SKIP && DEPLOY_MAVEN`） | `./build.sh deploy -v ${VERSION} -s ${STATUS_FILE} -c ${BUILD_TESTS_ENABLED}` | `Jenkinsfile:327`、`Jenkinsfile:213` |
| 9 | `publish-mothership` | `PUBLISH_MOTHERSHIP` | `./build.sh publish-mothership -v ${VERSION} -w ${WEB_IMAGE_TAG} -b ${BASE_IMAGE} -r ${REGISTRY_URL} -n ${NAMESPACE} -u ${DOCKER_PUSH_USER_USR} -p ${DOCKER_PUSH_USER_PSW} -c ${BUILD_TESTS_ENABLED} -t ${TAGS} -s ${STATUS_FILE}` | `Jenkinsfile:343` |
| 10 | `publish-develop` | `PUBLISH_DEVELOP` | `./build.sh publish-develop -v ${VERSION} -r ${REGISTRY_URL} -n ${NAMESPACE} -u ${DOCKER_PUSH_USER_USR} -p ${DOCKER_PUSH_USER_PSW} -c ${BUILD_TESTS_ENABLED} -e ${MOTHERSHIP_IMAGE_TAG} -t ${TAGS} -s ${STATUS_FILE}` | `Jenkinsfile:371` |

`build.sh` 的 `build` 阶段执行 `mvn install -P jdk21 -Djacoco.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true`（`build.sh:902`），
`deploy` 阶段执行 `mvn deploy -DskipTests -Dcheckstyle.skip=true -Dpmd.skip=true ...`（`build.sh:912,918`）：
两条路径都不跑静态检查（由 `verify-*` 阶段把关），覆盖率门禁在 `build` 被 `-Djacoco.skip=true` 跳过（见 6.5）。
开启构建测试时，`testing/architecture-tests` 模块随 `build` 的 Maven reactor 运行默认 `*Test.java` 及 `ArchitectureTestWithAllowlist`；独立 preflight 还通过 `-Dtest=ArchitectureTestWithAllowlist` 显式执行。

### 4.3 门禁开关语义与默认值

| 环境变量 | 默认值 | 语义 | 证据 |
|---------|-------|------|------|
| `CI_SKIP` | `false` | 整体跳过开关。以下情形置 `true`：`frontend-web/*` 引用（后端流水线跳过）、版本号无法解析、既非 `release/*`/`snapshot/*` 也非 `refs/tags/*` 的其他引用。为 `true` 时强制关闭门控与构建测试，并把两个发布开关置 `false` | `Jenkinsfile:61`、`Jenkinsfile:79-93`、`Jenkinsfile:179-192`、`Jenkinsfile:209` |
| `GATE_CHECKS_ENABLED` | `true`；`snapshot/*` 分支与 `CI_SKIP` 时为 `false`；`refs/tags/*` 保持 `true` | 控制全部 `verify-*` 阶段是否执行 | `Jenkinsfile:62,105,123,154-156`、`Jenkinsfile:210`、`Jenkinsfile:237` |
| `BUILD_TESTS_ENABLED` | `true`；`snapshot/*` 分支与 `CI_SKIP` 时为 `false` | 以 `-c` 传入 `build.sh`，为 `false` 时追加 `-DskipTests` | `Jenkinsfile:63,106,124`、`Jenkinsfile:211`、`build.sh:892-895` |

覆盖方式（提交信息指令，手动触发 / 分支 / 标签三条路径均生效）：

- `[gate-checks: on|off]` → 覆盖 `GATE_CHECKS_ENABLED`（`Jenkinsfile` 的 `getGateChecksEnabledFromMessage` 函数）
- `[build-tests: on|off]` → 覆盖 `BUILD_TESTS_ENABLED`（`Jenkinsfile` 的 `getBuildTestsEnabledFromMessage` 函数）
- 发布相关：`[skip-deploy]` / `[skip-deploy: true|false]`、`[skip-publish]` / `[skip-publish: true|false]`（`Jenkinsfile` 的 `isSkipDeploy` / `isSkipPublish` 函数）

优先级：手动触发时先按 `snapshot/*` 规则取默认值，再用提交信息指令覆盖（`Jenkinsfile:102-112`）。
构建参数 `DEPLOY_MAVEN`（默认 `false`）、`PUBLISH_MOTHERSHIP` / `PUBLISH_DEVELOP`（默认均 `true`）属于发布开关，
不参与门控判定（`Jenkinsfile:32-34`）。

### 4.4 前端流水线

`frontends/web/Jenkinsfile` 独立定义前端流水线，阶段为 `prepare`、`verify-node-npm-baseline`、`publish-web`、`publish-sdk`
（`frontends/web/Jenkinsfile:38,127,142,187`），不在本文范围内。

## 5. 质量门禁（现状）

### 5.1 合并条件

- [ ] Jenkins 流水线全部 `verify-*` 阶段通过（见 4.2）
- [ ] Checkstyle 无违规（`verify-checkstyle`，根 pom `failOnViolation=true`）
- [ ] PMD 无违规（`verify-pmd`，根 pom `failOnViolation=true`）
- [ ] 架构测试按实际入口通过；Ratchet 使用独立预检的显式测试选择，不能由普通 build 直接推定（见 3.2）
- [ ] 覆盖率满足根 pom 定义：`BUNDLE` 级 `LINE` `COVEREDRATIO` ≥ 70%（`pom.xml:300-313`）——注意该门禁当前不拦截 CI，见 6.5
- [ ] 至少 1 人 Code Review 通过、所有讨论已解决（人工流程，非脚本门禁）

### 5.2 门禁边界

- **由 CI 门控强制**：Checkstyle、PMD、包结构、Node/npm 基线。
- **由 CI 构建测试执行**：架构模块默认 `*Test.java` 及 `ArchitectureTestWithAllowlist`，受 `BUILD_TESTS_ENABLED` 控制；独立 preflight 也显式选择 Ratchet。
- **由发版前预检执行**：门控开启时执行 AI 装配、依赖策略、独立架构检查与硬编码配置检查；统计报表 E2E 通过 `RUN_STATISTICS_E2E=true` 显式开启（`scripts/ci/preflight-backend-release.sh`）。
- **由发版前预检强制（不在 CI 内）**：文档链接与锚点（`scripts/docs/check-links.sh`）、文档质量统计（`scripts/docs/check-doc-quality.sh`），见 `scripts/ci/preflight-backend-release.sh:87-88`。
- **由 Maven 构建强制**：Enforcer（版本要求 / 禁用依赖 / 依赖收敛）、Checkstyle 与 PMD 的 `validate` 绑定（本地完整构建时）。
- **仅人工**：Code Review、讨论解决。
- **未接入 CI**：覆盖率拦截、SpotBugs、GitHub Actions、质量评分看板——见第 6 节。

## 6. 未启用的机制

### 6.1 SpotBugs 静态分析

仓库 POM 和 Jenkins 流水线没有 SpotBugs 插件与阶段，不将其计入当前门控。

### 6.2 GitHub Actions

CI 使用 Jenkins 与 GitLab 提交状态回写；仓库没有 GitHub Actions workflows。

### 6.3 质量评分与看板

没有自动质量评分类、趋势数据与可视化看板；单次评估分数不能代替持续门控。

### 6.4 SonarQube

仓库 POM、Jenkinsfile 和 CI 配置没有 SonarQube 扫描配置。

### 6.5 覆盖率拦截

根 pom 定义了 `BUNDLE` 级 `LINE` `COVEREDRATIO` ≥ 0.70 的 `jacoco:check`（`pom.xml:295-314`），
但 Jenkins 的构建路径传入了跳过参数：`build` 阶段 `-Djacoco.skip=true`（`build.sh:902`），
`deploy` 阶段 `-DskipTests`（`build.sh:912,918`），因此**当前 CI 不会因覆盖率不足失败**。
该门禁只在本地完整 `mvn verify` / `mvn install`（不加跳过参数）时生效。若要真正拦截，需要调整 `build.sh` 的跳过参数或新增独立阶段。

### 6.6 自定义 TODO Maven 插件

`TodoCheckMojo`（`check-todos` goal）未实现；`maven-plugins/` 下只有 `ouroboros-dependency-maven-plugin`。
`TODO: implement` 红线现由 Checkstyle 的 `TodoComment` 规则覆盖（见 2.2）。

### 6.7 IDE 实时检查与 Git Hook 提交前检查

IDE 与本地 hook 的安装状态由 checkout 决定。规则文件（`docs/examples/automation-checks/checkstyle.xml`）已就绪，但仓库无 `.vscode/`、无 husky / `.githooks`、
本地 `core.hooksPath` 与 hooks 的安装状态因 checkout 而异，需要在实际工作区核对。仓库内的规则文件与本地工具安装状态分别验证。

## 7. 依赖策略门禁

依赖策略的长期机器输入为 [dependency-policy.yaml](../../scripts/ci/dependency-policy.yaml)，正式入口为 `bash scripts/ci/verify-dependency-policy.sh`。后端发布预检通过 `scripts/ci/preflight-backend-release.sh` 调用该入口。

验证器扫描真实 POM、前端 manifest 和 JUnit 4 源码，检查 Groovy 运行时坐标、前端工具声明、审批默认引擎边界、JUnit Vintage 的直接及继承归属，以及兼容例外的范围与有效期。策略中的基线计数和有效继承映射属于持续维护的约束，出现漂移时需要复核源码和台账。

`evidence` 引用长期设计、正式构建输入与行为测试，验证器检查引用文件存在。具体依赖状态由独立源码扫描判断。运行时依赖树、目标环境构建与发布状态需分别验证，单次执行记录保存在任务或 CI 附件中。

策略迁移时保留 `dependency_groups` 中的坐标、边界、必需状态与例外规则，正式输入和消费者同步更新。验证器自身的行为测试由 `python3 -m unittest discover -s scripts/ci/tests -p test_verify_dependency_policy.py -v` 执行，覆盖非法依赖、输入缺失、例外到期和 JUnit 归属等场景。

## 8. 相关文档

- AGENTS.md 红线规则: [AGENTS.md](../../AGENTS.md)
- 架构约束指南: [docs/architecture-constraint-guide.md](../architecture-constraint-guide.md)
- 架构总览: [ARCHITECTURE.md](../../ARCHITECTURE.md)
- 后端流水线: [Jenkinsfile](../../Jenkinsfile)；前端流水线: [frontends/web/Jenkinsfile](../../frontends/web/Jenkinsfile)
- 架构测试与 allowlist: `testing/architecture-tests/`

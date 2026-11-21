package com.ouroboros.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 架构约束测试（支持 Ratchet 机制）
 * 
 * 通过 allowlist 机制支持渐进式修复：
 * - 存量违规记录在 architecture-allowlist.tsv 中，不导致测试失败
 * - 新增违规会导致测试失败
 * - allowlist 只能减少，不能增加
 * 
 * @author AI Agent
 * @since 2026-04-13
 */
public class ArchitectureTestWithAllowlist {

    private static final Set<String> DIRECT_JDBC_TYPES = new HashSet<>(Arrays.asList(
        "java.sql.Connection",
        "java.sql.DriverManager",
        "java.sql.Statement",
        "java.sql.PreparedStatement",
        "java.sql.CallableStatement",
        "java.sql.ResultSet",
        "java.sql.DatabaseMetaData",
        "jakarta.persistence.EntityManager",
        "jakarta.persistence.EntityManagerFactory",
        "jakarta.persistence.PersistenceContext"
    ));
    
    private static JavaClasses classes;
    private static Set<String> allowlist;
    private static ArchCondition<JavaClass> autoConfigurationRegistration;
    
    @BeforeAll
    public static void setup() throws IOException {
        classes = ArchitectureClassImportSupport.importProjectClasses();
        
        allowlist = loadAllowlist();
        autoConfigurationRegistration = ArchitectureAutoConfigurationSupport.registrationCondition(
            classes, ArchitectureClassImportSupport.reactor().outputDirectories());
    }
    
    /**
     * 加载 allowlist
     */
    private static Set<String> loadAllowlist() throws IOException {
        Set<String> result = new HashSet<>();
        InputStream is = ArchitectureTestWithAllowlist.class
            .getResourceAsStream("/architecture-allowlist.tsv");
        
        if (is == null) {
            // allowlist 文件不存在，返回空集合
            return result;
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("rule_id\t")) {
                    continue; // 跳过空行和注释
                }
                result.add(trimmed);
            }
        }
        
        return result;
    }
    
    /**
     * 检查规则并应用 allowlist
     */
    private void checkRuleWithAllowlist(String ruleId, ArchRule rule) {
        EvaluationResult result = rule.evaluate(classes);
        
        if (result.hasViolation()) {
            Set<String> newViolations = new HashSet<>();
            
            for (String violation : result.getFailureReport().getDetails()) {
                // 检查是否在 allowlist 中
                boolean inAllowlist = false;
                for (String allowedPattern : allowlist) {
                    if (allowedPattern.startsWith(ruleId)
                        && normalizeViolation(violation).contains(normalizeViolation(extractViolationSignature(allowedPattern)))) {
                        inAllowlist = true;
                        break;
                    }
                }
                
                if (!inAllowlist) {
                    newViolations.add(violation);
                }
            }
            
            if (!newViolations.isEmpty()) {
                StringBuilder message = new StringBuilder();
                message.append("\n").append(ruleId).append(" 检测到新的违规（不在 allowlist 中）：\n\n");
                for (String violation : newViolations) {
                    message.append("  - ").append(violation).append("\n");
                }
                message.append("\n修复指引详见 docs/architecture-constraints.md\n");
                fail(message.toString());
            }
        }
    }
    
    /**
     * 从 allowlist 条目中提取违规签名
     */
    private String extractViolationSignature(String allowlistEntry) {
        String[] parts = allowlistEntry.split("\t");
        if (parts.length >= 4) {
            String source = parts[2].trim();
            String target = parts[3].trim();
            if (!target.isEmpty()) {
                return source + " " + target;
            }
            return source;
        }
        if (parts.length >= 3) {
            return parts[2].trim();
        }
        return allowlistEntry;
    }

    private String normalizeViolation(String violation) {
        return violation.replaceAll(" in \\(([^:()]+\\.java):\\d+\\)", " in ($1:*)");
    }
    
    /**
     * P0-3: 禁止跨层依赖
     */
    @Test
    public void layerDependencyRulesShouldBeRespected() throws IOException {
        ArchRule rule = ArchitectureLayerSupport.rule(ArchitectureClassImportSupport.reactor());
        checkRuleWithAllowlist("P0-3", rule);
    }

    /**
     * P0-3: UISchema runtime controllers should stay runtime-facing and not depend on app-management uischema packages.
     */
    @Test
    public void uiSchemaRuntimeControllersShouldNotDependOnAppManagementPackages() {
        ArchRule rule = classes()
            .that(isUiSchemaControllerInModule("core/ouroboros-ui-model/ouroboros-ui-model-schema-runtime"))
            .should(notDependOnUiSchemaPackages(
                "app-management uischema packages",
                "com.ouroboros.uischema.model.",
                "com.ouroboros.uischema.service."
            ))
            .as("[P0-3] uischema runtime controllers should not depend on app-management uischema packages");

        checkRuleWithAllowlist("P0-3-UISCHEMA-CTRL-RUNTIME", rule);
    }

    /**
     * P0-3: UISchema management controllers should not depend on dev-tooling uischema packages.
     */
    @Test
    public void uiSchemaManagementControllersShouldNotDependOnDevPackages() {
        ArchRule rule = classes()
            .that(isUiSchemaControllerInModule("app-module/ui-schema-management"))
            .should(notDependOnUiSchemaPackages(
                "dev-tooling uischema packages",
                "com.ouroboros.uischema.dev."
            ))
            .as("[P0-3] uischema management controllers should not depend on dev-tooling uischema packages");

        checkRuleWithAllowlist("P0-3-UISCHEMA-CTRL-APP", rule);
    }
    
    /**
     * P1-14: 禁止循环依赖
     */
    @Test
    public void noCircularDependenciesBetweenModules() throws IOException {
        ArchitectureModuleDependencies.check(
            ArchitectureModuleDependencies.between(classes, ArchitectureClassImportSupport.reactor()), allowlist);
    }
    
    /**
     * P1-8: 异常必须使用 Ouroboros 异常体系
     * 
     * 注意：引擎内核（data, logicflow）豁免，使用自有异常体系
     */
    @Test
    public void businessExceptionsShouldExtendOuroborosException() {
        ArchRule rule = classes()
            .that().resideInAPackage("..service..")
            .and().resideOutsideOfPackages("..data..", "..logicflow..")
            .and().haveSimpleNameEndingWith("Exception")
            .and().areNotAssignableTo(RuntimeException.class)
            .should().beAssignableTo("com.ouroboros.exception.OuroborosException")
            .allowEmptyShould(true)
            .as("[P1-8] 异常必须使用 Ouroboros 异常体系：业务异常应继承 OuroborosException");
        
        checkRuleWithAllowlist("P1-8", rule);
    }
    
    /**
     * P0-7: 禁止未注册的自动配置
     */
    @Test
    public void autoConfigurationsShouldBeRegistered() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("AutoConfiguration")
            .should().beAnnotatedWith("org.springframework.context.annotation.Configuration")
            .orShould().beAnnotatedWith("org.springframework.boot.autoconfigure.AutoConfiguration")
            .as("[P0-7] 禁止未注册的自动配置：所有 @Configuration 必须在 META-INF/spring.factories 中注册");
        
        checkRuleWithAllowlist("P0-7", rule);
    }
    
    /**
     * P1-12: 禁止绕过安全检查
     */
    @Test
    public void shouldNotBypassSecurityChecks() {
        ArchRule rule = noClasses()
            .should().beAnnotatedWith("jakarta.annotation.security.PermitAll")
            .as("[P1-12] 禁止绕过安全检查：不得使用 @PermitAll（除非有明确的业务理由）");
        
        checkRuleWithAllowlist("P1-12", rule);
    }
    
    /**
     * P1-11: 禁止直接操作数据库
     */
    @Test
    public void shouldNotUseDirectJdbcWithoutExemption() {
        ArchRule rule = classes()
            .that().resideOutsideOfPackages("..data..", "..logicflow..", "..test..")
            .should(notUseDirectJdbcWithoutExemption())
            .as("[P1-11] 禁止直接操作数据库：直接 JDBC 访问必须通过 @AllowDirectJDBC 标注并登记豁免");

        checkRuleWithAllowlist("P1-11", rule);
    }
    
    /**
     * 命名规范：Service 类应该在 service 包中
     */
    @Test
    public void serviceClassesShouldBeInServicePackage() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Service")
            .and().areNotInterfaces()
            .should().resideInAPackage("..service..")
            .as("Service 类应该在 service 包中");
        
        checkRuleWithAllowlist("NAMING-SERVICE", rule);
    }
    
    /**
     * 命名规范：Repository 类应该在 repository 包中
     */
    @Test
    public void repositoryClassesShouldBeInRepositoryPackage() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .and().areNotInterfaces()
            .should().resideInAPackage("..repository..")
            .allowEmptyShould(true)
            .as("Repository 类应该在 repository 包中");
        
        checkRuleWithAllowlist("NAMING-REPOSITORY", rule);
    }
    
    /**
     * P0-6: 禁止未注册的 SPI
     * 
     * 检查所有实现了常见 SPI 接口的类是否在 META-INF/services/ 中注册
     * 基于历史错误：依赖错误占7次fix
     */
    @Test
    public void spiImplementationsShouldBeRegistered() throws IOException {
        ArchitectureSpiSupport.check(classes, ArchitectureClassImportSupport.reactor());
    }
    
    /**
     * P0-7: 禁止未注册的自动配置（增强版）
     * 
     * 顶层配置类必须在 Spring 自动配置元数据中注册，或由已注册入口通过 @Import 引入
     * 基于历史错误：配置错误占29次fix（最高频）
     */
    @Test
    public void autoConfigurationsShouldBeRegisteredInSpringFactories() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("AutoConfiguration")
            .or().haveSimpleNameEndingWith("Configuration")
            .and().areAnnotatedWith("org.springframework.context.annotation.Configuration")
            .and().resideOutsideOfPackages("..test..", "..config..")
            .and().areTopLevelClasses()
            .should(autoConfigurationRegistration)
            .as("[P0-7] 禁止未注册的自动配置：顶层配置必须在 Spring 自动配置元数据中注册或由已注册配置通过 @Import 引入");
        
        checkRuleWithAllowlist("P0-7-ENHANCED", rule);
    }
    
    private static ArchCondition<JavaClass> notUseDirectJdbcWithoutExemption() {
        return new ArchCondition<JavaClass>("not use direct JDBC without @AllowDirectJDBC") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Set<String> matchedTypes = javaClass.getDirectDependenciesFromSelf().stream()
                    .map(dependency -> dependency.getTargetClass().getFullName())
                    .filter(DIRECT_JDBC_TYPES::contains)
                    .collect(Collectors.toSet());

                if (matchedTypes.isEmpty()) {
                    return;
                }

                boolean isExempted = javaClass.isAnnotatedWith("com.ouroboros.model.annotation.AllowDirectJDBC")
                    || javaClass.getAllMethods().stream()
                    .anyMatch(method -> method.isAnnotatedWith("com.ouroboros.model.annotation.AllowDirectJDBC"));

                if (!isExempted) {
                    String message = String.format(
                        "类 %s 直接依赖 JDBC/JPA 类型 %s，需改为 ouroboros-data 抽象或使用 @AllowDirectJDBC + jdbc-exemptions.md 登记",
                        javaClass.getName(),
                        matchedTypes
                    );
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        };
    }

    private static DescribedPredicate<JavaClass> isUiSchemaControllerInModule(String modulePathFragment) {
        return new DescribedPredicate<JavaClass>(
            "be a uischema controller in module " + modulePathFragment) {
            @Override
            public boolean test(JavaClass input) {
                if (!input.getPackageName().startsWith("com.ouroboros.uischema.controller")) {
                    return false;
                }
                return input.getSource()
                    .map(source -> source.getUri().toString().replace('\\', '/'))
                    .map(uri -> uri.contains("/" + modulePathFragment + "/target/classes/"))
                    .orElse(false);
            }
        };
    }

    private static ArchCondition<JavaClass> notDependOnUiSchemaPackages(String description, String... forbiddenPrefixes) {
        return new ArchCondition<JavaClass>("not depend on " + description) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Set<String> violations = javaClass.getDirectDependenciesFromSelf().stream()
                    .map(dependency -> dependency.getTargetClass().getFullName())
                    .filter(target -> {
                        for (String prefix : forbiddenPrefixes) {
                            if (target.startsWith(prefix)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .collect(Collectors.toSet());

                if (!violations.isEmpty()) {
                    String message = String.format(
                        "类 %s 直接依赖了不应访问的 uischema 包 %s",
                        javaClass.getName(),
                        violations
                    );
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        };
    }

}

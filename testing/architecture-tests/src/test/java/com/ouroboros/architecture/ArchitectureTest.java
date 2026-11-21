package com.ouroboros.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.EvaluationResult;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * 架构约束测试
 * 
 * 验证 P0-3: 禁止跨层依赖
 * 验证 P1-14: 禁止循环依赖
 * 
 * @author AI Agent
 * @since Phase 3
 */
public class ArchitectureTest {
    
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
     * P0-3: 禁止跨层依赖
     * 
     * 验证分层架构：libs → core → security → ability → app/dev → platform
     */
    @Test
    public void layerDependencyRulesShouldBeRespected() throws IOException {
        ArchRule rule = ArchitectureLayerSupport.rule(ArchitectureClassImportSupport.reactor());
        checkRuleWithAllowlist("P0-3", rule);
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
     */
    @Test
    public void businessExceptionsShouldExtendOuroborosException() {
        ArchRule rule = classes()
            .that().resideInAPackage("..service..")
            .and().haveSimpleNameEndingWith("Exception")
            .and().areNotAssignableTo(RuntimeException.class)
            .should().beAssignableTo("com.ouroboros.exception.OuroborosException")
            .allowEmptyShould(true)
            .as("[P1-8] 异常必须使用 Ouroboros 异常体系：业务异常应继承 OuroborosException");
        checkRuleWithAllowlist("P1-8", rule);
    }
    
    /**
     * P0-6: 禁止未注册的 SPI
     * 
     * 注意：此测试需要访问 META-INF/services/ 资源，实际实现需要更复杂的逻辑
     */
    @Test
    public void spiImplementationsShouldBeRegistered() throws IOException {
        ArchitectureSpiSupport.check(classes, ArchitectureClassImportSupport.reactor());
    }
    
    /**
     * P0-7: 禁止未注册的自动配置
     */
    @Test
    public void autoConfigurationsShouldBeRegistered() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("AutoConfiguration")
            .or().haveSimpleNameEndingWith("Configuration")
            .and().areAnnotatedWith("org.springframework.context.annotation.Configuration")
            .and().resideOutsideOfPackages("..test..", "..config..")
            .should(autoConfigurationRegistration)
            .as("[P0-7] 禁止未注册的自动配置：配置必须在 Spring 自动配置元数据中注册或由已注册配置通过 @Import 引入");
        checkRuleWithAllowlist("P0-7-ENHANCED", rule);
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

    private static Set<String> loadAllowlist() throws IOException {
        Set<String> result = new HashSet<String>();
        InputStream is = ArchitectureTest.class.getResourceAsStream("/architecture-allowlist.tsv");
        if (is == null) {
            return result;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("rule_id\t")) {
                    continue;
                }
                result.add(trimmed);
            }
        }
        return result;
    }

    private void checkRuleWithAllowlist(String ruleId, ArchRule rule) {
        EvaluationResult result = rule.evaluate(classes);
        if (!result.hasViolation()) {
            return;
        }

        Set<String> newViolations = new HashSet<String>();
        for (String violation : result.getFailureReport().getDetails()) {
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
            fail(message.toString());
        }
    }

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

}

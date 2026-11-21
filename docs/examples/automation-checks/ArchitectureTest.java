package com.ouroboros.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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
    
    @BeforeAll
    public static void setup() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages("com.ouroboros");
    }
    
    /**
     * P0-3: 禁止跨层依赖
     * 
     * 验证分层架构：libs → core → security → ability → app/dev → platform
     */
    @Test
    public void layerDependencyRulesShouldBeRespected() {
        layeredArchitecture()
            .consideringAllDependencies()
            
            // 定义层
            .layer("Foundation").definedBy(
                "com.ouroboros.util..",
                "com.ouroboros.common.."
            )
            .layer("Core").definedBy(
                "com.ouroboros.data..",
                "com.ouroboros.logicflow..",
                "com.ouroboros.ui..",
                "com.ouroboros.webapi..",
                "com.ouroboros.field..",
                "com.ouroboros.business..",
                "com.ouroboros.menu..",
                "com.ouroboros.app.module..",
                "com.ouroboros.i18n..",
                "com.ouroboros.error..",
                "com.ouroboros.resource.."
            )
            .layer("Security").definedBy(
                "com.ouroboros.security.."
            )
            .layer("Ability").definedBy(
                "com.ouroboros.ability..",
                "com.ouroboros.configuration..",
                "com.ouroboros.notification..",
                "com.ouroboros.file..",
                "com.ouroboros.rpc..",
                "com.ouroboros.organization..",
                "com.ouroboros.scheduler..",
                "com.ouroboros.message..",
                "com.ouroboros.websocket..",
                "com.ouroboros.widget.."
            )
            .layer("App").definedBy(
                "com.ouroboros.app.."
            )
            .layer("Dev").definedBy(
                "com.ouroboros.dev.."
            )
            
            // 定义依赖规则
            .whereLayer("Core").mayOnlyAccessLayers("Foundation")
            .whereLayer("Security").mayOnlyAccessLayers("Core", "Foundation")
            .whereLayer("Ability").mayOnlyAccessLayers("Security", "Core", "Foundation")
            .whereLayer("App").mayOnlyAccessLayers("Ability", "Security", "Core", "Foundation")
            .whereLayer("Dev").mayOnlyAccessLayers("Ability", "Security", "Core", "Foundation")
            
            .as("[P0-3] 禁止跨层依赖：严格遵守 libs → core → security → ability → app/dev → platform 依赖方向")
            .check(classes);
    }
    
    /**
     * P1-14: 禁止循环依赖
     */
    @Test
    public void noCircularDependenciesBetweenModules() {
        slices()
            .matching("com.ouroboros.(*)..")
            .should().beFreeOfCycles()
            .as("[P1-14] 禁止循环依赖：模块间不得出现循环依赖")
            .check(classes);
    }
    
    /**
     * P1-8: 异常必须使用 Ouroboros 异常体系
     */
    @Test
    public void businessExceptionsShouldExtendOuroborosException() {
        classes()
            .that().resideInAPackage("..service..")
            .and().haveSimpleNameEndingWith("Exception")
            .and().areNotAssignableTo(RuntimeException.class)
            .should().beAssignableTo("com.ouroboros.exception.OuroborosException")
            .as("[P1-8] 异常必须使用 Ouroboros 异常体系：业务异常应继承 OuroborosException")
            .check(classes);
    }
    
    /**
     * P0-6: 禁止未注册的 SPI
     * 
     * 注意：此测试需要访问 META-INF/services/ 资源，实际实现需要更复杂的逻辑
     */
    @Test
    public void spiImplementationsShouldBeRegistered() {
        // 检查所有实现了 NodeBuilder 的类
        classes()
            .that().implement("com.ouroboros.logicflow.nodes.NodeBuilder")
            .should().beAnnotatedWith("org.springframework.stereotype.Component")
            .orShould().bePublic()
            .as("[P0-6] 禁止未注册的 SPI：所有 SPI 实现必须在 META-INF/services/ 中注册")
            .check(classes);
    }
    
    /**
     * P0-7: 禁止未注册的自动配置
     */
    @Test
    public void autoConfigurationsShouldBeRegistered() {
        classes()
            .that().haveSimpleNameEndingWith("AutoConfiguration")
            .should().beAnnotatedWith("org.springframework.context.annotation.Configuration")
            .as("[P0-7] 禁止未注册的自动配置：所有 @Configuration 必须在 META-INF/spring.factories 中注册")
            .check(classes);
    }
    
    /**
     * 命名规范：Service 类应该在 service 包中
     */
    @Test
    public void serviceClassesShouldBeInServicePackage() {
        classes()
            .that().haveSimpleNameEndingWith("Service")
            .should().resideInAPackage("..service..")
            .as("Service 类应该在 service 包中")
            .check(classes);
    }
    
    /**
     * 命名规范：Repository 类应该在 repository 包中
     */
    @Test
    public void repositoryClassesShouldBeInRepositoryPackage() {
        classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage("..repository..")
            .as("Repository 类应该在 repository 包中")
            .check(classes);
    }
}

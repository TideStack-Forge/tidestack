package com.ouroboros.architecture;

import static com.ouroboros.architecture.ArchitectureClassImportSupport.resideInModule;
import static com.ouroboros.architecture.ArchitectureModuleDependencies.assertNoProductionDependency;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;

/** P1-16: public contracts belong to core; business consumers cannot bind their implementations. */
class ContractDependencyRulesTest {
    private static JavaClasses classes;

    @BeforeAll
    static void importReactor() {
        classes = ArchitectureClassImportSupport.importProjectClasses();
    }

    @Test
    void coreArtifactsCannotReachRuntimeImplementationsThroughProductionDependencies() throws Exception {
        var graph = ArchitectureModuleDependencies.effectiveMavenDependencies();
        var implementations = graph.values().stream().flatMap(List::stream)
            .map(ArchitectureModuleDependencies.Dependency::artifact)
            .filter(name -> name.endsWith("-runtime") || name.endsWith("-default"))
            .collect(Collectors.toSet());
        for (String module : graph.keySet()) {
            if (module.endsWith("-core")) {
                assertNoProductionDependency(graph, module, implementations);
            }
        }
    }

    @Test
    void repairedConsumersCannotRegainImplementationArtifacts() throws Exception {
        var graph = ArchitectureModuleDependencies.effectiveMavenDependencies();
        for (String module : List.of("common-service", "ouroboros-scheduler-quartz", "ouroboros-message-queue-runtime",
                "ouroboros-ai-openai-runtime", "ouroboros-ai-anthropic-runtime", "ouroboros-ai-mcp-runtime",
                "ouroboros-ai-rag-runtime", "ouroboros-widget-manage", "ouroboros-rpc-model-dev",
                "ouroboros-rpc-model-logicflow-node", "ouroboros-dev-app-management")) {
            assertNoProductionDependency(graph, module, Set.of("ouroboros-ai-runtime", "ouroboros-widget-runtime",
                "ouroboros-rpc-model-runtime", "ouroboros-organization-structure-runtime", "ouroboros-logicflow-runtime"));
        }
        for (String vendor : List.of("dingtalk", "wecom", "feishu", "fake")) {
            assertNoProductionDependency(graph, "ouroboros-approval-workflow-external-integration-" + vendor,
                Set.of("ouroboros-approval-workflow-external-integration-runtime"));
        }
        for (String module : List.of("ouroboros-business-model-runtime", "ouroboros-business-model-dev",
                "ouroboros-statistics-reporting-runtime")) {
            assertNoProductionDependency(graph, module, Set.of("ouroboros-business-model-default"));
        }
        assertNoProductionDependency(graph, "ouroboros-approval-workflow-dynamic-form", Set.of("dynamic-form-runtime"));
        assertNoProductionDependency(graph, "ouroboros-user-setting-runtime", Set.of("ouroboros-web-security-runtime"));
        assertNoProductionDependency(graph, "ouroboros-approval-workflow-external-integration-runtime",
            Set.of("ouroboros-web-security-runtime"));
        assertNoProductionDependency(graph, "common-service", Set.of("ouroboros-data-typed-runtime", "ouroboros-data-runtime"));
        assertNoProductionDependency(graph, "ouroboros-organization-structure-runtime", Set.of("ouroboros-configuration-runtime"));
    }

    @Test
    void platformAssemblyRetainsDynamicFormProviderAndApprovalAdapter() throws Exception {
        var graph = ArchitectureModuleDependencies.effectiveMavenDependencies();
        for (String platform : List.of("ouroboros-develop", "ouroboros-mothership")) {
            for (String artifact : List.of("dynamic-form-runtime", "ouroboros-approval-workflow-dynamic-form")) {
                assertTrue(graph.get(platform).stream().anyMatch(dependency -> dependency.artifact().equals(artifact)
                    && Set.of("compile", "runtime").contains(dependency.scope())), platform + " requires " + artifact);
            }
        }
    }

    @Test
    void platformAssemblyRetainsSecurityRuntimeThroughAbilityAggregation() throws Exception {
        var graph = ArchitectureModuleDependencies.effectiveMavenDependencies();
        for (String platform : List.of("ouroboros-develop", "ouroboros-mothership")) {
            assertTrue(graph.get(platform).stream().anyMatch(dependency ->
                dependency.artifact().equals("ouroboros-ability-all-runtime")
                    && Set.of("compile", "runtime").contains(dependency.scope())),
                platform + " requires ouroboros-ability-all-runtime");
        }
        assertTrue(graph.get("ouroboros-ability-all-runtime").stream().anyMatch(dependency ->
            dependency.artifact().equals("ouroboros-web-security-runtime")
                && Set.of("compile", "runtime").contains(dependency.scope())),
            "Ability aggregation requires security runtime for external authentication and secret sources");
    }

    @Test
    void businessCallsUseContractsAcrossModuleBoundaries() {
        noClasses().that(resideInModule(
            "ability/ouroboros-approval-workflow-external-integration/ouroboros-approval-workflow-external-integration-runtime"))
            .should().dependOnClassesThat(resideInModule(
                "security/ouroboros-web-security/ouroboros-web-security-runtime")).check(classes);
        for (String module : List.of("core/ouroboros-business-model/ouroboros-business-model-runtime",
                "core/ouroboros-business-model/ouroboros-business-model-dev",
                "core/ouroboros-statistics-reporting/ouroboros-statistics-reporting-runtime")) {
            noClasses().that(resideInModule(module)).should().dependOnClassesThat(
                resideInModule("core/ouroboros-business-model/ouroboros-business-model-default")).check(classes);
        }
        noClasses().that().resideInAnyPackage("com.ouroboros.statistics.reporting.dev.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.ouroboros.statistics.reporting.runtime.controller..").check(classes);
        noClasses().that().resideInAnyPackage("com.ouroboros.statistics.reporting.dev.controller..")
            .should().dependOnClassesThat().haveNameMatching(
                "com\\.ouroboros\\.statistics\\.reporting\\.runtime\\.(center|execution)\\.Default.*").check(classes);
        noClasses().that().resideInAnyPackage("com.ouroboros.webapi.dev.controller..", "com.ouroboros.uimodel.dev.controller..",
                "com.ouroboros.devhelper.dev.metadata.provider.adapter..", "com.ouroboros.workflow.approval.dev.candidate..")
            .should().dependOnClassesThat().resideInAnyPackage("com.ouroboros.logicflow.dev.service.impl..",
                "com.ouroboros.uischema.dev.service.impl..", "com.ouroboros.businessmodel.dev.service.impl..",
                "com.ouroboros.dev.app.service.impl..").check(classes);
    }
}

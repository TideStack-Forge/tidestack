package com.ouroboros.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static org.junit.jupiter.api.Assertions.*;
import static com.ouroboros.architecture.ArchitectureModuleDependencies.assertNoProductionDependency;
import com.ouroboros.architecture.ArchitectureModuleDependencies.Dependency;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;

class StorageDependencyRulesTest {
    private static final String UPLOAD = "ability/ouroboros-file-upload/";
    private static final String RESOURCE = "core/ouroboros-resource/";
    private static final String STORAGE = "libs/storage/";
    private static JavaClasses classes;

    @BeforeAll
    static void importReactor() {
        classes = ArchitectureClassImportSupport.importProjectClasses();
    }

    @Test
    void businessCodeDependsOnContractsInsteadOfStorageImplementations() {
        noClasses().that(ArchitectureClassImportSupport.resideInModule(RESOURCE + "ouroboros-resource-runtime-manage"))
            .should().dependOnClassesThat().resideInAnyPackage("com.ouroboros.resource.runtime..", "com.ouroboros.storage..")
            .check(classes);
        noClasses().that().resideInAnyPackage("com.ouroboros.upload.controller..", "com.ouroboros.upload.runtime.controller..",
                "com.ouroboros.dev.app.controller..", "com.ouroboros.documentmanagement.service..")
            .should().dependOnClassesThat(resideInAnyPackage("com.ouroboros.storage..")
                .or(ArchitectureClassImportSupport.resideInModule(UPLOAD + "ouroboros-file-upload-storage"))
                .or(ArchitectureClassImportSupport.resideInModule("app-module/document-management/document-management-storage")))
            .check(classes);
    }

    @Test
    void coreAndAdaptersCannotDependOutwardOnBackendImplementations() throws Exception {
        noClasses().that(ArchitectureClassImportSupport.resideInModule(UPLOAD + "ouroboros-file-upload-core"))
            .should().dependOnClassesThat().resideInAnyPackage("com.ouroboros.upload.service..",
                "com.ouroboros.storage.runtime..", "com.ouroboros.storage.filesystem..",
                "com.ouroboros.storage.s3..", "com.ouroboros.lock..")
            .check(classes);
        var reactor = ArchitectureClassImportSupport.reactor();
        for (String implementation : List.of("com.ouroboros.upload.service.BlobUploadStorageService",
                "com.ouroboros.upload.TimerCleanUpScheduler")) {
            assertEquals(Optional.of(UPLOAD + "ouroboros-file-upload-storage"),
                reactor.moduleOf(classes.get(implementation)), implementation);
        }
        noClasses().that(ArchitectureClassImportSupport.resideInModule(STORAGE + "ouroboros-storage-core"))
            .should().dependOnClassesThat().resideInAnyPackage("com.ouroboros.storage.runtime..",
                "com.ouroboros.storage.filesystem..", "com.ouroboros.storage.s3..")
            .check(classes);
        noClasses().that(ArchitectureClassImportSupport.resideInModule(UPLOAD + "ouroboros-file-upload-storage")
                .or(ArchitectureClassImportSupport.resideInModule(STORAGE + "ouroboros-storage-runtime")))
            .should().dependOnClassesThat().resideInAnyPackage("com.ouroboros.storage.filesystem..", "com.ouroboros.storage.s3..")
            .check(classes);
    }

    @Test
    void effectiveMavenProductionDependenciesRespectTheSameBoundaries() throws Exception {
        Path reactorManifest = Path.of(System.getProperty("architecture.reactorManifest"));
        List<String> rows = Files.readAllLines(reactorManifest.resolveSibling("architecture-maven-dependencies.tsv"));
        assertEquals(Files.readAllLines(reactorManifest).getFirst(), rows.getFirst());
        Map<String, List<Dependency>> dependencies = new HashMap<>();
        for (String row : rows.subList(1, rows.size())) {
            String[] values = row.split("\t", -1);
            assertEquals(3, values.length, row);
            dependencies.computeIfAbsent(values[0], key -> new ArrayList<>()).add(new Dependency(values[1], values[2]));
        }

        var physicalBackends = Set.of("ouroboros-storage-filesystem", "ouroboros-storage-s3");
        for (String module : List.of("ouroboros-file-upload-core", "ouroboros-file-upload-controller", "ouroboros-file-upload-storage")) {
            assertNoProductionDependency(dependencies, UPLOAD + module, physicalBackends);
        }
        assertNoProductionDependency(dependencies, UPLOAD + "ouroboros-file-upload-core",
            Set.of("ouroboros-file-upload-storage", "ouroboros-storage-runtime"));
        assertNoProductionDependency(dependencies, UPLOAD + "ouroboros-file-upload-controller",
            Set.of("ouroboros-file-upload-storage", "ouroboros-file-upload-runtime", "ouroboros-storage-runtime"));
        assertNoProductionDependency(dependencies, RESOURCE + "ouroboros-resource-runtime-manage",
            Set.of("ouroboros-resource-runtime", "ouroboros-storage-runtime", "ouroboros-storage-filesystem", "ouroboros-storage-s3"));
        assertNoProductionDependency(dependencies, RESOURCE + "ouroboros-resource-dev",
            Set.of("ouroboros-resource-runtime", "ouroboros-storage-runtime", "ouroboros-storage-filesystem", "ouroboros-storage-s3"));
        assertNoProductionDependency(dependencies, "app-module/document-management/document-management-service",
            Set.of("document-management-storage", "ouroboros-storage-runtime", "ouroboros-storage-filesystem", "ouroboros-storage-s3"));
        for (String module : List.of("ouroboros-storage-core", "ouroboros-storage-runtime")) {
            assertNoProductionDependency(dependencies, STORAGE + module, physicalBackends);
        }
    }

    @Test
    void dependencyGateRejectsTransitiveImplementationLeakageButAllowsTestFixtures() {
        var dependencies = Map.of(
            "consumer", List.of(new Dependency("adapter", "compile"), new Dependency("backend", "test")),
            "adapter", List.of(new Dependency("backend", "runtime")));
        AssertionError failure = assertThrows(AssertionError.class,
            () -> assertNoProductionDependency(dependencies, "ability/consumer", Set.of("backend")));
        assertTrue(failure.getMessage().contains("consumer -> adapter -> backend"));
        assertNoProductionDependency(Map.of("consumer", List.of(new Dependency("backend", "test"))),
            "ability/consumer", Set.of("backend"));
    }

}

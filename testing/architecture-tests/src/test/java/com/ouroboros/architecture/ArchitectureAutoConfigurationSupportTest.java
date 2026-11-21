package com.ouroboros.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.core.domain.JavaClass;

class ArchitectureAutoConfigurationSupportTest {
    @TempDir
    Path root;
    private Path output;
    private JavaClasses classes;

    @BeforeEach
    void compileConfigurationGraph() throws IOException {
        output = root.resolve("module/target/classes");
        Files.createDirectories(output);
        Path annotation = root.resolve("sources/org/springframework/context/annotation/Import.java");
        Files.createDirectories(annotation.getParent());
        Files.writeString(annotation, """
            package org.springframework.context.annotation;
            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            public @interface Import { Class<?>[] value(); }
            """);
        Path configurations = root.resolve("sources/fixture/RootConfiguration.java");
        Files.createDirectories(configurations.getParent());
        Files.writeString(configurations, """
            package fixture;
            import org.springframework.context.annotation.Import;
            @Import({ChildConfiguration.class, CycleAConfiguration.class})
            public class RootConfiguration { }
            @Import(LeafConfiguration.class) class ChildConfiguration { }
            class LeafConfiguration { }
            @Import(CycleBConfiguration.class) class CycleAConfiguration { }
            @Import(CycleAConfiguration.class) class CycleBConfiguration { }
            class OrphanConfiguration { }
            """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-d", output.toString(), annotation.toString(), configurations.toString()));
        classes = new ClassFileImporter().importPath(output);
    }

    @Test
    void factoriesEntryRegistersItsImportedConfigurationGraph() throws IOException {
        metadata(output, "META-INF/spring.factories", """
            org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\
              fixture.RootConfiguration,\\
              fixture.CycleAConfiguration
            unrelated.key=fixture.OrphanConfiguration
            """);

        var condition = ArchitectureAutoConfigurationSupport.registrationCondition(classes, List.of(output));

        assertRegistered(condition, "RootConfiguration");
        assertRegistered(condition, "ChildConfiguration");
        assertRegistered(condition, "LeafConfiguration");
        assertRegistered(condition, "CycleAConfiguration");
        assertRegistered(condition, "CycleBConfiguration");
        assertRejected(condition, "OrphanConfiguration");
    }

    @Test
    void importsOnlyEntryRegistersItsImportedConfigurationGraph() throws IOException {
        metadata(output, "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
            "# registered entry\n\nfixture.RootConfiguration # protocol entry\n");

        var condition = ArchitectureAutoConfigurationSupport.registrationCondition(classes, List.of(output));

        assertRegistered(condition, "RootConfiguration");
        assertRegistered(condition, "ChildConfiguration");
        assertRegistered(condition, "LeafConfiguration");
        assertRejected(condition, "OrphanConfiguration");
    }

    @Test
    void unregisteredImportCyclesDoNotBecomeDiscoveryRoots() throws IOException {
        var condition = ArchitectureAutoConfigurationSupport.registrationCondition(classes, List.of(output));

        assertRejected(condition, "CycleAConfiguration");
        assertRejected(condition, "CycleBConfiguration");
        assertRejected(condition, "OrphanConfiguration");
    }

    @Test
    void metadataOutsideTheSelectedModuleOutputsCannotRegisterAConfiguration() throws IOException {
        metadata(root.resolve("scratch/target/classes"), "META-INF/spring.factories",
            "org.springframework.boot.autoconfigure.EnableAutoConfiguration=fixture.OrphanConfiguration\n");
        metadata(root.resolve("module/src/main/resources"),
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
            "fixture.RootConfiguration\n");

        var condition = ArchitectureAutoConfigurationSupport.registrationCondition(classes, List.of(output));

        assertRejected(condition, "RootConfiguration");
        assertRejected(condition, "ChildConfiguration");
        assertRejected(condition, "OrphanConfiguration");
    }

    @Test
    void metadataFromAnotherSelectedModuleIsUsed() throws IOException {
        Path resources = root.resolve("resource-module/target/classes");
        metadata(resources, "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
            "fixture.RootConfiguration\n");

        var condition = ArchitectureAutoConfigurationSupport.registrationCondition(classes, List.of(output, resources));

        assertRegistered(condition, "RootConfiguration");
        assertRegistered(condition, "ChildConfiguration");
    }

    @Test
    void unreadableMetadataCannotSilentlyPassRegistrationChecks() throws IOException {
        Files.createDirectories(output.resolve("META-INF/spring.factories"));

        assertThrows(IOException.class,
            () -> ArchitectureAutoConfigurationSupport.registrationCondition(classes, List.of(output)));
    }

    private void metadata(Path directory, String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void assertRegistered(ArchCondition<JavaClass> condition, String name) {
        var events = ConditionEvents.Factory.create();
        condition.check(classes.get("fixture." + name), events);
        assertFalse(events.containViolation(), name);
    }

    private void assertRejected(ArchCondition<JavaClass> condition, String name) {
        var events = ConditionEvents.Factory.create();
        condition.check(classes.get("fixture." + name), events);
        assertTrue(events.containViolation(), name);
    }
}

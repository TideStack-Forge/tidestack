package com.ouroboros.architecture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

final class ArchitectureAutoConfigurationSupport {
    private static final String AUTO_CONFIGURATION_KEY =
        "org.springframework.boot.autoconfigure.EnableAutoConfiguration";
    private static final String IMPORTS_PATH =
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private ArchitectureAutoConfigurationSupport() {
    }

    static ArchCondition<JavaClass> registrationCondition(JavaClasses classes, List<Path> outputs)
        throws IOException {
        Set<String> registered = new HashSet<>();
        for (Path output : outputs) {
            Path factories = output.resolve("META-INF/spring.factories");
            if (Files.exists(factories)) {
                Properties properties = new Properties();
                try (InputStream input = Files.newInputStream(factories)) {
                    properties.load(input);
                }
                String entries = properties.getProperty(AUTO_CONFIGURATION_KEY, "");
                for (String entry : entries.split(",")) {
                    if (!entry.isBlank()) {
                        registered.add(entry.trim());
                    }
                }
            }
            Path imports = output.resolve(IMPORTS_PATH);
            if (Files.exists(imports)) {
                for (String line : Files.readAllLines(imports, StandardCharsets.UTF_8)) {
                    String entry = line.split("#", 2)[0].trim();
                    if (!entry.isEmpty()) {
                        registered.add(entry);
                    }
                }
            }
        }

        var pending = new ArrayDeque<>(registered);
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            if (!classes.contain(name)) {
                continue;
            }
            Set<String> imported = new HashSet<>();
            classes.get(name).tryGetAnnotationOfType("org.springframework.context.annotation.Import")
                .ifPresent(annotation -> collectImportedNames(imported, annotation.getProperties().get("value")));
            for (String child : imported) {
                if (registered.add(child)) {
                    pending.addLast(child);
                }
            }
        }

        return new ArchCondition<>(
            "be registered in Spring auto-configuration metadata or imported by a registered configuration"
        ) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (!registered.contains(javaClass.getName())) {
                    events.add(SimpleConditionEvent.violated(javaClass, String.format(
                        "类 %s 标注了 @Configuration 但未在 META-INF/spring.factories 中注册，"
                            + "也未在 AutoConfiguration.imports 中注册或被已注册配置通过 @Import 引入",
                        javaClass.getName())));
                }
            }
        };
    }

    private static void collectImportedNames(Set<String> names, Object value) {
        if (value instanceof JavaClass imported) {
            names.add(imported.getName());
        } else if (value instanceof Object[] values) {
            for (Object element : values) {
                collectImportedNames(names, element);
            }
        }
    }
}

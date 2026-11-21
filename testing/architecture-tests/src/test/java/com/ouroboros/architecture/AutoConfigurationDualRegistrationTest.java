package com.ouroboros.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutoConfigurationDualRegistrationTest {

    private static final String AUTO_CONFIGURATION_KEY =
        "org.springframework.boot.autoconfigure.EnableAutoConfiguration";
    @Test
    void springFactoriesEntriesShouldMatchSiblingAutoConfigurationImports() throws IOException {
        Path projectRoot = resolveRepoRoot();
        List<String> mismatches = findRegistrationMismatches(projectRoot);
        if (!mismatches.isEmpty()) {
            fail("Spring Boot auto-configuration registration mismatches:\n  - "
                + String.join("\n  - ", mismatches));
        }
    }

    @Test
    void duplicateClassNamesAcrossModulesDoNotHideMissingSiblingImports(
        @TempDir Path projectRoot
    ) throws IOException {
        String className = "com.example.SharedAutoConfiguration";
        createRegistration(projectRoot.resolve("module-one"), className, true);
        createRegistration(projectRoot.resolve("module-two"), className, false);

        List<String> mismatches = findRegistrationMismatches(projectRoot);

        assertEquals(1, mismatches.size());
        assertTrue(mismatches.get(0).contains(
            "module-two/src/main/resources/META-INF/spring.factories missing entries: " + className
        ));
    }

    private static List<String> findRegistrationMismatches(Path projectRoot) throws IOException {
        List<String> mismatches = new ArrayList<String>();
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            for (Path file : paths
                .filter(Files::isRegularFile)
                .filter(path -> isSpringFactoriesMetadata(projectRoot, path))
                .collect(Collectors.toList())) {
                Set<String> springFactoriesEntries = readSpringFactoriesEntries(file);
                Path importsFile = file.getParent().resolve("spring").resolve(
                    "org.springframework.boot.autoconfigure.AutoConfiguration.imports"
                );
                Set<String> importsEntries = Files.exists(importsFile)
                    ? readAutoConfigurationImports(importsFile)
                    : Collections.<String>emptySet();

                Set<String> missingImports = new TreeSet<String>(springFactoriesEntries);
                missingImports.removeAll(importsEntries);
                Set<String> extraImports = new TreeSet<String>(importsEntries);
                extraImports.removeAll(springFactoriesEntries);
                String relativeFactories = projectRoot.relativize(file).toString();
                if (!missingImports.isEmpty()) {
                    mismatches.add(relativeFactories + " missing entries: "
                        + String.join(", ", missingImports));
                }
                if (!extraImports.isEmpty()) {
                    mismatches.add(projectRoot.relativize(importsFile) + " extra entries: "
                        + String.join(", ", extraImports));
                }
            }
        }
        return mismatches;
    }

    private static boolean isSpringFactoriesMetadata(Path projectRoot, Path path) {
        return !isExcluded(projectRoot, path)
            && path.toString().endsWith("META-INF/spring.factories");
    }

    private static boolean isExcluded(Path projectRoot, Path path) {
        String value = path.toString();
        return value.contains("/legacy-compat/")
            || value.contains("/micro-app/")
            || value.contains("/node_modules/")
            || value.contains("/target/")
            || projectRoot.relativize(path).startsWith(".worktrees")
            || value.contains("/.ai-workflows/")
            || value.contains("/.sisyphus/")
            || value.contains("/.aider-desk/")
            || value.contains("/.gstack/");
    }

    private static Set<String> readSpringFactoriesEntries(Path file) throws IOException {
        Set<String> result = new HashSet<String>();
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(file)) {
            properties.load(inputStream);
        }
        collectDelimitedEntries(result, properties.getProperty(AUTO_CONFIGURATION_KEY));
        return result;
    }

    private static Set<String> readAutoConfigurationImports(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return reader.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toSet());
        }
    }

    private static void collectDelimitedEntries(Set<String> result, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.replace("\\", "").trim();
        if (normalized.isEmpty()) {
            return;
        }
        for (String entry : normalized.split(",")) {
            String candidate = entry.trim();
            if (!candidate.isEmpty()) {
                result.add(candidate);
            }
        }
    }

    private static void createRegistration(
        Path moduleRoot,
        String className,
        boolean includeImports
    ) throws IOException {
        Path metaInf = moduleRoot.resolve("src/main/resources/META-INF");
        Files.createDirectories(metaInf);
        Files.write(
            metaInf.resolve("spring.factories"),
            Collections.singletonList(AUTO_CONFIGURATION_KEY + "=" + className),
            StandardCharsets.UTF_8
        );
        if (includeImports) {
            Path importsFile = metaInf.resolve("spring").resolve(
                "org.springframework.boot.autoconfigure.AutoConfiguration.imports"
            );
            Files.createDirectories(importsFile.getParent());
            Files.write(
                importsFile,
                Collections.singletonList(className),
                StandardCharsets.UTF_8
            );
        }
    }

    private static Path resolveRepoRoot() {
        String mavenRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (mavenRoot != null && !mavenRoot.trim().isEmpty()) {
            return Paths.get(mavenRoot).toAbsolutePath().normalize();
        }
        return Paths.get("").toAbsolutePath().normalize();
    }
}

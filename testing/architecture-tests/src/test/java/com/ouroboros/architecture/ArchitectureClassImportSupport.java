package com.ouroboros.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

final class ArchitectureClassImportSupport {

    private ArchitectureClassImportSupport() {
    }

    static JavaClasses importProjectClasses() {
        try {
            return reactor().importClasses();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load complete architecture reactor; run Maven from "
                + "the repository root and compile production modules first", e);
        }
    }

    static ArchitectureReactor reactor() throws IOException {
        String manifest = System.getProperty("architecture.reactorManifest");
        if (manifest == null || manifest.isBlank()) {
            throw new IllegalStateException("Missing architecture reactor manifest. Run "
                + "mvn -pl testing/architecture-tests -am test from the repository root");
        }
        return ArchitectureReactor.read(resolveRepoRoot(), Paths.get(manifest));
    }

    static DescribedPredicate<JavaClass> resideInModule(String modulePath) {
        String normalizedModulePath = modulePath.replace('\\', '/');
        return DescribedPredicate.describe(
            "reside in Maven module " + normalizedModulePath,
            javaClass -> javaClass.getSource()
                .map(source -> source.getUri().toString().replace('\\', '/'))
                .map(uri -> uri.contains("/" + normalizedModulePath + "/target/classes/"))
                .orElse(false)
        );
    }

    private static Path resolveRepoRoot() {
        String mavenRoot = System.getProperty("maven.multiModuleProjectDirectory");
        Path current = mavenRoot != null && !mavenRoot.isBlank()
            ? Paths.get(mavenRoot).toAbsolutePath().normalize() : Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                && Files.exists(current.resolve("testing/architecture-tests/pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }

        throw new IllegalStateException("Cannot locate architecture repository root");
    }

    static boolean isExcludedModule(Path module) {
        return loadScopeExclusionMatchers().stream()
            .anyMatch(matcher -> matcher.matches(module.resolve("target/classes")));
    }

    private static List<PathMatcher> loadScopeExclusionMatchers() {
        InputStream is = ArchitectureClassImportSupport.class
            .getResourceAsStream("/architecture-scope-exclusions.tsv");
        if (is == null) {
            return List.of();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .map(line -> line.split("\t", 2)[0].trim())
                .filter(glob -> !glob.isEmpty())
                .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob + "/target/classes"))
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load architecture scope exclusions", e);
        }
    }
}

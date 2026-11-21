package com.ouroboros.architecture;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ApiEndpointCanonicalPathTest {

    private static final Pattern TRAILING_API_LITERAL = Pattern.compile(
        "([\"'`])((?:(?:GET|POST|PUT|DELETE|PATCH) |(?:get|post|put|delete|patch):)?/api[^\"'`\\s<>)]*/)(\\1)"
    );
    private static final Pattern API_MAPPING_LITERAL = Pattern.compile("[\"'](/api[^\"']*/)[\"']");
    private static final List<String> SCAN_ROOTS = Arrays.asList(
        "app-module",
        "ability",
        "dev-module",
        "core",
        "frontends",
        "security",
        "platform"
    );

    @Test
    void controllerApiMappingsShouldUseNoTrailingSlashCanonicalPath() throws IOException {
        List<String> violations = new ArrayList<String>();
        Path repoRoot = resolveRepoRoot();

        for (Path file : sourceFiles(repoRoot)) {
            if (!file.toString().endsWith(".java")) {
                continue;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!line.contains("Mapping") || !line.contains("/api")) {
                    continue;
                }
                Matcher matcher = API_MAPPING_LITERAL.matcher(line);
                while (matcher.find()) {
                    violations.add(formatViolation(repoRoot, file, i + 1, matcher.group(1)));
                }
            }
        }

        failOnViolations(
            "Controller @*Mapping API paths must use the canonical no-trailing-slash form.",
            violations
        );
    }

    @Test
    void metadataAndFrontendApiEndpointsShouldUseNoTrailingSlashCanonicalPath() throws IOException {
        List<String> violations = new ArrayList<String>();
        Path repoRoot = resolveRepoRoot();

        for (Path file : sourceFiles(repoRoot)) {
            if (!shouldScanEndpointContract(repoRoot, file)) {
                continue;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!isEndpointContext(line)) {
                    continue;
                }
                Matcher matcher = TRAILING_API_LITERAL.matcher(line);
                while (matcher.find()) {
                    String suffix = line.substring(matcher.end()).trim();
                    if (suffix.startsWith("+")) {
                        continue;
                    }
                    violations.add(formatViolation(repoRoot, file, i + 1, matcher.group(2)));
                }
            }
        }

        failOnViolations(
            "API endpoint strings in metadata and frontend source must use the canonical no-trailing-slash form.",
            violations
        );
    }

    private static List<Path> sourceFiles(Path repoRoot) throws IOException {
        List<Path> result = new ArrayList<Path>();
        for (String scanRoot : SCAN_ROOTS) {
            Path root = repoRoot.resolve(scanRoot);
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                result.addAll(paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !isExcluded(repoRoot, path))
                    .collect(Collectors.toList()));
            }
        }
        return result;
    }

    private static boolean shouldScanEndpointContract(Path repoRoot, Path file) {
        String relativePath = repoRoot.relativize(file).toString();
        if (relativePath.contains("/src/main/resources/META-INF/ouroboros/")) {
            return hasAnyExtension(relativePath, ".json", ".ftl");
        }
        if (relativePath.contains("/src/main/resources/META-INF/editor-schema-templates/")) {
            return hasAnyExtension(relativePath, ".json", ".ftl");
        }
        if (relativePath.startsWith("frontends/web/") && relativePath.contains("/src/")) {
            return hasAnyExtension(relativePath, ".js", ".jsx", ".ts", ".tsx", ".vue");
        }
        return false;
    }

    private static boolean isEndpointContext(String line) {
        String trimmed = line.trim();
        return trimmed.matches("\"(?:url|api|initApi|validateApi)\"\\s*:.*")
            || trimmed.matches("(?:url|api|initApi|validateApi)\\s*:.*")
            || trimmed.matches("\"(?:GET|POST|PUT|DELETE|PATCH) /api.*")
            || trimmed.contains(".get(")
            || trimmed.contains(".post(")
            || trimmed.contains(".put(")
            || trimmed.contains(".delete(")
            || trimmed.contains(".patch(")
            || trimmed.contains("fetch(");
    }

    private static boolean hasAnyExtension(String path, String... extensions) {
        for (String extension : extensions) {
            if (path.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExcluded(Path repoRoot, Path path) {
        String value = path.toString();
        Path relativePath = repoRoot.relativize(path);
        return value.contains("/target/")
            || value.contains("/node_modules/")
            || value.contains("/dist/")
            || value.contains("/build/")
            || relativePath.startsWith(".worktrees")
            || value.contains("/.ai-workflows/")
            || value.contains("/.sisyphus/")
            || value.contains("/.aider-desk/")
            || value.contains("/.gstack/");
    }

    private static Path resolveRepoRoot() {
        String mavenRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (mavenRoot != null && !mavenRoot.trim().isEmpty()) {
            return Paths.get(mavenRoot).toAbsolutePath().normalize();
        }
        return Paths.get("").toAbsolutePath().normalize();
    }

    private static String formatViolation(Path repoRoot, Path file, int line, String value) {
        return repoRoot.relativize(file) + ":" + line + " -> " + value;
    }

    private static void failOnViolations(String message, List<String> violations) {
        if (violations.isEmpty()) {
            return;
        }
        fail(message + "\n  - " + String.join("\n  - ", violations));
    }
}

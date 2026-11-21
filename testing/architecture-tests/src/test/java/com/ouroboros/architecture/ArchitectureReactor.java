package com.ouroboros.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

final class ArchitectureReactor {
    private final List<Module> modules;

    private ArchitectureReactor(List<Module> modules) {
        this.modules = List.copyOf(modules);
    }

    static ArchitectureReactor read(Path repository, Path manifest) throws IOException {
        Path root = repository.toAbsolutePath().normalize();
        List<String> lines = Files.readAllLines(manifest);
        if (lines.isEmpty() || !lines.get(0).equals("repository\t" + root)) {
            throw new IllegalArgumentException("Architecture manifest belongs to another checkout: " + manifest);
        }
        List<Module> modules = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<Path> outputs = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 3 || fields[0].isBlank() || fields[1].isBlank()
                || !(fields[2].equals("true") || fields[2].equals("false"))) {
                throw new IllegalArgumentException("Invalid architecture module: " + line);
            }
            Path output = root.resolve(fields[1]).normalize();
            if (!output.startsWith(root) || !names.add(fields[0]) || !outputs.add(output)) {
                throw new IllegalArgumentException("Invalid or duplicate architecture module: " + line);
            }
            if (!ArchitectureClassImportSupport.isExcludedModule(Path.of(fields[0]))) {
                modules.add(new Module(fields[0], output, Boolean.parseBoolean(fields[2])));
            }
        }
        if (modules.isEmpty()) {
            throw new IllegalArgumentException("Architecture manifest contains no production modules: " + manifest);
        }
        return new ArchitectureReactor(modules);
    }

    List<Path> outputDirectories() {
        return modules.stream().map(Module::output).toList();
    }

    JavaClasses importClasses() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Module module : modules) {
            if (!Files.isDirectory(module.output()) || module.hasCode() && !containsClasses(module.output())) {
                missing.add(module.path());
            }
        }
        if (!missing.isEmpty()) {
            throw new AssertionError("Architecture scan is incomplete. Run mvn -DskipTests test-compile "
                + "from the repository root first. Missing compiled modules:\n  - "
                + String.join("\n  - ", missing));
        }
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPaths(outputDirectories());
    }

    Optional<String> moduleOf(JavaClass javaClass) {
        return javaClass.getSource()
            .filter(source -> "file".equals(source.getUri().getScheme()))
            .flatMap(source -> {
                Path location = Path.of(source.getUri()).toAbsolutePath().normalize();
                return modules.stream().filter(module -> location.startsWith(module.output()))
                    .map(Module::path).findFirst();
            });
    }

    private boolean containsClasses(Path output) throws IOException {
        try (var paths = Files.walk(output)) {
            return paths.anyMatch(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"));
        }
    }

    private record Module(String path, Path output, boolean hasCode) {
    }
}

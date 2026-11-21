package com.ouroboros.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLClassLoader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;

final class ArchitectureSpiSupport {
    private ArchitectureSpiSupport() {
    }

    static void check(JavaClasses classes, ArchitectureReactor reactor) throws IOException {
        Path manifest = Path.of(System.getProperty("architecture.reactorManifest"));
        Path dependencies = manifest.resolveSibling("architecture-runtime-classpath.txt");
        List<Path> classpath = new ArrayList<>(reactor.outputDirectories());
        for (String line : Files.readAllLines(dependencies)) {
            if (!line.isBlank()) classpath.add(Path.of(line));
        }
        List<String> failures;
        try (var loader = new URLClassLoader(classpath.stream().map(ArchitectureSpiSupport::url).toArray(URL[]::new),
            ClassLoader.getPlatformClassLoader())) {
            failures = violations(classes, reactor.outputDirectories(), loader);
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("[P0-6] Invalid service registrations:\n" + String.join("\n", failures));
        }
    }

    private static URL url(Path path) {
        try {
            return path.toUri().toURL();
        } catch (java.net.MalformedURLException error) {
            throw new IllegalArgumentException("Invalid Maven classpath entry: " + path, error);
        }
    }

    static List<String> violations(JavaClasses classes, List<Path> outputs, ClassLoader loader) throws IOException {
        Map<String, Set<String>> providers = new HashMap<>();
        List<String> failures = new ArrayList<>();
        for (Path output : outputs) {
            Path services = output.resolve("META-INF/services");
            if (!Files.exists(services)) {
                continue;
            }
            try (var paths = Files.list(services)) {
                for (Path descriptor : paths.filter(Files::isRegularFile).toList()) {
                    String service = descriptor.getFileName().toString();
                    Set<String> registered = providers.computeIfAbsent(service, ignored -> new HashSet<>());
                    for (String line : Files.readAllLines(descriptor)) {
                        String provider = line.split("#", 2)[0].trim();
                        if (provider.isEmpty()) {
                            continue;
                        }
                        registered.add(provider);
                        if (!classes.contain(provider)) {
                            failures.add(descriptor + ": missing provider " + provider);
                            continue;
                        }
                        var type = classes.get(provider);
                        try {
                            Class<?> contract = Class.forName(service, false, loader);
                            Class<?> implementation = Class.forName(provider, false, loader);
                            if (!contract.isAssignableFrom(implementation)) {
                                failures.add(provider + " is not assignable to service " + service);
                            }
                        } catch (ClassNotFoundException | LinkageError error) {
                            failures.add(descriptor + ": cannot resolve provider contract: " + error);
                        }
                        if (!type.getModifiers().contains(JavaModifier.PUBLIC) || type.getModifiers().contains(JavaModifier.ABSTRACT)
                            || type.getConstructors().stream().noneMatch(constructor ->
                                constructor.getRawParameterTypes().isEmpty()
                                    && constructor.getModifiers().contains(JavaModifier.PUBLIC))) {
                            failures.add(provider + " must be public, concrete and have a public no-argument constructor");
                        }
                        if (!Files.isRegularFile(output.resolve(provider.replace('.', '/') + ".class"))) {
                            failures.add(provider + " is registered outside its owning module " + output);
                        }
                    }
                }
            }
        }
        if (providers.isEmpty()) {
            failures.add("No service contracts found in reactor outputs");
        }
        // This extension contract is discoverable even if its entire descriptor disappears.
        providers.computeIfAbsent("com.ouroboros.logicflow.nodes.NodeBuilder", ignored -> new HashSet<>());
        for (var type : classes) {
            if (type.isInterface() || type.getModifiers().contains(JavaModifier.ABSTRACT) || !type.getModifiers().contains(JavaModifier.PUBLIC)) {
                continue;
            }
            // Interfaces can also be implemented by ordinary beans, fallback values or delegated
            // providers. Only explicit provider markers and the NodeBuilder extension contract
            // imply automatic discovery; all actual descriptors are validated above.
            if (!type.isAssignableTo("com.ouroboros.logicflow.nodes.NodeBuilder")
                && !type.isAnnotatedWith("jakarta.annotation.Priority")) {
                continue;
            }
            if (type.isAnnotatedWith("org.springframework.stereotype.Component")
                || type.isMetaAnnotatedWith("org.springframework.stereotype.Component")) {
                continue;
            }
            for (var entry : providers.entrySet()) {
                if (type.isAssignableTo(entry.getKey()) && !type.getName().equals(entry.getKey())
                    && !entry.getValue().contains(type.getName())) {
                    failures.add(type.getName() + " is not registered for " + entry.getKey());
                }
            }
        }
        return failures;
    }
}

package com.ouroboros.architecture;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClasses;

final class ArchitectureModuleDependencies {
    private ArchitectureModuleDependencies() {
    }

    static Set<Edge> between(JavaClasses classes, ArchitectureReactor reactor) {
        Set<Edge> edges = new HashSet<>();
        for (var javaClass : classes) {
            String source = reactor.moduleOf(javaClass).orElseThrow(
                () -> new IllegalArgumentException("Imported class has no reactor module: " + javaClass.getName()));
            for (var dependency : javaClass.getDirectDependenciesFromSelf()) {
                reactor.moduleOf(dependency.getTargetClass())
                    .filter(target -> !source.equals(target))
                    .ifPresent(target -> edges.add(new Edge(source, target)));
            }
        }
        return Set.copyOf(edges);
    }

    static void check(Set<Edge> edges, Set<String> allowlist) {
        Set<Edge> violations = newCyclicEdges(edges, allowlist);
        if (!violations.isEmpty()) {
            throw new AssertionError("\nP1-14 module dependencies form cycles; unapproved directed edges:\n  - "
                + violations.stream().map(edge -> edge.source() + " -> " + edge.target())
                    .sorted().collect(Collectors.joining("\n  - ")));
        }
    }

    static Set<Edge> newCyclicEdges(Set<Edge> edges, Set<String> allowlist) {
        Set<Edge> baseline = baselineEdges(allowlist);
        Map<String, Set<String>> graph = new HashMap<>();
        for (Edge edge : edges) {
            if (!edge.source().equals(edge.target())) {
                graph.computeIfAbsent(edge.source(), ignored -> new TreeSet<>()).add(edge.target());
            }
        }
        Set<Edge> violations = new HashSet<>();
        for (Edge edge : edges) {
            if (!edge.source().equals(edge.target()) && !baseline.contains(edge)
                && reaches(edge.target(), edge.source(), graph)) {
                violations.add(edge);
            }
        }
        return Set.copyOf(violations);
    }

    private static boolean reaches(String start, String target, Map<String, Set<String>> graph) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (current.equals(target)) {
                return true;
            }
            if (visited.add(current)) {
                pending.addAll(graph.getOrDefault(current, Set.of()));
            }
        }
        return false;
    }

    private static Set<Edge> baselineEdges(Set<String> allowlist) {
        Set<Edge> baseline = new HashSet<>();
        for (String entry : allowlist) {
            String[] fields = entry.split("\t", -1);
            if (!fields[0].equals("P1-14")) {
                continue;
            }
            if (fields.length != 7 || !fields[1].equals("module_cycle_edge")
                || fields[2].isBlank() || fields[3].isBlank()) {
                throw new IllegalArgumentException("P1-14 requires an exact module_cycle_edge baseline: " + entry);
            }
            baseline.add(new Edge(fields[2], fields[3]));
        }
        return baseline;
    }

    record Edge(String source, String target) {
    }

    static Map<String, List<Dependency>> effectiveMavenDependencies() throws IOException {
        Path manifest = Path.of(System.getProperty("architecture.reactorManifest"));
        var rows = Files.readAllLines(manifest.resolveSibling("architecture-maven-dependencies.tsv"));
        assertEquals(Files.readAllLines(manifest).getFirst(), rows.getFirst());
        Map<String, List<Dependency>> graph = new HashMap<>();
        for (String row : rows.subList(1, rows.size())) {
            String[] values = row.split("\t", -1);
            assertEquals(3, values.length, row);
            graph.computeIfAbsent(values[0], ignored -> new ArrayList<>()).add(new Dependency(values[1], values[2]));
        }
        return graph;
    }

    static void assertNoProductionDependency(Map<String, List<Dependency>> graph, String source, Set<String> forbidden) {
        String sourceArtifact = Path.of(source).getFileName().toString();
        assertTrue(graph.containsKey(sourceArtifact), "Missing effective Maven module: " + source);
        var pending = new ArrayDeque<List<String>>();
        pending.add(List.of(sourceArtifact));
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            List<String> chain = pending.remove();
            String module = chain.getLast();
            if (!visited.add(module)) { continue; }
            for (Dependency dependency : graph.getOrDefault(module, List.of())) {
                if (!Set.of("compile", "runtime", "provided", "system").contains(dependency.scope())) { continue; }
                assertFalse(forbidden.contains(dependency.artifact()),
                    "Forbidden production dependency: " + String.join(" -> ", chain) + " -> " + dependency.artifact());
                String target = dependency.artifact();
                if (graph.containsKey(target)) {
                    var next = new ArrayList<>(chain);
                    next.add(target);
                    pending.add(next);
                }
            }
        }
    }

    record Dependency(String artifact, String scope) { }
}

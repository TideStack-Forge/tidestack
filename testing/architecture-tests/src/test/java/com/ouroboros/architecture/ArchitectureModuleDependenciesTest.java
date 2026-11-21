package com.ouroboros.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.architecture.ArchitectureModuleDependencies.Edge;

class ArchitectureModuleDependenciesTest {
    @Test
    void actualModuleCycleFailsWithBothDependencyDirections() {
        var edges = Set.of(new Edge("core/a", "ability/b"), new Edge("ability/b", "core/a"));

        AssertionError failure = assertThrows(AssertionError.class,
            () -> ArchitectureModuleDependencies.check(edges, Set.of()));

        assertTrue(failure.getMessage().contains("core/a -> ability/b"));
        assertTrue(failure.getMessage().contains("ability/b -> core/a"));
    }

    @Test
    void aBaselineExemptsOnlyItsExactDirectedModuleEdges() {
        var edges = Set.of(new Edge("a", "b"), new Edge("b", "a"),
            new Edge("a", "bc"), new Edge("bc", "a"));
        var baseline = Set.of(baseline("a", "b"), baseline("b", "a"));

        var unexpected = ArchitectureModuleDependencies.newCyclicEdges(edges, baseline);

        assertEquals(Set.of(new Edge("a", "bc"), new Edge("bc", "a")), unexpected);
    }

    @Test
    void moreThanOneHundredExemptCyclesCannotHideANewCycle() {
        Set<Edge> edges = new HashSet<>();
        Set<String> baseline = new HashSet<>();
        for (int index = 0; index < 120; index++) {
            String source = "old-" + index;
            String target = "peer-" + index;
            edges.add(new Edge(source, target));
            edges.add(new Edge(target, source));
            baseline.add(baseline(source, target));
            baseline.add(baseline(target, source));
        }
        edges.add(new Edge("new-a", "new-b"));
        edges.add(new Edge("new-b", "new-a"));

        assertEquals(Set.of(new Edge("new-a", "new-b"), new Edge("new-b", "new-a")),
            ArchitectureModuleDependencies.newCyclicEdges(edges, baseline));
    }

    @Test
    void packagePrefixExemptionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ArchitectureModuleDependencies.check(Set.of(),
                Set.of("P1-14\tbaseline_violation\tCycle detected: Slice appmodule ->\t\treason")));
    }

    @Test
    void acyclicAndInternalDependenciesAreAllowed() {
        ArchitectureModuleDependencies.check(Set.of(new Edge("a", "a"),
            new Edge("a", "b"), new Edge("b", "c")), Set.of());
    }

    private String baseline(String source, String target) {
        return "P1-14\tmodule_cycle_edge\t" + source + "\t" + target
            + "\treviewed historical dependency\t@arch-team\t2026-12-31";
    }
}

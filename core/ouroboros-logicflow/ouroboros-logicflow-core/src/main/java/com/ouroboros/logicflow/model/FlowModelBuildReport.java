package com.ouroboros.logicflow.model;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dependencies come from actual reference resolution, never from node-type inspection. */
public record FlowModelBuildReport(Map<String, Set<String>> dependencies, Map<String, List<String>> errors) {
  public FlowModelBuildReport {
    var edges = new LinkedHashMap<String, Set<String>>();
    dependencies.forEach((name, targets) -> edges.put(name, Set.copyOf(targets)));
    dependencies = Collections.unmodifiableMap(edges);
    var issues = new LinkedHashMap<String, List<String>>();
    errors.forEach((name, messages) -> issues.put(name, List.copyOf(messages)));
    errors = Collections.unmodifiableMap(issues);
  }

  public Set<String> dependenciesOf(Set<String> roots) {
    return closure(roots, dependencies);
  }

  public Set<String> impactedBy(Set<String> changed) {
    var reverse = new LinkedHashMap<String, Set<String>>();
    dependencies.forEach((owner, targets) -> targets.forEach(target ->
        reverse.computeIfAbsent(target, key -> new LinkedHashSet<>()).add(owner)));
    return closure(changed, reverse);
  }

  public Set<String> checkScope(Set<String> changed) {
    return dependenciesOf(impactedBy(changed));
  }

  public boolean valid(Set<String> scope) {
    return scope.stream().map(FlowModelDefinitions::nameKey)
        .noneMatch(name -> !errors.getOrDefault(name, List.of()).isEmpty());
  }

  static Set<String> closure(Set<String> roots, Map<String, Set<String>> graph) {
    var visited = new LinkedHashSet<String>();
    var pending = new ArrayDeque<String>();
    roots.forEach(name -> pending.add(FlowModelDefinitions.nameKey(name)));
    while (!pending.isEmpty()) {
      var name = pending.removeFirst();
      if (visited.add(name)) {
        pending.addAll(graph.getOrDefault(name, Set.of()));
      }
    }
    return Set.copyOf(visited);
  }
}

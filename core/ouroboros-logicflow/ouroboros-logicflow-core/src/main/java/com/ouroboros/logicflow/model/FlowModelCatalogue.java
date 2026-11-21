package com.ouroboros.logicflow.model;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A single observation of the original provider chain, including hidden and disabled definitions. */
public record FlowModelCatalogue(List<FlowModelSource> sources) {
  public FlowModelCatalogue {
    sources = sources.stream().sorted(Comparator.comparingInt(FlowModelSource::priority)).toList();
  }

  public FlowModelCatalogue defaults() {
    return new FlowModelCatalogue(sources.stream().filter(source -> !source.runtimeOverride()).toList());
  }

  public FlowModelCatalogue withOverrides(List<FlowModelSource> overrides) {
    if (overrides.stream().anyMatch(source -> !source.runtimeOverride())) {
      throw new IllegalArgumentException("独立刷新只允许替换声明的运行时覆盖来源");
    }
    return new FlowModelCatalogue(java.util.stream.Stream.concat(
        defaults().sources().stream(), overrides.stream()).toList());
  }

  public Map<String, String> definitions() {
    var selected = new TreeMap<String, String>(String::compareToIgnoreCase);
    sources.forEach(source -> source.definitions().forEach((name, definition) -> {
      if (Boolean.TRUE.equals(FlowModelDefinitions.read(definition).isEnabled())) {
        selected.put(name, definition);
      }
    }));
    return Collections.unmodifiableMap(selected);
  }

  public Map<String, String> origins() {
    var selected = new TreeMap<String, String>(String::compareToIgnoreCase);
    sources.forEach(source -> source.definitions().forEach((name, definition) -> {
      if (Boolean.TRUE.equals(FlowModelDefinitions.read(definition).isEnabled())) {
        selected.put(name, source.id());
      }
    }));
    return Collections.unmodifiableMap(selected);
  }

  public String stamp() {
    return FlowModelDefinitions.hash(sources.stream().map(FlowModelSource::stamp)
        .collect(java.util.stream.Collectors.joining(";")));
  }
}

package com.ouroboros.logicflow.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One captured provider observation; definition strings cannot mutate a published model. */
public record FlowModelSource(String id, int priority, boolean declared, boolean runtimeOverride,
    boolean complete, List<String> diagnostics, Map<String, String> definitions, String version) {
  public FlowModelSource {
    Objects.requireNonNull(id);
    Objects.requireNonNull(version);
    diagnostics = List.copyOf(diagnostics);
    definitions = Map.copyOf(definitions);
  }

  public static FlowModelSource capture(String id, int priority, boolean runtimeOverride,
      boolean complete, List<String> diagnostics, Map<String, FlowModelMeta> definitions) {
    return new FlowModelSource(id, priority, true, runtimeOverride, complete, diagnostics,
        FlowModelDefinitions.freeze(definitions), "");
  }

  public String stamp() {
    return FlowModelDefinitions.fingerprint(Map.of("id", id, "priority", priority,
        "declared", declared, "override", runtimeOverride, "complete", complete,
        "diagnostics", diagnostics, "definitions", definitions, "version", version));
  }
}

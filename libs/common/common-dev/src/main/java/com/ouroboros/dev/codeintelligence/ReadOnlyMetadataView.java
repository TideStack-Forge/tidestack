package com.ouroboros.dev.codeintelligence;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
public record ReadOnlyMetadataView(String appName, ApplicationMetadataSnapshot application, ScriptIntelligenceSnapshot environment,
    java.util.Map<String, com.ouroboros.codeintelligence.type.TypeShape> scriptOutputs) {
  public ReadOnlyMetadataView(String appName, ApplicationMetadataSnapshot application, ScriptIntelligenceSnapshot environment) {
    this(appName, application, environment, java.util.Map.of());
  }
  public ReadOnlyMetadataView { scriptOutputs = java.util.Map.copyOf(scriptOutputs); }
}

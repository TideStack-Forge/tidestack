package com.ouroboros.dev.projectgeneration.catalog;

import java.util.LinkedHashMap;
import java.util.Map;

public record ResolvedProjectTemplate(
    ProjectTemplateRelease release,
    ProjectTemplateCompatibilityRecord compatibilityRecord,
    Map<String, Object> parameters
) {
  public ResolvedProjectTemplate(ProjectTemplateRelease release, ProjectTemplateCompatibilityRecord compatibilityRecord) {
    this(release, compatibilityRecord, Map.of());
  }

  public ResolvedProjectTemplate {
    parameters = Map.copyOf(parameters == null ? Map.of() : new LinkedHashMap<>(parameters));
  }
}

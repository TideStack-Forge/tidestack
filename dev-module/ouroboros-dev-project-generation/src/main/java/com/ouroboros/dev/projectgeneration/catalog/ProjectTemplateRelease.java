package com.ouroboros.dev.projectgeneration.catalog;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;

public record ProjectTemplateRelease(
    String templateId,
    String templateVersion,
    ProjectTemplateScope scope,
    String displayName,
    String description,
    JsonNode parameterSchema,
    JsonNode uiHints,
    ProjectTemplateArtifactDescriptor artifact,
    List<ProjectTemplateCompatibilityRecord> compatibilityRecords
) {
  public ProjectTemplateRelease {
    ProjectTemplateIdentifierContracts.requireTemplateId(templateId, "templateId");
    ProjectTemplateIdentifierContracts.requireTemplateVersion(templateVersion, "templateVersion");
    if (scope == null) {
      throw new IllegalArgumentException("Template scope is required");
    }
    ProjectTemplateIdentifierContracts.requireDisplayText(displayName, "displayName", 120);
    ProjectTemplateIdentifierContracts.requireDisplayText(description, "description", 1000);
    if (parameterSchema == null || !parameterSchema.isObject()) {
      throw new IllegalArgumentException("Parameter schema is required");
    }
    if (artifact == null) {
      throw new IllegalArgumentException("Artifact descriptor is required");
    }
    compatibilityRecords = List.copyOf(
        compatibilityRecords == null ? List.of() : compatibilityRecords
    );
    if (compatibilityRecords.isEmpty()) {
      throw new IllegalArgumentException("Compatibility records are required");
    }
  }
}

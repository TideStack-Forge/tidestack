package com.ouroboros.dev.projectgeneration.catalog;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public record ProjectTemplateCompatibilityRecord(
    String rowId,
    ProjectTemplateChannel channel,
    ProjectTemplateCompatibilityStatus status,
    String declaredPlatformRange,
    List<String> testedPlatformVersions,
    JsonNode toolchain,
    JsonNode certificationEvidence
) {
  public ProjectTemplateCompatibilityRecord {
    ProjectTemplateIdentifierContracts.requireRowId(rowId, "rowId");
    if (channel == null || status == null) {
      throw new IllegalArgumentException("Compatibility channel and status are required");
    }
    if (declaredPlatformRange == null || declaredPlatformRange.isBlank()) {
      throw new IllegalArgumentException("Declared platform range is required");
    }
    testedPlatformVersions = List.copyOf(testedPlatformVersions == null ? List.of() : testedPlatformVersions);
    if (testedPlatformVersions.isEmpty()) {
      throw new IllegalArgumentException("Tested platform versions are required");
    }
  }
}

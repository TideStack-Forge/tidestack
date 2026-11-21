package com.ouroboros.dev.projectgeneration.render;

import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateIdentifierContracts;

public record ProjectTemplateRenderContext(
    String catalogRevision,
    String catalogDigestSha256,
    String targetPlatformVersion,
    String compatibilityRowId,
    String artifactSha256
) {
  public ProjectTemplateRenderContext {
    ProjectTemplateIdentifierContracts.requireRevision(catalogRevision, "catalogRevision");
    ProjectTemplateIdentifierContracts.requireRowId(compatibilityRowId, "compatibilityRowId");
    requireSha256(catalogDigestSha256, "catalogDigestSha256");
    requireSha256(artifactSha256, "artifactSha256");
    if (targetPlatformVersion == null || targetPlatformVersion.isBlank()) {
      throw new IllegalArgumentException("targetPlatformVersion is required");
    }
  }

  private static void requireSha256(String value, String fieldName) {
    if (value == null || !value.matches("^[a-f0-9]{64}$")) {
      throw new IllegalArgumentException(fieldName + " must be lowercase sha256 hex");
    }
  }
}

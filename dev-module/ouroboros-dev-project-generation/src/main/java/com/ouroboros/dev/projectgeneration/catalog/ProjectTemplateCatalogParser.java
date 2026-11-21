package com.ouroboros.dev.projectgeneration.catalog;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;

public class ProjectTemplateCatalogParser {

  public ProjectTemplateCatalogDocument parse(byte[] canonicalCatalogBytes) {
    JsonNode catalog = CanonicalJson.parseCanonical(canonicalCatalogBytes);
    String revision = text(catalog, "revision");
    long sequence = catalog.path("sequence").asLong(-1);
    JsonNode templates = catalog.path("templates");
    if (!templates.isArray()) {
      throw new IllegalArgumentException("Catalog templates must be an array");
    }
    List<ProjectTemplateRelease> releases = new ArrayList<>();
    for (JsonNode template : templates) {
      releases.add(parseRelease(template));
    }
    validateCatalogUniqueness(releases);
    return new ProjectTemplateCatalogDocument(revision, sequence, releases);
  }

  private ProjectTemplateRelease parseRelease(JsonNode node) {
    String templateId = text(node, "templateId");
    String templateVersion = text(node, "templateVersion");
    ProjectTemplateScope scope = ProjectTemplateScope.valueOf(text(node, "scope"));
    String displayName = text(node, "displayName");
    String description = text(node, "description");
    JsonNode parameterSchema = node.path("parameterSchema");
    JsonNode uiHints = node.path("uiHints");
    JsonNode artifactNode = node.path("artifact");
    ProjectTemplateArtifactDescriptor artifact = new ProjectTemplateArtifactDescriptor(
        URI.create(text(artifactNode, "uri")),
        text(artifactNode, "sha256"),
        compressedSize(artifactNode)
    );
    List<ProjectTemplateCompatibilityRecord> records = new ArrayList<>();
    JsonNode compatibilityRecords = node.path("compatibilityRecords");
    if (!compatibilityRecords.isArray()) {
      throw new IllegalArgumentException("Compatibility records must be an array");
    }
    for (JsonNode record : compatibilityRecords) {
      records.add(parseRecord(record));
    }
    return new ProjectTemplateRelease(
        templateId,
        templateVersion,
        scope,
        displayName,
        description,
        parameterSchema,
        uiHints.isMissingNode() ? CanonicalJson.parseCanonical("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) : uiHints,
        artifact,
        records
    );
  }

  private ProjectTemplateCompatibilityRecord parseRecord(JsonNode node) {
    ProjectTemplateChannel channel = ProjectTemplateChannel.valueOf(text(node, "channel"));
    ProjectTemplateCompatibilityStatus status = ProjectTemplateCompatibilityStatus.valueOf(text(node, "status"));
    return new ProjectTemplateCompatibilityRecord(
        text(node, "rowId"),
        channel,
        status,
        text(node, "declaredPlatformRange"),
        stringArray(node.path("testedPlatformVersions"), "testedPlatformVersions"),
        node.path("toolchain"),
        node.path("certificationEvidence")
    );
  }

  private static List<String> stringArray(JsonNode node, String fieldName) {
    if (!node.isArray()) {
      throw new IllegalArgumentException(fieldName + " must be an array");
    }
    List<String> values = new ArrayList<>();
    for (JsonNode value : node) {
      if (!value.isTextual() || value.textValue().isBlank()) {
        throw new IllegalArgumentException(fieldName + " entries must be text");
      }
      values.add(value.textValue());
    }
    return values;
  }

  private static String text(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw new IllegalArgumentException("Catalog field is missing: " + fieldName);
    }
    return value.textValue();
  }

  private static long compressedSize(JsonNode artifactNode) {
    JsonNode value = artifactNode.get("compressedSizeBytes");
    if (value == null) {
      value = artifactNode.get("compressedSize");
    }
    return value == null ? -1 : value.asLong(-1);
  }

  private static void validateCatalogUniqueness(List<ProjectTemplateRelease> releases) {
    Set<String> releaseKeys = new HashSet<>();
    Set<String> rowIds = new HashSet<>();
    Set<String> activeResolutionKeys = new HashSet<>();
    for (ProjectTemplateRelease release : releases) {
      String releaseKey = release.templateId() + "@" + release.templateVersion();
      if (!releaseKeys.add(releaseKey)) {
        throw new IllegalArgumentException("Duplicate template release: " + releaseKey);
      }
      for (ProjectTemplateCompatibilityRecord record : release.compatibilityRecords()) {
        if (!rowIds.add(record.rowId())) {
          throw new IllegalArgumentException("Duplicate compatibility row id: " + record.rowId());
        }
        if (record.channel() == ProjectTemplateChannel.stable) {
          ProjectTemplateIdentifierContracts.requireStableTemplateVersion(release.templateVersion(), "templateVersion");
        }
        if (record.status() == ProjectTemplateCompatibilityStatus.ACTIVE) {
          Iterator<String> versions = record.testedPlatformVersions().iterator();
          while (versions.hasNext()) {
            String resolutionKey = release.templateId() + "|" + record.channel() + "|" + versions.next();
            if (!activeResolutionKeys.add(resolutionKey)) {
              throw new IllegalArgumentException("Ambiguous ACTIVE compatibility row: " + resolutionKey);
            }
          }
        }
      }
    }
  }
}

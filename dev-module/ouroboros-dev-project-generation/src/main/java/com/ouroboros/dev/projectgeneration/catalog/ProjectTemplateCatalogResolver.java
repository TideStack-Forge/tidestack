package com.ouroboros.dev.projectgeneration.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import com.ouroboros.dev.projectgeneration.model.ProjectGenerationRequest;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateSummary;

public class ProjectTemplateCatalogResolver {

  private final ProjectTemplateCatalogParser parser;

  public ProjectTemplateCatalogResolver() {
    this(new ProjectTemplateCatalogParser());
  }

  public ProjectTemplateCatalogResolver(ProjectTemplateCatalogParser parser) {
    this.parser = parser == null ? new ProjectTemplateCatalogParser() : parser;
  }

  public List<ProjectTemplateSummary> list(
      ProjectTemplateCatalogSnapshot snapshot,
      ProjectTemplateScope scope,
      boolean includePreview,
      String targetPlatformVersion) {
    ProjectTemplateCatalogDocument catalog = parse(snapshot);
    List<ProjectTemplateSummary> summaries = new ArrayList<>();
    summaries.addAll(ProjectTemplateFamilyRegistry.summaries(
        catalog,
        scope,
        targetPlatformVersion,
        (record, platformVersion) -> isVisible(record, includePreview, platformVersion)
    ));
    for (ProjectTemplateRelease release : catalog.releases()) {
      if (release.scope() != scope) {
        continue;
      }
      if (ProjectTemplateFamilyRegistry.isConcreteBranchTemplate(release.templateId())) {
        continue;
      }
      for (ProjectTemplateCompatibilityRecord record : release.compatibilityRecords()) {
        if (isVisible(record, includePreview, targetPlatformVersion)) {
          summaries.add(new ProjectTemplateSummary(
              release.templateId(),
              release.templateVersion(),
              record.rowId(),
              release.displayName(),
              release.description(),
              record.channel().name(),
              release.parameterSchema(),
              release.uiHints()
          ));
        }
      }
    }
    summaries.sort(Comparator.comparing(ProjectTemplateSummary::templateId)
        .thenComparing(ProjectTemplateSummary::templateVersion)
        .thenComparing(ProjectTemplateSummary::compatibilityRowId));
    return summaries;
  }

  public ResolvedProjectTemplate resolveForGeneration(
      ProjectTemplateCatalogSnapshot snapshot,
      ProjectGenerationRequest request,
      String currentPlatformVersion) {
    if (snapshot == null || request == null) {
      throw new IllegalArgumentException("Catalog snapshot and generation request are required");
    }
    if (!snapshot.revision().equals(request.catalogRevision())) {
      throw new IllegalArgumentException("Project template catalog revision is stale");
    }
    if (!currentPlatformVersion.equals(request.targetPlatformVersion())) {
      throw new IllegalArgumentException("Project template target platform version is stale");
    }
    ProjectTemplateCatalogDocument catalog = parse(snapshot);
    var selection = new ProjectTemplateGenerationSelection(
        request.scope(),
        request.catalogRevision(),
        request.targetPlatformVersion(),
        request.templateId(),
        request.templateVersion(),
        request.compatibilityRowId(),
        request.allowPreview(),
        request.parameters() == null ? Map.of() : request.parameters()
    );
    var familyResolution = ProjectTemplateFamilyRegistry.resolveFamily(
        catalog,
        selection,
        (record, platformVersion) -> requireGeneratable(record, request.allowPreview(), platformVersion)
    );
    if (familyResolution.isPresent()) {
      return familyResolution.get();
    }
    if (ProjectTemplateFamilyRegistry.isConcreteBranchTemplate(request.templateId())) {
      throw new IllegalArgumentException("Project template branch id is not a public generation contract");
    }
    for (ProjectTemplateRelease release : catalog.releases()) {
      if (!release.templateId().equals(request.templateId())
          || !release.templateVersion().equals(request.templateVersion())
          || release.scope() != request.scope()) {
        continue;
      }
      for (ProjectTemplateCompatibilityRecord record : release.compatibilityRecords()) {
        if (!record.rowId().equals(request.compatibilityRowId())) {
          continue;
        }
        requireGeneratable(record, request.allowPreview(), currentPlatformVersion);
        return new ResolvedProjectTemplate(release, record, request.parameters());
      }
    }
    throw new IllegalArgumentException("Project template is not compatible with this request");
  }

  private ProjectTemplateCatalogDocument parse(ProjectTemplateCatalogSnapshot snapshot) {
    return parser.parse(snapshot.catalogBytes());
  }

  private static boolean isVisible(
      ProjectTemplateCompatibilityRecord record,
      boolean includePreview,
      String targetPlatformVersion) {
    return record.status() == ProjectTemplateCompatibilityStatus.ACTIVE
        && (record.channel() == ProjectTemplateChannel.stable || includePreview)
        && matchesPlatform(record, targetPlatformVersion);
  }

  private static void requireGeneratable(
      ProjectTemplateCompatibilityRecord record,
      boolean allowPreview,
      String targetPlatformVersion) {
    if (record.status() != ProjectTemplateCompatibilityStatus.ACTIVE) {
      throw new IllegalArgumentException("Project template compatibility row is not active");
    }
    if (record.channel() == ProjectTemplateChannel.preview && !allowPreview) {
      throw new IllegalArgumentException("Preview template requires explicit confirmation");
    }
    if (!matchesPlatform(record, targetPlatformVersion)) {
      throw new IllegalArgumentException("Project template row is not compatible with target platform");
    }
  }

  private static boolean matchesPlatform(ProjectTemplateCompatibilityRecord record, String targetPlatformVersion) {
    if (!record.testedPlatformVersions().contains(targetPlatformVersion)) {
      return false;
    }
    try {
      VersionRange range = VersionRange.createFromVersionSpec(record.declaredPlatformRange());
      return range.containsVersion(new DefaultArtifactVersion(targetPlatformVersion));
    } catch (InvalidVersionSpecificationException e) {
      throw new IllegalArgumentException("Invalid Maven platform range: " + record.declaredPlatformRange(), e);
    }
  }
}

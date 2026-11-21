package com.ouroboros.dev.projectgeneration.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import com.ouroboros.dev.projectgeneration.artifact.TemplateArchiveReader;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogResolver;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogSnapshot;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogSnapshotManager;
import com.ouroboros.dev.projectgeneration.catalog.ResolvedProjectTemplate;
import com.ouroboros.dev.projectgeneration.model.GeneratedProject;
import com.ouroboros.dev.projectgeneration.model.ProjectGenerationRequest;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateCatalogView;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateSummary;
import com.ouroboros.dev.projectgeneration.parameter.ProjectTemplateParameterValidator;
import com.ouroboros.dev.projectgeneration.render.ProjectTemplateRenderContext;
import com.ouroboros.dev.projectgeneration.render.ProjectTemplateRenderer;
import com.ouroboros.dev.projectgeneration.repository.TemplateArtifactRepository;
import com.ouroboros.dev.projectgeneration.service.ProjectGenerationService;
import com.ouroboros.dev.projectgeneration.service.ProjectPlatformVersionProvider;
import com.ouroboros.dev.projectgeneration.zip.DeterministicProjectZipWriter;
import com.ouroboros.exception.BusinessException;

public class CatalogBackedProjectGenerationService implements ProjectGenerationService {

  private final ProjectTemplateCatalogSnapshotManager snapshotManager;
  private final ProjectPlatformVersionProvider platformVersionProvider;
  private final ProjectTemplateCatalogResolver catalogResolver;
  private final ProjectTemplateParameterValidator parameterValidator;
  private final TemplateArtifactRepository artifactRepository;
  private final TemplateArchiveReader archiveReader;
  private final ProjectTemplateRenderer renderer;
  private final DeterministicProjectZipWriter zipWriter;

  public CatalogBackedProjectGenerationService(
      ProjectTemplateCatalogSnapshotManager snapshotManager,
      ProjectPlatformVersionProvider platformVersionProvider,
      ProjectTemplateCatalogResolver catalogResolver,
      ProjectTemplateParameterValidator parameterValidator,
      TemplateArtifactRepository artifactRepository) {
    this(
        snapshotManager,
        platformVersionProvider,
        catalogResolver,
        parameterValidator,
        artifactRepository,
        null,
        null,
        null
    );
  }

  public CatalogBackedProjectGenerationService(
      ProjectTemplateCatalogSnapshotManager snapshotManager,
      ProjectPlatformVersionProvider platformVersionProvider,
      ProjectTemplateCatalogResolver catalogResolver,
      ProjectTemplateParameterValidator parameterValidator,
      TemplateArtifactRepository artifactRepository,
      TemplateArchiveReader archiveReader,
      ProjectTemplateRenderer renderer) {
    this(
        snapshotManager,
        platformVersionProvider,
        catalogResolver,
        parameterValidator,
        artifactRepository,
        archiveReader,
        renderer,
        null
    );
  }

  public CatalogBackedProjectGenerationService(
      ProjectTemplateCatalogSnapshotManager snapshotManager,
      ProjectPlatformVersionProvider platformVersionProvider,
      ProjectTemplateCatalogResolver catalogResolver,
      ProjectTemplateParameterValidator parameterValidator,
      TemplateArtifactRepository artifactRepository,
      TemplateArchiveReader archiveReader,
      ProjectTemplateRenderer renderer,
      DeterministicProjectZipWriter zipWriter) {
    if (snapshotManager == null || platformVersionProvider == null) {
      throw new IllegalArgumentException("Catalog backed generation dependencies are required");
    }
    this.snapshotManager = snapshotManager;
    this.platformVersionProvider = platformVersionProvider;
    this.catalogResolver = catalogResolver == null ? new ProjectTemplateCatalogResolver() : catalogResolver;
    this.parameterValidator = parameterValidator == null
        ? new ProjectTemplateParameterValidator()
        : parameterValidator;
    this.artifactRepository = artifactRepository;
    this.archiveReader = archiveReader;
    this.renderer = renderer;
    this.zipWriter = zipWriter;
  }

  @Override
  public ProjectTemplateCatalogView list(String appName, ProjectTemplateScope scope, boolean includePreview) {
    ProjectTemplateCatalogSnapshot snapshot = snapshotManager.currentOrRefresh();
    String platformVersion = platformVersionProvider.getCurrentVersion();
    List<ProjectTemplateSummary> templates = catalogResolver.list(snapshot, scope, includePreview, platformVersion);
    return new ProjectTemplateCatalogView(
        snapshot.revision(),
        platformVersion,
        scope,
        includePreview,
        templates
    );
  }

  @Override
  public GeneratedProject generate(String appName, ProjectGenerationRequest request) {
    ProjectTemplateCatalogSnapshot snapshot = snapshotManager.currentOrRefresh();
    String platformVersion = platformVersionProvider.getCurrentVersion();
    ResolvedProjectTemplate resolved = catalogResolver.resolveForGeneration(snapshot, request, platformVersion);
    Map<String, Object> normalizedParameters = parameterValidator.normalize(
        resolved.release().parameterSchema(),
        resolved.release().uiHints(),
        resolved.parameters()
    );
    if (artifactRepository == null || archiveReader == null || renderer == null) {
      throw new BusinessException("项目模板渲染器尚未接入，无法生成项目");
    }
    if (zipWriter == null) {
      throw new BusinessException("项目 ZIP 输出尚未接入，无法生成项目");
    }
    try (var artifact = artifactRepository.fetch(resolved.release().artifact());
        var input = Files.newInputStream(artifact.path())) {
      var renderedTemplate = renderer.render(
          resolved.release(),
          resolved.compatibilityRecord(),
          archiveReader.read(input),
          normalizedParameters,
          new ProjectTemplateRenderContext(
              snapshot.revision(),
              snapshot.digest(),
              platformVersion,
              resolved.compatibilityRecord().rowId(),
              resolved.release().artifact().sha256()
          )
      );
      return zipWriter.write(renderedTemplate, resolved.release().templateVersion(), null);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read project template artifact", e);
    }
  }
}

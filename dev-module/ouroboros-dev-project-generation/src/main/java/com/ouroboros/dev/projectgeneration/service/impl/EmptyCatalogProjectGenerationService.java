package com.ouroboros.dev.projectgeneration.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.dev.projectgeneration.model.GeneratedProject;
import com.ouroboros.dev.projectgeneration.model.ProjectGenerationRequest;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateCatalogView;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;
import com.ouroboros.dev.projectgeneration.service.ProjectGenerationService;
import com.ouroboros.dev.projectgeneration.service.ProjectPlatformVersionProvider;

@Service
public class EmptyCatalogProjectGenerationService implements ProjectGenerationService {

  public static final String EMPTY_REVISION = "EMPTY";

  private final ProjectPlatformVersionProvider platformVersionProvider;

  public EmptyCatalogProjectGenerationService(ProjectPlatformVersionProvider platformVersionProvider) {
    this.platformVersionProvider = platformVersionProvider;
  }

  @Override
  public ProjectTemplateCatalogView list(String appName, ProjectTemplateScope scope, boolean includePreview) {
    return new ProjectTemplateCatalogView(
        EMPTY_REVISION,
        platformVersionProvider.getCurrentVersion(),
        scope,
        includePreview,
        List.of()
    );
  }

  @Override
  public GeneratedProject generate(String appName, ProjectGenerationRequest request) {
    throw new BusinessException("项目模板目录尚未加载，无法生成项目");
  }
}

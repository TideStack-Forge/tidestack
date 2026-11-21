package com.ouroboros.dev.projectgeneration.service;

import com.ouroboros.dev.projectgeneration.model.GeneratedProject;
import com.ouroboros.dev.projectgeneration.model.ProjectGenerationRequest;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateCatalogView;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;

public interface ProjectGenerationService {

  ProjectTemplateCatalogView list(String appName, ProjectTemplateScope scope, boolean includePreview);

  GeneratedProject generate(String appName, ProjectGenerationRequest request);
}

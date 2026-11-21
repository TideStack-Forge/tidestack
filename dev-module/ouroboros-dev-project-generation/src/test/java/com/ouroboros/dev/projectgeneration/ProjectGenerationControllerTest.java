package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.controller.ProjectGenerationController;
import com.ouroboros.dev.projectgeneration.model.GeneratedProject;
import com.ouroboros.dev.projectgeneration.model.ProjectGenerationRequest;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateCatalogView;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;
import com.ouroboros.dev.projectgeneration.service.ProjectGenerationService;

class ProjectGenerationControllerTest {

  @Test
  void shouldPassScopeAndPreviewFlagToProjectGenerationService() {
    AtomicReference<String> appNameRef = new AtomicReference<>();
    AtomicReference<ProjectTemplateScope> scopeRef = new AtomicReference<>();
    AtomicReference<Boolean> previewRef = new AtomicReference<>();
    ProjectGenerationController controller = new ProjectGenerationController(new ProjectGenerationService() {
      @Override
      public ProjectTemplateCatalogView list(String appName, ProjectTemplateScope scope, boolean includePreview) {
        appNameRef.set(appName);
        scopeRef.set(scope);
        previewRef.set(includePreview);
        return new ProjectTemplateCatalogView("rev", "2.0.0", scope, includePreview, List.of());
      }

      @Override
      public GeneratedProject generate(String appName, ProjectGenerationRequest request) {
        throw new UnsupportedOperationException("not used by this test");
      }
    });

    var response = controller.list("demo-app", ProjectTemplateScope.BACKEND_EXTENSION, true);

    assertEquals("demo-app", appNameRef.get());
    assertEquals(ProjectTemplateScope.BACKEND_EXTENSION, scopeRef.get());
    assertEquals(true, previewRef.get());
    assertEquals(ProjectTemplateScope.BACKEND_EXTENSION, response.getData().scope());
    assertEquals(true, response.getData().previewIncluded());
  }
}

package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogResolver;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogSnapshot;
import com.ouroboros.dev.projectgeneration.model.ProjectGenerationRequest;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;

class ProjectGenerationPreviewAcknowledgementTest {

  @Test
  void shouldHidePreviewRowsUntilRequested() {
    ProjectTemplateCatalogSnapshot snapshot = snapshot();
    ProjectTemplateCatalogResolver resolver = new ProjectTemplateCatalogResolver();

    assertEquals(0, resolver.list(snapshot, ProjectTemplateScope.MICRO_FRONTEND, false, "2.0.0").size());
    assertEquals(1, resolver.list(snapshot, ProjectTemplateScope.MICRO_FRONTEND, true, "2.0.0").size());
  }

  @Test
  void shouldRequireExplicitAllowPreviewForGeneration() {
    ProjectTemplateCatalogSnapshot snapshot = snapshot();
    ProjectTemplateCatalogResolver resolver = new ProjectTemplateCatalogResolver();

    assertThrows(IllegalArgumentException.class, () -> resolver.resolveForGeneration(
        snapshot,
        request(false),
        "2.0.0"
    ));

    assertEquals("preview", resolver.resolveForGeneration(snapshot, request(true), "2.0.0")
        .compatibilityRecord()
        .channel()
        .name());
  }

  private static ProjectTemplateCatalogSnapshot snapshot() {
    return ProjectGenerationBatch5TestSupport.snapshot(ProjectGenerationBatch5TestSupport.oneTemplate(
        "[" + ProjectGenerationBatch5TestSupport.activePreviewRow() + "]"
    ));
  }

  private static ProjectGenerationRequest request(boolean allowPreview) {
    return new ProjectGenerationRequest(
        ProjectTemplateScope.MICRO_FRONTEND,
        "cat_100_abcd",
        "2.0.0",
        "frontend.micro-app",
        "1.0.0",
        "frontend.micro-app-1.0.0-platform-2.0.0",
        allowPreview,
        Map.of(
            "framework", "react",
            "buildTool", "webpack",
            "projectName", "customer-portal"
        )
    );
  }
}

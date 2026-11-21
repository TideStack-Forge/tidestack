package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogResolver;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogSnapshot;
import com.ouroboros.dev.projectgeneration.model.ProjectGenerationRequest;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateSummary;

class ProjectTemplateCatalogResolutionTest {

  @Test
  void shouldListOnlyFamilyRowsForExactTestedActiveStableBranches() {
    ProjectTemplateCatalogSnapshot snapshot = snapshot(
        "[" + ProjectGenerationBatch5TestSupport.activeStableRow() + ","
            + ProjectGenerationBatch5TestSupport.row(
                "frontend.micro-app.react-webpack-1.0.0-platform-2.0.1",
                "stable",
                "ACTIVE",
                "[2.0.0,2.1.0)",
                List.of("2.0.1")
            )
            + "]"
    );

    List<ProjectTemplateSummary> templates = new ProjectTemplateCatalogResolver().list(
        snapshot,
        ProjectTemplateScope.MICRO_FRONTEND,
        false,
        "2.0.0"
    );

    assertEquals(1, templates.size());
    assertEquals("frontend.micro-app", templates.getFirst().templateId());
    assertEquals("frontend.micro-app-1.0.0-platform-2.0.0", templates.getFirst().compatibilityRowId());
    assertEquals("object", templates.getFirst().parameterSchema().path("type").asText());
    assertEquals("react", templates.getFirst().parameterSchema().path("properties").path("framework").path("enum").get(0).asText());
    assertEquals("webpack", templates.getFirst().parameterSchema().path("properties").path("buildTool").path("enum").get(0).asText());
    assertEquals(true, templates.getFirst().uiHints().isObject());
    assertEquals("${framework == 'react'}",
        templates.getFirst().uiHints().path("buildTool").path("optionVisibleWhen").path("webpack").asText());
  }

  @Test
  void shouldResolveFamilyGenerationToSelectedBranch() {
    ProjectTemplateCatalogSnapshot snapshot = snapshot(
        "[" + ProjectGenerationBatch5TestSupport.activeStableRow() + "]"
    );
    ProjectGenerationRequest request = request(
        snapshot.revision(),
        "2.0.0",
        ProjectTemplateScope.MICRO_FRONTEND,
        "frontend.micro-app.react-webpack-1.0.0-platform-2.0.0"
    );

    var resolved = new ProjectTemplateCatalogResolver().resolveForGeneration(snapshot, request, "2.0.0");

    assertEquals("frontend.micro-app.react-webpack", resolved.release().templateId());
    assertEquals("frontend.micro-app.react-webpack-1.0.0-platform-2.0.0", resolved.compatibilityRecord().rowId());
    assertEquals("customer-portal", resolved.parameters().get("projectName"));
    assertTrue(!resolved.parameters().containsKey("framework"));
    assertTrue(!resolved.parameters().containsKey("buildTool"));
  }

  @Test
  void shouldRejectStaleRevisionWrongScopeOrWrongRow() {
    ProjectTemplateCatalogSnapshot snapshot = snapshot(
        "[" + ProjectGenerationBatch5TestSupport.activeStableRow() + "]"
    );
    ProjectTemplateCatalogResolver resolver = new ProjectTemplateCatalogResolver();

    assertThrows(IllegalArgumentException.class, () -> resolver.resolveForGeneration(
        snapshot,
        request("old_revision", "2.0.0", ProjectTemplateScope.MICRO_FRONTEND,
            "frontend.micro-app.react-webpack-1.0.0-platform-2.0.0"),
        "2.0.0"
    ));
    assertThrows(IllegalArgumentException.class, () -> resolver.resolveForGeneration(
        snapshot,
        request(snapshot.revision(), "2.0.0", ProjectTemplateScope.BACKEND_EXTENSION,
            "frontend.micro-app.react-webpack-1.0.0-platform-2.0.0"),
        "2.0.0"
    ));
    assertThrows(IllegalArgumentException.class, () -> resolver.resolveForGeneration(
        snapshot,
        request(snapshot.revision(), "2.0.0", ProjectTemplateScope.MICRO_FRONTEND, "missing-row"),
        "2.0.0"
    ));
  }

  private static ProjectTemplateCatalogSnapshot snapshot(String rowsJson) {
    return ProjectGenerationBatch5TestSupport.snapshot(ProjectGenerationBatch5TestSupport.oneTemplate(rowsJson));
  }

  private static ProjectGenerationRequest request(
      String revision,
      String targetPlatformVersion,
      ProjectTemplateScope scope,
      String rowId) {
    return new ProjectGenerationRequest(
        scope,
        revision,
        targetPlatformVersion,
        "frontend.micro-app",
        "1.0.0",
        rowId.equals("missing-row") ? rowId : "frontend.micro-app-1.0.0-platform-" + targetPlatformVersion,
        false,
        Map.of(
            "framework", "react",
            "buildTool", "webpack",
            "projectName", "customer-portal"
        )
    );
  }
}

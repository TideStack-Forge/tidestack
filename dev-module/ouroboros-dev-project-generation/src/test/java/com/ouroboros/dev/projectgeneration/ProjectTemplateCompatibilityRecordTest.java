package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogParser;

class ProjectTemplateCompatibilityRecordTest {

  @Test
  void shouldRejectAmbiguousActiveRowsForSameTemplateChannelAndPlatform() {
    String rows = "[" + ProjectGenerationBatch5TestSupport.activeStableRow() + ","
        + ProjectGenerationBatch5TestSupport.row(
            "frontend.micro-app.react-webpack-1.0.0-platform-2.0.0-alt",
            "stable",
            "ACTIVE",
            "[2.0.0,2.1.0)",
            List.of("2.0.0")
        ) + "]";

    assertThrows(IllegalArgumentException.class, () -> parse(ProjectGenerationBatch5TestSupport.oneTemplate(rows)));
  }

  @Test
  void shouldRejectPrereleaseTemplateVersionForStableRows() {
    String release = ProjectGenerationBatch5TestSupport.oneTemplate(
        "[" + ProjectGenerationBatch5TestSupport.activeStableRow() + "]"
    ).replace("\"templateVersion\": \"1.0.0\"", "\"templateVersion\": \"1.0.0-rc.1\"");

    assertThrows(IllegalArgumentException.class, () -> parse(release));
  }

  private static void parse(String templatesJson) {
    new ProjectTemplateCatalogParser().parse(
        ProjectGenerationBatch5TestSupport.snapshot(templatesJson).catalogBytes()
    );
  }
}

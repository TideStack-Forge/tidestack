package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.projectgeneration.artifact.TemplateArchiveReader;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogResolver;
import com.ouroboros.dev.projectgeneration.model.GeneratedProject;
import com.ouroboros.dev.projectgeneration.model.ProjectGenerationRequest;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;
import com.ouroboros.dev.projectgeneration.parameter.ProjectTemplateParameterValidator;
import com.ouroboros.dev.projectgeneration.render.ProjectTemplateRenderer;
import com.ouroboros.dev.projectgeneration.render.ProjectTemplateRendererLimits;
import com.ouroboros.dev.projectgeneration.service.impl.CatalogBackedProjectGenerationService;
import com.ouroboros.dev.projectgeneration.zip.DeterministicProjectZipWriter;
import com.ouroboros.util.ZipLimits;

class ProjectGenerationServiceTest {

  @TempDir
  java.nio.file.Path cacheDir;

  @Test
  void shouldGenerateDeterministicZipFromRenderedArtifact() throws Exception {
    byte[] archive = archive("""
        {
          "contractVersion": "2",
          "operations": [
            {"kind":"render","source":"tree/package.json.ftl","target":"package.json","contentType":"application/json"}
          ],
          "templateId": "frontend.micro-app.react-webpack",
          "templateVersion": "1.0.0"
        }
        """, Map.of("tree/package.json.ftl", ProjectGenerationBatch6TestSupport.utf8("""
            {"name": ${esc.json(packageName)}}
            """)));
    var service = service(archive);

    Path generatedPath;
    try (GeneratedProject generatedProject = service.generate("demo", request())) {
      generatedPath = generatedProject.path();

      assertEquals("customer-portal-1.0.0.zip", generatedProject.filename());
      assertTrue(generatedProject.size() > 0);
      assertNotNull(generatedProject.sha256());
      assertEquals(64, generatedProject.sha256().length());
      assertTrue(Files.exists(generatedPath));
      assertTrue(containsEntry(generatedProject.content(), "customer-portal/package.json"));
    }
    assertFalse(Files.exists(generatedPath.getParent()));
  }

  private CatalogBackedProjectGenerationService service(byte[] archive) {
    return new CatalogBackedProjectGenerationService(
        ProjectGenerationBatch6TestSupport.snapshotManagerForArtifact(archive),
        () -> "2.0.0",
        new ProjectTemplateCatalogResolver(),
        new ProjectTemplateParameterValidator(),
        ProjectGenerationBatch6TestSupport.artifactRepository(cacheDir, archive),
        new TemplateArchiveReader(ZipLimits.strict()),
        new ProjectTemplateRenderer(ProjectTemplateRendererLimits.defaults(), new ProjectTemplateParameterValidator()),
        new DeterministicProjectZipWriter()
    );
  }

  private static ProjectGenerationRequest request() {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("framework", "react");
    parameters.put("buildTool", "webpack");
    parameters.putAll(ProjectGenerationBatch6TestSupport.parameters());
    return new ProjectGenerationRequest(
        ProjectTemplateScope.MICRO_FRONTEND,
        "cat_100_abcd",
        "2.0.0",
        "frontend.micro-app",
        "1.0.0",
        "frontend.micro-app-1.0.0-platform-2.0.0",
        false,
        parameters
    );
  }

  private static byte[] archive(String manifestJson, Map<String, byte[]> treeEntries) throws Exception {
    return ProjectGenerationBatch6TestSupport.archive(manifestJson, treeEntries);
  }

  private static boolean containsEntry(InputStream content, String expectedEntry) throws Exception {
    try (ZipInputStream zip = new ZipInputStream(content)) {
      var entry = zip.getNextEntry();
      while (entry != null) {
        if (expectedEntry.equals(entry.getName())) {
          return true;
        }
        entry = zip.getNextEntry();
      }
    }
    return false;
  }
}

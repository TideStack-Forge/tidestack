package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogParser;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRelease;
import com.ouroboros.dev.projectgeneration.parameter.ProjectTemplateParameterValidator;
import com.ouroboros.dev.projectgeneration.render.ProjectTemplateRenderer;
import com.ouroboros.dev.projectgeneration.render.ProjectTemplateRendererLimits;

class ProjectTemplateFreemarkerRendererTest {

  @Test
  void shouldRenderContractV2FreemarkerContentAndTarget() throws Exception {
    var result = render(archive("""
        {
          "contractVersion": "2",
          "operations": [
            {"kind":"render","source":"tree/src/module.ts.ftl","target":"src/${path.sourceName}.ts","contentType":"text/typescript"}
          ],
          "templateId": "frontend.micro-app.react-webpack",
          "templateVersion": "1.0.0"
        }
        """, Map.of("tree/src/module.ts.ftl", ProjectGenerationBatch6TestSupport.utf8("""
            export const sourceName = ${esc.json(sourceName)};
            export const displayName = ${esc.json(displayName)};
            <#if includeExample>
            export const exampleRoute = "/example";
            </#if>
            export const mavenVersion = "<#noparse>${project.version}</#noparse>";
            """))));

    String output = text(result.entries().get("customer-portal/src/CustomerPortal.ts"));
    assertTrue(output.contains("export const sourceName = \"CustomerPortal\";"));
    assertTrue(output.contains("export const displayName = \"客户门户\";"));
    assertTrue(output.contains("export const exampleRoute = \"/example\";"));
    assertTrue(output.contains("export const mavenVersion = \"${project.version}\";"));
  }

  private static com.ouroboros.dev.projectgeneration.render.RenderedProjectTemplate render(
      Map<String, byte[]> archiveEntries
  ) {
    ProjectTemplateRelease release = release();
    return new ProjectTemplateRenderer(ProjectTemplateRendererLimits.defaults(), new ProjectTemplateParameterValidator())
        .render(
            release,
            ProjectGenerationBatch6TestSupport.activeRow(release),
            archiveEntries,
            ProjectGenerationBatch6TestSupport.parameters(),
            ProjectGenerationBatch6TestSupport.renderContext()
        );
  }

  private static ProjectTemplateRelease release() {
    return new ProjectTemplateCatalogParser().parse(ProjectGenerationBatch5TestSupport.snapshot(
        ProjectGenerationBatch6TestSupport.oneTemplate(
            ProjectGenerationBatch6TestSupport.renderContext().artifactSha256(),
            4
        )
    ).catalogBytes()).releases().getFirst();
  }

  private static Map<String, byte[]> archive(String manifestJson, Map<String, byte[]> treeEntries) throws Exception {
    return new com.ouroboros.dev.projectgeneration.artifact.TemplateArchiveReader(com.ouroboros.util.ZipLimits.strict())
        .read(new java.io.ByteArrayInputStream(ProjectGenerationBatch6TestSupport.archive(manifestJson, treeEntries)));
  }

  private static String text(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }
}

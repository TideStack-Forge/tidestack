package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.dev.projectgeneration.artifact.TemplateArchiveReader;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogParser;
import com.ouroboros.dev.projectgeneration.parameter.ProjectTemplateParameterValidator;
import com.ouroboros.dev.projectgeneration.render.ProjectTemplateRenderer;
import com.ouroboros.dev.projectgeneration.render.ProjectTemplateRendererLimits;
import com.ouroboros.util.ZipLimits;

class GeneratedContentParserCompilerTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldGenerateParserAndCompilerSafeJsonXmlPomJavaAndTypescriptCorpus() throws Exception {
    var release = new ProjectTemplateCatalogParser().parse(ProjectGenerationBatch5TestSupport.snapshot(
        ProjectGenerationBatch6TestSupport.oneTemplate(
            ProjectGenerationBatch6TestSupport.renderContext().artifactSha256(),
            4
        )
    ).catalogBytes()).releases().getFirst();
    var archive = new TemplateArchiveReader(ZipLimits.strict()).read(new java.io.ByteArrayInputStream(
        ProjectGenerationBatch6TestSupport.archive("""
            {
              "contractVersion": "2",
              "operations": [
                {"kind":"render","source":"tree/package.json.ftl","target":"package.json","contentType":"application/json"},
                {"kind":"render","source":"tree/config.xml.ftl","target":"config.xml","contentType":"application/xml"},
                {"kind":"render","source":"tree/pom.xml.ftl","target":"pom.xml","contentType":"application/xml"},
                {"kind":"render","source":"tree/App.java.ftl","target":"src/main/java/com/acme/${path.sourceName}.java","contentType":"text/x-java"},
                {"kind":"render","source":"tree/index.ts.ftl","target":"src/${path.sourceName}.ts","contentType":"text/typescript"}
              ],
              "templateId": "frontend.micro-app.react-webpack",
              "templateVersion": "1.0.0"
            }
            """, Map.of(
            "tree/package.json.ftl", utf8("""
                {"name": ${esc.json(packageName)}, "version": "1.0.0"}
                """),
            "tree/config.xml.ftl", utf8("""
                <config name="${esc.xmlAttribute(projectName)}">${esc.xmlText(packageName)}</config>
                """),
            "tree/pom.xml.ftl", utf8("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>${esc.xmlText(groupId)}</groupId><artifactId>${esc.xmlText(projectName)}</artifactId><version>1.0.0</version></project>
                """),
            "tree/App.java.ftl", utf8("""
                package com.acme;
                public final class ${esc.identifier(sourceName)} {
                  public static final String NAME = ${esc.javaString(sourceName)};
                }
                """),
            "tree/index.ts.ftl", utf8("""
                export const ${esc.identifier(sourceName)} = ${esc.json(sourceName)};
                """)
        ))
    ));

    var rendered = new ProjectTemplateRenderer(ProjectTemplateRendererLimits.defaults(), new ProjectTemplateParameterValidator())
        .render(
            release,
            ProjectGenerationBatch6TestSupport.activeRow(release),
            archive,
            ProjectGenerationBatch6TestSupport.parameters(),
            ProjectGenerationBatch6TestSupport.renderContext()
        );

    assertEquals("@acme/customer-portal", new ObjectMapper()
        .readTree(rendered.entries().get("customer-portal/package.json"))
        .path("name")
        .asText());
    assertEquals("config", parseXml(text(rendered.entries().get("customer-portal/config.xml"))).getDocumentElement()
        .getNodeName());
    assertEquals("project", parseXml(text(rendered.entries().get("customer-portal/pom.xml"))).getDocumentElement()
        .getLocalName());
    assertCompiles(rendered.entries().get("customer-portal/src/main/java/com/acme/CustomerPortal.java"));
    assertTrue(text(rendered.entries().get("customer-portal/src/CustomerPortal.ts"))
        .contains("export const CustomerPortal = \"CustomerPortal\";"));
  }

  private static org.w3c.dom.Document parseXml(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    return factory.newDocumentBuilder().parse(new InputSource(new java.io.StringReader(xml)));
  }

  private void assertCompiles(byte[] javaSource) throws Exception {
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "JDK compiler is required");
    Path source = tempDir.resolve("CustomerPortal.java");
    Path output = tempDir.resolve("classes");
    Files.createDirectories(output);
    Files.write(source, javaSource);
    assertEquals(0, compiler.run(null, null, null, "-d", output.toString(), source.toString()));
  }

  private static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static String text(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }
}

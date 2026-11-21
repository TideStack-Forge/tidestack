package com.ouroboros.dev.projectgeneration;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.ouroboros.dev.projectgeneration.repository.TemplateArtifactRepository;
import com.ouroboros.dev.projectgeneration.catalog.CanonicalJson;
import com.ouroboros.dev.projectgeneration.catalog.InMemoryProjectTemplateCatalogReplayStateStore;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogFetchResult;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogRemoteException;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogSnapshotManager;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCompatibilityRecord;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteFetchPolicy;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRelease;
import com.ouroboros.dev.projectgeneration.catalog.SignedProjectTemplateCatalogVerifier;
import com.ouroboros.dev.projectgeneration.render.ProjectTemplateRenderContext;

final class ProjectGenerationBatch6TestSupport {

  private ProjectGenerationBatch6TestSupport() {
  }

  static Map<String, Object> parameters() {
    return Map.of(
        "projectName", "customer-portal",
        "packageName", "@acme/customer-portal",
        "sourceName", "CustomerPortal",
        "displayName", "客户门户",
        "groupId", "com.acme",
        "includeExample", true
    );
  }

  static ProjectTemplateRenderContext renderContext() {
    return new ProjectTemplateRenderContext(
        "cat_100_abcd",
        "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
        "2.0.0",
        "frontend.micro-app.react-webpack-1.0.0-platform-2.0.0",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    );
  }

  static String parameterSchema() {
    return """
        {
          "x-ouroboros-project-root": "projectName",
          "additionalProperties": false,
          "properties": {
            "groupId": {
              "default": "com.example",
              "maxLength": 80,
              "type": "string",
              "x-ouroboros-format": "java-package"
            },
            "displayName": {
              "default": "示例项目",
              "maxLength": 80,
              "type": "string"
            },
            "includeExample": {
              "default": true,
              "type": "boolean"
            },
            "packageName": {
              "default": "demo-app",
              "maxLength": 80,
              "type": "string",
              "x-ouroboros-format": "npm-package-name"
            },
            "projectName": {
              "maxLength": 64,
              "type": "string",
              "x-ouroboros-format": "path-segment"
            },
            "sourceName": {
              "default": "DemoApp",
              "maxLength": 80,
              "type": "string",
              "x-ouroboros-format": "java-class-name"
            }
          },
          "required": ["projectName"],
          "type": "object"
        }
        """;
  }

  static String oneTemplate(String artifactSha256, long compressedSize) {
    return """
        [{
          "artifact": {
            "compressedSize": %d,
            "sha256": "%s",
            "uri": "https://templates.example.com/artifact.zip"
          },
          "compatibilityRecords": [%s],
          "description": "生成可挂载到平台的前端应用。",
          "displayName": "React 微前端应用",
          "parameterSchema": %s,
          "scope": "MICRO_FRONTEND",
          "templateId": "frontend.micro-app.react-webpack",
          "templateVersion": "1.0.0",
          "uiHints": {}
        }]
        """.formatted(compressedSize, artifactSha256, ProjectGenerationBatch5TestSupport.activeStableRow(), parameterSchema());
  }

  static ProjectTemplateCompatibilityRecord activeRow(ProjectTemplateRelease release) {
    return release.compatibilityRecords().getFirst();
  }

  static byte[] archive(String manifestJson, Map<String, byte[]> treeEntries) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      zip.putNextEntry(new ZipEntry("template.json"));
      zip.write(utf8(manifestJson));
      zip.closeEntry();
      for (Map.Entry<String, byte[]> entry : treeEntries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }

  static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  static ProjectTemplateCatalogSnapshotManager snapshotManagerForArtifact(byte[] artifactBytes) {
    KeyPair keyPair = CatalogTestSupport.ed25519KeyPair();
    byte[] catalog = CanonicalJson.canonicalize(CatalogTestSupport.utf8("""
        {
          "contractVersion": "1",
          "expiresAt": "%s",
          "issuedAt": "%s",
          "revision": "cat_100_abcd",
          "sequence": 100,
          "templates": %s
        }
        """.formatted(
            ProjectGenerationBatch5TestSupport.NOW.plusSeconds(3600),
            ProjectGenerationBatch5TestSupport.NOW.minusSeconds(60),
            oneTemplate(
                SignedProjectTemplateCatalogVerifier.sha256Hex(artifactBytes),
                artifactBytes.length
            )
        )));
    byte[] envelope = CatalogTestSupport.utf8(
        CatalogTestSupport.signatureEnvelope("catalog-key-1", keyPair, catalog)
    );
    var verifier = new SignedProjectTemplateCatalogVerifier(
        Map.of("catalog-key-1", keyPair.getPublic()),
        ProjectGenerationBatch5TestSupport.CLOCK
    );
    return new ProjectTemplateCatalogSnapshotManager(
        "project-template-catalog",
        verifier,
        new InMemoryProjectTemplateCatalogReplayStateStore(),
        () -> new ProjectTemplateCatalogFetchResult(catalog, envelope),
        ProjectGenerationBatch5TestSupport.CLOCK
    );
  }

  static TemplateArtifactRepository artifactRepository(java.nio.file.Path cacheDir, byte[] body) {
    return new TemplateArtifactRepository(
        cacheDir,
        ProjectTemplateRemoteFetchPolicy.exactOrigin("https://templates.example.com", List.of()),
        (uri, policy, maxBytes, connectTimeout, readTimeout, requestHeaders) -> {
          try {
            java.net.InetAddress address = java.net.InetAddress.getByName("93.184.216.34");
            return new com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteHttpResponse(
                200,
                body,
                List.of(address),
                address,
                Map.of()
            );
          } catch (Exception e) {
            throw new ProjectTemplateCatalogRemoteException("Cannot build fake artifact response", e);
          }
        },
        body.length,
        body.length * 2L,
        0,
        Duration.ofMillis(100),
        Duration.ofMillis(100)
    );
  }
}

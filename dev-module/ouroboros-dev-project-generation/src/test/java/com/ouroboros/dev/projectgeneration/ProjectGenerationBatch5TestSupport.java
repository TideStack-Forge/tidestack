package com.ouroboros.dev.projectgeneration;

import java.net.URI;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.ouroboros.dev.projectgeneration.catalog.CanonicalJson;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogSnapshot;
import com.ouroboros.dev.projectgeneration.catalog.SignedProjectTemplateCatalogVerifier;

final class ProjectGenerationBatch5TestSupport {

  static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  static final String ARTIFACT_SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

  private ProjectGenerationBatch5TestSupport() {
  }

  static ProjectTemplateCatalogSnapshot snapshot(String templatesJson) {
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
        """.formatted(NOW.plusSeconds(3600), NOW.minusSeconds(60), templatesJson)));
    byte[] envelope = CatalogTestSupport.utf8(
        CatalogTestSupport.signatureEnvelope("catalog-key-1", keyPair, catalog)
    );
    return new SignedProjectTemplateCatalogVerifier(
        Map.of("catalog-key-1", keyPair.getPublic()),
        CLOCK
    ).verify(catalog, envelope);
  }

  static String oneTemplate(String compatibilityRecordsJson) {
    return """
        [{
          "artifact": {
            "compressedSize": 4,
            "sha256": "%s",
            "uri": "https://templates.example.com/artifact.zip"
          },
          "compatibilityRecords": %s,
          "description": "生成可挂载到平台的前端应用。",
          "displayName": "React 微前端应用",
          "parameterSchema": %s,
          "scope": "MICRO_FRONTEND",
          "templateId": "frontend.micro-app.react-webpack",
          "templateVersion": "1.0.0",
          "uiHints": {}
        }]
        """.formatted(ARTIFACT_SHA, compatibilityRecordsJson, parameterSchema());
  }

  static String activeStableRow() {
    return row("frontend.micro-app.react-webpack-1.0.0-platform-2.0.0", "stable", "ACTIVE", "[2.0.0,2.1.0)", List.of("2.0.0"));
  }

  static String activePreviewRow() {
    return row("frontend.micro-app.react-webpack-1.0.0-preview-2.0.0", "preview", "ACTIVE", "[2.0.0,2.1.0)", List.of("2.0.0"));
  }

  static String row(String rowId, String channel, String status, String range, List<String> testedVersions) {
    String tested = testedVersions.stream()
        .map(version -> "\"" + version + "\"")
        .collect(java.util.stream.Collectors.joining(","));
    return """
        {
          "certificationEvidence": {
            "attestationSha256": "%s",
            "qualificationDescriptorSha256": "%s"
          },
          "channel": "%s",
          "declaredPlatformRange": "%s",
          "rowId": "%s",
          "status": "%s",
          "testedPlatformVersions": [%s],
          "toolchain": {
            "node": "22.23.1"
          }
        }
        """.formatted(ARTIFACT_SHA, ARTIFACT_SHA, channel, range, rowId, status, tested);
  }

  static String parameterSchema() {
    return """
        {
          "x-ouroboros-project-root": "projectName",
          "additionalProperties": false,
          "properties": {
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
            }
          },
          "required": ["projectName"],
          "type": "object"
        }
        """;
  }

  static URI artifactUri() {
    return URI.create("https://templates.example.com/artifact.zip");
  }
}

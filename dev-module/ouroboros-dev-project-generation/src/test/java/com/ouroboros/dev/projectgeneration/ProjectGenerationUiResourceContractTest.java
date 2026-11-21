package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProjectGenerationUiResourceContractTest {

  @Test
  void shouldMountMicroFrontendEntryWithFixedMicroFrontendScope() throws Exception {
    String schema = readRepoFile("core/ouroboros-ui-model/ouroboros-ui-model-microweb-dev/src/main/metadata/dev/{appName}/micro-web.ui-schema.json");
    assertTrue(schema.contains("\"label\": \"生成项目\""));
    assertTrue(schema.indexOf("\"label\": \"生成项目\"") < schema.indexOf("\"label\": \"新建\""));
    assertTrue(schema.contains("\"level\": \"enhance\""));
    assertTrue(schema.contains("\"type\": \"ui-schema\""));
    assertTrue(schema.contains("/api/dev/${appName}/project-generation-form"));
    assertTrue(schema.contains("\"scope\": \"MICRO_FRONTEND\""));
  }

  @Test
  void shouldMountBackendExtensionEntryWithFixedBackendExtensionScope() throws Exception {
    String schema = readRepoFile("dev-module/ouroboros-dev-app-management/src/main/metadata/dev/{appName}/extend-jar-management.ui-schema.json");
    assertTrue(schema.contains("\"label\": \"生成项目\""));
    assertTrue(schema.indexOf("\"label\": \"生成项目\"") < schema.indexOf("\"label\": \"上传\""));
    assertTrue(schema.contains("\"level\": \"enhance\""));
    assertTrue(schema.contains("\"type\": \"ui-schema\""));
    assertTrue(schema.contains("/api/dev/${appName}/project-generation-form"));
    assertTrue(schema.contains("\"scope\": \"BACKEND_EXTENSION\""));
  }

  @Test
  void shouldMountPlatformComponentLibraryEntryWithFixedPlatformComponentLibraryScope() throws Exception {
    String schema = readRepoFile("dev-module/ouroboros-dev-platform-component-lib/src/main/metadata/platform-component-lib.ui-schema.json");
    assertTrue(schema.contains("\"label\": \"生成项目\""));
    assertTrue(schema.indexOf("\"label\": \"上传组件库\"") < schema.indexOf("\"label\": \"生成项目\""));
    assertTrue(schema.contains("\"level\": \"enhance\""));
    assertTrue(schema.contains("\"type\": \"ui-schema\""));
    assertTrue(schema.contains("/api/project-generation-form"));
    assertTrue(schema.contains("\"scope\": \"PLATFORM_COMPONENT_LIBRARY\""));

    String authority = readRepoFile("dev-module/ouroboros-dev-platform-component-lib/src/main/metadata/platformComponentLib.authority.json");
    assertTrue(authority.contains("GET /api/project-templates"));

    String appSchema = readRepoFile("dev-module/ouroboros-dev-platform-component-lib/src/main/metadata/dev/{appName}/platform-component-lib.ui-schema.json");
    assertTrue(!appSchema.contains("\"label\": \"生成项目\""));
  }

  private static String readRepoFile(String relativePath) throws IOException {
    List<Path> candidates = List.of(Path.of(relativePath), Path.of("..", "..", relativePath));
    Path path = candidates.stream()
        .map(Path::toAbsolutePath)
        .map(Path::normalize)
        .filter(Files::exists)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("missing repo file: " + relativePath));
    return Files.readString(path);
  }
}

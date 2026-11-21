package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProjectGenerationArchitectureTest {

  @Test
  void shouldBeOwnedByDedicatedDevModuleAndAggregatedByDevModuleAll() throws Exception {
    String aggregatorPom = readRepoFile("dev-module/ouroboros-dev-module-all/pom.xml");
    assertTrue(aggregatorPom.contains("<artifactId>ouroboros-dev-project-generation</artifactId>"));
  }

  @Test
  void shouldKeepTemplateSourceOutsideRuntimeModuleSources() throws Exception {
    Path module = resolveRepoFile("dev-module/ouroboros-dev-project-generation/src/main");
    try (var files = Files.walk(module)) {
      boolean hasTemplateArtifacts = files
          .filter(Files::isRegularFile)
          .map(Path::toString)
          .anyMatch(path -> path.contains("project-templates") || path.endsWith("template-artifact.zip"));
      assertTrue(!hasTemplateArtifacts, "runtime module sources must not embed generated template artifacts");
    }
  }

  @Test
  void shouldPackageOfficialProjectTemplatesIntoDevelopImageByDefault() throws Exception {
    String application = readRepoFile("platform/ouroboros-develop/docker/root/app/metadata/application.yml");
    assertTrue(application.contains("local-template-catalog:"));
    assertTrue(application.contains("enabled: true"));
    assertTrue(application.contains("root: /app/project-templates"));
    assertTrue(application.contains("catalog: /app/project-templates/catalog/stable-catalog.json"));
    assertTrue(application.contains("artifact-root: /app/project-templates/artifacts"));
    assertTrue(application.contains("cache: /app/runtime/project-generation-template-cache"));
    assertTrue(application.contains("template-origin: https://templates.example.invalid"));
    assertTrue(!application.contains("/app/project-templates/target"));

    String localRuntime = readRepoFile("project-generation-test/runtime/application-project-generation-local.yml");
    assertTrue(localRuntime.contains("template-origin: https://templates.example.invalid"));
    assertTrue(localRuntime.contains("/project-templates/dist/catalog/stable-catalog.json"));
    assertTrue(localRuntime.contains("/project-templates/dist/artifacts"));
    assertTrue(!localRuntime.contains("/project-templates/target/publication"));

    String localCatalog = readRepoFile(
        "dev-module/ouroboros-dev-project-generation/src/main/java/com/ouroboros/dev/projectgeneration/config/LocalProjectGenerationCatalogConfiguration.java");
    assertTrue(localCatalog.contains("DEFAULT_TEMPLATE_ROOT = \"/app/project-templates\""));
    assertTrue(localCatalog.contains("DEFAULT_CATALOG = \"/app/project-templates/catalog/stable-catalog.json\""));
    assertTrue(localCatalog.contains("DEFAULT_ARTIFACT_ROOT = \"/app/project-templates/artifacts\""));
    assertTrue(localCatalog.contains("DEFAULT_CACHE = \"/app/runtime/project-generation-template-cache\""));
    assertTrue(!localCatalog.contains("DEFAULT_CATALOG = \"/app/project-templates/target"));

    String dockerfile = readRepoFile("platform/ouroboros-develop/docker/dockerfile");
    assertTrue(dockerfile.contains("COPY ./template-layer /"));
    assertTrue(dockerfile.indexOf("COPY ./template-layer /") < dockerfile.indexOf("COPY ./build-layer /"));

    String buildScript = readRepoFile("scripts/build-local-develop.sh");
    assertTrue(buildScript.contains("TEMPLATE_LAYER_PATH=\"$(pwd)/docker/template-layer\""));
    assertTrue(buildScript.contains("PROJECT_TEMPLATES_PATH=\"${TEMPLATE_LAYER_PATH}/app/project-templates\""));
    assertTrue(buildScript.contains("ensure_frontend_template_dependencies"));
    assertTrue(buildScript.contains("verify-frontend-templates.sh --qualification --clean"));
    assertTrue(buildScript.contains("verify-backend-templates.sh --qualification --clean --platform-smoke"));
    assertTrue(buildScript.contains("verify-template-publication.sh --clean"));
    assertTrue(buildScript.contains("cp \"project-templates/dist/catalog/stable-catalog.json\""));
    assertTrue(buildScript.contains("cp -R \"project-templates/dist/artifacts/.\""));

    String ciBuildScript = readRepoFile("build.sh");
    assertTrue(ciBuildScript.contains("local template_layer_path=\"$(pwd)/docker/template-layer\""));
    assertTrue(ciBuildScript.contains("local project_templates_path=\"${template_layer_path}/app/project-templates\""));
    assertTrue(ciBuildScript.contains("ensure_frontend_template_dependencies"));
    assertTrue(ciBuildScript.contains("verify-frontend-templates.sh --qualification --clean"));
    assertTrue(ciBuildScript.contains("verify-backend-templates.sh --qualification --clean --platform-smoke"));
    assertTrue(ciBuildScript.contains("verify-template-publication.sh --clean"));
    assertTrue(ciBuildScript.contains("cp \"project-templates/dist/catalog/stable-catalog.json\""));
    assertTrue(ciBuildScript.contains("cp -R \"project-templates/dist/artifacts/.\""));
  }

  private static String readRepoFile(String relativePath) throws IOException {
    return Files.readString(resolveRepoFile(relativePath));
  }

  private static Path resolveRepoFile(String relativePath) {
    List<Path> candidates = List.of(Path.of(relativePath), Path.of("..", "..", relativePath));
    return candidates.stream()
        .map(Path::toAbsolutePath)
        .map(Path::normalize)
        .filter(Files::exists)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("missing repo file: " + relativePath));
  }
}

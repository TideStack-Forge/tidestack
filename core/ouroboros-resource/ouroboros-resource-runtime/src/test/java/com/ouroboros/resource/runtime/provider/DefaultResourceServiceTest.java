package com.ouroboros.resource.runtime.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.StreamUtils;

import com.ouroboros.resource.model.ResourceLayer;
import com.ouroboros.resource.model.ResourceReadResult;
import com.ouroboros.resource.model.ResourceResolveContext;
import com.ouroboros.resource.model.ResourceScope;

class DefaultResourceServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void runtimeOverrideWinsOverAppDefault() throws Exception {
    Path appDefaultRoot = tempDir.resolve("app-default");
    Path runtimeOverrideRoot = tempDir.resolve("runtime-override");
    writeFile(appDefaultRoot, "frontend/templates/default.txt", "default-template");
    writeFile(runtimeOverrideRoot, "frontend/templates/default.txt", "override-template");

    DefaultResourceService service = new DefaultResourceService(
        new RuntimeOverrideResourceProvider(new com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory().create(java.util.Map.of("root-directory", runtimeOverrideRoot.toString()))),
        new AppDefaultResourceProvider(appDefaultRoot));

    ResourceReadResult result =
        service.read(new ResourceResolveContext(ResourceScope.FRONTEND, "templates/default.txt"));

    assertEquals(ResourceLayer.RUNTIME_OVERRIDE, result.getEffectiveLayer());
    assertTrue(result.isHasOverride());
    assertTrue(result.isDefaultExists());
    assertEquals("override-template", readContent(result));
  }

  @Test
  void fallsBackToAppDefaultWhenNoOverrideExists() throws Exception {
    Path appDefaultRoot = tempDir.resolve("app-default");
    Path runtimeOverrideRoot = tempDir.resolve("runtime-override");
    writeFile(appDefaultRoot, "backend/config/default.yml", "mode: default");

    DefaultResourceService service = new DefaultResourceService(
        new RuntimeOverrideResourceProvider(new com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory().create(java.util.Map.of("root-directory", runtimeOverrideRoot.toString()))),
        new AppDefaultResourceProvider(appDefaultRoot));

    ResourceReadResult result =
        service.read(new ResourceResolveContext(ResourceScope.BACKEND, "config/default.yml"));

    assertEquals(ResourceLayer.APP_DEFAULT, result.getEffectiveLayer());
    assertFalse(result.isHasOverride());
    assertTrue(result.isDefaultExists());
    assertEquals("mode: default", readContent(result));
  }

  @Test
  void derivesMetadataForBareRuntimeOverrideFile() throws Exception {
    Path appDefaultRoot = tempDir.resolve("app-default");
    Path runtimeOverrideRoot = tempDir.resolve("runtime-override");
    writeFile(appDefaultRoot, "frontend/templates/default.txt", "default-template");
    writeFile(runtimeOverrideRoot, "frontend/templates/default.txt", "override-template");

    DefaultResourceService service = new DefaultResourceService(
        new RuntimeOverrideResourceProvider(new com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory().create(java.util.Map.of("root-directory", runtimeOverrideRoot.toString()))),
        new AppDefaultResourceProvider(appDefaultRoot));

    ResourceReadResult result =
        service.read(new ResourceResolveContext(ResourceScope.FRONTEND, "templates/default.txt"));

    assertEquals("text/plain", result.getEffectiveDescriptor().getContentType());
    assertEquals(Long.valueOf("override-template".getBytes(StandardCharsets.UTF_8).length),
        result.getEffectiveDescriptor().getSize());
    assertEquals(calculateChecksum("override-template"),
        result.getEffectiveDescriptor().getChecksum());
  }

  @Test
  void derivesMetadataForBareAppDefaultFile() throws Exception {
    Path appDefaultRoot = tempDir.resolve("app-default");
    Path runtimeOverrideRoot = tempDir.resolve("runtime-override");
    writeFile(appDefaultRoot, "backend/config/default.txt", "mode: default");

    DefaultResourceService service = new DefaultResourceService(
        new RuntimeOverrideResourceProvider(new com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory().create(java.util.Map.of("root-directory", runtimeOverrideRoot.toString()))),
        new AppDefaultResourceProvider(appDefaultRoot));

    ResourceReadResult result =
        service.read(new ResourceResolveContext(ResourceScope.BACKEND, "config/default.txt"));

    assertEquals("text/plain", result.getEffectiveDescriptor().getContentType());
    assertEquals(Long.valueOf("mode: default".getBytes(StandardCharsets.UTF_8).length),
        result.getEffectiveDescriptor().getSize());
    assertEquals(calculateChecksum("mode: default"),
        result.getEffectiveDescriptor().getChecksum());
  }

  @Test
  void fileBasedListingsIgnoreIndexEntriesWithoutBackingFiles() throws Exception {
    Path appDefaultRoot = tempDir.resolve("app-default");
    writeFile(appDefaultRoot, "frontend/templates/actual.txt", "actual-template");
    writeFile(appDefaultRoot, "frontend/templates/index.json", "{\n"
        + "  \"path\": \"frontend/templates\",\n"
        + "  \"resources\": [\n"
        + "    { \"name\": \"actual.txt\" },\n"
        + "    { \"name\": \"ghost.txt\" }\n"
        + "  ]\n"
        + "}");

    AppDefaultResourceProvider provider = new AppDefaultResourceProvider(appDefaultRoot);

    assertEquals(Collections.singletonList("frontend/templates/actual.txt"),
        provider.listResources("frontend/templates"));
  }

  private void writeFile(Path root, String relativePath, String content) throws IOException {
    Path file = root.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }

  private String readContent(ResourceReadResult result) throws IOException {
    return StreamUtils.copyToString(result.getEffectiveResource().getContent(), StandardCharsets.UTF_8);
  }

  private String calculateChecksum(String content) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
    StringBuilder builder = new StringBuilder();
    for (byte value : hash) {
      builder.append(String.format("%02x", value));
    }
    return "sha256:" + builder;
  }
}

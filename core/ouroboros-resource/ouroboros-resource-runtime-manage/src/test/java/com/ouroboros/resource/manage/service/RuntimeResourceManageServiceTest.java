package com.ouroboros.resource.manage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.resource.manage.dto.RuntimeResourceItem;
import com.ouroboros.resource.model.ResourceLayer;
import com.ouroboros.resource.model.ResourceMetadata;
import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.resource.runtime.provider.AppDefaultResourceProvider;
import com.ouroboros.resource.runtime.provider.DefaultResourceService;
import com.ouroboros.resource.runtime.provider.RuntimeOverrideResourceProvider;
import com.ouroboros.web.TreeSelectOption;
import com.ouroboros.util.Json;

class RuntimeResourceManageServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void uploadListAndDeleteOverridePreserveLayerSemantics() throws Exception {
    Path appDefaultRoot = tempDir.resolve("app-default");
    Path runtimeOverrideRoot = tempDir.resolve("runtime-override");
    writeFile(appDefaultRoot, "frontend/templates/default.txt", "default-template");
    writeSidecar(appDefaultRoot, "frontend/templates/default.txt", new ResourceMetadata()
        .setScope(ResourceScope.FRONTEND)
        .setKey("templates/default.txt")
        .setContentType("text/plain")
        .setDisplayName("Default Template"));

    RuntimeResourceManageService manageService = new RuntimeResourceManageService(
        new DefaultResourceService(
            new RuntimeOverrideResourceProvider(new com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory().create(java.util.Map.of("root-directory", runtimeOverrideRoot.toString()))),
            new AppDefaultResourceProvider(appDefaultRoot)),
        new AppDefaultResourceProvider(appDefaultRoot),
        new RuntimeOverrideResourceProvider(new com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory().create(java.util.Map.of("root-directory", runtimeOverrideRoot.toString())))
    );

    RuntimeResourceItem uploaded = manageService.upload(
        ResourceScope.FRONTEND,
        "templates/default.txt",
        "override-template".getBytes(StandardCharsets.UTF_8),
        "text/plain",
        "default.txt",
        "tester"
    );

    assertEquals(ResourceLayer.RUNTIME_OVERRIDE, uploaded.getEffectiveLayer());
    assertTrue(uploaded.isHasOverride());
    assertTrue(uploaded.isDefaultExists());
    assertEquals("text/plain", uploaded.getContentType());
    assertEquals(Long.valueOf("override-template".getBytes(StandardCharsets.UTF_8).length), uploaded.getSize());

    List<RuntimeResourceItem> items = manageService.list(ResourceScope.FRONTEND, "templates/default");
    assertEquals(1, items.size());
    assertEquals("templates/default.txt", items.get(0).getKey());

    RuntimeResourceItem restored = manageService.deleteOverride(ResourceScope.FRONTEND, "templates/default.txt");

    assertEquals(ResourceLayer.APP_DEFAULT, restored.getEffectiveLayer());
    assertFalse(restored.isHasOverride());
    assertTrue(restored.isDefaultExists());
    assertEquals("Default Template", restored.getDisplayName());
  }

  @Test
  void listAndTreeSupportDirectoryBrowsingAcrossDefaultAndOverrideResources() throws Exception {
    Path appDefaultRoot = tempDir.resolve("app-default");
    Path runtimeOverrideRoot = tempDir.resolve("runtime-override");
    writeFile(appDefaultRoot, "frontend/templates/default.txt", "default-template");
    writeSidecar(appDefaultRoot, "frontend/templates/default.txt", new ResourceMetadata()
        .setScope(ResourceScope.FRONTEND)
        .setKey("templates/default.txt")
        .setContentType("text/plain"));
    writeFile(appDefaultRoot, "frontend/templates/mail/welcome.html", "<h1>welcome</h1>");
    writeSidecar(appDefaultRoot, "frontend/templates/mail/welcome.html", new ResourceMetadata()
        .setScope(ResourceScope.FRONTEND)
        .setKey("templates/mail/welcome.html")
        .setContentType("text/html"));
    writeFile(runtimeOverrideRoot, "frontend/templates/mail/reset.html", "<h1>reset</h1>");
    writeSidecar(runtimeOverrideRoot, "frontend/templates/mail/reset.html", new ResourceMetadata()
        .setScope(ResourceScope.FRONTEND)
        .setKey("templates/mail/reset.html")
        .setContentType("text/html")
        .setLayer(ResourceLayer.RUNTIME_OVERRIDE));
    writeFile(runtimeOverrideRoot, "frontend/themes/dark.css", "body { color: #111; }");
    writeSidecar(runtimeOverrideRoot, "frontend/themes/dark.css", new ResourceMetadata()
        .setScope(ResourceScope.FRONTEND)
        .setKey("themes/dark.css")
        .setContentType("text/css")
        .setLayer(ResourceLayer.RUNTIME_OVERRIDE));

    RuntimeResourceManageService manageService = new RuntimeResourceManageService(
        new DefaultResourceService(
            new RuntimeOverrideResourceProvider(new com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory().create(java.util.Map.of("root-directory", runtimeOverrideRoot.toString()))),
            new AppDefaultResourceProvider(appDefaultRoot)),
        new AppDefaultResourceProvider(appDefaultRoot),
        new RuntimeOverrideResourceProvider(new com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory().create(java.util.Map.of("root-directory", runtimeOverrideRoot.toString())))
    );

    List<RuntimeResourceItem> directoryItems = manageService.list(ResourceScope.FRONTEND, "templates/mail", null);

    assertEquals(Arrays.asList(
        "templates/mail/reset.html",
        "templates/mail/welcome.html"
    ), directoryItems.stream().map(RuntimeResourceItem::getKey).collect(Collectors.toList()));

    List<TreeSelectOption> directories = manageService.listDirectories(ResourceScope.FRONTEND);

    assertEquals(Arrays.asList("templates", "themes"),
        directories.stream().map(TreeSelectOption::getValue).collect(Collectors.toList()));
    assertEquals(Arrays.asList("templates/mail"),
        directories.get(0).getChildren().stream().map(TreeSelectOption::getValue).collect(Collectors.toList()));
  }

  @Test
  void uploadRejectsPathTraversalKeys() {
    Path appDefaultRoot = tempDir.resolve("app-default");
    Path runtimeOverrideRoot = tempDir.resolve("runtime-override");

    RuntimeResourceManageService manageService = new RuntimeResourceManageService(
        new DefaultResourceService(
            new RuntimeOverrideResourceProvider(new com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory().create(java.util.Map.of("root-directory", runtimeOverrideRoot.toString()))),
            new AppDefaultResourceProvider(appDefaultRoot)),
        new AppDefaultResourceProvider(appDefaultRoot),
        new RuntimeOverrideResourceProvider(new com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory().create(java.util.Map.of("root-directory", runtimeOverrideRoot.toString())))
    );

    assertThrows(BusinessException.class, () -> manageService.upload(
        ResourceScope.FRONTEND,
        "../../escaped.txt",
        "override-template".getBytes(StandardCharsets.UTF_8),
        "text/plain",
        "escaped.txt",
        "tester"
    ));
    assertFalse(Files.exists(tempDir.resolve("escaped.txt")));
  }

  private static void writeFile(Path root, String scopedPath, String content) throws Exception {
    Path file = root.resolve(scopedPath);
    Files.createDirectories(file.getParent());
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }

  private static void writeSidecar(Path root, String scopedPath, ResourceMetadata metadata) throws Exception {
    Path sidecar = root.resolve(scopedPath + ".resource.json");
    Files.createDirectories(sidecar.getParent());
    Files.write(sidecar, Json.toPrettyJsonString(metadata).getBytes(StandardCharsets.UTF_8));
  }
}

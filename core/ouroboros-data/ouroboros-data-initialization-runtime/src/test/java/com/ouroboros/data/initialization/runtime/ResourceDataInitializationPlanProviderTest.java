package com.ouroboros.data.initialization.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceDataInitializationPlanProviderTest {
  @TempDir
  Path tempDir;

  @Test
  void loadsClasspathDataInitResources() {
    var provider = new ResourceDataInitializationPlanProvider(Thread.currentThread().getContextClassLoader(), null);

    var assets = provider.loadPlans();

    assertTrue(assets.stream().anyMatch(asset -> "test-status".equals(asset.plan().id())));
    var asset = assets.stream().filter(item -> "test-status".equals(item.plan().id())).findFirst().get();
    assertEquals("classpath", asset.sourceKind());
    assertEquals("Status", asset.plan().blocks().get(0).targetModel());
    assertEquals(List.of("code"), asset.plan().blocks().get(0).matchFields());
  }

  @Test
  void loadsAppHomeDataInitResources() throws Exception {
    Path dataInit = Files.createDirectories(tempDir.resolve("data-init"));
    Files.writeString(dataInit.resolve("external.data-init.json"), """
        {
          "id": "external-status",
          "version": "v1",
          "blocks": [
            {
              "id": "statuses",
              "targetModel": "Status",
              "matchFields": ["code"],
              "records": [{"code": "disabled", "label": "Disabled"}]
            }
          ]
        }
        """);
    var provider = new ResourceDataInitializationPlanProvider(new URLClassLoader(new URL[0], null), tempDir);

    var assets = provider.loadPlans();

    assertEquals(1, assets.size());
    assertEquals("app-home", assets.get(0).sourceKind());
    assertEquals("external-status", assets.get(0).plan().id());
  }
}

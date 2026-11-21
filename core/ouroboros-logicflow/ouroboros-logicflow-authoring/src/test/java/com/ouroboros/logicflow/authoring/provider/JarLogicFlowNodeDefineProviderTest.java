package com.ouroboros.logicflow.authoring.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarLogicFlowNodeDefineProviderTest {
  @TempDir
  Path directory;

  @Test
  void enumerationFailureDoesNotBlockAndCanRetry() throws Exception {
    var reads = new java.util.concurrent.atomic.AtomicInteger();
    try (var classLoader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null) {
      @Override
      public java.util.Enumeration<URL> getResources(String name) throws java.io.IOException {
        if (reads.incrementAndGet() == 1) {
          throw new java.io.IOException("jar unavailable");
        }
        return super.getResources(name);
      }
    }) {
      var provider = new JarLogicFlowNodeDefineProvider(classLoader);
      assertTrue(provider.getNodeDefines().isEmpty());
      assertTrue(provider.getNodeDefines().isEmpty());
      assertTrue(provider.getNodeDefines().isEmpty());
      assertEquals(2, reads.get());
    }
  }

  @Test
  void loadedNodesSurviveResourceRemovalAndCallerMutation() throws Exception {
    var resource = directory.resolve("META-INF/ouroboros/metadata/test.flow-node.json");
    Files.createDirectories(resource.getParent());
    Files.writeString(resource, "{\"type\":\"test\",\"label\":\"Original\"}");
    try (var classLoader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null)) {
      var provider = new JarLogicFlowNodeDefineProvider(classLoader);
      var first = provider.getNodeDefines();
      first.getFirst().setLabel("changed");
      first.clear();
      Files.delete(resource);

      assertEquals("Original", provider.getNodeDefines().getFirst().getLabel());
    }
  }

  @Test
  void brokenNodeIsSkippedAndCanRetry() throws Exception {
    var root = directory.resolve("META-INF/ouroboros/metadata");
    Files.createDirectories(root);
    Files.writeString(root.resolve("good.flow-node.json"), "{\"type\":\"good\"}");
    var broken = root.resolve("broken.flow-node.json");
    Files.writeString(broken, "{");
    try (var classLoader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null)) {
      var provider = new JarLogicFlowNodeDefineProvider(classLoader);
      assertEquals("good", provider.getNodeDefines().getFirst().getType());

      Files.writeString(broken, "{\"type\":\"recovered\"}");

      assertEquals(2, provider.getNodeDefines().size());
    }
  }

  @Test
  void shouldLoadOfficialNodeDefinesFromClasspathMetadata() {
    var nodeTypes = new JarLogicFlowNodeDefineProvider().getNodeDefines().stream()
        .map(nodeDefine -> nodeDefine.getType())
        .collect(Collectors.toSet());

    assertEquals(17, nodeTypes.size());
    assertTrue(nodeTypes.contains("return"));
    assertTrue(nodeTypes.contains("script"));
  }
}

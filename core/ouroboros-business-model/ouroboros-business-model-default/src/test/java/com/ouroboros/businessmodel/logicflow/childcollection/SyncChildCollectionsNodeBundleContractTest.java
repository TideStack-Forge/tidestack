package com.ouroboros.businessmodel.logicflow.childcollection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.util.Json;

class SyncChildCollectionsNodeBundleContractTest {

  @Test
  void shouldDiscoverSyncChildCollectionsBuilderFromSpi() {
    List<String> discoveredBuilders = new ArrayList<String>();
    for (NodeBuilder<?> builder : ServiceLoader.load(NodeBuilder.class)) {
      discoveredBuilders.add(builder.getClass().getName());
      if (builder instanceof SyncChildCollectionsNode.Builder) {
        return;
      }
    }

    fail("Builder " + SyncChildCollectionsNode.Builder.class.getName()
        + " not found. Discovered: " + discoveredBuilders);
  }

  @Test
  void shouldRegisterSyncChildCollectionsBuilderInDescriptor() throws Exception {
    List<String> builders = readProviderEntries(readDescriptorLines(
        "META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder"));

    assertTrue(builders.contains(
        "com.ouroboros.businessmodel.logicflow.childcollection.SyncChildCollectionsNode$Builder"));
  }

  @Test
  void shouldPairSyncChildCollectionsBuilderWithNodeDefineResource() throws Exception {
    Map<String, Object> nodeDefine = loadNodeDefine(
        "META-INF/ouroboros/metadata/SyncChildCollectionsNode.flow-node.json");

    assertNotNull(nodeDefine);
    assertEquals(SyncChildCollectionsNode.TYPE, nodeDefine.get("type"));

    NodeBuilder<?> builder = new SyncChildCollectionsNode.Builder();
    assertTrue(builder.support(FlowNodeMeta.builder().type((String) nodeDefine.get("type")).build()));
  }

  private Map<String, Object> loadNodeDefine(String resourcePath) throws Exception {
    try (InputStream inputStream = SyncChildCollectionsNodeBundleContractTest.class.getClassLoader()
        .getResourceAsStream(resourcePath)) {
      assertNotNull(inputStream);
      var content = readAll(inputStream);
      return Json.toMap(content);
    }
  }

  private String readAll(InputStream inputStream) throws Exception {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      StringBuilder builder = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        builder.append(line);
      }
      return builder.toString();
    }
  }

  private List<String> readDescriptorLines(String resourcePath) throws Exception {
    Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(resourcePath);
    List<String> lines = new ArrayList<String>();

    while (resources.hasMoreElements()) {
      URL resource = resources.nextElement();
      try (InputStream inputStream = resource.openStream();
           BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
        }
      }
    }

    assertTrue(!lines.isEmpty(), () -> "Missing SPI descriptor: " + resourcePath);
    return lines;
  }

  private List<String> readProviderEntries(List<String> descriptorLines) {
    List<String> providers = new ArrayList<String>();
    for (String line : descriptorLines) {
      String provider = line.trim();
      if (!provider.isEmpty() && !provider.startsWith("#")) {
        providers.add(provider);
      }
    }
    return providers;
  }
}

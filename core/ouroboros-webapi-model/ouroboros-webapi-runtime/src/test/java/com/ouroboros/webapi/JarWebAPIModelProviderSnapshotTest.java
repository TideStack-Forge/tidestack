package com.ouroboros.webapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class JarWebAPIModelProviderSnapshotTest {
  @Test
  void partialDefinitionsAreUsableAndSameProviderRetriesAfterFailure() {
    var broken = new AtomicReference<>("{");
    var provider = new JarWebAPIModelProvider(spec -> List.of(
        new com.ouroboros.metadata.resource.MetadataResource("good.webapi.json", "{\"method\":\"GET\",\"path\":\"/good\"}"),
        new com.ouroboros.metadata.resource.MetadataResource("broken.good.webapi.json", broken.get())));
    assertEquals(1, provider.getWebAPIModelMap().size());
    assertTrue(provider.getWebAPIModelMap().containsKey("GET /good"));
    broken.set("{\"method\":\"GET\",\"path\":\"/recovered\"}");
    assertEquals(2, provider.getWebAPIModelMap().size());
  }

  @Test
  void sourceFailureIsNonblockingAndSameProviderCanRecover() {
    var broken = new AtomicBoolean(true);
    var provider = new JarWebAPIModelProvider(spec -> {
      if (broken.get()) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new com.ouroboros.metadata.resource.MetadataResource("good.webapi.json", "{\"method\":\"GET\",\"path\":\"/good\"}"));
    });
    assertTrue(provider.getWebAPIModelMap().isEmpty());
    broken.set(false);
    assertEquals(1, provider.getWebAPIModelMap().size());
  }

  @Test
  void recreatedProviderKeepsIndependentDefinitionsAfterResourceRemoval() throws Exception {
    var file = Path.of(getClass().getResource("/META-INF/ouroboros/metadata/jar-orders.webapi.json").toURI());
    var content = Files.readString(file);
    var first = new JarWebAPIModelProvider().getWebAPIModelMap().get("GET /jar/orders");
    try {
      Files.delete(file);

      var second = new JarWebAPIModelProvider().getWebAPIModelMap().get("GET /jar/orders");

      assertEquals("Jar Orders", second.getSummary());
      assertNotSame(first, second);
    } finally {
      Files.writeString(file, content);
    }
  }
}

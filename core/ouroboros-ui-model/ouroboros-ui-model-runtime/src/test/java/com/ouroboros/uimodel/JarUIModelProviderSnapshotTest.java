package com.ouroboros.uimodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class JarUIModelProviderSnapshotTest {
  @Test
  void partialDefinitionsAreUsableAndSameProviderRetriesAfterFailure() {
    var broken = new AtomicReference<>("{");
    var provider = new JarUIModelProvider(spec -> List.of(
        new com.ouroboros.metadata.resource.MetadataResource("good.ui-model.json", "{\"path\":\"/good\"}"),
        new com.ouroboros.metadata.resource.MetadataResource("broken.good.ui-model.json", broken.get())));
    assertEquals(1, provider.getUIModelMap().size());
    assertTrue(provider.getUIModel("/good").isPresent());
    broken.set("{\"path\":\"/recovered\"}");
    assertEquals(2, provider.getUIModelMap().size());
  }

  @Test
  void sourceFailureIsNonblockingAndSameProviderCanRecover() {
    var broken = new AtomicBoolean(true);
    var provider = new JarUIModelProvider(spec -> {
      if (broken.get()) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new com.ouroboros.metadata.resource.MetadataResource("good.ui-model.json", "{\"path\":\"/good\"}"));
    });
    assertTrue(provider.getUIModelMap().isEmpty());
    broken.set(false);
    assertEquals(1, provider.getUIModelMap().size());
  }

  @Test
  void recreatedProviderKeepsDefinitionsAfterResourceRemovalAndCallerMutation() throws Exception {
    var file = Path.of(getClass().getResource("/META-INF/ouroboros/metadata/snapshot.ui-model.json").toURI());
    var content = Files.readString(file);
    var first = new JarUIModelProvider().getUIModel("/snapshot").orElseThrow();
    ((SimpleUIModel) first).setTitle("changed");
    try {
      Files.delete(file);

      var second = new JarUIModelProvider().getUIModel("/snapshot").orElseThrow();

      assertEquals("Snapshot UI", second.getTitle());
    } finally {
      Files.writeString(file, content);
    }
  }
}

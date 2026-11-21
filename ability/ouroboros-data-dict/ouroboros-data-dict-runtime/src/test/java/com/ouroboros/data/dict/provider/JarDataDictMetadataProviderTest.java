package com.ouroboros.data.dict.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ouroboros.metadata.resource.MetadataResource;

class JarDataDictMetadataProviderTest {
  private static final String CONTENT = """
      {"name":"test","type":"STATIC","treeMode":false,"editable":false,
       "items":[{"value":"original","label":"Original"}]}
      """;

  @Test
  void snapshotSurvivesReadFailureAndCallerMutation() {
    var reads = new AtomicInteger();
    var provider = new JarDataDictMetadataProvider(spec -> {
      if (reads.incrementAndGet() > 1) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("test.dict.json", CONTENT));
    });
    var first = provider.getDataDicts().get("test");
    first.setItems(List.of());

    assertEquals(1, provider.getDataDicts().get("test").getItems().size());
    assertEquals(1, reads.get());
  }

  @Test
  void invalidSnapshotIsSkippedAndRetried() {
    var reads = new AtomicInteger();
    var provider = new JarDataDictMetadataProvider(spec -> List.of(new MetadataResource("test.dict.json",
        reads.incrementAndGet() == 1 ? "{" : CONTENT)));

    assertTrue(provider.getDataDicts().isEmpty());
    assertEquals(1, provider.getDataDicts().get("test").getItems().size());
    assertEquals(2, reads.get());
  }
  @Test
  void sourceFailureIsNonblockingAndDoesNotCacheAnEmptyResult() {
    var reads = new AtomicInteger();
    var provider = new JarDataDictMetadataProvider(spec -> {
      if (reads.incrementAndGet() == 1) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("test.json", CONTENT));
    });

    assertTrue(provider.getDataDicts().isEmpty());
    assertEquals(1, provider.getDataDicts().size());
    provider.getDataDicts();
    assertEquals(2, reads.get());
  }

}

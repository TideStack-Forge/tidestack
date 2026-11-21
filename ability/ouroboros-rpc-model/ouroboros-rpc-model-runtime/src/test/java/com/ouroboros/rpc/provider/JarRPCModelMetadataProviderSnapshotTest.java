package com.ouroboros.rpc.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ouroboros.metadata.resource.MetadataResource;

class JarRPCModelMetadataProviderSnapshotTest {
  private static final String CONTENT = """
      {"name":"original","title":"Original"}
      """;

  @Test
  void successfulSnapshotSurvivesReadFailureAndCallerMutation() {
    var reads = new AtomicInteger();
    var provider = new JarRPCModelMetadataProvider(spec -> {
      if (reads.incrementAndGet() > 1) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("test.json", CONTENT));
    });
    var first = provider.getAllRpcModelMetadata();
    first.getFirst().setTitle("changed");
    first.clear();

    assertEquals("Original", provider.getAllRpcModelMetadata().getFirst().getTitle());
    assertEquals(1, reads.get());
  }

  @Test
  void invalidSnapshotIsSkippedAndRetried() {
    var reads = new AtomicInteger();
    var provider = new JarRPCModelMetadataProvider(spec -> List.of(new MetadataResource("test.json",
        reads.incrementAndGet() == 1 ? "{" : CONTENT)));

    assertTrue(provider.getAllRpcModelMetadata().isEmpty());
    assertEquals("Original", provider.getAllRpcModelMetadata().getFirst().getTitle());
    assertEquals(2, reads.get());
  }
  @Test
  void sourceFailureIsNonblockingAndDoesNotCacheAnEmptyResult() {
    var reads = new AtomicInteger();
    var provider = new JarRPCModelMetadataProvider(spec -> {
      if (reads.incrementAndGet() == 1) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("test.json", CONTENT));
    });

    assertTrue(provider.getAllRpcModelMetadata().isEmpty());
    assertEquals(1, provider.getAllRpcModelMetadata().size());
    provider.getAllRpcModelMetadata();
    assertEquals(2, reads.get());
  }

}

package com.ouroboros.rpc.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ouroboros.metadata.resource.MetadataResource;

class JarRPCAuthCredentialMetadataProviderSnapshotTest {
  private static final String CONTENT = """
      {"name":"original","title":"Original"}
      """;

  @Test
  void successfulSnapshotSurvivesReadFailureAndCallerMutation() {
    var reads = new AtomicInteger();
    var provider = new JarRPCAuthCredentialMetadataProvider(spec -> {
      if (reads.incrementAndGet() > 1) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("test.json", CONTENT));
    });
    var first = provider.getAllRpcAuthCredentialMetadata();
    first.getFirst().setTitle("changed");
    first.clear();

    assertEquals("Original", provider.getAllRpcAuthCredentialMetadata().getFirst().getTitle());
    assertEquals(1, reads.get());
  }

  @Test
  void invalidSnapshotIsSkippedAndRetried() {
    var reads = new AtomicInteger();
    var provider = new JarRPCAuthCredentialMetadataProvider(spec -> List.of(new MetadataResource("test.json",
        reads.incrementAndGet() == 1 ? "{" : CONTENT)));

    assertTrue(provider.getAllRpcAuthCredentialMetadata().isEmpty());
    assertEquals("Original", provider.getAllRpcAuthCredentialMetadata().getFirst().getTitle());
    assertEquals(2, reads.get());
  }
  @Test
  void sourceFailureIsNonblockingAndDoesNotCacheAnEmptyResult() {
    var reads = new AtomicInteger();
    var provider = new JarRPCAuthCredentialMetadataProvider(spec -> {
      if (reads.incrementAndGet() == 1) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("test.json", CONTENT));
    });

    assertTrue(provider.getAllRpcAuthCredentialMetadata().isEmpty());
    assertEquals(1, provider.getAllRpcAuthCredentialMetadata().size());
    provider.getAllRpcAuthCredentialMetadata();
    assertEquals(2, reads.get());
  }

}

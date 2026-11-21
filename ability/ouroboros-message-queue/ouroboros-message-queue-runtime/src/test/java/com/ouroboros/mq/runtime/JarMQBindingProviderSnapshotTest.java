package com.ouroboros.mq.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ouroboros.metadata.resource.MetadataResource;

class JarMQBindingProviderSnapshotTest {
  private static final String CONTENT = """
      {"bindingType":"producer","bindingName":"original","destination":"orders"}
      """;

  @Test
  void successfulSnapshotSurvivesReadFailureAndCallerMutation() {
    var reads = new AtomicInteger();
    var provider = new JarMQBindingProvider(spec -> {
      if (reads.incrementAndGet() > 1) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("test.json", CONTENT));
    });
    var first = provider.getAllBindings();
    first.getFirst().setDestination("changed");
    first.clear();

    assertEquals("orders", provider.getAllBindings().getFirst().getDestination());
    assertEquals(1, reads.get());
  }

  @Test
  void invalidSnapshotIsSkippedAndRetried() {
    var reads = new AtomicInteger();
    var provider = new JarMQBindingProvider(spec -> List.of(new MetadataResource("test.json",
        reads.incrementAndGet() == 1 ? "{" : CONTENT)));

    assertTrue(provider.getAllBindings().isEmpty());
    assertEquals("orders", provider.getAllBindings().getFirst().getDestination());
    assertEquals(2, reads.get());
  }
  @Test
  void sourceFailureIsNonblockingAndDoesNotCacheAnEmptyResult() {
    var reads = new AtomicInteger();
    var provider = new JarMQBindingProvider(spec -> {
      if (reads.incrementAndGet() == 1) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("test.json", CONTENT));
    });

    assertTrue(provider.getAllBindings().isEmpty());
    assertEquals(1, provider.getAllBindings().size());
    provider.getAllBindings();
    assertEquals(2, reads.get());
  }

}

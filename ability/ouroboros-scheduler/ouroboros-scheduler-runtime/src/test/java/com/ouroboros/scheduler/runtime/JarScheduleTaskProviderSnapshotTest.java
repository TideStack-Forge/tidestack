package com.ouroboros.scheduler.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ouroboros.metadata.resource.MetadataResource;

class JarScheduleTaskProviderSnapshotTest {
  private static final String CONTENT = """
      {"name":"original","cron":"0 0 2 * * ?","flowName":"daily.sync"}
      """;

  @Test
  void successfulSnapshotSurvivesReadFailureAndCallerMutation() {
    var reads = new AtomicInteger();
    var provider = new JarScheduleTaskProvider(spec -> {
      if (reads.incrementAndGet() > 1) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("test.json", CONTENT));
    });
    var first = provider.getAllScheduleTask();
    ((com.ouroboros.scheduler.SimpleScheduleTask) first.getFirst()).setFlowName("changed");
    first.clear();

    assertEquals("daily.sync", provider.getAllScheduleTask().getFirst().getFlowName());
    assertEquals(1, reads.get());
  }

  @Test
  void invalidSnapshotIsSkippedAndRetried() {
    var reads = new AtomicInteger();
    var provider = new JarScheduleTaskProvider(spec -> List.of(new MetadataResource("test.json",
        reads.incrementAndGet() == 1 ? "{" : CONTENT)));

    assertTrue(provider.getAllScheduleTask().isEmpty());
    assertEquals("daily.sync", provider.getAllScheduleTask().getFirst().getFlowName());
    assertEquals(2, reads.get());
  }
  @Test
  void sourceFailureIsNonblockingAndDoesNotCacheAnEmptyResult() {
    var reads = new AtomicInteger();
    var provider = new JarScheduleTaskProvider(spec -> {
      if (reads.incrementAndGet() == 1) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("test.json", CONTENT));
    });

    assertTrue(provider.getAllScheduleTask().isEmpty());
    assertEquals(1, provider.getAllScheduleTask().size());
    provider.getAllScheduleTask();
    assertEquals(2, reads.get());
  }

}

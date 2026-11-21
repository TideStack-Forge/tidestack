package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.catalog.InMemoryProjectTemplateCatalogReplayStateStore;

class ProjectTemplateCatalogMultiInstanceReplayTest {

  @Test
  void shouldShareHighWaterMarkAcrossStoreInstancesBackedBySameStateMap() {
    var sharedState = new ConcurrentHashMap<String, com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogReplayState>();
    var nodeA = new InMemoryProjectTemplateCatalogReplayStateStore(sharedState);
    var nodeB = new InMemoryProjectTemplateCatalogReplayStateStore(sharedState);

    nodeA.accept("default", 20, "a".repeat(64));

    assertEquals(20, nodeB.current("default").orElseThrow().sequence());
    assertThrows(IllegalArgumentException.class, () -> nodeB.accept("default", 19, "b".repeat(64)));
  }
}

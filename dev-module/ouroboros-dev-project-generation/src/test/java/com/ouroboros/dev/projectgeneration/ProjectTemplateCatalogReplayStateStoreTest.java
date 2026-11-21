package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.catalog.InMemoryProjectTemplateCatalogReplayStateStore;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogReplayState;

class ProjectTemplateCatalogReplayStateStoreTest {

  @Test
  void shouldAcceptStrictlyNewerSequenceAndIdempotentSameDigest() {
    var store = new InMemoryProjectTemplateCatalogReplayStateStore();

    ProjectTemplateCatalogReplayState first = store.accept("default", 10, "a".repeat(64));
    ProjectTemplateCatalogReplayState same = store.accept("default", 10, "a".repeat(64));
    ProjectTemplateCatalogReplayState newer = store.accept("default", 11, "b".repeat(64));

    assertEquals(first, same);
    assertEquals(11, newer.sequence());
    assertEquals("b".repeat(64), newer.digest());
  }

  @Test
  void shouldRejectOldSequenceAndSameSequenceDifferentDigest() {
    var store = new InMemoryProjectTemplateCatalogReplayStateStore();
    store.accept("default", 10, "a".repeat(64));

    assertThrows(IllegalArgumentException.class, () -> store.accept("default", 9, "b".repeat(64)));
    assertThrows(IllegalArgumentException.class, () -> store.accept("default", 10, "b".repeat(64)));
  }
}

package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.catalog.LastKnownGoodCatalog;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogReplayState;

class ProjectTemplateCatalogLkgTest {

  @Test
  void shouldUseOnlyUnexpiredLkgMatchingSharedReplayPair() {
    Instant now = Instant.parse("2026-07-30T00:00:00Z");
    var replayState = new ProjectTemplateCatalogReplayState(42, "a".repeat(64), 1);
    var lkg = new LastKnownGoodCatalog(
        CatalogTestSupport.utf8("{}"),
        CatalogTestSupport.utf8("{}"),
        now.minusSeconds(60),
        now.plusSeconds(3600),
        42,
        "a".repeat(64),
        "catalog-key-1"
    );

    assertTrue(lkg.isUsable(replayState, now));
    assertFalse(lkg.isUsable(new ProjectTemplateCatalogReplayState(41, "a".repeat(64), 1), now));
    assertFalse(lkg.isUsable(new ProjectTemplateCatalogReplayState(42, "b".repeat(64), 1), now));
    assertFalse(lkg.isUsable(replayState, now.plusSeconds(7200)));
  }
}

package com.ouroboros.devhelper.metadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperRetirementFence;

class MetadataRefreshProtocolTest {

  @Test
  void retirementFenceShouldKeepOnlyTheLatestStartWatermarkPerIncarnation() {
    HelperRetirementFence fence = HelperRetirementFence.empty();
    for (long startedAt = 1L; startedAt <= 1000L; startedAt++) {
      fence = fence.retire(new HelperIdentity("helper-" + startedAt, startedAt, "incarnation-a"));
    }

    assertTrue(fence.rejects(new HelperIdentity("first", 1L, "incarnation-a")));
    assertTrue(fence.rejects(new HelperIdentity("latest", 1000L, "incarnation-a")));
    assertFalse(fence.rejects(new HelperIdentity("newer", 1001L, "incarnation-a")));

    fence = fence.retire(new HelperIdentity("replacement", 5L, "incarnation-b"));
    assertFalse(fence.rejects(new HelperIdentity("old-incarnation", 1000L, "incarnation-a")));
    assertTrue(fence.rejects(new HelperIdentity("replacement", 5L, "incarnation-b")));
    assertFalse(fence.rejects(new HelperIdentity("newer-replacement", 6L, "incarnation-b")));
  }
}

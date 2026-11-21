package com.ouroboros.devhelper.util;

import java.util.UUID;

import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;

public final class HelperRuntimeIdentity {
  private static final HelperIdentity CURRENT = new HelperIdentity(
      UUID.randomUUID().toString(),
      System.currentTimeMillis(),
      appIncarnationId()
  );

  private HelperRuntimeIdentity() {
  }

  public static HelperIdentity current() {
    return CURRENT;
  }

  private static String appIncarnationId() {
    String configured = System.getenv(com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.APP_INCARNATION_ID_ENV);
    return configured == null || configured.trim().isEmpty()
        ? "unmanaged-" + UUID.randomUUID()
        : configured.trim();
  }
}

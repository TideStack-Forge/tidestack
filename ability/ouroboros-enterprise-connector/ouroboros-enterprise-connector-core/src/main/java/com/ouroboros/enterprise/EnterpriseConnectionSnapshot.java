package com.ouroboros.enterprise;

import java.util.Map;

/** Published connection facts. Credentials and access tokens never enter this value. */
public record EnterpriseConnectionSnapshot(String provider, String connectionId, String revision,
    String fingerprint, String credentialRef, Map<String, Object> providerConfig, boolean enabled,
    boolean authEnabled) {
  public EnterpriseConnectionSnapshot { providerConfig = Map.copyOf(providerConfig); }
}

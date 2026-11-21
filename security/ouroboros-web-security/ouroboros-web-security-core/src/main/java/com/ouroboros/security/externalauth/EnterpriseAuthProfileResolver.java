package com.ouroboros.security.externalauth;

import java.util.Optional;

import com.ouroboros.security.externalauth.config.ProviderSpec;

/**
 * Resolves a connection-scoped external authentication profile.
 *
 * <p>The security module owns the authentication contract, while the
 * enterprise connector owns the source of the connection snapshot. Returning
 * an empty value is fail-closed: a workflow connection without an explicitly
 * published authentication profile cannot be used as a login provider.</p>
 */
public interface EnterpriseAuthProfileResolver {

  Resolution resolve(String connectionId);

  /** Connection candidates; callers still resolve each current profile before advertising it. */
  default java.util.List<String> connectionIds() { return java.util.List.of(); }

  /** Recheck a connection-scoped profile and admit each actual protocol request. */
  default void beforeRequest(String connectionId, String expectedRevision) {
    throw new com.ouroboros.exception.BusinessException("企业认证连接未提供请求准入能力");
  }

  record Resolution(boolean connectionClaimed, Optional<ProviderSpec> profile) {

    public Resolution {
      profile = profile == null ? Optional.empty() : profile;
      if (!connectionClaimed && profile.isPresent()) {
        throw new IllegalArgumentException("An unclaimed connection cannot provide an auth profile");
      }
    }

    public static Resolution notClaimed() {
      return new Resolution(false, Optional.empty());
    }

    public static Resolution claimedWithoutProfile() {
      return new Resolution(true, Optional.empty());
    }

    public static Resolution claimed(ProviderSpec profile) {
      return new Resolution(true, Optional.of(profile));
    }
  }
}

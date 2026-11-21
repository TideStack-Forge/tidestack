package com.ouroboros.security.profile;

/** Rules are supplied by the existing configuration runtime, not by account persistence. */
public interface UserProfilePolicySource {
  Snapshot current();
  record Snapshot(String revision, UserProfilePolicy policy) { }
}

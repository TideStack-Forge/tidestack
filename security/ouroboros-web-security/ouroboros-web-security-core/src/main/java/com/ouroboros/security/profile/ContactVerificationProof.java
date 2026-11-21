package com.ouroboros.security.profile;

/** Deployment-keyed proof hashing, also required for low-entropy phone codes. */
public interface ContactVerificationProof {
  boolean ready();
  String digest(String requestId, String proof);
}

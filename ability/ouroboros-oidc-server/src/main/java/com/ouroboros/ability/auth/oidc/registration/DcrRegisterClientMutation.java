package com.ouroboros.ability.auth.oidc.registration;

import com.ouroboros.configuration.management.ConfigurationMutation;

/** Frozen persistent candidate, without plaintext credentials. Only the owning policy can qualify it. */
public record DcrRegisterClientMutation(String candidate, String policyRevision, String requestId,
    DcrCredentials.Claim claim) implements ConfigurationMutation {
  @Override public String toString() { return "DcrRegisterClientMutation[REDACTED]"; }
}

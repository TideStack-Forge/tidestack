package com.ouroboros.ability.auth.oidc;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Deployment identity installs the protocol; unified configuration controls issuance at runtime. */
public final class OidcDeploymentCondition extends SpringBootCondition {
  @Override
  public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
    var environment = context.getEnvironment();
    String issuer = environment.getProperty("ouroboros.security.oidc.issuer");
    String keys = environment.getProperty("ouroboros.security.oidc.jwk-set-ref");
    return issuer != null && !issuer.isBlank() && keys != null && !keys.isBlank()
        ? ConditionOutcome.match("OIDC deployment identity provided")
        : ConditionOutcome.noMatch("OIDC deployment identity is not configured");
  }
}

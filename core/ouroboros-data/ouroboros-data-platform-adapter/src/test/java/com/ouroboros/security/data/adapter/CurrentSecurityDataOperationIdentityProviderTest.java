package com.ouroboros.security.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.DataOperationIdentityProviders;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.identity.SystemSecurityIdentities;

class CurrentSecurityDataOperationIdentityProviderTest {

  @AfterEach
  void cleanup() {
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void findCurrentOperator_returnsEmptyWhenCurrentSecurityIdentityMissing() {
    CurrentSecurityContextHolder.clearDelegate();

    Optional<String> operator = new CurrentSecurityDataOperationIdentityProvider().findCurrentOperator();

    assertFalse(operator.isPresent());
  }

  @Test
  void findCurrentOperator_returnsCurrentSecurityUserIdentity() {
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(() -> "seam-operator")));

    Optional<String> operator = new CurrentSecurityDataOperationIdentityProvider().findCurrentOperator();

    assertEquals("seam-operator", operator.get());
  }

  @Test
  void dataOperationIdentityProviderServiceLoadsRuntimeSecurityAdapter() {
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(() -> "service-operator")));

    Optional<String> operator = DataOperationIdentityProviders.findCurrentOperator();

    assertEquals("service-operator", operator.get());
  }

  @Test
  void dataOperationIdentityProviderReadsScopedSystemIdentity() {
    Optional<String> operator = CurrentSecurityContextHolder.withIdentity(
        SystemSecurityIdentities.system(),
        DataOperationIdentityProviders::findCurrentOperator);

    assertEquals(SystemSecurityIdentities.SYSTEM_USER_ID, operator.get());
    assertFalse(CurrentSecurityContextHolder.findIdentity().isPresent());
  }
}

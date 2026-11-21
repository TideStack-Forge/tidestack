package com.ouroboros.security.context;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.identity.SystemSecurityIdentities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentSecurityContextHolderScopedContextTest {

  @AfterEach
  void resetContextHolder() {
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void withIdentityExposesIdentityOnlyInsideScope() {
    String value = CurrentSecurityContextHolder.withIdentity(SystemSecurityIdentities.system(), () -> {
      assertEquals(SystemSecurityIdentities.SYSTEM_USER_ID,
          CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());
      return "ok";
    });

    assertEquals("ok", value);
    assertFalse(CurrentSecurityContextHolder.findIdentity().isPresent());
  }

  @Test
  void withIdentityRestoresPreviousScopedIdentity() {
    var outerIdentity = new DefaultSecurityIdentity(() -> "outer");
    var innerIdentity = new DefaultSecurityIdentity(() -> "inner");

    String value = CurrentSecurityContextHolder.withIdentity(outerIdentity, () -> {
      String innerValue = CurrentSecurityContextHolder.withIdentity(innerIdentity,
          () -> CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());
      assertEquals("inner", innerValue);
      return CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity();
    });

    assertEquals("outer", value);
    assertFalse(CurrentSecurityContextHolder.findIdentity().isPresent());
  }

  @Test
  void withIdentityRestoresDelegateAfterFailure() {
    CurrentSecurityContextHolder.setDelegate(
        () -> Optional.of(new DefaultSecurityIdentity(() -> "delegate")));

    assertThrows(IllegalStateException.class,
        () -> CurrentSecurityContextHolder.withIdentity(SystemSecurityIdentities.system(), () -> {
          assertEquals(SystemSecurityIdentities.SYSTEM_USER_ID,
              CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());
          throw new IllegalStateException("boom");
        }));

    assertEquals("delegate", CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());
  }

  @Test
  void withIdentityThrowingRestoresContextAndPropagatesCheckedException() {
    var outerIdentity = new DefaultSecurityIdentity(() -> "outer");

    IOException thrown = assertThrows(IOException.class,
        () -> CurrentSecurityContextHolder.withIdentityThrowing(outerIdentity, () -> {
          assertEquals("outer", CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());
          throw new IOException("boom");
        }));

    assertEquals("boom", thrown.getMessage());
    assertFalse(CurrentSecurityContextHolder.findIdentity().isPresent());
  }

  @Test
  void withContextFallsBackToDelegateOutsideScope() {
    CurrentSecurityContextHolder.setDelegate(
        () -> Optional.of(new DefaultSecurityIdentity(() -> "delegate")));

    String scoped = CurrentSecurityContextHolder.withContext(
        () -> Optional.of(new DefaultSecurityIdentity(() -> "scoped")),
        () -> CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());

    assertEquals("scoped", scoped);
    assertEquals("delegate", CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());
    assertTrue(CurrentSecurityContextHolder.findIdentity().isPresent());
  }

  @Test
  void clientTypeFollowsScopedContextAndRestoresRequestDelegateAfterFailure() {
    assertEquals("UNKNOWN", CurrentSecurityContextHolder.getClientType());
    CurrentSecurityContextHolder.setDelegate(clientContext("WEB"));
    CurrentSecurityContextHolder.withIdentity(SystemSecurityIdentities.system(), () -> {
      assertEquals("WEB", CurrentSecurityContextHolder.getClientType());
      assertThrows(IllegalStateException.class, () -> CurrentSecurityContextHolder.withContext(clientContext("MOBILE"), () -> {
        assertEquals("MOBILE", CurrentSecurityContextHolder.getClientType());
        throw new IllegalStateException("request failed");
      }));
      assertEquals("WEB", CurrentSecurityContextHolder.getClientType());
      return null;
    });
    assertEquals("WEB", CurrentSecurityContextHolder.getClientType());
  }

  private static CurrentSecurityContext clientContext(String clientType) {
    return new CurrentSecurityContext() {
      public Optional<com.ouroboros.security.identity.SecurityIdentity> findIdentity() { return Optional.empty(); }
      public Optional<String> findClientType() { return Optional.of(clientType); }
    };
  }
}

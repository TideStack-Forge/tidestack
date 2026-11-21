package com.ouroboros.security.rbac;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserIdResolverImplTest {

  @Test
  void shouldReturnPrincipalAsUserId() {
    var resolver = new UserIdResolverImpl();

    var userId = resolver.getUserId("email", "principal-1");
    assertTrue(userId.isPresent());
    assertEquals("principal-1", userId.get());
  }
}

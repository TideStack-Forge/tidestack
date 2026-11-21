package com.ouroboros.businessmodel.event;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessEventConsumerIdentityResolverTest {
  @Test
  void missingAssignmentNeverUsesTheSourceActorOrAnAdministratorFallback() {
    var users = mock(UserCenter.class);
    var resolver = new BusinessEventConsumerIdentityResolver(new BusinessEventConsumerProperties(), users);
    assertTrue(resolver.resolve("inventory.Stock").isEmpty());
    verifyNoInteractions(users);
  }

  @Test
  void assignmentIsModelScopedAndRechecksDisabledOrRemovedAccounts() {
    var properties = new BusinessEventConsumerProperties();
    properties.setPrincipals(Map.of("inventory.Stock", "stock-service"));
    var users = mock(UserCenter.class);
    var user = mock(User.class);
    when(user.isEnabled()).thenReturn(true);
    when(users.getUser("stock-service")).thenReturn(Optional.of(user));
    var resolver = new BusinessEventConsumerIdentityResolver(properties, users);
    assertSame(user, resolver.resolve("INVENTORY.stock").orElseThrow());
    assertTrue(resolver.resolve("sales.Order").isEmpty());
    when(user.isEnabled()).thenReturn(false);
    assertTrue(resolver.resolve("inventory.Stock").isEmpty());
    when(users.getUser("stock-service")).thenReturn(Optional.empty());
    assertTrue(resolver.resolve("inventory.Stock").isEmpty());
    properties.setPrincipals(Map.of());
    clearInvocations(users);
    assertTrue(resolver.resolve("inventory.Stock").isEmpty());
    verifyNoInteractions(users);
  }
}

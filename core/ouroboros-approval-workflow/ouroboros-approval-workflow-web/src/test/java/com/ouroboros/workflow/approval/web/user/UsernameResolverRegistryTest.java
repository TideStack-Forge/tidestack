package com.ouroboros.workflow.approval.web.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class UsernameResolverRegistryTest {

  @Test
  void resolveListShouldPreserveEarlierResolverPrecedence() {
    UsernameResolverRegistry registry = new UsernameResolverRegistry(
        java.util.Arrays.asList(
            new HighPriorityUsernameResolver(),
            new LowPriorityUsernameResolver()));

    assertEquals("高优先级", registry.resolve("employee-1").orElse(null));
    assertEquals("高优先级", registry.resolve(Collections.singletonList("employee-1")).get("employee-1"));
  }

  @Test
  void resolveShouldIgnoreSpiResolversAndUseBeanResolversOnly() {
    UsernameResolverRegistry registry = new UsernameResolverRegistry(Collections.emptyList());

    assertTrue(!registry.resolve("employee-from-spi").isPresent());
    assertTrue(registry.resolve(Collections.singletonList("employee-from-spi")).isEmpty());
  }

  private abstract static class FixedUsernameResolver implements UsernameResolver {
    private final String displayName;

    private FixedUsernameResolver(String displayName) {
      this.displayName = displayName;
    }

    @Override
    public Optional<String> resolve(String user) {
      return "employee-1".equals(user) ? Optional.of(displayName) : Optional.empty();
    }

    @Override
    public Map<String, String> resolve(java.util.List<String> users) {
      return users.contains("employee-1")
          ? Collections.singletonMap("employee-1", displayName)
          : Collections.emptyMap();
    }
  }

  private static final class HighPriorityUsernameResolver extends FixedUsernameResolver {
    private HighPriorityUsernameResolver() {
      super("高优先级");
    }
  }

  private static final class LowPriorityUsernameResolver extends FixedUsernameResolver {
    private LowPriorityUsernameResolver() {
      super("低优先级");
    }
  }
}

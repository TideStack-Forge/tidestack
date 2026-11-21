package com.ouroboros.security.claims;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.Priority;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ClaimsAggregatorBehaviorTest {

  @Test
  void aggregateSortsProvidersAndMergesClaimsWhileToleratingFailures() {
    var lowPriority = new BaseProvider("low", 10) {
      @Override
      public Claims provide(User user, ClaimsContext context) {
        try {
          user.getClaims();
          fail("ProtectedUser should block getClaims during aggregation");
        } catch (UnsupportedOperationException expected) {
          // expected
        }
        return Claims.builder().claim("name", "alice").claim("number", 100).build();
      }
    };

    var highPriority = new BaseProvider("high", 20) {
      @Override
      public Claims provide(User user, ClaimsContext context) {
        assertTrue(context.hasClaim("name"));
        assertEquals("alice", context.getString("name").orElse(null));
        return Claims.builder().claim("name", "alice-override").build();
      }
    };

    var broken = new BaseProvider("broken", 30) {
      @Override
      public Claims provide(User user, ClaimsContext context) {
        throw new IllegalStateException("expected");
      }
    };

    var nullProvider = new BaseProvider("nullProvider", 40) {
      @Override
      public Claims provide(User user, ClaimsContext context) {
        return null;
      }
    };

    var aggregator = new ClaimsAggregator(Arrays.asList(lowPriority, highPriority, broken, nullProvider));
    var claims = aggregator.aggregate(user("u1"));

    assertEquals("alice-override", claims.getString("name").orElse(null));
    assertEquals(Integer.valueOf(100), claims.getInteger("number").orElse(null));
    assertFalse(claims.getTypes().isEmpty());
  }

  private static User user(final String id) {
    return new User() {
      @Override
      public String getUserIdentity() {
        return id;
      }

      @Override
      public List<Role> getRoles() {
        return Collections.emptyList();
      }

      @Override
      public Subject getSubject() {
        return null;
      }
    };
  }

  @Priority(0)
  private abstract static class BaseProvider implements ClaimsProvider {
    private final String name;
    private final int p;

    BaseProvider(String name, int p) {
      this.name = name;
      this.p = p;
    }

    @Override
    public Set<String> getProvidedClaimTypes() {
      return new HashSet<String>(Collections.singletonList(name));
    }

    @Override
    public String getName() {
      return name + "-" + p;
    }
  }
}

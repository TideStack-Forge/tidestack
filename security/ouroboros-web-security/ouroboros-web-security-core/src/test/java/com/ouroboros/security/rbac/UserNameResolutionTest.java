package com.ouroboros.security.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.claims.Claims;

class UserNameResolutionTest {
  @Test
  void nicknameTakesPrecedenceOverTheFormalName() {
    assertEquals("小张", user("小张", "张三").getDisplayName());
  }

  @Test
  void missingNicknamePreservesTheRegisteredName() {
    assertEquals("张三", user(null, "张三").getDisplayName());
    assertEquals("张三", user("  ", "张三").getDisplayName());
  }

  @Test
  void anUnboundAccountCanStillUseItsNickname() {
    assertEquals("小张", user("小张", null).getDisplayName());
  }

  @Test
  void missingNamesFallBackToTheLoginIdentity() {
    assertEquals("alice", user(null, null).getDisplayName());
    assertEquals("alice", user(null, " ").getDisplayName());
  }

  private User user(String nickname, String formalName) {
    return new User() {
      public String getUserIdentity() { return "alice"; }

      public String getNickname() { return nickname; }

      public Subject getSubject() {
        if (formalName == null) { return null; }
        return new PersonSubject() {
          public String getId() { return "employee-1"; }
          public String getName() { return formalName; }
        };
      }

      public Claims getClaims() {
        throw new AssertionError("Resolving names must not invoke claims aggregation recursively");
      }
    };
  }
}

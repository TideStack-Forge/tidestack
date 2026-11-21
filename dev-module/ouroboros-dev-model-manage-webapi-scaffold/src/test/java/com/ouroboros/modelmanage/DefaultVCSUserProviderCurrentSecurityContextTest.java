package com.ouroboros.modelmanage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultVCSUserProviderCurrentSecurityContextTest {

  private final DefaultVCSUserProvider provider = new DefaultVCSUserProvider();

  @AfterEach
  void tearDown() {
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void returnsIdentityFromCurrentSecurityContext() {
    CurrentSecurityContextHolder.setDelegate(securityContext(identity("alice")));

    assertThat(provider.getCurrentUser()).isEqualTo("alice");
  }

  @Test
  void returnsNullWhenSecurityContextIsEmpty() {
    CurrentSecurityContextHolder.clearDelegate();

    assertThat(provider.getCurrentUser()).isNull();
  }

  private static CurrentSecurityContext securityContext(SecurityIdentity identity) {
    return () -> Optional.ofNullable(identity);
  }

  private static SecurityIdentity identity(String userIdentity) {
    return new SecurityIdentity() {

      private final User user = new User() {
        @Override
        public String getUserIdentity() {
          return userIdentity;
        }
      };

      @Override
      public String getPrincipal() {
        return userIdentity;
      }

      @Override
      public String getUserIdentity() {
        return userIdentity;
      }

      @Override
      public User getUserDetail() {
        return user;
      }

      @Override
      public Claims getClaims() {
        return Claims.empty();
      }

      @Override
      public Subject getSubject() {
        return null;
      }

      @Override
      public List<Role> getRoles() {
        return Collections.emptyList();
      }
    };
  }
}

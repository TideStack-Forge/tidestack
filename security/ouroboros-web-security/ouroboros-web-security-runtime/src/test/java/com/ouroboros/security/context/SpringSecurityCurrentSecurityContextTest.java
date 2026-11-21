package com.ouroboros.security.context;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.StandardClaims;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringSecurityCurrentSecurityContextTest {

  private final SpringSecurityCurrentSecurityContext context = new SpringSecurityCurrentSecurityContext();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void emptySecurityContextReturnsEmptyOptional() {
    assertFalse(context.findIdentity().isPresent());
  }

  @Test
  void ouroborosAuthenticationReturnsMappedIdentity() {
    Claims claims = Claims.builder()
        .claim(StandardClaims.EMAIL, "alice@ouroboros.test")
        .build();

    Subject subject = new Subject() {
      @Override
      public String getId() {
        return "subject-1";
      }

      @Override
      public String getName() {
        return "Alice";
      }

      @Override
      public String getType() {
        return "EMPLOYEE";
      }

      @Override
      public String getDisplayName() {
        return "Alice Zhang";
      }
    };

    Role role = new Role() {
      @Override
      public String getId() {
        return "role-admin";
      }

      @Override
      public String getName() {
        return "Admin";
      }

      @Override
      public java.util.List<com.ouroboros.security.authority.OuroborosGrantedAuthority> getAuthorities() {
        return Collections.emptyList();
      }
    };

    User user = new User() {
      @Override
      public String getUserIdentity() {
        return "alice";
      }

      @Override
      public Claims getClaims() {
        return claims;
      }

      @Override
      public Subject getSubject() {
        return subject;
      }

      @Override
      public java.util.List<Role> getRoles() {
        return Collections.singletonList(role);
      }
    };

    SecurityContextHolder.getContext().setAuthentication(
        OuroborosAuthenticationHolder.toSpringAuthentication(new TestingOuroborosAuthentication(user)));

    Optional<SecurityIdentity> identityOptional = context.findIdentity();
    assertTrue(identityOptional.isPresent());

    SecurityIdentity identity = identityOptional.get();
    assertEquals("alice", identity.getPrincipal());
    assertEquals("alice", identity.getUserIdentity());
    assertSame(user, identity.getUserDetail());
    assertSame(claims, identity.getClaims());
    assertSame(subject, identity.getSubject());
    assertEquals(1, identity.getRoles().size());
    assertSame(role, identity.getRoles().get(0));
  }

  @Test
  void nonOuroborosAuthenticationFallsBackToUserInDetails() {
    User detailUser = () -> "bob";
    TestingAuthenticationToken authentication = new TestingAuthenticationToken("ignored", null);
    authentication.setDetails(detailUser);
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    Optional<SecurityIdentity> identityOptional = context.findIdentity();
    assertTrue(identityOptional.isPresent());
    assertEquals("bob", identityOptional.get().getUserIdentity());
    assertSame(detailUser, identityOptional.get().getUserDetail());
  }

  @Test
  void anonymousAuthenticationStillProducesIdentityFromPrincipalName() {
    TestingAuthenticationToken authentication = new TestingAuthenticationToken("anonymousUser", null);
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    Optional<SecurityIdentity> identityOptional = context.findIdentity();
    assertTrue(identityOptional.isPresent());
    assertEquals("anonymousUser", identityOptional.get().getPrincipal());
    assertEquals("anonymousUser", identityOptional.get().getUserIdentity());
  }

  private static final class TestingOuroborosAuthentication implements OuroborosAuthentication {

    private final User user;
    private boolean authenticated = true;

    private TestingOuroborosAuthentication(User user) {
      this.user = user;
    }

    @Override
    public String getName() {
      return user.getUserIdentity();
    }

    @Override
    public Object getCredentials() {
      return null;
    }

    @Override
    public Object getDetails() {
      return null;
    }

    @Override
    public boolean isAuthenticated() {
      return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
      this.authenticated = isAuthenticated;
    }

    @Override
    public java.util.List<com.ouroboros.security.authority.OuroborosGrantedAuthority> getAuthorities() {
      return user.getRoles().stream()
          .map(Role::getAuthorities)
          .flatMap(java.util.Collection::stream)
          .map(com.ouroboros.security.authority.OuroborosGrantedAuthority.class::cast)
          .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public String getPrincipal() {
      return user.getUserIdentity();
    }

    @Override
    public User getUserDetail() {
      return user;
    }

    @Override
    public java.util.List<? extends Role> getRoles() {
      return user.getRoles();
    }

    @Override
    public AccessControlParams getAccessControlParams() {
      return AccessControlParams.empty();
    }
  }
}

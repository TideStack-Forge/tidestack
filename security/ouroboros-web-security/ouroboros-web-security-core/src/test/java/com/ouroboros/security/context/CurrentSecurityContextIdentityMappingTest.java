package com.ouroboros.security.context;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.StandardClaims;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentSecurityContextIdentityMappingTest {

  @AfterEach
  void resetContextHolder() {
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void mapsUserFactsToSecurityIdentity() {
    Claims claims = Claims.builder()
        .claim(StandardClaims.EMAIL, "alice@ouroboros.test")
        .claim(StandardClaims.SUB, "subject-100")
        .build();

    Subject subject = new Subject() {
      @Override
      public String getId() {
        return "subject-100";
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
      public List<com.ouroboros.security.authority.OuroborosGrantedAuthority> getAuthorities() {
        return Collections.emptyList();
      }
    };

    List<Role> roles = Collections.singletonList(role);

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
      public List<Role> getRoles() {
        return roles;
      }
    };

    SecurityIdentity identity = new DefaultSecurityIdentity(user);

    assertEquals("alice", identity.getPrincipal());
    assertEquals("alice", identity.getUserIdentity());
    assertSame(user, identity.getUserDetail());
    assertSame(claims, identity.getClaims());
    assertSame(subject, identity.getSubject());
    assertSame(roles, identity.getRoles());
  }

  @Test
  void platformIdentityCarriesUnrestrictedCredentialFactsWithoutARequestDecision() {
    SecurityIdentity identity = new DefaultSecurityIdentity(() -> "alice");
    assertEquals("alice", identity.getPrincipal());
    assertEquals(com.ouroboros.security.identity.CredentialContext.platform(), identity.getCredentialContext());
    assertEquals(com.ouroboros.security.identity.AuthorizationBoundary.unrestricted(), identity.getAuthorizationBoundary());
    assertTrue(identity.getAuthorizationBoundary().permissions().isEmpty());
  }

  @Test
  void currentSecurityContextHolderDelegatesAndCanClear() {
    SecurityIdentity identity = new SecurityIdentity() {
      @Override
      public String getPrincipal() {
        return "bob";
      }

      @Override
      public String getUserIdentity() {
        return "bob";
      }

      @Override
      public User getUserDetail() {
        return () -> "bob";
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

    CurrentSecurityContext delegate = new CurrentSecurityContext() {
      @Override
      public Optional<SecurityIdentity> findIdentity() {
        return Optional.of(identity);
      }
    };

    CurrentSecurityContextHolder.setDelegate(delegate);

    assertSame(delegate, CurrentSecurityContextHolder.getDelegate());
    assertTrue(CurrentSecurityContextHolder.findIdentity().isPresent());
    assertSame(identity, CurrentSecurityContextHolder.getRequiredIdentity());

    CurrentSecurityContextHolder.clearDelegate();

    assertFalse(CurrentSecurityContextHolder.findIdentity().isPresent());
  }

  @Test
  void getRequiredIdentityThrowsWhenIdentityMissing() {
    CurrentSecurityContextHolder.clearDelegate();

    assertThrows(IllegalStateException.class, CurrentSecurityContextHolder::getRequiredIdentity);
  }
}

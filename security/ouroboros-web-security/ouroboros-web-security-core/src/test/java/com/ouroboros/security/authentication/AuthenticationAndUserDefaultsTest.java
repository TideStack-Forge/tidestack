package com.ouroboros.security.authentication;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.StandardClaims;
import com.ouroboros.security.rbac.PersonSubject;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationAndUserDefaultsTest {

  @Test
  void anonymousAuthenticationExposesExpectedDefaults() {
    AnonymousAuthentication first = AnonymousAuthentication.getInstance();
    AnonymousAuthentication second = AnonymousAuthentication.getInstance();
    assertSame(first, second);
    assertEquals("anonymousUser", first.getPrincipal());
    assertNull(first.getName());
    assertNull(first.getCredentials());
    assertNull(first.getDetails());
    assertFalse(first.isAuthenticated());
    first.setAuthenticated(true);
    assertTrue(first.getAuthorities().isEmpty());
    assertTrue(first.getRoles().isEmpty());
    org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.security.exception.AuthorizationDeniedException.class, first::getAccessControlParams);
    org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.security.exception.AuthorizationDeniedException.class, () -> first.getAccessControlParams("x"));
  }

  @Test
  void ouroborosAuthenticationDefaultMethodsMergeAuthoritiesAndParams() {
    AccessControlParams p1 = params(Arrays.asList("a"), Arrays.asList("d1"));
    AccessControlParams p2 = params(Arrays.asList("b"), Arrays.asList("d2"));
    Authority authority = authority("perm.order");

    OuroborosGrantedAuthority g1 = granted("perm.order", authority, p1);
    OuroborosGrantedAuthority g2 = granted("perm.order", authority, p2);
    OuroborosGrantedAuthority ignored = granted("perm.null", null, p1);

    Role r1 = role(Arrays.asList(g1, ignored));
    Role r2 = role(Collections.singletonList(g2));

    OuroborosAuthentication auth = new OuroborosAuthentication() {
      @Override
      public String getPrincipal() {
        return "u1";
      }

      @Override
      public Object getCredentials() {
        return "c";
      }

      @Override
      public Object getDetails() {
        return "d";
      }

      @Override
      public boolean isAuthenticated() {
        return true;
      }

      @Override
      public void setAuthenticated(boolean isAuthenticated) {
      }

      @Override
      public User getUserDetail() {
        return userWithClaims();
      }

      @Override
      public List<? extends Role> getRoles() {
        return Arrays.asList(r1, r2);
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return AccessControlParams.empty();
      }
    };

    try (var definitions = org.mockito.Mockito.mockStatic(com.ouroboros.security.authority.AuthorityCenter.class)) {
      definitions.when(() -> com.ouroboros.security.authority.AuthorityCenter.getAuthority("perm.order"))
          .thenReturn(java.util.Optional.of(authority));
      definitions.when(() -> com.ouroboros.security.authority.AuthorityCenter.getAuthority("perm.null"))
          .thenReturn(java.util.Optional.empty());
      definitions.when(() -> com.ouroboros.security.authority.AuthorityCenter.getAuthority("missing"))
          .thenReturn(java.util.Optional.empty());
      assertEquals("u1", auth.getName());
      assertEquals(1, auth.getAuthorities().size());
      AccessControlParams merged = auth.getAccessControlParams("perm.order");
      assertTrue(merged.getFilters().containsKey("OR"));
      assertEquals(java.util.Set.of("a", "b"), merged.getFieldConstraint().fields());
      org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.security.exception.AuthorizationDeniedException.class,
          () -> auth.getAccessControlParams("missing"));
    }
  }

  @Test
  void userAndPersonSubjectDefaultMethodsReadClaims() {
    User user = userWithClaims();
    assertEquals("Alice", user.getDisplayName());
    assertEquals("alice@test", user.getEmail());
    assertEquals("/avatar.png", user.getPicture());
    assertEquals("13800000000", user.getPhoneNumber());
    assertTrue(user.isEnabled());

    PersonSubject person = new PersonSubject() {
      @Override
      public String getId() {
        return "p1";
      }

      @Override
      public String getName() {
        return "Alice";
      }
    };
    assertEquals("PERSON", person.getType());
    assertEquals("Alice", person.getDisplayName());
  }

  private static User userWithClaims() {
    final Claims claims = Claims.builder()
        .claim(StandardClaims.EMAIL, "alice@test")
        .claim(StandardClaims.PICTURE, "/avatar.png")
        .claim(StandardClaims.PHONE_NUMBER, "13800000000")
        .build();
    final Subject subject = new Subject() {
      @Override
      public String getId() {
        return "s1";
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
        return "Alice";
      }
    };
    return new User() {
      @Override
      public String getUserIdentity() {
        return "u1";
      }

      @Override
      public Claims getClaims() {
        return claims;
      }

      @Override
      public Subject getSubject() {
        return subject;
      }
    };
  }

  private static Role role(final List<OuroborosGrantedAuthority> authorities) {
    return new Role() {
      @Override
      public String getId() {
        return "r";
      }

      @Override
      public String getName() {
        return "role";
      }

      @Override
      public List<OuroborosGrantedAuthority> getAuthorities() {
        return authorities;
      }
    };
  }

  private static Authority authority(final String name) {
    return new Authority() {
      @Override
      public String getFullName() {
        return name;
      }

      @Override
      public String getTitle() {
        return name;
      }

      @Override
      public AccessControlDefine getAccessControlDefine() {
        return AccessControlDefine.empty();
      }

      @Override
      public List<String> getApis() {
        return Collections.emptyList();
      }

      @Override
      public List<String> getUiViews() {
        return Collections.emptyList();
      }

      @Override
      public List<String> getMenuItems() {
        return Collections.emptyList();
      }
    };
  }

  private static AccessControlParams params(final List<String> allow, final List<String> disallow) {
    return new AccessControlParams() {
      @Override
      public List<String> getAllowedFields() {
        return allow;
      }

      @Override
      public List<String> getDisallowedFields() {
        return disallow;
      }

      @Override
      public java.util.Map<String, Object> getFilters() {
        return java.util.Collections.<String, Object>singletonMap("k", "v");
      }
    };
  }

  private static OuroborosGrantedAuthority granted(final String name, final Authority authority, final AccessControlParams params) {
    return new OuroborosGrantedAuthority() {
      @Override
      public String getAuthority() {
        return name;
      }

      @Override
      public Authority getAuthorityObject() {
        return authority;
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return params;
      }
    };
  }
}

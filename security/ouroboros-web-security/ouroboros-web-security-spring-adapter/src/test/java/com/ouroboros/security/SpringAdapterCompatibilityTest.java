package com.ouroboros.security;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.security.authentication.DefaultAuthentication;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.AuthorizationDecisionContextHolder;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SpringAdapterCompatibilityTest {

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    CurrentSecurityContextHolder.clearDelegate();
    AuthorizationDecisionContextHolder.clearCurrentRequest();
  }

  @Test
  void nullSecurityContextFallsBackToAnonymousAuthentication() {
    SecurityContextHolder.getContext().setAuthentication(null);

    OuroborosAuthentication authentication = OuroborosAuthenticationHolder.getAuthentication();
    assertSame(AnonymousAuthentication.getInstance(), authentication);
  }

  @Test
  void adaptsSpringAuthenticationPrincipal() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("spring-user", "secret"));

    OuroborosAuthentication authentication = OuroborosAuthenticationHolder.getAuthentication();
    assertEquals("spring-user", authentication.getPrincipal());
  }

  @Test
  void defaultAuthenticationDecisionUsesDecisionContextHolder() {
    AccessControlParams expectedParams = new AccessControlParams() {
      @Override
      public java.util.List<String> getAllowedFields() {
        return Collections.singletonList("id");
      }

      @Override
      public java.util.List<String> getDisallowedFields() {
        return Collections.emptyList();
      }

      @Override
      public java.util.Map<String, Object> getFilters() {
        return Collections.emptyMap();
      }
    };

    Authority authority = new Authority() {
      @Override
      public String getFullName() {
        return "orders.read";
      }

      @Override
      public String getTitle() {
        return "orders.read";
      }

      @Override
      public AccessControlDefine getAccessControlDefine() {
        return AccessControlDefine.empty();
      }

      @Override
      public java.util.List<String> getApis() {
        return Collections.singletonList("GET /api/orders");
      }

      @Override
      public java.util.List<String> getUiViews() {
        return Collections.emptyList();
      }

      @Override
      public java.util.List<String> getMenuItems() {
        return Collections.emptyList();
      }
    };

    OuroborosGrantedAuthority grantedAuthority = new OuroborosGrantedAuthority() {
      @Override
      public String getAuthority() {
        return "orders.read";
      }

      @Override
      public Authority getAuthorityObject() {
        return authority;
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return expectedParams;
      }
    };

    User user = new User() {
      @Override
      public String getUserIdentity() {
        return "user-1";
      }

      @Override
      public java.util.List<Role> getRoles() {
        return Collections.singletonList(new Role() {
          @Override
          public String getId() {
            return "role-1";
          }

          @Override
          public String getName() {
            return "role-1";
          }

          @Override
          public java.util.List<? extends OuroborosGrantedAuthority> getAuthorities() {
            return Collections.singletonList(grantedAuthority);
          }
        });
      }
    };

    DefaultAuthentication authentication = new DefaultAuthentication(user, new MockHttpServletRequest("GET", "/legacy"));
    AuthorizationDecisionContextHolder.setCurrentRequest("GET", "/api/orders");

    assertEquals(expectedParams.getFieldConstraint(), authentication.getAccessControlParams().getFieldConstraint());
    assertEquals(expectedParams.getRowConstraint(), authentication.getAccessControlParams().getRowConstraint());
    AuthorizationDecisionContextHolder.clearCurrentRequest();
    org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.security.exception.AuthorizationDeniedException.class,
        authentication::getAccessControlParams);
  }
}

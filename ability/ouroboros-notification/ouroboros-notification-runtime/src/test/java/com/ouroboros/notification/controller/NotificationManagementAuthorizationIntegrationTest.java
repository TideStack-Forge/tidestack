package com.ouroboros.notification.controller;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authorize.filter.AuthorizeCheckFilterSupplier;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.exception.UnauthenticatedException;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.provider.JarAuthorityProvider;
import com.ouroboros.security.rbac.provider.SpringBeanAuthorityProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Packaged authority metadata is loaded by the original provider and enforced by the original filter/evaluator. */
class NotificationManagementAuthorizationIntegrationTest {
  private Object previousProvider;
  private AuthorizeCheckFilterSupplier supplier;

  @BeforeEach
  void usePackagedDefinitions() {
    previousProvider = ReflectionTestUtils.getField(SpringBeanAuthorityProvider.SingletonHolder.class, "AUTHORITY_PROVIDER");
    var bridge = new SpringBeanAuthorityProvider();
    ReflectionTestUtils.setField(bridge, "authorityProviders", List.of(new JarAuthorityProvider()));
    supplier = new AuthorizeCheckFilterSupplier();
    ReflectionTestUtils.setField(supplier, "servletContext", new MockServletContext());
  }

  @AfterEach
  void restoreContext() {
    ReflectionTestUtils.setField(SpringBeanAuthorityProvider.SingletonHolder.class, "AUTHORITY_PROVIDER", previousProvider);
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void runtimeRoutesDenyAnonymousAndUngrantUsersAndAllowTheOriginalAuthority() throws Exception {
    var authority = AuthorityCenter.getAuthority("systemSetting.notificationTemplate").orElseThrow();
    assertTrue(authority.getUiViews().contains("/system-setting/notification-templates/edit"));
    for (var api : List.of("GET /api/sys/notification-template", "GET /api/sys/notification-template/alpha/runtime-definition",
        "POST /api/sys/notification-template/alpha/runtime-override/validate", "PUT /api/sys/notification-template/alpha/runtime-override",
        "POST /api/sys/notification-template/alpha/restore-default", "GET /api/notification/adapter-schema/management")) {
      CurrentSecurityContextHolder.setDelegate(() -> Optional.empty());
      assertThrows(UnauthenticatedException.class, () -> assertAllowed(api));
      authenticate(false);
      assertThrows(AccessDeniedException.class, () -> assertAllowed(api));
      authenticate(true);
      assertAllowed(api);
    }
    assertThrows(AccessDeniedException.class, () -> assertAllowed("PUT /api/sys/configuration"));
  }

  private void authenticate(boolean granted) {
    var definition = AuthorityCenter.getAuthority("systemSetting.notificationTemplate").orElseThrow();
    var grant = mock(OuroborosGrantedAuthority.class);
    when(grant.getAuthority()).thenReturn(definition.getFullName());
    when(grant.getAccessControlParams()).thenReturn(AccessControlParams.empty());
    var role = mock(Role.class);
    doReturn(granted ? List.of(grant) : List.of()).when(role).getAuthorities();
    var user = mock(User.class);
    when(user.getUserIdentity()).thenReturn("notification-admin");
    when(user.getRoles()).thenReturn(List.of(role));
    var identity = new DefaultSecurityIdentity(user);
    var authentication = mock(OuroborosAuthentication.class);
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getIdentity()).thenReturn(identity);
    CurrentSecurityContextHolder.setDelegate(new CurrentSecurityContext() {
      public Optional<SecurityIdentity> findIdentity() { return Optional.of(identity); }
      public Optional<OuroborosAuthentication> findAuthentication() { return Optional.of(authentication); }
    });
  }

  private void assertAllowed(String api) throws Exception {
    int separator = api.indexOf(' ');
    var reached = new AtomicBoolean();
    supplier.getAuthorizeFilter().doFilter(new MockHttpServletRequest(api.substring(0, separator), api.substring(separator + 1)),
        new MockHttpServletResponse(), (request, response) -> reached.set(true));
    assertTrue(reached.get(), api);
  }
}

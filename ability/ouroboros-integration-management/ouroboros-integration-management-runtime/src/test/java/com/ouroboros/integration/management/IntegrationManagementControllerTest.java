package com.ouroboros.integration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ouroboros.integration.management.IntegrationManagementPage.Section;
import com.ouroboros.security.authentication.OuroborosAuthentication.EffectiveAuthority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.BaseAuthority;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;
import com.ouroboros.util.Json;

class IntegrationManagementControllerTest {
  private final IntegrationManagementController controller = new IntegrationManagementController(
      new IntegrationWorkspace(List.of(() -> List.of(
          new IntegrationManagementPage("ldap", Section.CONNECTIONS, "LDAP", "/ldap", 10, Set.of("login.configure")),
          new IntegrationManagementPage("clients", Section.APPLICATIONS, "Applications", "/clients", 10, Set.of("clients.manage"))))));

  @AfterEach
  void clearIdentity() { CurrentSecurityContextHolder.clearDelegate(); }

  @Test
  void projectsTheCurrentAdministratorsAuthorizedSectionWithoutLeakingOtherEditors() {
    try (var definitions = mockStatic(AuthorityCenter.class)) {
      definitions.when(() -> AuthorityCenter.getAuthority(anyString())).thenAnswer(call -> {
        var definition = new BaseAuthority(); definition.setFullName(call.getArgument(0)); return Optional.of(definition);
      });
      authenticate("login.configure");
      var response = new MockHttpServletResponse();
      String upper = Json.toJsonString(controller.section("connections", response).getData());
      assertThat(upper).contains("/ldap").doesNotContain("/clients");
      assertThat(Json.toJsonString(controller.section("applications", response).getData()))
          .contains("管理权限").doesNotContain("/clients");
      assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
  }

  @Test
  void rejectsAnonymousRequestsAndUnknownSections() {
    assertThatThrownBy(() -> controller.section("connections", new MockHttpServletResponse()))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> controller.section("unknown", new MockHttpServletResponse()))
        .hasMessageContaining("未知");
  }

  private void authenticate(String name) {
    var definition = new BaseAuthority(); definition.setFullName(name);
    var grant = new EffectiveAuthority(definition, ResolvedAccessControlParams.all());
    var role = mock(Role.class); when(role.getAuthorities()).thenAnswer(call -> new ArrayList<>(List.of(grant)));
    var user = mock(User.class); when(user.getUserIdentity()).thenReturn("admin"); when(user.getRoles()).thenReturn(List.of(role));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
  }
}

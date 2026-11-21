package com.ouroboros.organizationstructure.directory;

import com.ouroboros.organizationstructure.directory.service.EnterpriseDirectoryService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.*;
import com.ouroboros.security.authentication.OuroborosAuthentication.EffectiveAuthority;
import com.ouroboros.security.authority.*;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.exception.AuthorizationDeniedException;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.*;
import com.ouroboros.util.Json;

class EnterpriseDirectoryControllerTest {
  private final EnterpriseDirectoryService directory = mock(EnterpriseDirectoryService.class);
  private final DirectoryBindingCommands bindings = mock(DirectoryBindingCommands.class);
  private final DirectorySyncCommands synchronization = mock(DirectorySyncCommands.class);
  private final DirectoryStore store = mock(DirectoryStore.class);
  private final DirectoryOrganization organization = mock(DirectoryOrganization.class);
  private final EnterpriseDirectoryController controller = new EnterpriseDirectoryController(
      directory, bindings, synchronization, store, organization);
  private org.mockito.MockedStatic<AuthorityCenter> authorities;

  @BeforeEach void setup() {
    authorities = mockStatic(AuthorityCenter.class);
    authorities.when(() -> AuthorityCenter.getAuthority(anyString())).thenAnswer(call -> {
      var definition = new BaseAuthority(); definition.setFullName(call.getArgument(0)); return Optional.of(definition);
    });
    CurrentSecurityContextHolder.clearDelegate();
  }

  @AfterEach void cleanup() { authorities.close(); CurrentSecurityContextHolder.clearDelegate(); }

  @Test void anonymousRequestsCannotReadOrMutateTheDirectory() {
    assertThatThrownBy(controller::managementState).isInstanceOf(AuthorizationDeniedException.class);
    assertThatThrownBy(() -> controller.options("MEMBER")).isInstanceOf(AuthorizationDeniedException.class);
    assertThatThrownBy(controller::connections).isInstanceOf(AuthorizationDeniedException.class);
    assertThatThrownBy(() -> controller.snapshot("connection")).isInstanceOf(AuthorizationDeniedException.class);
    assertThatThrownBy(() -> controller.bindings("connection")).isInstanceOf(AuthorizationDeniedException.class);
    assertThatThrownBy(controller::scopes).isInstanceOf(AuthorizationDeniedException.class);
    assertThatThrownBy(() -> controller.bind(new EnterpriseDirectoryController.Bind("connection", "MEMBER", "remote", "local", null)))
        .isInstanceOf(AuthorizationDeniedException.class);
    assertThatThrownBy(() -> controller.unbind("binding", new EnterpriseDirectoryController.Unbind(3, true)))
        .isInstanceOf(AuthorizationDeniedException.class);
    assertThatThrownBy(() -> controller.preview("company")).isInstanceOf(AuthorizationDeniedException.class);
    assertThatThrownBy(() -> controller.scope(scope(false, true))).isInstanceOf(AuthorizationDeniedException.class);
    assertThatThrownBy(() -> controller.apply("run", new EnterpriseDirectoryController.Apply(true, List.of())))
        .isInstanceOf(AuthorizationDeniedException.class);
    verifyNoInteractions(directory, bindings, synchronization, store, organization);
  }

  @Test void viewingDoesNotAuthorizeBindingOrSynchronization() {
    authenticate(EnterpriseDirectoryController.VIEW);
    when(directory.connections()).thenReturn(List.of(new com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog.ConnectionSummary("main", "feishu")));
    when(organization.options("MEMBER")).thenReturn(List.of(Map.of("label", "员工甲", "value", "employee")));
    assertThat(Json.toJsonString(controller.managementState().getData())).contains("\"canBind\":false", "\"canSynchronize\":false");
    assertThat(Json.toJsonString(controller.connections().getData())).contains("main");
    assertThat(Json.toJsonString(controller.options("MEMBER").getData())).contains("员工甲");
    controller.snapshot("main"); controller.bindings("main"); controller.scopes();
    assertThatThrownBy(() -> controller.options("ACCOUNT")).hasMessageContaining("类型无效");
    assertThatThrownBy(() -> controller.bind(new EnterpriseDirectoryController.Bind("main", "MEMBER", "remote", "local", null)))
        .isInstanceOf(AuthorizationDeniedException.class);
    assertThatThrownBy(() -> controller.preview("company")).isInstanceOf(AuthorizationDeniedException.class);
    verifyNoInteractions(bindings, synchronization);
  }

  @Test void bindingPermissionDoesNotGrantSourceOwnershipAndUnbindingRequiresImpactAcknowledgement() {
    authenticate(EnterpriseDirectoryController.BIND);
    controller.bind(new EnterpriseDirectoryController.Bind("main", "MEMBER", "remote", "local", null));
    verify(directory).bind("main", "MEMBER", "remote", "local", null);
    assertThatThrownBy(() -> controller.bind(new EnterpriseDirectoryController.Bind("main", "MEMBER", "remote", "local", "company")))
        .isInstanceOf(AuthorizationDeniedException.class);
    assertThatThrownBy(() -> controller.unbind("binding", new EnterpriseDirectoryController.Unbind(7, false)))
        .hasMessageContaining("确认解绑影响");
    verifyNoInteractions(bindings);
    controller.unbind("binding", new EnterpriseDirectoryController.Unbind(7, true));
    verify(bindings).unbind("binding", 7);
  }

  @Test void sourceSettingsRetainTheExpectedVersionAndRequireDisableAcknowledgement() {
    authenticate(EnterpriseDirectoryController.SYNCHRONIZE);
    assertThatThrownBy(() -> controller.scope(scope(false, false))).hasMessageContaining("确认停用");
    verifyNoInteractions(bindings);
    controller.scope(scope(false, true));
    verify(bindings).configure(argThat(value -> value.getId().equals("company")
        && value.getConnectionId().equals("main") && !value.getEnabled() && !value.getSyncMobile() && value.getSyncEmail()), eq(4L));
    controller.preview("company"); verify(directory).preview("company");
    assertThatThrownBy(() -> controller.unbind("binding", new EnterpriseDirectoryController.Unbind(7, true)))
        .isInstanceOf(AuthorizationDeniedException.class);
  }

  @Test void previewApplicationPassesExplicitPrimaryChoicesAndRejectsAmbiguousChoices() {
    authenticate(EnterpriseDirectoryController.SYNCHRONIZE);
    var choice = new EnterpriseDirectoryController.PrimaryDepartment("member", "product");
    controller.apply("run", new EnterpriseDirectoryController.Apply(true, List.of(choice)));
    verify(synchronization).apply("run", true, Map.of("member", "product"));
    assertThatThrownBy(() -> controller.apply("run", new EnterpriseDirectoryController.Apply(true, List.of(choice, choice))))
        .hasMessageContaining("唯一主部门");
    assertThatThrownBy(() -> controller.apply("run", new EnterpriseDirectoryController.Apply(true,
        List.of(new EnterpriseDirectoryController.PrimaryDepartment("member", "")))))
        .hasMessageContaining("唯一主部门");
    verifyNoMoreInteractions(synchronization);
  }

  private EnterpriseDirectoryController.Scope scope(boolean enabled, boolean acknowledge) {
    return new EnterpriseDirectoryController.Scope("company", "main", "0", enabled, false, true, 4L, acknowledge);
  }

  private void authenticate(String... permissions) {
    var grants = Arrays.stream(permissions).map(name -> {
      var authority = new BaseAuthority(); authority.setFullName(name);
      return new EffectiveAuthority(authority, ResolvedAccessControlParams.all());
    }).toList();
    var role = mock(Role.class); when(role.getAuthorities()).thenAnswer(call -> new ArrayList<>(grants));
    var user = mock(User.class); when(user.getUserIdentity()).thenReturn("admin"); when(user.getRoles()).thenReturn(List.of(role));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
  }
}

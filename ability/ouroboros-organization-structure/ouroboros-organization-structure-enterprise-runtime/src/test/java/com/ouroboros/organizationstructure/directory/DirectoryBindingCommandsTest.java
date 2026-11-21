package com.ouroboros.organizationstructure.directory;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ouroboros.enterprise.EnterpriseConnectionSnapshot;
import com.ouroboros.enterprise.directory.*;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.organizationstructure.entity.*;

class DirectoryBindingCommandsTest {
  private final MemoryDirectoryStore store = new MemoryDirectoryStore();
  private final DirectoryOrganization organization = mock(DirectoryOrganization.class);
  private final EnterpriseConnectionCatalog connections = mock(EnterpriseConnectionCatalog.class);
  private final DirectoryBindingCommands commands = new DirectoryBindingCommands(store, organization, connections);
  private final EnterpriseDirectorySnapshot snapshot = new EnterpriseDirectorySnapshot(
      List.of(new EnterpriseDirectoryDepartment("dep", null, "部门")),
      List.of(new EnterpriseDirectoryMember("member", "员工", null, null, List.of("dep"), "dep", true)), true);

  @BeforeEach void setUp() {
    when(connections.requireActive("connection")).thenReturn(new EnterpriseConnectionSnapshot(
        "wecom", "connection", "rev", "fp", "managed:connection", Map.of(), true, true));
    var department = new OrganizationNodeEntity(); department.setId("department"); department.setType("department");
    when(organization.node("department")).thenReturn(department);
  }

  @Test void bindingNeedsNeitherAccountNorProfileMutationAndIsUniqueInBothDirections() {
    var binding = commands.bind("connection", "rev", snapshot, "MEMBER", "member", "employee", null);
    assertThat(binding.getSyncOwnerKey()).isNull(); assertThat(binding.getActive()).isTrue();
    verify(organization, never()).updateMember(any(), any(), any());
    assertThatThrownBy(() -> commands.bind("connection", "rev", snapshot, "MEMBER", "member", "other", null))
        .hasMessageContaining("已有关联");
    assertThatThrownBy(() -> commands.bind("connection", "rev-old", snapshot, "DEPARTMENT", "dep", "department", null))
        .hasMessageContaining("连接已改变");
    assertThatThrownBy(() -> commands.bind("connection", "rev", snapshot, "MEMBER", "unknown", "other", null))
        .hasMessageContaining("不在已读取");
  }

  @Test void unbindPreservesHistoryAndRebindingGetsANewIdentity() {
    var original = commands.bind("connection", "rev", snapshot, "MEMBER", "member", "employee", null);
    String oldId = original.getId(); original.setAppointmentIds("[\"owned\"]");
    commands.unbind(oldId, original.getVersion());
    assertThat(original.getActive()).isFalse(); assertThat(original.getExternalRouteKey()).isNull();
    verify(organization).endAppointments(List.of("owned"));
    var next = commands.bind("connection", "rev", snapshot, "MEMBER", "member", "employee", null);
    assertThat(next.getId()).isNotEqualTo(oldId); assertThat(store.associations).hasSize(2);
  }

  @Test void syncIsOptInAndDisablingReleasesAuthorityWhileKeepingObjectsAndRelations() {
    var scope = new DirectorySyncScope(); scope.setId("company"); scope.setConnectionId("connection"); scope.setExternalRootId("root");
    scope.setEnabled(true); scope.setSyncMobile(false); scope.setSyncEmail(true);
    commands.configure(scope, null);
    var binding = commands.bind("connection", "rev", snapshot, "MEMBER", "member", "employee", "company");
    assertThat(binding.getManagedFields()).contains("email").doesNotContain("mobile");
    var disabled = new DirectorySyncScope(); disabled.setId("company"); disabled.setConnectionId("connection");
    disabled.setExternalRootId("root"); disabled.setEnabled(false); disabled.setSyncMobile(false); disabled.setSyncEmail(true);
    commands.configure(disabled, scope.getVersion());
    assertThat(binding.getSyncOwnerKey()).isNull(); assertThat(binding.getScopeId()).isNull();
    assertThat(binding.getActive()).isTrue(); verify(organization, never()).endAppointments(any());
  }

  @Test void sourceChangeRequiresExplicitExitAndScopeVersionMustMatch() {
    var scope = new DirectorySyncScope(); scope.setId("company"); scope.setConnectionId("connection"); scope.setExternalRootId("root");
    scope.setEnabled(true); scope.setSyncMobile(false); scope.setSyncEmail(false); commands.configure(scope, null);
    var replacement = new DirectorySyncScope(); replacement.setId("company"); replacement.setConnectionId("connection");
    replacement.setExternalRootId("another"); replacement.setEnabled(true); replacement.setSyncMobile(false); replacement.setSyncEmail(false);
    assertThatThrownBy(() -> commands.configure(replacement, 99L)).hasMessageContaining("请刷新");
    assertThatThrownBy(() -> commands.configure(replacement, scope.getVersion())).hasMessageContaining("先停用");
  }
}

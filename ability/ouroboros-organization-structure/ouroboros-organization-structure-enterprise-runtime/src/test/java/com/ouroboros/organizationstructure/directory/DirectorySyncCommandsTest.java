package com.ouroboros.organizationstructure.directory;

import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ouroboros.enterprise.EnterpriseConnectionSnapshot;
import com.ouroboros.enterprise.directory.*;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;

class DirectorySyncCommandsTest {
  private final MemoryDirectoryStore store = new MemoryDirectoryStore();
  private final DirectoryOrganization organization = mock(DirectoryOrganization.class);
  private final EnterpriseConnectionCatalog connections = mock(EnterpriseConnectionCatalog.class);
  private final DirectorySyncCommands commands = new DirectorySyncCommands(store, organization, connections);
  private DirectorySyncScope scope;
  private EnterpriseDirectorySnapshot snapshot;

  @BeforeEach void setUp() {
    scope = new DirectorySyncScope(); scope.setId("company"); scope.setConnectionId("feishu-main");
    scope.setExternalRootId("root"); scope.setEnabled(true); scope.setSyncMobile(false); scope.setSyncEmail(false); scope.setVersion(1L);
    store.sources.put("company", scope);
    var company = new OrganizationNodeEntity(); company.setId("company"); company.setName("本地公司"); company.setType("company");
    when(organization.company("company")).thenReturn(company);
    when(connections.requireActive("feishu-main")).thenReturn(new EnterpriseConnectionSnapshot(
        "feishu", "feishu-main", "rev-1", "fingerprint", "managed:feishu-main", Map.of(), true, false));
    snapshot = new EnterpriseDirectorySnapshot(List.of(new EnterpriseDirectoryDepartment("root", null, "集团"),
        new EnterpriseDirectoryDepartment("dep", "root", "研发")), List.of(new EnterpriseDirectoryMember(
        "m1", "成员", "phone", "mail", List.of("dep"), "dep", true)), true);
    when(organization.createDepartment(any(), eq("company"))).thenReturn("local-dep");
    when(organization.contains("company", "local-dep")).thenReturn(true);
    when(organization.createMember(any(), eq(scope))).thenReturn("local-employee");
    when(organization.appointments(eq("local-employee"), any(), anyMap(), anyList())).thenReturn(List.of("owned-appointment"));
    when(organization.baseline(any())).thenReturn(Map.of("local", "unchanged"));
  }

  @Test void previewWritesNoOrganizationAndConfirmedApplyUsesStableBindings() {
    var first = commands.preview(scope, "rev-1", snapshot);
    verify(organization, never()).createDepartment(any(), any());
    assertThat(first.changes()).hasSize(2);
    commands.apply(first.runId(), true);
    assertThat(store.associations).hasSize(2);
    var member = store.associations.values().stream().filter(b -> "MEMBER".equals(b.getObjectType())).findFirst().orElseThrow();
    assertThat(member.getManagedFields()).isEqualTo("name,appointments");
    assertThat(member.getAppointmentIds()).isEqualTo("[\"owned-appointment\"]");
    commands.apply(first.runId(), true);
    var second = commands.preview(scope, "rev-1", snapshot); commands.apply(second.runId(), true);
    verify(organization, times(1)).createDepartment(any(), any());
    verify(organization, times(1)).createMember(any(), any());
    assertThat(store.previews.get(first.runId()).getStatus()).isEqualTo("APPLIED");
  }

  @Test void requiresCompleteReadAndExplicitConfirmation() {
    var partial = commands.preview(scope, "rev-1", new EnterpriseDirectorySnapshot(snapshot.departments(), snapshot.members(), false));
    assertThatThrownBy(() -> commands.apply(partial.runId(), true)).hasMessageContaining("不完整");
    var complete = commands.preview(scope, "rev-1", snapshot);
    assertThatThrownBy(() -> commands.apply(complete.runId(), false)).hasMessageContaining("确认");
    verify(organization, never()).createMember(any(), any());
  }

  @Test void changedConnectionScopeAndLocalRecordsInvalidatePreview() {
    var preview = commands.preview(scope, "rev-old", snapshot);
    String staleRun = preview.runId();
    assertThatThrownBy(() -> commands.apply(staleRun, true)).hasMessageContaining("连接已改变");
    preview = commands.preview(scope, "rev-1", snapshot); String localRun = preview.runId();
    when(organization.company("company")).thenReturn(new OrganizationNodeEntity());
    assertThatThrownBy(() -> commands.apply(localRun, true)).hasMessageContaining("本地资料已改变");
    scope.setEnabled(false);
    assertThatThrownBy(() -> commands.apply(localRun, true)).hasMessageContaining("已停用");
  }

  @Test void missingMembersStayForManualReviewWhileExplicitInactiveEndsOnlyOwnedAppointments() {
    var member = bound("MEMBER", "m1", "local-employee", true); member.setAppointmentIds("[\"owned\"]");
    var empty = new EnterpriseDirectorySnapshot(snapshot.departments(), List.of(), true);
    var preview = commands.preview(scope, "rev-1", empty);
    assertThat(preview.missingPendingReview()).contains("MEMBER:m1");
    commands.apply(preview.runId(), true);
    assertThat(member.getActive()).isTrue(); verify(organization, never()).endAppointments(anyList());
    var inactive = new EnterpriseDirectoryMember("m1", "成员", null, null, List.of("dep"), "dep", false);
    preview = commands.preview(scope, "rev-1", new EnterpriseDirectorySnapshot(snapshot.departments(), List.of(inactive), true));
    commands.apply(preview.runId(), true);
    assertThat(member.getActive()).isFalse(); verify(organization).endAppointments(List.of("owned"));
  }

  @Test void manualBindingsKeepTheirLocalProfileAndAppointments() {
    bound("DEPARTMENT", "dep", "local-dep", false); bound("MEMBER", "m1", "local-employee", false);
    var preview = commands.preview(scope, "rev-1", snapshot); commands.apply(preview.runId(), true);
    verify(organization, never()).updateDepartment(any(), any(), any());
    verify(organization, never()).updateMember(any(), any(), any());
    verify(organization, never()).appointments(any(), any(), any(), any());
  }

  @Test void previewExpiresAndExternalScopeMustBeVisible() {
    var preview = commands.preview(scope, "rev-1", snapshot);
    store.previews.get(preview.runId()).setCreatedAt(LocalDateTime.now().minusHours(1));
    assertThatThrownBy(() -> commands.apply(preview.runId(), true)).hasMessageContaining("已失效");
    assertThatThrownBy(() -> commands.preview(scope, "rev-1", new EnterpriseDirectorySnapshot(List.of(), List.of(), true)))
        .hasMessageContaining("根部门不可见");
  }

  private DirectoryBinding bound(String type, String externalId, String localId, boolean owned) {
    var b = new DirectoryBinding(); b.setConnectionId("feishu-main"); b.setObjectType(type); b.setExternalId(externalId);
    b.setLocalId(localId); b.setActive(true); DirectoryBindingCommands.route(b);
    if (owned) { DirectoryBindingCommands.own(b, scope); }
    return store.insert(b);
  }
}

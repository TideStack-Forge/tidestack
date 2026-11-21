package com.ouroboros.organizationstructure.directory;

import org.junit.jupiter.api.Test;
import com.ouroboros.organizationstructure.entity.*;
import static org.assertj.core.api.Assertions.*;

class DirectoryMutationPolicyTest {
  private final MemoryDirectoryStore store = new MemoryDirectoryStore();
  private final DirectoryMutationPolicy policy = new DirectoryMutationPolicy(store);

  @Test void sourceFieldsAreProtectedWhileLocalFieldsAndSecondaryAppointmentsRemainEditable() {
    member(); var local = new EmployeeEntity(); local.setGender("女"); local.setMobile("local-mobile");
    assertThatCode(() -> policy.employee("employee", local, false)).doesNotThrowAnyException();
    var renamed = new EmployeeEntity(); renamed.setName("changed");
    assertThatThrownBy(() -> policy.employee("employee", renamed, false)).hasMessageContaining("主通讯录");
    assertThatThrownBy(() -> policy.employee("employee", null, true)).hasMessageContaining("主通讯录");
    var primary = new AppointmentEntity(); primary.setEmployee("employee"); primary.setType("PRIMARY");
    assertThatThrownBy(() -> policy.appointment(primary, false)).hasMessageContaining("主通讯录");
    primary.setType("SECONDARY"); assertThatCode(() -> policy.appointment(primary, false)).doesNotThrowAnyException();
    primary.setId("owned"); assertThatThrownBy(() -> policy.appointment(primary, true)).hasMessageContaining("主通讯录");
    DirectoryWriteContext.run("scope", () -> policy.employee("employee", renamed, false));
    assertThatThrownBy(() -> policy.employee("employee", renamed, false)).hasMessageContaining("主通讯录");
  }

  @Test void localDepartmentNotesRemainEditableButSourceHierarchyAndScopeDeletionAreProtected() {
    var binding = new DirectoryBinding(); binding.setObjectType("DEPARTMENT"); binding.setLocalId("dep");
    binding.setConnectionId("conn"); binding.setExternalId("external"); binding.setScopeId("scope");
    binding.setSyncOwnerKey("DEPARTMENT:dep"); binding.setManagedFields("name,parent"); store.insert(binding);
    var note = new OrganizationNodeEntity(); note.setDescription("local note");
    assertThatCode(() -> policy.node("dep", note, false)).doesNotThrowAnyException();
    note.setParent("another"); assertThatThrownBy(() -> policy.node("dep", note, false)).hasMessageContaining("主通讯录");
    var scope = new DirectorySyncScope(); scope.setId("company"); scope.setEnabled(true); store.sources.put("company", scope);
    assertThatThrownBy(() -> policy.node("company", null, true)).hasMessageContaining("主通讯录");
  }

  private void member() {
    var b = new DirectoryBinding(); b.setConnectionId("conn"); b.setObjectType("MEMBER"); b.setExternalId("member");
    b.setLocalId("employee"); b.setActive(true); b.setScopeId("scope"); b.setSyncOwnerKey("MEMBER:employee");
    b.setManagedFields("name,appointments,email"); b.setAppointmentIds("[\"owned\"]"); store.insert(b);
  }
}

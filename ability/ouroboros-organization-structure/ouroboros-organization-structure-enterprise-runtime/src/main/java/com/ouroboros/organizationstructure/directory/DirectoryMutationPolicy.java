package com.ouroboros.organizationstructure.directory;

import java.util.*;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.organizationstructure.entity.*;
import com.ouroboros.organizationstructure.service.OrganizationMutationPolicy;

public class DirectoryMutationPolicy implements OrganizationMutationPolicy {
  private final DirectoryStore store;
  public DirectoryMutationPolicy(DirectoryStore store) { this.store = store; }

  @Override public void node(String id, OrganizationNodeEntity patch, boolean removing) {
    if (removing && store.scope(id).filter(scope -> Boolean.TRUE.equals(scope.getEnabled())).isPresent()) { reject(); }
    for (var binding : owners("DEPARTMENT", id)) {
      if (removing || patch != null && (patch.getName() != null || patch.getParent() != null || patch.getType() != null)) { reject(); }
    }
  }

  @Override public void employee(String id, EmployeeEntity patch, boolean removing) {
    for (var binding : owners("MEMBER", id)) {
      var fields = Set.of(binding.getManagedFields().split(","));
      if (removing || patch != null && (patch.getName() != null
          || fields.contains("mobile") && patch.getMobile() != null
          || fields.contains("email") && patch.getEmail() != null)) { reject(); }
    }
  }

  @Override public void appointment(AppointmentEntity appointment, boolean removing) {
    for (var binding : owners("MEMBER", appointment.getEmployee())) {
      if (removing && DirectoryOrganization.ownedAppointments(binding).contains(appointment.getId())
          || !removing && "PRIMARY".equals(appointment.getType())) { reject(); }
    }
  }

  private List<DirectoryBinding> owners(String type, String id) {
    return store.bindingsForLocal(type, id).stream().filter(binding -> binding.getSyncOwnerKey() != null)
        .filter(binding -> !DirectoryWriteContext.owns(binding.getScopeId())).toList();
  }
  private static void reject() { throw new ValidationException("资料或任职由主通讯录来源维护，请先退出该范围同步再修改"); }
}

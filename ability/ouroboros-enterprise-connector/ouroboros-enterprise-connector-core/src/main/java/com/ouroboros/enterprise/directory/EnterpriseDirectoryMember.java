package com.ouroboros.enterprise.directory;

import java.util.List;

/** Stable provider member identity and the department in which the member is currently active. */
public record EnterpriseDirectoryMember(
    String externalId,
    String name,
    String mobile,
    String email,
    List<String> departmentExternalIds,
    String primaryDepartmentExternalId,
    boolean active) {
  public EnterpriseDirectoryMember {
    departmentExternalIds = departmentExternalIds == null ? List.of() : List.copyOf(departmentExternalIds);
    if (externalId == null || externalId.isBlank()) {
      throw new IllegalArgumentException("外部成员 ID 不能为空");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("外部成员名称不能为空");
    }
    if (primaryDepartmentExternalId != null && !departmentExternalIds.contains(primaryDepartmentExternalId)) {
      throw new IllegalArgumentException("主部门必须属于该成员的部门范围");
    }
  }
}
